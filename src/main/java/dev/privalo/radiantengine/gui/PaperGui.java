package dev.privalo.radiantengine.gui;

import dev.privalo.radiantengine.generator.GuiTextures;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Paper-only inventory titles using Adventure components, so the background glyph can use the custom font.
 * Only loaded after {@link #isAvailable()} returned true.
 */
final class PaperGui {

    private static Boolean available;

    private PaperGui() {
    }

    static boolean isAvailable() {
        if (available == null) {
            boolean ok;
            try {
                Class<?> component = Class.forName("net.kyori.adventure.text.Component");
                Bukkit.class.getMethod("createInventory", InventoryHolder.class, int.class, component);
                ok = true;
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                ok = false;
            }
            available = ok;
        }
        return available;
    }

    static Inventory create(InventoryHolder holder, int size, String namespace, String miniTitle) {
        Component background = Component.text(GuiTextures.titlePrefix())
                .font(Key.key(namespace, GuiTextures.FONT))
                .color(NamedTextColor.WHITE);
        Component title = MiniMessage.miniMessage().deserialize(miniTitle)
                .font(Key.key("minecraft", "default"));
        return Bukkit.createInventory(holder, size, Component.empty().append(background).append(title));
    }
}
