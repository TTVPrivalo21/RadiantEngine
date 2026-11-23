package dev.privalo.radiantengine.hook.mythic;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.config.Settings;
import dev.privalo.radiantengine.model.ActiveRig;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.core.skills.SkillExecutor;
import org.bukkit.Color;
import org.bukkit.entity.Entity;

/**
 * {@code - tint{m=<id>;c=FF0000;d=false} @self} - {@code d=true} flashes the damage tint instead.
 */
final class TintMechanic extends RadiantMechanic {

    private final Color color;
    private final boolean damage;

    TintMechanic(RadiantEngine plugin, SkillExecutor executor, MythicLineConfig mlc) {
        super(plugin, executor, mlc);
        this.color = Settings.parseColor(mlc.getString(new String[]{"color", "c"}, "FFFFFF"), Color.WHITE);
        this.damage = mlc.getBoolean(new String[]{"damage", "d"}, false);
    }

    @Override
    protected SkillResult apply(SkillMetadata data, Entity caster, Entity target) {
        ActiveRig model = model(target);
        if (model == null) {
            return SkillResult.INVALID_TARGET;
        }
        if (damage) {
            model.flashDamageTint();
        } else {
            model.setTint(color);
            plugin.getRigManager().storeTint(target, model.getId(), color);
        }
        return SkillResult.SUCCESS;
    }
}
