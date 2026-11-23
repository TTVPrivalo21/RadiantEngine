package dev.privalo.radiantengine.api;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.animation.runtime.RigState;
import dev.privalo.radiantengine.blueprint.RigBlueprint;
import dev.privalo.radiantengine.config.Settings;
import dev.privalo.radiantengine.hologram.HologramMode;
import dev.privalo.radiantengine.hologram.HologramRig;
import dev.privalo.radiantengine.model.ActiveRig;
import dev.privalo.radiantengine.model.RiggedEntity;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Public API of RadiantEngine.
 *
 * <p>Terminology: a <b>blueprint</b> is an imported {@code .bbmodel}; an {@link ActiveRig} is one blueprint
 * instance rendered on an entity; a {@link RiggedEntity} is the base entity together with all of its rigs.</p>
 *
 * <p>All methods must be called from the main server thread. Methods return {@code null} / {@code false} instead of
 * throwing when the model or entity does not exist.</p>
 *
 * <pre>{@code
 * // Spawn a living rig (AI from the base mob) and animate it
 * ActiveRig dragon = RadiantAPI.spawnRig(location, "dragon", EntityType.HUSK);
 * RadiantAPI.playAnimation(dragon, "roar");
 *
 * // Put a model on any existing entity and tint it
 * RadiantAPI.applyRig(zombie, "knight");
 * RadiantAPI.setTint(zombie, "knight", Color.fromRGB(0xFF5555));
 *
 * // Disguise a player
 * RadiantAPI.applyDisguise(player, "werewolf");
 * }</pre>
 */
public final class RadiantAPI {

    private static RadiantEngine plugin;

    private RadiantAPI() {
    }

    /**
     * Internal: called by RadiantEngine on enable.
     *
     * @param instance plugin instance
     */
    public static void init(RadiantEngine instance) {
        plugin = instance;
    }

    /**
     * @return the running plugin
     * @throws IllegalStateException if RadiantEngine is not enabled
     */
    public static RadiantEngine getPlugin() {
        if (plugin == null) {
            throw new IllegalStateException("RadiantEngine is not enabled");
        }
        return plugin;
    }

    // ------------------------------------------------------------------ blueprints

    /**
     * @param modelId blueprint id (file name of the .bbmodel, lower case)
     * @return the blueprint or null
     */
    public static RigBlueprint getBlueprint(String modelId) {
        return getPlugin().getRegistry().get(modelId);
    }

    /** @return every loaded blueprint by id (read only) */
    public static Map<String, RigBlueprint> getBlueprints() {
        return getPlugin().getRegistry().all();
    }

    // ------------------------------------------------------------------ spawning / applying

    /**
     * Spawns a static rig anchored to an Interaction entity sized like the model hitbox. It is saved with the chunk.
     *
     * @param location where to place it (yaw is used as facing)
     * @param modelId  blueprint id
     * @return the rig, or null if the model does not exist
     */
    public static ActiveRig spawnRig(Location location, String modelId) {
        return getPlugin().getRigManager().summonProp(location, modelId, true);
    }

    /**
     * Spawns a new living entity of the given type wearing the model (the entity is made invisible).
     *
     * @param location spawn location
     * @param modelId  blueprint id
     * @param baseType entity type providing AI, hitbox and sounds
     * @return the rig, or null if the model or type is invalid
     */
    public static ActiveRig spawnRig(Location location, String modelId, EntityType baseType) {
        return getPlugin().getRigManager().spawnRig(location, baseType, modelId);
    }

    /**
     * Attaches a model to an existing entity. The model id is stored on the entity and restored after restarts.
     *
     * @param entity  base entity
     * @param modelId blueprint id
     * @return the rig, or null if the model does not exist
     */
    public static ActiveRig applyRig(Entity entity, String modelId) {
        return getPlugin().getRigManager().addModel(entity, modelId, true);
    }

    /**
     * Attaches a model without persisting it (it disappears on chunk unload or restart).
     *
     * @param entity  base entity
     * @param modelId blueprint id
     * @return the rig, or null if the model does not exist
     */
    public static ActiveRig applyTemporaryRig(Entity entity, String modelId) {
        return getPlugin().getRigManager().addModel(entity, modelId, false);
    }

