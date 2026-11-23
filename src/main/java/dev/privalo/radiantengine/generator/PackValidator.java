package dev.privalo.radiantengine.generator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks the generated resource pack before any CustomModelData, item model or font glyph is handed to players, so a
 * missing or broken file never shows up in game as the pink/black "missing texture" square. Every problem is reported
 * with the exact file path.
 */
final class PackValidator {

    private final File packFolder;
    private final List<String> log;
    private final Map<String, Boolean> textureCache = new HashMap<>();

    PackValidator(File packFolder, List<String> log) {
        this.packFolder = packFolder;
        this.log = log;
    }

    /** {@code namespace:path} -> file below assets with the given folder and extension. */
    File asset(String resource, String folder, String extension) {
        String ns = "minecraft";
        String path = resource;
        int colon = resource.indexOf(':');
        if (colon >= 0) {
            ns = resource.substring(0, colon);
            path = resource.substring(colon + 1);
        }
        return new File(packFolder, "assets/" + ns + "/" + folder + "/" + path + extension);
    }

    private static boolean vanilla(String resource) {
        return !resource.contains(":") || resource.startsWith("minecraft:");
    }

    /**
     * Validates a generated item model and every texture it references.
     *
     * @param modelPath model id ({@code namespace:path})
     * @return true when the model can be used safely
     */
    boolean model(String modelPath) {
        File file = asset(modelPath, "models", ".json");
        if (!file.isFile()) {
            log.add("ERROR Pack: falta el modelo " + rel(file) + " (" + modelPath + "); no se asignara CustomModelData.");
            return false;
        }
        JsonObject json;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            log.add("ERROR Pack: el modelo " + rel(file) + " no es JSON valido (" + e.getMessage() + ").");
            return false;
        }
        String parent = json.has("parent") ? json.get("parent").getAsString() : null;
        if (parent != null && !vanilla(parent) && !asset(parent, "models", ".json").isFile()) {
            log.add("ERROR Pack: el modelo " + rel(file) + " usa un parent inexistente: " + parent);
            return false;
        }
        boolean ok = true;
        if (json.has("textures") && json.get("textures").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("textures").entrySet()) {
                if (!e.getValue().isJsonPrimitive()) {
                    continue;
                }
                String ref = e.getValue().getAsString();
                if (ref.startsWith("#") || vanilla(ref)) {
                    continue;
                }
                if (!texture(ref)) {
                    log.add("ERROR Pack: la textura '" + ref + "' (" + rel(asset(ref, "textures", ".png"))
                            + ") usada por " + rel(file) + " no existe o no es un PNG valido.");
                    ok = false;
                }
            }
        }
        if (!json.has("elements") && parent == null) {
            log.add("ERROR Pack: el modelo " + rel(file) + " no tiene elements ni parent.");
            ok = false;
        }
        return ok;
    }

    /** Validates a texture (readable PNG) and its animation metadata; a broken .mcmeta is removed. */
    boolean texture(String ref) {
        return textureCache.computeIfAbsent(ref, r -> checkTexture(asset(r, "textures", ".png")));
    }

    boolean textureFile(File png) {
        return checkTexture(png);
    }

    private boolean checkTexture(File png) {
        if (!png.isFile()) {
            return false;
        }
        BufferedImage image;
        try {
            image = ImageIO.read(png);
        } catch (IOException e) {
            image = null;
        }
        if (image == null) {
            return false;
        }
        File meta = new File(png.getPath() + ".mcmeta");
        if (meta.isFile()) {
            boolean valid;
            try (Reader reader = Files.newBufferedReader(meta.toPath(), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject animation = json.has("animation") ? json.getAsJsonObject("animation") : null;
                int frameWidth = animation != null && animation.has("width") ? animation.get("width").getAsInt() : image.getWidth();
                int frameHeight = animation != null && animation.has("height") ? animation.get("height").getAsInt()
                        : Math.min(image.getWidth(), image.getHeight());
                valid = frameWidth > 0 && frameHeight > 0
                        && image.getWidth() % frameWidth == 0 && image.getHeight() % frameHeight == 0;
            } catch (IOException | RuntimeException e) {
                valid = false;
            }
            if (!valid) {
                log.add("WARN Pack: la animacion " + rel(meta) + " no coincide con el tamaño de la imagen ("
                        + image.getWidth() + "x" + image.getHeight() + "); se elimina para evitar la textura faltante.");
                try {
                    Files.deleteIfExists(meta.toPath());
                } catch (IOException ignored) {
                    // left in place; reported above
                }
            }
        }
        return true;
    }

    /** Validates the menu font: JSON readable and its bitmap texture present. */
    boolean font(File fontFile, String bitmapRef) {
        if (!fontFile.isFile()) {
            log.add("ERROR Pack: falta la fuente del menu " + rel(fontFile) + "; se usara el titulo sin textura.");
            return false;
        }
        File png = asset(bitmapRef, "textures", "");
        if (!textureFile(png)) {
            log.add("ERROR Pack: falta la textura del menu " + rel(png) + " (" + bitmapRef + "); se usara el titulo sin textura.");
            return false;
        }
        return true;
    }

    String rel(File file) {
        String base = packFolder.getAbsolutePath();
        String path = file.getAbsolutePath();
        return (path.startsWith(base) ? "resource_pack" + path.substring(base.length()) : path).replace('\\', '/');
    }
}
