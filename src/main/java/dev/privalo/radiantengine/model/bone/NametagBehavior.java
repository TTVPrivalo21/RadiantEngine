package dev.privalo.radiantengine.model.bone;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Dynamic nametag on a tag_ bone, rendered with a billboarded TextDisplay that follows the bone.
 */
public final class NametagBehavior implements BoneBehavior {

    private final RigBone bone;
    private TextDisplay display;
    private String shownText;
    private Location lastLocation;

    public NametagBehavior(RigBone bone) {
        this.bone = bone;
    }

    private String text() {
        if (!bone.isEffectivelyVisible() || bone.getModel().isHidden()) {
            return null;
        }
        return bone.getModel().resolveNametag(bone.getName());
    }

    @Override
    public void spawn(Location origin) {
        Location loc = bone.absoluteLocation(origin);
        String text = text();
        display = PartFactory.textDisplay(bone.getModel(), loc, d -> {
            d.setText(text == null ? "" : text);
            d.setSeeThrough(false);
            d.setShadowed(true);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setVisibleByDefault(true);
            if (text == null || text.isEmpty()) {
                d.setTextOpacity((byte) 0);
                d.setDefaultBackground(false);
                d.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            }
        });
        shownText = text;
        lastLocation = loc;
    }

    @Override
    public void update(Location origin, boolean originMoved) {
        if (display == null || !display.isValid()) {
            return;
        }
        String text = text();
        if (!Objects.equals(text, shownText)) {
            boolean empty = text == null || text.isEmpty();
            display.setText(empty ? "" : text);
            display.setTextOpacity(empty ? (byte) 0 : (byte) -1);
            display.setBackgroundColor(empty ? org.bukkit.Color.fromARGB(0, 0, 0, 0) : org.bukkit.Color.fromARGB(64, 0, 0, 0));
            shownText = text;
        }
        Location loc = bone.absoluteLocation(origin);
        if (lastLocation == null || lastLocation.distanceSquared(loc) > 1.0E-6) {
            display.teleport(PartFactory.flat(loc));
            lastLocation = loc;
        }
    }

    @Override
    public void destroy() {
        if (display != null) {
            bone.getModel().unregisterPart(display);
            display.remove();
            display = null;
        }
    }

    @Override
    public void refresh() {
        shownText = "\u0000";
    }

    @Override
    public Collection<? extends Entity> entities() {
        return display == null ? List.of() : List.of(display);
    }
}
