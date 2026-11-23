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
 * {@code - state{m=<id>;s=<animation>;li=0.1;lo=0.1;sp=1;r=false;f=true} @self}
 */
final class StateMechanic extends RadiantMechanic {

    private final String state;
    private final double lerpIn;
    private final double lerpOut;
    private final double speed;
    private final boolean remove;
    private final boolean force;

    StateMechanic(RadiantEngine plugin, SkillExecutor executor, MythicLineConfig mlc) {
        super(plugin, executor, mlc);
        this.state = mlc.getString(new String[]{"state", "s", "animation", "anim", "a"}, "").toLowerCase(Locale.ROOT);
        this.lerpIn = mlc.getDouble(new String[]{"lerpin", "li"}, 0.1);
        this.lerpOut = mlc.getDouble(new String[]{"lerpout", "lo"}, 0.1);
        this.speed = mlc.getDouble(new String[]{"speed", "sp"}, 1.0);
        this.remove = mlc.getBoolean(new String[]{"remove", "r"}, false);
        this.force = mlc.getBoolean(new String[]{"force", "f"}, true);
    }

    @Override
    protected SkillResult apply(SkillMetadata data, Entity caster, Entity target) {
        ActiveRig model = model(target);
        if (model == null || state.isEmpty()) {
            return SkillResult.INVALID_TARGET;
        }
        if (remove) {
            model.stopAnimation(state);
        } else {
            model.playAnimation(state, lerpIn, lerpOut, speed, force);
        }
        return SkillResult.SUCCESS;
    }
}
