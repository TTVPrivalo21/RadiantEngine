package dev.privalo.radiantengine.api.event;

import dev.privalo.radiantengine.model.ActiveRig;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after a model has been attached to an entity and its display entities spawned. */
public final class RigAttachEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final ActiveRig model;

    public RigAttachEvent(ActiveRig model) {
        this.model = model;
    }

    public ActiveRig getModel() {
        return model;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
