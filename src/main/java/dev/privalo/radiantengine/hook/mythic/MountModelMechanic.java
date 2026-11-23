package dev.privalo.radiantengine.hook.mythic;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.model.ActiveRig;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.core.skills.SkillExecutor;
import org.bukkit.entity.Entity;

import java.util.Locale;

/**
 * {@code - mountmodel{m=<id>;d=true;p=<seat bone>} @trigger} - mounts the target on the caster's model.
 * {@code d=true} uses the driver seat ("mount" bone) so the target controls the caster with WASD + jump.
 */
final class MountModelMechanic extends RadiantMechanic {

    private final boolean driver;
    private final String seat;

    MountModelMechanic(RadiantEngine plugin, SkillExecutor executor, MythicLineConfig mlc) {
        super(plugin, executor, mlc);
        this.driver = mlc.getBoolean(new String[]{"driver", "d"}, true);
        String p = mlc.getString(new String[]{"pbone", "p", "seat"}, "");
        this.seat = p.isEmpty() ? null : p.toLowerCase(Locale.ROOT);
    }

    @Override
    protected SkillResult apply(SkillMetadata data, Entity caster, Entity target) {
        if (caster == null || caster.equals(target)) {
            return SkillResult.INVALID_TARGET;
        }
        ActiveRig model = model(caster);
        if (model == null) {
            return SkillResult.INVALID_TARGET;
        }
        return plugin.getMountManager().mount(target, model, seat, driver) ? SkillResult.SUCCESS : SkillResult.CONDITION_FAILED;
    }
}
