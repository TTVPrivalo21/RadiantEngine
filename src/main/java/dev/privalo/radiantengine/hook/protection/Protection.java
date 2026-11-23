package dev.privalo.radiantengine.hook.protection;

import dev.privalo.radiantengine.RadiantEngine;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Entry point for region checks. Without WorldGuard (or with worldguard.enabled: false) everything is allowed.
 */
public final class Protection {

    public static final String BYPASS = "radiantengine.bypass.region";

    private final RadiantEngine plugin;
    private RegionChecker checker;

    public Protection(RadiantEngine plugin) {
        this.plugin = plugin;
    }

    /** Called on enable, after WorldGuard has loaded. */
    public void detect() {
        checker = null;
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard") && WorldGuardFlagsHolder.registered()) {
            try {
                checker = new WorldGuardHook();
                plugin.getLogger().info("Integracion con WorldGuard activada (flags radiant-allow-spawn, radiant-allow-mounts).");
            } catch (Throwable t) {
                plugin.getLogger().warning("No se pudo activar WorldGuard: " + t);
            }
        }
    }

    private boolean active(Player player) {
        return checker != null && plugin.getSettings().worldGuardEnabled && !player.hasPermission(BYPASS);
    }

    public boolean canSpawn(Player player, Location location) {
        return !active(player) || location == null || checker.canSpawn(player, location);
    }

    public boolean canMount(Player player, Location location) {
        return !active(player) || location == null || checker.canMount(player, location);
    }

    /** Checks spawning and tells the player when it is denied. */
    public boolean checkSpawn(Player player, Location location) {
        if (canSpawn(player, location)) {
            return true;
        }
        plugin.getLang().send(player, "region-denied-spawn");
        return false;
    }

    /** Checks mounting and tells the player when it is denied. */
    public boolean checkMount(Player player, Location location) {
        if (canMount(player, location)) {
            return true;
        }
        plugin.getLang().send(player, "region-denied-mount");
        return false;
    }
}
