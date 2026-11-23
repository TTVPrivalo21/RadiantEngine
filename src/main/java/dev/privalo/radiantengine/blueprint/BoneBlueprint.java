package dev.privalo.radiantengine.blueprint;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Static bone definition read from the Blockbench outliner.
 *
 * <p>Coordinate conventions:
 * <ul>
 *   <li>{@code pivotPixels}: Blockbench absolute pivot (pixels).</li>
 *   <li>{@code modelPivot}: pivot in model space, in blocks. Model space is Blockbench space rotated 180 degrees
 *       around Y, i.e. {@code (-x, y, -z) / 16}, so the model faces +Z (south) at yaw 0.</li>
 *   <li>{@code localOffset}: {@code modelPivot - parent.modelPivot}.</li>
 *   <li>{@code rotation}: Blockbench euler rotation (degrees, ZYX order) as stored in the file.</li>
 * </ul>
 */
public final class BoneBlueprint {

    public enum ItemSlot {
        NONE,
        MAIN_HAND,
        OFF_HAND,
        HEAD,
        CUSTOM
    }

    private final String name;
    private final String rawName;
    private final UUID uuid;
    private BoneBlueprint parent;
    private final List<BoneBlueprint> children = new ArrayList<>();
    private final Vector3f pivotPixels;
    private final Vector3f modelPivot;
    private final Vector3f localOffset = new Vector3f();
    private final Vector3f rotation;
    private final List<Cube> cubes = new ArrayList<>();

    private boolean head;
    private boolean ghost;
    private boolean driverSeat;
    private boolean seat;
    private boolean nametag;
    private boolean subHitbox;
    private Hitbox subHitboxSize;
    private ItemSlot itemSlot = ItemSlot.NONE;
    private boolean visibleByDefault = true;

    // generated data
    private int customModelData = -1;
    private float renderScale = 1.0f;
    private String modelPath;

    public BoneBlueprint(String name, String rawName, UUID uuid, Vector3f pivotPixels, Vector3f rotation) {
        this.name = name;
        this.rawName = rawName;
        this.uuid = uuid;
        this.pivotPixels = pivotPixels;
        this.modelPivot = new Vector3f(-pivotPixels.x, pivotPixels.y, -pivotPixels.z).div(16f);
        this.rotation = rotation;
    }

    public void attach(BoneBlueprint parent) {
        this.parent = parent;
        if (parent != null) {
            parent.children.add(this);
        }
        recomputeOffset();
    }

    public void recomputeOffset() {
        if (parent == null) {
            localOffset.set(modelPivot);
        } else {
            localOffset.set(modelPivot).sub(parent.modelPivot);
        }
        for (BoneBlueprint child : children) {
            child.recomputeOffset();
        }
    }

    /** Moves the pivot (used for sub hitboxes whose center is the cube center). */
    public void setModelPivot(Vector3f pivot) {
        this.modelPivot.set(pivot);
        recomputeOffset();
    }

    public String getName() {
        return name;
    }

    public String getRawName() {
        return rawName;
    }

    public UUID getUuid() {
        return uuid;
    }

    public BoneBlueprint getParent() {
        return parent;
    }

    public List<BoneBlueprint> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public Vector3f getPivotPixels() {
        return pivotPixels;
    }

    public Vector3f getModelPivot() {
        return modelPivot;
    }

    public Vector3f getLocalOffset() {
        return localOffset;
    }

    public Vector3f getRotation() {
        return rotation;
    }

    public List<Cube> getCubes() {
        return cubes;
    }

    public boolean hasGeometry() {
        return !cubes.isEmpty() && !subHitbox;
    }

    public boolean isRenderable() {
        return hasGeometry() && !ghost && customModelData > 0;
    }

    public boolean isHead() {
        return head;
    }

    public void setHead(boolean head) {
        this.head = head;
    }

    public boolean isGhost() {
        return ghost;
    }

    public void setGhost(boolean ghost) {
        this.ghost = ghost;
    }

    public boolean isDriverSeat() {
        return driverSeat;
    }

    public void setDriverSeat(boolean driverSeat) {
        this.driverSeat = driverSeat;
    }

    public boolean isSeat() {
        return seat;
    }

    public void setSeat(boolean seat) {
        this.seat = seat;
    }

    public boolean isNametag() {
        return nametag;
    }

    public void setNametag(boolean nametag) {
        this.nametag = nametag;
    }

    public boolean isSubHitbox() {
        return subHitbox;
    }

    public Hitbox getSubHitboxSize() {
        return subHitboxSize;
    }

    public void setSubHitbox(Hitbox size) {
        this.subHitbox = size != null;
        this.subHitboxSize = size;
    }

    public ItemSlot getItemSlot() {
        return itemSlot;
    }

    public void setItemSlot(ItemSlot itemSlot) {
        this.itemSlot = itemSlot;
    }

    public boolean isVisibleByDefault() {
        return visibleByDefault;
    }

    public void setVisibleByDefault(boolean visibleByDefault) {
        this.visibleByDefault = visibleByDefault;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public void setCustomModelData(int customModelData) {
        this.customModelData = customModelData;
    }

    /** Inverse of the shrink factor applied to the item model JSON (so the display scales it back up). */
    public float getRenderScale() {
        return renderScale;
    }

    public void setRenderScale(float renderScale) {
        this.renderScale = renderScale;
    }

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }
}
