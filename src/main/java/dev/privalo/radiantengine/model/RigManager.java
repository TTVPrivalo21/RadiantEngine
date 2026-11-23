package dev.privalo.radiantengine.model;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.blueprint.Hitbox;
import dev.privalo.radiantengine.blueprint.RigBlueprint;
import dev.privalo.radiantengine.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns every {@link RiggedEntity}, runs the per-tick update loop and persists model ids on base entities.
 */
public final class RigManager {

    private final RadiantEngine plugin;
    private final Map<UUID, RiggedEntity> entities = new HashMap<>();
    private final Map<UUID, RiggedEntity> partOwners = new HashMap<>();
    private BukkitTask task;
    private final List<Runnable> preTickHooks = new ArrayList<>();

    /** Runs every tick right before rigs are updated (pets and cosmetics position their anchors here). */
    public void addPreTickHook(Runnable hook) {
        preTickHooks.add(hook);
    }

    public RigManager(RadiantEngine plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    private void tick() {
        for (Runnable hook : preTickHooks) {
            try {
                hook.run();
            } catch (Throwable t) {
                plugin.getLogger().warning("Error en una tarea previa al tick de rigs: " + t);
                if (plugin.getSettings().debug) {
                    t.printStackTrace();
                }
            }
        }
        if (entities.isEmpty()) {
            return;
        }
        for (RiggedEntity me : new ArrayList<>(entities.values())) {
            boolean alive;
            try {
                alive = me.tick();
            } catch (Throwable t) {
                plugin.getLogger().warning("Error actualizando el modelo de " + me.getBase().getUniqueId() + ": " + t);
                if (plugin.getSettings().debug) {
                    t.printStackTrace();
                }
                alive = me.getBase().isValid();
            }
            if (!alive) {
                entities.remove(me.getBase().getUniqueId());
                me.destroy();
                if (me.isDying() && plugin.getKeys().flag(me.getBase(), plugin.getKeys().owned) && me.getBase().isValid()) {
                    me.getBase().remove();
                }
            }
        }
    }

    // ------------------------------------------------------------------ lookup

    public RiggedEntity get(Entity base) {
        return base == null ? null : entities.get(base.getUniqueId());
    }

    public RiggedEntity get(UUID uuid) {
        return entities.get(uuid);
    }

    public RiggedEntity getByPart(Entity part) {
        return part == null ? null : partOwners.get(part.getUniqueId());
    }

    /** Owner of a part entity, or the modeled entity itself when the given entity is a base. */
    public RiggedEntity resolve(Entity entity) {
        if (entity == null) {
            return null;
        }
        RiggedEntity me = entities.get(entity.getUniqueId());
        return me != null ? me : partOwners.get(entity.getUniqueId());
    }

    public Collection<RiggedEntity> all() {
        return Collections.unmodifiableCollection(entities.values());
    }

    void registerPart(UUID part, RiggedEntity owner) {
        partOwners.put(part, owner);
    }

    void unregisterPart(UUID part) {
        partOwners.remove(part);
    }

    // ------------------------------------------------------------------ add / remove

    public RiggedEntity getOrCreate(Entity base) {
        RiggedEntity me = entities.get(base.getUniqueId());
        if (me == null || me.isDestroyed()) {
            me = new RiggedEntity(plugin, base);
            entities.put(base.getUniqueId(), me);
        }
        return me;
    }

    /**
     * Attaches a model to an entity.
     *
     * @param persist store the model id on the entity so it is restored after restarts / chunk reloads
     */
    public ActiveRig addModel(Entity base, String modelId, boolean persist) {
        RigBlueprint bp = plugin.getRegistry().get(modelId);
        if (bp == null || base == null || !base.isValid()) {
            return null;
        }
        RiggedEntity me = getOrCreate(base);
        if (me.isDying()) {
            return null;
        }
        ActiveRig model = me.addModel(bp);
        if (persist && !(base instanceof Player)) {
            Set<String> ids = storedModels(base);
            ids.add(bp.getId());
            writeStoredModels(base, ids);
        }
        Color tint = storedTint(base, bp.getId());
        if (tint != null) {
            model.setTint(tint);
        }
        return model;
    }

    public boolean removeModel(Entity base, String modelId) {
        RiggedEntity me = get(base);
        boolean removed = me != null && me.removeModel(modelId);
        Set<String> ids = storedModels(base);
        if (ids.remove(modelId)) {
            writeStoredModels(base, ids);
            removed = true;
        }
        if (me != null && me.isEmpty()) {
            removeRig(base, false);
        }
        return removed;
    }

    /**
     * Removes all models from an entity.
     *
     * @param removeOwnedBase also delete the base entity if it was created by RadiantEngine
     */
    public void removeRig(Entity base, boolean removeOwnedBase) {
        RiggedEntity me = entities.remove(base.getUniqueId());
        if (me != null) {
            me.destroy();
        }
        Keys keys = plugin.getKeys();
        keys.setString(base, keys.models, null);
        keys.setString(base, keys.tints, null);
        boolean owned = keys.flag(base, keys.owned);
        if (removeOwnedBase && owned && !(base instanceof Player)) {
            base.remove();
            return;
        }
        if (keys.flag(base, keys.hideBase) && base instanceof LivingEntity living && !(base instanceof Player)) {
            living.setInvisible(false);
            keys.setFlag(base, keys.hideBase, false);
        }
    }

    /** Destroys runtime parts but keeps persisted data (chunk unload, quit, shutdown). */
    public void unload(Entity base) {
        RiggedEntity me = entities.remove(base.getUniqueId());
        if (me != null) {
            me.destroy();
        }
    }

    /** Called when a base entity dies: plays death animations or removes the models. */
    public void handleDeath(Entity base) {
        RiggedEntity me = entities.get(base.getUniqueId());
        if (me == null) {
            return;
        }
        if (!me.startDying()) {
            entities.remove(base.getUniqueId());
            me.destroy();
        }
    }

    // ------------------------------------------------------------------ spawning helpers

    /** Spawns a new living base entity with the model (/radiant spawn). */
    public ActiveRig spawnRig(Location location, EntityType type, String modelId) {
        RigBlueprint bp = plugin.getRegistry().get(modelId);
        if (bp == null || type == null || type.getEntityClass() == null || location.getWorld() == null) {
            return null;
        }
        Keys keys = plugin.getKeys();
        Entity base = location.getWorld().spawn(location, type.getEntityClass(), e -> {
            keys.setFlag(e, keys.owned, true);
            keys.setString(e, keys.models, bp.getId());
            if (plugin.getSettings().spawnSilent) {
                e.setSilent(true);
            }
            if (e instanceof LivingEntity living) {
                if (plugin.getSettings().hideBase) {
                    living.setInvisible(true);
                    keys.setFlag(e, keys.hideBase, true);
                }
                if (plugin.getSettings().clearEquipment) {
                    EntityEquipment eq = living.getEquipment();
                    if (eq != null) {
                        eq.clear();
                    }
                }
                if (living instanceof Mob mob) {
                    mob.setRemoveWhenFarAway(false);
                }
            }
        });
        return addModel(base, bp.getId(), true);
    }

    /** Summons a static prop: the base is an Interaction sized like the model hitbox (/radiant summon). */
    public ActiveRig summonProp(Location location, String modelId) {
        return summonProp(location, modelId, true);
    }

    /**
     * Summons a static prop anchored to an Interaction.
     *
     * @param persistent false for temporary anchors (previews, packet NPC rigs) that must not be saved with the chunk
     */
    public ActiveRig summonProp(Location location, String modelId, boolean persistent) {
        RigBlueprint bp = plugin.getRegistry().get(modelId);
        if (bp == null || location.getWorld() == null) {
            return null;
        }
        Hitbox h = bp.getMainHitbox();
        float width = h == null ? 1f : (float) Math.max(0.1, h.horizontal());
        float height = h == null ? 1f : (float) Math.max(0.1, h.height());
        Keys keys = plugin.getKeys();
        Interaction anchor = location.getWorld().spawn(location, Interaction.class, e -> {
            e.setInteractionWidth(width);
            e.setInteractionHeight(height);
            e.setResponsive(true);
            e.setPersistent(persistent);
            keys.setFlag(e, keys.owned, true);
            keys.setFlag(e, keys.prop, true);
            keys.setString(e, keys.models, bp.getId());
        });
        return addModel(anchor, bp.getId(), persistent);
    }

    /**
     * Spawns a rig on an invisible, non-persistent ItemDisplay anchor without hitbox (used by cosmetics so they
     * never intercept clicks aimed at the player).
     */
    public ActiveRig summonDisplayAnchor(Location location, String modelId) {
        RigBlueprint bp = plugin.getRegistry().get(modelId);
        if (bp == null || location.getWorld() == null) {
            return null;
        }
        Keys keys = plugin.getKeys();
        org.bukkit.entity.ItemDisplay anchor = location.getWorld().spawn(location, org.bukkit.entity.ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setItemStack(null);
            e.setInvulnerable(true);
            e.setTeleportDuration(plugin.getSettings().teleportTicks);
            keys.setFlag(e, keys.owned, true);
        });
        RiggedEntity me = getOrCreate(anchor);
        me.setHitboxEnabled(false);
        return addModel(anchor, bp.getId(), false);
    }

