package dev.privalo.radiantengine.config;

import dev.privalo.radiantengine.animation.runtime.RigState;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable snapshot of config.yml. A new instance is built on every reload.
 */
public final class Settings {

    public record StateSettings(String animation, double lerpIn, double lerpOut, double speed) {
    }

    public final String namespace;
    public final boolean debug;

    public final Material baseItem;
    public final int customModelDataStart;
    public final boolean createZip;
    public final String packDescription;
    public final float maxElementExtent;

    public final int interpolationTicks;
    public final int teleportTicks;
    public final float viewRange;
    public final double moveThreshold;

    public final boolean oraxenEnabled;
    public final String oraxenItemsFile;
    public final boolean useOraxenItems;
    public final boolean useItemModel;
    public final String oraxenReloadCommand;

    public final boolean damageTint;
    public final Color damageTintColor;
    public final int damageTintTicks;
    public final float headMaxYaw;
    public final double walkThreshold;
    public final boolean mainHitbox;
    public final boolean hideVanillaNameWithTag;

    public final Map<RigState, StateSettings> states;

    public final String defaultEntity;
    public final boolean spawnSilent;
    public final boolean hideBase;
    public final boolean clearEquipment;

    /** How a disguised player sees their own model. */
    public enum SelfView {
        /** Whole model visible (third person looks complete, may block the first person camera). */
        FULL,
        /** Everything except head bones (h_) and nametags: complete in F5, clean first person view. */
        HEADLESS,
        /** Model hidden from its own player. */
        NONE;

        public static SelfView parse(String s, SelfView def) {
            if (s == null) {
                return def;
            }
            try {
                return valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return def;
            }
        }
    }

    public final SelfView disguiseSelfView;
    public final boolean disguiseUseHide;
    public final boolean disguisePersist;

    public final double mountSpeed;
    public final double mountJump;
    public final double seatOffsetY;
    public final boolean rightClickMount;

    public final Map<String, String> itemLinks;

    public final boolean allowCommandScripts;

    public final String guiTitle;
    public final String guiTexturedTitle;
    public final boolean npcHideOriginal;
    public final boolean bedrockGeneratePack;
    public final boolean brandingEnabled;
    public final java.util.List<String> brandingFiles;
    public final boolean brandingPackIcon;
    public final int brandingPackIconSize;
    public final boolean brandingMenuHeader;
    public final int brandingMenuLogoHeight;
    public final boolean brandingGuiItem;
    public final int brandingItemSize;
    public final boolean brandingOraxenPackIcon;
    public final boolean brandingUseDefault;
    public final double holoSpinSpeed;
    public final double holoBobAmplitude;
    public final double holoBobPeriod;
    public final double holoFloatHeight;
    public final String holoTint;
    public final boolean holoFullbright;
    public final boolean holoGlowing;
    public final String holoGlowColor;
    public final boolean holoRingEnabled;
    public final String holoRingParticle;
    public final double holoRingRadius;
    public final int holoRingPoints;
    public final int holoRingInterval;
    public final int holoTextRefresh;
    public final double holoTextOffset;
    public final long holoClickCooldown;
    public final int npcSyncInterval;
    public final boolean worldGuardEnabled;
    public final String placeholderNone;
    public final boolean guiCustomTexture;
    public final boolean guiModelIcons;
    public final int previewSeconds;

