package dev.privalo.radiantengine.animation.runtime;

import dev.privalo.radiantengine.blueprint.animation.BlueprintAnimation;
import dev.privalo.radiantengine.blueprint.animation.LoopMode;

/**
 * Playback state of one animation on one model (single animation layer): lerp-in, play and lerp-out phases,
 * speed, forced loop mode and forced override.
 */
public final class AnimationProperty {

    public enum Phase {
        LERP_IN,
        PLAY,
        LERP_OUT
    }

    private static final double EPS = 1.0E-5;

    private final BlueprintAnimation animation;
    private final double lerpIn;
    private final double lerpOut;
    private double speed;
    private double lerpInTime;
    private double lerpOutTime;
    private double time;
    private double previousTime;
    private boolean wrapped;
    private boolean justStarted;
    private Phase phase = Phase.LERP_IN;
    private LoopMode forceLoopMode;
    private boolean forceOverride;

    public AnimationProperty(BlueprintAnimation animation, double lerpIn, double lerpOut, double speed) {
        this.animation = animation;
        this.lerpIn = Math.max(0, lerpIn);
        this.lerpOut = Math.max(0, lerpOut);
        this.speed = speed;
    }

    /**
     * Advances one server tick.
     *
     * @return false once the animation has completely finished
     */
    public boolean update() {
        previousTime = time;
        wrapped = false;
        justStarted = false;
        return switch (phase) {
            case LERP_IN -> updateLerpIn();
            case PLAY -> updateTime();
            case LERP_OUT -> updateLerpOut();
        };
    }

    private boolean updateLerpIn() {
        if (lerpInTime >= lerpIn - EPS) {
            phase = Phase.PLAY;
            justStarted = true;
            return true;
        }
        lerpInTime += Math.abs(speed) / 20.0;
        return true;
    }

    private boolean updateTime() {
        double length = animation.getLength();
        double step = speed / 20.0;
        switch (loopMode()) {
            case ONCE -> {
                if (time < length - EPS) {
                    time = Math.min(time + step, length);
                    return true;
                }
                return beginLerpOut();
            }
            case HOLD -> {
                time = Math.min(time + step, length);
                return true;
            }
            case LOOP -> {
                if (length <= EPS) {
                    time = 0;
                    return true;
                }
                double next = time + step;
                if (next > length + EPS) {
                    next = next % length;
                    wrapped = true;
                }
                time = next;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean beginLerpOut() {
        if (lerpOut > EPS) {
            phase = Phase.LERP_OUT;
            return true;
        }
        return false;
    }

    private boolean updateLerpOut() {
        if (lerpOutTime >= lerpOut - EPS) {
            return false;
        }
        lerpOutTime += Math.abs(speed) / 20.0;
        return true;
    }

    /** Requests a smooth stop (lerp-out); returns false when it must be removed immediately. */
    public boolean stop() {
        if (lerpOut <= EPS) {
            return false;
        }
        phase = Phase.LERP_OUT;
        return true;
    }

    /** Blend weight of this animation for the current tick. */
    public double weight() {
        return switch (phase) {
            case LERP_IN -> lerpIn <= EPS ? 1.0 : Math.min(1.0, lerpInTime / lerpIn);
            case PLAY -> 1.0;
            case LERP_OUT -> lerpOut <= EPS ? 0.0 : Math.max(0.0, 1.0 - lerpOutTime / lerpOut);
        };
    }

    public LoopMode loopMode() {
        return forceLoopMode == null ? animation.getLoopMode() : forceLoopMode;
    }

    public boolean isOverride() {
        return forceOverride || animation.isOverride();
    }

    public boolean isFinished() {
        return phase == Phase.LERP_OUT || (loopMode() == LoopMode.ONCE && time >= animation.getLength() - EPS);
    }

    public String getName() {
        return animation.getName();
    }

    public BlueprintAnimation getAnimation() {
        return animation;
    }

    public double getLerpIn() {
        return lerpIn;
    }

    public double getLerpOut() {
        return lerpOut;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double getTime() {
        return time;
    }

    public void setTime(double time) {
        this.time = Math.max(0, Math.min(animation.getLength(), time));
    }

    public double getPreviousTime() {
        return previousTime;
    }

    public boolean hasWrapped() {
        return wrapped;
    }

    public boolean hasJustStarted() {
        return justStarted;
    }

    public Phase getPhase() {
        return phase;
    }

    public LoopMode getForceLoopMode() {
        return forceLoopMode;
    }

    public void setForceLoopMode(LoopMode forceLoopMode) {
        this.forceLoopMode = forceLoopMode;
    }

    public boolean isForceOverride() {
        return forceOverride;
    }

    public void setForceOverride(boolean forceOverride) {
        this.forceOverride = forceOverride;
    }
}
