package dev.privalo.radiantengine.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.InventoryHolder;

/**
 * Inventory menus handled by the shared click listener.
 */
public interface ClickableMenu extends InventoryHolder {

    void handleClick(Player player, int slot, ClickType click);
}
