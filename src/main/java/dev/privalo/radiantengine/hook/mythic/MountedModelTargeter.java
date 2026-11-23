package dev.privalo.radiantengine.hook.mythic;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.model.ActiveRig;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.core.skills.SkillExecutor;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * {@code @MountedModel} - the base entity of the model the caster is currently riding.
 */
final class MountedModelTargeter extends RadiantTargeter {

    MountedModelTargeter(RadiantEngine plugin, SkillExecutor executor, MythicLineConfig mlc) {
        super(plugin, executor, mlc);
    }

    @Override
    public Collection<AbstractEntity> getEntities(SkillMetadata data) {
        List<Entity> list = new ArrayList<>();
        Entity caster = caster(data);
        if (caster != null) {
            ActiveRig model = plugin.getMountManager().getMountedModel(caster);
            if (model != null) {
                list.add(model.getOwner().getBase());
            }
        }
        return adapt(list);
    }
}
