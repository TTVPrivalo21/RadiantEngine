package dev.privalo.radiantengine.generator;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.privalo.radiantengine.util.FileUtil;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * Generates the custom menu background: a 176x127 texture drawn over the top half of the 6-row chest screen
 * through a bitmap font glyph placed in the inventory title (the usual "custom GUI" technique).
 *
 * <p>Layout matches the vanilla generic_54 screen: slots start at (7, 17) with an 18 px pitch, the title is drawn
 * at (8, 6) and the player inventory label at y = 128, which is left untouched.</p>
 */
public final class GuiTextures {

    /** Font key (namespace:radiant_gui). */
    public static final String FONT = "radiant_gui";
    /** Menu background glyph. */
    public static final char BACKGROUND = '';
    /** Moves the cursor 8 px left (title starts at x = 8). */
    public static final char SHIFT_START = '';
    /** Moves the cursor back from the end of the background to x = 8 to draw the title text. */
    public static final char SHIFT_BACK = '';

    public static final int WIDTH = 176;
    public static final int HEIGHT = 127;

    /** Accent colors of the bottom control row, by column (0-8); null = plain tile. */
    private static final Color[] CONTROL_ACCENTS = {
            new Color(0x36D1DC), null, new Color(0xB06BFF), null, new Color(0xFFC857), null,
            new Color(0xFF5C6C), null, new Color(0x36D1DC)
    };

    private GuiTextures() {
    }

    /** Glyph string to prepend (in {@link #FONT}) to a menu title. */
    public static String titlePrefix() {
        return "" + SHIFT_START + BACKGROUND + SHIFT_BACK;
    }

    public static void write(File packFolder, String namespace) throws IOException {
        FileUtil.writeBytes(new File(packFolder, "assets/" + namespace + "/textures/radiant/gui/models_menu.png"), menuTexture());

        JsonObject bitmap = new JsonObject();
        bitmap.addProperty("type", "bitmap");
        bitmap.addProperty("file", namespace + ":radiant/gui/models_menu.png");
        bitmap.addProperty("ascent", 13);
        bitmap.addProperty("height", HEIGHT);
        JsonArray chars = new JsonArray();
        chars.add(String.valueOf(BACKGROUND));
        bitmap.add("chars", chars);

        JsonObject advances = new JsonObject();
        advances.addProperty(String.valueOf(SHIFT_START), -8);
        // bitmap advance = width + 1, so return (WIDTH + 1) and add the 8 px title margin back
        advances.addProperty(String.valueOf(SHIFT_BACK), -(WIDTH + 1) + 8);
        JsonObject space = new JsonObject();
        space.addProperty("type", "space");
        space.add("advances", advances);

        JsonArray providers = new JsonArray();
        providers.add(space);
        providers.add(bitmap);
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        FileUtil.writeString(new File(packFolder, "assets/" + namespace + "/font/" + FONT + ".json"),
                new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(font));
    }

    static byte[] menuTexture() throws IOException {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        // Panel body with a soft vertical gradient and rounded corners
        g.setPaint(new GradientPaint(0, 0, new Color(0x1C1F2E), 0, HEIGHT, new Color(0x12141D)));
        g.fillRoundRect(0, 0, WIDTH, HEIGHT, 8, 8);

        // Header
        g.setPaint(new GradientPaint(0, 0, new Color(0x2A2F48), WIDTH, 0, new Color(0x1F2336)));
        g.fillRoundRect(0, 0, WIDTH, 17, 8, 8);
        g.fillRect(0, 8, WIDTH, 8);
        // accent line under the header
        g.setPaint(new GradientPaint(0, 0, new Color(0x36D1DC), WIDTH, 0, new Color(0x8E54E9)));
        g.fillRect(4, 15, WIDTH - 8, 1);
        // tiny decorative dots on the right side of the header
        g.setColor(new Color(0x36D1DC));
        g.fillRect(WIDTH - 14, 6, 2, 2);
        g.setColor(new Color(0x8E54E9));
        g.fillRect(WIDTH - 10, 6, 2, 2);

        // Outer border
        g.setColor(new Color(0x343A56));
        g.drawRoundRect(0, 0, WIDTH - 1, HEIGHT - 1, 8, 8);

        // Model slots (rows 0-4)
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 9; col++) {
                tile(g, 7 + col * 18, 17 + row * 18, null);
            }
        }
        // Control row (row 5) with colored accents
        for (int col = 0; col < 9; col++) {
            Color accent = CONTROL_ACCENTS[col];
            if (accent != null) {
                tile(g, 7 + col * 18, 17 + 5 * 18, accent);
            } else {
                g.setColor(new Color(0x181A26));
                g.fillRoundRect(7 + col * 18 + 2, 17 + 5 * 18 + 2, 14, 14, 4, 4);
            }
        }
        // side markers next to the control row
        g.setColor(new Color(0x36D1DC));
        g.fillRect(2, 17 + 5 * 18 + 5, 2, 8);
        g.setColor(new Color(0x8E54E9));
        g.fillRect(WIDTH - 4, 17 + 5 * 18 + 5, 2, 8);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static void tile(Graphics2D g, int x, int y, Color accent) {
        // 18x18 slot cell; item renders at (x+1, y+1) with 16x16
        g.setColor(new Color(0x262A3D));
        g.fillRoundRect(x, y, 18, 18, 5, 5);
        g.setColor(new Color(0x2F3450));
        g.drawLine(x + 2, y + 1, x + 15, y + 1);
        g.setColor(new Color(0x1A1D2B));
        g.drawLine(x + 2, y + 16, x + 15, y + 16);
        g.setColor(accent != null ? accent : new Color(0x3A4064));
        g.drawRoundRect(x, y, 17, 17, 5, 5);
        if (accent != null) {
            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 70));
            g.fillRoundRect(x + 1, y + 1, 16, 16, 4, 4);
        }
    }
}
