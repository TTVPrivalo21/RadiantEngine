package dev.privalo.radiantengine.generator;

import dev.privalo.radiantengine.blueprint.BoneBlueprint;
import dev.privalo.radiantengine.blueprint.RigBlueprint;
import dev.privalo.radiantengine.config.Settings;
import dev.privalo.radiantengine.util.FileUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Registers every bone item inside Oraxen:
 * <ul>
 *   <li>{@code plugins/Oraxen/items/<items-file>} with one item per bone (explicit custom_model_data and model path)</li>
 *   <li>{@code plugins/Oraxen/pack/assets/<ns>/...} with the generated models and textures</li>
 * </ul>
 * Only file IO is used, so no Oraxen class is loaded here.
 */
public final class OraxenExporter {

    private final File oraxenFolder;
    private final File packFolder;
    private final Settings settings;

    public OraxenExporter(File oraxenFolder, File packFolder, Settings settings) {
        this.oraxenFolder = oraxenFolder;
        this.packFolder = packFolder;
        this.settings = settings;
    }

    public static String itemId(String model, String bone) {
        return ("radiant_" + model + "_" + bone).replaceAll("[^a-z0-9_]", "_");
    }

    /**
     * @return true when the exported files differ from what was there before
     */
    public boolean export(Collection<RigBlueprint> blueprints) throws IOException {
        String ns = settings.namespace;
        File itemsFile = new File(oraxenFolder, "items/" + settings.oraxenItemsFile);
        File targetAssets = new File(oraxenFolder, "pack/assets/" + ns);
        String before = FileUtil.hash(itemsFile, targetAssets);

        FileUtil.deleteRecursively(targetAssets);
        FileUtil.copyRecursively(new File(packFolder, "assets/" + ns), targetAssets);

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "Generado automaticamente por RadiantEngine. No editar: se sobrescribe en cada /radiant reload.",
                "Cada entrada es un hueso renderizable de un modelo Blockbench."));
        String material = settings.baseItem.name();
        for (RigBlueprint bp : blueprints) {
            if (bp.getIconCustomModelData() > 0) {
                ConfigurationSection icon = yaml.createSection(itemId(bp.getId(), ResourcePackGenerator.ICON));
                icon.set("displayname", "<aqua>" + bp.getId());
                icon.set("material", material);
                icon.set("excludeFromInventory", true);
                ConfigurationSection pack = icon.createSection("Pack");
                pack.set("generate_model", false);
                pack.set("model", bp.getIconModelPath());
                pack.set("custom_model_data", bp.getIconCustomModelData());
            }
            for (BoneBlueprint bone : bp.getBones().values()) {
                if (!bone.isRenderable()) {
                    continue;
                }
                ConfigurationSection item = yaml.createSection(itemId(bp.getId(), bone.getName()));
                item.set("displayname", "<white>" + bp.getId() + " / " + bone.getName());
                item.set("material", material);
                item.set("excludeFromInventory", true);
                item.set("excludeFromCommands", false);
                ConfigurationSection pack = item.createSection("Pack");
                pack.set("generate_model", false);
                pack.set("model", bone.getModelPath());
                pack.set("custom_model_data", bone.getCustomModelData());
            }
        }
        File parent = itemsFile.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("No se pudo crear " + parent);
        }
        yaml.save(itemsFile);
        return !before.equals(FileUtil.hash(itemsFile, targetAssets));
    }
}
