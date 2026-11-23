package dev.privalo.radiantengine.api.event;

import dev.privalo.radiantengine.animation.runtime.AnimationProperty;
import dev.privalo.radiantengine.model.ActiveRig;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired before an animation starts playing on a model. Cancelling prevents it. */
public final class RigAnimationStartEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final ActiveRig model;
    private final AnimationProperty property;
    private boolean cancelled;

    public RigAnimationStartEvent(ActiveRig model, AnimationProperty property) {
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
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
