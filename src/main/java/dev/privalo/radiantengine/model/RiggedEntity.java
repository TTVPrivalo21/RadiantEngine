package dev.privalo.radiantengine.model;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.api.event.RigAttachEvent;
import dev.privalo.radiantengine.blueprint.Hitbox;
import dev.privalo.radiantengine.blueprint.RigBlueprint;
import dev.privalo.radiantengine.model.bone.PartFactory;
import dev.privalo.radiantengine.util.EntityCompat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Flying;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base entity + all models attached to it. Ticks movement detection, forward kinematics and part entities.
 */
public final class RiggedEntity {

    private final RadiantEngine plugin;
    private final Entity base;
    private final Map<String, ActiveRig> models = new LinkedHashMap<>();

    private Location origin;
    private Location lastOrigin;
    private MovementState movement = MovementState.STILL;
    private float bodyYaw;
    private float headYaw;
    private float pitch;

    private Interaction mainHitbox;
    private float hitboxWidth;
    private float hitboxHeight;

    private boolean dying;
    private int dyingTicks;
    private boolean disguise;
    private boolean destroyed;

    RiggedEntity(RadiantEngine plugin, Entity base) {
        this.plugin = plugin;
        this.base = base;
        this.origin = PartFactory.flat(base.getLocation());
        this.bodyYaw = EntityCompat.bodyYaw(base);
        this.headYaw = base.getLocation().getYaw();
        this.pitch = base.getLocation().getPitch();
    }

    // ------------------------------------------------------------------ models

    public ActiveRig addModel(RigBlueprint blueprint) {
        ActiveRig old = models.remove(blueprint.getId());
        if (old != null) {
            old.destroy();
        }
        ActiveRig model = new ActiveRig(plugin, this, blueprint);
        models.put(blueprint.getId(), model);
        model.spawn(origin, bodyYaw, headYaw, pitch);
        if (plugin.getSettings().hideVanillaNameWithTag && model.hasNametagBone() && !(base instanceof Player)) {
            base.setCustomNameVisible(false);
        }
        updateMainHitbox();
        Bukkit.getPluginManager().callEvent(new RigAttachEvent(model));
        return model;
    }

    public boolean removeModel(String id) {
        ActiveRig model = models.remove(id);
        if (model == null) {
            return false;
        }
        plugin.getMountManager().dismountAll(model);
        model.destroy();
        updateMainHitbox();
        return true;
    }

    public ActiveRig getModel(String id) {
        return models.get(id);
    }

    public Map<String, ActiveRig> getModels() {
        return Collections.unmodifiableMap(models);
    }

    public Collection<ActiveRig> models() {
        return Collections.unmodifiableCollection(models.values());
    }

    public boolean isEmpty() {
        return models.isEmpty();
    }

    // ------------------------------------------------------------------ tick

    /**
     * @return false when this modeled entity must be removed
     */
    boolean tick() {
        if (destroyed) {
            return false;
        }
        if (dying) {
            if (--dyingTicks <= 0) {
                return false;
            }
            for (ActiveRig model : new ArrayList<>(models.values())) {
                model.tick(origin, false, bodyYaw, headYaw, pitch, MovementState.STILL);
            }
            return true;
        }
        if (!base.isValid()) {
            return false;
        }
        Location loc = base.getLocation();
        boolean worldChanged = lastOrigin == null || lastOrigin.getWorld() != loc.getWorld();
        double dx = worldChanged ? 0 : loc.getX() - lastOrigin.getX();
        double dy = worldChanged ? 0 : loc.getY() - lastOrigin.getY();
        double dz = worldChanged ? 0 : loc.getZ() - lastOrigin.getZ();
        double threshold = plugin.getSettings().moveThreshold;
        boolean moved = worldChanged || dx * dx + dy * dy + dz * dz > threshold * threshold;
        movement = new MovementState(Math.sqrt(dx * dx + dz * dz), dy, base.isOnGround(), isFlying(), base.isInWater());

        plugin.getMountManager().drive(this);

        headYaw = loc.getYaw();
        pitch = loc.getPitch();
        bodyYaw = base instanceof LivingEntity ? EntityCompat.bodyYaw(base) : headYaw;
        origin = PartFactory.flat(loc);
        if (worldChanged && lastOrigin != null) {
            respawnParts();
        }
        for (ActiveRig model : new ArrayList<>(models.values())) {
            model.tick(origin, moved, bodyYaw, headYaw, pitch, movement);
        }
        if (mainHitbox != null) {
            if (!mainHitbox.isValid()) {
                mainHitbox = null;
                updateMainHitbox();
            } else if (moved) {
                mainHitbox.teleport(origin);
            }
        }
        lastOrigin = loc;
        return true;
    }

