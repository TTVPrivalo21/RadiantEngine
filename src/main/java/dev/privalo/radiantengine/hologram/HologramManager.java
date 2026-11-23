package dev.privalo.radiantengine.hologram;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.animation.runtime.RigState;
import dev.privalo.radiantengine.blueprint.RigBlueprint;
import dev.privalo.radiantengine.config.Settings;
import dev.privalo.radiantengine.hook.papi.PapiBridge;
import dev.privalo.radiantengine.model.ActiveRig;
import dev.privalo.radiantengine.text.Lang;
import dev.privalo.radiantengine.util.EntityCompat;
import dev.privalo.radiantengine.util.MathUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent 3D holograms stored in holograms.yml. Each hologram is a rig on a non-persistent, invulnerable
 * Interaction anchor (no gravity, no AI) that is re-created whenever its chunk is loaded.
 */
public final class HologramManager {

    private static final double TWO_PI = Math.PI * 2;

    private final RadiantEngine plugin;
    private final File file;
    private final Map<String, HologramRig> holograms = new LinkedHashMap<>();
    /** Entries whose world is not loaded; written back untouched. */
    private final Map<String, Map<String, Object>> unresolved = new LinkedHashMap<>();
    private final Map<UUID, HologramRig> anchors = new HashMap<>();
    private final Map<UUID, Long> clickCooldowns = new HashMap<>();
    private BukkitTask task;

