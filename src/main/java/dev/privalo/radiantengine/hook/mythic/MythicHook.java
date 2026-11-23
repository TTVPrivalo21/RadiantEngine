package dev.privalo.radiantengine.hook.mythic;

import dev.privalo.radiantengine.RadiantEngine;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.bukkit.events.MythicTargeterLoadEvent;
import io.lumine.mythic.core.skills.SkillExecutor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Locale;

/**
 * MythicMobs integration. Registers mechanics:
 * {@code model}, {@code state}, {@code tint}, {@code mountmodel}, {@code dismountmodel}, {@code disguise},
 * {@code undisguise}; and targeters {@code @ModelPassengers}, {@code @ModelDriver}, {@code @MountedModel}.
 * Only loaded when MythicMobs is enabled.
 */
public final class MythicHook implements Listener {

    private final RadiantEngine plugin;

    public MythicHook(RadiantEngine plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getScriptRunner().setMythicCaster((entity, skill) -> {
            try {
                return MythicBukkit.inst().getAPIHelper().castSkill(entity, skill);
            } catch (Throwable t) {
                plugin.getLogger().warning("No se pudo lanzar la habilidad de MythicMobs '" + skill + "': " + t.getMessage());
                return false;
            }
        });
        plugin.getLogger().info("Integracion con MythicMobs activada.");
    }

    static SkillExecutor executor() {
        return MythicBukkit.inst().getSkillManager();
    }

    @EventHandler
    public void onMechanicLoad(MythicMechanicLoadEvent event) {
        String name = event.getMechanicName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "model", "radiantmodel" -> event.register(new ModelMechanic(plugin, executor(), event.getConfig()));
            case "state", "radiantstate", "animation" -> event.register(new StateMechanic(plugin, executor(), event.getConfig()));
            case "tint", "radianttint" -> event.register(new TintMechanic(plugin, executor(), event.getConfig()));
            case "mountmodel", "radiantmount" -> event.register(new MountModelMechanic(plugin, executor(), event.getConfig()));
            case "dismountmodel", "radiantdismount" -> event.register(new DismountModelMechanic(plugin, executor(), event.getConfig()));
            case "disguise", "radiantdisguise" -> event.register(new DisguiseMechanic(plugin, executor(), event.getConfig()));
            case "undisguise", "radiantundisguise" -> event.register(new UndisguiseMechanic(plugin, executor(), event.getConfig()));
            default -> {
            }
        }
    }

    @EventHandler
    public void onTargeterLoad(MythicTargeterLoadEvent event) {
        String name = event.getTargeterName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "modelpassengers", "modelpassenger" -> event.register(new ModelPassengersTargeter(plugin, executor(), event.getConfig()));
            case "modeldriver" -> event.register(new ModelDriverTargeter(plugin, executor(), event.getConfig()));
            case "mountedmodel" -> event.register(new MountedModelTargeter(plugin, executor(), event.getConfig()));
            default -> {
            }
        }
    }
}
