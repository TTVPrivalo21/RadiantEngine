package dev.privalo.radiantengine.text;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

/**
 * Native MiniMessage rendering on Paper. Only loaded when Adventure's MiniMessage is available.
 */
final class PaperText {

    private PaperText() {
    }

    static void setText(org.bukkit.entity.TextDisplay display, String mini) {
        display.text(MiniMessage.miniMessage().deserialize(mini));
    }

    static void send(CommandSender sender, String mini) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize(mini));
    }
}
