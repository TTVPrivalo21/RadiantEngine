package dev.privalo.radiantengine.hook.mythic;

import dev.privalo.radiantengine.RadiantEngine;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.core.skills.SkillExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * {@code - disguise{m=<id>} @trigger} - disguises a player as a model.
 */
final class DisguiseMechanic extends RadiantMechanic {

    DisguiseMechanic(RadiantEngine plugin, SkillExecutor executor, MythicLineConfig mlc) {
        super(plugin, executor, mlc);
    }

    @Override
    protected SkillResult apply(SkillMetadata data, Entity caster, Entity target) {
        if (!(target instanceof Player player) || modelId.isEmpty()) {
            return SkillResult.INVALID_TARGET;
        }
        return plugin.getDisguiseManager().disguise(player, modelId) != null ? SkillResult.SUCCESS : SkillResult.ERROR;
    }
}
