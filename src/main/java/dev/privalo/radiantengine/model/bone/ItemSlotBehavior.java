package dev.privalo.radiantengine.model.bone;

import dev.privalo.radiantengine.blueprint.BoneBlueprint;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

/**
 * Renders an item on an item bone (ir_ / il_ / ih_ / item_). The item comes from the API, from an item link
 * (Oraxen or vanilla) or from the base entity's equipment.
 */
public final class ItemSlotBehavior extends RenderBehavior {

    private ItemStack current;
    private int ticks;

    public ItemSlotBehavior(RigBone bone) {
        super(bone);
    }

    @Override
    protected ItemStack createItem() {
        current = bone.getModel().resolveSlotItem(bone);
        return current == null ? null : current.clone();
    }

    @Override
    protected boolean shouldShow() {
        if (!super.shouldShow()) {
            return false;
        }
        ItemStack item = bone.getModel().resolveSlotItem(bone);
        return item != null && !item.getType().isAir();
    }

    @Override
    protected ItemDisplay.ItemDisplayTransform displayTransform() {
        BoneBlueprint.ItemSlot slot = bone.getBlueprint().getItemSlot();
        return switch (slot) {
            case MAIN_HAND -> ItemDisplay.ItemDisplayTransform.THIRDPERSON_RIGHTHAND;
            case OFF_HAND -> ItemDisplay.ItemDisplayTransform.THIRDPERSON_LEFTHAND;
            case HEAD -> ItemDisplay.ItemDisplayTransform.HEAD;
            default -> ItemDisplay.ItemDisplayTransform.FIXED;
        };
    }

    @Override
    protected float extraScale() {
        return 1.0f;
    }

    @Override
    public void update(Location origin, boolean originMoved) {
        if (++ticks >= 10) {
            ticks = 0;
            ItemStack now = bone.getModel().resolveSlotItem(bone);
            boolean same = now == null ? current == null : now.isSimilar(current) && current != null
                    && now.getAmount() == current.getAmount();
            if (!same) {
                refresh();
            }
        }
        super.update(origin, originMoved);
    }
}
