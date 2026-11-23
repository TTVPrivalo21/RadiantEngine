package dev.privalo.radiantengine.parser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.privalo.radiantengine.animation.molang.Molang;
import dev.privalo.radiantengine.blueprint.BoneBlueprint;
import dev.privalo.radiantengine.blueprint.Cube;
import dev.privalo.radiantengine.blueprint.Hitbox;
import dev.privalo.radiantengine.blueprint.RigBlueprint;
import dev.privalo.radiantengine.blueprint.TextureData;
import dev.privalo.radiantengine.blueprint.animation.BlueprintAnimation;
import dev.privalo.radiantengine.blueprint.animation.BoneTimeline;
import dev.privalo.radiantengine.blueprint.animation.Channel;
import dev.privalo.radiantengine.blueprint.animation.EffectKeyframe;
import dev.privalo.radiantengine.blueprint.animation.Interpolation;
import dev.privalo.radiantengine.blueprint.animation.Keyframe;
import dev.privalo.radiantengine.blueprint.animation.LoopMode;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Blockbench (.bbmodel) parser. Implements the standard Blockbench rig semantics: outliner hierarchy, special bone prefixes,
 * main/sub hitboxes, cube geometry with per-face UV, embedded textures and animation clips.
 */
public final class BlockbenchParser {

    private static final String[] FACE_DIRECTIONS = {"north", "east", "south", "west", "up", "down"};

    public record Result(RigBlueprint blueprint, List<String> warnings) {
    }

    private final List<String> warnings = new ArrayList<>();
    private final Map<String, Cube> elements = new HashMap<>();
    private final Map<String, Integer> textureIndexByUuid = new HashMap<>();
    private final Map<UUID, String> finalBoneNames = new HashMap<>();
    private final Set<String> usedNames = new HashSet<>();
    private int resolutionWidth = 16;
    private int resolutionHeight = 16;
    private RigBlueprint blueprint;
    private File sourceFile;

