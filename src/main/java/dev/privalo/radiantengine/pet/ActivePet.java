package dev.privalo.radiantengine.pet;

import dev.privalo.radiantengine.animation.runtime.LoopAnimator;
import dev.privalo.radiantengine.model.ActiveRig;
import org.bukkit.entity.Entity;

import java.util.UUID;

/**
 * A summoned pet: its rig, anchor and movement state.
 */
public final class ActivePet {

    private final UUID owner;
    private final PetDefinition definition;
    final ActiveRig rig;
    final Entity anchor;
    final LoopAnimator animator = new LoopAnimator(0.2);
    double x;
    double y;
    double z;
    float yaw;
    long ticks;
    int ownerJumpTicks;
    double lastOwnerY = Double.NaN;
    boolean moving;

    ActivePet(UUID owner, PetDefinition definition, ActiveRig rig, Entity anchor) {
        this.owner = owner;
        this.definition = definition;
        this.rig = rig;
        this.anchor = anchor;
        this.x = anchor.getLocation().getX();
        this.y = anchor.getLocation().getY();
        this.z = anchor.getLocation().getZ();
        this.yaw = anchor.getLocation().getYaw();
    }

    public UUID getOwner() {
        return owner;
    }

    public PetDefinition getDefinition() {
        return definition;
    }

    public ActiveRig getRig() {
        return rig;
    }

    public Entity getAnchor() {
        return anchor;
    }

    /** Current looping animation (idle, walk, sprint, jump...). */
    public String getCurrentAnimation() {
        return animator.getCurrent();
    }

    public boolean isValid() {
        return anchor.isValid() && rig != null;
    }
}
