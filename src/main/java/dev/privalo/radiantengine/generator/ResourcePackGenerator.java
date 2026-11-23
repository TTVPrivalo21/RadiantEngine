package dev.privalo.radiantengine.generator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.privalo.radiantengine.blueprint.BoneBlueprint;
import dev.privalo.radiantengine.blueprint.RigBlueprint;
import dev.privalo.radiantengine.blueprint.TextureData;
import dev.privalo.radiantengine.config.Settings;
import dev.privalo.radiantengine.parser.BlockbenchParser;
import dev.privalo.radiantengine.util.FileUtil;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Imports every .bbmodel and generates a standalone resource pack:
 * <ul>
 *   <li>{@code assets/<ns>/models/<model>/<bone>.json} - one item model per bone</li>
 *   <li>{@code assets/<ns>/textures/item/radiant/<model>/*.png(.mcmeta)}</li>
 *   <li>{@code assets/minecraft/models/item/<base>.json} - legacy custom_model_data overrides (1.21 - 1.21.3)</li>
 *   <li>{@code assets/minecraft/items/<base>.json} - range_dispatch item definition with dye tint (1.21.4 - 26.x)</li>
 * </ul>
 * This class performs file IO only and is safe to run off the main thread.
 */
public final class ResourcePackGenerator {

    public record Output(Map<String, RigBlueprint> blueprints, List<String> log, int bones, boolean oraxenExported,
                         boolean oraxenChanged) {
    }

    /** Pseudo bone name used for the whole-model icon. */
    public static final String ICON = "__icon";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Gson PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private final File dataFolder;
    private final File blueprintFolder;
    private final File packFolder;
    private final File zipFile;

    public ResourcePackGenerator(File dataFolder) {
        this.dataFolder = dataFolder;
        this.blueprintFolder = new File(dataFolder, "blueprints");
        this.packFolder = new File(dataFolder, "resource_pack");
        this.zipFile = new File(dataFolder, "resource_pack.zip");
        //noinspection ResultOfMethodCallIgnored
        blueprintFolder.mkdirs();
    }

    public File getBlueprintFolder() {
        return blueprintFolder;
    }

    public File getPackFolder() {
        return packFolder;
    }

    public File getZipFile() {
        return zipFile;
    }

    public Output run(Settings settings, File oraxenFolder) {
        List<String> log = new ArrayList<>();
        Map<String, RigBlueprint> blueprints = new LinkedHashMap<>();
        CustomModelDataRegistry cmd = new CustomModelDataRegistry(new File(dataFolder, "cmd-cache.yml"), settings.customModelDataStart);
        String ns = settings.namespace;

        List<File> files = FileUtil.listRecursively(blueprintFolder, BlockbenchParser::isBlockbenchFile);
        Map<RigBlueprint, Map<BoneBlueprint, JsonObject>> generated = new LinkedHashMap<>();
        Map<RigBlueprint, JsonObject> icons = new LinkedHashMap<>();
        int boneCount = 0;
        for (File file : files) {
            String id = BlockbenchParser.modelIdFromFile(file);
            if (blueprints.containsKey(id)) {
                log.add("WARN [" + id + "] Ya existe un modelo con este id; se omite " + file.getPath());
                continue;
            }
            BlockbenchParser.Result result;
            try {
                result = new BlockbenchParser().parse(file);
            } catch (Exception e) {
                log.add("ERROR [" + id + "] No se pudo leer " + file.getName() + ": " + e);
                continue;
            }
            RigBlueprint bp = result.blueprint();
            List<String> warnings = new ArrayList<>(result.warnings());
            Map<BoneBlueprint, JsonObject> models = new LinkedHashMap<>();
            for (BoneBlueprint bone : bp.getBones().values()) {
                if (!bone.hasGeometry() || bone.isGhost()) {
                    continue;
                }
                JsonObject model = ItemModelBuilder.build(ns, bp, bone, settings.maxElementExtent, warnings);
                if (model == null) {
                    continue;
                }
                bone.setCustomModelData(cmd.get(bp.getId(), bone.getName()));
                bone.setModelPath(ns + ":" + bp.getId() + "/" + bone.getName());
                models.put(bone, model);
                boneCount++;
            }
            JsonObject icon = ItemModelBuilder.buildIcon(ns, bp);
            if (icon != null) {
                bp.setIcon(cmd.get(bp.getId(), ICON), ns + ":" + bp.getId() + "/" + ICON);
                icons.put(bp, icon);
            }
            for (String w : warnings) {
                log.add("WARN [" + id + "] " + w);
            }
            log.add("INFO [" + id + "] " + bp.getBones().size() + " huesos, " + models.size() + " renderizables, "
                    + bp.getAnimations().size() + " animaciones.");
            blueprints.put(id, bp);
            generated.put(bp, models);
        }

        try {
            cmd.save();
        } catch (IOException e) {
            log.add("ERROR No se pudo guardar cmd-cache.yml: " + e.getMessage());
        }

        try {
            writePack(settings, generated, icons);
            if (settings.createZip) {
                FileUtil.zipDirectory(packFolder, zipFile);
            }
            log.add("INFO Resource pack generado en " + packFolder.getPath() + (settings.createZip ? " (+ zip)" : ""));
        } catch (IOException e) {
            log.add("ERROR Fallo al generar el resource pack: " + e);
        }

        boolean exported = false;
        boolean changed = false;
        if (oraxenFolder != null && settings.oraxenEnabled) {
            try {
                changed = new OraxenExporter(oraxenFolder, packFolder, settings).export(generated.keySet());
                exported = true;
                log.add("INFO Modelos exportados a Oraxen (" + (changed ? "con cambios" : "sin cambios") + ").");
            } catch (IOException e) {
                log.add("ERROR Fallo al exportar a Oraxen: " + e);
            }
        }
        return new Output(blueprints, log, boneCount, exported, changed);
    }

    private void writePack(Settings settings, Map<RigBlueprint, Map<BoneBlueprint, JsonObject>> generated,
                           Map<RigBlueprint, JsonObject> icons) throws IOException {
        String ns = settings.namespace;
        String base = settings.baseItem.getKey().getKey();
        FileUtil.deleteRecursively(new File(packFolder, "assets/" + ns));
        File legacyBase = new File(packFolder, "assets/minecraft/models/item/" + base + ".json");
        File itemDefinition = new File(packFolder, "assets/minecraft/items/" + base + ".json");

        List<Map.Entry<Integer, String>> allModels = new ArrayList<>();
        for (Map.Entry<RigBlueprint, Map<BoneBlueprint, JsonObject>> entry : generated.entrySet()) {
            RigBlueprint bp = entry.getKey();
            for (Map.Entry<BoneBlueprint, JsonObject> m : entry.getValue().entrySet()) {
                BoneBlueprint bone = m.getKey();
                FileUtil.writeString(new File(packFolder, "assets/" + ns + "/models/" + bp.getId() + "/" + bone.getName() + ".json"),
                        GSON.toJson(m.getValue()));
                allModels.add(Map.entry(bone.getCustomModelData(), bone.getModelPath()));
            }
            JsonObject icon = icons.get(bp);
            if (icon != null) {
                FileUtil.writeString(new File(packFolder, "assets/" + ns + "/models/" + bp.getId() + "/" + ICON + ".json"),
                        GSON.toJson(icon));
                allModels.add(Map.entry(bp.getIconCustomModelData(), bp.getIconModelPath()));
            }
            for (TextureData tex : bp.getTextures()) {
                if (tex.getPng() == null) {
                    continue;
                }
                File png = new File(packFolder, "assets/" + ns + "/textures/item/radiant/" + bp.getId() + "/" + tex.getName() + ".png");
                FileUtil.writeBytes(png, tex.getPng());
                if (tex.getMcmeta() != null) {
                    FileUtil.writeString(new File(png.getPath() + ".mcmeta"), tex.getMcmeta());
                }
            }
        }
        allModels.sort(Map.Entry.comparingByKey());
        GuiTextures.write(packFolder, ns);

        // Legacy (<= 1.21.3) overrides
        JsonObject legacy = new JsonObject();
        legacy.addProperty("parent", "minecraft:item/generated");
        JsonObject legacyTextures = new JsonObject();
        legacyTextures.addProperty("layer0", "minecraft:item/" + base);
        legacy.add("textures", legacyTextures);
        JsonArray overrides = new JsonArray();
        for (Map.Entry<Integer, String> m : allModels) {
            JsonObject predicate = new JsonObject();
            predicate.addProperty("custom_model_data", m.getKey());
            JsonObject override = new JsonObject();
            override.add("predicate", predicate);
            override.addProperty("model", m.getValue());
            overrides.add(override);
        }
        legacy.add("overrides", overrides);
        FileUtil.writeString(legacyBase, PRETTY.toJson(legacy));

        // 1.21.4+ item definition
        boolean dyeable = base.startsWith("leather_");
        JsonObject fallback = modelEntry("minecraft:item/" + base, dyeable ? defaultDye(base) : null);
        JsonObject dispatch = new JsonObject();
        dispatch.addProperty("type", "minecraft:range_dispatch");
        dispatch.addProperty("property", "minecraft:custom_model_data");
        dispatch.addProperty("index", 0);
        JsonArray entries = new JsonArray();
        for (Map.Entry<Integer, String> m : allModels) {
            JsonObject e = new JsonObject();
            e.addProperty("threshold", m.getKey());
            e.add("model", modelEntry(m.getValue(), -1));
            entries.add(e);
        }
        dispatch.add("entries", entries);
        dispatch.add("fallback", fallback);
        JsonObject definition = new JsonObject();
        definition.add("model", dispatch);
        FileUtil.writeString(itemDefinition, PRETTY.toJson(definition));

        // pack.mcmeta (pack_format/supported_formats for 1.21-1.21.8, min/max_format for 1.21.9+ and 26.x)
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 34);
        JsonObject supported = new JsonObject();
        supported.addProperty("min_inclusive", 34);
        supported.addProperty("max_inclusive", 999);
        pack.add("supported_formats", supported);
        pack.addProperty("min_format", 34);
        pack.addProperty("max_format", 999);
        pack.addProperty("description", settings.packDescription);
        JsonObject mcmeta = new JsonObject();
        mcmeta.add("pack", pack);
        FileUtil.writeString(new File(packFolder, "pack.mcmeta"), PRETTY.toJson(mcmeta));
        FileUtil.writeBytes(new File(packFolder, "pack.png"), packIcon());
    }

    private static JsonObject modelEntry(String model, Integer dyeDefault) {
        JsonObject m = new JsonObject();
        m.addProperty("type", "minecraft:model");
        m.addProperty("model", model);
        if (dyeDefault != null) {
            JsonObject tint = new JsonObject();
            tint.addProperty("type", "minecraft:dye");
            tint.addProperty("default", dyeDefault);
            JsonArray tints = new JsonArray();
            tints.add(tint);
            m.add("tints", tints);
        }
        return m;
    }

    private static int defaultDye(String base) {
        // Vanilla leather default color (#A06540)
        return base.toLowerCase(Locale.ROOT).startsWith("leather_") ? -6265536 : -1;
    }

    private static byte[] packIcon() throws IOException {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(0x1E3C72), 128, 128, new Color(0x2AF598)));
        g.fillRect(0, 0, 128, 128);
        g.setColor(new Color(255, 255, 255, 220));
        int[][] cubes = {{40, 30}, {64, 44}, {40, 58}, {64, 72}};
        for (int[] c : cubes) {
            g.fillRect(c[0], c[1], 22, 22);
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
