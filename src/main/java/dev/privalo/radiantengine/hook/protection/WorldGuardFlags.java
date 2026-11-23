package dev.privalo.radiantengine.hook.protection;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;

/**
 * Custom WorldGuard flags: {@code radiant-allow-spawn} and {@code radiant-allow-mounts} (both allow by default).
 */
final class WorldGuardFlags {

    static StateFlag ALLOW_SPAWN;
    static StateFlag ALLOW_MOUNTS;

    private WorldGuardFlags() {
    }

    static void register() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        ALLOW_SPAWN = register(registry, "radiant-allow-spawn");
        ALLOW_MOUNTS = register(registry, "radiant-allow-mounts");
    }

    private static StateFlag register(FlagRegistry registry, String name) {
        StateFlag flag = new StateFlag(name, true);
        try {
            registry.register(flag);
            return flag;
        } catch (FlagConflictException e) {
            Flag<?> existing = registry.get(name);
            if (existing instanceof StateFlag state) {
                return state;
            }
            throw new IllegalStateException("Flag '" + name + "' ya registrado con otro tipo", e);
        }
    }
}
