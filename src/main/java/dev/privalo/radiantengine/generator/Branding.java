package dev.privalo.radiantengine.generator;

import dev.privalo.radiantengine.config.Settings;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Logo support. Looks for the configured custom files ({@code branding.logo-files}, relative to the plugin folder) and
 * loads the first readable image; otherwise it uses the RadiantEngine logo bundled in the jar
 * ({@code branding/radiantengine.png} + the 96x96 {@code branding/radiantengine96.png} for small targets). Produces
 * scaled variants for the pack icon, the menu header and the logo item. With branding disabled every method returns
 * null so callers fall back to the generated art.
 */
public final class Branding {

    private static final long MAX_FILE_BYTES = 16L * 1024 * 1024;
    private static final int MAX_DIMENSION = 8192;

    /** Bundled default logos (inside the plugin jar). */
    public static final String DEFAULT_LOGO = "branding/radiantengine.png";
    public static final String DEFAULT_LOGO_SMALL = "branding/radiantengine96.png";

    private final BufferedImage logo;
    private final BufferedImage smallLogo;
    private final String source;
    private final Settings settings;

    private Branding(BufferedImage logo, BufferedImage smallLogo, String source, Settings settings) {
        this.logo = logo;
        this.smallLogo = smallLogo;
        this.source = source;
        this.settings = settings;
    }

    /** Branding without a custom logo (defaults everywhere). */
    public static Branding none(Settings settings) {
        return new Branding(null, null, null, settings);
    }

