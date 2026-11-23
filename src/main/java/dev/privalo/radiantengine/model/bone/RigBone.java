package dev.privalo.radiantengine.model.bone;

import dev.privalo.radiantengine.animation.molang.MolangContext;
import dev.privalo.radiantengine.animation.runtime.AnimationHandler;
import dev.privalo.radiantengine.blueprint.BoneBlueprint;
import dev.privalo.radiantengine.model.ActiveRig;
import dev.privalo.radiantengine.util.MathUtil;
import org.bukkit.Location;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runtime bone. Holds the result of forward kinematics in model space (blocks, relative to the model origin,
 * already rotated by the body yaw and scaled by the model scale) and its attached behaviors.
 */
public final class RigBone {

    private final ActiveRig model;
    private final BoneBlueprint blueprint;
    private final RigBone parent;
    private final List<RigBone> children = new ArrayList<>();
    private final List<BoneBehavior> behaviors = new ArrayList<>();

    private final Vector3f globalPosition = new Vector3f();
    private final Quaternionf globalRotation = new Quaternionf();
    private final Vector3f globalScale = new Vector3f(1, 1, 1);

    private final Vector3f localPosition = new Vector3f();
    private final Quaternionf localRotation = new Quaternionf();
    private final Vector3f localScale = new Vector3f(1, 1, 1);

    private final Vector3d animPos = new Vector3d();
    private final Vector3d animRot = new Vector3d();
    private final Vector3d animScale = new Vector3d(1, 1, 1);
    private final Quaternionf headRotation = new Quaternionf();
    private final Vector3f tmp = new Vector3f();

    private boolean visible;
    private final Vector3f positionOffset = new Vector3f();
    private final Vector3f rotationOffset = new Vector3f();

    public RigBone(ActiveRig model, BoneBlueprint blueprint, RigBone parent) {
        this.model = model;
        this.blueprint = blueprint;
        this.parent = parent;
        this.visible = blueprint.isVisibleByDefault();
        if (parent != null) {
            parent.children.add(this);
        }
    }

    /**
     * Forward kinematics: local TRS from blueprint pose + animation, composed with the parent's global transform.
     */
    public void computeTransform(Vector3f parentPos, Quaternionf parentRot, Vector3f parentScale,
                                 AnimationHandler handler, MolangContext ctx,
                                 float relativeHeadYaw, float headPitch, boolean applyHead) {
        handler.sample(blueprint.getName(), ctx, animPos, animRot, animScale);

        // Blockbench animation position: (x, y, z) px -> model space (x, y, -z) / 16
        Vector3f offset = blueprint.getLocalOffset();
        localPosition.set(
                offset.x + (float) (animPos.x + positionOffset.x) / 16f,
                offset.y + (float) (animPos.y + positionOffset.y) / 16f,
                offset.z - (float) (animPos.z + positionOffset.z) / 16f);

        // Blockbench applies animation rotation as (-x, -y, +z) on top of the group rotation.
        Vector3f base = blueprint.getRotation();
        MathUtil.blockbenchToModel(
                base.x - animRot.x - rotationOffset.x,
                base.y - animRot.y - rotationOffset.y,
                base.z + animRot.z + rotationOffset.z,
                localRotation);

        if (applyHead && blueprint.isHead() && (parent == null || !parent.blueprint.isHead())) {
            headRotation.identity()
                    .rotateY(MathUtil.rad(-relativeHeadYaw))
                    .rotateX(MathUtil.rad(headPitch));
            localRotation.premul(headRotation);
        }

        localScale.set((float) animScale.x, (float) animScale.y, (float) animScale.z);

        parentScale.mul(localScale, globalScale);
        tmp.set(localPosition).mul(parentScale);
        parentRot.transform(tmp);
        parentPos.add(tmp, globalPosition);
        parentRot.mul(localRotation, globalRotation);

        for (RigBone child : children) {
            child.computeTransform(globalPosition, globalRotation, globalScale, handler, ctx,
                    relativeHeadYaw, headPitch, applyHead);
        }
    }

    public Location absoluteLocation(Location origin) {
        return origin.clone().add(globalPosition.x, globalPosition.y, globalPosition.z);
    }

    public void addBehavior(BoneBehavior behavior) {
        behaviors.add(behavior);
    }

    @SuppressWarnings("unchecked")
    public <T extends BoneBehavior> T getBehavior(Class<T> type) {
        for (BoneBehavior b : behaviors) {
            if (type.isInstance(b)) {
                return (T) b;
            }
        }
        return null;
    }

    public List<BoneBehavior> getBehaviors() {
        return Collections.unmodifiableList(behaviors);
    }

    public ActiveRig getModel() {
        return model;
    }

    public BoneBlueprint getBlueprint() {
        return blueprint;
    }

    public String getName() {
        return blueprint.getName();
    }

    public RigBone getParent() {
        return parent;
    }

    public List<RigBone> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public Vector3f getGlobalPosition() {
        return globalPosition;
    }

    public Quaternionf getGlobalRotation() {
        return globalRotation;
    }

    public Vector3f getGlobalScale() {
        return globalScale;
    }

    /** Effective visibility: hidden if this bone or any ancestor is hidden. */
    public boolean isEffectivelyVisible() {
        return visible && (parent == null || parent.isEffectivelyVisible());
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /** Extra position added on top of animations (Blockbench pixels). */
    public Vector3f getPositionOffset() {
        return positionOffset;
    }

    /** Extra rotation added on top of animations (Blockbench keyframe degrees). */
    public Vector3f getRotationOffset() {
        return rotationOffset;
    }
}
