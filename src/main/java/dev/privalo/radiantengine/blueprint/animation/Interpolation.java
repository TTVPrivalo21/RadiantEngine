package dev.privalo.radiantengine.blueprint.animation;

import java.util.Locale;

public enum Interpolation {
    LINEAR,
    SMOOTH,
    STEP,
    BEZIER;

    public static Interpolation parse(String s) {
        if (s == null) {
            return LINEAR;
        }
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "catmullrom", "smooth" -> SMOOTH;
            case "step" -> STEP;
            case "bezier" -> BEZIER;
            default -> LINEAR;
        };
    }
}
