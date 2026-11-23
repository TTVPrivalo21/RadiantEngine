package dev.privalo.radiantengine.blueprint.animation;

import dev.privalo.radiantengine.animation.molang.Molang;
import dev.privalo.radiantengine.animation.molang.MolangContext;
import org.joml.Vector3d;

/**
 * A single 3-component keyframe. Blockbench may store two data points (pre / post) to create discontinuities:
 * the "pre" value is used when interpolating towards this keyframe and the "post" value when leaving it.
 * Bezier handles (when present) are stored as time/value offsets relative to the keyframe.
 */
public final class Keyframe {

    private final double time;
    private final Molang.Compiled[] pre;
    private final Molang.Compiled[] post;
    private final Interpolation interpolation;
    private final double[] bezierLeftTime;
    private final double[] bezierLeftValue;
    private final double[] bezierRightTime;
    private final double[] bezierRightValue;

    public Keyframe(double time, Molang.Compiled[] pre, Molang.Compiled[] post, Interpolation interpolation,
                    double[] bezierLeftTime, double[] bezierLeftValue,
                    double[] bezierRightTime, double[] bezierRightValue) {
        this.time = time;
        this.pre = pre;
        this.post = post;
        this.interpolation = interpolation;
        this.bezierLeftTime = bezierLeftTime;
        this.bezierLeftValue = bezierLeftValue;
        this.bezierRightTime = bezierRightTime;
        this.bezierRightValue = bezierRightValue;
    }

    private String[] preRaw = {"0", "0", "0"};
    private String[] postRaw = {"0", "0", "0"};

    /** Original Blockbench strings (numbers or Molang), used by the Bedrock exporter. */
    public void setRaw(String[] pre, String[] post) {
        this.preRaw = pre;
        this.postRaw = post;
    }

    public String[] preRaw() {
        return preRaw;
    }

    public String[] postRaw() {
        return postRaw;
    }

    public double time() {
        return time;
    }

    public Interpolation interpolation() {
        return interpolation;
    }

    public Vector3d pre(MolangContext ctx, Vector3d out) {
        return out.set(pre[0].eval(ctx), pre[1].eval(ctx), pre[2].eval(ctx));
    }

    public Vector3d post(MolangContext ctx, Vector3d out) {
        return out.set(post[0].eval(ctx), post[1].eval(ctx), post[2].eval(ctx));
    }

    public double[] bezierLeftTime() {
        return bezierLeftTime;
    }

    public double[] bezierLeftValue() {
        return bezierLeftValue;
    }

    public double[] bezierRightTime() {
        return bezierRightTime;
    }

    public double[] bezierRightValue() {
        return bezierRightValue;
    }
}
