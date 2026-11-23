package dev.privalo.radiantengine.hook.mythic;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.model.ActiveRig;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.core.skills.SkillExecutor;
import org.bukkit.entity.Entity;

/**
 * {@code - dismountmodel @self} dismounts the target from any model seat;
 * {@code - dismountmodel{all=true} @self} ejects every rider of the target's model.
 */
final class DismountModelMechanic extends RadiantMechanic {

    private final boolean all;

    DismountModelMechanic(RadiantEngine plugin, SkillExecutor executor, MythicLineConfig mlc) {
        super(plugin, executor, mlc);
        this.all = mlc.getBoolean(new String[]{"all", "a"}, false);
    }

    @Override
    protected SkillResult apply(SkillMetadata data, Entity caster, Entity target) {
        if (all) {
            ActiveRig model = model(target);
            if (model == null) {
                return SkillResult.INVALID_TARGET;
            }
            plugin.getMountManager().dismountAll(model);
            return SkillResult.SUCCESS;
        }
        return plugin.getMountManager().dismount(target) ? SkillResult.SUCCESS : SkillResult.CONDITION_FAILED;
    }
}
