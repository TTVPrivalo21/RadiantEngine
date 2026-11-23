package dev.privalo.radiantengine.hologram;

import dev.privalo.radiantengine.model.ActiveRig;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

/**
 * Persistent 3D hologram: a model floating over a pedestal point, with optional text, particle ring and click
 * command. Its entities are runtime only (non-persistent) and are re-created when its chunk is loaded.
 */
public final class HologramRig {

    /** Where the floating text is placed. */
    public enum TextPosition {
        ABOVE,
        BELOW
    }

    private final String id;
    private String modelId;
    private Location location;
    private HologramMode mode;
    private float scale;
    private float yaw;
    private double spinSpeed;
    private double bobAmplitude;
    private double bobPeriod;
    private double floatHeight;
    private Color tint;
    private boolean fullbright;
    private boolean glowing;
    private Color glowColor;
    private boolean ringEnabled;
    private String ringParticle;
    private double ringRadius;
    private int ringPoints;
    private int ringInterval;
    private String text;
    private TextPosition textPosition = TextPosition.ABOVE;
    private String command;

    // runtime
    Entity anchor;
    ActiveRig rig;
    TextDisplay textDisplay;
    String shownText;
    float currentYaw;
    long ticks;

    public HologramRig(String id, String modelId, Location location, HologramMode mode, float scale) {
        this.id = id;
        this.modelId = modelId;
        this.location = location.clone();
        this.mode = mode;
        this.scale = scale;
        this.yaw = location.getYaw();
        this.currentYaw = yaw;
    }

    public boolean isSpawned() {
        return anchor != null && anchor.isValid() && rig != null;
    }

    /** Location of the model itself (pedestal point + float height). */
    public Location modelLocation() {
        return location.clone().add(0, floatHeight, 0);
    }

    public String getId() {
        return id;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public Location getLocation() {
        return location.clone();
    }

    public void setLocation(Location location) {
        this.location = location.clone();
    }

    public HologramMode getMode() {
        return mode;
    }

    public void setMode(HologramMode mode) {
        this.mode = mode;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = Math.max(0.05f, scale);
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public double getSpinSpeed() {
        return spinSpeed;
    }

    public void setSpinSpeed(double spinSpeed) {
        this.spinSpeed = spinSpeed;
    }

    public double getBobAmplitude() {
        return bobAmplitude;
    }

    public void setBobAmplitude(double bobAmplitude) {
        this.bobAmplitude = Math.max(0, bobAmplitude);
    }

    public double getBobPeriod() {
        return bobPeriod;
    }

    public void setBobPeriod(double bobPeriod) {
        this.bobPeriod = Math.max(0.2, bobPeriod);
    }

    public double getFloatHeight() {
        return floatHeight;
    }

    public void setFloatHeight(double floatHeight) {
        this.floatHeight = floatHeight;
    }

    public Color getTint() {
        return tint;
    }

    public void setTint(Color tint) {
        this.tint = tint;
    }

    public boolean isFullbright() {
        return fullbright;
    }

    public void setFullbright(boolean fullbright) {
        this.fullbright = fullbright;
    }

    public boolean isGlowing() {
        return glowing;
    }

    public void setGlowing(boolean glowing) {
        this.glowing = glowing;
    }

    public Color getGlowColor() {
        return glowColor;
    }

    public void setGlowColor(Color glowColor) {
        this.glowColor = glowColor;
    }

    public boolean isRingEnabled() {
        return ringEnabled;
    }

    public void setRingEnabled(boolean ringEnabled) {
        this.ringEnabled = ringEnabled;
    }

    public String getRingParticle() {
        return ringParticle;
    }

    public void setRingParticle(String ringParticle) {
        this.ringParticle = ringParticle;
    }

    public double getRingRadius() {
        return ringRadius;
    }

    public void setRingRadius(double ringRadius) {
        this.ringRadius = Math.max(0.1, ringRadius);
    }

    public int getRingPoints() {
        return ringPoints;
    }

    public void setRingPoints(int ringPoints) {
        this.ringPoints = Math.max(3, ringPoints);
    }

    public int getRingInterval() {
        return ringInterval;
    }

    public void setRingInterval(int ringInterval) {
        this.ringInterval = Math.max(1, ringInterval);
    }

    /** MiniMessage text (PlaceholderAPI placeholders allowed), or null. */
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text == null || text.isBlank() ? null : text;
    }

    public TextPosition getTextPosition() {
        return textPosition;
    }

    public void setTextPosition(TextPosition textPosition) {
        this.textPosition = textPosition == null ? TextPosition.ABOVE : textPosition;
    }

    /** Command run on right click ("console:" prefix runs it from console; {player} placeholder), or null. */
    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command == null || command.isBlank() ? null : command;
    }

    /** The live rig, or null if the hologram chunk is not loaded. */
    public ActiveRig getRig() {
        return rig;
    }

    /** The base anchor entity, or null when not spawned. */
    public Entity getAnchor() {
        return anchor;
    }
}
