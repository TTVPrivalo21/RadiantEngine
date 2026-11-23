package dev.privalo.radiantengine.hologram;

import java.util.Locale;

/**
 * How a hologram is oriented.
 */
public enum HologramMode {
    /** Turns 360 degrees around Y and floats up and down. */
    SPIN,
    /** Always faces the viewer (client-side vertical billboard, per player). */
    BILLBOARD,
    /** Fixed yaw chosen at creation. */
    STATIC;

    public static HologramMode parse(String s, HologramMode def) {
        if (s == null) {
            return def;
        }
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
