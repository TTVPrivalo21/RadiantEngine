package dev.privalo.radiantengine.hook.mythic;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.model.ActiveRig;
import dev.privalo.radiantengine.model.RiggedEntity;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.core.skills.SkillExecutor;
import io.lumine.mythic.core.skills.targeters.IEntitySelector;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Base for RadiantEngine targeters operating on the caster's models.
 */
abstract class RadiantTargeter extends IEntitySelector {

    protected final RadiantEngine plugin;
    protected final String modelId;

    protected RadiantTargeter(RadiantEngine plugin, SkillExecutor executor, MythicLineConfig mlc) {
        super(executor, mlc);
        this.plugin = plugin;
        this.modelId = mlc.getString(new String[]{"modelid", "mid", "m", "model"}, "").toLowerCase(Locale.ROOT);
    }

    protected List<ActiveRig> casterModels(SkillMetadata data) {
        List<ActiveRig> list = new ArrayList<>();
        Entity caster = caster(data);
        RiggedEntity me = caster == null ? null : plugin.getRigManager().get(caster);
        if (me == null) {
            return list;
        }
        for (ActiveRig m : me.models()) {
            if (modelId.isEmpty() || m.getId().equals(modelId)) {
                list.add(m);
            }
        }
        return list;
    }

    protected static Entity caster(SkillMetadata data) {
        if (data.getCaster() == null || data.getCaster().getEntity() == null) {
            return null;
        }
        return data.getCaster().getEntity().getBukkitEntity();
    }

    protected static Collection<AbstractEntity> adapt(Collection<Entity> entities) {
        HashSet<AbstractEntity> out = new HashSet<>();
        for (Entity e : entities) {
            out.add(BukkitAdapter.adapt(e));
        }
        return out;
    }
}