    /**
     * Loads the first valid logo file. Never throws: problems are reported in {@code log} and the default art is used.
     */
    public static Branding load(File dataFolder, Settings settings, List<String> log) {
        if (!settings.brandingEnabled) {
            return none(settings);
        }
        File root;
        try {
            root = dataFolder.getCanonicalFile();
        } catch (IOException e) {
            root = dataFolder.getAbsoluteFile();
        }
        for (String candidate : settings.brandingFiles) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            File file = new File(root, candidate.trim());
            try {
                File canonical = file.getCanonicalFile();
                if (!canonical.toPath().startsWith(root.toPath())) {
                    log.add("WARN Logo '" + candidate + "' esta fuera de la carpeta del plugin; se ignora.");
                    continue;
                }
                if (!canonical.isFile()) {
                    continue;
                }
                if (canonical.length() > MAX_FILE_BYTES) {
                    log.add("WARN Logo '" + candidate + "' supera 16 MB; se usa el logo por defecto.");
                    continue;
                }
                BufferedImage image = ImageIO.read(canonical);
                if (image == null) {
                    log.add("WARN Logo '" + candidate + "' no es una imagen valida (PNG, JPG, GIF o BMP); se usa el logo por defecto.");
                    continue;
                }
                if (image.getWidth() > MAX_DIMENSION || image.getHeight() > MAX_DIMENSION) {
                    log.add("WARN Logo '" + candidate + "' es demasiado grande (" + image.getWidth() + "x" + image.getHeight()
                            + "); maximo " + MAX_DIMENSION + " px.");
                    continue;
                }
                log.add("INFO Logo personalizado cargado: " + candidate + " (" + image.getWidth() + "x" + image.getHeight() + ")");
                BufferedImage argb = toArgb(image);
                return new Branding(argb, argb, candidate, settings);
            } catch (IOException | RuntimeException e) {
                log.add("WARN No se pudo leer el logo '" + candidate + "': " + e.getMessage() + "; se usa el logo por defecto.");
            }
        }
        return settings.brandingUseDefault ? bundled(settings, log) : none(settings);
    }

    /** RadiantEngine logo shipped inside the jar (large + 96x96 versions). */
    private static Branding bundled(Settings settings, List<String> log) {
        BufferedImage large = resource(DEFAULT_LOGO);
        BufferedImage small = resource(DEFAULT_LOGO_SMALL);
        if (large == null && small == null) {
            log.add("WARN No se encontro el logo por defecto dentro del plugin; se usa el icono generado.");
            return none(settings);
        }
        log.add("INFO Usando el logo por defecto de RadiantEngine (pon tu logo en plugins/RadiantEngine/logo.png para cambiarlo).");
        return new Branding(large != null ? large : small, small != null ? small : large, null, settings);
    }

    private static BufferedImage resource(String path) {
        try (java.io.InputStream in = Branding.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            BufferedImage image = ImageIO.read(in);
            return image == null ? null : toArgb(image);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** True when a custom logo file from the plugin folder is used (false for the bundled default). */
    public boolean isCustom() {
        return source != null;
    }

    /** Picks the small source image when it is big enough for the target, otherwise the large one. */
    private BufferedImage sourceFor(int targetSize) {
        if (smallLogo != null && Math.max(smallLogo.getWidth(), smallLogo.getHeight()) >= targetSize) {
            return smallLogo;
        }
        return logo;
    }

    public boolean hasLogo() {
        return logo != null;
    }

    /** Relative path of the loaded logo, or null. */
    public String getSource() {
        return source;
    }

    /** Square pack icon (pack.png / pack_icon.png) or null to use the default icon. */
    public byte[] packIcon() throws IOException {
        if (logo == null || !settings.brandingPackIcon) {
            return null;
        }
        int size = settings.brandingPackIconSize;
        return png(fit(logo, size, size));
    }

    /** Logo fitted inside the menu header area, or null to keep the default decoration. */
    public BufferedImage menuHeaderLogo(int maxWidth, int maxHeight) {
        if (logo == null || !settings.brandingMenuHeader) {
            return null;
        }
        int height = Math.min(maxHeight, settings.brandingMenuLogoHeight);
        BufferedImage src = sourceFor(height);
        double ratio = (double) src.getWidth() / src.getHeight();
        int width = (int) Math.max(1, Math.min(maxWidth, Math.round(height * ratio)));
        height = (int) Math.max(1, Math.min(height, Math.round(width / ratio)));
        return scale(src, width, height);
    }

    /** Square texture for the logo item, or null when the logo item is disabled / unavailable. */
    public byte[] itemTexture() throws IOException {
        if (logo == null || !settings.brandingGuiItem) {
            return null;
        }
        int size = settings.brandingItemSize;
        return png(fit(sourceFor(size), size, size));
    }

    // ------------------------------------------------------------------ image helpers

    private static BufferedImage toArgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) {
            return src;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    /** Fits the image inside width x height keeping its aspect ratio, centered on a transparent canvas. */
    static BufferedImage fit(BufferedImage src, int width, int height) {
        double ratio = Math.min((double) width / src.getWidth(), (double) height / src.getHeight());
        int w = Math.max(1, (int) Math.round(src.getWidth() * ratio));
        int h = Math.max(1, (int) Math.round(src.getHeight() * ratio));
        BufferedImage scaled = scale(src, w, h);
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setComposite(AlphaComposite.SrcOver);
        g.drawImage(scaled, (width - w) / 2, (height - h) / 2, null);
        g.dispose();
        return canvas;
    }

    /**
     * Resizes an image. Upscaling uses nearest neighbour (keeps pixel art crisp); downscaling halves the image step by
     * step with bilinear filtering for a smooth result.
     */
    static BufferedImage scale(BufferedImage src, int width, int height) {
        if (src.getWidth() == width && src.getHeight() == height) {
            return src;
        }
        boolean upscale = width >= src.getWidth() && height >= src.getHeight();
        BufferedImage current = src;
        if (!upscale) {
            int w = current.getWidth();
            int h = current.getHeight();
            while (w / 2 >= width && h / 2 >= height) {
                w /= 2;
                h /= 2;
                current = draw(current, w, h, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            }
        }
        return draw(current, width, height, upscale
                ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    private static BufferedImage draw(BufferedImage src, int width, int height, Object interpolation) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.drawImage(src, 0, 0, width, height, null);
        g.dispose();
        return out;
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
