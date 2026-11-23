package dev.privalo.radiantengine.util;

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Paper-only helpers. This class is only loaded when Paper's TeleportFlag API exists at runtime.
 */
final class PaperTeleport {

    private PaperTeleport() {
    }

    static boolean teleportRetainingPassengers(Entity entity, Location location) {
        return entity.teleport(location, TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }
}
