package dev.privalo.radiantengine.animation.runtime;

import dev.privalo.radiantengine.animation.molang.MolangContext;
import dev.privalo.radiantengine.api.event.RigAnimationEndEvent;
import dev.privalo.radiantengine.api.event.RigAnimationStartEvent;
import dev.privalo.radiantengine.blueprint.animation.BlueprintAnimation;
import dev.privalo.radiantengine.blueprint.animation.BoneTimeline;
import dev.privalo.radiantengine.blueprint.animation.EffectKeyframe;
import dev.privalo.radiantengine.model.ActiveRig;
import org.bukkit.Bukkit;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Priority animation handler (layered priority handler): several animations play as layers, ordered by their
 * position in the .bbmodel. Non-override layers are additive; override layers blend towards their own pose.
 * Lerp-in / lerp-out weights produce the fade transitions.
 */
public final class AnimationHandler {

    public interface EffectSink {
        void onEffect(AnimationProperty property, EffectKeyframe keyframe);
    }

    private final ActiveRig model;
    private final Map<String, AnimationProperty> properties = new LinkedHashMap<>();
    private List<AnimationProperty> ordered = Collections.emptyList();
    private boolean dirty;
    private final Vector3d tmp = new Vector3d();

    public AnimationHandler(ActiveRig model) {
        this.model = model;
    }

    /** Advances every animation by one tick, fires effect keyframes and removes finished ones. */
    public void tick(EffectSink sink) {
        Iterator<Map.Entry<String, AnimationProperty>> it = properties.entrySet().iterator();
        List<AnimationProperty> ended = null;
        while (it.hasNext()) {
            AnimationProperty p = it.next().getValue();
            boolean alive = p.update();
            if (!alive) {
                it.remove();
                dirty = true;
                if (ended == null) {
                    ended = new ArrayList<>(2);
                }
                ended.add(p);
                continue;
            }
            if (sink != null && p.getPhase() == AnimationProperty.Phase.PLAY) {
                fireEffects(p, sink);
            }
        }
        if (dirty) {
            rebuildOrder();
        }
        if (ended != null) {
            for (AnimationProperty p : ended) {
                Bukkit.getPluginManager().callEvent(new RigAnimationEndEvent(model, p));
            }
        }
    }

    private void fireEffects(AnimationProperty p, EffectSink sink) {
        List<EffectKeyframe> effects = p.getAnimation().getEffects();
        if (effects.isEmpty()) {
            return;
        }
        double now = p.getTime();
        double before = p.getPreviousTime();
        for (EffectKeyframe k : effects) {
            double t = k.time();
            boolean fire;
            if (p.hasJustStarted()) {
                fire = t <= now + 1.0E-6;
            } else if (p.hasWrapped()) {
                fire = t > before + 1.0E-6 || t <= now + 1.0E-6;
            } else {
                fire = t > before + 1.0E-6 && t <= now + 1.0E-6;
            }
            if (fire) {
                sink.onEffect(p, k);
            }
        }
    }

    private void rebuildOrder() {
        List<AnimationProperty> list = new ArrayList<>(properties.values());
        list.sort(Comparator.comparingInt(p -> p.getAnimation().getIndex()));
        ordered = list;
        dirty = false;
    }

    /**
     * Blends every playing animation for one bone.
     *
     * @param pos   output, Blockbench pixels (raw keyframe values)
     * @param rot   output, Blockbench degrees (raw keyframe values)
     * @param scale output, multiplicative scale
     */
    public void sample(String bone, MolangContext ctx, Vector3d pos, Vector3d rot, Vector3d scale) {
        pos.zero();
        rot.zero();
        scale.set(1, 1, 1);
        for (AnimationProperty p : ordered) {
            BoneTimeline timeline = p.getAnimation().getTimeline(bone);
            if (timeline == null) {
                continue;
            }
            double w = p.weight();
            if (w <= 0) {
                continue;
            }
            boolean override = p.isOverride();
            double time = p.getTime();
            ctx.animTime = time;
            ctx.animLength = p.getAnimation().getLength();
            if (timeline.position().sample(time, ctx, tmp)) {
                if (override) {
                    pos.lerp(tmp, w);
                } else {
                    pos.fma(w, tmp);
                }
            }
            if (timeline.rotation().sample(time, ctx, tmp)) {
                if (override) {
                    rot.lerp(tmp, w);
                } else {
                    rot.fma(w, tmp);
                }
            }
            if (timeline.scale().sample(time, ctx, tmp)) {
                if (override) {
                    scale.lerp(tmp, w);
                } else {
                    scale.mul(1 + (tmp.x - 1) * w, 1 + (tmp.y - 1) * w, 1 + (tmp.z - 1) * w);
                }
            }
        }
    }

    public boolean playAnimation(String name, double lerpIn, double lerpOut, double speed, boolean force) {
        BlueprintAnimation animation = model.getBlueprint().getAnimations().get(name);
        if (animation == null) {
            return false;
        }
        return playAnimation(new AnimationProperty(animation, lerpIn, lerpOut, speed), force);
    }

    public boolean playAnimation(AnimationProperty property, boolean force) {
        RigAnimationStartEvent event = new RigAnimationStartEvent(model, property);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        AnimationProperty old = properties.get(property.getName());
        if (old != null && !force && old.getPhase() != AnimationProperty.Phase.LERP_OUT) {
            return false;
        }
        properties.put(property.getName(), property);
        dirty = true;
        rebuildOrder();
        return true;
    }

    public boolean isPlaying(String name) {
        AnimationProperty p = properties.get(name);
        return p != null && p.getPhase() != AnimationProperty.Phase.LERP_OUT;
    }

    public AnimationProperty getProperty(String name) {
        return properties.get(name);
    }

    public Map<String, AnimationProperty> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    public void stopAnimation(String name) {
        AnimationProperty p = properties.get(name);
        if (p != null && !p.stop()) {
            forceStopAnimation(name);
        }
    }

    public void forceStopAnimation(String name) {
        AnimationProperty p = properties.remove(name);
        if (p != null) {
            rebuildOrder();
            Bukkit.getPluginManager().callEvent(new RigAnimationEndEvent(model, p));
        }
    }

    public void forceStopAll() {
        List<AnimationProperty> all = new ArrayList<>(properties.values());
        properties.clear();
        rebuildOrder();
        for (AnimationProperty p : all) {
            Bukkit.getPluginManager().callEvent(new RigAnimationEndEvent(model, p));
        }
    }

    public boolean isEmpty() {
        return properties.isEmpty();
    }
}
