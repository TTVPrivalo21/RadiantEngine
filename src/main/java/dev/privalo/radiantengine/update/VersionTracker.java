package dev.privalo.radiantengine.update;

import dev.privalo.radiantengine.text.Lang;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Logger;

/**
 * Remembers the last version that ran in {@code plugins/RadiantEngine/.last-version} and prints a console notice when it
 * changes (or the first time the plugin is installed).
 */
public final class VersionTracker {

    public static final String FILE_NAME = ".last-version";

    private VersionTracker() {
    }

    /**
     * Compares the running version with the stored one, notifies the console and stores the running version.
     *
     * @return the previous version, or {@code null} on a first install
     */
    public static String track(File dataFolder, String currentVersion, Lang lang, CommandSender console, Logger logger) {
        File file = new File(dataFolder, FILE_NAME);
        String previous = read(file);
        if (previous == null) {
            lang.send(console, "version-first-install", "version", currentVersion);
        } else if (!previous.equals(currentVersion)) {
            lang.send(console, "version-changed", "old", previous, "new", currentVersion);
        }
        if (!currentVersion.equals(previous)) {
            write(file, currentVersion, logger);
        }
        return previous;
    }

    private static String read(File file) {
        if (!file.isFile()) {
            return null;
        }
        try {
            String value = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
            return value.isEmpty() ? null : value;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void write(File file, String version, Logger logger) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("no se pudo crear " + parent);
            }
            Files.writeString(file.toPath(), version + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            logger.warning("No se pudo guardar " + FILE_NAME + ": " + e.getMessage());
        }
    }
}
