package dev.privalo.radiantengine.cosmetic;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.access.AccessList;
import dev.privalo.radiantengine.animation.runtime.LoopAnimator;
import dev.privalo.radiantengine.blueprint.RigBlueprint;
import dev.privalo.radiantengine.gui.CollectionMenu;
import dev.privalo.radiantengine.model.ActiveRig;
import dev.privalo.radiantengine.text.Lang;
import dev.privalo.radiantengine.util.EntityCompat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 3D cosmetics attached to players (back, head, orbit). Each cosmetic is a rig on an invisible ItemDisplay anchor
 * without hitbox, positioned every tick right before the rig update so it follows the body yaw, posture and movement.
 * Definitions, mass assignments and equipped cosmetics are stored in cosmetics.yml.
 */
public final class CosmeticManager implements Listener, CollectionMenu.Source {

    /** A worn cosmetic. */
    public static final class Worn {
        private final CosmeticDefinition definition;
        private final ActiveRig rig;
        private final Entity anchor;
        private final LoopAnimator animator = new LoopAnimator(0.2);
        private final double phase;

        Worn(CosmeticDefinition definition, ActiveRig rig, Entity anchor, double phase) {
            this.definition = definition;
            this.rig = rig;
            this.anchor = anchor;
            this.phase = phase;
        }

        public CosmeticDefinition getDefinition() {
            return definition;
        }

        public ActiveRig getRig() {
            return rig;
        }

        public Entity getAnchor() {
            return anchor;
        }
    }

    /** Per-player movement tracking. */
    private static final class Tracker {
        double lastX = Double.NaN;
        double lastY;
        double lastZ;
        double speed;
        double vertical;
        long ticks;
        final Map<String, Worn> worn = new LinkedHashMap<>();
    }

    private final RadiantEngine plugin;
    private final File file;
    private final Map<String, CosmeticDefinition> definitions = new LinkedHashMap<>();
    private final AccessList access = new AccessList();
    private final Map<UUID, List<String>> equipped = new LinkedHashMap<>();
    private final Map<UUID, Tracker> trackers = new HashMap<>();
    private final Map<UUID, UUID> anchors = new HashMap<>();

    private boolean onlyDefined;
    private CosmeticDefinition.Anchor defaultAnchor;
    private int maxEquipped;
    private boolean hideWhileDisguised;
    private boolean hideWhenInvisible;
    private String animIdle;
    private String animWalk;
    private String animSprint;
    private String animJump;
    private String animFly;

    public CosmeticManager(RadiantEngine plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cosmetics.yml");
    }

    // ------------------------------------------------------------------ lifecycle

    public void enable() {
        if (!file.isFile()) {
            plugin.saveResource("cosmetics.yml", false);
        }
        reload();
        plugin.getRigManager().addPreTickHook(this::tick);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        onlyDefined = c.getBoolean("cosmetics.only-defined", false);
        defaultAnchor = CosmeticDefinition.Anchor.parse(c.getString("cosmetics.default-anchor"), CosmeticDefinition.Anchor.BACK);
        maxEquipped = Math.max(1, c.getInt("cosmetics.max-equipped", 4));
        hideWhileDisguised = c.getBoolean("cosmetics.hide-while-disguised", true);
        hideWhenInvisible = c.getBoolean("cosmetics.hide-when-invisible", true);
        animIdle = c.getString("cosmetics.animations.idle", "idle");
        animWalk = c.getString("cosmetics.animations.walk", "walk");
        animSprint = c.getString("cosmetics.animations.sprint", "sprint");
        animJump = c.getString("cosmetics.animations.jump", "jump");
        animFly = c.getString("cosmetics.animations.fly", "fly");

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        definitions.clear();
        ConfigurationSection defs = yaml.getConfigurationSection("cosmetics");
        if (defs != null) {
            for (String id : defs.getKeys(false)) {
                ConfigurationSection s = defs.getConfigurationSection(id);
                if (s != null) {
                    CosmeticDefinition d = CosmeticDefinition.fromSection(id, s, defaultAnchor);
                    definitions.put(d.id(), d);
                }
            }
        }
        access.load(yaml.getConfigurationSection("assignments"));
        equipped.clear();
        ConfigurationSection eq = yaml.getConfigurationSection("equipped");
        if (eq != null) {
            for (String key : eq.getKeys(false)) {
                try {
                    List<String> ids = new ArrayList<>();
                    for (String s : eq.getStringList(key)) {
                        ids.add(s.toLowerCase(Locale.ROOT));
                    }
                    equipped.put(UUID.fromString(key), ids);
                } catch (IllegalArgumentException ignored) {
                    // invalid uuid key, skipped
                }
            }
        }
        respawnAll();
    }

