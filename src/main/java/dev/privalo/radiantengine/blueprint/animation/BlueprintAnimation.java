package dev.privalo.radiantengine.blueprint.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable-after-parse animation clip read from a .bbmodel file.
 */
public final class BlueprintAnimation {

    private final String name;
    private final int index;
    private LoopMode loopMode = LoopMode.ONCE;
    private double length;
    private boolean override;
    private final Map<String, BoneTimeline> timelines = new LinkedHashMap<>();
    private final List<EffectKeyframe> effects = new ArrayList<>();

    public BlueprintAnimation(String name, int index) {
        this.name = name;
        this.index = index;
    }

    public String getName() {
        return name;
    }

    /** Order inside the .bbmodel file; later animations have higher priority (Blockbench order). */
    public int getIndex() {
        return index;
    }

    public LoopMode getLoopMode() {
        return loopMode;
    }

    public void setLoopMode(LoopMode loopMode) {
        this.loopMode = loopMode;
    }

    public double getLength() {
        return length;
    }

    public void setLength(double length) {
        this.length = Math.max(0, length);
    }

    public boolean isOverride() {
        return override;
    }

    public void setOverride(boolean override) {
        this.override = override;
    }

    public BoneTimeline getOrCreateTimeline(String bone) {
        return timelines.computeIfAbsent(bone, b -> new BoneTimeline());
    }

    public BoneTimeline getTimeline(String bone) {
        return timelines.get(bone);
    }

    public Map<String, BoneTimeline> getTimelines() {
        return Collections.unmodifiableMap(timelines);
    }

    public void addEffect(EffectKeyframe keyframe) {
        effects.add(keyframe);
        effects.sort(Comparator.comparingDouble(EffectKeyframe::time));
    }

    public List<EffectKeyframe> getEffects() {
        return Collections.unmodifiableList(effects);
    }
}
