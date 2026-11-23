package dev.privalo.radiantengine.generator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.privalo.radiantengine.blueprint.BoneBlueprint;
import dev.privalo.radiantengine.blueprint.Cube;
import dev.privalo.radiantengine.blueprint.RigBlueprint;
import dev.privalo.radiantengine.blueprint.TextureData;
import dev.privalo.radiantengine.blueprint.animation.BlueprintAnimation;
import dev.privalo.radiantengine.blueprint.animation.BoneTimeline;
import dev.privalo.radiantengine.blueprint.animation.Channel;
import dev.privalo.radiantengine.blueprint.animation.EffectKeyframe;
import dev.privalo.radiantengine.blueprint.animation.Interpolation;
import dev.privalo.radiantengine.blueprint.animation.Keyframe;
import dev.privalo.radiantengine.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Exports every blueprint as a Bedrock Edition resource pack ({@code bedrock_pack/} + {@code bedrock_pack.mcpack}):
 * <ul>
 *   <li>{@code manifest.json} with deterministic UUIDs</li>
 *   <li>{@code models/entity/<model>.geo.json} - Bedrock geometry (format 1.12.0, per-face UV)</li>
 *   <li>{@code animations/<model>.animation.json} - the original Blockbench keyframes (numbers and Molang)</li>
 *   <li>{@code entity/<model>.entity.json} - client entity definitions (geometry, texture, animations)</li>
 *   <li>{@code textures/entity/<namespace>/<model>/*.png}</li>
 * </ul>
 * The output is a structured starting point for Geyser custom entities or Bedrock add-ons.
 */
public final class BedrockPackGenerator {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private BedrockPackGenerator() {
    }

    /** @return number of exported models */
    public static int generate(File dataFolder, String namespace, String description, Collection<RigBlueprint> blueprints,
                               byte[] icon, List<String> log) throws IOException {
        File root = new File(dataFolder, "bedrock_pack");
        FileUtil.deleteRecursively(root);
        FileUtil.writeString(new File(root, "manifest.json"), GSON.toJson(manifest(namespace, description)));
        FileUtil.writeBytes(new File(root, "pack_icon.png"), icon);
        int count = 0;
        for (RigBlueprint bp : blueprints) {
            if (bp.getBones().isEmpty()) {
                continue;
            }
            String id = bp.getId();
            TextureData mainTexture = firstTexture(bp);
            if (mainTexture == null) {
                log.add("WARN [" + id + "] Bedrock: el modelo no tiene texturas; se omite.");
                continue;
            }
            long textures = bp.getTextures().stream().filter(t -> t.getPng() != null).count();
            if (textures > 1) {
                log.add("WARN [" + id + "] Bedrock: la geometria Bedrock usa una sola textura; se usara '"
                        + mainTexture.getName() + "' (el resto se exporta igualmente).");
            }
            FileUtil.writeString(new File(root, "models/entity/" + id + ".geo.json"),
                    GSON.toJson(geometry(namespace, bp, mainTexture)));
            for (TextureData tex : bp.getTextures()) {
                if (tex.getPng() != null) {
                    FileUtil.writeBytes(new File(root, "textures/entity/" + namespace + "/" + id + "/" + tex.getName() + ".png"), tex.getPng());
                }
            }
            if (!bp.getAnimations().isEmpty()) {
                FileUtil.writeString(new File(root, "animations/" + id + ".animation.json"),
                        GSON.toJson(animations(namespace, bp)));
            }
            FileUtil.writeString(new File(root, "entity/" + id + ".entity.json"),
                    GSON.toJson(clientEntity(namespace, bp, mainTexture)));
            count++;
        }
        FileUtil.writeString(new File(root, "README.txt"), readme(namespace));
        FileUtil.zipDirectory(root, new File(dataFolder, "bedrock_pack.mcpack"));
        return count;
    }

    private static TextureData firstTexture(RigBlueprint bp) {
        for (TextureData t : bp.getTextures()) {
            if (t.getPng() != null) {
                return t;
            }
        }
        return null;
    }

    private static String uuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static JsonObject manifest(String namespace, String description) {
        JsonObject header = new JsonObject();
        header.addProperty("name", "RadiantEngine (" + namespace + ")");
        header.addProperty("description", description);
        header.addProperty("uuid", uuid("radiantengine:" + namespace + ":header"));
        header.add("version", ints(1, 0, 0));
        header.add("min_engine_version", ints(1, 20, 0));
        JsonObject module = new JsonObject();
        module.addProperty("type", "resources");
        module.addProperty("uuid", uuid("radiantengine:" + namespace + ":resources"));
        module.add("version", ints(1, 0, 0));
        JsonArray modules = new JsonArray();
        modules.add(module);
        JsonObject manifest = new JsonObject();
        manifest.addProperty("format_version", 2);
        manifest.add("header", header);
        manifest.add("modules", modules);
        return manifest;
    }

    // ------------------------------------------------------------------ geometry

    private static JsonObject geometry(String namespace, RigBlueprint bp, TextureData texture) {
        JsonObject description = new JsonObject();
        description.addProperty("identifier", "geometry." + namespace + "." + bp.getId());
        description.addProperty("texture_width", texture.getUvWidth());
        description.addProperty("texture_height", texture.getUvHeight());
        description.addProperty("visible_bounds_width", Math.ceil(bp.getMaxHorizontalExtent() * 2 + 1));
        description.addProperty("visible_bounds_height", Math.ceil(bp.getMaxVerticalExtent() + 1));
        description.add("visible_bounds_offset", nums(0, bp.getMaxVerticalExtent() / 2.0, 0));

        JsonArray bones = new JsonArray();
        for (BoneBlueprint bone : bp.getBones().values()) {
            JsonObject b = new JsonObject();
            b.addProperty("name", bone.getName());
            if (bone.getParent() != null) {
                b.addProperty("parent", bone.getParent().getName());
            }
            // Blockbench -> Bedrock: X is mirrored, rotation X/Y negated
            b.add("pivot", nums(-bone.getPivotPixels().x, bone.getPivotPixels().y, bone.getPivotPixels().z));
            if (bone.getRotation().lengthSquared() > 1.0E-6f) {
                b.add("rotation", nums(-bone.getRotation().x, -bone.getRotation().y, bone.getRotation().z));
            }
            if (!bone.isSubHitbox() && !bone.isGhost()) {
                JsonArray cubes = new JsonArray();
                for (Cube cube : bone.getCubes()) {
                    cubes.add(cube(cube, texture.getIndex()));
                }
                if (!cubes.isEmpty()) {
                    b.add("cubes", cubes);
                }
            }
            bones.add(b);
        }
        JsonObject geo = new JsonObject();
        geo.add("description", description);
        geo.add("bones", bones);
        JsonArray list = new JsonArray();
        list.add(geo);
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.12.0");
        root.add("minecraft:geometry", list);
        return root;
    }

    private static JsonObject cube(Cube cube, int mainTexture) {
        double[] from = cube.getFrom();
        double[] to = cube.getTo();
        JsonObject c = new JsonObject();
        c.add("origin", nums(-to[0], from[1], from[2]));
        c.add("size", nums(to[0] - from[0], to[1] - from[1], to[2] - from[2]));
        if (Math.abs(cube.getInflate()) > 1.0E-6) {
            c.addProperty("inflate", cube.getInflate());
        }
        double[] rot = cube.getRotation();
        if (Math.abs(rot[0]) + Math.abs(rot[1]) + Math.abs(rot[2]) > 1.0E-6) {
            c.add("pivot", nums(-cube.getOrigin()[0], cube.getOrigin()[1], cube.getOrigin()[2]));
            c.add("rotation", nums(-rot[0], -rot[1], rot[2]));
        }
        JsonObject uv = new JsonObject();
        for (Map.Entry<String, Cube.Face> e : cube.getFaces().entrySet()) {
            Cube.Face f = e.getValue();
            JsonObject face = new JsonObject();
            if (e.getKey().equals("up") || e.getKey().equals("down")) {
                face.add("uv", nums(f.u2(), f.v2()));
                face.add("uv_size", nums(f.u1() - f.u2(), f.v1() - f.v2()));
            } else {
                face.add("uv", nums(f.u1(), f.v1()));
                face.add("uv_size", nums(f.u2() - f.u1(), f.v2() - f.v1()));
            }
            if (f.rotation() != 0) {
                face.addProperty("uv_rotation", ((f.rotation() % 360) + 360) % 360);
            }
            if (f.texture() != mainTexture) {
                face.addProperty("material_instance", "texture_" + f.texture());
            }
            uv.add(e.getKey(), face);
        }
        c.add("uv", uv);
        return c;
    }

    // ------------------------------------------------------------------ animations

    private static JsonObject animations(String namespace, RigBlueprint bp) {
        JsonObject all = new JsonObject();
        for (BlueprintAnimation a : bp.getAnimations().values()) {
            JsonObject anim = new JsonObject();
            switch (a.getLoopMode()) {
                case LOOP -> anim.addProperty("loop", true);
                case HOLD -> anim.addProperty("loop", "hold_on_last_frame");
                default -> anim.addProperty("loop", false);
            }
            anim.addProperty("animation_length", a.getLength());
            if (a.isOverride()) {
                anim.addProperty("override_previous_animation", true);
            }
            JsonObject bones = new JsonObject();
            for (Map.Entry<String, BoneTimeline> t : a.getTimelines().entrySet()) {
                JsonObject bone = new JsonObject();
                channel(bone, "rotation", t.getValue().rotation());
                channel(bone, "position", t.getValue().position());
                channel(bone, "scale", t.getValue().scale());
                if (!bone.isEmpty()) {
                    bones.add(t.getKey(), bone);
                }
            }
            if (!bones.isEmpty()) {
                anim.add("bones", bones);
            }
            JsonObject sounds = new JsonObject();
            JsonObject particles = new JsonObject();
            JsonObject timeline = new JsonObject();
            for (EffectKeyframe k : a.getEffects()) {
                String time = time(k.time());
                for (EffectKeyframe.Entry entry : k.entries()) {
                    JsonObject o = new JsonObject();
                    switch (k.type()) {
                        case SOUND -> {
                            o.addProperty("effect", entry.effect());
                            sounds.add(time, o);
                        }
                        case PARTICLE -> {
                            o.addProperty("effect", entry.effect());
                            if (entry.locator() != null) {
                                o.addProperty("locator", entry.locator());
                            }
                            particles.add(time, o);
                        }
                        case SCRIPT -> {
                            String line = entry.locator() == null ? entry.effect() : entry.locator() + ":" + entry.effect();
                            String previous = timeline.has(time) ? timeline.get(time).getAsString() + "\n" : "";
                            timeline.addProperty(time, previous + line);
                        }
                    }
                }
            }
            if (!sounds.isEmpty()) {
                anim.add("sound_effects", sounds);
            }
            if (!particles.isEmpty()) {
                anim.add("particle_effects", particles);
            }
            if (!timeline.isEmpty()) {
                anim.add("timeline", timeline);
            }
            all.add("animation." + namespace + "." + bp.getId() + "." + a.getName(), anim);
        }
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.8.0");
        root.add("animations", all);
        return root;
    }

    private static void channel(JsonObject bone, String name, Channel channel) {
        if (channel.isEmpty()) {
            return;
        }
        JsonObject frames = new JsonObject();
        for (Keyframe k : channel.keyframes()) {
            JsonElement pre = values(k.preRaw());
            JsonElement post = values(k.postRaw());
            boolean split = !pre.equals(post);
            if (k.interpolation() == Interpolation.SMOOTH || split) {
                JsonObject o = new JsonObject();
                if (split) {
                    o.add("pre", pre);
                }
                o.add("post", post);
                if (k.interpolation() == Interpolation.SMOOTH) {
                    o.addProperty("lerp_mode", "catmullrom");
                }
                frames.add(time(k.time()), o);
            } else {
                frames.add(time(k.time()), post);
            }
        }
        bone.add(name, frames);
    }

    private static JsonArray values(String[] raw) {
        JsonArray a = new JsonArray();
        for (String v : raw) {
            try {
                a.add(new JsonPrimitive(Double.parseDouble(v)));
            } catch (NumberFormatException e) {
                a.add(v);
            }
        }
        return a;
    }

    private static String time(double t) {
        return String.format(Locale.ROOT, "%.4f", t).replaceAll("0+$", "").replaceAll("\\.$", ".0");
    }

    // ------------------------------------------------------------------ client entity

    private static JsonObject clientEntity(String namespace, RigBlueprint bp, TextureData texture) {
        String id = bp.getId();
        JsonObject materials = new JsonObject();
        materials.addProperty("default", "entity_alphatest");
        JsonObject textures = new JsonObject();
        textures.addProperty("default", "textures/entity/" + namespace + "/" + id + "/" + texture.getName());
        for (TextureData t : bp.getTextures()) {
            if (t.getPng() != null && t != texture) {
                textures.addProperty("texture_" + t.getIndex(), "textures/entity/" + namespace + "/" + id + "/" + t.getName());
            }
        }
        JsonObject geometry = new JsonObject();
        geometry.addProperty("default", "geometry." + namespace + "." + id);
        JsonArray renderControllers = new JsonArray();
        renderControllers.add("controller.render.default");

        JsonObject description = new JsonObject();
        description.addProperty("identifier", namespace + ":" + id);
        description.add("materials", materials);
        description.add("textures", textures);
        description.add("geometry", geometry);
        description.add("render_controllers", renderControllers);
        if (!bp.getAnimations().isEmpty()) {
            JsonObject anims = new JsonObject();
            for (BlueprintAnimation a : bp.getAnimations().values()) {
                anims.addProperty(a.getName(), "animation." + namespace + "." + id + "." + a.getName());
            }
            description.add("animations", anims);
            String idle = bp.getAnimations().containsKey("idle") ? "idle" : null;
            if (idle != null) {
                JsonArray animate = new JsonArray();
                animate.add(idle);
                JsonObject scripts = new JsonObject();
                scripts.add("animate", animate);
                description.add("scripts", scripts);
            }
        }
        JsonObject entity = new JsonObject();
        entity.add("description", description);
        JsonObject root = new JsonObject();
        root.addProperty("format_version", "1.10.0");
        root.add("minecraft:client_entity", entity);
        return root;
    }

    private static String readme(String namespace) {
        return """
                RadiantEngine - Bedrock pack
                ============================
                Generado automaticamente en cada /radiant reload (o reload pack).

                Contenido:
                  manifest.json                 - pack de recursos Bedrock (UUIDs estables)
                  models/entity/*.geo.json      - geometria de cada modelo (geometry.%1$s.<modelo>)
                  animations/*.animation.json   - animaciones originales de Blockbench (animation.%1$s.<modelo>.<anim>)
                  entity/*.entity.json          - client entities (%1$s:<modelo>)
                  textures/entity/%1$s/...      - texturas

                Uso con Geyser: copia bedrock_pack.mcpack a plugins/Geyser-Spigot/packs/ para enviarlo a los
                jugadores Bedrock. Para mostrar los modelos como entidades personalizadas necesitas una extension
                de Geyser compatible con custom entities que asocie cada client entity a las entidades del servidor.
                """.formatted(namespace);
    }

    // ------------------------------------------------------------------ json helpers

    private static JsonArray ints(int... values) {
        JsonArray a = new JsonArray();
        for (int v : values) {
            a.add(v);
        }
        return a;
    }

    private static JsonArray nums(double... values) {
        JsonArray a = new JsonArray();
        for (double v : values) {
            a.add(Math.round(v * 10000.0) / 10000.0);
        }
        return a;
    }
}
