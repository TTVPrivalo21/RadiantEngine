package dev.privalo.radiantengine.hook.mythic;

import dev.privalo.radiantengine.RadiantEngine;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.core.skills.SkillExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * {@code - undisguise @trigger}
 */
final class UndisguiseMechanic extends RadiantMechanic {

    UndisguiseMechanic(RadiantEngine plugin, SkillExecutor executor, MythicLineConfig mlc) {
        super(plugin, executor, mlc);
    }

    @Override
    protected SkillResult apply(SkillMetadata data, Entity caster, Entity target) {
        if (!(target instanceof Player player)) {
            return SkillResult.INVALID_TARGET;
        }
        return plugin.getDisguiseManager().undisguise(player) ? SkillResult.SUCCESS : SkillResult.CONDITION_FAILED;
    }
}
