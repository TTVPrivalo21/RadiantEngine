package dev.privalo.radiantengine.hook;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 1.21.4+ item model component ({@code minecraft:item_model}). On older servers the method does not exist and items
 * keep using CustomModelData only.
 */
final class ModernItems {

    private static final boolean ITEM_MODEL_API = detect();

    private ModernItems() {
    }

    private static boolean detect() {
        try {
            ItemMeta.class.getMethod("setItemModel", NamespacedKey.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    static boolean supported() {
        return ITEM_MODEL_API;
    }

    /** Points the item to our own item definition (assets/&lt;ns&gt;/items/&lt;path&gt;.json). */
    static void setItemModel(ItemMeta meta, String modelPath) {
        if (!ITEM_MODEL_API || meta == null || modelPath == null) {
            return;
        }
        NamespacedKey key = NamespacedKey.fromString(modelPath);
        if (key == null) {
            return;
        }
        try {
            meta.setItemModel(key);
        } catch (Throwable ignored) {
            // server without the component: CustomModelData is used instead
        }
    }
}
