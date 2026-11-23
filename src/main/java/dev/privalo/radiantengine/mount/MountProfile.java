package dev.privalo.radiantengine.mount;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * Driving settings of a model used as a mount / vehicle (mounts.yml).
 *
 * @param movement         GROUND, FLYING, SWIMMING or AMPHIBIOUS
 * @param speed            ground speed (blocks per tick)
 * @param sprintMultiplier multiplier while the driver sprints
 * @param jumpStrength     initial vertical velocity of a jump
 * @param stepHeight       blocks the mount climbs automatically (1 = full blocks)
 * @param gravity          downward acceleration per tick (non-living vehicles)
 * @param maxFallSpeed     terminal fall speed (blocks per tick)
 * @param flySpeed         flying speed (blocks per tick)
 * @param verticalSpeed    climb / descend speed while flying or swimming
 * @param swimSpeed        underwater speed (blocks per tick)
 * @param buoyancy         upward push per tick in water without vertical input
 * @param shiftAction      what sneaking does while flying / swimming
 * @param banking          roll the model into turns
 * @param bankingIntensity roll degrees per degree of yaw turned in a tick
 * @param bankingMaxAngle  maximum roll in degrees
 * @param smoothing        0-1, how fast roll and pitch reach their target
 * @param pitchFollow      0-1, how much the model pitches with the driver's look while flying / swimming
 * @param baseSeat         if the model has no seat bones, riders sit on the base entity itself
 */
public record MountProfile(Movement movement, double speed, double sprintMultiplier, double jumpStrength,
                           double stepHeight, double gravity, double maxFallSpeed, double flySpeed,
                           double verticalSpeed, double swimSpeed, double buoyancy, ShiftAction shiftAction,
                           boolean banking, double bankingIntensity, double bankingMaxAngle, double smoothing,
                           double pitchFollow, boolean baseSeat) {

    /** Configured movement type. */
    public enum Movement {
        GROUND,
        FLYING,
        SWIMMING,
        AMPHIBIOUS;

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

    /** Movement actually applied in a tick (AMPHIBIOUS resolves to one of these). */
    public enum Mode {
        GROUND,
        FLYING,
        SWIMMING
    }

    /** Effect of sneaking while flying or swimming. */
    public enum ShiftAction {
        DESCEND,
        DISMOUNT;

        public static ShiftAction parse(String s, ShiftAction def) {
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

    /** Built-in defaults, overridden by config.yml mount.* and the mounts.yml "default" section. */
    public static MountProfile builtin(double speed, double jump) {
        return new MountProfile(Movement.GROUND, speed, 1.35, jump, 1.0, 0.08, 1.5, 0.45, 0.3, 0.25, 0.04,
                ShiftAction.DESCEND, true, 1.2, 25, 0.18, 0.5, true);
    }

    /** Reads a section, using {@code base} for every missing key. */
    public static MountProfile from(ConfigurationSection s, MountProfile base) {
        if (s == null) {
            return base;
        }
        ConfigurationSection bank = s.getConfigurationSection("banking");
        return new MountProfile(
                Movement.parse(s.getString("movement"), base.movement()),
                Math.max(0, s.getDouble("speed", base.speed())),
                Math.max(1, s.getDouble("sprint-multiplier", base.sprintMultiplier())),
                Math.max(0, s.getDouble("jump-strength", base.jumpStrength())),
                Math.max(0, s.getDouble("step-height", base.stepHeight())),
                Math.max(0, s.getDouble("gravity", base.gravity())),
                Math.max(0.1, s.getDouble("max-fall-speed", base.maxFallSpeed())),
                Math.max(0, s.getDouble("fly-speed", base.flySpeed())),
                Math.max(0, s.getDouble("vertical-speed", base.verticalSpeed())),
                Math.max(0, s.getDouble("swim-speed", base.swimSpeed())),
                Math.max(0, s.getDouble("buoyancy", base.buoyancy())),
                ShiftAction.parse(s.getString("shift-action"), base.shiftAction()),
                bank == null ? base.banking() : bank.getBoolean("enabled", base.banking()),
                bank == null ? base.bankingIntensity() : bank.getDouble("intensity", base.bankingIntensity()),
                bank == null ? base.bankingMaxAngle() : Math.max(0, bank.getDouble("max-angle", base.bankingMaxAngle())),
                bank == null ? base.smoothing() : clamp01(bank.getDouble("smoothing", base.smoothing())),
                clamp01(s.getDouble("pitch-follow", base.pitchFollow())),
                s.getBoolean("base-seat", base.baseSeat()));
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
