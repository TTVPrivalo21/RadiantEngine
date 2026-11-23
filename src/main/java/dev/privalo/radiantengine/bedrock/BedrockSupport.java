package dev.privalo.radiantengine.bedrock;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.api.event.RigAttachEvent;
import dev.privalo.radiantengine.config.Settings;
import dev.privalo.radiantengine.model.ActiveRig;
import dev.privalo.radiantengine.model.RiggedEntity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Bedrock Edition (Geyser / Floodgate) support. Floodgate and Geyser are accessed through reflection so the plugin
 * works the same when they are not installed.
 *
 * <p>Runtime adjustments for Bedrock players (all configurable in {@code bedrock:}): per-viewer hiding of heavy rigs,
 * particle reduction, scale/offset multipliers for their own pets and cosmetics and a dedicated disguise self view.</p>
 */
public final class BedrockSupport implements Listener {

    private final RadiantEngine plugin;
    private final Set<UUID> bedrockPlayers = new HashSet<>();
    private Object floodgateApi;
    private Method floodgateCheck;
    private Object geyserApi;
    private Method geyserCheck;
    private String backend = "none";

    private boolean enabled;
    private boolean hideHeavyRigs;
    private int maxRenderBones;
    private boolean reduceParticles;
    private double particleDistance;
    private float petScale;
    private float cosmeticScale;
    private double cosmeticOffsetY;
    private Settings.SelfView disguiseSelfView;
    private boolean prefixFallback;

    public BedrockSupport(RadiantEngine plugin) {
        this.plugin = plugin;
    }

    public void reload(FileConfiguration c) {
        enabled = c.getBoolean("bedrock.enabled", true);
        hideHeavyRigs = c.getBoolean("bedrock.hide-heavy-rigs", false);
        maxRenderBones = Math.max(1, c.getInt("bedrock.max-render-bones", 24));
        reduceParticles = c.getBoolean("bedrock.reduce-particles", true);
        particleDistance = Math.max(8, c.getDouble("bedrock.particle-distance", 48));
        petScale = (float) Math.max(0.05, c.getDouble("bedrock.pet-scale", 1.0));
        cosmeticScale = (float) Math.max(0.05, c.getDouble("bedrock.cosmetic-scale", 1.0));
        cosmeticOffsetY = c.getDouble("bedrock.cosmetic-offset-y", 0.0);
        disguiseSelfView = Settings.SelfView.parse(c.getString("bedrock.disguise-self-view"), Settings.SelfView.NONE);
        prefixFallback = c.getBoolean("bedrock.detect-by-uuid-prefix", false);
    }

    /** Looks up the Floodgate (preferred) or Geyser API. Safe to call when neither is installed. */
    public void detect() {
        floodgateApi = null;
        floodgateCheck = null;
        geyserApi = null;
        geyserCheck = null;
        backend = "none";
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateApi = api.getMethod("getInstance").invoke(null);
            floodgateCheck = api.getMethod("isFloodgatePlayer", UUID.class);
            backend = "Floodgate";
        } catch (Throwable ignored) {
            floodgateApi = null;
            floodgateCheck = null;
        }
        if (floodgateCheck == null) {
            try {
                Class<?> api = Class.forName("org.geysermc.geyser.api.GeyserApi");
                geyserApi = api.getMethod("api").invoke(null);
                geyserCheck = api.getMethod("isBedrockPlayer", UUID.class);
                backend = "Geyser";
            } catch (Throwable ignored) {
                geyserApi = null;
                geyserCheck = null;
            }
        }
        if (!"none".equals(backend)) {
            plugin.getLogger().info("Soporte Bedrock activado mediante " + backend + ".");
        }
        bedrockPlayers.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (query(p.getUniqueId())) {
                bedrockPlayers.add(p.getUniqueId());
            }
        }
    }

    public String getBackend() {
        return backend;
    }

    private boolean query(UUID uuid) {
        try {
            if (floodgateCheck != null) {
                return (boolean) floodgateCheck.invoke(floodgateApi, uuid);
            }
            if (geyserCheck != null) {
                return (boolean) geyserCheck.invoke(geyserApi, uuid);
            }
        } catch (Throwable ignored) {
            return false;
        }
        // Floodgate linked-less UUIDs start with 00000000-0000-0000-
        return prefixFallback && uuid.getMostSignificantBits() == 0L;
    }

    /** @return true if the player joined from Bedrock Edition (always false when bedrock support is disabled) */
    public boolean isBedrock(Player player) {
        return enabled && player != null && bedrockPlayers.contains(player.getUniqueId());
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ------------------------------------------------------------------ runtime adjustments

    public float petScale(Player owner) {
        return isBedrock(owner) ? petScale : 1f;
    }

    public float cosmeticScale(Player owner) {
        return isBedrock(owner) ? cosmeticScale : 1f;
    }

    public double cosmeticOffsetY(Player owner) {
        return isBedrock(owner) ? cosmeticOffsetY : 0.0;
    }

    /** Disguise self view used for Bedrock players without a personal /radiant selfview choice. */
    public Settings.SelfView disguiseSelfView() {
        return disguiseSelfView;
    }

    private boolean isHeavy(ActiveRig rig) {
        return rig.getBlueprint().getRenderableBones().size() > maxRenderBones;
    }

    /** Hides heavy rigs from one Bedrock viewer (when enabled). */
    public void applyViewer(Player viewer) {
        if (!isBedrock(viewer) || !hideHeavyRigs) {
            return;
        }
        for (RiggedEntity rigged : plugin.getRigManager().all()) {
            if (rigged.getBase().equals(viewer)) {
                continue;
            }
            for (ActiveRig rig : rigged.models()) {
                if (isHeavy(rig)) {
                    rig.hideFrom(viewer);
                }
            }
        }
    }

    /**
     * Spawns particles for nearby viewers, skipping Bedrock players when particle reduction is enabled.
     *
     * @param data particle data or null
     */
    public <T> void spawnParticle(World world, Particle particle, Location at, int count,
                                  double ox, double oy, double oz, double speed, T data) {
        double max = particleDistance * particleDistance;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(at) > max) {
                continue;
            }
            int amount = count;
            if (reduceParticles && isBedrock(p)) {
                amount = Math.max(0, count / 4);
                if (amount == 0) {
                    continue;
                }
            }
            if (data == null) {
                p.spawnParticle(particle, at, amount, ox, oy, oz, speed);
            } else {
                p.spawnParticle(particle, at, amount, ox, oy, oz, speed, data);
            }
        }
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (query(player.getUniqueId())) {
            bedrockPlayers.add(player.getUniqueId());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    applyViewer(player);
                }
            }, 20L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        bedrockPlayers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRigAttach(RigAttachEvent event) {
        if (!hideHeavyRigs || bedrockPlayers.isEmpty() || !isHeavy(event.getModel())) {
            return;
        }
        ActiveRig rig = event.getModel();
        for (UUID id : bedrockPlayers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && enabled && !rig.getOwner().getBase().equals(p)) {
                rig.hideFrom(p);
            }
        }
    }
}
