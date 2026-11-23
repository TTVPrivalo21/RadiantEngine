package dev.privalo.radiantengine.model.bone;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.List;

/**
 * Renders a bone's geometry with an ItemDisplay. Bone motion is sent as native interpolated Transformations
 * (translation + left rotation + scale), so the client animates smoothly between server ticks.
 */
public class RenderBehavior implements BoneBehavior {

    protected final RigBone bone;
    protected ItemDisplay display;
    private final float[] last = new float[10];
    private boolean hasLast;
    private boolean shownItem;

    public RenderBehavior(RigBone bone) {
        this.bone = bone;
    }

    protected ItemStack createItem() {
        return bone.getModel().createBoneItem(bone.getBlueprint());
    }

    protected ItemDisplay.ItemDisplayTransform displayTransform() {
        return ItemDisplay.ItemDisplayTransform.NONE;
    }

    protected float extraScale() {
        return bone.getBlueprint().getRenderScale();
    }

    protected boolean shouldShow() {
        return bone.isEffectivelyVisible() && !bone.getModel().isHidden();
    }

    @Override
    public void spawn(Location origin) {
        Transformation t = transformation();
        boolean show = shouldShow();
        ItemStack item = show ? createItem() : null;
        display = PartFactory.itemDisplay(bone.getModel(), origin, d -> {
            d.setItemDisplayTransform(displayTransform());
            bone.getModel().styleDisplay(d);
            d.setItemStack(item);
            d.setTransformation(t);
        });
        shownItem = show;
        remember(t);
    }

    @Override
    public void update(Location origin, boolean originMoved) {
        if (display == null || !display.isValid()) {
            return;
        }
        boolean show = shouldShow();
        if (show != shownItem) {
            display.setItemStack(show ? createItem() : null);
            shownItem = show;
        }
        if (originMoved) {
            display.teleport(PartFactory.flat(origin));
        }
        Transformation t = transformation();
        if (changed(t)) {
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(bone.getModel().getPlugin().getSettings().interpolationTicks);
            display.setTransformation(t);
            remember(t);
        }
    }

    @Override
    public void refresh() {
        if (display != null && display.isValid()) {
            boolean show = shouldShow();
            display.setItemStack(show ? createItem() : null);
            shownItem = show;
        }
    }

    protected Transformation transformation() {
        float s = extraScale();
        Vector3f scale = new Vector3f(bone.getGlobalScale()).mul(s);
        return new Transformation(
                new Vector3f(bone.getGlobalPosition()),
                new Quaternionf(bone.getGlobalRotation()),
                scale,
                new Quaternionf());
    }

    private boolean changed(Transformation t) {
        if (!hasLast) {
            return true;
        }
        Vector3f p = t.getTranslation();
        Quaternionf r = t.getLeftRotation();
        Vector3f s = t.getScale();
        float eps = 1.0E-4f;
        return Math.abs(last[0] - p.x) > eps || Math.abs(last[1] - p.y) > eps || Math.abs(last[2] - p.z) > eps
                || Math.abs(last[3] - r.x) > eps || Math.abs(last[4] - r.y) > eps || Math.abs(last[5] - r.z) > eps
                || Math.abs(last[6] - r.w) > eps
                || Math.abs(last[7] - s.x) > eps || Math.abs(last[8] - s.y) > eps || Math.abs(last[9] - s.z) > eps;
    }

    private void remember(Transformation t) {
        Vector3f p = t.getTranslation();
        Quaternionf r = t.getLeftRotation();
        Vector3f s = t.getScale();
        last[0] = p.x;
        last[1] = p.y;
        last[2] = p.z;
        last[3] = r.x;
        last[4] = r.y;
        last[5] = r.z;
        last[6] = r.w;
        last[7] = s.x;
        last[8] = s.y;
        last[9] = s.z;
        hasLast = true;
    }

    @Override
    public void destroy() {
        if (display != null) {
            bone.getModel().unregisterPart(display);
            display.remove();
            display = null;
        }
    }

    @Override
    public Collection<? extends Entity> entities() {
        return display == null ? List.of() : List.of(display);
    }

    public ItemDisplay getDisplay() {
        return display;
    }
}
