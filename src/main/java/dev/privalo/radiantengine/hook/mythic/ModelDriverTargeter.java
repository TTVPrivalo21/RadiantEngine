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
 * {@code @ModelDriver{m=<id>}} - the entity sitting on the driver ("mount") seat of the caster's model.
 */
final class ModelDriverTargeter extends RadiantTargeter {

    ModelDriverTargeter(RadiantEngine plugin, SkillExecutor executor, MythicLineConfig mlc) {
        super(plugin, executor, mlc);
    }

    @Override
    public Collection<AbstractEntity> getEntities(SkillMetadata data) {
        List<Entity> list = new ArrayList<>();
        for (ActiveRig model : casterModels(data)) {
            Entity driver = plugin.getMountManager().getDriver(model);
            if (driver != null) {
                list.add(driver);
            }
        }
        return adapt(list);
    }
}
