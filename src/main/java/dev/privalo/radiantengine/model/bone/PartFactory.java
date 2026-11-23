package dev.privalo.radiantengine.model.bone;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.model.ActiveRig;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;

import java.util.function.Consumer;

/**
 * Spawns non-persistent, tagged part entities through the plain Bukkit API.
 */
public final class PartFactory {

    private PartFactory() {
    }

    private static <T extends Entity> void common(ActiveRig model, T entity) {
        RadiantEngine plugin = model.getPlugin();
        entity.setPersistent(false);
        entity.setSilent(true);
        entity.setInvulnerable(false);
        plugin.getKeys().setFlag(entity, plugin.getKeys().part, true);
    }

    private static void display(ActiveRig model, Display d) {
        RadiantEngine plugin = model.getPlugin();
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(plugin.getSettings().interpolationTicks);
        d.setTeleportDuration(plugin.getSettings().teleportTicks);
        d.setViewRange(plugin.getSettings().viewRange);
        d.setShadowRadius(0f);
        d.setShadowStrength(0f);
        float w = (model.getBlueprint().getMaxHorizontalExtent() * 2f + 1f) * model.getScale();
        float h = (model.getBlueprint().getMaxVerticalExtent() + 1f) * model.getScale();
        d.setDisplayWidth(w);
        d.setDisplayHeight(h);
    }

    public static ItemDisplay itemDisplay(ActiveRig model, Location location, Consumer<ItemDisplay> setup) {
        Location loc = flat(location);
        ItemDisplay d = loc.getWorld().spawn(loc, ItemDisplay.class, e -> {
            common(model, e);
            display(model, e);
            setup.accept(e);
        });
        model.registerPart(d);
        return d;
    }

    public static TextDisplay textDisplay(ActiveRig model, Location location, Consumer<TextDisplay> setup) {
        Location loc = flat(location);
        TextDisplay d = loc.getWorld().spawn(loc, TextDisplay.class, e -> {
            common(model, e);
            display(model, e);
            e.setBillboard(Display.Billboard.CENTER);
            setup.accept(e);
        });
        model.registerPart(d);
        return d;
    }

    public static Interaction interaction(ActiveRig model, Location location, float width, float height) {
        Location loc = flat(location);
        Interaction i = loc.getWorld().spawn(loc, Interaction.class, e -> {
            common(model, e);
            e.setInteractionWidth(width);
            e.setInteractionHeight(height);
            e.setResponsive(true);
        });
        model.registerPart(i);
        return i;
    }

    public static Location flat(Location location) {
        Location l = location.clone();
        l.setYaw(0f);
        l.setPitch(0f);
        return l;
    }
}
