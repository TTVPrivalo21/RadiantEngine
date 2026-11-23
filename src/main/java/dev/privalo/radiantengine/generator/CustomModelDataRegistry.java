package dev.privalo.radiantengine.generator;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Persistent CustomModelData assignments ("model/bone" -> id) so ids stay stable across reloads and restarts,
 * which keeps already generated items, Oraxen configs and client caches valid.
 */
public final class CustomModelDataRegistry {

    private final File file;
    private final Map<String, Integer> ids = new LinkedHashMap<>();
    private final Set<Integer> used = new HashSet<>();
    private int next;

    public CustomModelDataRegistry(File file, int start) {
        this.file = file;
        this.next = start;
        if (file.isFile()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String key : yaml.getKeys(false)) {
                int id = yaml.getInt(key, -1);
                if (id > 0) {
                    ids.put(key.replace('|', '/'), id);
                    used.add(id);
                }
            }
        }
        for (int id : used) {
            next = Math.max(next, id + 1);
        }
        if (next < start) {
            next = start;
        }
    }

    public synchronized int get(String model, String bone) {
        String key = model + "/" + bone;
        Integer existing = ids.get(key);
        if (existing != null) {
            return existing;
        }
        while (used.contains(next)) {
            next++;
        }
        int id = next++;
        ids.put(key, id);
        used.add(id);
        return id;
    }

    public synchronized void save() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Integer> e : ids.entrySet()) {
            yaml.set(e.getKey().replace('/', '|'), e.getValue());
        }
        yaml.options().setHeader(java.util.List.of(
                "CustomModelData asignados por RadiantEngine (modelo|hueso: id).",
                "No edites este archivo salvo que quieras reasignar IDs; borra una linea para liberar su ID."));
        yaml.save(file);
    }
}
