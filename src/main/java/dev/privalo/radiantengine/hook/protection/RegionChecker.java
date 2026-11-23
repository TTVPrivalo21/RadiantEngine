package dev.privalo.radiantengine.hook.protection;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Region protection backend (WorldGuard).
 */
public interface RegionChecker {

    boolean canSpawn(Player player, Location location);

    boolean canMount(Player player, Location location);
}