    public void respawnAll() {
        for (UUID owner : new ArrayList<>(trackers.keySet())) {
            despawnAll(owner);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            restore(p);
        }
    }

    public void shutdown() {
        for (UUID owner : new ArrayList<>(trackers.keySet())) {
            despawnAll(owner);
        }
    }

    private void save() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("assignments", null);
        access.save(yaml.createSection("assignments"));
        yaml.set("equipped", null);
        ConfigurationSection eq = yaml.createSection("equipped");
        equipped.forEach((k, v) -> {
            if (!v.isEmpty()) {
                eq.set(k.toString(), new ArrayList<>(v));
            }
        });
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar cosmetics.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ access

    public CosmeticDefinition definition(String id) {
        if (id == null) {
            return null;
        }
        String key = id.toLowerCase(Locale.ROOT);
        CosmeticDefinition d = definitions.get(key);
        if (d != null) {
            return d;
        }
        if (!onlyDefined && plugin.getRegistry().contains(key)) {
            return CosmeticDefinition.implicit(key, defaultAnchor);
        }
        return null;
    }

    public boolean hasAccess(Player player, String id) {
        String key = id.toLowerCase(Locale.ROOT);
        return player.hasPermission("radiantengine.cosmetic." + key) || player.hasPermission("radiantengine.cosmetic.*")
                || access.has(player, key);
    }

