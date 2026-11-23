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

    /**
     * @param brandingCustomModelData CustomModelData of the custom logo item, or -1 when no custom logo is used
     */
    public record Output(Map<String, RigBlueprint> blueprints, List<String> log, int bones, boolean oraxenExported,
                         boolean oraxenChanged, int brandingCustomModelData, String brandingModel, boolean guiTexture,
                         List<String> guiReport, String zipSha1, long zipSize) {
    }

    /** Pseudo model \/ bone of the custom logo item. */
    public static final String BRANDING_MODEL = "__radiant";
    public static final String BRANDING_BONE = "logo";

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
        Branding branding = Branding.load(dataFolder, settings, log);
        byte[] logoItemTexture = null;
        try {
            logoItemTexture = branding.itemTexture();
        } catch (IOException e) {
            log.add("WARN No se pudo preparar el item del logo: " + e.getMessage());
        }
        int brandingCmd = logoItemTexture == null ? -1 : cmd.get(BRANDING_MODEL, BRANDING_BONE);
        String brandingModel = ns + ":radiant/branding/" + BRANDING_BONE;

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

        PackResult packResult = null;
        String zipSha1 = null;
        long zipSize = 0;
        try {
            packResult = writePack(settings, generated, icons, branding, logoItemTexture, brandingCmd, brandingModel, log);
            brandingCmd = packResult.brandingCmd();
            if (settings.createZip) {
                FileUtil.zipDirectory(packFolder, zipFile);
                zipSha1 = FileUtil.sha1(zipFile);
                zipSize = zipFile.length();
            }
            log.add("INFO Resource pack generado en " + packFolder.getPath() + (settings.createZip ? " (+ zip)" : ""));
        } catch (IOException e) {
            log.add("ERROR Fallo al generar el resource pack: " + e);
        }

        if (settings.bedrockGeneratePack) {
            try {
                int models = BedrockPackGenerator.generate(dataFolder, ns, settings.packDescription, blueprints.values(), packIcon(branding, log), log);
                log.add("INFO Pack Bedrock generado (" + models + " modelos) en " + new File(dataFolder, "bedrock_pack").getPath() + " (+ bedrock_pack.mcpack)");
            } catch (IOException e) {
                log.add("ERROR Fallo al generar el pack Bedrock: " + e);
            }
        }

        boolean exported = false;
        boolean changed = false;
        if (oraxenFolder != null && settings.oraxenEnabled) {
            try {
                changed = new OraxenExporter(oraxenFolder, packFolder, settings).export(generated.keySet(), brandingCmd, brandingModel,
                        settings.brandingOraxenPackIcon ? customPackIcon(branding, log) : null);
                exported = true;
                log.add("INFO Modelos exportados a Oraxen (" + (changed ? "con cambios" : "sin cambios") + ").");
            } catch (IOException e) {
                log.add("ERROR Fallo al exportar a Oraxen: " + e);
            }
        }
        return new Output(blueprints, log, boneCount, exported, changed, brandingCmd, brandingModel,
                packResult != null && packResult.guiTexture(), packResult == null ? List.of() : packResult.report(), zipSha1, zipSize);
    }

    /** Result of writing the pack: what the GUI may use and a report of GUI/icon files. */
    private record PackResult(boolean guiTexture, int brandingCmd, List<String> report) {
    }

    /** A model that gets a CustomModelData; {@code invalidate} clears it when validation fails. */
    private record ModelEntry(int cmd, String path, String label, Runnable invalidate) {
    }

    private PackResult writePack(Settings settings, Map<RigBlueprint, Map<BoneBlueprint, JsonObject>> generated,
                                 Map<RigBlueprint, JsonObject> icons, Branding branding, byte[] logoItemTexture,
                                 int brandingCmd, String brandingModel, List<String> log) throws IOException {
        String ns = settings.namespace;
        String base = settings.baseItem.getKey().getKey();
        FileUtil.deleteRecursively(new File(packFolder, "assets/" + ns));
        File legacyBase = new File(packFolder, "assets/minecraft/models/item/" + base + ".json");
        File itemDefinition = new File(packFolder, "assets/minecraft/items/" + base + ".json");
        List<String> report = new ArrayList<>();

        List<ModelEntry> candidates = new ArrayList<>();
        for (Map.Entry<RigBlueprint, Map<BoneBlueprint, JsonObject>> entry : generated.entrySet()) {
            RigBlueprint bp = entry.getKey();
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
            for (Map.Entry<BoneBlueprint, JsonObject> m : entry.getValue().entrySet()) {
                BoneBlueprint bone = m.getKey();
                FileUtil.writeString(new File(packFolder, "assets/" + ns + "/models/" + bp.getId() + "/" + bone.getName() + ".json"),
                        GSON.toJson(m.getValue()));
                candidates.add(new ModelEntry(bone.getCustomModelData(), bone.getModelPath(),
                        bp.getId() + "/" + bone.getName(), () -> bone.setCustomModelData(-1)));
            }
            JsonObject icon = icons.get(bp);
            if (icon != null) {
                FileUtil.writeString(new File(packFolder, "assets/" + ns + "/models/" + bp.getId() + "/" + ICON + ".json"),
                        GSON.toJson(icon));
                candidates.add(new ModelEntry(bp.getIconCustomModelData(), bp.getIconModelPath(),
                        bp.getId() + " (icono)", () -> bp.setIcon(-1, null)));
            }
        }
        int[] logoCmd = {-1};
        if (logoItemTexture != null && brandingCmd > 0) {
            // Flat logo item (used as icon of the menu info buttons)
            FileUtil.writeBytes(new File(packFolder, "assets/" + ns + "/textures/item/radiant/branding/" + BRANDING_BONE + ".png"), logoItemTexture);
            JsonObject logoModel = new JsonObject();
            logoModel.addProperty("parent", "minecraft:item/generated");
            JsonObject logoTextures = new JsonObject();
            logoTextures.addProperty("layer0", ns + ":item/radiant/branding/" + BRANDING_BONE);
            logoModel.add("textures", logoTextures);
            FileUtil.writeString(new File(packFolder, "assets/" + ns + "/models/radiant/branding/" + BRANDING_BONE + ".json"),
                    GSON.toJson(logoModel));
            logoCmd[0] = brandingCmd;
            candidates.add(new ModelEntry(brandingCmd, brandingModel, "logo", () -> logoCmd[0] = -1));
        }

        // Validate before any CustomModelData / item model is used, so nothing points to a missing file.
        PackValidator validator = new PackValidator(packFolder, log);
        List<Map.Entry<Integer, String>> allModels = new ArrayList<>();
        int icons3d = 0;
        int invalid = 0;
        for (ModelEntry e : candidates) {
            if (validator.model(e.path())) {
                allModels.add(Map.entry(e.cmd(), e.path()));
                File def = validator.asset(e.path(), "items", ".json");
                FileUtil.writeString(def, PRETTY.toJson(itemModelDefinition(e.path())));
                if (e.path().endsWith("/" + ICON)) {
                    icons3d++;
                    report.add("icono " + e.label() + " -> cmd " + e.cmd() + ", " + validator.rel(def));
                }
            } else {
                e.invalidate().run();
                invalid++;
            }
        }
        if (logoCmd[0] > 0) {
            report.add("logo -> cmd " + logoCmd[0] + ", " + validator.rel(validator.asset(brandingModel, "items", ".json"))
                    + (branding.isCustom() ? " (personalizado: " + branding.getSource() + ")" : " (por defecto)"));
        }
        allModels.sort(Map.Entry.comparingByKey());

        GuiTextures.write(packFolder, ns, branding.menuHeaderLogo(64, 13));
        File font = new File(packFolder, "assets/" + ns + "/font/" + GuiTextures.FONT + ".json");
        boolean guiTexture = validator.font(font, ns + ":" + GuiTextures.TEXTURE);
        report.add(0, "fuente " + validator.rel(font) + " + textura "
                + validator.rel(validator.asset(ns + ":" + GuiTextures.TEXTURE, "textures", "")) + (guiTexture ? "" : " (INVALIDA)"));
        report.add(1, "iconos 3D: " + icons3d + ", logo: " + (logoCmd[0] > 0 ? "si" : "no")
                + ", modelos descartados: " + invalid);

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

        // 1.21.4+ base item definition (custom_model_data dispatch, for items without the item_model component)
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
        FileUtil.writeBytes(new File(packFolder, "pack.png"), packIcon(branding, null));
        return new PackResult(guiTexture, logoCmd[0], report);
    }

    /** Modern item definition (assets/&lt;ns&gt;/items/&lt;path&gt;.json) targeted by the item_model component. */
    private static JsonObject itemModelDefinition(String modelPath) {
        JsonObject definition = new JsonObject();
        definition.add("model", modelEntry(modelPath, -1));
        return definition;
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

    /** Custom logo as pack icon, or null when there is none (never the default icon). */
    private static byte[] customPackIcon(Branding branding, List<String> log) {
        try {
            return branding.packIcon();
        } catch (IOException | RuntimeException e) {
            log.add("WARN No se pudo escalar el logo para el pack de Oraxen: " + e.getMessage());
            return null;
        }
    }

    /** Custom logo as pack icon, falling back to the generated default icon. */
    private static byte[] packIcon(Branding branding, List<String> log) throws IOException {
        try {
            byte[] custom = branding.packIcon();
            if (custom != null) {
                return custom;
            }
        } catch (IOException | RuntimeException e) {
            if (log != null) {
                log.add("WARN No se pudo escalar el logo para el icono del pack: " + e.getMessage());
            }
        }
        return packIcon();
    }

    /** Default generated pack icon. */
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
