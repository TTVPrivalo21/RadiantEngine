package dev.privalo.radiantengine.util;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Persistent data keys stored on entities.
 */
public final class Keys {

    /** Comma separated model ids applied to a base entity. */
    public final NamespacedKey models;
    /** Byte flag: base entity is hidden (invisible) because of the model. */
    public final NamespacedKey hideBase;
    /** Byte flag: base entity was created by RadiantEngine (removed together with the model). */
    public final NamespacedKey owned;
    /** Byte flag: base entity is a static prop created with /radiant summon. */
    public final NamespacedKey prop;
    /** Byte flag on every display/interaction part entity. */
    public final NamespacedKey part;
    /** Model id a player is disguised as. */
    public final NamespacedKey disguise;
    /** Tint per model "model=RRGGBB;model2=RRGGBB". */
    public final NamespacedKey tints;

    public Keys(Plugin plugin) {
        this.models = new NamespacedKey(plugin, "models");
        this.hideBase = new NamespacedKey(plugin, "hide_base");
        this.owned = new NamespacedKey(plugin, "owned");
        this.prop = new NamespacedKey(plugin, "prop");
        this.part = new NamespacedKey(plugin, "part");
        this.disguise = new NamespacedKey(plugin, "disguise");
        this.tints = new NamespacedKey(plugin, "tints");
    }

    public boolean flag(Entity e, NamespacedKey key) {
        Byte b = e.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return b != null && b != 0;
    }

    public void setFlag(Entity e, NamespacedKey key, boolean value) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        if (value) {
            pdc.set(key, PersistentDataType.BYTE, (byte) 1);
        } else {
            pdc.remove(key);
        }
    }

    public String string(Entity e, NamespacedKey key) {
        return e.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public void setString(Entity e, NamespacedKey key, String value) {
        if (value == null || value.isEmpty()) {
            e.getPersistentDataContainer().remove(key);
        } else {
            e.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        }
    }

    public boolean isPart(Entity e) {
        return flag(e, part);
    }
}