    public static boolean isBlockbenchFile(File file) {
        return file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".bbmodel");
    }

    public static String modelIdFromFile(File file) {
        String n = file.getName();
        int dot = n.lastIndexOf('.');
        if (dot > 0) {
            n = n.substring(0, dot);
        }
        return sanitize(n);
    }

    public static String sanitize(String s) {
        String r = s.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9_-]", "_");
        return r.isEmpty() ? "unnamed" : r;
    }

    public Result parse(File file) throws IOException {
        this.sourceFile = file;
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        return parse(modelIdFromFile(file), root);
    }

    public Result parse(String modelId, JsonObject root) {
        blueprint = new RigBlueprint(modelId);
        JsonObject meta = obj(root, "meta");
        if (meta != null && bool(meta, "box_uv", false)) {
            warn("El modelo usa Box UV; se usaran las UV por cara almacenadas en el archivo.");
        }
        readResolution(root);
        readElements(root);
        readTextures(root);
        readOutliner(root);
        readPlaceholders(root);
        readAnimations(root);
        computeExtents();
        if (blueprint.getMainHitbox() == null) {
            warn("El modelo no tiene un grupo 'hitbox' con un cubo; se usara la hitbox de la entidad base.");
        } else if (blueprint.getMainHitbox().eyeHeight() <= 0) {
            warn("El pivote del grupo 'hitbox' esta en y<=0; la altura de los ojos sera 0.");
        }
        return new Result(blueprint, warnings);
    }

    // ------------------------------------------------------------------ resolution / elements

    private void readResolution(JsonObject root) {
        JsonObject res = obj(root, "resolution");
        if (res != null) {
            resolutionWidth = Math.max(1, integer(res, "width", 16));
            resolutionHeight = Math.max(1, integer(res, "height", 16));
        }
    }

    private void readElements(JsonObject root) {
        JsonArray arr = arr(root, "elements");
        if (arr == null) {
            return;
        }
        for (JsonElement e : arr) {
            if (!e.isJsonObject()) {
                continue;
            }
            JsonObject o = e.getAsJsonObject();
            String type = str(o, "type", "cube");
            if (!"cube".equals(type)) {
                if ("mesh".equals(type)) {
                    warn("Las mallas (mesh) no son compatibles con modelos de item y se omiten: " + str(o, "name", "?"));
                }
                continue;
            }
            String uuid = str(o, "uuid", null);
            if (uuid == null) {
                continue;
            }
            double[] from = vec(o, "from", 0);
            double[] to = vec(o, "to", 0);
            double[] origin = vec(o, "origin", 0);
            double[] rotation = vec(o, "rotation", 0);
            double inflate = dbl(o, "inflate", 0);
            Cube cube = new Cube(str(o, "name", "cube"), from, to, origin, rotation, inflate);
            JsonObject faces = obj(o, "faces");
            if (faces != null) {
                for (String dir : FACE_DIRECTIONS) {
                    JsonObject f = obj(faces, dir);
                    if (f == null) {
                        continue;
                    }
                    JsonArray uv = arr(f, "uv");
                    if (uv == null || uv.size() < 4) {
                        continue;
                    }
                    double u1 = uv.get(0).getAsDouble();
                    double v1 = uv.get(1).getAsDouble();
                    double u2 = uv.get(2).getAsDouble();
                    double v2 = uv.get(3).getAsDouble();
                    if (Math.abs(u2 - u1) < 1.0E-5 && Math.abs(v2 - v1) < 1.0E-5) {
                        continue;
                    }
                    int texture = faceTexture(f.get("texture"));
                    if (texture < 0) {
                        continue;
                    }
                    cube.getFaces().put(dir, new Cube.Face(u1, v1, u2, v2, integer(f, "rotation", 0), texture));
                }
            }
            elements.put(uuid, cube);
        }
    }

    private int faceTexture(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return -1;
        }
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            return e.getAsInt();
        }
        if (e.isJsonPrimitive()) {
            String s = e.getAsString();
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ex) {
                return resolveTextureUuidLater(s);
            }
        }
        return -1;
    }

    private final Map<String, Integer> pendingTextureUuids = new HashMap<>();

    /** Faces may reference textures by uuid in newer formats; textures are read later so remember a placeholder. */
    private int resolveTextureUuidLater(String uuid) {
        return pendingTextureUuids.computeIfAbsent(uuid, k -> 100000 + pendingTextureUuids.size());
    }

    // ------------------------------------------------------------------ textures

    private void readTextures(JsonObject root) {
        JsonArray arr = arr(root, "textures");
        if (arr == null) {
            return;
        }
        Set<String> names = new HashSet<>();
        int index = 0;
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            String uuid = str(o, "uuid", UUID.randomUUID().toString());
            String name = str(o, "name", "texture_" + index);
            int dot = name.toLowerCase(Locale.ROOT).lastIndexOf(".png");
            if (dot > 0) {
                name = name.substring(0, dot);
            }
            name = sanitize(name);
            if (!names.add(name)) {
                name = name + "_" + index;
                names.add(name);
            }
            byte[] png = decodeSource(o);
            if (png == null) {
                warn("No se pudo leer la textura '" + name + "' (sin datos embebidos).");
            }
            int uvW = o.has("uv_width") ? integer(o, "uv_width", resolutionWidth) : resolutionWidth;
            int uvH = o.has("uv_height") ? integer(o, "uv_height", resolutionHeight) : resolutionHeight;
            String mcmeta = null;
            JsonObject mcmetas = obj(root, "mcmetas");
            if (mcmetas != null && mcmetas.has(uuid)) {
                mcmeta = mcmetas.get(uuid).toString();
            } else if (png != null && o.has("frame_time")) {
                mcmeta = buildAnimationMeta(png, o, uvW, uvH);
            }
            blueprint.getTextures().add(new TextureData(index, uuid, name, uvW, uvH, png, mcmeta));
            textureIndexByUuid.put(uuid, index);
            index++;
        }
        if (!pendingTextureUuids.isEmpty()) {
            for (Cube cube : elements.values()) {
                for (Map.Entry<String, Cube.Face> entry : cube.getFaces().entrySet()) {
                    Cube.Face f = entry.getValue();
                    if (f.texture() >= 100000) {
                        String uuid = null;
                        for (Map.Entry<String, Integer> p : pendingTextureUuids.entrySet()) {
                            if (p.getValue() == f.texture()) {
                                uuid = p.getKey();
                                break;
                            }
                        }
                        Integer real = uuid == null ? null : textureIndexByUuid.get(uuid);
                        entry.setValue(new Cube.Face(f.u1(), f.v1(), f.u2(), f.v2(), f.rotation(), real == null ? 0 : real));
                    }
                }
            }
        }
    }

    private byte[] decodeSource(JsonObject o) {
        String source = str(o, "source", null);
        if (source != null && source.startsWith("data:")) {
            int comma = source.indexOf(',');
            if (comma > 0) {
                try {
                    return Base64.getDecoder().decode(source.substring(comma + 1).trim());
                } catch (IllegalArgumentException ex) {
                    return null;
                }
            }
        }
        String path = str(o, "path", null);
        if (path != null && !path.isEmpty()) {
            File f = new File(path);
            if (!f.isFile() && sourceFile != null) {
                f = new File(sourceFile.getParentFile(), new File(path).getName());
            }
            if (f.isFile()) {
                try {
                    return Files.readAllBytes(f.toPath());
                } catch (IOException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private String buildAnimationMeta(byte[] png, JsonObject o, int uvW, int uvH) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null) {
                return null;
            }
            double frameHeight = img.getWidth() * ((double) uvH / Math.max(1, uvW));
            if (img.getHeight() <= frameHeight + 0.5) {
                return null;
            }
            JsonObject animation = new JsonObject();
            animation.addProperty("frametime", Math.max(1, integer(o, "frame_time", 1)));
            if (bool(o, "frame_interpolate", false)) {
                animation.addProperty("interpolate", true);
            }
            JsonObject meta = new JsonObject();
            meta.add("animation", animation);
            return meta.toString();
        } catch (IOException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ outliner

    private void readOutliner(JsonObject root) {
        JsonArray arr = arr(root, "outliner");
        if (arr == null) {
            return;
        }
        for (JsonElement e : arr) {
            if (e.isJsonObject()) {
                BoneBlueprint bone = readBone(e.getAsJsonObject(), null);
                if (bone != null) {
                    blueprint.getRoots().add(bone);
                }
            } else if (e.isJsonPrimitive()) {
                Cube cube = elements.get(e.getAsString());
                if (cube != null) {
                    warn("El cubo '" + cube.getName() + "' esta fuera de un grupo y se ignora (los modelos requieren huesos).");
                }
            }
        }
    }

    private BoneBlueprint readBone(JsonObject outline, BoneBlueprint parent) {
        String rawName = str(outline, "name", "bone").toLowerCase(Locale.ROOT).trim();
        double[] origin = vec(outline, "origin", 0);
        double[] rotation = vec(outline, "rotation", 0);
        UUID uuid;
        try {
            uuid = UUID.fromString(str(outline, "uuid", UUID.randomUUID().toString()));
        } catch (IllegalArgumentException ex) {
            uuid = UUID.randomUUID();
        }
        boolean visibility = bool(outline, "visibility", true);
        JsonArray children = arr(outline, "children");
        if (children == null) {
            children = new JsonArray();
        }

        if (rawName.equals("hitbox")) {
            Cube cube = firstCube(children);
            if (cube != null) {
                blueprint.setMainHitbox(buildHitbox(cube, origin[1] / 16.0));
                return null;
            }
        }

        String name = rawName;
        boolean head = false;
        boolean ghost = false;
        if (name.startsWith("h_")) {
            head = true;
            name = name.substring(2);
        }
        if (name.startsWith("g_")) {
            ghost = true;
            name = name.substring(2);
        }

        Hitbox subHitbox = null;
        Vector3f subCenter = null;
        if (name.startsWith("b_") || name.startsWith("ob_") || name.startsWith("hitbox_") || name.startsWith("subhitbox_")) {
            Cube cube = firstCube(children);
            if (cube != null) {
                subHitbox = buildHitbox(cube, origin[1] / 16.0);
                subCenter = cubeCenter(cube);
            }
        }

        boolean driver = name.equals("mount");
        boolean seat = false;
        boolean nametag = false;
        BoneBlueprint.ItemSlot slot = BoneBlueprint.ItemSlot.NONE;
        if (subHitbox == null && !driver) {
            boolean changed = true;
            while (changed) {
                changed = false;
                if (name.startsWith("tag_") && name.length() > 4) {
                    nametag = true;
                    name = name.substring(4);
                    changed = true;
                } else if (name.startsWith("p_") && name.length() > 2) {
                    seat = true;
                    name = name.substring(2);
                    changed = true;
                } else if (name.startsWith("ir_") && name.length() > 3) {
                    slot = BoneBlueprint.ItemSlot.MAIN_HAND;
                    name = name.substring(3);
                    changed = true;
                } else if (name.startsWith("il_") && name.length() > 3) {
                    slot = BoneBlueprint.ItemSlot.OFF_HAND;
                    name = name.substring(3);
                    changed = true;
                } else if (name.startsWith("ih_") && name.length() > 3) {
                    slot = BoneBlueprint.ItemSlot.HEAD;
                    name = name.substring(3);
                    changed = true;
                } else if (name.startsWith("item_") && name.length() > 5) {
                    slot = BoneBlueprint.ItemSlot.CUSTOM;
                    name = name.substring(5);
                    changed = true;
                }
            }
        }
        if (name.isEmpty()) {
            name = "bone";
        }
        name = uniqueName(sanitize(name));

        BoneBlueprint bone = new BoneBlueprint(name, rawName, uuid,
                new Vector3f((float) origin[0], (float) origin[1], (float) origin[2]),
                new Vector3f((float) rotation[0], (float) rotation[1], (float) rotation[2]));
        bone.setHead(head);
        bone.setGhost(ghost);
        bone.setDriverSeat(driver);
        bone.setSeat(seat || driver);
        bone.setNametag(nametag);
        bone.setItemSlot(slot);
        bone.setVisibleByDefault(visibility);
        bone.attach(parent);
        if (subHitbox != null) {
            bone.setSubHitbox(subHitbox);
            bone.setModelPivot(subCenter);
        }
        blueprint.getBones().put(name, bone);
        finalBoneNames.put(uuid, name);

        for (JsonElement c : children) {
            if (c.isJsonObject()) {
                readBone(c.getAsJsonObject(), bone);
            } else if (c.isJsonPrimitive() && subHitbox == null) {
                Cube cube = elements.get(c.getAsString());
                if (cube == null) {
                    continue;
                }
                if (cube.getFaces().isEmpty()) {
                    warn("El cubo '" + cube.getName() + "' del hueso '" + name + "' no tiene caras con textura.");
                    continue;
                }
                bone.getCubes().add(cube);
            }
        }
        return bone;
    }

    private String uniqueName(String name) {
        String n = name;
        int i = 2;
        while (!usedNames.add(n)) {
            n = name + "_" + i++;
        }
        if (!n.equals(name)) {
            warn("Nombre de hueso duplicado '" + name + "', renombrado a '" + n + "'.");
        }
        return n;
    }

    private Cube firstCube(JsonArray children) {
        for (JsonElement c : children) {
            if (c.isJsonPrimitive()) {
                Cube cube = elements.get(c.getAsString());
                if (cube != null) {
                    return cube;
                }
            }
        }
        return null;
    }

    private static Hitbox buildHitbox(Cube cube, double eyeHeight) {
        double x = Math.abs(cube.getTo()[0] - cube.getFrom()[0]) / 16.0;
        double y = Math.abs(cube.getTo()[1] - cube.getFrom()[1]) / 16.0;
        double z = Math.abs(cube.getTo()[2] - cube.getFrom()[2]) / 16.0;
        return new Hitbox(x, y, z, eyeHeight);
    }

    private static Vector3f cubeCenter(Cube cube) {
        float x = (float) ((cube.getTo()[0] + cube.getFrom()[0]) / 32.0);
        float y = (float) ((cube.getTo()[1] + cube.getFrom()[1]) / 32.0);
        float z = (float) ((cube.getTo()[2] + cube.getFrom()[2]) / 32.0);
        return new Vector3f(-x, y, -z);
    }

    // ------------------------------------------------------------------ animations

    private void readPlaceholders(JsonObject root) {
        if (!root.has("animation_variable_placeholders")) {
            return;
        }
        String text = root.get("animation_variable_placeholders").getAsString();
        for (String line : text.split("\n")) {
            String l = line.trim();
            if (l.isEmpty() || l.startsWith("//")) {
                continue;
            }
            String[] kv = l.split("=", 2);
            String key = kv[0].trim().toLowerCase(Locale.ROOT);
            if (key.startsWith("v.")) {
                key = "variable." + key.substring(2);
            }
            String value = kv.length > 1 ? kv[1].trim() : "0";
            if (value.endsWith(";")) {
                value = value.substring(0, value.length() - 1);
            }
            blueprint.getVariables().put(key, value);
        }
    }

    private void readAnimations(JsonObject root) {
        JsonArray arr = arr(root, "animations");
        if (arr == null) {
            return;
        }
        int index = 0;
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            String name = str(o, "name", "animation_" + index).toLowerCase(Locale.ROOT).trim();
            if (name.startsWith("animation.")) {
                int lastDot = name.lastIndexOf('.');
                name = name.substring(lastDot + 1);
            }
            BlueprintAnimation animation = new BlueprintAnimation(name, index++);
            animation.setLoopMode(LoopMode.parse(str(o, "loop", "once")));
            animation.setOverride(bool(o, "override", false));
            animation.setLength(dbl(o, "length", 0));
            JsonObject animators = obj(o, "animators");
            if (animators != null) {
                for (Map.Entry<String, JsonElement> entry : animators.entrySet()) {
                    JsonObject animator = entry.getValue().getAsJsonObject();
                    JsonArray keyframes = arr(animator, "keyframes");
                    if (keyframes == null) {
                        continue;
                    }
                    String type = str(animator, "type", "bone");
                    if ("effects".equals(entry.getKey()) || "effect".equals(type)) {
                        readEffects(animation, keyframes);
                        continue;
                    }
                    if (!"bone".equals(type)) {
                        continue;
                    }
                    String boneName;
                    try {
                        boneName = finalBoneNames.get(UUID.fromString(entry.getKey()));
                    } catch (IllegalArgumentException ex) {
                        boneName = null;
                    }
                    if (boneName == null) {
                        String byName = str(animator, "name", null);
                        boneName = byName == null ? null : findBoneByRawName(byName.toLowerCase(Locale.ROOT));
                    }
                    if (boneName == null) {
                        continue;
                    }
                    readBoneKeyframes(animation.getOrCreateTimeline(boneName), keyframes);
                }
            }
            if (animation.getLength() <= 0) {
                animation.setLength(computeLength(animation));
            }
            blueprint.getAnimations().put(name, animation);
        }
    }

    private String findBoneByRawName(String raw) {
        for (BoneBlueprint b : blueprint.getBones().values()) {
            if (b.getRawName().equals(raw) || b.getName().equals(raw)) {
                return b.getName();
            }
        }
        return null;
    }

    private void readBoneKeyframes(BoneTimeline timeline, JsonArray keyframes) {
        for (JsonElement ke : keyframes) {
            JsonObject k = ke.getAsJsonObject();
            String channel = str(k, "channel", "");
            JsonArray points = arr(k, "data_points");
            if (points == null || points.isEmpty() || !k.has("time")) {
                continue;
            }
            double def = "scale".equals(channel) ? 1.0 : 0.0;
            Molang.Compiled[] pre = point(points.get(0).getAsJsonObject(), def);
            Molang.Compiled[] post = points.size() > 1 ? point(points.get(1).getAsJsonObject(), def) : pre;
            Keyframe keyframe = new Keyframe(
                    dbl(k, "time", 0),
                    pre,
                    post,
                    Interpolation.parse(str(k, "interpolation", "linear")),
                    vecOrNull(k, "bezier_left_time"),
                    vecOrNull(k, "bezier_left_value"),
                    vecOrNull(k, "bezier_right_time"),
                    vecOrNull(k, "bezier_right_value"));
            Channel target = switch (channel) {
                case "position" -> timeline.position();
                case "rotation" -> timeline.rotation();
                case "scale" -> timeline.scale();
                default -> null;
            };
            if (target != null) {
                target.add(keyframe);
            }
        }
    }

    private Molang.Compiled[] point(JsonObject p, double def) {
        return new Molang.Compiled[]{
                Molang.compileSafe(asString(p.get("x")), def, warnings),
                Molang.compileSafe(asString(p.get("y")), def, warnings),
                Molang.compileSafe(asString(p.get("z")), def, warnings)
        };
    }

    private void readEffects(BlueprintAnimation animation, JsonArray keyframes) {
        for (JsonElement ke : keyframes) {
            JsonObject k = ke.getAsJsonObject();
            String channel = str(k, "channel", "");
            JsonArray points = arr(k, "data_points");
            if (points == null || !k.has("time")) {
                continue;
            }
            double time = dbl(k, "time", 0);
            List<EffectKeyframe.Entry> entries = new ArrayList<>();
            switch (channel) {
                case "sound" -> {
                    for (JsonElement p : points) {
                        String effect = str(p.getAsJsonObject(), "effect", "");
                        if (!effect.isBlank()) {
                            entries.add(new EffectKeyframe.Entry(effect.trim(), null));
                        }
                    }
                    if (!entries.isEmpty()) {
                        animation.addEffect(new EffectKeyframe(time, EffectKeyframe.Type.SOUND, entries));
                    }
                }
                case "particle" -> {
                    for (JsonElement p : points) {
                        JsonObject po = p.getAsJsonObject();
                        String effect = str(po, "effect", "");
                        String locator = str(po, "locator", "");
                        if (!effect.isBlank()) {
                            entries.add(new EffectKeyframe.Entry(effect.trim(),
                                    locator.isBlank() ? null : resolveLocator(locator.trim().toLowerCase(Locale.ROOT))));
                        }
                    }
                    if (!entries.isEmpty()) {
                        animation.addEffect(new EffectKeyframe(time, EffectKeyframe.Type.PARTICLE, entries));
                    }
                }
                case "timeline" -> {
                    for (JsonElement p : points) {
                        String script = str(p.getAsJsonObject(), "script", "");
                        for (String line : script.split("\n")) {
                            String l = line.trim();
                            if (l.isEmpty()) {
                                continue;
                            }
                            String[] parts = l.split(":", 2);
                            if (parts.length == 2) {
                                entries.add(new EffectKeyframe.Entry(parts[1].trim(), parts[0].trim().toLowerCase(Locale.ROOT)));
                            } else {
                                entries.add(new EffectKeyframe.Entry(parts[0].trim(), null));
                            }
                        }
                    }
                    if (!entries.isEmpty()) {
                        animation.addEffect(new EffectKeyframe(time, EffectKeyframe.Type.SCRIPT, entries));
                    }
                }
                default -> {
                }
            }
        }
    }

    private String resolveLocator(String locator) {
        if (blueprint.getBones().containsKey(locator)) {
            return locator;
        }
        String byRaw = findBoneByRawName(locator);
        return byRaw == null ? locator : byRaw;
    }

    private static double computeLength(BlueprintAnimation animation) {
        double max = 0;
        for (EffectKeyframe k : animation.getEffects()) {
            max = Math.max(max, k.time());
        }
        return max;
    }

    private void computeExtents() {
        float horizontal = 0.5f;
        float vertical = 1f;
        for (BoneBlueprint bone : blueprint.getBones().values()) {
            for (Cube cube : bone.getCubes()) {
                for (int i = 0; i < 2; i++) {
                    double[] p = i == 0 ? cube.getFrom() : cube.getTo();
                    horizontal = (float) Math.max(horizontal, Math.max(Math.abs(p[0]), Math.abs(p[2])) / 16.0);
                    vertical = (float) Math.max(vertical, Math.abs(p[1]) / 16.0);
                }
            }
        }
        blueprint.setExtents(horizontal, vertical);
    }

    // ------------------------------------------------------------------ json helpers

    private void warn(String message) {
        warnings.add(message);
    }

    private static String asString(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return "";
        }
        return e.getAsString();
    }

    private static JsonObject obj(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static JsonArray arr(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }

    private static String str(JsonObject o, String key, String def) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }

    private static boolean bool(JsonObject o, String key, boolean def) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return def;
        }
        try {
            return e.getAsBoolean();
        } catch (RuntimeException ex) {
            return def;
        }
    }

    private static int integer(JsonObject o, String key, int def) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return def;
        }
        try {
            return (int) Math.round(e.getAsDouble());
        } catch (RuntimeException ex) {
            return def;
        }
    }

    private static double dbl(JsonObject o, String key, double def) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return def;
        }
        try {
            return e.getAsDouble();
        } catch (RuntimeException ex) {
            return def;
        }
    }

    private static double[] vec(JsonObject o, String key, double def) {
        JsonArray a = arr(o, key);
        double[] out = {def, def, def};
        if (a != null) {
            for (int i = 0; i < 3 && i < a.size(); i++) {
                try {
                    out[i] = a.get(i).getAsDouble();
                } catch (RuntimeException ignored) {
                    out[i] = def;
                }
            }
        }
        return out;
    }

    private static double[] vecOrNull(JsonObject o, String key) {
        return arr(o, key) == null ? null : vec(o, key, 0);
    }
}
