package dev.privalo.radiantengine.model.bone;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.Collection;

/**
 * A server-side representation attached to a bone (item display, nametag, hitbox, seat...).
 */
public interface BoneBehavior {

    /** Spawns the backing entities at the model origin. */
    void spawn(Location origin);

    /**
     * Called every tick after forward kinematics have been computed for the bone.
     *
     * @param origin      model origin (base entity location, yaw/pitch zeroed)
     * @param originMoved whether the origin moved since the previous tick
     */
    void update(Location origin, boolean originMoved);

    /** Removes the backing entities. */
    void destroy();

    /** Backing entities, used for per-player visibility and ownership lookups. */
    Collection<? extends Entity> entities();

    /** Re-reads items/colors after a tint or item change. */
    default void refresh() {
    }
}
