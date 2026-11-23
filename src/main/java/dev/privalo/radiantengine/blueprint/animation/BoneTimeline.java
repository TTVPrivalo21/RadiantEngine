package dev.privalo.radiantengine.blueprint.animation;

/**
 * Position / rotation / scale channels of one bone inside one animation.
 * Values are stored exactly as Blockbench writes them (pixels for position, degrees for rotation).
 */
public final class BoneTimeline {

    private final Channel position = new Channel();
    private final Channel rotation = new Channel();
    private final Channel scale = new Channel();

    public Channel position() {
        return position;
    }

    public Channel rotation() {
        return rotation;
    }

    public Channel scale() {
        return scale;
    }

    public boolean isEmpty() {
        return position.isEmpty() && rotation.isEmpty() && scale.isEmpty();
    }
}
