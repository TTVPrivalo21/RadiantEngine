package dev.privalo.radiantengine.blueprint.animation;

import dev.privalo.radiantengine.animation.molang.MolangContext;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One animated channel (position, rotation or scale) of a bone timeline.
 * Implements Blockbench's sampling rules: step / linear / catmull-rom / bezier with pre and post values.
 */
public final class Channel {

    private final List<Keyframe> keyframes = new ArrayList<>();
    private Keyframe[] sorted = new Keyframe[0];

    public void add(Keyframe keyframe) {
        keyframes.add(keyframe);
        keyframes.sort(Comparator.comparingDouble(Keyframe::time));
        sorted = keyframes.toArray(new Keyframe[0]);
    }

    /** Keyframes sorted by time (read only). */
    public java.util.List<Keyframe> keyframes() {
        return java.util.List.of(sorted);
    }

    public boolean isEmpty() {
        return sorted.length == 0;
    }

    public int size() {
        return sorted.length;
    }

    /**
     * Samples the channel.
     *
     * @return false when the channel has no keyframes (bone not animated on this channel)
     */
    public boolean sample(double time, MolangContext ctx, Vector3d out) {
        Keyframe[] k = sorted;
        if (k.length == 0) {
            return false;
        }
        if (k.length == 1 || time <= k[0].time()) {
            if (time <= k[0].time()) {
                k[0].pre(ctx, out);
            } else {
                k[0].post(ctx, out);
            }
            return true;
        }
        if (time >= k[k.length - 1].time()) {
            k[k.length - 1].post(ctx, out);
            return true;
        }
        int i = findLower(k, time);
        Keyframe before = k[i];
        Keyframe after = k[i + 1];
        if (Math.abs(time - before.time()) < 1.0E-7) {
            before.post(ctx, out);
            return true;
        }
        double span = after.time() - before.time();
        double t = span <= 1.0E-9 ? 1.0 : (time - before.time()) / span;

        if (before.interpolation() == Interpolation.STEP) {
            before.post(ctx, out);
            return true;
        }
        Vector3d a = before.post(ctx, new Vector3d());
        Vector3d b = after.pre(ctx, new Vector3d());

        if (before.interpolation() == Interpolation.SMOOTH || after.interpolation() == Interpolation.SMOOTH) {
            Vector3d p0 = i > 0 ? k[i - 1].post(ctx, new Vector3d()) : new Vector3d(a);
            Vector3d p3 = i + 2 < k.length ? k[i + 2].pre(ctx, new Vector3d()) : new Vector3d(b);
            out.set(catmull(p0.x, a.x, b.x, p3.x, t), catmull(p0.y, a.y, b.y, p3.y, t), catmull(p0.z, a.z, b.z, p3.z, t));
            return true;
        }
        if (before.interpolation() == Interpolation.BEZIER || after.interpolation() == Interpolation.BEZIER) {
            out.set(
                    bezier(before, after, 0, a.x, b.x, time),
                    bezier(before, after, 1, a.y, b.y, time),
                    bezier(before, after, 2, a.z, b.z, time));
            return true;
        }
        out.set(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
        return true;
    }

    private static int findLower(Keyframe[] k, double time) {
        int lo = 0;
        int hi = k.length - 1;
        while (lo < hi - 1) {
            int mid = (lo + hi) >>> 1;
            if (k[mid].time() <= time) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** Uniform Catmull-Rom spline between p1 and p2. */
    public static double catmull(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    private static double bezier(Keyframe before, Keyframe after, int axis, double v0, double v1, double time) {
        double t0 = before.time();
        double t1 = after.time();
        double c1t = t0 + (before.bezierRightTime() == null ? (t1 - t0) / 3 : before.bezierRightTime()[axis]);
        double c1v = v0 + (before.bezierRightValue() == null ? 0 : before.bezierRightValue()[axis]);
        double c2t = t1 + (after.bezierLeftTime() == null ? -(t1 - t0) / 3 : after.bezierLeftTime()[axis]);
        double c2v = v1 + (after.bezierLeftValue() == null ? 0 : after.bezierLeftValue()[axis]);
        c1t = Math.max(t0, Math.min(t1, c1t));
        c2t = Math.max(t0, Math.min(t1, c2t));
        // Solve x(s) = time with bisection (x is monotonic because control times are clamped to the span).
        double lo = 0;
        double hi = 1;
        double s = 0.5;
        for (int iter = 0; iter < 24; iter++) {
            s = (lo + hi) * 0.5;
            double x = cubic(t0, c1t, c2t, t1, s);
            if (x < time) {
                lo = s;
            } else {
                hi = s;
            }
        }
        return cubic(v0, c1v, c2v, v1, s);
    }

    private static double cubic(double p0, double p1, double p2, double p3, double s) {
        double u = 1 - s;
        return u * u * u * p0 + 3 * u * u * s * p1 + 3 * u * s * s * p2 + s * s * s * p3;
    }
}
