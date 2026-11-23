package dev.privalo.radiantengine.api.event;

import dev.privalo.radiantengine.blueprint.RigBlueprint;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Collections;
import java.util.Map;

/**
 * Fired on the main thread before ({@link Phase#PRE}) and after ({@link Phase#POST}) models are (re)imported.
 */
public final class BlueprintLoadEvent extends Event {

    public enum Phase {
        PRE,
        POST
    }

    private static final HandlerList HANDLERS = new HandlerList();
    private final Phase phase;
    private final Map<String, RigBlueprint> blueprints;

    public BlueprintLoadEvent(Phase phase, Map<String, RigBlueprint> blueprints) {
        this.phase = phase;
        this.blueprints = blueprints == null ? Collections.emptyMap() : Collections.unmodifiableMap(blueprints);
    }

    public Phase getPhase() {
        return phase;
    }

    /** Registered blueprints (empty in PRE). */
    public Map<String, RigBlueprint> getBlueprints() {
        return blueprints;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
