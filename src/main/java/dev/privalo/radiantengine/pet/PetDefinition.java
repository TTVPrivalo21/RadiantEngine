package dev.privalo.radiantengine.pet;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * Pet type. Defined in pets.yml ({@code pets:} section) or implicitly from a model id with the config defaults.
 *
 * @param id             pet id (permission radiantengine.pet.&lt;id&gt;)
 * @param model          blueprint id
 * @param movement       WALK follows the ground, FLY hovers next to the owner
 * @param scale          model scale
 * @param speed          blocks per tick while walking
 * @param sprintSpeed    blocks per tick while the owner sprints
 * @param followDistance distance kept behind the owner
 * @param flyHeight      height above the owner's feet for FLY pets
 * @param name           MiniMessage name shown on tag_ bones ({player} placeholder), may be null
 * @param rideable       the owner can ride the pet (it then moves with its mount profile)
 * @param mountProfile   mounts.yml profile used while ridden (null = model id)
 */
public record PetDefinition(String id, String model, Movement movement, float scale, double speed, double sprintSpeed,
                            double followDistance, double flyHeight, String name, boolean rideable,
                            String mountProfile) {

    public enum Movement {
        WALK,
        FLY;

        public static Movement parse(String s, Movement def) {
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

    /** Defaults used for pets that are not defined in pets.yml. */
    public record Defaults(Movement movement, double speed, double sprintSpeed, double followDistance,
                           double flyHeight, boolean rideable) {
    }

    public static PetDefinition fromSection(String id, ConfigurationSection s, Defaults d) {
        return new PetDefinition(
                id.toLowerCase(Locale.ROOT),
                s.getString("model", id).toLowerCase(Locale.ROOT),
                Movement.parse(s.getString("movement"), d.movement()),
                (float) Math.max(0.05, s.getDouble("scale", 1.0)),
                Math.max(0.01, s.getDouble("speed", d.speed())),
                Math.max(0.01, s.getDouble("sprint-speed", d.sprintSpeed())),
                Math.max(0.5, s.getDouble("follow-distance", d.followDistance())),
                s.getDouble("fly-height", d.flyHeight()),
                s.getString("name"),
                s.getBoolean("rideable", d.rideable()),
                s.getString("mount-profile"));
    }

    public static PetDefinition implicit(String modelId, Defaults d) {
        String id = modelId.toLowerCase(Locale.ROOT);
        return new PetDefinition(id, id, d.movement(), 1f, d.speed(), d.sprintSpeed(), d.followDistance(), d.flyHeight(), null,
                d.rideable(), null);
    }
}
