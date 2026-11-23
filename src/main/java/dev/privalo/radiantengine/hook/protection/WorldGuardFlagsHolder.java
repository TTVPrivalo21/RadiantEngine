package dev.privalo.radiantengine.hook.protection;

import org.bukkit.Bukkit;

import java.util.logging.Logger;

/**
 * Registers the custom WorldGuard flags during onLoad (WorldGuard only accepts new flags before it enables).
 * Keeps no WorldGuard type in its signature so it can be referenced safely without WorldGuard installed.
 */
public final class WorldGuardFlagsHolder {

    private static boolean registered;

    private WorldGuardFlagsHolder() {
    }

    public static void registerIfPresent(Logger logger) {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
            return;
        }
        try {
            WorldGuardFlags.register();
            registered = true;
        } catch (Throwable t) {
            logger.warning("No se pudieron registrar los flags de WorldGuard: " + t);
        }
    }

    public static boolean registered() {
        return registered;
    }
}
