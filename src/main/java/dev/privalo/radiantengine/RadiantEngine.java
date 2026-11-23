package dev.privalo.radiantengine;

import dev.privalo.radiantengine.animation.runtime.ScriptRunner;
import dev.privalo.radiantengine.api.RadiantAPI;
import dev.privalo.radiantengine.api.event.BlueprintLoadEvent;
import dev.privalo.radiantengine.command.RadiantCommand;
import dev.privalo.radiantengine.config.Settings;
import dev.privalo.radiantengine.disguise.DisguiseManager;
import dev.privalo.radiantengine.generator.ResourcePackGenerator;
import dev.privalo.radiantengine.hologram.HologramManager;
import dev.privalo.radiantengine.hook.ItemResolver;
import dev.privalo.radiantengine.hook.mythic.MythicHook;
import dev.privalo.radiantengine.hook.papi.RadiantExpansion;
import dev.privalo.radiantengine.hook.protection.Protection;
import dev.privalo.radiantengine.hook.protection.WorldGuardFlagsHolder;
import dev.privalo.radiantengine.listener.EntityListener;
import dev.privalo.radiantengine.listener.PlayerListener;
import dev.privalo.radiantengine.model.BlueprintRegistry;
import dev.privalo.radiantengine.model.RigManager;
import dev.privalo.radiantengine.mount.MountManager;
import dev.privalo.radiantengine.npc.NpcManager;
import dev.privalo.radiantengine.text.Lang;
import dev.privalo.radiantengine.util.EntityCompat;
import dev.privalo.radiantengine.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * RadiantEngine - Blockbench rigs rendered with Display Entities for Paper / Spigot 1.21.x - 26.x.
 *
 * <p>Developers should use {@link RadiantAPI}.</p>
 */
public final class RadiantEngine extends JavaPlugin {

    /** What a reload re-imports. */
    public enum ReloadMode {
        /** Re-import .bbmodel files, regenerate the pack and hot-swap every live rig. */
        MODELS,
        /** Regenerate the resource pack / Oraxen export and refresh bone items, keeping live rigs untouched. */
        PACK
    }

    private Settings settings;
    private Lang lang;
    private Keys keys;
    private BlueprintRegistry registry;
    private RigManager rigManager;
    private ResourcePackGenerator packGenerator;
    private ItemResolver itemResolver;
    private MountManager mountManager;
    private DisguiseManager disguiseManager;
    private ScriptRunner scriptRunner;
    private NpcManager npcManager;
    private Protection protection;
    private HologramManager hologramManager;
    private volatile boolean reloading;

