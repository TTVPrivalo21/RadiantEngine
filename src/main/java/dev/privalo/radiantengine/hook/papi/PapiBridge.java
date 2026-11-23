package dev.privalo.radiantengine.hook.papi;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * Optional PlaceholderAPI replacement. {@link #apply(OfflinePlayer, String)} returns the input unchanged when
 * PlaceholderAPI is not installed.
 */
public final class PapiBridge {

    private PapiBridge() {
    }

    public static boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public static String apply(OfflinePlayer player, String text) {
        if (text == null || text.indexOf('%') < 0 || !available()) {
            return text;
        }
        try {
            return Real.apply(player, text);
        } catch (Throwable t) {
            return text;
        }
    }

    /** Isolated so PlaceholderAPI classes are only loaded when the plugin exists. */
    private static final class Real {
        static String apply(OfflinePlayer player, String text) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
    }
}