    /**
     * Removes one model from an entity.
     *
     * @param entity  base entity
     * @param modelId blueprint id
     * @return true if something was removed
     */
    public static boolean removeRig(Entity entity, String modelId) {
        return getPlugin().getRigManager().removeModel(entity, modelId);
    }

    /**
     * Removes every model from an entity. Entities created by RadiantEngine (spawnRig) are removed as well.
     *
     * @param entity base entity
     */
    public static void removeRig(Entity entity) {
        getPlugin().getRigManager().removeRig(entity, true);
    }

    // ------------------------------------------------------------------ lookup

    /**
     * @param entity  base entity
     * @param modelId blueprint id
     * @return the rig of that model on the entity, or null
     */
    public static ActiveRig getActiveRig(Entity entity, String modelId) {
        RiggedEntity rigged = getRiggedEntity(entity);
        return rigged == null ? null : rigged.getModel(modelId);
    }

    /**
     * @param entity base entity
     * @return the first rig on the entity, or null
     */
    public static ActiveRig getActiveRig(Entity entity) {
        RiggedEntity rigged = getRiggedEntity(entity);
        return rigged == null || rigged.isEmpty() ? null : rigged.models().iterator().next();
    }

    /**
     * Resolves a base entity or any of its part entities (displays, hitboxes, seats).
     *
     * @param entityOrPart base or part entity
     * @return the rigged entity or null
     */
    public static RiggedEntity getRiggedEntity(Entity entityOrPart) {
        return getPlugin().getRigManager().resolve(entityOrPart);
    }

    /** @return true if the entity (or part) belongs to a rig */
    public static boolean isRigged(Entity entityOrPart) {
        RiggedEntity rigged = getRiggedEntity(entityOrPart);
        return rigged != null && !rigged.isEmpty();
    }

    /** @return every live rigged entity (read only) */
    public static Collection<RiggedEntity> getRiggedEntities() {
        return getPlugin().getRigManager().all();
    }

    // ------------------------------------------------------------------ animation

    /**
     * Plays an animation with the default fade (0.1s in / 0.1s out), forcing a restart if already playing.
     *
     * @param rig       target rig
     * @param animation animation name as written in Blockbench
     * @return false if the animation does not exist or a listener cancelled it
     */
    public static boolean playAnimation(ActiveRig rig, String animation) {
        return rig != null && rig.playAnimation(animation, 0.1, 0.1, 1.0, true);
    }

    /**
     * Plays an animation on a model of an entity.
     *
     * @param entity    base entity
     * @param modelId   blueprint id
     * @param animation animation name
     * @param lerpIn    fade-in seconds
     * @param lerpOut   fade-out seconds
     * @param speed     playback speed (1 = normal)
     * @param force     restart it if it is already playing
     * @return false if the rig or animation does not exist
     */
    public static boolean playAnimation(Entity entity, String modelId, String animation,
                                        double lerpIn, double lerpOut, double speed, boolean force) {
        ActiveRig rig = getActiveRig(entity, modelId);
        return rig != null && rig.playAnimation(animation, lerpIn, lerpOut, speed, force);
    }

    /**
     * Stops an animation (with its fade-out).
     *
     * @param entity    base entity
     * @param modelId   blueprint id
     * @param animation animation name
     */
    public static void stopAnimation(Entity entity, String modelId, String animation) {
        ActiveRig rig = getActiveRig(entity, modelId);
        if (rig != null) {
            rig.stopAnimation(animation);
        }
    }

    /**
     * Overrides the animation used for an automatic state (idle, walk, attack...) on one rig.
     *
     * @param rig       target rig
     * @param state     automatic state
     * @param animation animation name, or null to restore the config default
     * @param lerpIn    fade-in seconds
     * @param lerpOut   fade-out seconds
     * @param speed     playback speed
     */
    public static void setStateAnimation(ActiveRig rig, RigState state, String animation,
                                         double lerpIn, double lerpOut, double speed) {
        rig.getStateController().setStateOverride(state,
                animation == null ? null : new Settings.StateSettings(animation, lerpIn, lerpOut, speed));
    }

