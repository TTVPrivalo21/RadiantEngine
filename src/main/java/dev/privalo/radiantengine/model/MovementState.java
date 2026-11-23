package dev.privalo.radiantengine.model;

/**
 * Movement snapshot of the base entity computed once per tick.
 */
public record MovementState(double horizontalSpeed, double verticalSpeed, boolean onGround, boolean flying,
                            boolean inLiquid) {

    public static final MovementState STILL = new MovementState(0, 0, true, false, false);
}
