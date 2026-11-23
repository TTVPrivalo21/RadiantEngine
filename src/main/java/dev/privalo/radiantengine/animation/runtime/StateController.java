package dev.privalo.radiantengine.animation.runtime;

import dev.privalo.radiantengine.config.Settings;
import dev.privalo.radiantengine.model.ActiveRig;
import dev.privalo.radiantengine.model.MovementState;

import java.util.EnumMap;
import java.util.Map;

/**
 * Automatic state machine: picks idle / walk / jump / fly every tick from the entity movement and handles the
 * one-shot spawn / attack / death states. Overrides per state can be set through the API.
 */
public final class StateController {

    private final ActiveRig model;
    private final Map<RigState, Settings.StateSettings> overrides = new EnumMap<>(RigState.class);
    private RigState current;
    private boolean enabled = true;
    private int airTicks;

    public StateController(ActiveRig model) {
        this.model = model;
    }

    public Settings.StateSettings settings(RigState state) {
        Settings.StateSettings s = overrides.get(state);
        return s != null ? s : model.getPlugin().getSettings().state(state);
    }

    public void setStateOverride(RigState state, Settings.StateSettings settings) {
        if (settings == null) {
            overrides.remove(state);
        } else {
            overrides.put(state, settings);
        }
    }

    public boolean hasAnimation(RigState state) {
        Settings.StateSettings s = settings(state);
        return s != null && model.getBlueprint().getAnimations().containsKey(s.animation());
    }

    public boolean play(RigState state, boolean force) {
        Settings.StateSettings s = settings(state);
        if (s == null) {
            return false;
        }
        return model.getAnimationHandler().playAnimation(s.animation(), s.lerpIn(), s.lerpOut(), s.speed(), force);
    }

    public void stop(RigState state) {
        Settings.StateSettings s = settings(state);
        if (s != null) {
            model.getAnimationHandler().stopAnimation(s.animation());
        }
    }

    public void tick(MovementState movement) {
        if (!enabled || model.isDying()) {
            return;
        }
        airTicks = movement.onGround() ? 0 : airTicks + 1;
        RigState desired = resolve(movement);
        if (desired == current && (desired == null || model.getAnimationHandler().isPlaying(settings(desired).animation()))) {
            return;
        }
        if (current != null && current != desired) {
            stop(current);
        }
        current = desired;
        if (desired != null) {
            play(desired, false);
        }
    }

    private RigState resolve(MovementState m) {
        boolean moving = m.horizontalSpeed() > model.getPlugin().getSettings().walkThreshold;
        if (m.flying() && hasAnimation(RigState.FLY)) {
            return RigState.FLY;
        }
        if (!m.onGround() && !m.inLiquid()) {
            if (m.verticalSpeed() > 0.02 && hasAnimation(RigState.JUMP)) {
                return RigState.JUMP;
            }
            if (airTicks > 12 && hasAnimation(RigState.FLY) && Math.abs(m.verticalSpeed()) < 0.05) {
                return RigState.FLY;
            }
            if (current == RigState.JUMP && airTicks < 20 && hasAnimation(RigState.JUMP)) {
                return RigState.JUMP;
            }
        }
        if (moving && hasAnimation(RigState.WALK)) {
            return RigState.WALK;
        }
        if (hasAnimation(RigState.IDLE)) {
            return RigState.IDLE;
        }
        return moving && hasAnimation(RigState.WALK) ? RigState.WALK : null;
    }

    /** Stops the current looping state (used when death begins or auto states are disabled). */
    public void clearLoopState() {
        if (current != null) {
            stop(current);
            current = null;
        }
    }

    public RigState getCurrent() {
        return current;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            clearLoopState();
        }
    }
}
