package dev.privalo.radiantengine.access;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Direct per-user grants ("item id" -> players). Players are stored as UUIDs or lower case names, so entries can be
 * written by hand in YAML either as a list or as a comma separated string:
 * <pre>
 * assignments:
 *   dragon: [Steve, 069a79f4-44e9-4726-a5be-fca90e38aaf5]
 *   cat: "Alex, Notch"
 * </pre>
 */
public final class AccessList {

    private final Map<String, Set<String>> grants = new LinkedHashMap<>();

    public void load(ConfigurationSection section) {
        grants.clear();
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            Set<String> keys = new LinkedHashSet<>();
            if (section.isList(id)) {
                for (String s : section.getStringList(id)) {
                    keys.addAll(parseTargets(s));
                }
            } else {
                keys.addAll(parseTargets(section.getString(id, "")));
            }
            if (!keys.isEmpty()) {
                grants.put(id.toLowerCase(Locale.ROOT), keys);
            }
        }
    }

    public void save(ConfigurationSection section) {
        for (Map.Entry<String, Set<String>> e : grants.entrySet()) {
            if (!e.getValue().isEmpty()) {
                section.set(e.getKey(), new ArrayList<>(e.getValue()));
            }
        }
    }

    /** Splits "a, b,c" (and spaces) into normalized player keys (UUID string or lower case name). */
    public static List<String> parseTargets(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String part : raw.split("[,;\\s]+")) {
            String key = normalize(part);
            if (key != null) {
                out.add(key);
            }
        }
        return out;
    }

    public static String normalize(String target) {
        if (target == null) {
            return null;
        }
        String t = target.trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(t).toString();
        } catch (IllegalArgumentException e) {
            return t.toLowerCase(Locale.ROOT);
        }
    }

    public boolean has(Player player, String id) {
        Set<String> keys = grants.get(id.toLowerCase(Locale.ROOT));
        return keys != null && (keys.contains(player.getUniqueId().toString())
                || keys.contains(player.getName().toLowerCase(Locale.ROOT)));
    }

    /** Ids granted directly to this player. */
    public Set<String> grantedTo(Player player) {
        Set<String> ids = new LinkedHashSet<>();
        String uuid = player.getUniqueId().toString();
        String name = player.getName().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Set<String>> e : grants.entrySet()) {
            if (e.getValue().contains(uuid) || e.getValue().contains(name)) {
                ids.add(e.getKey());
            }
        }
        return ids;
    }

    /** @return how many new grants were added */
    public int grant(Collection<String> targets, String id) {
        Set<String> keys = grants.computeIfAbsent(id.toLowerCase(Locale.ROOT), k -> new LinkedHashSet<>());
        int added = 0;
        for (String t : targets) {
            String key = normalize(t);
            if (key != null && keys.add(key)) {
                added++;
            }
        }
        return added;
    }

    /** @return how many grants were removed (matching by UUID or name) */
    public int revoke(Collection<String> targets, String id) {
        Set<String> keys = grants.get(id.toLowerCase(Locale.ROOT));
        if (keys == null) {
            return 0;
        }
        int removed = 0;
        for (String t : targets) {
            String key = normalize(t);
            if (key != null && keys.remove(key)) {
                removed++;
            }
        }
        if (keys.isEmpty()) {
            grants.remove(id.toLowerCase(Locale.ROOT));
        }
        return removed;
    }

    public Map<String, Set<String>> view() {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        grants.forEach((k, v) -> copy.put(k, Collections.unmodifiableSet(new LinkedHashSet<>(v))));
        return Collections.unmodifiableMap(copy);
    }
}
