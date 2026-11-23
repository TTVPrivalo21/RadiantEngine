package dev.privalo.radiantengine.gui;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.blueprint.BoneBlueprint;
import dev.privalo.radiantengine.blueprint.RigBlueprint;
import dev.privalo.radiantengine.model.ActiveRig;
import dev.privalo.radiantengine.model.RiggedEntity;
import dev.privalo.radiantengine.util.EntityCompat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Paginated model browser: preview, spawn, summon, disguise and manage loaded models.
 */
public final class RigMenu implements InventoryHolder {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_UNDISGUISE = 47;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_CLEAR = 51;
    private static final int SLOT_NEXT = 53;

    private final RadiantEngine plugin;
    private final List<String> ids;
    private final int page;
    private final int pages;
    private final Inventory inventory;

    private final boolean textured;

    private RigMenu(RadiantEngine plugin, int page) {
        this.plugin = plugin;
        this.ids = new ArrayList<>(plugin.getRegistry().all().keySet());
        this.pages = Math.max(1, (ids.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        this.page = Math.max(0, Math.min(page, pages - 1));
        this.textured = plugin.getSettings().guiCustomTexture && PaperGui.isAvailable();
        String title = (textured ? plugin.getSettings().guiTexturedTitle : plugin.getSettings().guiTitle)
                .replace("{page}", String.valueOf(this.page + 1))
                .replace("{pages}", String.valueOf(pages));
        if (textured) {
            this.inventory = PaperGui.create(this, 54, plugin.getSettings().namespace, title);
        } else {
            String legacy = dev.privalo.radiantengine.text.Lang.legacy(title);
            this.inventory = Bukkit.createInventory(this, 54, legacy.length() > 64 ? legacy.substring(0, 64) : legacy);
        }
        fill();
    }

    public static void open(RadiantEngine plugin, Player player, int page) {
        player.openInventory(new RigMenu(plugin, page).getInventory());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static final String SEP = ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "                         ";

    private void fill() {
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < ids.size(); i++) {
            RigBlueprint bp = plugin.getRegistry().get(ids.get(start + i));
            if (bp != null) {
                inventory.setItem(i, icon(bp));
            }
        }
        if (!textured) {
            ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, " ");
            for (int s = 45; s < 54; s++) {
                inventory.setItem(s, filler);
            }
        }
        if (page > 0) {
            inventory.setItem(SLOT_PREV, button(Material.SPECTRAL_ARROW, ChatColor.AQUA + "« Pagina anterior",
                    ChatColor.GRAY + "Ir a la pagina " + ChatColor.WHITE + page));
        }
        if (page < pages - 1) {
            inventory.setItem(SLOT_NEXT, button(Material.SPECTRAL_ARROW, ChatColor.AQUA + "Pagina siguiente »",
                    ChatColor.GRAY + "Ir a la pagina " + ChatColor.WHITE + (page + 2)));
        }
        inventory.setItem(SLOT_UNDISGUISE, button(Material.NAME_TAG, ChatColor.LIGHT_PURPLE + "Quitar disfraz",
                ChatColor.GRAY + "Vuelve a tu apariencia normal."));
        inventory.setItem(SLOT_CLEAR, button(Material.TNT, ChatColor.RED + "Limpiar modelos cercanos",
                ChatColor.GRAY + "Elimina en 12 bloques los modelos",
                ChatColor.GRAY + "creados por RadiantEngine."));
        inventory.setItem(SLOT_INFO, button(Material.KNOWLEDGE_BOOK, ChatColor.GOLD + "" + ChatColor.BOLD + "RadiantEngine",
                SEP,
                ChatColor.GRAY + "Modelos cargados  " + ChatColor.WHITE + ids.size(),
                ChatColor.GRAY + "Entidades activas  " + ChatColor.WHITE + plugin.getRigManager().all().size(),
                SEP,
                ChatColor.AQUA + "Click izquierdo " + ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + "invocar mob",
                ChatColor.LIGHT_PURPLE + "Click derecho " + ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + "disfrazarse",
                ChatColor.GREEN + "Shift + izquierdo " + ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + "modelo estatico",
                ChatColor.YELLOW + "Shift + derecho " + ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + "previsualizar "
                        + plugin.getSettings().previewSeconds + "s"));
    }

    private ItemStack icon(RigBlueprint bp) {
        ItemStack item = plugin.getSettings().guiModelIcons ? plugin.getItemResolver().iconItem(bp) : null;
        if (item == null) {
            item = new ItemStack(Material.ARMOR_STAND);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + bp.getId());
            List<String> lore = new ArrayList<>();
            lore.add(SEP);
            lore.add(ChatColor.GRAY + "Huesos  " + ChatColor.WHITE + bp.getBones().size()
                    + ChatColor.DARK_GRAY + "  (" + bp.getRenderableBones().size() + " visibles)");
            lore.add(ChatColor.GRAY + "Animaciones  " + ChatColor.WHITE + bp.getAnimations().size());
            if (!bp.getAnimations().isEmpty()) {
                String names = String.join(", ", bp.getAnimations().keySet());
                lore.add(ChatColor.DARK_AQUA + " " + (names.length() > 40 ? names.substring(0, 37) + "..." : names));
            }
            lore.add(ChatColor.GRAY + "Hitbox  " + ChatColor.WHITE + (bp.getMainHitbox() == null ? "entidad base"
                    : String.format("%.2f × %.2f", bp.getMainHitbox().horizontal(), bp.getMainHitbox().height())));
            long seats = bp.getBones().values().stream().filter(BoneBlueprint::isSeat).count();
            if (seats > 0) {
                lore.add(ChatColor.GRAY + "Asientos  " + ChatColor.WHITE + seats);
            }
            lore.add(SEP);
            lore.add(ChatColor.AQUA + "Izq " + ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + "mob   "
                    + ChatColor.LIGHT_PURPLE + "Der " + ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + "disfraz");
            lore.add(ChatColor.GREEN + "Shift+Izq " + ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + "estatico   "
                    + ChatColor.YELLOW + "Shift+Der " + ChatColor.DARK_GRAY + "» " + ChatColor.GRAY + "preview");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack button(Material material, String name, String... lore) {
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
    public void handleClick(Player player, int slot, ClickType click) {
        if (slot < 0 || slot >= 54) {
            return;
        }
        switch (slot) {
            case SLOT_PREV -> {
                if (page > 0) {
                    open(plugin, player, page - 1);
                }
                return;
            }
            case SLOT_NEXT -> {
                if (page < pages - 1) {
                    open(plugin, player, page + 1);
                }
                return;
            }
            case SLOT_UNDISGUISE -> {
                player.closeInventory();
                if (plugin.getDisguiseManager().undisguise(player)) {
                    plugin.getLang().send(player, "menu-undisguised");
                } else {
                    plugin.getLang().send(player, "menu-not-disguised");
                }
                return;
            }
            case SLOT_CLEAR -> {
                if (!player.hasPermission("radiantengine.command.kill")) {
                    plugin.getLang().send(player, "no-permission");
                    return;
                }
                player.closeInventory();
                int removed = 0;
                for (RiggedEntity me : new ArrayList<>(plugin.getRigManager().all())) {
                    Entity base = me.getBase();
                    if (base instanceof Player || !base.getWorld().equals(player.getWorld())
                            || base.getLocation().distanceSquared(player.getLocation()) > 144) {
                        continue;
                    }
                    if (plugin.getKeys().flag(base, plugin.getKeys().owned)) {
                        plugin.getRigManager().removeRig(base, true);
                        removed++;
                    }
                }
                plugin.getLang().send(player, "menu-cleared", "count", removed);
                return;
            }
            case SLOT_INFO -> {
                return;
            }
            default -> {
            }
        }
        if (slot >= PAGE_SIZE) {
            return;
        }
        int index = page * PAGE_SIZE + slot;
        if (index >= ids.size()) {
            return;
        }
        String id = ids.get(index);
        player.closeInventory();
        switch (click) {
            case LEFT -> {
                if (!check(player, "spawn") || !plugin.getProtection().checkSpawn(player, player.getLocation())) {
                    return;
                }
                EntityType type = EntityCompat.entityType(plugin.getSettings().defaultEntity);
                ActiveRig m = plugin.getRigManager().spawnRig(player.getLocation(), type, id);
                plugin.getLang().send(player, m == null ? "menu-spawn-failed" : "menu-spawned", "model", id);
            }
            case RIGHT -> {
                if (!check(player, "disguise")) {
                    return;
                }
                ActiveRig m = plugin.getDisguiseManager().disguise(player, id);
                plugin.getLang().send(player, m == null ? "disguise-failed" : "menu-disguised", "model", id);
            }
            case SHIFT_LEFT -> {
                if (!check(player, "summon") || !plugin.getProtection().checkSpawn(player, inFront(player, 2.5))) {
                    return;
                }
                ActiveRig m = plugin.getRigManager().summonProp(inFront(player, 2.5), id, true);
                plugin.getLang().send(player, m == null ? "summon-failed" : "menu-summoned");
            }
            case SHIFT_RIGHT -> {
                if (plugin.getProtection().checkSpawn(player, inFront(player, 3.0))) {
                    preview(player, id);
                }
            }
            default -> {
            }
        }
    }

    private boolean check(Player player, String perm) {
        if (player.hasPermission("radiantengine.command." + perm)) {
            return true;
        }
        plugin.getLang().send(player, "no-permission");
        return false;
    }

    private static Location inFront(Player player, double distance) {
        Location eye = player.getLocation();
        Vector dir = eye.getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(0, 0, 1);
        }
        Location loc = eye.clone().add(dir.normalize().multiply(distance));
        loc.setYaw(eye.getYaw() + 180f);
        loc.setPitch(0f);
        return loc;
    }

    private void preview(Player player, String id) {
        ActiveRig model = plugin.getRigManager().summonProp(inFront(player, 3.0), id, false);
        if (model == null) {
            plugin.getLang().send(player, "menu-spawn-failed", "model", id);
            return;
        }
        Entity anchor = model.getOwner().getBase();
        List<String> animations = new ArrayList<>(model.getBlueprint().getAnimations().keySet());
        plugin.getLang().send(player, "menu-preview", "model", id, "count", animations.size());
        int totalTicks = plugin.getSettings().previewSeconds * 20;
        new BukkitRunnable() {
            int ticks;
            int index;

            @Override
            public void run() {
                if (!anchor.isValid()) {
                    cancel();
                    return;
                }
                if (ticks >= totalTicks) {
                    plugin.getRigManager().removeRig(anchor, true);
                    cancel();
                    return;
                }
                if (ticks % 40 == 0 && !animations.isEmpty()) {
                    String name = animations.get(index++ % animations.size());
                    model.getAnimationHandler().forceStopAll();
                    model.playAnimation(name, 0.1, 0.1, 1.0, true);
                    if (player.isOnline()) {
                        plugin.getLang().send(player, "menu-preview-animation", "model", id, "animation", name);
                    }
                }
                ticks += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }
}
