package dev.privalo.radiantengine.listener;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.animation.runtime.RigState;
import dev.privalo.radiantengine.gui.RigMenu;
import dev.privalo.radiantengine.model.ActiveRig;
import dev.privalo.radiantengine.model.RiggedEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Disguise synchronisation (attack swing, join/quit), seat dismounting and GUI clicks.
 */
public final class PlayerListener implements Listener {

    private final RadiantEngine plugin;

    public PlayerListener(RadiantEngine plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getDisguiseManager().handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getMountManager().dismount(player);
        plugin.getDisguiseManager().handleQuit(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        RiggedEntity me = plugin.getRigManager().get(event.getPlayer());
        if (me == null || !me.isDisguise()) {
            return;
        }
        for (ActiveRig model : me.models()) {
            if (model.getStateController().hasAnimation(RigState.ATTACK)) {
                model.getStateController().play(RigState.ATTACK, true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        plugin.getMountManager().handleDismount(event.getEntity(), event.getDismounted());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof RigMenu menu) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player && event.getClickedInventory() != null
                    && event.getClickedInventory().getHolder() instanceof RigMenu) {
                menu.handleClick(player, event.getRawSlot(), event.getClick());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof RigMenu) {
            event.setCancelled(true);
        }
    }
}
