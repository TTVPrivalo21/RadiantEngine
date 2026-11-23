package dev.privalo.radiantengine.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.privalo.radiantengine.blueprint.BoneBlueprint;
import dev.privalo.radiantengine.blueprint.Cube;
import dev.privalo.radiantengine.blueprint.RigBlueprint;
import dev.privalo.radiantengine.blueprint.TextureData;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Builds the Minecraft item model JSON of one bone (one model per bone, the standard per-bone item model layout).
 *
 * <p>Geometry is expressed relative to the bone pivot. Coordinates are shrunk by a per-bone factor so every element
 * fits Minecraft's [-16, 32] limit; the inverse factor is stored in {@link BoneBlueprint#setRenderScale(float)} and
 * applied back by the ItemDisplay transformation. Because ItemDisplay renders items rotated 180 degrees around Y and
 * centered on (8, 8, 8), Blockbench coordinates can be exported unchanged and appear in model space (facing +Z).</p>
 */
public final class ItemModelBuilder {

    private ItemModelBuilder() {
    }

    public static String texturePath(String namespace, RigBlueprint blueprint, TextureData texture) {
        return namespace + ":item/radiant/" + blueprint.getId() + "/" + texture.getName();
    }

    public static JsonObject build(String namespace, RigBlueprint blueprint, BoneBlueprint bone,
                                   float maxExtent, List<String> warnings) {
        float px = bone.getPivotPixels().x;
        float py = bone.getPivotPixels().y;
        float pz = bone.getPivotPixels().z;

        double maxAbs = 0;
        for (Cube cube : bone.getCubes()) {
            double inf = cube.getInflate();
            maxAbs = Math.max(maxAbs, Math.abs(cube.getFrom()[0] - inf - px));
            maxAbs = Math.max(maxAbs, Math.abs(cube.getFrom()[1] - inf - py));
            maxAbs = Math.max(maxAbs, Math.abs(cube.getFrom()[2] - inf - pz));
            maxAbs = Math.max(maxAbs, Math.abs(cube.getTo()[0] + inf - px));
            maxAbs = Math.max(maxAbs, Math.abs(cube.getTo()[1] + inf - py));
            maxAbs = Math.max(maxAbs, Math.abs(cube.getTo()[2] + inf - pz));
            maxAbs = Math.max(maxAbs, Math.abs(cube.getOrigin()[0] - px));
            maxAbs = Math.max(maxAbs, Math.abs(cube.getOrigin()[1] - py));
            maxAbs = Math.max(maxAbs, Math.abs(cube.getOrigin()[2] - pz));
        }
        double shrink = maxAbs > maxExtent ? maxExtent / maxAbs : 1.0;
        bone.setRenderScale((float) (1.0 / shrink));
        if (shrink < 0.25) {
            warnings.add("El hueso '" + bone.getName() + "' es muy grande (" + Math.round(maxAbs)
                    + "px desde su pivote); se reducira x" + String.format("%.2f", shrink)
                    + " y puede perder precision. Divide el hueso o mueve el pivote al centro.");
        }

        TreeSet<Integer> usedTextures = new TreeSet<>();
        JsonArray elements = new JsonArray();
        for (Cube cube : bone.getCubes()) {
            double inf = cube.getInflate();
            JsonObject element = new JsonObject();
            element.add("from", arr(
                    (cube.getFrom()[0] - inf - px) * shrink + 8,
                    (cube.getFrom()[1] - inf - py) * shrink + 8,
                    (cube.getFrom()[2] - inf - pz) * shrink + 8));
            element.add("to", arr(
                    (cube.getTo()[0] + inf - px) * shrink + 8,
                    (cube.getTo()[1] + inf - py) * shrink + 8,
                    (cube.getTo()[2] + inf - pz) * shrink + 8));
            JsonObject rotation = rotation(cube, px, py, pz, shrink, bone.getName(), warnings);
            if (rotation != null) {
                element.add("rotation", rotation);
            }
            JsonObject faces = new JsonObject();
            for (Map.Entry<String, Cube.Face> entry : cube.getFaces().entrySet()) {
                Cube.Face f = entry.getValue();
                TextureData tex = f.texture() >= 0 && f.texture() < blueprint.getTextures().size()
                        ? blueprint.getTextures().get(f.texture()) : null;
                if (tex == null || tex.getPng() == null) {
                    continue;
                }
                usedTextures.add(f.texture());
                double fu = 16.0 / tex.getUvWidth();
                double fv = 16.0 / tex.getUvHeight();
                JsonObject face = new JsonObject();
                face.add("uv", arr(f.u1() * fu, f.v1() * fv, f.u2() * fu, f.v2() * fv));
                face.addProperty("texture", "#" + f.texture());
                if (f.rotation() != 0) {
                    face.addProperty("rotation", ((f.rotation() % 360) + 360) % 360);
                }
                face.addProperty("tintindex", 0);
                faces.add(entry.getKey(), face);
            }
            if (faces.isEmpty()) {
                continue;
            }
            element.add("faces", faces);
            elements.add(element);
        }

        JsonObject textures = new JsonObject();
        boolean first = true;
        for (int idx : usedTextures) {
            String path = texturePath(namespace, blueprint, blueprint.getTextures().get(idx));
            textures.addProperty(String.valueOf(idx), path);
            if (first) {
                textures.addProperty("particle", path);
                first = false;
            }
        }

        JsonObject model = new JsonObject();
        model.addProperty("credit", "Generated by RadiantEngine");
        model.add("textures", textures);
        model.add("elements", elements);
        return elements.isEmpty() ? null : model;
    }

