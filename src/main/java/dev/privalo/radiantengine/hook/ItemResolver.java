package dev.privalo.radiantengine.hook;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.blueprint.BoneBlueprint;
import dev.privalo.radiantengine.blueprint.RigBlueprint;
import dev.privalo.radiantengine.generator.OraxenExporter;
import dev.privalo.radiantengine.generator.ResourcePackGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Builds bone items (CustomModelData + dye tint) and resolves item links ("oraxen:id", "minecraft:material").
 */
public final class ItemResolver {

    private final RadiantEngine plugin;
    private OraxenHook oraxen;
    private final Map<String, ItemStack> boneCache = new HashMap<>();

    public ItemResolver(RadiantEngine plugin) {
        this.plugin = plugin;
        detectOraxen();
    }

    public void detectOraxen() {
        boolean present = Bukkit.getPluginManager().isPluginEnabled("Oraxen");
        this.oraxen = present ? new OraxenHook() : null;
    }

    public boolean hasOraxen() {
        return oraxen != null;
    }

    public OraxenHook getOraxen() {
        return oraxen;
    }

    public void clearCache() {
        boneCache.clear();
    }

    /** Item rendering a bone, tinted with the given color. */
    public ItemStack boneItem(RigBlueprint blueprint, BoneBlueprint bone, Color tint) {
        String key = blueprint.getId() + "/" + bone.getName();
        ItemStack base = boneCache.get(key);
        if (base == null) {
            base = createBase(blueprint, bone);
            boneCache.put(key, base);
        }
        ItemStack item = base.clone();
        if (tint != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof LeatherArmorMeta leather) {
                leather.setColor(tint);
                item.setItemMeta(leather);
            } else if (meta instanceof PotionMeta potion) {
                potion.setColor(tint);
                item.setItemMeta(potion);
            }
        }
        return item;
    }

    /** Whole-model 3D icon (falls back to the first renderable bone, or null). */
    public ItemStack iconItem(RigBlueprint blueprint) {
        if (blueprint.getIconCustomModelData() <= 0) {
            BoneBlueprint first = blueprint.getFirstRenderableBone();
            return first == null ? null : boneItem(blueprint, first, null);
        }
        String key = blueprint.getId() + "/" + ResourcePackGenerator.ICON;
        ItemStack base = boneCache.get(key);
        if (base == null) {
            base = createBase(blueprint.getId(), ResourcePackGenerator.ICON, blueprint.getIconCustomModelData());
            boneCache.put(key, base);
        }
        return base.clone();
    }

    private ItemStack createBase(RigBlueprint blueprint, BoneBlueprint bone) {
        return createBase(blueprint.getId(), bone.getName(), bone.getCustomModelData());
    }

    private ItemStack createBase(String modelId, String boneName, int customModelData) {
        if (oraxen != null && plugin.getSettings().oraxenEnabled && plugin.getSettings().useOraxenItems) {
            ItemStack fromOraxen = oraxen.getItem(OraxenExporter.itemId(modelId, boneName));
            if (fromOraxen != null) {
                return fromOraxen;
            }
        }
        ItemStack item = new ItemStack(plugin.getSettings().baseItem);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(customModelData);
            meta.setDisplayName(modelId + ":" + boneName);
            if (meta instanceof LeatherArmorMeta leather) {
                leather.setColor(Color.WHITE);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Resolves "oraxen:id", "minecraft:material" or a bare material name. */
    public ItemStack resolve(String spec) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        String s = spec.trim();
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("oraxen:")) {
            return oraxen == null ? null : oraxen.getItem(s.substring(7));
        }
        if (oraxen != null && !lower.contains(":") && oraxen.exists(s)) {
            return oraxen.getItem(s);
        }
        Material m = Material.matchMaterial(s);
        return m == null || !m.isItem() || m.isAir() ? null : new ItemStack(m);
    }
}
