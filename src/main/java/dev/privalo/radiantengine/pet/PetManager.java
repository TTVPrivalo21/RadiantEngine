package dev.privalo.radiantengine.pet;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.access.AccessList;
import dev.privalo.radiantengine.blueprint.RigBlueprint;
import dev.privalo.radiantengine.gui.CollectionMenu;
import dev.privalo.radiantengine.model.ActiveRig;
import dev.privalo.radiantengine.mount.MountProfile;
import dev.privalo.radiantengine.text.Lang;
import dev.privalo.radiantengine.util.EntityCompat;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 3D pets. Definitions, direct per-user assignments and the equipped pet of every player live in pets.yml.
 * Access: permission {@code radiantengine.pet.<id>} / {@code radiantengine.pet.*} or a direct assignment.
 */
public final class PetManager implements Listener, CollectionMenu.Source {

    private final RadiantEngine plugin;
    private final File file;
    private final Map<String, PetDefinition> definitions = new LinkedHashMap<>();
    private final AccessList access = new AccessList();
    private final Map<UUID, String> equipped = new LinkedHashMap<>();
    private final Map<UUID, ActivePet> active = new HashMap<>();
    private final Map<UUID, UUID> anchors = new HashMap<>();

    private PetDefinition.Defaults defaults;
    private boolean onlyDefined;
    private double teleportDistance;
    private double stopDistance;
    private boolean hideWhileDisguised;
    private boolean rideInteract;
    private boolean allowPassengers;
    private String animIdle;
    private String animWalk;
    private String animSprint;
    private String animJump;
    private String animFly;
    private String animOwnerAttack;
    private String animOwnerHurt;

    public PetManager(RadiantEngine plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pets.yml");
    }

    // ------------------------------------------------------------------ lifecycle

