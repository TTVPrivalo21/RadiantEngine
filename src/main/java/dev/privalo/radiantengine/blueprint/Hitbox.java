package dev.privalo.radiantengine.blueprint;

/**
 * Axis aligned hitbox size in blocks. {@code eyeHeight} is taken from the pivot of the "hitbox" group.
 */
public record Hitbox(double width, double height, double depth, double eyeHeight) {

    public double horizontal() {
        return Math.max(width, depth);
    }
}
