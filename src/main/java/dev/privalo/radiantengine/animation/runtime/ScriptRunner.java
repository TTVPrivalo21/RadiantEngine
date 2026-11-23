package dev.privalo.radiantengine.animation.runtime;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.config.Settings;
import dev.privalo.radiantengine.model.ActiveRig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.Locale;
import java.util.function.BiPredicate;

/**
 * Executes "timeline" effect keyframes written in Blockbench as {@code reader:script} lines.
 * <ul>
 *   <li>{@code mm:<skill>} / {@code mythic:<skill>} - casts a MythicMobs skill from the base entity</li>
 *   <li>{@code cmd:<command>} - console command ({entity} {uuid} {x} {y} {z} {world} {model} placeholders)</li>
 *   <li>{@code anim:<name> [lerpIn] [lerpOut] [speed]} - plays another animation on the same model</li>
 *   <li>{@code stop:<name>} - stops an animation</li>
 *   <li>{@code tint:<RRGGBB>} - tints the model</li>
 *   <li>{@code sound:<key> [volume] [pitch]} - plays a sound</li>
 * </ul>
 * Lines without a reader are sent to MythicMobs when it is installed.
 */
public final class ScriptRunner {

    private final RadiantEngine plugin;
    private BiPredicate<Entity, String> mythicCaster;

    public ScriptRunner(RadiantEngine plugin) {
        this.plugin = plugin;
    }

    public void setMythicCaster(BiPredicate<Entity, String> mythicCaster) {
        this.mythicCaster = mythicCaster;
    }

    public void run(ActiveRig model, String reader, String script) {
        if (script == null || script.isBlank()) {
            return;
        }
        Entity base = model.getOwner().getBase();
        String r = reader == null ? "" : reader.toLowerCase(Locale.ROOT);
        try {
            switch (r) {
                case "", "mm", "mythic", "mythicmobs" -> {
                    if (mythicCaster != null && base != null) {
                        mythicCaster.test(base, script.trim());
                    }
                }
                case "cmd", "command", "console" -> {
                    if (plugin.getSettings().allowCommandScripts) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), placeholders(model, script.trim()));
                    }
                }
                case "anim", "play", "animation" -> {
                    String[] p = script.trim().split("\\s+");
                    double lerpIn = p.length > 1 ? Double.parseDouble(p[1]) : 0.1;
                    double lerpOut = p.length > 2 ? Double.parseDouble(p[2]) : 0.1;
                    double speed = p.length > 3 ? Double.parseDouble(p[3]) : 1.0;
                    model.playAnimation(p[0].toLowerCase(Locale.ROOT), lerpIn, lerpOut, speed, true);
                }
                case "stop" -> model.stopAnimation(script.trim().toLowerCase(Locale.ROOT));
                case "tint" -> model.setTint(Settings.parseColor(script.trim(), org.bukkit.Color.WHITE));
                case "sound" -> {
                    String[] p = script.trim().split("\\s+");
                    Location at = model.getOwner().getOrigin();
                    float volume = p.length > 1 ? Float.parseFloat(p[1]) : 1f;
                    float pitch = p.length > 2 ? Float.parseFloat(p[2]) : 1f;
                    if (at != null && at.getWorld() != null) {
                        at.getWorld().playSound(at, p[0].contains(":") ? p[0] : "minecraft:" + p[0], volume, pitch);
                    }
                }
                default -> {
                    if (plugin.getSettings().debug) {
                        plugin.getLogger().info("Lector de script desconocido '" + reader + "' en " + model.getId());
                    }
                }
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Error ejecutando script '" + reader + ":" + script + "' en " + model.getId() + ": " + ex.getMessage());
        }
    }

    private static String placeholders(ActiveRig model, String command) {
        Entity base = model.getOwner().getBase();
        Location loc = model.getOwner().getOrigin();
        String out = command.startsWith("/") ? command.substring(1) : command;
        out = out.replace("{uuid}", base.getUniqueId().toString())
                .replace("{entity}", base.getName())
                .replace("{model}", model.getId());
        if (loc != null && loc.getWorld() != null) {
            out = out.replace("{x}", String.format(Locale.ROOT, "%.3f", loc.getX()))
                    .replace("{y}", String.format(Locale.ROOT, "%.3f", loc.getY()))
                    .replace("{z}", String.format(Locale.ROOT, "%.3f", loc.getZ()))
                    .replace("{world}", loc.getWorld().getName());
        }
        return out;
    }
}
