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
 * {@code @ModelPassengers{m=<id>;driver=true}} - every rider seated on the caster's model.
 */
final class ModelPassengersTargeter extends RadiantTargeter {

    private final boolean includeDriver;

    ModelPassengersTargeter(RadiantEngine plugin, SkillExecutor executor, MythicLineConfig mlc) {
        super(plugin, executor, mlc);
        this.includeDriver = mlc.getBoolean(new String[]{"driver", "d"}, true);
    }

    @Override
    public Collection<AbstractEntity> getEntities(SkillMetadata data) {
        List<Entity> list = new ArrayList<>();
        for (ActiveRig model : casterModels(data)) {
            Entity driver = plugin.getMountManager().getDriver(model);
            for (Entity rider : plugin.getMountManager().getPassengers(model)) {
                if (includeDriver || !rider.equals(driver)) {
                    list.add(rider);
                }
            }
        }
        return adapt(list);
    }
}
