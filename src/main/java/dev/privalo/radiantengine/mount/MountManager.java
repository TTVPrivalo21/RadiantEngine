package dev.privalo.radiantengine.mount;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.model.ActiveRig;
import dev.privalo.radiantengine.model.RiggedEntity;
import dev.privalo.radiantengine.model.bone.RigBone;
import dev.privalo.radiantengine.model.bone.SeatBehavior;
import dev.privalo.radiantengine.util.EntityCompat;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Seats on p_ / mount bones and WASD + jump driving of the base entity by the rider of the "mount" bone.
 */
public final class MountManager {

    private final RadiantEngine plugin;
    private final Map<UUID, SeatBehavior> riders = new HashMap<>();
    /** Base entities whose AI was disabled while driven, with their previous awareness. */
    private final Map<UUID, Boolean> controlled = new HashMap<>();
    private final Set<UUID> internalDismount = new HashSet<>();
    private int regionCheckTicks;

    public MountManager(RadiantEngine plugin) {
        this.plugin = plugin;
    }

    /**
     * Mounts an entity on a model seat.
     *
     * @param seatBone seat bone name, or null to pick the driver seat (if {@code driver}) or the first free seat
     */
    public boolean mount(Entity rider, ActiveRig model, String seatBone, boolean driver) {
        if (rider instanceof Player player && !plugin.getProtection().checkMount(player, model.getOwner().getOrigin())) {
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
                for (SeatBehavior seat : model.getSeats()) {
                    if (!seat.isOccupied()) {
                        target = seat;
                        break;
                    }
                }
            }
        }
        if (target == null || target.isOccupied()) {
            return false;
        }
        dismount(rider);
        if (!target.mount(rider)) {
            return false;
        }
        riders.put(rider.getUniqueId(), target);
        return true;
    }

    public boolean dismount(Entity rider) {
        SeatBehavior seat = riders.remove(rider.getUniqueId());
        if (seat == null) {
            return false;
        }
        internalDismount.add(rider.getUniqueId());
        try {
            if (seat.getSeatEntity() != null) {
                seat.getSeatEntity().removePassenger(rider);
            }
        } finally {
            internalDismount.remove(rider.getUniqueId());
        }
        afterLeave(seat);
        return true;
    }

    public void dismountAll(ActiveRig model) {
        for (SeatBehavior seat : model.getSeats()) {
            Entity rider = seat.getRider();
            if (rider != null) {
                riders.remove(rider.getUniqueId());
                internalDismount.add(rider.getUniqueId());
                try {
                    seat.dismount();
                } finally {
                    internalDismount.remove(rider.getUniqueId());
                }
            }
        }
        releaseControl(model.getOwner());
    }

    /** Called from the dismount listener when a rider leaves a seat on its own. */
    public void handleDismount(Entity rider, Entity vehicle) {
        if (internalDismount.contains(rider.getUniqueId()) || EntityCompat.isRemounting(rider)) {
            return;
        }
        SeatBehavior seat = riders.get(rider.getUniqueId());
        if (seat != null && seat.getSeatEntity() != null && seat.getSeatEntity().equals(vehicle)) {
            riders.remove(rider.getUniqueId());
            afterLeave(seat);
        }
    }

    public boolean isRemountInProgress(Entity rider) {
        return internalDismount.contains(rider.getUniqueId()) || EntityCompat.isRemounting(rider);
    }

    private void afterLeave(SeatBehavior seat) {
        if (seat.isDriver()) {
            releaseControl(seat.getBone().getModel().getOwner());
        }
    }

    public SeatBehavior getSeat(Entity rider) {
        return riders.get(rider.getUniqueId());
    }

    public ActiveRig getMountedModel(Entity rider) {
        SeatBehavior seat = riders.get(rider.getUniqueId());
        return seat == null ? null : seat.getBone().getModel();
    }

    public Entity getDriver(ActiveRig model) {
        for (SeatBehavior seat : model.getSeats()) {
            if (seat.isDriver() && seat.getRider() != null) {
                return seat.getRider();
            }
        }
        return null;
    }

    public List<Entity> getPassengers(ActiveRig model) {
        List<Entity> list = new ArrayList<>();
        for (SeatBehavior seat : model.getSeats()) {
            Entity rider = seat.getRider();
            if (rider != null) {
                list.add(rider);
            }
        }
        return list;
    }

    /** Applies the driver's input to the base entity. Called every tick before the model is updated. */
    public void drive(RiggedEntity me) {
        Entity base = me.getBase();
        if (base instanceof Player) {
            return;
        }
        Player driver = null;
        for (ActiveRig model : me.models()) {
            Entity d = getDriver(model);
            if (d instanceof Player p) {
                driver = p;
                break;
            }
        }
        if (driver == null) {
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
        if (base instanceof Mob mob && !controlled.containsKey(base.getUniqueId())) {
            controlled.put(base.getUniqueId(), mob.isAware());
            mob.setAware(false);
        } else if (!controlled.containsKey(base.getUniqueId())) {
            controlled.put(base.getUniqueId(), Boolean.TRUE);
        }

        float yaw = driver.getLocation().getYaw();
        double forward = 0;
        double strafe = 0;
        boolean jump = false;
        if (EntityCompat.hasInputApi()) {
            double[] input = InputReader.read(driver);
            forward = input[0];
            strafe = input[1];
            jump = input[2] > 0;
            if (input[3] > 0) {
                forward *= 1.3;
            }
        } else if (driver.getLocation().getPitch() < 45f) {
            // Pre-1.21.2 servers expose no movement input: walk where the driver looks, look down to stop.
            forward = 1;
        }
        double rad = Math.toRadians(yaw);
        Vector fwd = new Vector(-Math.sin(rad), 0, Math.cos(rad));
        Vector left = new Vector(Math.cos(rad), 0, Math.sin(rad));
        Vector move = fwd.multiply(forward).add(left.multiply(strafe));
        if (move.lengthSquared() > 1.0E-6) {
            double len = Math.min(1.3, move.length());
            move.normalize().multiply(plugin.getSettings().mountSpeed * len);
        }
        if (base instanceof LivingEntity) {
            Vector velocity = base.getVelocity();
            double vy = velocity.getY();
            if (jump && base.isOnGround()) {
                vy = plugin.getSettings().mountJump;
            }
            base.setVelocity(new Vector(move.getX(), vy, move.getZ()));
            base.setRotation(yaw, 0f);
            EntityCompat.setBodyYaw(base, yaw);
        } else {
            Location loc = base.getLocation();
            loc.add(move.getX(), jump ? 0.15 : 0, move.getZ());
            loc.setYaw(yaw);
            EntityCompat.teleportWithPassengers(base, loc);
        }
    }

    /** Restores AI of a base entity that was being driven. */
    public void releaseControl(RiggedEntity me) {
        Boolean aware = controlled.remove(me.getBase().getUniqueId());
        if (aware != null && me.getBase() instanceof Mob mob && mob.isValid()) {
            mob.setAware(aware);
        }
    }

    public void shutdown() {
        for (UUID rider : new ArrayList<>(riders.keySet())) {
            SeatBehavior seat = riders.remove(rider);
            if (seat != null) {
                seat.dismount();
            }
        }
        controlled.clear();
    }
}
