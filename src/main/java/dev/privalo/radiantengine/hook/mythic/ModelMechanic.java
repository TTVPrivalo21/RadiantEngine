package dev.privalo.radiantengine.hook.mythic;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.model.ActiveRig;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.core.skills.SkillExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * {@code - model{m=<id>;r=false;hb=true;s=1.0;n=true} @self}
 * <ul>
 *   <li>m / mid / model: model id</li>
 *   <li>r / remove: remove the model instead of adding it</li>
 *   <li>hb / hidebase: make the base entity invisible (default true)</li>
 *   <li>s / scale: model scale</li>
 *   <li>n / nametag: show the entity custom name on tag_ bones (default true)</li>
 * </ul>
 */
final class ModelMechanic extends RadiantMechanic {

    private final boolean remove;
    private final boolean hideBase;
    private final float scale;
    private final boolean useName;

    ModelMechanic(RadiantEngine plugin, SkillExecutor executor, MythicLineConfig mlc) {
        super(plugin, executor, mlc);
        this.remove = mlc.getBoolean(new String[]{"remove", "r"}, false);
        this.hideBase = mlc.getBoolean(new String[]{"hidebase", "hb", "invisible"}, true);
        this.scale = mlc.getFloat(new String[]{"scale", "s"}, 1f);
        this.useName = mlc.getBoolean(new String[]{"nametag", "n"}, true);
    }

    @Override
    protected SkillResult apply(SkillMetadata data, Entity caster, Entity target) {
        if (modelId.isEmpty()) {
            return SkillResult.INVALID_CONFIG;
        }
        if (remove) {
            plugin.getRigManager().removeModel(target, modelId);
            return SkillResult.SUCCESS;
        }
        if (!plugin.getRegistry().contains(modelId)) {
            plugin.getLogger().warning("[MythicMobs] Modelo desconocido: " + modelId);
            return SkillResult.INVALID_CONFIG;
        }
        if (hideBase && target instanceof LivingEntity living && !(target instanceof Player)) {
            living.setInvisible(true);
            plugin.getKeys().setFlag(target, plugin.getKeys().hideBase, true);
        }
        ActiveRig model = plugin.getRigManager().addModel(target, modelId, !(target instanceof Player));
        if (model == null) {
            return SkillResult.ERROR;
        }
        if (Math.abs(scale - 1f) > 1.0E-4) {
            model.setScale(scale);
        }
        model.setUseBaseName(useName);
        return SkillResult.SUCCESS;
    }
}