    public Settings(FileConfiguration c) {
        this.namespace = sanitizeNamespace(c.getString("namespace", "radiantengine"));
        this.debug = c.getBoolean("debug", false);

        Material base = Material.matchMaterial(c.getString("generator.base-item", "LEATHER_HORSE_ARMOR"));
        this.baseItem = base == null || !isItem(base) ? Material.LEATHER_HORSE_ARMOR : base;
        this.customModelDataStart = Math.max(1, c.getInt("generator.custom-model-data-start", 10000));
        this.createZip = c.getBoolean("generator.create-zip", true);
        this.packDescription = c.getString("generator.pack-description", "RadiantEngine resource pack");
        this.maxElementExtent = (float) Math.max(4.0, Math.min(24.0, c.getDouble("generator.max-element-extent", 24.0)));

        this.interpolationTicks = clamp(c.getInt("render.interpolation-ticks", 2), 0, 20);
        this.teleportTicks = clamp(c.getInt("render.teleport-ticks", 2), 0, 59);
        this.viewRange = (float) Math.max(0.05, c.getDouble("render.view-range", 1.0));
        this.moveThreshold = Math.max(0.0, c.getDouble("render.move-threshold", 0.0005));

        this.oraxenEnabled = c.getBoolean("oraxen.enabled", true);
        this.oraxenItemsFile = c.getString("oraxen.items-file", "radiantengine_models.yml");
        this.useOraxenItems = c.getBoolean("oraxen.use-oraxen-items", false);
        this.useItemModel = c.getBoolean("generator.use-item-model", true);
        this.oraxenReloadCommand = c.getString("oraxen.reload-command", "oraxen reload all");

        this.damageTint = c.getBoolean("model.damage-tint", true);
        this.damageTintColor = parseColor(c.getString("model.damage-tint-color", "FF6B6B"), Color.fromRGB(0xFF6B6B));
        this.damageTintTicks = Math.max(1, c.getInt("model.damage-tint-ticks", 6));
        this.headMaxYaw = (float) Math.max(0.0, Math.min(180.0, c.getDouble("model.head-max-yaw", 75.0)));
        this.walkThreshold = Math.max(0.0, c.getDouble("model.walk-threshold", 0.015));
        this.mainHitbox = c.getBoolean("model.main-hitbox", true);
        this.hideVanillaNameWithTag = c.getBoolean("model.hide-vanilla-name-with-tag", true);

        EnumMap<RigState, StateSettings> map = new EnumMap<>(RigState.class);
        for (RigState state : RigState.values()) {
            String key = state.name().toLowerCase(Locale.ROOT);
            ConfigurationSection s = c.getConfigurationSection("states." + key);
            if (s == null) {
                map.put(state, new StateSettings(key, state.defaultLerpIn(), state.defaultLerpOut(), 1.0));
            } else {
                map.put(state, new StateSettings(
                        s.getString("animation", key).toLowerCase(Locale.ROOT),
                        Math.max(0.0, s.getDouble("lerp-in", state.defaultLerpIn())),
                        Math.max(0.0, s.getDouble("lerp-out", state.defaultLerpOut())),
                        s.getDouble("speed", 1.0)));
            }
        }
        this.states = Collections.unmodifiableMap(map);

        this.defaultEntity = c.getString("spawn.default-entity", "husk").toLowerCase(Locale.ROOT);
        this.spawnSilent = c.getBoolean("spawn.silent", false);
        this.hideBase = c.getBoolean("spawn.hide-base", true);
        this.clearEquipment = c.getBoolean("spawn.clear-equipment", true);

        this.disguiseSelfView = SelfView.parse(c.getString("disguise.self-view"), SelfView.HEADLESS);
        this.disguiseUseHide = "HIDE".equalsIgnoreCase(c.getString("disguise.hide-mode", "INVISIBILITY"));
        this.disguisePersist = c.getBoolean("disguise.persist", true);

        this.mountSpeed = c.getDouble("mount.speed", 0.28);
        this.mountJump = c.getDouble("mount.jump-strength", 0.55);
        this.seatOffsetY = c.getDouble("mount.seat-offset-y", -0.4);
        this.rightClickMount = c.getBoolean("mount.right-click-to-mount", false);

        Map<String, String> links = new LinkedHashMap<>();
        ConfigurationSection linkSection = c.getConfigurationSection("item-links");
        if (linkSection != null) {
            for (String key : linkSection.getKeys(false)) {
                String value = linkSection.getString(key);
                if (value != null) {
                    links.put(key.toLowerCase(Locale.ROOT), value);
                }
            }
        }
        this.itemLinks = Collections.unmodifiableMap(links);

        this.allowCommandScripts = c.getBoolean("scripts.allow-commands", true);

        this.guiTitle = c.getString("gui.title", "<dark_gray>Modelos</dark_gray> <gray>({page}/{pages})</gray>");
        this.guiTexturedTitle = c.getString("gui.textured-title", "<white>Modelos</white> <dark_gray>»</dark_gray> <aqua>{page}</aqua><gray>/</gray><aqua>{pages}</aqua>");
        this.npcHideOriginal = c.getBoolean("npc.hide-original", true);
        this.bedrockGeneratePack = c.getBoolean("bedrock.generate-pack", true);
        this.brandingEnabled = c.getBoolean("branding.enabled", true);
        java.util.List<String> logoFiles = c.getStringList("branding.logo-files");
        this.brandingFiles = java.util.List.copyOf(logoFiles.isEmpty()
                ? java.util.List.of("logo.png", "assets/logo.png", "branding/logo.png") : logoFiles);
        this.brandingPackIcon = c.getBoolean("branding.pack-icon", true);
        this.brandingPackIconSize = powerOfTwo(c.getInt("branding.pack-icon-size", 256), 16, 512);
        this.brandingMenuHeader = c.getBoolean("branding.menu-header", true);
        this.brandingMenuLogoHeight = clamp(c.getInt("branding.menu-logo-height", 12), 4, 13);
        this.brandingGuiItem = c.getBoolean("branding.gui-item", true);
        this.brandingItemSize = powerOfTwo(c.getInt("branding.item-size", 64), 16, 256);
        this.brandingOraxenPackIcon = c.getBoolean("branding.oraxen-pack-icon", false);
        this.brandingUseDefault = c.getBoolean("branding.use-default-logo", true);
        this.holoSpinSpeed = c.getDouble("holograms.defaults.spin-speed", 30.0);
        this.holoBobAmplitude = Math.max(0, c.getDouble("holograms.defaults.bob-amplitude", 0.08));
        this.holoBobPeriod = Math.max(0.2, c.getDouble("holograms.defaults.bob-period", 3.0));
        this.holoFloatHeight = c.getDouble("holograms.defaults.float-height", 0.35);
        this.holoTint = c.getString("holograms.defaults.tint", "7FE9FF");
        this.holoFullbright = c.getBoolean("holograms.defaults.fullbright", true);
        this.holoGlowing = c.getBoolean("holograms.defaults.glowing", false);
        this.holoGlowColor = c.getString("holograms.defaults.glow-color", "00E5FF");
        this.holoRingEnabled = c.getBoolean("holograms.defaults.ring.enabled", true);
        this.holoRingParticle = c.getString("holograms.defaults.ring.particle", "end_rod");
        this.holoRingRadius = Math.max(0.1, c.getDouble("holograms.defaults.ring.radius", 0.6));
        this.holoRingPoints = Math.max(3, c.getInt("holograms.defaults.ring.points", 18));
        this.holoRingInterval = Math.max(1, c.getInt("holograms.defaults.ring.interval", 10));
        this.holoTextRefresh = Math.max(1, c.getInt("holograms.text-refresh-ticks", 20));
        this.holoTextOffset = c.getDouble("holograms.text-offset", 0.3);
        this.holoClickCooldown = Math.max(0, c.getLong("holograms.click-cooldown-ms", 500));
        this.npcSyncInterval = Math.max(5, c.getInt("npc.sync-interval", 20));
        this.worldGuardEnabled = c.getBoolean("worldguard.enabled", true);
        this.placeholderNone = c.getString("placeholderapi.none", "ninguno");
        this.guiCustomTexture = c.getBoolean("gui.custom-texture", true);
        this.guiModelIcons = c.getBoolean("gui.use-model-icons", true);
        this.previewSeconds = Math.max(1, c.getInt("gui.preview-seconds", 10));
    }

    public StateSettings state(RigState state) {
        return states.get(state);
    }

    public static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    public static Color parseColor(String hex, Color def) {
        if (hex == null) {
            return def;
        }
        String h = hex.trim();
        if (h.startsWith("#")) {
            h = h.substring(1);
        }
        if (h.startsWith("0x") || h.startsWith("0X")) {
            h = h.substring(2);
        }
        try {
            return Color.fromRGB(Integer.parseInt(h, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean isItem(Material material) {
        try {
            return material.isItem();
        } catch (Throwable t) {
            // registries not bootstrapped (offline tooling): trust the configured material
            return !material.name().endsWith("_WALL_SIGN");
        }
    }

    /** Clamps and rounds down to a power of two (Minecraft textures). */
    private static int powerOfTwo(int v, int min, int max) {
        int c = clamp(v, min, max);
        return Integer.highestOneBit(c);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String sanitizeNamespace(String ns) {
        String s = ns == null ? "radiantengine" : ns.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return s.isEmpty() ? "radiantengine" : s;
    }
}
