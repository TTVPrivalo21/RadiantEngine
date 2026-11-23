package dev.privalo.radiantengine.hook.mythic;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.model.ActiveRig;
import dev.privalo.radiantengine.model.RiggedEntity;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.core.skills.SkillExecutor;
import io.lumine.mythic.core.skills.SkillMechanic;
import org.bukkit.entity.Entity;

import java.util.Locale;

/**
 * Base class for the RadiantEngine MythicMobs mechanics: runs on the main thread and targets entities.
 */
abstract class RadiantMechanic extends SkillMechanic implements ITargetedEntitySkill {

    protected final RadiantEngine plugin;
    protected final String modelId;

    protected RadiantMechanic(RadiantEngine plugin, SkillExecutor executor, MythicLineConfig mlc) {
        super(executor, mlc.getLine(), mlc);
        this.plugin = plugin;
        this.modelId = mlc.getString(new String[]{"modelid", "mid", "m", "model"}, "").toLowerCase(Locale.ROOT);
        setAsyncSafe(false);
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        Entity bukkit = target == null ? null : target.getBukkitEntity();
        if (bukkit == null) {
            return SkillResult.INVALID_TARGET;
        }
        Entity caster = data.getCaster() == null || data.getCaster().getEntity() == null
                ? null : data.getCaster().getEntity().getBukkitEntity();
        return apply(data, caster, bukkit);
    }

    protected abstract SkillResult apply(SkillMetadata data, Entity caster, Entity target);

    /** Model with {@link #modelId} on the entity, or its first model when no id was given. */
    protected ActiveRig model(Entity entity) {
        RiggedEntity me = plugin.getRigManager().get(entity);
        if (me == null || me.isEmpty()) {
            return null;
        }
        if (modelId.isEmpty()) {
            return me.models().iterator().next();
        }
        return me.getModel(modelId);
    }
}
