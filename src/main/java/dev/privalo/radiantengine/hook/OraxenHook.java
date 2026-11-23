package dev.privalo.radiantengine.hook;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.items.ItemBuilder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Direct Oraxen API access. Only instantiated when the Oraxen plugin is enabled.
 */
public final class OraxenHook {

    public ItemStack getItem(String id) {
        try {
            if (!OraxenItems.exists(id)) {
                return null;
            }
            ItemBuilder builder = OraxenItems.getItemById(id);
            return builder == null ? null : builder.build();
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean exists(String id) {
        try {
            return OraxenItems.exists(id);
        } catch (Throwable t) {
            return false;
        }
    }

    public List<String> itemIds() {
        try {
            return new ArrayList<>(OraxenItems.getNames());
        } catch (Throwable t) {
            return List.of();
        }
    }
}