    @Override
    public void onLoad() {
        WorldGuardFlagsHolder.registerIfPresent(getLogger());
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        this.settings = new Settings(getConfig());
        this.lang = new Lang();
        lang.reload(getConfig());
        this.keys = new Keys(this);
        this.registry = new BlueprintRegistry();
        this.packGenerator = new ResourcePackGenerator(getDataFolder());
        this.itemResolver = new ItemResolver(this);
        this.scriptRunner = new ScriptRunner(this);
        this.rigManager = new RigManager(this);
        this.mountManager = new MountManager(this);
        this.disguiseManager = new DisguiseManager(this);
        this.protection = new Protection(this);
        this.npcManager = new NpcManager(this);
        this.hologramManager = new HologramManager(this);
        RadiantAPI.init(this);

        Bukkit.getPluginManager().registerEvents(new EntityListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);

        PluginCommand command = getCommand("radiant");
        if (command != null) {
            RadiantCommand executor = new RadiantCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        protection.detect();
        npcManager.enable();
        hologramManager.enable();
        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            try {
                new MythicHook(this).register();
            } catch (Throwable t) {
                getLogger().warning("No se pudo activar la integracion con MythicMobs: " + t);
            }
        }
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                new RadiantExpansion(this).register();
                getLogger().info("Expansion de PlaceholderAPI 'radiant' registrada.");
            } catch (Throwable t) {
                getLogger().warning("No se pudo registrar la expansion de PlaceholderAPI: " + t);
            }
        }
        if (itemResolver.hasOraxen()) {
            getLogger().info("Oraxen detectado: los huesos se exportaran como items de Oraxen.");
        }
        getLogger().info("Plataforma: " + (EntityCompat.isPaper() ? "Paper" : "Spigot")
                + " | Input API: " + (EntityCompat.hasInputApi() ? "si" : "no (monturas siguen la mirada)"));

        rigManager.start();
        reloadModels(null, ReloadMode.MODELS);
    }

    @Override
    public void onDisable() {
        if (hologramManager != null) {
            hologramManager.shutdown();
        }
        if (npcManager != null) {
            npcManager.shutdown();
        }
        if (mountManager != null) {
            mountManager.shutdown();
        }
        if (disguiseManager != null) {
            disguiseManager.shutdown();
        }
        if (rigManager != null) {
            rigManager.shutdown();
        }
    }

    /** Reloads config.yml and messages only; live rigs keep running and re-read their items. */
    public void reloadSettings() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        this.settings = new Settings(getConfig());
        lang.reload(getConfig());
        itemResolver.detectOraxen();
        itemResolver.clearCache();
        protection.detect();
        npcManager.restartTask();
        rigManager.refreshItemsAll();
    }

    /**
     * Re-imports .bbmodel files off the main thread, regenerates the resource pack and Oraxen export, then applies
     * the result on the main thread without removing spawned rigs.
     */
    public void reloadModels(CommandSender sender, ReloadMode mode) {
        if (reloading) {
            if (sender != null) {
                lang.send(sender, "reload-busy");
            }
            return;
        }
        reloading = true;
        itemResolver.detectOraxen();
        itemResolver.clearCache();
        Bukkit.getPluginManager().callEvent(new BlueprintLoadEvent(BlueprintLoadEvent.Phase.PRE, null));

        Plugin oraxen = Bukkit.getPluginManager().getPlugin("Oraxen");
        File oraxenFolder = oraxen != null && oraxen.isEnabled() ? oraxen.getDataFolder() : null;
        Settings snapshot = settings;
        long start = System.currentTimeMillis();
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            ResourcePackGenerator.Output output;
            try {
                output = packGenerator.run(snapshot, oraxenFolder);
            } catch (Throwable t) {
                getLogger().severe("Error importando modelos: " + t);
                t.printStackTrace();
                output = null;
            }
            ResourcePackGenerator.Output result = output;
            Bukkit.getScheduler().runTask(this, () -> finishReload(sender, mode, result, start));
        });
    }

    private void finishReload(CommandSender sender, ReloadMode mode, ResourcePackGenerator.Output output, long start) {
        try {
            if (output == null) {
                if (sender != null) {
                    lang.send(sender, "reload-failed");
                }
                return;
            }
            for (String line : output.log()) {
                if (line.startsWith("ERROR")) {
                    getLogger().severe(line.substring(6));
                } else if (line.startsWith("WARN")) {
                    getLogger().warning(line.substring(5));
                } else {
                    getLogger().info(line.startsWith("INFO") ? line.substring(5) : line);
                }
            }
            boolean firstLoad = registry.all().isEmpty();
            registry.replace(output.blueprints());
            itemResolver.clearCache();
            if (mode == ReloadMode.MODELS || firstLoad) {
                rigManager.refreshAll();
            } else {
                rigManager.refreshItemsAll();
            }
            npcManager.applyAll();
            if (mode == ReloadMode.MODELS || firstLoad) {
                hologramManager.respawnAll();
            }
            Bukkit.getPluginManager().callEvent(new BlueprintLoadEvent(BlueprintLoadEvent.Phase.POST, output.blueprints()));
            if (output.oraxenChanged() && settings.oraxenReloadCommand != null && !settings.oraxenReloadCommand.isBlank()) {
                getLogger().info("Los assets de Oraxen cambiaron; ejecutando '" + settings.oraxenReloadCommand + "'.");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), settings.oraxenReloadCommand);
                itemResolver.clearCache();
                rigManager.refreshItemsAll();
            }
            String summary = output.blueprints().size() + " modelos, " + output.bones() + " huesos renderizables ("
                    + (System.currentTimeMillis() - start) + " ms)"
                    + (output.oraxenExported() ? ", exportado a Oraxen" : ", pack: resource_pack.zip");
            getLogger().info("Modelos cargados: " + summary);
            if (sender != null) {
                lang.send(sender, "reload-done", "summary", summary);
            }
        } finally {
            reloading = false;
        }
    }

    public Settings getSettings() {
        return settings;
    }

    public Lang getLang() {
        return lang;
    }

    public Keys getKeys() {
        return keys;
    }

    public BlueprintRegistry getRegistry() {
        return registry;
    }

    public RigManager getRigManager() {
        return rigManager;
    }

    public ResourcePackGenerator getPackGenerator() {
        return packGenerator;
    }

    public ItemResolver getItemResolver() {
        return itemResolver;
    }

    public MountManager getMountManager() {
        return mountManager;
    }

    public DisguiseManager getDisguiseManager() {
        return disguiseManager;
    }

    public ScriptRunner getScriptRunner() {
        return scriptRunner;
    }

    public NpcManager getNpcManager() {
        return npcManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public Protection getProtection() {
        return protection;
    }
}
