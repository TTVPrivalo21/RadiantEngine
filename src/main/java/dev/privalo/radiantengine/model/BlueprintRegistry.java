package dev.privalo.radiantengine.model;

import dev.privalo.radiantengine.blueprint.RigBlueprint;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Registered blueprints. Replaced atomically on reload (main thread).
 */
public final class BlueprintRegistry {

    private volatile Map<String, RigBlueprint> blueprints = Collections.emptyMap();

    public void replace(Map<String, RigBlueprint> newBlueprints) {
        this.blueprints = Collections.unmodifiableMap(new LinkedHashMap<>(newBlueprints));
    }

    public RigBlueprint get(String id) {
        return id == null ? null : blueprints.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean contains(String id) {
        return get(id) != null;
    }

    public Map<String, RigBlueprint> all() {
        return blueprints;
    }
}
