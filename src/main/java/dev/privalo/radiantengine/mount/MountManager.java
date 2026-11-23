package dev.privalo.radiantengine.mount;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.model.ActiveRig;
import dev.privalo.radiantengine.model.RiggedEntity;
import dev.privalo.radiantengine.model.bone.RigBone;
import dev.privalo.radiantengine.model.bone.SeatBehavior;
import dev.privalo.radiantengine.util.EntityCompat;
import dev.privalo.radiantengine.util.MathUtil;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mounts and vehicles: seats on the {@code mount} (driver) and {@code p_} (passenger) bones, a fallback seat on the
 * base entity itself, and driving with per-model movement profiles from mounts.yml (GROUND, FLYING, SWIMMING,
 * AMPHIBIOUS) including banking. Living bases move with vanilla physics (velocity); non-living bases (Interaction /
 * display anchors of props and pets) move with a small kinematic solver (gravity, steps, walls, water).
 */
public final class MountManager {

    /** Per vehicle driving state. */
    private static final class DriveState {
        MountProfile.Mode mode = MountProfile.Mode.GROUND;
        boolean airFlight;
        boolean grounded = true;
        float lastYaw = Float.NaN;
        float roll;
        float tilt;
        Vector velocity = new Vector();
        Boolean gravityBefore;
        Boolean awareBefore;
    }

    private final RadiantEngine plugin;
    private final Map<UUID, SeatBehavior> riders = new HashMap<>();
    /** Riders sitting on the base entity itself (models without seat bones). */
    private final Map<UUID, RiggedEntity> baseRiders = new HashMap<>();
    private final Map<UUID, DriveState> states = new HashMap<>();
    /** Hitbox entities hidden from each rider so passengers can fight and click through their own vehicle. */
    private final Map<UUID, List<Entity>> hiddenHitboxes = new HashMap<>();
    private final Set<UUID> internalDismount = new HashSet<>();
    private final Map<String, MountProfile> profiles = new LinkedHashMap<>();
    private MountProfile defaultProfile = MountProfile.builtin(0.28, 0.55);
    private int regionCheckTicks;

