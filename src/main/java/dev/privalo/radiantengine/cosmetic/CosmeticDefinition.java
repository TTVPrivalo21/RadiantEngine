package dev.privalo.radiantengine.cosmetic;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;

/**
 * Cosmetic type defined in cosmetics.yml ({@code cosmetics:} section) or implicitly from a model id.
 *
 * <p>Model conventions: {@code back} models have their origin at the player's feet (author them on a player-sized
 * reference); {@code head} models have their origin at eye level; {@code orbit} models are centered.</p>
 *
 * @param id          cosmetic id (permission radiantengine.cosmetic.&lt;id&gt;)
 * @param model       blueprint id
 * @param anchor      attachment point
 * @param right       offset to the player's right (blocks)
 * @param up          vertical offset (blocks)
 * @param forward     offset forward (blocks)
 * @param scale       model scale
 * @param selfView    how the owner sees it
 * @param followPitch head cosmetics tilt with the look pitch
 * @param radius      orbit radius
 * @param speed       orbit speed in degrees per second
 * @param height      orbit height above the feet
 */
public record CosmeticDefinition(String id, String model, Anchor anchor, double right, double up, double forward,
                                 float scale, SelfView selfView, boolean followPitch, double radius, double speed,
                                 double height) {

    public enum Anchor {
        BACK,
        HEAD,
        ORBIT;

        public static Anchor parse(String s, Anchor def) {
            if (s == null) {
                return def;
            }
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return def;
            }
        }
    }

    /** Owner visibility: AUTO hides head cosmetics only (first person camera), VISIBLE / HIDDEN force it. */
    public enum SelfView {
        AUTO,
        VISIBLE,
        HIDDEN;

        public static SelfView parse(String s) {
            if (s == null) {
                return AUTO;
            }
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return AUTO;
            }
        }
    }

    /** @return true if the owner must not see this cosmetic */
    public boolean hiddenFromOwner() {
        return switch (selfView) {
            case VISIBLE -> false;
            case HIDDEN -> true;
            case AUTO -> anchor == Anchor.HEAD;
        };
    }

    public static CosmeticDefinition fromSection(String id, ConfigurationSection s, Anchor defaultAnchor) {
        List<Double> offset = s.getDoubleList("offset");
        Anchor anchor = Anchor.parse(s.getString("anchor"), defaultAnchor);
        return new CosmeticDefinition(
                id.toLowerCase(Locale.ROOT),
                s.getString("model", id).toLowerCase(Locale.ROOT),
                anchor,
                offset.size() > 0 ? offset.get(0) : 0,
                offset.size() > 1 ? offset.get(1) : 0,
                offset.size() > 2 ? offset.get(2) : 0,
                (float) Math.max(0.05, s.getDouble("scale", 1.0)),
                SelfView.parse(s.getString("self-view")),
                s.getBoolean("follow-pitch", false),
                Math.max(0.2, s.getDouble("radius", 1.1)),
                s.getDouble("speed", 90),
                s.getDouble("height", 1.2));
    }

    public static CosmeticDefinition implicit(String modelId, Anchor anchor) {
        String id = modelId.toLowerCase(Locale.ROOT);
        return new CosmeticDefinition(id, id, anchor, 0, 0, 0, 1f, SelfView.AUTO, false, 1.1, 90, 1.2);
    }
}