    /**
     * Builds a single item model containing every visible cube of the blueprint (rest pose), centered and with a
     * 3/4 "gui" display transform. Used as the model icon in menus.
     */
    public static JsonObject buildIcon(String namespace, RigBlueprint blueprint) {
        List<Cube> cubes = new java.util.ArrayList<>();
        for (BoneBlueprint bone : blueprint.getBones().values()) {
            if (bone.hasGeometry() && !bone.isGhost() && bone.isVisibleByDefault()) {
                cubes.addAll(bone.getCubes());
            }
        }
        if (cubes.isEmpty()) {
            return null;
        }
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (Cube cube : cubes) {
            double inf = cube.getInflate();
            for (int a = 0; a < 3; a++) {
                min[a] = Math.min(min[a], Math.min(cube.getFrom()[a], cube.getTo()[a]) - inf);
                max[a] = Math.max(max[a], Math.max(cube.getFrom()[a], cube.getTo()[a]) + inf);
            }
        }
        float cx = (float) ((min[0] + max[0]) / 2);
        float cy = (float) ((min[1] + max[1]) / 2);
        float cz = (float) ((min[2] + max[2]) / 2);
        double half = Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2])) / 2.0;
        double shrink = half <= 1.0E-6 ? 1.0 : 24.0 / half;
        List<String> ignored = new java.util.ArrayList<>();

        TreeSet<Integer> usedTextures = new TreeSet<>();
        JsonArray elements = new JsonArray();
        for (Cube cube : cubes) {
            double inf = cube.getInflate();
            JsonObject element = new JsonObject();
            element.add("from", arr(
                    (cube.getFrom()[0] - inf - cx) * shrink + 8,
                    (cube.getFrom()[1] - inf - cy) * shrink + 8,
                    (cube.getFrom()[2] - inf - cz) * shrink + 8));
            element.add("to", arr(
                    (cube.getTo()[0] + inf - cx) * shrink + 8,
                    (cube.getTo()[1] + inf - cy) * shrink + 8,
                    (cube.getTo()[2] + inf - cz) * shrink + 8));
            JsonObject rotation = rotation(cube, cx, cy, cz, shrink, "icon", ignored);
            if (rotation != null) {
                element.add("rotation", rotation);
            }
            JsonObject faces = new JsonObject();
            for (Map.Entry<String, Cube.Face> entry : cube.getFaces().entrySet()) {
                Cube.Face f = entry.getValue();
                TextureData tex = f.texture() >= 0 && f.texture() < blueprint.getTextures().size()
                        ? blueprint.getTextures().get(f.texture()) : null;
                if (tex == null || tex.getPng() == null) {
                    continue;
                }
                usedTextures.add(f.texture());
                double fu = 16.0 / tex.getUvWidth();
                double fv = 16.0 / tex.getUvHeight();
                JsonObject face = new JsonObject();
                face.add("uv", arr(f.u1() * fu, f.v1() * fv, f.u2() * fu, f.v2() * fv));
                face.addProperty("texture", "#" + f.texture());
                if (f.rotation() != 0) {
                    face.addProperty("rotation", ((f.rotation() % 360) + 360) % 360);
                }
                faces.add(entry.getKey(), face);
            }
            if (faces.isEmpty()) {
                continue;
            }
            element.add("faces", faces);
            elements.add(element);
        }
        if (elements.isEmpty()) {
            return null;
        }
        JsonObject textures = new JsonObject();
        boolean first = true;
        for (int idx : usedTextures) {
            String path = texturePath(namespace, blueprint, blueprint.getTextures().get(idx));
            textures.addProperty(String.valueOf(idx), path);
            if (first) {
                textures.addProperty("particle", path);
                first = false;
            }
        }
        // The model spans 48 units on its largest axis; 0.24 fits it (rotated) inside the 16 unit slot.
        JsonObject gui = new JsonObject();
        gui.add("rotation", arr(25, 210, 0));
        gui.add("translation", arr(0, 0, 0));
        gui.add("scale", arr(0.24, 0.24, 0.24));
        JsonObject ground = new JsonObject();
        ground.add("scale", arr(0.12, 0.12, 0.12));
        JsonObject fixed = new JsonObject();
        fixed.add("rotation", arr(0, 180, 0));
        fixed.add("scale", arr(0.3, 0.3, 0.3));
        JsonObject display = new JsonObject();
        display.add("gui", gui);
        display.add("ground", ground);
        display.add("fixed", fixed);

        JsonObject model = new JsonObject();
        model.addProperty("credit", "Generated by RadiantEngine");
        model.addProperty("gui_light", "side");
        model.add("textures", textures);
        model.add("elements", elements);
        model.add("display", display);
        return model;
    }

    private static JsonObject rotation(Cube cube, float px, float py, float pz, double shrink,
                                       String boneName, List<String> warnings) {
        double rx = cube.getRotation()[0];
        double ry = cube.getRotation()[1];
        double rz = cube.getRotation()[2];
        int nonZero = (Math.abs(rx) > 1.0E-4 ? 1 : 0) + (Math.abs(ry) > 1.0E-4 ? 1 : 0) + (Math.abs(rz) > 1.0E-4 ? 1 : 0);
        if (nonZero == 0) {
            return null;
        }
        if (nonZero > 1) {
            warnings.add("El cubo '" + cube.getName() + "' del hueso '" + boneName
                    + "' rota en varios ejes; Minecraft solo admite uno. Se usara el mayor.");
        }
        String axis;
        double angle;
        if (Math.abs(rx) >= Math.abs(ry) && Math.abs(rx) >= Math.abs(rz)) {
            axis = "x";
            angle = rx;
        } else if (Math.abs(rz) >= Math.abs(ry)) {
            axis = "z";
            angle = rz;
        } else {
            axis = "y";
            angle = ry;
        }
        double clamped = Math.max(-45, Math.min(45, angle));
        double snapped = Math.round(clamped / 22.5) * 22.5;
        if (Math.abs(snapped - angle) > 1.0E-3) {
            warnings.add("El cubo '" + cube.getName() + "' del hueso '" + boneName + "' usa " + angle
                    + " grados; Minecraft solo admite -45/-22.5/0/22.5/45. Se usara " + snapped
                    + ". Usa la rotacion del grupo (hueso) para angulos libres.");
        }
        if (Math.abs(snapped) < 1.0E-4) {
            return null;
        }
        JsonObject rotation = new JsonObject();
        rotation.addProperty("angle", snapped);
        rotation.addProperty("axis", axis);
        rotation.add("origin", arr(
                (cube.getOrigin()[0] - px) * shrink + 8,
                (cube.getOrigin()[1] - py) * shrink + 8,
                (cube.getOrigin()[2] - pz) * shrink + 8));
        return rotation;
    }

    private static JsonArray arr(double... values) {
        JsonArray a = new JsonArray();
        for (double v : values) {
            a.add(Math.round(v * 10000.0) / 10000.0);
        }
        return a;
    }
}