    public void enable() {
        if (!file.isFile()) {
            plugin.saveResource("pets.yml", false);
        }
        reload();
        plugin.getRigManager().addPreTickHook(this::tick);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Re-reads config.yml pet settings and pets.yml, then respawns every pet. */
    public void reload() {
        FileConfiguration c = plugin.getConfig();
        defaults = new PetDefinition.Defaults(
                PetDefinition.Movement.parse(c.getString("pets.default-movement"), PetDefinition.Movement.WALK),
                c.getDouble("pets.speed", 0.24),
                c.getDouble("pets.sprint-speed", 0.42),
                c.getDouble("pets.follow-distance", 2.2),
                c.getDouble("pets.fly-height", 1.5),
                c.getBoolean("pets.ride.default-rideable", false));
        rideInteract = c.getBoolean("pets.ride.interact", true);
        allowPassengers = c.getBoolean("pets.ride.allow-passengers", true);
        onlyDefined = c.getBoolean("pets.only-defined", false);
        teleportDistance = Math.max(4, c.getDouble("pets.teleport-distance", 24));
        stopDistance = Math.max(0.3, c.getDouble("pets.stop-distance", 1.2));
        hideWhileDisguised = c.getBoolean("pets.hide-while-disguised", false);
        animIdle = c.getString("pets.animations.idle", "idle");
        animWalk = c.getString("pets.animations.walk", "walk");
        animSprint = c.getString("pets.animations.sprint", "sprint");
        animJump = c.getString("pets.animations.jump", "jump");
        animFly = c.getString("pets.animations.fly", "fly");
        animOwnerAttack = c.getString("pets.animations.owner-attack", "owner_attack");
        animOwnerHurt = c.getString("pets.animations.owner-hurt", "owner_hurt");

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        definitions.clear();
        ConfigurationSection defs = yaml.getConfigurationSection("pets");
        if (defs != null) {
            for (String id : defs.getKeys(false)) {
                ConfigurationSection s = defs.getConfigurationSection(id);
                if (s != null) {
                    PetDefinition d = PetDefinition.fromSection(id, s, defaults);
                    definitions.put(d.id(), d);
                }
            }
        }
        access.load(yaml.getConfigurationSection("assignments"));
        equipped.clear();
        ConfigurationSection eq = yaml.getConfigurationSection("equipped");
        if (eq != null) {
            for (String key : eq.getKeys(false)) {
                try {
                    equipped.put(UUID.fromString(key), eq.getString(key, "").toLowerCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    // invalid uuid key, skipped
                }
            }
        }
        respawnAll();
    }

    public void respawnAll() {
        for (UUID owner : new ArrayList<>(active.keySet())) {
            despawn(owner);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            restore(p);
        }
    }

    public void shutdown() {
        for (UUID owner : new ArrayList<>(active.keySet())) {
            despawn(owner);
        }
    }

    private void save() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("assignments", null);
        access.save(yaml.createSection("assignments"));
        yaml.set("equipped", null);
        ConfigurationSection eq = yaml.createSection("equipped");
        equipped.forEach((k, v) -> eq.set(k.toString(), v));
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar pets.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ access

    /** Pet definition for an id (defined in pets.yml, or implicit from a model when allowed). */
    public PetDefinition definition(String id) {
        if (id == null) {
            return null;
        }
        String key = id.toLowerCase(Locale.ROOT);
        PetDefinition d = definitions.get(key);
        if (d != null) {
            return d;
        }
        if (!onlyDefined && plugin.getRegistry().contains(key)) {
            return PetDefinition.implicit(key, defaults);
        }
        return null;
    }

    public boolean hasAccess(Player player, String id) {
        String key = id.toLowerCase(Locale.ROOT);
        return player.hasPermission("radiantengine.pet." + key) || player.hasPermission("radiantengine.pet.*")
                || access.has(player, key);
    }

    /** Pets the player can summon (defined pets first, then other models). */
    public List<String> available(Player player) {
        Set<String> ids = new LinkedHashSet<>(definitions.keySet());
        ids.addAll(access.grantedTo(player));
        if (!onlyDefined) {
            ids.addAll(plugin.getRegistry().all().keySet());
        }
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            PetDefinition d = definition(id);
            if (d != null && plugin.getRegistry().contains(d.model()) && hasAccess(player, id)) {
                out.add(id);
            }
        }
        return out;
    }

    public Collection<String> definedIds() {
        return Collections.unmodifiableCollection(definitions.keySet());
    }

    public int assign(Collection<String> targets, String id) {
        int n = access.grant(targets, id);
        save();
        return n;
    }

    public int unassign(Collection<String> targets, String id) {
        int n = access.revoke(targets, id);
        save();
        for (Player p : Bukkit.getOnlinePlayers()) {
            String eq = equipped.get(p.getUniqueId());
            if (eq != null && eq.equals(id.toLowerCase(Locale.ROOT)) && !hasAccess(p, eq)) {
                dismiss(p);
            }
        }
        return n;
    }

    public Map<String, Set<String>> assignments() {
        return access.view();
    }

    // ------------------------------------------------------------------ summon / dismiss

    public enum Result {
        SUCCESS,
        UNKNOWN,
        NO_ACCESS,
        FAILED
    }

    public Result summon(Player player, String id) {
        PetDefinition def = definition(id);
        if (def == null || !plugin.getRegistry().contains(def.model())) {
            return Result.UNKNOWN;
        }
        if (!hasAccess(player, def.id())) {
            return Result.NO_ACCESS;
        }
        despawn(player.getUniqueId());
        equipped.put(player.getUniqueId(), def.id());
        save();
        return spawn(player, def) ? Result.SUCCESS : Result.FAILED;
    }

    public boolean dismiss(Player player) {
        boolean had = equipped.remove(player.getUniqueId()) != null;
        despawn(player.getUniqueId());
        if (had) {
            save();
        }
        return had;
    }

    public ActivePet getPet(Player player) {
        return active.get(player.getUniqueId());
    }

    public String getEquipped(Player player) {
        return equipped.get(player.getUniqueId());
    }

    // ------------------------------------------------------------------ riding

    public enum RideResult {
        SUCCESS,
        NO_PET,
        NOT_RIDEABLE,
        NO_ACCESS,
        NO_SEAT
    }

    private boolean isRidden(ActivePet pet) {
        return plugin.getMountManager().getDriver(pet.rig) != null;
    }

    /** mounts.yml profile used when this pet anchor is driven (null if the entity is not a pet). */
    public String mountProfileFor(Entity base) {
        UUID owner = base == null ? null : anchors.get(base.getUniqueId());
        ActivePet pet = owner == null ? null : active.get(owner);
        if (pet == null) {
            return null;
        }
        PetDefinition def = pet.getDefinition();
        return def.mountProfile() != null ? def.mountProfile() : def.model();
    }

    /** Mounts the owner on their summoned pet (it switches to vehicle mode until they get off). */
    public RideResult ride(Player player) {
        ActivePet pet = active.get(player.getUniqueId());
        if (pet == null || !pet.isValid()) {
            return RideResult.NO_PET;
        }
        PetDefinition def = pet.getDefinition();
        if (!def.rideable()) {
            return RideResult.NOT_RIDEABLE;
        }
        if (!hasAccess(player, def.id()) || !player.hasPermission("radiantengine.pet.ride")) {
            return RideResult.NO_ACCESS;
        }
        if (plugin.getMountManager().getRiddenEntity(player) == pet.rig.getOwner()) {
            return RideResult.SUCCESS;
        }
        return plugin.getMountManager().mount(player, pet.rig, null, true) ? RideResult.SUCCESS : RideResult.NO_SEAT;
    }

    /** Summons the pet if needed and mounts the owner on it. */
    public RideResult ride(Player player, String id) {
        if (id != null && !id.equalsIgnoreCase(equipped.get(player.getUniqueId()))) {
            Result r = summon(player, id);
            if (r == Result.NO_ACCESS) {
                return RideResult.NO_ACCESS;
            }
            if (r != Result.SUCCESS) {
                return RideResult.NO_PET;
            }
        } else if (active.get(player.getUniqueId()) == null) {
            restore(player);
        }
        return ride(player);
    }

    /** Sends the localized result of a ride attempt. */
    public void sendRideResult(Player player, RideResult result) {
        String id = equipped.get(player.getUniqueId());
        switch (result) {
            case SUCCESS -> plugin.getLang().send(player, "pet-riding", "pet", id == null ? "-" : id);
            case NO_PET -> plugin.getLang().send(player, "pet-none");
            case NOT_RIDEABLE -> plugin.getLang().send(player, "pet-not-rideable", "pet", id == null ? "-" : id);
            case NO_ACCESS -> plugin.getLang().send(player, "pet-no-access", "pet", id == null ? "-" : id);
            case NO_SEAT -> plugin.getLang().send(player, "pet-no-seat", "pet", id == null ? "-" : id);
        }
    }

    private void animateRidden(ActivePet pet) {
        MountProfile.Mode mode = plugin.getMountManager().getMode(pet.rig.getOwner());
        boolean moving = plugin.getMountManager().isMoving(pet.rig.getOwner());
        ActiveRig rig = pet.rig;
        if (mode == MountProfile.Mode.FLYING) {
            pet.animator.setLoop(rig, animFly, moving ? animWalk : animIdle, animIdle);
        } else if (mode == MountProfile.Mode.SWIMMING) {
            pet.animator.setLoop(rig, "swim", animFly, moving ? animWalk : animIdle, animIdle);
        } else if (moving) {
            pet.animator.setLoop(rig, animWalk, animIdle);
        } else {
            pet.animator.setLoop(rig, animIdle);
        }
    }

    /** Right click on a pet: the owner rides it, other players take a passenger seat. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPetInteract(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        dev.privalo.radiantengine.model.RiggedEntity rigged = plugin.getRigManager().resolve(event.getRightClicked());
        if (rigged == null || !isPetBase(rigged.getBase())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        UUID owner = anchors.get(rigged.getBase().getUniqueId());
        ActivePet pet = owner == null ? null : active.get(owner);
        if (pet == null) {
            return;
        }
        if (player.getUniqueId().equals(owner)) {
            if (rideInteract && pet.getDefinition().rideable()) {
                sendRideResult(player, ride(player));
            }
        } else if (allowPassengers && pet.getDefinition().rideable() && isRidden(pet)) {
            if (plugin.getMountManager().mount(player, pet.rig, null, false)) {
                plugin.getLang().send(player, "pet-passenger", "pet", pet.getDefinition().id());
            }
        }
    }

    public boolean isPetBase(Entity entity) {
        return entity != null && anchors.containsKey(entity.getUniqueId());
    }

    private void restore(Player player) {
        String id = equipped.get(player.getUniqueId());
        if (id == null || active.containsKey(player.getUniqueId()) || player.isDead()) {
            return;
        }
        PetDefinition def = definition(id);
        if (def == null || !plugin.getRegistry().contains(def.model())) {
            return;
        }
        if (!hasAccess(player, def.id())) {
            equipped.remove(player.getUniqueId());
            save();
            return;
        }
        spawn(player, def);
    }

    private Location behind(Player owner, PetDefinition def) {
        Location loc = owner.getLocation();
        Vector back = loc.getDirection().setY(0);
        if (back.lengthSquared() < 1.0E-4) {
            back = new Vector(0, 0, 1);
        }
        back.normalize().multiply(-def.followDistance());
        Location target = loc.clone().add(back);
        if (def.movement() == PetDefinition.Movement.FLY) {
            target.add(0, def.flyHeight(), 0);
        }
        target.setPitch(0f);
        target.setYaw(loc.getYaw());
        return target;
    }

    private boolean spawn(Player owner, PetDefinition def) {
        Location loc = behind(owner, def);
        ActiveRig rig = plugin.getRigManager().summonProp(loc, def.model(), false);
        if (rig == null) {
            return false;
        }
        Entity anchor = rig.getOwner().getBase();
        anchor.setInvulnerable(true);
        anchor.setGravity(false);
        float scale = def.scale() * plugin.getBedrock().petScale(owner);
        if (anchor instanceof Interaction interaction) {
            interaction.setInteractionWidth(interaction.getInteractionWidth() * scale);
            interaction.setInteractionHeight(interaction.getInteractionHeight() * scale);
            interaction.setResponsive(false);
        }
        if (def.name() != null) {
            anchor.setCustomName(Lang.legacy(Lang.apply(def.name(), "player", owner.getName())));
            anchor.setCustomNameVisible(false);
        }
        rig.setScale(scale);
        rig.getStateController().setEnabled(false);
        ActivePet pet = new ActivePet(owner.getUniqueId(), def, rig, anchor);
        pet.animator.setLoop(rig, animIdle);
        active.put(owner.getUniqueId(), pet);
        anchors.put(anchor.getUniqueId(), owner.getUniqueId());
        return true;
    }

    private void despawn(UUID owner) {
        ActivePet pet = active.remove(owner);
        if (pet == null) {
            return;
        }
        anchors.remove(pet.anchor.getUniqueId());
        if (pet.anchor.isValid()) {
            plugin.getRigManager().removeRig(pet.anchor, true);
        }
    }

    // ------------------------------------------------------------------ movement / animation

    private void tick() {
        if (active.isEmpty()) {
            return;
        }
        for (ActivePet pet : new ArrayList<>(active.values())) {
            Player owner = Bukkit.getPlayer(pet.getOwner());
            if (owner == null || !owner.isOnline()) {
                despawn(pet.getOwner());
                continue;
            }
            if (!pet.anchor.isValid()) {
                active.remove(pet.getOwner());
                anchors.remove(pet.anchor.getUniqueId());
                restore(owner);
                continue;
            }
            if (owner.isDead()) {
                continue;
            }
            pet.ticks++;
            pet.rig.setHidden(hideWhileDisguised && plugin.getDisguiseManager().isDisguised(owner));
            if (isRidden(pet)) {
                Location at = pet.anchor.getLocation();
                pet.x = at.getX();
                pet.y = at.getY();
                pet.z = at.getZ();
                pet.yaw = at.getYaw();
                animateRidden(pet);
                continue;
            }
            move(pet, owner);
            animate(pet, owner);
        }
    }

    private void move(ActivePet pet, Player owner) {
        PetDefinition def = pet.getDefinition();
        Location ownerLoc = owner.getLocation();
        World world = ownerLoc.getWorld();
        Location target = behind(owner, def);
        boolean sameWorld = pet.anchor.getWorld().equals(world);
        double dx = target.getX() - pet.x;
        double dz = target.getZ() - pet.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double ownerDistance = sameWorld
                ? Math.sqrt(Math.pow(ownerLoc.getX() - pet.x, 2) + Math.pow(ownerLoc.getY() - pet.y, 2) + Math.pow(ownerLoc.getZ() - pet.z, 2))
                : Double.MAX_VALUE;

        if (!sameWorld || ownerDistance > teleportDistance) {
            pet.x = target.getX();
            pet.y = def.movement() == PetDefinition.Movement.FLY ? target.getY() : ownerLoc.getY();
            pet.z = target.getZ();
            pet.yaw = ownerLoc.getYaw();
            Location tp = new Location(world, pet.x, pet.y, pet.z, pet.yaw, 0f);
            EntityCompat.teleportWithPassengers(pet.anchor, tp);
            pet.moving = false;
            return;
        }

        double step = 0;
        if (horizontal > stopDistance * 0.5 && ownerDistance > stopDistance) {
            double speed = owner.isSprinting() ? def.sprintSpeed() : def.speed();
            speed = Math.max(speed, Math.min(horizontal * 0.12, def.sprintSpeed() * 1.6));
            step = Math.min(speed, horizontal);
            pet.x += dx / horizontal * step;
            pet.z += dz / horizontal * step;
        }
        pet.moving = step > 0.02;

        if (def.movement() == PetDefinition.Movement.FLY) {
            double hover = Math.sin(pet.ticks / 12.0) * 0.12;
            double targetY = ownerLoc.getY() + def.flyHeight() + hover;
            pet.y += Math.max(-0.3, Math.min(0.3, (targetY - pet.y) * 0.25));
        } else {
            double ground = groundY(world, pet.x, pet.y, pet.z);
            if (Double.isNaN(ground)) {
                pet.y -= 0.4;
                if (ownerLoc.getY() - pet.y > 6) {
                    pet.y = ownerLoc.getY();
                }
            } else {
                double diff = ground - pet.y;
                pet.y += diff > 0 ? Math.min(diff, 0.55) : Math.max(diff, -0.6);
            }
        }

        float desiredYaw;
        if (pet.moving) {
            Location dir = new Location(world, 0, 0, 0);
            dir.setDirection(new Vector(dx, 0, dz));
            desiredYaw = dir.getYaw();
        } else {
            Location dir = new Location(world, 0, 0, 0);
            dir.setDirection(new Vector(ownerLoc.getX() - pet.x, 0, ownerLoc.getZ() - pet.z));
            desiredYaw = dir.getYaw();
        }
        pet.yaw = dev.privalo.radiantengine.util.MathUtil.approachDegrees(pet.yaw, desiredYaw, pet.moving ? 25f : 10f);
        Location next = new Location(world, pet.x, pet.y, pet.z, pet.yaw, 0f);
        EntityCompat.teleportWithPassengers(pet.anchor, next);
    }

    /** Ground height below (x, z) searched from slightly above the pet, or NaN if nothing within 4 blocks. */
    private static double groundY(World world, double x, double y, double z) {
        Location start = new Location(world, x, y + 1.25, z);
        RayTraceResult hit = world.rayTraceBlocks(start, new Vector(0, -1, 0), 5.25, FluidCollisionMode.NEVER, true);
        return hit == null ? Double.NaN : hit.getHitPosition().getY();
    }

    private void animate(ActivePet pet, Player owner) {
        double oy = owner.getLocation().getY();
        if (!Double.isNaN(pet.lastOwnerY) && oy - pet.lastOwnerY > 0.08 && !owner.isOnGround() && !owner.isFlying()) {
            pet.ownerJumpTicks = 10;
        } else if (pet.ownerJumpTicks > 0) {
            pet.ownerJumpTicks--;
        }
        pet.lastOwnerY = oy;
        ActiveRig rig = pet.rig;
        if (pet.ownerJumpTicks > 0) {
            pet.animator.setLoop(rig, animJump, animWalk, animIdle);
        } else if (pet.moving && pet.getDefinition().movement() == PetDefinition.Movement.FLY) {
            pet.animator.setLoop(rig, animFly, owner.isSprinting() ? animSprint : animWalk, animWalk, animIdle);
        } else if (pet.moving && owner.isSprinting()) {
            pet.animator.setLoop(rig, animSprint, animWalk, animIdle);
        } else if (pet.moving) {
            pet.animator.setLoop(rig, animWalk, animIdle);
        } else {
            pet.animator.setLoop(rig, animIdle);
        }
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                restore(player);
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        despawn(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        despawn(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                restore(player);
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnerAttack(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            damager = shooter;
        }
        if (damager instanceof Player player) {
            ActivePet pet = active.get(player.getUniqueId());
            if (pet != null && pet.isValid() && !isPetBase(event.getEntity())) {
                pet.animator.oneShot(pet.rig, animOwnerAttack, "attack");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnerHurt(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            ActivePet pet = active.get(player.getUniqueId());
            if (pet != null && pet.isValid()) {
                pet.animator.oneShot(pet.rig, animOwnerHurt, "hurt");
            }
        }
    }

    /** Pets are invulnerable; clicks on them do nothing. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPetDamaged(EntityDamageEvent event) {
        if (isPetBase(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ GUI source

    @Override
    public String title(boolean textured) {
        return plugin.getLang().raw(textured ? "pet-gui-title-textured" : "pet-gui-title");
    }

    @Override
    public List<String> entries(Player player) {
        return available(player);
    }

    @Override
    public ItemStack icon(Player player, String id) {
        PetDefinition def = definition(id);
        RigBlueprint bp = def == null ? null : plugin.getRegistry().get(def.model());
        ItemStack item = bp == null ? null : plugin.getItemResolver().iconItem(bp);
        if (item == null) {
            item = new ItemStack(Material.BONE);
        }
        boolean current = id.equals(equipped.get(player.getUniqueId()));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Lang.legacy((current ? "<green>✔ " : "<aqua>") + "<bold>" + Lang.escape(id)));
            List<String> lore = new ArrayList<>();
            lore.add(Lang.legacy("<gray>Modelo: <white>" + Lang.escape(def == null ? id : def.model())));
            if (def != null) {
                lore.add(Lang.legacy("<gray>Movimiento: <white>" + (def.movement() == PetDefinition.Movement.FLY ? "vuela" : "camina")));
            }
            lore.add("");
            lore.add(Lang.legacy(current ? "<red>Click izquierdo: guardar mascota" : "<green>Click izquierdo: invocar mascota"));
            if (def != null && def.rideable()) {
                lore.add(Lang.legacy("<aqua>Click derecho: montar"));
            }
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void select(Player player, String id, ClickType click) {
        PetDefinition rideDef = definition(id);
        if (click.isRightClick() && rideDef != null && rideDef.rideable()) {
            player.closeInventory();
            sendRideResult(player, ride(player, id));
            return;
        }
        if (id.equals(equipped.get(player.getUniqueId()))) {
            dismiss(player);
            plugin.getLang().send(player, "pet-dismissed");
            return;
        }
        if (!plugin.getProtection().checkSpawn(player, player.getLocation())) {
            return;
        }
        Result r = summon(player, id);
        switch (r) {
            case SUCCESS -> plugin.getLang().send(player, "pet-summoned", "pet", id);
            case NO_ACCESS -> plugin.getLang().send(player, "pet-no-access", "pet", id);
            case UNKNOWN -> plugin.getLang().send(player, "pet-unknown", "pet", id);
            case FAILED -> plugin.getLang().send(player, "pet-failed", "pet", id);
        }
    }

    @Override
    public ItemStack clearButton(Player player) {
        return CollectionMenu.button(Material.BARRIER, Lang.legacy("<red>Guardar mascota"),
                Lang.legacy("<gray>Retira tu mascota actual."));
    }

    @Override
    public void clear(Player player) {
        plugin.getLang().send(player, dismiss(player) ? "pet-dismissed" : "pet-none");
    }

    @Override
    public ItemStack infoButton(Player player, int count) {
        String current = equipped.get(player.getUniqueId());
        return CollectionMenu.button(Material.KNOWLEDGE_BOOK, Lang.legacy("<gold><bold>Mascotas"),
                Lang.legacy("<gray>Disponibles: <white>" + count),
                Lang.legacy("<gray>Equipada: <white>" + (current == null ? "-" : Lang.escape(current))));
    }

    public void openMenu(Player player) {
        CollectionMenu.open(plugin, player, this, 0);
    }
}
