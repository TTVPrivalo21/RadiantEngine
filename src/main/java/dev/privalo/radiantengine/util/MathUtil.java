package dev.privalo.radiantengine.util;

import org.joml.Quaternionf;

public final class MathUtil {

    private static final float DEG = (float) (Math.PI / 180.0);

    private MathUtil() {
    }

    /**
     * Converts a Blockbench euler rotation (degrees, three.js 'ZYX' order, Blockbench space) into a model space
     * quaternion. Model space is Blockbench space rotated 180 degrees about Y, which negates X and Z rotations:
     * {@code q = Rz(-z) * Ry(y) * Rx(-x)}.
     */
    public static Quaternionf blockbenchToModel(double xDeg, double yDeg, double zDeg, Quaternionf dest) {
        return dest.identity()
                .rotateZ((float) -zDeg * DEG)
                .rotateY((float) yDeg * DEG)
                .rotateX((float) -xDeg * DEG);
    }

    /** Minecraft yaw (degrees) to a model space Y rotation: yaw 0 faces +Z, positive yaw turns towards -X. */
    public static Quaternionf yaw(float yawDeg, Quaternionf dest) {
        return dest.identity().rotateY(-yawDeg * DEG);
    }

    public static float wrapDegrees(float deg) {
        float d = deg % 360f;
        if (d >= 180f) {
            d -= 360f;
        }
        if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    public static float approachDegrees(float current, float target, float maxStep) {
        float diff = wrapDegrees(target - current);
        if (diff > maxStep) {
            diff = maxStep;
        } else if (diff < -maxStep) {
            diff = -maxStep;
        }
        return current + diff;
    }

    public static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }

    public static float rad(float deg) {
        return deg * DEG;
    }
}
