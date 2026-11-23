package dev.privalo.radiantengine.hook.protection;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * WorldGuard region checks using the RadiantEngine flags. Region bypass (WorldGuard) is honoured.
 */
final class WorldGuardHook implements RegionChecker {

    private boolean test(Player player, Location location, StateFlag flag) {
        if (flag == null || location.getWorld() == null) {
            return true;
        }
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        if (WorldGuard.getInstance().getPlatform().getSessionManager()
                .hasBypass(localPlayer, BukkitAdapter.adapt(location.getWorld()))) {
            return true;
        }
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        return query.testState(BukkitAdapter.adapt(location), localPlayer, flag);
    }

    @Override
    public boolean canSpawn(Player player, Location location) {
        return test(player, location, WorldGuardFlags.ALLOW_SPAWN);
    }

    @Override
    public boolean canMount(Player player, Location location) {
        return test(player, location, WorldGuardFlags.ALLOW_MOUNTS);
    }
}
