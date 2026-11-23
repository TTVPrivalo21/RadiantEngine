package dev.privalo.radiantengine.animation.molang;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runtime values available to Molang expressions inside Blockbench keyframes.
 */
public final class MolangContext {

    public double animTime;
    public double animLength;
    public double lifeTime;
    public double groundSpeed;
    public double verticalSpeed;
    public boolean onGround;
    public double health;
    public double maxHealth;
    public double headPitch;
    public double headYaw;
    public double bodyYaw;
    public boolean sneaking;
    public boolean sprinting;
    public final Map<String, Double> variables = new HashMap<>();

    public double query(String name) {
        return switch (name) {
            case "anim_time" -> animTime;
            case "anim_length", "animation_length" -> animLength;
            case "life_time" -> lifeTime;
            case "ground_speed", "modified_move_speed", "walk_distance" -> groundSpeed;
            case "vertical_speed" -> verticalSpeed;
            case "is_on_ground" -> onGround ? 1 : 0;
            case "health" -> health;
            case "max_health" -> maxHealth;
            case "head_x_rotation", "target_x_rotation" -> headPitch;
            case "head_y_rotation", "target_y_rotation" -> headYaw;
            case "body_y_rotation" -> bodyYaw;
            case "is_sneaking" -> sneaking ? 1 : 0;
            case "is_sprinting" -> sprinting ? 1 : 0;
            case "is_moving" -> groundSpeed > 0.001 ? 1 : 0;
            case "time_stamp" -> System.currentTimeMillis() / 50.0;
            case "random" -> ThreadLocalRandom.current().nextDouble();
            default -> 0;
        };
    }

    public double variable(String name) {
        Double v = variables.get(name);
        return v == null ? 0 : v;
    }

    public void setVariable(String name, double value) {
        variables.put(name, value);
    }
}