    public List<String> available(Player player) {
        Set<String> ids = new LinkedHashSet<>(definitions.keySet());
        ids.addAll(access.grantedTo(player));
        if (!onlyDefined) {
            ids.addAll(plugin.getRegistry().all().keySet());
        }
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            CosmeticDefinition d = definition(id);
            if (d != null && plugin.getRegistry().contains(d.model()) && hasAccess(player, id)) {
                out.add(id);
            }
        }
        return out;
    }

    public Collection<String> definedIds() {
        return Collections.unmodifiableCollection(definitions.keySet());
    }

    public int assign(Collection<String> targets, String id) {
        int n = access.grant(targets, id);
        save();
        return n;
    }

    public int unassign(Collection<String> targets, String id) {
        int n = access.revoke(targets, id);
        save();
        for (Player p : Bukkit.getOnlinePlayers()) {
            List<String> list = equipped.get(p.getUniqueId());
            if (list != null && list.contains(id.toLowerCase(Locale.ROOT)) && !hasAccess(p, id)) {
                unequip(p, id);
            }
        }
        return n;
    }

    public Map<String, Set<String>> assignments() {
        return access.view();
    }

    // ------------------------------------------------------------------ equip

    public enum Result {
        SUCCESS,
        UNKNOWN,
        NO_ACCESS,
        LIMIT,
        FAILED
    }

    public Result equip(Player player, String id) {
        CosmeticDefinition def = definition(id);
        if (def == null || !plugin.getRegistry().contains(def.model())) {
            return Result.UNKNOWN;
        }
        if (!hasAccess(player, def.id())) {
            return Result.NO_ACCESS;
        }
        List<String> list = equipped.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        if (list.contains(def.id())) {
            return Result.SUCCESS;
        }
        if (def.anchor() != CosmeticDefinition.Anchor.ORBIT) {
            for (String other : new ArrayList<>(list)) {
                CosmeticDefinition od = definition(other);
                if (od != null && od.anchor() == def.anchor()) {
                    unequip(player, other);
                }
            }
            list = equipped.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        }
        if (list.size() >= maxEquipped) {
            return Result.LIMIT;
        }
        list.add(def.id());
        save();
        return spawn(player, def) ? Result.SUCCESS : Result.FAILED;
    }

    public boolean unequip(Player player, String id) {
        String key = id.toLowerCase(Locale.ROOT);
        List<String> list = equipped.get(player.getUniqueId());
        boolean had = list != null && list.remove(key);
        if (list != null && list.isEmpty()) {
            equipped.remove(player.getUniqueId());
        }
        Tracker t = trackers.get(player.getUniqueId());
        if (t != null) {
            Worn w = t.worn.remove(key);
            if (w != null) {
                despawn(w);
            }
        }
        if (had) {
            save();
        }
        return had;
    }

    /** @return how many cosmetics were removed */
    public int unequipAll(Player player) {
        List<String> list = equipped.remove(player.getUniqueId());
        despawnAll(player.getUniqueId());
        if (list != null && !list.isEmpty()) {
            save();
            return list.size();
        }
        return 0;
    }

    public List<String> getEquipped(Player player) {
        List<String> list = equipped.get(player.getUniqueId());
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    public Collection<Worn> getWorn(Player player) {
        Tracker t = trackers.get(player.getUniqueId());
        return t == null ? List.of() : Collections.unmodifiableCollection(t.worn.values());
    }

    public boolean isCosmeticBase(Entity entity) {
        return entity != null && anchors.containsKey(entity.getUniqueId());
    }

    private void restore(Player player) {
        List<String> list = equipped.get(player.getUniqueId());
        if (list == null || player.isDead()) {
            return;
        }
        boolean changed = false;
        for (String id : new ArrayList<>(list)) {
            CosmeticDefinition def = definition(id);
            if (def == null || !plugin.getRegistry().contains(def.model())) {
                continue;
            }
            if (!hasAccess(player, id)) {
                list.remove(id);
                changed = true;
                continue;
            }
            Tracker t = trackers.get(player.getUniqueId());
            if (t == null || !t.worn.containsKey(id)) {
                spawn(player, def);
            }
        }
        if (changed) {
            save();
        }
    }

    private boolean spawn(Player owner, CosmeticDefinition def) {
        Tracker t = trackers.computeIfAbsent(owner.getUniqueId(), k -> new Tracker());
        Location loc = owner.getLocation();
        loc.setPitch(0f);
        ActiveRig rig = plugin.getRigManager().summonDisplayAnchor(loc, def.model());
        if (rig == null) {
            return false;
        }
        rig.setScale(def.scale() * plugin.getBedrock().cosmeticScale(owner));
        rig.getStateController().setEnabled(false);
        if (def.hiddenFromOwner()) {
            rig.hideFrom(owner);
        }
        int orbitIndex = 0;
        for (Worn w : t.worn.values()) {
            if (w.definition.anchor() == CosmeticDefinition.Anchor.ORBIT) {
                orbitIndex++;
            }
        }
        Worn worn = new Worn(def, rig, rig.getOwner().getBase(), orbitIndex * 137.5);
        worn.animator.setLoop(rig, animIdle);
        t.worn.put(def.id(), worn);
        anchors.put(worn.anchor.getUniqueId(), owner.getUniqueId());
        position(owner, t, worn);
        return true;
    }

    private void despawn(Worn w) {
        anchors.remove(w.anchor.getUniqueId());
        if (w.anchor.isValid()) {
            plugin.getRigManager().removeRig(w.anchor, true);
        }
    }

    private void despawnAll(UUID owner) {
        Tracker t = trackers.remove(owner);
        if (t != null) {
            for (Worn w : t.worn.values()) {
                despawn(w);
            }
        }
    }

    // ------------------------------------------------------------------ positioning

    private void tick() {
        if (trackers.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Tracker> entry : new ArrayList<>(trackers.entrySet())) {
            Player owner = Bukkit.getPlayer(entry.getKey());
            Tracker t = entry.getValue();
            if (owner == null || !owner.isOnline()) {
                despawnAll(entry.getKey());
                continue;
            }
            Location loc = owner.getLocation();
            if (!Double.isNaN(t.lastX)) {
                double dx = loc.getX() - t.lastX;
                double dz = loc.getZ() - t.lastZ;
                t.speed = Math.sqrt(dx * dx + dz * dz);
                t.vertical = loc.getY() - t.lastY;
            }
            t.lastX = loc.getX();
            t.lastY = loc.getY();
            t.lastZ = loc.getZ();
            t.ticks++;
            boolean hidden = owner.isDead() || owner.getGameMode() == GameMode.SPECTATOR
                    || (hideWhileDisguised && plugin.getDisguiseManager().isDisguised(owner))
                    || (hideWhenInvisible && owner.hasPotionEffect(PotionEffectType.INVISIBILITY)
                    && !plugin.getDisguiseManager().isDisguised(owner));
            for (Map.Entry<String, Worn> w : new ArrayList<>(t.worn.entrySet())) {
                Worn worn = w.getValue();
                if (!worn.anchor.isValid()) {
                    anchors.remove(worn.anchor.getUniqueId());
                    t.worn.remove(w.getKey());
                    CosmeticDefinition def = definition(w.getKey());
                    if (def != null && !owner.isDead()) {
                        spawn(owner, def);
                    }
                    continue;
                }
                worn.rig.setHidden(hidden);
                position(owner, t, worn);
                animate(owner, t, worn);
            }
        }
    }

    private void position(Player owner, Tracker t, Worn worn) {
        CosmeticDefinition def = worn.definition;
        Location feet = owner.getLocation();
        float bodyYaw = EntityCompat.bodyYaw(owner);
        boolean horizontal = owner.isGliding() || owner.isSwimming();
        double bedrockY = plugin.getBedrock().cosmeticOffsetY(owner);
        Location target;
        float yaw;
        float tilt = 0f;
        switch (def.anchor()) {
            case HEAD -> {
                target = owner.getEyeLocation();
                yaw = feet.getYaw();
                if (def.followPitch()) {
                    tilt = feet.getPitch();
                }
            }
            case ORBIT -> {
                double angle = Math.toRadians(worn.phase + t.ticks * def.speed() / 20.0);
                double bob = Math.sin(t.ticks / 15.0 + worn.phase) * 0.1;
                target = feet.clone().add(Math.cos(angle) * def.radius(), def.height() + bob, Math.sin(angle) * def.radius());
                Location dir = target.clone();
                dir.setDirection(new Vector(-Math.sin(angle), 0, Math.cos(angle)).multiply(Math.signum(def.speed()) == 0 ? 1 : Math.signum(def.speed())));
                yaw = dir.getYaw();
            }
            default -> {
                target = feet.clone();
                yaw = bodyYaw;
                if (horizontal) {
                    tilt = 80f;
                    target.add(0, 0.3, 0);
                } else if (owner.isSneaking()) {
                    tilt = 20f;
                    target.add(0, -0.1, 0);
                }
            }
        }
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        double rx = -Math.cos(rad);
        double rz = -Math.sin(rad);
        if (def.anchor() != CosmeticDefinition.Anchor.ORBIT) {
            target.add(rx * def.right() + fx * def.forward(), def.up(), rz * def.right() + fz * def.forward());
        } else {
            target.add(0, def.up(), 0);
        }
        target.add(0, bedrockY, 0);
        target.setYaw(yaw);
        target.setPitch(0f);
        worn.rig.setRootTilt(tilt);
        worn.anchor.teleport(target);
    }

    private void animate(Player owner, Tracker t, Worn worn) {
        boolean moving = t.speed > 0.03;
        ActiveRig rig = worn.rig;
        if (owner.isGliding() || owner.isFlying()) {
            worn.animator.setLoop(rig, animFly, moving ? animWalk : animIdle, animIdle);
        } else if (!owner.isOnGround() && t.vertical > 0.08) {
            worn.animator.setLoop(rig, animJump, animWalk, animIdle);
        } else if (moving && owner.isSprinting()) {
            worn.animator.setLoop(rig, animSprint, animWalk, animIdle);
        } else if (moving) {
            worn.animator.setLoop(rig, animWalk, animIdle);
        } else {
            worn.animator.setLoop(rig, animIdle);
        }
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                restore(player);
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        despawnAll(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        despawnAll(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                restore(player);
            }
        }, 5L);
    }

    // ------------------------------------------------------------------ GUI source

    @Override
    public String title(boolean textured) {
        return plugin.getLang().raw(textured ? "cosmetic-gui-title-textured" : "cosmetic-gui-title");
    }

    @Override
    public List<String> entries(Player player) {
        return available(player);
    }

    @Override
    public ItemStack icon(Player player, String id) {
        CosmeticDefinition def = definition(id);
        RigBlueprint bp = def == null ? null : plugin.getRegistry().get(def.model());
        ItemStack item = bp == null ? null : plugin.getItemResolver().iconItem(bp);
        if (item == null) {
            item = new ItemStack(Material.LEATHER_CHESTPLATE);
        }
        boolean worn = getEquipped(player).contains(id);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Lang.legacy((worn ? "<green>✔ " : "<light_purple>") + "<bold>" + Lang.escape(id)));
            List<String> lore = new ArrayList<>();
            if (def != null) {
                String anchor = switch (def.anchor()) {
                    case BACK -> "espalda";
                    case HEAD -> "cabeza";
                    case ORBIT -> "orbita";
                };
                lore.add(Lang.legacy("<gray>Posicion: <white>" + anchor));
                lore.add(Lang.legacy("<gray>Modelo: <white>" + Lang.escape(def.model())));
            }
            lore.add("");
            lore.add(Lang.legacy(worn ? "<red>Click: quitar" : "<green>Click: equipar"));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void select(Player player, String id, ClickType click) {
        if (getEquipped(player).contains(id)) {
            unequip(player, id);
            plugin.getLang().send(player, "cosmetic-unequipped", "cosmetic", id);
            return;
        }
        Result r = equip(player, id);
        switch (r) {
            case SUCCESS -> plugin.getLang().send(player, "cosmetic-equipped", "cosmetic", id);
            case NO_ACCESS -> plugin.getLang().send(player, "cosmetic-no-access", "cosmetic", id);
            case UNKNOWN -> plugin.getLang().send(player, "cosmetic-unknown", "cosmetic", id);
            case LIMIT -> plugin.getLang().send(player, "cosmetic-limit", "max", maxEquipped);
            case FAILED -> plugin.getLang().send(player, "cosmetic-failed", "cosmetic", id);
        }
    }

    @Override
    public ItemStack clearButton(Player player) {
        return CollectionMenu.button(Material.BARRIER, Lang.legacy("<red>Quitar todo"),
                Lang.legacy("<gray>Retira todos tus cosmeticos."));
    }

    @Override
    public void clear(Player player) {
        int n = unequipAll(player);
        plugin.getLang().send(player, n > 0 ? "cosmetic-unequipped-all" : "cosmetic-none", "count", n);
    }

    @Override
    public ItemStack infoButton(Player player, int count) {
        List<String> worn = getEquipped(player);
        return CollectionMenu.button(Material.KNOWLEDGE_BOOK, Lang.legacy("<gold><bold>Cosmeticos"),
                Lang.legacy("<gray>Disponibles: <white>" + count),
                Lang.legacy("<gray>Equipados: <white>" + worn.size() + "/" + maxEquipped),
                Lang.legacy("<dark_gray>" + Lang.escape(String.join(", ", worn))));
    }

    public void openMenu(Player player) {
        CollectionMenu.open(plugin, player, this, 0);
    }

    public int getMaxEquipped() {
        return maxEquipped;
    }
}
