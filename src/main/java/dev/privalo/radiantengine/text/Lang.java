package dev.privalo.radiantengine.text;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Messages from the {@code messages} section of config.yml, written in MiniMessage.
 * Placeholders use {@code {name}} and are replaced (escaped) before parsing.
 * Paper renders MiniMessage natively; Spigot receives an equivalent legacy-colored string.
 */
public final class Lang {

    private static final boolean MINIMESSAGE = detect();

    private ConfigurationSection messages;
    private String prefix = "";

    public void reload(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("messages");
        this.messages = section;
        this.prefix = section == null ? "" : section.getString("prefix", "");
    }

    public String prefix() {
        return prefix;
    }

    /** Raw MiniMessage template of a key (falls back to the key itself). */
    public String raw(String key) {
        if (messages == null) {
            return key;
        }
        String value = messages.getString(key);
        return value == null ? key : value;
    }

    public String format(String key, Object... placeholders) {
        return apply(raw(key), placeholders);
    }

    public static String apply(String template, Object... placeholders) {
        String out = template;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            out = out.replace("{" + placeholders[i] + "}", escape(String.valueOf(placeholders[i + 1])));
        }
        return out;
    }

    /** Escapes MiniMessage tags inside user supplied values. */
    public static String escape(String value) {
        return value.replace("\\", "\\\\").replace("<", "\\<");
    }

    /** Sends a prefixed message from a key. */
    public void send(CommandSender sender, String key, Object... placeholders) {
        sendRaw(sender, prefix + format(key, placeholders));
    }

    /** Sends a prefixed MiniMessage string that is not a config key. */
    public void sendText(CommandSender sender, String mini) {
        sendRaw(sender, prefix + mini);
    }

    /** Sends every line of a list key without prefix. */
    public void sendList(CommandSender sender, String key, Object... placeholders) {
        for (String line : list(key)) {
            sendRaw(sender, apply(line, placeholders));
        }
    }

    public List<String> list(String key) {
        if (messages == null) {
            return new ArrayList<>();
        }
        return messages.getStringList(key);
    }

    public static void sendRaw(CommandSender sender, String mini) {
        if (MINIMESSAGE) {
            try {
                PaperText.send(sender, mini);
                return;
            } catch (Throwable ignored) {
                // fall back to legacy below
            }
        }
        sender.sendMessage(MiniLegacy.toLegacy(mini));
    }

    /** Sets MiniMessage text on a TextDisplay (Adventure on Paper, legacy colors on Spigot). */
    public static void setDisplayText(org.bukkit.entity.TextDisplay display, String mini) {
        String text = mini.replace("\\n", "<newline>");
        if (MINIMESSAGE) {
            try {
                PaperText.setText(display, text);
                return;
            } catch (Throwable ignored) {
                // fall back to legacy below
            }
        }
        display.setText(MiniLegacy.toLegacy(text));
    }

    /** Converts MiniMessage to legacy text (for item names, titles on Spigot, logs). */
    public static String legacy(String mini) {
        return MiniLegacy.toLegacy(mini);
    }

    private static boolean detect() {
        try {
            Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
            Class<?> component = Class.forName("net.kyori.adventure.text.Component");
            CommandSender.class.getMethod("sendMessage", component);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return false;
        }
    }
}