    /**
     * Enables or disables the automatic idle/walk/jump/fly states of a rig.
     *
     * @param rig     target rig
     * @param enabled new value
     */
    public static void setAutoStates(ActiveRig rig, boolean enabled) {
        rig.getStateController().setEnabled(enabled);
    }

    // ------------------------------------------------------------------ appearance

    /**
     * Tints a model (multiplies the texture color). The tint is stored on the entity.
     *
     * @param entity  base entity
     * @param modelId blueprint id
     * @param color   tint, or {@link Color#WHITE} to reset
     */
    public static void setTint(Entity entity, String modelId, Color color) {
        ActiveRig rig = getActiveRig(entity, modelId);
        if (rig != null) {
            rig.setTint(color);
            getPlugin().getRigManager().storeTint(entity, modelId, color);
        }
    }

    /**
     * Tints a rig without persisting it.
     *
     * @param rig   target rig
     * @param color tint
     */
    public static void setTint(ActiveRig rig, Color color) {
        rig.setTint(color);
    }

    /**
     * @param rig   target rig
     * @param scale uniform scale (1 = Blockbench size)
     */
    public static void setScale(ActiveRig rig, float scale) {
        rig.setScale(scale);
    }

    /**
     * @param rig     target rig
     * @param bone    bone name
     * @param visible new visibility (children inherit it)
     */
    public static void setBoneVisible(ActiveRig rig, String bone, boolean visible) {
        rig.setBoneVisible(bone, visible);
    }

    /**
     * @param rig  target rig
     * @param bone tag_ bone name (without prefix)
     * @param text legacy-colored text, or null to fall back to the entity name
     */
    public static void setNametag(ActiveRig rig, String bone, String text) {
        rig.setNametag(bone, text);
    }

    /**
     * @param rig  target rig
     * @param bone item bone name (ir_/il_/ih_/item_ without prefix)
     * @param item item to show, or null for the default
     */
    public static void setBoneItem(ActiveRig rig, String bone, ItemStack item) {
        rig.setBoneItem(bone, item);
    }

    /**
     * Shows an Oraxen item on an item bone.
     *
     * @param rig      target rig
     * @param bone     item bone name
     * @param oraxenId Oraxen item id
     * @return false if Oraxen or the item is missing
     */
    public static boolean setBoneOraxenItem(ActiveRig rig, String bone, String oraxenId) {
        ItemStack item = getPlugin().getItemResolver().resolve("oraxen:" + oraxenId);
        if (item == null) {
            return false;
        }
        rig.setBoneItem(bone, item);
        return true;
    }

    // ------------------------------------------------------------------ disguises

    /**
     * Disguises a player as a model. Movement, look direction, jumps and attacks are synchronized automatically.
     *
     * @param player  player to disguise
     * @param modelId blueprint id
     * @return the rig, or null if the model does not exist
     */
    public static ActiveRig applyDisguise(Player player, String modelId) {
        return getPlugin().getDisguiseManager().disguise(player, modelId);
    }

    /**
     * @param player player
     * @return true if the player was disguised
     */
    public static boolean removeDisguise(Player player) {
        return getPlugin().getDisguiseManager().undisguise(player);
    }

    /** @return true if the player is disguised */
    public static boolean isDisguised(Player player) {
        return getPlugin().getDisguiseManager().isDisguised(player);
    }

    /** @return the model id the player is disguised as, or null */
    public static String getDisguise(Player player) {
        return getPlugin().getDisguiseManager().getDisguiseModel(player);
    }

    // ------------------------------------------------------------------ mounts

    /**
     * Mounts an entity on a rig seat.
     *
     * @param rider    entity to seat
     * @param rig      target rig
     * @param seatBone seat bone name, or null for automatic selection
     * @param driver   prefer the driver seat ("mount" bone, controls the rig with WASD + jump)
     * @return true on success
     */
    public static boolean mount(Entity rider, ActiveRig rig, String seatBone, boolean driver) {
        return getPlugin().getMountManager().mount(rider, rig, seatBone, driver);
    }

