package dev.privalo.radiantengine.blueprint;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A Blockbench cube in absolute Blockbench coordinates (pixels).
 */
public final class Cube {

    public record Face(double u1, double v1, double u2, double v2, int rotation, int texture) {
    }

    private final String name;
    private final double[] from;
    private final double[] to;
    private final double[] origin;
    private final double[] rotation;
    private final double inflate;
    private final Map<String, Face> faces = new LinkedHashMap<>();

    public Cube(String name, double[] from, double[] to, double[] origin, double[] rotation, double inflate) {
        this.name = name;
        this.from = from;
        this.to = to;
        this.origin = origin;
        this.rotation = rotation;
        this.inflate = inflate;
    }

    public String getName() {
        return name;
    }

    public double[] getFrom() {
        return from;
    }

    public double[] getTo() {
        return to;
    }

    public double[] getOrigin() {
        return origin;
    }

    public double[] getRotation() {
        return rotation;
    }

    public double getInflate() {
        return inflate;
    }

    public Map<String, Face> getFaces() {
        return faces;
    }
}
