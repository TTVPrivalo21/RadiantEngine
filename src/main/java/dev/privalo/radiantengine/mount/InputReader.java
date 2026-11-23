package dev.privalo.radiantengine.mount;

import org.bukkit.Input;
import org.bukkit.entity.Player;

/**
 * Reads the player movement input (Bukkit 1.21.2+ Input API). Isolated so older servers never load {@link Input}.
 */
final class InputReader {

    private InputReader() {
    }

    /** @return {forward, strafe(left positive), jump(0/1), sprint(0/1)} */
    static double[] read(Player player) {
        Input input = player.getCurrentInput();
        return new double[]{
                (input.isForward() ? 1 : 0) - (input.isBackward() ? 1 : 0),
                (input.isLeft() ? 1 : 0) - (input.isRight() ? 1 : 0),
                input.isJump() ? 1 : 0,
                input.isSprint() ? 1 : 0
        };
    }
}