    /** @return true if the rider was seated on a rig */
    public static boolean dismount(Entity rider) {
        return getPlugin().getMountManager().dismount(rider);
    }

    /** @return the entity on the driver seat, or null */
    public static Entity getDriver(ActiveRig rig) {
        return getPlugin().getMountManager().getDriver(rig);
    }

    /** @return every entity seated on the rig */
    public static List<Entity> getPassengers(ActiveRig rig) {
        return getPlugin().getMountManager().getPassengers(rig);
    }

    /** @return the rig the entity is riding, or null */
    public static ActiveRig getMountedRig(Entity rider) {
        return getPlugin().getMountManager().getMountedModel(rider);
    }

    // ------------------------------------------------------------------ holograms

    /**
     * Creates a persistent 3D hologram (saved in holograms.yml) using the configured hologram defaults
     * (tint, brightness, particle ring...). It is re-created automatically whenever its chunk loads.
     *
     * @param id       unique id (a-z, 0-9, _ and -)
     * @param location pedestal point; its yaw is used by {@link HologramMode#STATIC}
     * @param modelId  blueprint id
     * @param mode     SPIN, BILLBOARD or STATIC
     * @param scale    model scale
     * @return the hologram, or null if the id already exists or the model is unknown
     */
    public static HologramRig spawnHologram(String id, Location location, String modelId, HologramMode mode, float scale) {
        return getPlugin().getHologramManager().create(id, modelId, location, mode, scale);
    }

    /**
     * Creates a spinning hologram at scale 1.
     *
     * @param id       unique id
     * @param location pedestal point
     * @param modelId  blueprint id
     * @return the hologram, or null if the id already exists or the model is unknown
     */
    public static HologramRig spawnHologram(String id, Location location, String modelId) {
        return spawnHologram(id, location, modelId, HologramMode.SPIN, 1f);
    }

    /**
     * Deletes a hologram from the world and from holograms.yml.
     *
     * @param id hologram id
     * @return false if it did not exist
     */
    public static boolean removeHologram(String id) {
        return getPlugin().getHologramManager().remove(id);
    }

    /**
     * @param id hologram id
     * @return the hologram or null
     */
    public static HologramRig getHologram(String id) {
        return getPlugin().getHologramManager().get(id);
    }

    /** @return every hologram (read only) */
    public static Collection<HologramRig> getHolograms() {
        return getPlugin().getHologramManager().all();
    }

    /**
     * Sets the floating text of a hologram (MiniMessage, PlaceholderAPI placeholders allowed).
     *
     * @param id   hologram id
     * @param text text, or null to remove it
     * @return false if the hologram does not exist
     */
    public static boolean setHologramText(String id, String text) {
        HologramRig h = getHologram(id);
        if (h == null) {
            return false;
        }
        h.setText(text);
        getPlugin().getHologramManager().update(h);
        return true;
    }

    /**
     * Sets the command run when a player right clicks the hologram ("console:" prefix runs it from console,
     * {player} is replaced by the player name).
     *
     * @param id      hologram id
     * @param command command, or null to remove it
     * @return false if the hologram does not exist
     */
    public static boolean setHologramCommand(String id, String command) {
        HologramRig h = getHologram(id);
        if (h == null) {
            return false;
        }
        h.setCommand(command);
        getPlugin().getHologramManager().save();
        return true;
    }

    /**
     * Moves a hologram (and saves it).
     *
     * @param id       hologram id
     * @param location new pedestal point
     * @return false if the hologram does not exist
     */
    public static boolean moveHologram(String id, Location location) {
        HologramRig h = getHologram(id);
        if (h == null) {
            return false;
        }
        getPlugin().getHologramManager().move(h, location);
        return true;
    }

    /**
     * Applies changes made through {@link HologramRig} setters (respawns it and saves holograms.yml).
     *
     * @param hologram modified hologram
     */
    public static void updateHologram(HologramRig hologram) {
        getPlugin().getHologramManager().update(hologram);
    }
}