package dev.privalo.radiantengine.api.event;

import dev.privalo.radiantengine.animation.runtime.AnimationProperty;
import dev.privalo.radiantengine.model.ActiveRig;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after an animation has completely finished (including its lerp-out) or was force-stopped. */
public final class RigAnimationEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final ActiveRig model;
    private final AnimationProperty property;

    public RigAnimationEndEvent(ActiveRig model, AnimationProperty property) {
        this.model = model;
        this.property = property;
    }

    public ActiveRig getModel() {
        return model;
    }

    public AnimationProperty getProperty() {
        return property;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
