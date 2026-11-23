package dev.privalo.radiantengine.blueprint.animation;

import java.util.List;

/**
 * Effect keyframes from the Blockbench "effects" animator: sounds, particles and timeline scripts.
 */
public record EffectKeyframe(double time, Type type, List<Entry> entries) {

    public enum Type {
        SOUND,
        PARTICLE,
        SCRIPT
    }

    /**
     * @param effect  sound key, particle key, or script body
     * @param locator bone used as origin (particles) or script reader id (scripts); may be null
     */
    public record Entry(String effect, String locator) {
    }
}
