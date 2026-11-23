package dev.privalo.radiantengine.blueprint;

/**
 * Texture embedded in a .bbmodel. {@code png} holds the decoded image bytes.
 */
public final class TextureData {

    private final int index;
    private final String uuid;
    private final String name;
    private final int uvWidth;
    private final int uvHeight;
    private final byte[] png;
    private final String mcmeta;

    public TextureData(int index, String uuid, String name, int uvWidth, int uvHeight, byte[] png, String mcmeta) {
        this.index = index;
        this.uuid = uuid;
        this.name = name;
        this.uvWidth = uvWidth;
        this.uvHeight = uvHeight;
        this.png = png;
        this.mcmeta = mcmeta;
    }

    public int getIndex() {
        return index;
    }

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public int getUvWidth() {
        return uvWidth;
    }

    public int getUvHeight() {
        return uvHeight;
    }

    public byte[] getPng() {
        return png;
    }

    public String getMcmeta() {
        return mcmeta;
    }
}
