package dev.privalo.radiantengine.npc;

import dev.privalo.radiantengine.RadiantEngine;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Model assignments for NPCs (Citizens, FancyNpcs, ZNPCsPlus), persisted in npcs.yml and re-applied whenever an
 * NPC spawns, its chunk loads or the server restarts.
 */
public final class NpcManager {

    /** Resolved NPC reference. */
    public record Target(NpcProvider provider, String id) {
    }

    private final RadiantEngine plugin;
    private final File file;
    private final Map<String, NpcProvider> providers = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> assignments = new LinkedHashMap<>();
    private final Map<UUID, String[]> bases = new HashMap<>();
    private BukkitTask task;

    public NpcManager(RadiantEngine plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "npcs.yml");
    }

    // ------------------------------------------------------------------ lifecycle

    public void enable() {
        load();
        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            register(() -> new CitizensProvider(plugin, this), "Citizens");
        }
        if (Bukkit.getPluginManager().isPluginEnabled("FancyNpcs")) {
            register(() -> new FancyNpcsProvider(plugin, this), "FancyNpcs");
        }
        if (Bukkit.getPluginManager().isPluginEnabled("ZNPCsPlus")) {
            register(() -> new ZnpcsProvider(plugin, this), "ZNPCsPlus");
        }
        restartTask();
    }

    private void register(java.util.function.Supplier<NpcProvider> factory, String name) {
        try {
            NpcProvider provider = factory.get();
            providers.put(provider.id(), provider);
            if (provider instanceof Listener listener) {
                Bukkit.getPluginManager().registerEvents(listener, plugin);
            }
            plugin.getLogger().info("Integracion de NPCs activada: " + name);
        } catch (Throwable t) {
            plugin.getLogger().warning("No se pudo activar la integracion con " + name + ": " + t);
        }
    }

    public void restartTask() {
        if (task != null) {
            task.cancel();
        }
        if (providers.isEmpty()) {
            task = null;
            return;
        }
        long interval = plugin.getSettings().npcSyncInterval;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    private void tick() {
        for (Map.Entry<String, Map<String, String>> entry : assignments.entrySet()) {
            NpcProvider provider = providers.get(entry.getKey());
            if (provider == null) {
                continue;
            }
            for (Map.Entry<String, String> a : new ArrayList<>(entry.getValue().entrySet())) {
                if (!plugin.getRegistry().contains(a.getValue())) {
                    continue;
                }
                try {
                    provider.tick(a.getKey(), a.getValue());
                } catch (Throwable t) {
                    if (plugin.getSettings().debug) {
                        plugin.getLogger().warning("Error sincronizando NPC " + a.getKey() + ": " + t);
                    }
                }
            }
        }
    }

    /** Re-applies every assignment (after the blueprints are (re)loaded). */
    public void applyAll() {
        for (NpcProvider provider : providers.values()) {
            applyProvider(provider);
        }
    }

    void applyProvider(NpcProvider provider) {
        Map<String, String> map = assignments.get(provider.id());
        if (map == null) {
            return;
        }
        for (Map.Entry<String, String> a : map.entrySet()) {
            if (plugin.getRegistry().contains(a.getValue())) {
                provider.attach(a.getKey(), a.getValue());
            }
        }
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (NpcProvider provider : providers.values()) {
            try {
                provider.shutdown();
            } catch (Throwable ignored) {
                // provider plugin may already be disabled
            }
        }
        bases.clear();
    }

    // ------------------------------------------------------------------ persistence

    private void load() {
        assignments.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String provider : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(provider);
            if (section == null) {
                continue;
            }
            Map<String, String> map = new LinkedHashMap<>();
            for (String id : section.getKeys(false)) {
                String model = section.getString(id);
                if (model != null && !model.isBlank()) {
                    map.put(id, model.toLowerCase(Locale.ROOT));
                }
            }
            assignments.put(provider.toLowerCase(Locale.ROOT), map);
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "Modelos asignados a NPCs por RadiantEngine (proveedor -> id del NPC -> modelo).",
                "Se gestiona con /radiant npc <id> <modelo|remove>."));
        for (Map.Entry<String, Map<String, String>> entry : assignments.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            ConfigurationSection section = yaml.createSection(entry.getKey());
            entry.getValue().forEach(section::set);
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar npcs.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ assignments

    public boolean hasProviders() {
        return !providers.isEmpty();
    }

    public Collection<NpcProvider> getProviders() {
        return Collections.unmodifiableCollection(providers.values());
    }

    public NpcProvider getProvider(String id) {
        return providers.get(id);
    }

    /**
     * Resolves "provider:id" or a bare id (Citizens numeric id first, then FancyNpcs, then ZNPCsPlus).
     */
    public Target resolve(String spec) {
        String s = spec.trim();
        int colon = s.indexOf(':');
        if (colon > 0) {
            NpcProvider provider = providers.get(providerAlias(s.substring(0, colon)));
            if (provider != null) {
                String id = provider.canonical(s.substring(colon + 1));
                return id == null ? null : new Target(provider, id);
            }
        }
        for (NpcProvider provider : providers.values()) {
            String id = provider.canonical(s);
            if (id != null) {
                return new Target(provider, id);
            }
        }
        return null;
    }

    private static String providerAlias(String alias) {
        return switch (alias.toLowerCase(Locale.ROOT)) {
            case "citizens", "cit", "c" -> "citizens";
            case "fancynpcs", "fancy", "f" -> "fancynpcs";
            case "znpcsplus", "znpcs", "znpc", "z" -> "znpcsplus";
            default -> alias.toLowerCase(Locale.ROOT);
        };
    }

    public void assign(Target target, String modelId) {
        String provider = target.provider().id();
        String old = getModel(provider, target.id());
        if (old != null && !old.equals(modelId)) {
            target.provider().detach(target.id());
        }
        assignments.computeIfAbsent(provider, k -> new LinkedHashMap<>()).put(target.id(), modelId);
        save();
        target.provider().attach(target.id(), modelId);
    }

    public boolean unassign(Target target) {
        Map<String, String> map = assignments.get(target.provider().id());
        if (map == null || map.remove(target.id()) == null) {
            return false;
        }
        save();
        target.provider().detach(target.id());
        return true;
    }

    /** Called by providers when the NPC itself was deleted. */
    void forget(String provider, String npcId) {
        Map<String, String> map = assignments.get(provider);
        if (map != null && map.remove(npcId) != null) {
            save();
        }
    }

    public String getModel(String provider, String npcId) {
        Map<String, String> map = assignments.get(provider);
        return map == null ? null : map.get(npcId);
    }

    /** provider -> (npc id -> model). */
    public Map<String, Map<String, String>> getAssignments() {
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        assignments.forEach((k, v) -> copy.put(k, Collections.unmodifiableMap(new LinkedHashMap<>(v))));
        return Collections.unmodifiableMap(copy);
    }

    // ------------------------------------------------------------------ base entity bindings

    void bind(Entity base, String provider, String npcId) {
        bases.put(base.getUniqueId(), new String[]{provider, npcId});
    }

    void unbind(Entity base) {
        bases.remove(base.getUniqueId());
    }

    public boolean isNpcBase(Entity entity) {
        return entity != null && bases.containsKey(entity.getUniqueId());
    }

    /** Removes the assignment of the NPC owning this rig base (used by /radiant kill). */
    public void unassignByBase(Entity base) {
        String[] key = bases.get(base.getUniqueId());
        if (key == null) {
            return;
        }
        NpcProvider provider = providers.get(key[0]);
        if (provider != null) {
            unassign(new Target(provider, key[1]));
        }
        bases.remove(base.getUniqueId());
    }

    /** Forwards a click on an NPC rig. Returns true when the click was consumed. */
    public boolean handleInteract(Entity base, Player player, boolean rightClick) {
        String[] key = base == null ? null : bases.get(base.getUniqueId());
        if (key == null) {
            return false;
        }
        NpcProvider provider = providers.get(key[0]);
        return provider != null && provider.interact(key[1], player, rightClick);
    }

    /** Target of the Citizens NPC selected by the sender (/npc select), or null. */
    public Target selectedCitizensNpc(org.bukkit.command.CommandSender sender) {
        NpcProvider citizens = providers.get("citizens");
        if (citizens == null) {
            return null;
        }
        String id = CitizensProvider.selected(sender);
        return id == null ? null : new Target(citizens, id);
    }

    public int countAssignments() {
        int n = 0;
        for (Map<String, String> m : assignments.values()) {
            n += m.size();
        }
        return n;
    }
}
