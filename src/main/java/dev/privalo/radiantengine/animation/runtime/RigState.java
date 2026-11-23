package dev.privalo.radiantengine.animation.runtime;

/**
 * Automatic model states, built on the classic default state set (idle, walk, jump, spawn, death)
 * extended with fly and attack.
 */
public enum RigState {
    IDLE(true, 0.25, 0.25),
    WALK(true, 0.25, 0.25),
    JUMP(true, 0.15, 0.15),
    FLY(true, 0.25, 0.25),
    ATTACK(false, 0.05, 0.15),
    DEATH(false, 0.15, 0.0),
    SPAWN(false, 0.0, 0.25);

    private final boolean looping;
    private final double defaultLerpIn;
    private final double defaultLerpOut;

    RigState(boolean looping, double defaultLerpIn, double defaultLerpOut) {
        this.looping = looping;
        this.defaultLerpIn = defaultLerpIn;
        this.defaultLerpOut = defaultLerpOut;
    }

    /** Looping states are mutually exclusive and selected automatically every tick. */
    public boolean isLooping() {
        return looping;
    }

    public double defaultLerpIn() {
        return defaultLerpIn;
    }

    public double defaultLerpOut() {
        return defaultLerpOut;
    }
}
