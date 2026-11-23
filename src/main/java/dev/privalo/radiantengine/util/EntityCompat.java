package dev.privalo.radiantengine.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Runtime compatibility layer between Paper and Spigot (1.21.x - 26.x) without NMS.
 */
public final class EntityCompat {

    private static final boolean PAPER_TELEPORT = classExists("io.papermc.paper.entity.TeleportFlag");
    private static final boolean INPUT_API = methodExists(Player.class, "getCurrentInput");
    private static final boolean BODY_YAW_API = methodExists(LivingEntity.class, "getBodyYaw");

    /** Entities currently being re-mounted by the Spigot teleport fallback (their dismount events are ignored). */
    private static final Set<UUID> REMOUNTING = new HashSet<>();

    private EntityCompat() {
    }

    public static boolean isPaper() {
        return PAPER_TELEPORT;
    }

    public static boolean hasInputApi() {
        return INPUT_API;
    }

    public static boolean isRemounting(Entity entity) {
        return REMOUNTING.contains(entity.getUniqueId());
    }

    /** Teleports an entity keeping its passengers mounted (Paper flag, or eject/teleport/remount on Spigot). */
    public static void teleportWithPassengers(Entity entity, Location location) {
        List<Entity> passengers = entity.getPassengers();
        if (passengers.isEmpty()) {
            entity.teleport(location);
            return;
        }
        if (PAPER_TELEPORT) {
            try {
                if (PaperTeleport.teleportRetainingPassengers(entity, location)) {
                    return;
                }
            } catch (Throwable ignored) {
                // fall back to the Spigot path below
            }
        }
        List<Entity> copy = new ArrayList<>(passengers);
        for (Entity p : copy) {
            REMOUNTING.add(p.getUniqueId());
        }
        try {
            entity.eject();
            entity.teleport(location);
            for (Entity p : copy) {
                if (p.isValid()) {
                    entity.addPassenger(p);
                }
            }
        } finally {
            for (Entity p : copy) {
                REMOUNTING.remove(p.getUniqueId());
            }
        }
    }

    public static float bodyYaw(Entity entity) {
        if (BODY_YAW_API && entity instanceof LivingEntity living) {
            try {
                return living.getBodyYaw();
            } catch (Throwable ignored) {
                return entity.getLocation().getYaw();
            }
        }
        return entity.getLocation().getYaw();
    }

    public static void setBodyYaw(Entity entity, float yaw) {
        if (BODY_YAW_API && entity instanceof LivingEntity living) {
            try {
                living.setBodyYaw(yaw);
            } catch (Throwable ignored) {
                // not supported on this server
            }
        }
    }

    public static EntityType entityType(String name) {
        if (name == null) {
            return null;
        }
        String n = name.toLowerCase(Locale.ROOT).trim();
        NamespacedKey key = n.contains(":") ? NamespacedKey.fromString(n) : NamespacedKey.minecraft(n);
        if (key == null) {
            return null;
        }
        try {
            return Registry.ENTITY_TYPE.get(key);
        } catch (Throwable t) {
            return null;
        }
    }

    public static List<String> entityTypeNames() {
        List<String> out = new ArrayList<>();
        try {
            for (EntityType type : Registry.ENTITY_TYPE) {
                if (type.isSpawnable() && type.isAlive()) {
                    out.add(type.getKey().getKey());
                }
            }
        } catch (Throwable ignored) {
            // registry unavailable
        }
        return out;
    }

    public static Particle particle(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String n = name.toLowerCase(Locale.ROOT).trim();
        NamespacedKey key = n.contains(":") ? NamespacedKey.fromString(n) : NamespacedKey.minecraft(n);
        if (key == null) {
            return null;
        }
        try {
            return Registry.PARTICLE_TYPE.get(key);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Player onlinePlayer(UUID uuid) {
        return uuid == null ? null : Bukkit.getPlayer(uuid);
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean methodExists(Class<?> type, String name) {
        try {
            type.getMethod(name);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
