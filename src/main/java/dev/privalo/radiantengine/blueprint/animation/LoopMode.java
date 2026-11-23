package dev.privalo.radiantengine.blueprint.animation;

import java.util.Locale;

public enum LoopMode {
    ONCE,
    HOLD,
    LOOP;

    public static LoopMode parse(String mode) {
        if (mode == null) {
            return ONCE;
        }
        try {
            return valueOf(mode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ONCE;
        }
    }
}