    public HologramManager(RadiantEngine plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "holograms.yml");
    }

    // ------------------------------------------------------------------ lifecycle

    public void enable() {
        load();
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (HologramRig h : holograms.values()) {
            despawn(h);
        }
        anchors.clear();
    }

    /** Re-creates every hologram (after blueprints were reloaded). */
    public void respawnAll() {
        for (HologramRig h : holograms.values()) {
            despawn(h);
            spawn(h);
        }
    }

    // ------------------------------------------------------------------ persistence

    private void load() {
        holograms.clear();
        unresolved.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("holograms");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            World world = Bukkit.getWorld(s.getString("world", "world"));
            if (world == null) {
                unresolved.put(id, s.getValues(true));
                plugin.getLogger().warning("Holograma '" + id + "': el mundo '" + s.getString("world") + "' no esta cargado.");
                continue;
            }
            Location loc = new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                    (float) s.getDouble("yaw"), 0f);
            HologramRig h = new HologramRig(id, s.getString("model", ""), loc,
                    HologramMode.parse(s.getString("mode"), HologramMode.SPIN), (float) s.getDouble("scale", 1.0));
            Settings d = plugin.getSettings();
            h.setSpinSpeed(s.getDouble("spin-speed", d.holoSpinSpeed));
            h.setBobAmplitude(s.getDouble("bob-amplitude", d.holoBobAmplitude));
            h.setBobPeriod(s.getDouble("bob-period", d.holoBobPeriod));
            h.setFloatHeight(s.getDouble("float-height", d.holoFloatHeight));
            h.setTint(color(s.getString("tint", d.holoTint)));
            h.setFullbright(s.getBoolean("fullbright", d.holoFullbright));
            h.setGlowing(s.getBoolean("glowing", d.holoGlowing));
            h.setGlowColor(color(s.getString("glow-color", d.holoGlowColor)));
            h.setRingEnabled(s.getBoolean("ring.enabled", d.holoRingEnabled));
            h.setRingParticle(s.getString("ring.particle", d.holoRingParticle));
            h.setRingRadius(s.getDouble("ring.radius", d.holoRingRadius));
            h.setRingPoints(s.getInt("ring.points", d.holoRingPoints));
            h.setRingInterval(s.getInt("ring.interval", d.holoRingInterval));
            h.setText(s.getString("text"));
            h.setTextPosition("BELOW".equalsIgnoreCase(s.getString("text-position"))
                    ? HologramRig.TextPosition.BELOW : HologramRig.TextPosition.ABOVE);
            h.setCommand(s.getString("command"));
            holograms.put(id, h);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "Hologramas 3D de RadiantEngine. Puedes editar los valores y usar /radiant reload.",
                "mode: SPIN | BILLBOARD | STATIC   tint/glow-color: RRGGBB o \"\" para ninguno",
                "text: MiniMessage + PlaceholderAPI   command: comando al hacer clic derecho ('console:' para consola, {player})"));
        ConfigurationSection root = yaml.createSection("holograms");
        for (HologramRig h : holograms.values()) {
            ConfigurationSection s = root.createSection(h.getId());
            Location l = h.getLocation();
            s.set("model", h.getModelId());
            s.set("world", l.getWorld() == null ? "world" : l.getWorld().getName());
            s.set("x", round(l.getX()));
            s.set("y", round(l.getY()));
            s.set("z", round(l.getZ()));
            s.set("yaw", round(h.getYaw()));
            s.set("mode", h.getMode().name());
            s.set("scale", round(h.getScale()));
            s.set("spin-speed", h.getSpinSpeed());
            s.set("bob-amplitude", h.getBobAmplitude());
            s.set("bob-period", h.getBobPeriod());
            s.set("float-height", h.getFloatHeight());
            s.set("tint", hex(h.getTint()));
            s.set("fullbright", h.isFullbright());
            s.set("glowing", h.isGlowing());
            s.set("glow-color", hex(h.getGlowColor()));
            s.set("ring.enabled", h.isRingEnabled());
            s.set("ring.particle", h.getRingParticle());
            s.set("ring.radius", h.getRingRadius());
            s.set("ring.points", h.getRingPoints());
            s.set("ring.interval", h.getRingInterval());
            s.set("text", h.getText());
            s.set("text-position", h.getTextPosition().name());
            s.set("command", h.getCommand());
        }
        for (Map.Entry<String, Map<String, Object>> e : unresolved.entrySet()) {
            ConfigurationSection s = root.createSection(e.getKey());
            e.getValue().forEach((k, v) -> {
                if (!(v instanceof ConfigurationSection)) {
                    s.set(k, v);
                }
            });
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo guardar holograms.yml: " + ex.getMessage());
        }
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static Color color(String hex) {
        if (hex == null || hex.isBlank() || hex.equalsIgnoreCase("none")) {
            return null;
        }
        return Settings.parseColor(hex, null);
    }

    private static String hex(Color c) {
        return c == null ? "" : String.format("%06X", c.asRGB());
    }

    // ------------------------------------------------------------------ management

    /**
     * Creates and spawns a hologram with the configured defaults.
     *
     * @return the hologram, or null if the id is taken or the model does not exist
     */
    public HologramRig create(String id, String modelId, Location location, HologramMode mode, float scale) {
        String key = id.toLowerCase(Locale.ROOT);
        RigBlueprint bp = plugin.getRegistry().get(modelId);
        if (holograms.containsKey(key) || unresolved.containsKey(key) || bp == null || location.getWorld() == null) {
            return null;
        }
        HologramRig h = new HologramRig(key, bp.getId(), location, mode, scale);
        Settings d = plugin.getSettings();
        h.setSpinSpeed(d.holoSpinSpeed);
        h.setBobAmplitude(d.holoBobAmplitude);
        h.setBobPeriod(d.holoBobPeriod);
        h.setFloatHeight(d.holoFloatHeight);
        h.setTint(color(d.holoTint));
        h.setFullbright(d.holoFullbright);
        h.setGlowing(d.holoGlowing);
        h.setGlowColor(color(d.holoGlowColor));
        h.setRingEnabled(d.holoRingEnabled);
        h.setRingParticle(d.holoRingParticle);
        h.setRingRadius(d.holoRingRadius);
        h.setRingPoints(d.holoRingPoints);
        h.setRingInterval(d.holoRingInterval);
        holograms.put(key, h);
        save();
        spawn(h);
        return h;
    }

    public boolean remove(String id) {
        HologramRig h = holograms.remove(id.toLowerCase(Locale.ROOT));
        boolean pending = unresolved.remove(id.toLowerCase(Locale.ROOT)) != null;
        if (h == null && !pending) {
            return false;
        }
        if (h != null) {
            despawn(h);
        }
        save();
        return true;
    }

    public HologramRig get(String id) {
        return id == null ? null : holograms.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<HologramRig> all() {
        return Collections.unmodifiableCollection(holograms.values());
    }

    /** Applies changes made to a hologram (respawns it and saves holograms.yml). */
    public void update(HologramRig h) {
        despawn(h);
        spawn(h);
        save();
    }

    public void move(HologramRig h, Location location) {
        h.setLocation(location);
        h.setYaw(location.getYaw());
        update(h);
    }

    public boolean isHologramBase(Entity entity) {
        return entity != null && anchors.containsKey(entity.getUniqueId());
    }

    public HologramRig getByAnchor(Entity entity) {
        return entity == null ? null : anchors.get(entity.getUniqueId());
    }

    // ------------------------------------------------------------------ runtime

    private static boolean chunkLoaded(Location loc) {
        return loc.getWorld() != null && loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    private void spawn(HologramRig h) {
        if (!plugin.getRegistry().contains(h.getModelId()) || !chunkLoaded(h.getLocation())) {
            return;
        }
        Location loc = h.modelLocation();
        loc.setPitch(0f);
        loc.setYaw(switch (h.getMode()) {
            case BILLBOARD -> 0f;
            case SPIN -> h.currentYaw;
            case STATIC -> h.getYaw();
        });
        ActiveRig rig = plugin.getRigManager().summonProp(loc, h.getModelId(), false);
        if (rig == null) {
            return;
        }
        Entity anchor = rig.getOwner().getBase();
        anchor.setInvulnerable(true);
        anchor.setGravity(false);
        if (anchor instanceof Interaction interaction) {
            interaction.setInteractionWidth(interaction.getInteractionWidth() * h.getScale());
            interaction.setInteractionHeight(interaction.getInteractionHeight() * h.getScale());
        }
        rig.setScale(h.getScale());
        if (h.getTint() != null) {
            rig.setTint(h.getTint());
        }
        rig.setDisplayStyle(h.getMode() == HologramMode.BILLBOARD ? Display.Billboard.VERTICAL : Display.Billboard.FIXED,
                h.isFullbright() ? new Display.Brightness(15, 15) : null, h.isGlowing(), h.getGlowColor());
        rig.getStateController().setEnabled(false);
        if (rig.getStateController().hasAnimation(RigState.IDLE)) {
            rig.getStateController().play(RigState.IDLE, true);
        }
        h.anchor = anchor;
        h.rig = rig;
        anchors.put(anchor.getUniqueId(), h);
        spawnText(h);
    }

    private void despawn(HologramRig h) {
        if (h.textDisplay != null) {
            h.textDisplay.remove();
            h.textDisplay = null;
        }
        h.shownText = null;
        if (h.anchor != null) {
            anchors.remove(h.anchor.getUniqueId());
            plugin.getRigManager().removeRig(h.anchor, true);
            h.anchor = null;
        }
        h.rig = null;
    }

    private Location textLocation(HologramRig h) {
        RigBlueprint bp = plugin.getRegistry().get(h.getModelId());
        double height = bp == null ? 1.0 : bp.getMaxVerticalExtent();
        double offset = plugin.getSettings().holoTextOffset;
        Location base = h.modelLocation();
        if (h.getTextPosition() == HologramRig.TextPosition.BELOW) {
            return base.subtract(0, offset, 0);
        }
        return base.add(0, height * h.getScale() + h.getBobAmplitude() + offset, 0);
    }

    private void spawnText(HologramRig h) {
        if (h.getText() == null) {
            return;
        }
        Location loc = textLocation(h);
        loc.setYaw(0f);
        loc.setPitch(0f);
        boolean bright = h.isFullbright();
        h.textDisplay = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setShadowed(true);
            d.setSeeThrough(false);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setLineWidth(250);
            if (bright) {
                d.setBrightness(new Display.Brightness(15, 15));
            }
        });
        updateText(h);
    }

    private void updateText(HologramRig h) {
        if (h.textDisplay == null || !h.textDisplay.isValid() || h.getText() == null) {
            return;
        }
        String rendered = PapiBridge.apply(null, h.getText());
        if (!rendered.equals(h.shownText)) {
            Lang.setDisplayText(h.textDisplay, rendered);
            h.shownText = rendered;
        }
    }

    private void tick() {
        for (HologramRig h : holograms.values()) {
            h.ticks++;
            if (!h.isSpawned()) {
                if (h.ticks % 20 == 0) {
                    despawn(h);
                    spawn(h);
                }
                continue;
            }
            if (h.getMode() == HologramMode.SPIN) {
                h.currentYaw = MathUtil.wrapDegrees((float) (h.currentYaw + h.getSpinSpeed() / 20.0));
                double bob = h.getBobAmplitude() <= 0 ? 0
                        : h.getBobAmplitude() * Math.sin(TWO_PI * h.ticks / (h.getBobPeriod() * 20.0));
                Location l = h.modelLocation().add(0, bob, 0);
                l.setYaw(h.currentYaw);
                l.setPitch(0f);
                h.anchor.teleport(l);
            }
            if (h.isRingEnabled() && h.ticks % h.getRingInterval() == 0) {
                ring(h);
            }
            if (h.ticks % plugin.getSettings().holoTextRefresh == 0) {
                if (h.getText() != null && (h.textDisplay == null || !h.textDisplay.isValid())) {
                    spawnText(h);
                } else {
                    updateText(h);
                }
            }
        }
    }

    private void ring(HologramRig h) {
        Location base = h.getLocation();
        World world = base.getWorld();
        if (world == null) {
            return;
        }
        boolean viewer = false;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(base) < 48 * 48) {
                viewer = true;
                break;
            }
        }
        if (!viewer) {
            return;
        }
        Particle particle = EntityCompat.particle(h.getRingParticle());
        if (particle == null) {
            return;
        }
        Object data = null;
        if (particle.getDataType() == Particle.DustOptions.class) {
            Color c = h.getGlowColor() != null ? h.getGlowColor() : h.getTint() != null ? h.getTint() : Color.AQUA;
            data = new Particle.DustOptions(c, 0.8f);
        } else if (particle.getDataType() != Void.class) {
            return;
        }
        double radius = h.getRingRadius() * Math.max(0.5, h.getScale());
        int points = h.getRingPoints();
        double phase = h.ticks * 0.04;
        for (int i = 0; i < points; i++) {
            double a = phase + TWO_PI * i / points;
            double x = base.getX() + Math.cos(a) * radius;
            double z = base.getZ() + Math.sin(a) * radius;
            if (data == null) {
                world.spawnParticle(particle, x, base.getY() + 0.05, z, 1, 0, 0, 0, 0);
            } else {
                world.spawnParticle(particle, x, base.getY() + 0.05, z, 1, 0, 0, 0, 0, data);
            }
        }
    }

    // ------------------------------------------------------------------ interaction

    /**
     * Runs the hologram command when a player right clicks it.
     *
     * @return true if the entity is a hologram (the click is consumed)
     */
    public boolean handleInteract(Entity entity, Player player) {
        HologramRig h = getByAnchor(entity);
        if (h == null) {
            return false;
        }
        if (h.getCommand() == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long last = clickCooldowns.get(player.getUniqueId());
        if (last != null && now - last < plugin.getSettings().holoClickCooldown) {
            return true;
        }
        clickCooldowns.put(player.getUniqueId(), now);
        String cmd = h.getCommand()
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{hologram}", h.getId());
        cmd = PapiBridge.apply(player, cmd);
        if (cmd.regionMatches(true, 0, "console:", 0, 8)) {
            String console = cmd.substring(8).trim();
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), console.startsWith("/") ? console.substring(1) : console);
        } else {
            String own = cmd.startsWith("player:") ? cmd.substring(7).trim() : cmd.trim();
            player.performCommand(own.startsWith("/") ? own.substring(1) : own);
        }
        return true;
    }
}
