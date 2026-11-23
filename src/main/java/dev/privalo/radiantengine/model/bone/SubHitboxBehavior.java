package dev.privalo.radiantengine.model.bone;

import dev.privalo.radiantengine.blueprint.Hitbox;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;

import java.util.Collection;
import java.util.List;

/**
 * Sub hitbox (b_ / ob_ / hitbox_ / subhitbox_ bones): an Interaction entity that follows the bone and redirects
 * damage and clicks to the base entity. Oriented boxes are approximated by their axis aligned size.
 */
public final class SubHitboxBehavior implements BoneBehavior {

    private final RigBone bone;
    private Interaction interaction;
    private Location lastLocation;
    private float lastWidth;
    private float lastHeight;

    public SubHitboxBehavior(RigBone bone) {
        this.bone = bone;
    }

    private float width() {
        Hitbox h = bone.getBlueprint().getSubHitboxSize();
        float s = Math.max(Math.abs(bone.getGlobalScale().x), Math.abs(bone.getGlobalScale().z));
        return (float) Math.max(0.05, h.horizontal() * s);
    }

    private float height() {
        Hitbox h = bone.getBlueprint().getSubHitboxSize();
        return (float) Math.max(0.05, h.height() * Math.abs(bone.getGlobalScale().y));
    }

    private Location target(Location origin) {
        Location loc = bone.absoluteLocation(origin);
        loc.subtract(0, height() / 2.0, 0);
        return loc;
    }

    @Override
    public void spawn(Location origin) {
        lastWidth = width();
        lastHeight = height();
        lastLocation = target(origin);
        interaction = PartFactory.interaction(bone.getModel(), lastLocation, lastWidth, lastHeight);
    }

    @Override
    public void update(Location origin, boolean originMoved) {
        if (interaction == null || !interaction.isValid()) {
            return;
        }
        float w = width();
        float h = height();
        if (Math.abs(w - lastWidth) > 1.0E-3 || Math.abs(h - lastHeight) > 1.0E-3) {
            interaction.setInteractionWidth(w);
            interaction.setInteractionHeight(h);
            lastWidth = w;
            lastHeight = h;
        }
        Location loc = target(origin);
        if (lastLocation == null || lastLocation.distanceSquared(loc) > 1.0E-6) {
            interaction.teleport(PartFactory.flat(loc));
            lastLocation = loc;
        }
    }

    @Override
    public void destroy() {
        if (interaction != null) {
            bone.getModel().unregisterPart(interaction);
            interaction.remove();
            interaction = null;
        }
    }

    @Override
    public Collection<? extends Entity> entities() {
        return interaction == null ? List.of() : List.of(interaction);
    }
}