    private void respawnParts() {
        List<ActiveRig> list = new ArrayList<>(models.values());
        for (ActiveRig m : list) {
            plugin.getMountManager().dismountAll(m);
            m.destroy();
            m.spawn(origin, bodyYaw, headYaw, pitch);
        }
        removeMainHitbox();
        updateMainHitbox();
    }

    private boolean isFlying() {
        if (base instanceof Player p) {
            return p.isFlying() || p.isGliding();
        }
        if (base instanceof LivingEntity l && l.isGliding()) {
            return true;
        }
        if (base instanceof Flying || base instanceof Bat || base instanceof Vex || base instanceof Allay) {
            return !base.isOnGround();
        }
        if (base instanceof Bee || base instanceof Parrot) {
            return !base.isOnGround() && Math.abs(movement.verticalSpeed()) < 0.1;
        }
        return !base.hasGravity() && !base.isOnGround();
    }

    // ------------------------------------------------------------------ hitbox

    void updateMainHitbox() {
        if (!plugin.getSettings().mainHitbox || base instanceof Interaction || dying) {
            removeMainHitbox();
            return;
        }
        Hitbox best = null;
        float bestScale = 1f;
        for (ActiveRig m : models.values()) {
            Hitbox h = m.getBlueprint().getMainHitbox();
            if (h != null && (best == null || h.horizontal() * h.height() * m.getScale() > best.horizontal() * best.height() * bestScale)) {
                best = h;
                bestScale = m.getScale();
            }
        }
        if (best == null) {
            removeMainHitbox();
            return;
        }
        float w = (float) (best.horizontal() * bestScale);
        float h = (float) (best.height() * bestScale);
        if (mainHitbox != null && mainHitbox.isValid()) {
            if (Math.abs(w - hitboxWidth) > 1.0E-3 || Math.abs(h - hitboxHeight) > 1.0E-3) {
                mainHitbox.setInteractionWidth(w);
                mainHitbox.setInteractionHeight(h);
            }
        } else {
            mainHitbox = origin.getWorld().spawn(origin, Interaction.class, e -> {
                e.setPersistent(false);
                e.setSilent(true);
                e.setInteractionWidth(w);
                e.setInteractionHeight(h);
                e.setResponsive(true);
                plugin.getKeys().setFlag(e, plugin.getKeys().part, true);
            });
            plugin.getRigManager().registerPart(mainHitbox.getUniqueId(), this);
            applyPartRules(mainHitbox);
        }
        hitboxWidth = w;
        hitboxHeight = h;
    }

    private void removeMainHitbox() {
        if (mainHitbox != null) {
            plugin.getRigManager().unregisterPart(mainHitbox.getUniqueId());
            mainHitbox.remove();
            mainHitbox = null;
        }
    }

    /** Visibility rules for newly spawned parts (disguised players never see/click their own hitboxes). */
    void applyPartRules(Entity part) {
        if (base instanceof Player p && part instanceof Interaction) {
            p.hideEntity(plugin, part);
        }
    }

    // ------------------------------------------------------------------ death / destroy

    /**
     * Starts the death animation of every model.
     *
     * @return true if at least one model plays a death animation (the entity keeps rendering until it ends)
     */
    boolean startDying() {
        int max = -1;
        for (ActiveRig m : models.values()) {
            max = Math.max(max, m.startDeath());
        }
        if (max <= 0) {
            return false;
        }
        for (ActiveRig m : models.values()) {
            plugin.getMountManager().dismountAll(m);
        }
        dying = true;
        dyingTicks = max;
        removeMainHitbox();
        return true;
    }

    void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        for (ActiveRig m : models.values()) {
            plugin.getMountManager().dismountAll(m);
            m.destroy();
        }
        models.clear();
        removeMainHitbox();
        plugin.getMountManager().releaseControl(this);
    }

    // ------------------------------------------------------------------ getters

    public RadiantEngine getPlugin() {
        return plugin;
    }

    public Entity getBase() {
        return base;
    }

    public Location getOrigin() {
        return origin;
    }

    public MovementState getMovement() {
        return movement;
    }

    public float getBodyYaw() {
        return bodyYaw;
    }

    public float getHeadYaw() {
        return headYaw;
    }

    public float getPitch() {
        return pitch;
    }

    public boolean isDying() {
        return dying;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public boolean isDisguise() {
        return disguise;
    }

    public void setDisguise(boolean disguise) {
        this.disguise = disguise;
    }

    public Interaction getMainHitbox() {
        return mainHitbox;
    }
}