    public MountManager(RadiantEngine plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ profiles

    /** Loads mounts.yml (created with examples on first run). */
    public void reload() {
        File file = new File(plugin.getDataFolder(), "mounts.yml");
        if (!file.isFile()) {
            plugin.saveResource("mounts.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        MountProfile base = MountProfile.builtin(plugin.getSettings().mountSpeed, plugin.getSettings().mountJump);
        defaultProfile = MountProfile.from(yaml.getConfigurationSection("default"), base);
        profiles.clear();
        ConfigurationSection mounts = yaml.getConfigurationSection("mounts");
        if (mounts != null) {
            for (String id : mounts.getKeys(false)) {
                profiles.put(id.toLowerCase(Locale.ROOT), MountProfile.from(mounts.getConfigurationSection(id), defaultProfile));
            }
        }
    }

    /** Profile of a model (or profile id), falling back to the default profile. */
    public MountProfile profile(String id) {
        MountProfile p = id == null ? null : profiles.get(id.toLowerCase(Locale.ROOT));
        return p != null ? p : defaultProfile;
    }

    private MountProfile profileFor(RiggedEntity me) {
        String override = plugin.getPetManager() == null ? null : plugin.getPetManager().mountProfileFor(me.getBase());
        if (override != null) {
            return profile(override);
        }
        for (ActiveRig rig : me.models()) {
            if (profiles.containsKey(rig.getId())) {
                return profiles.get(rig.getId());
            }
        }
        return defaultProfile;
    }

    public Map<String, MountProfile> getProfiles() {
        return java.util.Collections.unmodifiableMap(profiles);
    }

    // ------------------------------------------------------------------ mounting

    /**
     * Mounts an entity on a model.
     *
     * @param seatBone seat bone name, or null to pick the driver seat (if {@code driver}) or the first free seat
     * @param driver   prefer the driver seat; models without seat bones seat the driver on the base entity
     */
    public boolean mount(Entity rider, ActiveRig model, String seatBone, boolean driver) {
        if (rider instanceof Player player && !plugin.getProtection().checkMount(player, model.getOwner().getOrigin())) {
            return false;
        }
        if (rider.equals(model.getOwner().getBase())) {
            return false;
        }
        SeatBehavior target = null;
        if (seatBone != null) {
            RigBone bone = model.getBone(seatBone);
            target = bone == null ? null : bone.getBehavior(SeatBehavior.class);
        } else {
            for (SeatBehavior seat : model.getSeats()) {
                if (seat.isOccupied()) {
                    continue;
                }
                if (driver == seat.isDriver()) {
                    target = seat;
                    break;
                }
                if (target == null && !driver) {
                    target = seat;
                }
            }
            if (target == null && driver) {
                // No free "mount" bone. Passenger bones (p_) can be anywhere (e.g. a "grab" seat in front of a boss),
                // so a driver is never put on them: sit on the base entity instead and really drive it.
                if (profileFor(model.getOwner()).baseSeat() && getDriver(model.getOwner()) == null) {
                    return mountBase(rider, model.getOwner());
                }
                for (SeatBehavior seat : model.getSeats()) {
                    if (!seat.isOccupied()) {
                        target = seat;
                        break;
                    }
                }
            }
        }
        if (target == null) {
            return false;
        }
        if (target.isOccupied()) {
            return false;
        }
        dismount(rider);
        if (!target.mount(rider)) {
            return false;
        }
        riders.put(rider.getUniqueId(), target);
        hideHitboxes(rider, model.getOwner(), null);
        return true;
    }

    private boolean mountBase(Entity rider, RiggedEntity me) {
        Entity base = me.getBase();
        if (base instanceof Player || getDriver(me) != null) {
            return false;
        }
        dismount(rider);
        if (rider.isInsideVehicle()) {
            rider.leaveVehicle();
        }
        if (!base.addPassenger(rider)) {
            return false;
        }
        baseRiders.put(rider.getUniqueId(), me);
        hideHitboxes(rider, me, base);
        return true;
    }

    public boolean dismount(Entity rider) {
        SeatBehavior seat = riders.remove(rider.getUniqueId());
        RiggedEntity onBase = baseRiders.remove(rider.getUniqueId());
        if (seat == null && onBase == null) {
            return false;
        }
        internalDismount.add(rider.getUniqueId());
        try {
            if (seat != null && seat.getSeatEntity() != null) {
                seat.getSeatEntity().removePassenger(rider);
            }
            if (onBase != null) {
                onBase.getBase().removePassenger(rider);
            }
        } finally {
            internalDismount.remove(rider.getUniqueId());
        }
        showHitboxes(rider);
        if (seat != null) {
            afterLeave(seat.isDriver(), seat.getBone().getModel().getOwner());
        } else {
            afterLeave(true, onBase);
        }
        return true;
    }

    public void dismountAll(ActiveRig model) {
        for (SeatBehavior seat : model.getSeats()) {
            Entity rider = seat.getRider();
            if (rider != null) {
                riders.remove(rider.getUniqueId());
                showHitboxes(rider);
                internalDismount.add(rider.getUniqueId());
                try {
                    seat.dismount();
                } finally {
                    internalDismount.remove(rider.getUniqueId());
                }
            }
        }
        RiggedEntity me = model.getOwner();
        for (Map.Entry<UUID, RiggedEntity> e : new ArrayList<>(baseRiders.entrySet())) {
            if (e.getValue() == me) {
                baseRiders.remove(e.getKey());
                Entity rider = org.bukkit.Bukkit.getEntity(e.getKey());
                if (rider != null) {
                    showHitboxes(rider);
                    internalDismount.add(rider.getUniqueId());
                    try {
                        me.getBase().removePassenger(rider);
                    } finally {
                        internalDismount.remove(rider.getUniqueId());
                    }
                }
            }
        }
        releaseControl(me);
    }

    /** Called from the dismount listener when a rider leaves a seat on its own. */
    public void handleDismount(Entity rider, Entity vehicle) {
        if (internalDismount.contains(rider.getUniqueId()) || EntityCompat.isRemounting(rider)) {
            return;
        }
        SeatBehavior seat = riders.get(rider.getUniqueId());
        if (seat != null && seat.getSeatEntity() != null && seat.getSeatEntity().equals(vehicle)) {
            riders.remove(rider.getUniqueId());
            showHitboxes(rider);
            afterLeave(seat.isDriver(), seat.getBone().getModel().getOwner());
            return;
        }
        RiggedEntity onBase = baseRiders.get(rider.getUniqueId());
        if (onBase != null && onBase.getBase().equals(vehicle)) {
            baseRiders.remove(rider.getUniqueId());
            showHitboxes(rider);
            afterLeave(true, onBase);
        }
    }

    /**
     * True when a sneak-dismount must be cancelled: the driver of a flying / swimming mount with
     * {@code shift-action: DESCEND} uses sneak to go down instead (dismounting is allowed once on the ground).
     */
    public boolean shouldBlockDismount(Entity rider, Entity vehicle) {
        if (!(rider instanceof Player player) || internalDismount.contains(rider.getUniqueId())
                || EntityCompat.isRemounting(rider) || player.isDead() || !player.isOnline()
                || !EntityCompat.hasInputApi()) {
            return false;
        }
        RiggedEntity me = drivenBy(player);
        if (me == null) {
            return false;
        }
        DriveState state = states.get(me.getBase().getUniqueId());
        if (state == null || state.grounded || state.mode == MountProfile.Mode.GROUND) {
            return false;
        }
        if (profileFor(me).shiftAction() != MountProfile.ShiftAction.DESCEND) {
            return false;
        }
        return InputReader.read(player)[4] > 0;
    }

    public boolean isRemountInProgress(Entity rider) {
        return internalDismount.contains(rider.getUniqueId()) || EntityCompat.isRemounting(rider);
    }

    private void afterLeave(boolean wasDriver, RiggedEntity me) {
        if (wasDriver && me != null) {
            releaseControl(me);
        }
    }

    private void hideHitboxes(Entity rider, RiggedEntity me, Entity vehicle) {
        if (!(rider instanceof Player player)) {
            return;
        }
        List<Entity> list = me.hitboxEntities();
        if (vehicle != null) {
            list.remove(vehicle);
        }
        for (Entity e : list) {
            player.hideEntity(plugin, e);
        }
        hiddenHitboxes.put(player.getUniqueId(), list);
    }

    private void showHitboxes(Entity rider) {
        List<Entity> list = hiddenHitboxes.remove(rider.getUniqueId());
        if (list == null || !(rider instanceof Player player)) {
            return;
        }
        for (Entity e : list) {
            if (e.isValid()) {
                player.showEntity(plugin, e);
            }
        }
    }

    // ------------------------------------------------------------------ queries

    public SeatBehavior getSeat(Entity rider) {
        return riders.get(rider.getUniqueId());
    }

    /** True if the rider drives a vehicle (driver seat or base seat). */
    public boolean isDriver(Entity rider) {
        SeatBehavior seat = riders.get(rider.getUniqueId());
        return seat != null ? seat.isDriver() : baseRiders.containsKey(rider.getUniqueId());
    }

    public ActiveRig getMountedModel(Entity rider) {
        SeatBehavior seat = riders.get(rider.getUniqueId());
        if (seat != null) {
            return seat.getBone().getModel();
        }
        RiggedEntity me = baseRiders.get(rider.getUniqueId());
        return me == null || me.isEmpty() ? null : me.models().iterator().next();
    }

    /** Rigged entity the rider sits on (seat or base), or null. */
    public RiggedEntity getRiddenEntity(Entity rider) {
        SeatBehavior seat = riders.get(rider.getUniqueId());
        if (seat != null) {
            return seat.getBone().getModel().getOwner();
        }
        return baseRiders.get(rider.getUniqueId());
    }

    public Entity getDriver(ActiveRig model) {
        for (SeatBehavior seat : model.getSeats()) {
            if (seat.isDriver() && seat.getRider() != null) {
                return seat.getRider();
            }
        }
        return getDriver(model.getOwner(), false);
    }

    private Entity getDriver(RiggedEntity me) {
        return getDriver(me, true);
    }

    private Entity getDriver(RiggedEntity me, boolean includeSeats) {
        if (includeSeats) {
            for (ActiveRig model : me.models()) {
                for (SeatBehavior seat : model.getSeats()) {
                    if (seat.isDriver() && seat.getRider() != null) {
                        return seat.getRider();
                    }
                }
            }
        }
        for (Map.Entry<UUID, RiggedEntity> e : baseRiders.entrySet()) {
            if (e.getValue() == me) {
                Entity rider = org.bukkit.Bukkit.getEntity(e.getKey());
                if (rider != null && me.getBase().getPassengers().contains(rider)) {
                    return rider;
                }
            }
        }
        return null;
    }

    private RiggedEntity drivenBy(Player player) {
        SeatBehavior seat = riders.get(player.getUniqueId());
        if (seat != null) {
            return seat.isDriver() ? seat.getBone().getModel().getOwner() : null;
        }
        return baseRiders.get(player.getUniqueId());
    }

    public List<Entity> getPassengers(ActiveRig model) {
        List<Entity> list = new ArrayList<>();
        for (SeatBehavior seat : model.getSeats()) {
            Entity rider = seat.getRider();
            if (rider != null) {
                list.add(rider);
            }
        }
        for (Map.Entry<UUID, RiggedEntity> e : baseRiders.entrySet()) {
            if (e.getValue() == model.getOwner()) {
                Entity rider = org.bukkit.Bukkit.getEntity(e.getKey());
                if (rider != null) {
                    list.add(rider);
                }
            }
        }
        return list;
    }

    /** Movement mode used in the last tick by the vehicle, or null if it is not being driven. */
    public MountProfile.Mode getMode(RiggedEntity me) {
        DriveState state = states.get(me.getBase().getUniqueId());
        return state == null ? null : state.mode;
    }

    /** True when the vehicle moved horizontally in the last driven tick. */
    public boolean isMoving(RiggedEntity me) {
        DriveState state = states.get(me.getBase().getUniqueId());
        return state != null && (state.velocity.getX() * state.velocity.getX() + state.velocity.getZ() * state.velocity.getZ()) > 0.0004;
    }

    // ------------------------------------------------------------------ driving

    /** Applies the driver's input to the base entity. Called every tick before the model is updated. */
    public void drive(RiggedEntity me) {
        Entity base = me.getBase();
        if (base instanceof Player) {
            return;
        }
        Entity d = getDriver(me);
        if (!(d instanceof Player driver)) {
            releaseControl(me);
            return;
        }
        if (++regionCheckTicks >= 20) {
            regionCheckTicks = 0;
            if (!plugin.getProtection().checkMount(driver, base.getLocation())) {
                dismount(driver);
                releaseControl(me);
                return;
            }
        }
        MountProfile profile = profileFor(me);
        DriveState state = states.computeIfAbsent(base.getUniqueId(), k -> new DriveState());
        if (base instanceof Mob mob && state.awareBefore == null) {
            state.awareBefore = mob.isAware();
            mob.setAware(false);
        }
        if (state.gravityBefore == null) {
            state.gravityBefore = base.hasGravity();
        }

        Location driverLoc = driver.getLocation();
        float yaw = driverLoc.getYaw();
        float pitch = driverLoc.getPitch();
        double forward = 0;
        double strafe = 0;
        boolean jump = false;
        boolean sprint = false;
        boolean sneak = false;
        if (EntityCompat.hasInputApi()) {
            double[] input = InputReader.read(driver);
            forward = input[0];
            strafe = input[1];
            jump = input[2] > 0;
            sprint = input[3] > 0;
            sneak = input[4] > 0;
        } else if (pitch < 45f) {
            // Pre-1.21.2 servers expose no movement input: move where the driver looks, look down to stop.
            forward = 1;
        }

        Location loc = base.getLocation();
        boolean inWater = isWater(loc) || isWater(loc.clone().add(0, 0.6, 0));
        boolean living = base instanceof LivingEntity;
        state.grounded = living ? base.isOnGround() : groundBelow(loc, 0.06) != null;
        state.mode = resolveMode(profile, state, inWater, jump);

        double rad = Math.toRadians(yaw);
        Vector fwd = new Vector(-Math.sin(rad), 0, Math.cos(rad));
        Vector left = new Vector(Math.cos(rad), 0, Math.sin(rad));
        Vector move;
        double vertical = 0;
        boolean verticalInput = false;
        switch (state.mode) {
            case FLYING, SWIMMING -> {
                double speed = state.mode == MountProfile.Mode.FLYING ? profile.flySpeed() : profile.swimSpeed();
                Vector look = driverLoc.getDirection();
                move = look.multiply(forward).add(left.multiply(strafe));
                if (move.lengthSquared() > 1.0E-6) {
                    move.normalize().multiply(speed * (sprint ? profile.sprintMultiplier() : 1.0));
                }
                if (jump) {
                    vertical = profile.verticalSpeed();
                    verticalInput = true;
                } else if (sneak && profile.shiftAction() == MountProfile.ShiftAction.DESCEND) {
                    vertical = -profile.verticalSpeed();
                    verticalInput = true;
                }
            }
            default -> {
                move = fwd.multiply(forward).add(left.multiply(strafe));
                if (move.lengthSquared() > 1.0E-6) {
                    double speed = profile.speed() * (sprint ? profile.sprintMultiplier() : 1.0);
                    if (profile.movement() == MountProfile.Movement.SWIMMING) {
                        speed *= 0.35;
                    }
                    move.normalize().multiply(speed);
                }
            }
        }

        if (living) {
            driveLiving(base, profile, state, move, vertical, verticalInput, jump, inWater, yaw);
        } else {
            driveKinematic(base, profile, state, move, vertical, verticalInput, jump, inWater, yaw);
        }
        bank(me, profile, state, yaw, pitch);
    }

    private MountProfile.Mode resolveMode(MountProfile profile, DriveState state, boolean inWater, boolean jump) {
        return switch (profile.movement()) {
            case FLYING -> MountProfile.Mode.FLYING;
            case SWIMMING -> inWater ? MountProfile.Mode.SWIMMING : MountProfile.Mode.GROUND;
            case GROUND -> MountProfile.Mode.GROUND;
            case AMPHIBIOUS -> {
                if (inWater) {
                    state.airFlight = false;
                    yield MountProfile.Mode.SWIMMING;
                }
                if (!state.grounded && jump) {
                    state.airFlight = true;
                }
                if (state.grounded && !jump) {
                    state.airFlight = false;
                }
                yield state.airFlight ? MountProfile.Mode.FLYING : MountProfile.Mode.GROUND;
            }
        };
    }

    private void driveLiving(Entity base, MountProfile profile, DriveState state, Vector move, double vertical,
                             boolean verticalInput, boolean jump, boolean inWater, float yaw) {
        Vector current = base.getVelocity();
        double vy;
        switch (state.mode) {
            case FLYING -> {
                base.setGravity(false);
                vy = verticalInput ? vertical : move.getY() + current.getY() * 0.6;
            }
            case SWIMMING -> {
                base.setGravity(false);
                if (verticalInput) {
                    vy = vertical;
                } else {
                    vy = move.getY() + Math.min(current.getY() * 0.6 + profile.buoyancy(), 0.1);
                    if (!isWater(base.getLocation().add(0, 1.0, 0)) && vy > 0) {
                        vy = 0;
                    }
                }
            }
            default -> {
                base.setGravity(state.gravityBefore == null || state.gravityBefore);
                vy = current.getY();
                if (jump && state.grounded) {
                    vy = profile.jumpStrength();
                } else if (state.grounded && needsStep(base.getLocation(), move, profile.stepHeight())) {
                    vy = Math.max(vy, 0.42 + Math.max(0, profile.stepHeight() - 1) * 0.12);
                }
            }
        }
        Vector velocity = new Vector(move.getX(), vy, move.getZ());
        if (state.mode != MountProfile.Mode.GROUND && move.lengthSquared() < 1.0E-6) {
            velocity.setX(current.getX() * 0.8);
            velocity.setZ(current.getZ() * 0.8);
        }
        base.setVelocity(velocity);
        state.velocity = velocity;
        base.setRotation(yaw, 0f);
        EntityCompat.setBodyYaw(base, yaw);
    }

    private void driveKinematic(Entity base, MountProfile profile, DriveState state, Vector move, double vertical,
                                boolean verticalInput, boolean jump, boolean inWater, float yaw) {
        Location loc = base.getLocation();
        World world = loc.getWorld();
        Vector v = state.velocity;
        double vy;
        switch (state.mode) {
            case FLYING -> vy = verticalInput ? vertical : move.getY() + v.getY() * 0.6;
            case SWIMMING -> {
                if (verticalInput) {
                    vy = vertical;
                } else {
                    vy = move.getY() + Math.min(v.getY() * 0.6 + profile.buoyancy(), 0.1);
                    if (!isWater(loc.clone().add(0, 1.0, 0)) && vy > 0) {
                        vy = 0;
                    }
                }
            }
            default -> {
                if (state.grounded) {
                    vy = jump ? profile.jumpStrength() : 0;
                } else {
                    vy = Math.max(-profile.maxFallSpeed(), v.getY() - profile.gravity());
                }
            }
        }
        double vx = move.getX();
        double vz = move.getZ();
        if (state.mode != MountProfile.Mode.GROUND && move.lengthSquared() < 1.0E-6) {
            vx = v.getX() * 0.8;
            vz = v.getZ() * 0.8;
        }

        // horizontal collision with step-up
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        if (Math.abs(vx) > 1.0E-6 || Math.abs(vz) > 1.0E-6) {
            Location ahead = new Location(world, x + vx + Math.signum(vx) * 0.3, y, z + vz + Math.signum(vz) * 0.3);
            if (solid(ahead) || solid(ahead.clone().add(0, 1, 0))) {
                Location stepTo = ahead.clone();
                int step = (int) Math.ceil(profile.stepHeight());
                boolean stepped = false;
                if (state.mode == MountProfile.Mode.GROUND && state.grounded && step > 0) {
                    for (int s = 1; s <= step; s++) {
                        Location candidate = stepTo.clone().add(0, s, 0);
                        if (!solid(candidate) && !solid(candidate.clone().add(0, 1, 0)) && solid(candidate.clone().add(0, -1, 0))) {
                            y = Math.floor(y) + s;
                            stepped = true;
                            break;
                        }
                    }
                }
                if (!stepped) {
                    vx = 0;
                    vz = 0;
                }
            }
        }
        x += vx;
        z += vz;

        // vertical collision
        double ny = y + vy;
        if (vy < 0) {
            Double ground = groundBelow(new Location(world, x, y, z), -vy + 0.06);
            if (ground != null && ny <= ground) {
                ny = ground;
                vy = 0;
                state.grounded = true;
            }
        } else if (vy > 0 && solid(new Location(world, x, ny + 1.8, z))) {
            ny = y;
            vy = 0;
        }
        state.velocity = new Vector(vx, vy, vz);
        Location next = new Location(world, x, ny, z, yaw, 0f);
        EntityCompat.teleportWithPassengers(base, next);
    }

    private void bank(RiggedEntity me, MountProfile profile, DriveState state, float yaw, float pitch) {
        float yawDelta = Float.isNaN(state.lastYaw) ? 0f : MathUtil.wrapDegrees(yaw - state.lastYaw);
        state.lastYaw = yaw;
        float smoothing = (float) profile.smoothing();
        float targetRoll = 0f;
        if (profile.banking()) {
            float max = (float) profile.bankingMaxAngle();
            targetRoll = MathUtil.clamp((float) (yawDelta * profile.bankingIntensity() * 2.5), -max, max);
        }
        float targetTilt = state.mode == MountProfile.Mode.GROUND ? 0f
                : MathUtil.clamp((float) (pitch * profile.pitchFollow()), -60f, 60f);
        state.roll += (targetRoll - state.roll) * smoothing;
        state.tilt += (targetTilt - state.tilt) * smoothing;
        for (ActiveRig rig : me.models()) {
            rig.setRootRoll(state.roll);
            rig.setRootTilt(state.tilt);
        }
    }

    // ------------------------------------------------------------------ world helpers

    private static boolean isWater(Location loc) {
        if (loc.getWorld() == null) {
            return false;
        }
        Block block = loc.getBlock();
        if (block.getType() == Material.WATER || block.getType() == Material.BUBBLE_COLUMN) {
            return true;
        }
        return block.getBlockData() instanceof Waterlogged w && w.isWaterlogged();
    }

    private static boolean solid(Location loc) {
        return loc.getWorld() != null && !loc.getBlock().isPassable();
    }

    /** Top of the ground within {@code distance} below the location, or null. */
    private static Double groundBelow(Location loc, double distance) {
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        Location start = loc.clone().add(0, 0.05, 0);
        RayTraceResult hit = world.rayTraceBlocks(start, new Vector(0, -1, 0), distance + 0.05, FluidCollisionMode.NEVER, true);
        return hit == null ? null : hit.getHitPosition().getY();
    }

    private static boolean needsStep(Location loc, Vector move, double stepHeight) {
        if (stepHeight < 0.75 || move.lengthSquared() < 1.0E-4) {
            return false;
        }
        Vector dir = move.clone().setY(0).normalize().multiply(0.7);
        Location ahead = loc.clone().add(dir);
        return solid(ahead) && !solid(ahead.clone().add(0, 1, 0)) && !solid(ahead.clone().add(0, 2, 0));
    }

    /** Restores AI, gravity and model orientation of a base entity that was being driven. */
    public void releaseControl(RiggedEntity me) {
        DriveState state = states.remove(me.getBase().getUniqueId());
        if (state == null) {
            return;
        }
        Entity base = me.getBase();
        if (base.isValid()) {
            if (state.awareBefore != null && base instanceof Mob mob) {
                mob.setAware(state.awareBefore);
            }
            if (state.gravityBefore != null) {
                base.setGravity(state.gravityBefore);
            }
        }
        for (ActiveRig rig : me.models()) {
            rig.setRootRoll(0f);
            rig.setRootTilt(0f);
        }
    }

    public void shutdown() {
        for (UUID rider : new ArrayList<>(riders.keySet())) {
            SeatBehavior seat = riders.remove(rider);
            if (seat != null) {
                seat.dismount();
            }
        }
        for (Map.Entry<UUID, RiggedEntity> e : new ArrayList<>(baseRiders.entrySet())) {
            Entity rider = org.bukkit.Bukkit.getEntity(e.getKey());
            if (rider != null) {
                e.getValue().getBase().removePassenger(rider);
                showHitboxes(rider);
            }
        }
        baseRiders.clear();
        for (UUID id : new ArrayList<>(states.keySet())) {
            Entity base = org.bukkit.Bukkit.getEntity(id);
            if (base != null) {
                RiggedEntity me = plugin.getRigManager().get(base);
                if (me != null) {
                    releaseControl(me);
                }
            }
        }
        states.clear();
    }
}