    // ------------------------------------------------------------------ persistence

    public Set<String> storedModels(Entity base) {
        String raw = plugin.getKeys().string(base, plugin.getKeys().models);
        Set<String> set = new LinkedHashSet<>();
        if (raw != null && !raw.isBlank()) {
            set.addAll(Arrays.asList(raw.split(",")));
        }
        set.removeIf(String::isBlank);
        return set;
    }

    private void writeStoredModels(Entity base, Set<String> ids) {
        plugin.getKeys().setString(base, plugin.getKeys().models, ids.isEmpty() ? null : String.join(",", ids));
    }

    public void storeTint(Entity base, String modelId, Color color) {
        if (base instanceof Player) {
            return;
        }
        Map<String, String> map = new HashMap<>();
        String raw = plugin.getKeys().string(base, plugin.getKeys().tints);
        if (raw != null) {
            for (String part : raw.split(";")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    map.put(kv[0], kv[1]);
                }
            }
        }
        if (color == null || color.asRGB() == 0xFFFFFF) {
            map.remove(modelId);
        } else {
            map.put(modelId, String.format("%06X", color.asRGB()));
        }
        List<String> parts = new ArrayList<>();
        map.forEach((k, v) -> parts.add(k + "=" + v));
        plugin.getKeys().setString(base, plugin.getKeys().tints, parts.isEmpty() ? null : String.join(";", parts));
    }

    private Color storedTint(Entity base, String modelId) {
        String raw = plugin.getKeys().string(base, plugin.getKeys().tints);
        if (raw == null) {
            return null;
        }
        for (String part : raw.split(";")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(modelId)) {
                try {
                    return Color.fromRGB(Integer.parseInt(kv[1], 16) & 0xFFFFFF);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** Re-creates models stored on an entity (chunk load / startup). Removes stray part entities. */
    public void restore(Entity entity) {
        Keys keys = plugin.getKeys();
        if (keys.isPart(entity)) {
            if (!partOwners.containsKey(entity.getUniqueId())) {
                entity.remove();
            }
            return;
        }
        if (entity instanceof Player || entities.containsKey(entity.getUniqueId())) {
            return;
        }
        Set<String> ids = storedModels(entity);
        if (ids.isEmpty()) {
            return;
        }
        if (keys.flag(entity, keys.hideBase) && entity instanceof LivingEntity living) {
            living.setInvisible(true);
        }
        for (String id : ids) {
            if (plugin.getRegistry().contains(id)) {
                addModel(entity, id, false);
            }
        }
    }

    public void restoreLoadedWorlds() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                restore(e);
            }
        }
    }

    private record RigSnapshot(String id, Color tint, float scale, boolean hidden, List<AnimationSnapshot> animations) {
    }

    private record AnimationSnapshot(String name, double speed, double time) {
    }

    /**
     * Hot reload: rebuilds every live rig with the freshly imported blueprints while keeping its base entity,
     * tint, scale, visibility and playing animations (restarted at the same time position).
     */
    public void refreshAll() {
        List<RiggedEntity> list = new ArrayList<>(entities.values());
        for (RiggedEntity me : list) {
            List<RigSnapshot> snapshots = new ArrayList<>();
            for (ActiveRig rig : me.models()) {
                List<AnimationSnapshot> anims = new ArrayList<>();
                rig.getAnimationHandler().getProperties().forEach((name, p) -> {
                    if (p.getPhase() != dev.privalo.radiantengine.animation.runtime.AnimationProperty.Phase.LERP_OUT) {
                        anims.add(new AnimationSnapshot(name, p.getSpeed(), p.getTime()));
                    }
                });
                snapshots.add(new RigSnapshot(rig.getId(), rig.getTint(), rig.getScale(), rig.isHidden(), anims));
            }
            Entity base = me.getBase();
            boolean disguise = me.isDisguise();
            entities.remove(base.getUniqueId());
            me.destroy();
            if (!base.isValid()) {
                continue;
            }
            for (RigSnapshot snap : snapshots) {
                ActiveRig rig = addModel(base, snap.id(), false);
                if (rig == null) {
                    continue;
                }
                rig.setTint(snap.tint());
                rig.setScale(snap.scale());
                rig.setHidden(snap.hidden());
                rig.getAnimationHandler().forceStopAll();
                for (AnimationSnapshot a : snap.animations()) {
                    var animation = rig.getBlueprint().getAnimations().get(a.name());
                    if (animation == null) {
                        continue;
                    }
                    var property = new dev.privalo.radiantengine.animation.runtime.AnimationProperty(animation, 0, 0.15, a.speed());
                    property.setTime(a.time());
                    rig.getAnimationHandler().playAnimation(property, true);
                }
            }
            RiggedEntity fresh = get(base);
            if (fresh != null) {
                fresh.setDisguise(disguise);
                if (disguise && base instanceof Player p) {
                    plugin.getDisguiseManager().applySelfVisibility(p, fresh);
                }
            }
        }
        restoreLoadedWorlds();
    }

    /** Re-sends bone items of every live rig (after a pack sync or config reload: new CMD / Oraxen items / tints). */
    public void refreshItemsAll() {
        for (RiggedEntity me : entities.values()) {
            for (ActiveRig rig : me.models()) {
                rig.refreshItems();
            }
        }
    }

    // ------------------------------------------------------------------ kill / cleanup

    /** Result of a kill operation. */
    public record KillResult(int rigs, int orphans) {
    }

    /**
     * Removes rigs and cleans orphan part entities.
     *
     * @param modelId only rigs using this model (null = every model)
     * @param world   only this world (null = every world)
     * @param center  center for the radius filter (null = no radius)
     * @param radius  radius in blocks (ignored when center is null or radius &lt;= 0)
     */
    public KillResult kill(String modelId, World world, Location center, double radius) {
        boolean useRadius = center != null && radius > 0;
        double r2 = radius * radius;
        int rigs = 0;
        for (RiggedEntity me : new ArrayList<>(entities.values())) {
            Entity base = me.getBase();
            Location loc = me.getOrigin() != null ? me.getOrigin() : base.getLocation();
            if (world != null && !world.equals(loc.getWorld())) {
                continue;
            }
            if (useRadius && (!loc.getWorld().equals(center.getWorld()) || loc.distanceSquared(center) > r2)) {
                continue;
            }
            if (modelId != null && me.getModel(modelId) == null) {
                continue;
            }
            if (plugin.isManagedBase(base)) {
                continue;
            }
            rigs++;
            if (plugin.getNpcManager().isNpcBase(base)) {
                plugin.getNpcManager().unassignByBase(base);
                continue;
            }
            if (base instanceof Player player && me.isDisguise()) {
                plugin.getDisguiseManager().undisguise(player);
                continue;
            }
            if (modelId != null && me.getModels().size() > 1) {
                removeModel(base, modelId);
                continue;
            }
            removeRig(base, true);
        }
        int orphans = 0;
        Keys keys = plugin.getKeys();
        for (World w : Bukkit.getWorlds()) {
            if (world != null && !world.equals(w)) {
                continue;
            }
            for (Entity e : w.getEntities()) {
                if (useRadius && (!w.equals(center.getWorld()) || e.getLocation().distanceSquared(center) > r2)) {
                    continue;
                }
                if (keys.isPart(e) && !partOwners.containsKey(e.getUniqueId())) {
                    e.remove();
                    orphans++;
                    continue;
                }
                if (!entities.containsKey(e.getUniqueId()) && keys.flag(e, keys.owned) && !(e instanceof Player)) {
                    Set<String> stored = storedModels(e);
                    if (!stored.isEmpty() && (modelId == null || stored.contains(modelId))) {
                        e.remove();
                        orphans++;
                    }
                }
            }
        }
        return new KillResult(rigs, orphans);
    }
    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (RiggedEntity me : new ArrayList<>(entities.values())) {
            me.destroy();
        }
        entities.clear();
        partOwners.clear();
    }
}
