package dev.privalo.radiantengine.gui;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.text.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Paginated per-player selection menu (pets, cosmetics) using the same textured layout as the model menu:
 * 45 entries plus a control row (previous, clear, info, next).
 */
public final class CollectionMenu implements ClickableMenu {

    /** Content provider of a collection menu. */
    public interface Source {
        /** MiniMessage title; {page} and {pages} are replaced. */
        String title(boolean textured);

        List<String> entries(Player player);

        ItemStack icon(Player player, String id);

        void select(Player player, String id, ClickType click);

        ItemStack clearButton(Player player);

        void clear(Player player);

        ItemStack infoButton(Player player, int count);
    }

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_CLEAR = 47;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private final RadiantEngine plugin;
    private final Source source;
    private final List<String> ids;
    private final int page;
    private final int pages;
    private final boolean textured;
    private final Inventory inventory;

    private CollectionMenu(RadiantEngine plugin, Player player, Source source, int page) {
        this.plugin = plugin;
        this.source = source;
        this.ids = new ArrayList<>(source.entries(player));
        this.pages = Math.max(1, (ids.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        this.page = Math.max(0, Math.min(page, pages - 1));
        this.textured = plugin.getSettings().guiCustomTexture && plugin.isGuiTextureReady() && PaperGui.isAvailable();
        String title = source.title(textured)
                .replace("{page}", String.valueOf(this.page + 1))
                .replace("{pages}", String.valueOf(pages));
        if (textured) {
            this.inventory = PaperGui.create(this, 54, plugin.getSettings().namespace, title);
        } else {
            String legacy = Lang.legacy(title);
            this.inventory = Bukkit.createInventory(this, 54, legacy.length() > 64 ? legacy.substring(0, 64) : legacy);
        }
        fill(player);
    }

    public static void open(RadiantEngine plugin, Player player, Source source, int page) {
        player.openInventory(new CollectionMenu(plugin, player, source, page).getInventory());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void fill(Player player) {
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < ids.size(); i++) {
            inventory.setItem(i, source.icon(player, ids.get(start + i)));
        }
        if (!textured) {
            ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, " ");
            for (int s = 45; s < 54; s++) {
                inventory.setItem(s, filler);
            }
        }
        if (page > 0) {
            inventory.setItem(SLOT_PREV, button(Material.SPECTRAL_ARROW, Lang.legacy("<aqua>« Pagina anterior")));
        }
        if (page < pages - 1) {
            inventory.setItem(SLOT_NEXT, button(Material.SPECTRAL_ARROW, Lang.legacy("<aqua>Pagina siguiente »")));
        }
        inventory.setItem(SLOT_CLEAR, source.clearButton(player));
        inventory.setItem(SLOT_INFO, plugin.getItemResolver().branded(source.infoButton(player, ids.size())));
    }

    public static ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void handleClick(Player player, int slot, ClickType click) {
        if (slot < 0 || slot >= 54) {
            return;
        }
        switch (slot) {
            case SLOT_PREV -> {
                if (page > 0) {
                    open(plugin, player, source, page - 1);
                }
                return;
            }
            case SLOT_NEXT -> {
                if (page < pages - 1) {
                    open(plugin, player, source, page + 1);
                }
                return;
            }
            case SLOT_CLEAR -> {
                source.clear(player);
                open(plugin, player, source, page);
                return;
            }
            default -> {
            }
        }
        if (slot >= PAGE_SIZE) {
            return;
        }
        int index = page * PAGE_SIZE + slot;
        if (index < ids.size()) {
            source.select(player, ids.get(index), click);
            if (player.isOnline()) {
                open(plugin, player, source, page);
            }
        }
    }
}
