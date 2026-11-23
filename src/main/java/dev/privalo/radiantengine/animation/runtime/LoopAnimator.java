package dev.privalo.radiantengine.animation.runtime;

import dev.privalo.radiantengine.model.ActiveRig;

/**
 * Drives a rig with explicit named states instead of the automatic state controller: one looping state at a time
 * (each with fallbacks) plus one-shot animations layered on top.
 */
public final class LoopAnimator {

    private final double lerp;
    private String current;

    public LoopAnimator(double lerp) {
        this.lerp = lerp;
    }

    /**
     * Switches the looping animation to the first candidate that exists in the rig.
     *
     * @return the animation now playing, or null if none of the candidates exists
     */
    public String setLoop(ActiveRig rig, String... candidates) {
        String chosen = first(rig, candidates);
        if (chosen != null && chosen.equals(current) && rig.getAnimationHandler().isPlaying(chosen)) {
            return chosen;
        }
        if (current != null && !current.equals(chosen)) {
            rig.stopAnimation(current);
        }
        current = chosen;
        if (chosen != null) {
            rig.getAnimationHandler().playAnimation(chosen, lerp, lerp, 1.0, true);
        }
        return chosen;
    }

    /** Plays the first existing candidate once on top of the loop. */
    public boolean oneShot(ActiveRig rig, String... candidates) {
        String chosen = first(rig, candidates);
        if (chosen == null || chosen.equals(current)) {
            return false;
        }
        AnimationProperty property = new AnimationProperty(rig.getBlueprint().getAnimations().get(chosen), 0.05, 0.15, 1.0);
        property.setForceLoopMode(dev.privalo.radiantengine.blueprint.animation.LoopMode.ONCE);
        return rig.getAnimationHandler().playAnimation(property, true);
    }

    public String getCurrent() {
        return current;
    }

    public void reset() {
        current = null;
    }

    private static String first(ActiveRig rig, String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isEmpty() && rig.getBlueprint().getAnimations().containsKey(c)) {
                return c;
            }
        }
        return null;
    }
}
