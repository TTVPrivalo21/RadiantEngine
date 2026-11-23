package dev.privalo.radiantengine.command;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.blueprint.BoneBlueprint;
import dev.privalo.radiantengine.blueprint.RigBlueprint;
import dev.privalo.radiantengine.config.Settings;
import dev.privalo.radiantengine.access.AccessList;
import dev.privalo.radiantengine.cosmetic.CosmeticManager;
import dev.privalo.radiantengine.gui.RigMenu;
import dev.privalo.radiantengine.pet.PetManager;
import dev.privalo.radiantengine.hologram.HologramManager;
import dev.privalo.radiantengine.hologram.HologramMode;
import dev.privalo.radiantengine.hologram.HologramRig;
import dev.privalo.radiantengine.model.ActiveRig;
import dev.privalo.radiantengine.model.RigManager;
import dev.privalo.radiantengine.model.RiggedEntity;
import dev.privalo.radiantengine.model.bone.SeatBehavior;
import dev.privalo.radiantengine.npc.NpcManager;
import dev.privalo.radiantengine.npc.NpcProvider;
import dev.privalo.radiantengine.text.Lang;
import dev.privalo.radiantengine.util.EntityCompat;
import dev.privalo.radiantengine.util.MathUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /radiant (aliases /re, /rengine) with full tab completion.
 */
public final class RadiantCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of(
            "help", "reload", "list", "info", "spawn", "summon", "apply", "remove", "kill", "killall",
            "disguise", "undisguise", "selfview", "playanim", "stopanim", "tint", "mount", "dismount", "npc", "holo", "pet", "cosmetic", "gui");

    private final RadiantEngine plugin;
    private final Lang lang;

    public RadiantCommand(RadiantEngine plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLang();
    }

    private static String permission(String sub) {
        return "radiantengine.command." + switch (sub) {
            case "info" -> "list";
            case "stopanim" -> "playanim";
            case "dismount" -> "mount";
            case "selfview" -> "disguise";
            case "killall" -> "kill";
            default -> sub;
        };
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (!SUBCOMMANDS.contains(sub)) {
            lang.send(sender, "unknown-subcommand", "label", label);
            return true;
        }
        if (!sender.hasPermission(permission(sub))) {
            lang.send(sender, "no-permission");
            return true;
        }
        try {
            switch (sub) {
                case "help" -> lang.sendList(sender, "help", "label", label, "version", plugin.getDescription().getVersion());
                case "reload" -> reload(sender, args);
                case "list" -> list(sender, args);
                case "info" -> info(sender, args);
                case "spawn" -> spawn(sender, args);
                case "summon" -> summon(sender, args);
                case "apply" -> apply(sender, args);
                case "remove" -> remove(sender, args);
                case "kill" -> kill(sender, args, false);
                case "killall" -> kill(sender, args, true);
                case "disguise" -> disguise(sender, args);
                case "undisguise" -> undisguise(sender, args);
                case "selfview" -> selfView(sender, args);
                case "playanim" -> playAnim(sender, label, args);
                case "stopanim" -> stopAnim(sender, label, args);
                case "tint" -> tint(sender, label, args);
                case "mount" -> mount(sender, args);
                case "dismount" -> dismount(sender);
                case "npc" -> npc(sender, label, args);
                case "holo" -> holo(sender, label, args);
                case "pet" -> pet(sender, label, args);
                case "cosmetic" -> cosmetic(sender, label, args);
                case "gui" -> gui(sender);
                default -> lang.send(sender, "unknown-subcommand", "label", label);
            }
        } catch (NumberFormatException e) {
            lang.send(sender, "invalid-number", "value", e.getMessage() == null ? "?" : e.getMessage().replace("For input string: ", ""));
        }
        return true;
    }

    // ------------------------------------------------------------------ reload / info

    private void reload(CommandSender sender, String[] args) {
        String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "all";
        switch (what) {
            case "config" -> {
                plugin.reloadSettings();
                lang.send(sender, "reload-config-done");
            }
            case "models" -> {
                lang.send(sender, "reload-start", "what", "modelos");
                plugin.reloadModels(sender, RadiantEngine.ReloadMode.MODELS);
            }
            case "pack" -> {
                lang.send(sender, "reload-start", "what", "resource pack");
                plugin.reloadModels(sender, RadiantEngine.ReloadMode.PACK);
            }
            default -> {
                plugin.reloadSettings();
                lang.send(sender, "reload-start", "what", "todo");
                plugin.reloadModels(sender, RadiantEngine.ReloadMode.MODELS);
            }
        }
    }

    private void list(CommandSender sender, String[] args) {
        List<String> ids = new ArrayList<>(plugin.getRegistry().all().keySet());
        int perPage = 10;
        int pages = Math.max(1, (ids.size() + perPage - 1) / perPage);
        int page = args.length > 1 ? Math.max(1, Math.min(pages, Integer.parseInt(args[1]))) : 1;
        lang.send(sender, "list-header", "count", ids.size(), "page", page, "pages", pages);
        for (int i = (page - 1) * perPage; i < Math.min(ids.size(), page * perPage); i++) {
            RigBlueprint bp = plugin.getRegistry().get(ids.get(i));
            Lang.sendRaw(sender, lang.format("list-entry", "model", bp.getId(),
                    "bones", bp.getBones().size(), "animations", bp.getAnimations().size()));
        }
    }

    private void info(CommandSender sender, String[] args) {
        RigBlueprint bp = requireModel(sender, args, 1);
        if (bp == null) {
            return;
        }
        lang.send(sender, "info-header", "model", bp.getId());
        List<String> bones = new ArrayList<>();
        for (BoneBlueprint b : bp.getBones().values()) {
            List<String> tags = new ArrayList<>();
            if (b.isHead()) tags.add("cabeza");
            if (b.isDriverSeat()) tags.add("conductor");
            else if (b.isSeat()) tags.add("asiento");
            if (b.isNametag()) tags.add("tag");
            if (b.isSubHitbox()) tags.add("hitbox");
            if (b.getItemSlot() != BoneBlueprint.ItemSlot.NONE) tags.add("item:" + b.getItemSlot().name().toLowerCase(Locale.ROOT));
            if (b.isGhost()) tags.add("fantasma");
            if (b.isRenderable()) tags.add("cmd=" + b.getCustomModelData());
            bones.add(b.getName() + (tags.isEmpty() ? "" : " [" + String.join(" ", tags) + "]"));
        }
        List<String> anims = new ArrayList<>();
        bp.getAnimations().values().forEach(a -> anims.add(a.getName() + " (" + a.getLoopMode().name().toLowerCase(Locale.ROOT)
                + ", " + String.format(Locale.ROOT, "%.2fs", a.getLength()) + (a.isOverride() ? ", override" : "") + ")"));
        String hitbox = bp.getMainHitbox() == null ? "-" : String.format(Locale.ROOT, "%.2f x %.2f (ojos %.2f)",
                bp.getMainHitbox().horizontal(), bp.getMainHitbox().height(), bp.getMainHitbox().eyeHeight());
        Lang.sendRaw(sender, lang.format("info-bones", "bones", String.join(", ", bones)));
        Lang.sendRaw(sender, lang.format("info-animations", "animations", anims.isEmpty() ? "-" : String.join(", ", anims)));
        Lang.sendRaw(sender, lang.format("info-hitbox", "hitbox", hitbox));
    }

    // ------------------------------------------------------------------ spawning

    private void spawn(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        RigBlueprint bp = player == null ? null : requireModel(sender, args, 1);
        if (bp == null) {
            return;
        }
        String typeName = args.length > 2 ? args[2] : plugin.getSettings().defaultEntity;
        EntityType type = EntityCompat.entityType(typeName);
        if (type == null || !type.isSpawnable() || type.getEntityClass() == null
                || Player.class.isAssignableFrom(type.getEntityClass())) {
            lang.send(sender, "invalid-entity", "entity", typeName);
            return;
        }
        if (!plugin.getProtection().checkSpawn(player, player.getLocation())) {
            return;
        }
        ActiveRig rig = plugin.getRigManager().spawnRig(player.getLocation(), type, bp.getId());
        if (rig == null) {
            lang.send(sender, "spawn-failed");
        } else {
            lang.send(sender, "spawn-success", "model", bp.getId(), "entity", type.getKey().getKey());
        }
    }

    private void summon(CommandSender sender, String[] args) {
        RigBlueprint bp = requireModel(sender, args, 1);
        if (bp == null) {
            return;
        }
        Location loc;
        if (args.length >= 5) {
            Location ref = sender instanceof Player p ? p.getLocation() : Bukkit.getWorlds().get(0).getSpawnLocation();
            loc = new Location(ref.getWorld(), coord(args[2], ref.getX()), coord(args[3], ref.getY()), coord(args[4], ref.getZ()));
            loc.setYaw(args.length >= 6 ? Float.parseFloat(args[5]) : ref.getYaw());
        } else if (sender instanceof Player p) {
            loc = p.getLocation();
            loc.setPitch(0f);
        } else {
            lang.send(sender, "summon-console-coords");
            return;
        }
        if (sender instanceof Player p && !plugin.getProtection().checkSpawn(p, loc)) {
            return;
        }
        ActiveRig rig = plugin.getRigManager().summonProp(loc, bp.getId(), true);
        if (rig == null) {
            lang.send(sender, "summon-failed");
        } else {
            lang.send(sender, "summon-success", "model", bp.getId());
        }
    }

    private static double coord(String arg, double relativeTo) {
        if (arg.startsWith("~")) {
            return relativeTo + (arg.length() > 1 ? Double.parseDouble(arg.substring(1)) : 0);
        }
        return Double.parseDouble(arg);
    }

    private void apply(CommandSender sender, String[] args) {
        RigBlueprint bp = requireModel(sender, args, 1);
        if (bp == null) {
            return;
        }
        NpcManager.Target selected = plugin.getNpcManager().selectedCitizensNpc(sender);
        Player player = sender instanceof Player p ? p : null;
        Entity target = player == null ? null : rayTrace(player, false);
        if (selected != null && (target == null || plugin.getNpcManager().isNpcBase(target)
                || target.hasMetadata("NPC"))) {
            plugin.getNpcManager().assign(selected, bp.getId());
            lang.send(sender, "npc-assigned", "npc", selected.id(), "provider", selected.provider().id(), "model", bp.getId());
            return;
        }
        if (target == null || target instanceof Player) {
            lang.send(sender, "apply-no-target");
            return;
        }
        boolean hide = args.length <= 2 || Boolean.parseBoolean(args[2]);
        if (hide && target instanceof LivingEntity living) {
            living.setInvisible(true);
            plugin.getKeys().setFlag(target, plugin.getKeys().hideBase, true);
        }
        ActiveRig rig = plugin.getRigManager().addModel(target, bp.getId(), true);
        if (rig == null) {
            lang.send(sender, "apply-failed");
        } else {
            lang.send(sender, "apply-success", "model", bp.getId(), "entity", target.getName());
        }
    }

    private void remove(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length > 1) {
            double radius = Double.parseDouble(args[1]);
            int removed = 0;
            for (RiggedEntity me : new ArrayList<>(plugin.getRigManager().all())) {
                Entity base = me.getBase();
                if (base instanceof Player || plugin.getNpcManager().isNpcBase(base)
                        || plugin.isManagedBase(base)
                        || !base.getWorld().equals(player.getWorld())
                        || base.getLocation().distanceSquared(player.getLocation()) > radius * radius) {
                    continue;
                }
                plugin.getRigManager().removeRig(base, true);
                removed++;
            }
            lang.send(sender, "remove-radius", "count", removed, "radius", args[1]);
            return;
        }
        Entity target = rayTrace(player, true);
        RiggedEntity me = target == null ? null : plugin.getRigManager().resolve(target);
        if (me == null || me.getBase() instanceof Player) {
            lang.send(sender, "remove-no-target");
            return;
        }
        if (plugin.getHologramManager().isHologramBase(me.getBase())) {
            lang.send(sender, "holo-use-remove", "label", "radiant");
            return;
        }
        if (plugin.isManagedBase(me.getBase())) {
            lang.send(sender, "remove-managed");
            return;
        }
        if (plugin.getNpcManager().isNpcBase(me.getBase())) {
            plugin.getNpcManager().unassignByBase(me.getBase());
        } else {
            plugin.getRigManager().removeRig(me.getBase(), true);
        }
        lang.send(sender, "remove-success");
    }

    /**
     * /radiant kill [model] [radius] and /radiant killall [radius].
     * Players without radius: their world. Console without radius: every world.
     */
    private void kill(CommandSender sender, String[] args, boolean all) {
        String model = null;
        Double radius = null;
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (isNumber(a)) {
                radius = Double.parseDouble(a);
            } else if (!all) {
                RigBlueprint bp = plugin.getRegistry().get(a);
                model = bp != null ? bp.getId() : a.toLowerCase(Locale.ROOT);
            }
        }
        Location center = null;
        World world = null;
        String scope;
        if (sender instanceof Player p) {
            world = p.getWorld();
            if (radius != null && radius > 0) {
                center = p.getLocation();
                scope = lang.format("kill-scope-radius", "radius", trim(radius));
            } else {
                scope = lang.format("kill-scope-world", "world", world.getName());
            }
        } else {
            scope = lang.format("kill-scope-all");
        }
        RigManager.KillResult result = plugin.getRigManager().kill(model, world, center, radius == null ? 0 : radius);
        // scope is MiniMessage taken from config, so it is inserted unescaped
        String text = lang.format("kill-done", "rigs", result.rigs(), "orphans", result.orphans()).replace("{scope}", scope);
        Lang.sendRaw(sender, lang.prefix() + text);
    }

    private static boolean isNumber(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    // ------------------------------------------------------------------ disguise

    private void disguise(CommandSender sender, String[] args) {
        RigBlueprint bp = requireModel(sender, args, 1);
        if (bp == null) {
            return;
        }
        Player target = targetPlayer(sender, args, 2);
        if (target == null) {
            return;
        }
        ActiveRig rig = plugin.getDisguiseManager().disguise(target, bp.getId());
        if (rig == null) {
            lang.send(sender, "disguise-failed");
        } else {
            lang.send(sender, "disguise-success", "player", target.getName(), "model", bp.getId());
        }
    }

    private void undisguise(CommandSender sender, String[] args) {
        Player target = targetPlayer(sender, args, 1);
        if (target == null) {
            return;
        }
        boolean was = plugin.getDisguiseManager().undisguise(target);
        lang.send(sender, was ? "undisguise-success" : "undisguise-none", "player", target.getName());
    }

    private void selfView(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            lang.send(sender, "selfview-current", "mode",
                    plugin.getDisguiseManager().getSelfView(player).name().toLowerCase(Locale.ROOT));
            return;
        }
        Settings.SelfView mode = Settings.SelfView.parse(args[1], null);
        if (mode == null) {
            lang.send(sender, "selfview-usage");
            return;
        }
        plugin.getDisguiseManager().setSelfView(player, mode);
        lang.send(sender, "selfview-set", "mode", mode.name().toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ animation / tint

    private void playAnim(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            lang.send(sender, "playanim-usage", "label", label);
            return;
        }
        ActiveRig rig = targetRig(sender, args[1]);
        if (rig == null) {
            return;
        }
        String anim = args[2].toLowerCase(Locale.ROOT);
        if (!rig.getBlueprint().getAnimations().containsKey(anim)) {
            lang.send(sender, "animation-missing", "animation", anim);
            return;
        }
        double lerpIn = args.length > 3 ? Double.parseDouble(args[3]) : 0.1;
        double lerpOut = args.length > 4 ? Double.parseDouble(args[4]) : 0.1;
        double speed = args.length > 5 ? Double.parseDouble(args[5]) : 1.0;
        boolean force = args.length <= 6 || Boolean.parseBoolean(args[6]);
        if (rig.playAnimation(anim, lerpIn, lerpOut, speed, force)) {
            lang.send(sender, "playanim-success", "animation", anim);
        } else {
            lang.send(sender, "playanim-busy");
        }
    }

    private void stopAnim(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            lang.send(sender, "stopanim-usage", "label", label);
            return;
        }
        ActiveRig rig = targetRig(sender, args[1]);
        if (rig == null) {
            return;
        }
        rig.stopAnimation(args[2].toLowerCase(Locale.ROOT));
        lang.send(sender, "stopanim-success");
    }

    private void tint(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            lang.send(sender, "tint-usage", "label", label);
            return;
        }
        ActiveRig rig = targetRig(sender, args[1]);
        if (rig == null) {
            return;
        }
        Color color = args[2].equalsIgnoreCase("reset") ? Color.WHITE : Settings.parseColor(args[2], null);
        if (color == null) {
            lang.send(sender, "tint-invalid");
            return;
        }
        rig.setTint(color);
        plugin.getRigManager().storeTint(rig.getOwner().getBase(), rig.getId(), color);
        lang.send(sender, "tint-success");
    }

    // ------------------------------------------------------------------ mounts

    private void mount(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Entity target = rayTrace(player, true);
        RiggedEntity me = target == null ? null : plugin.getRigManager().resolve(target);
        if (me == null) {
            lang.send(sender, "mount-no-target");
            return;
        }
        if (!plugin.getProtection().checkMount(player, me.getOrigin())) {
            return;
        }
        String seat = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : null;
        for (ActiveRig rig : me.models()) {
            if (seat != null && rig.getBone(seat) == null) {
                continue;
            }
            if (plugin.getMountManager().mount(player, rig, seat, true)) {
                boolean driver = plugin.getMountManager().isDriver(player);
                lang.send(sender, driver ? "mount-driver" : "mount-success", "model", rig.getId());
                return;
            }
        }
        lang.send(sender, "mount-no-seat");
    }

    private void dismount(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player != null) {
            lang.send(sender, plugin.getMountManager().dismount(player) ? "dismount-success" : "dismount-none");
        }
    }

    // ------------------------------------------------------------------ npc

    private void npc(CommandSender sender, String label, String[] args) {
        NpcManager npcs = plugin.getNpcManager();
        if (!npcs.hasProviders()) {
            lang.send(sender, "npc-no-provider");
            return;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("list")) {
            lang.send(sender, "npc-list-header", "count", npcs.countAssignments());
            for (Map.Entry<String, Map<String, String>> provider : npcs.getAssignments().entrySet()) {
                for (Map.Entry<String, String> a : provider.getValue().entrySet()) {
                    Lang.sendRaw(sender, lang.format("npc-list-entry", "npc", a.getKey(), "provider", provider.getKey(), "model", a.getValue()));
                }
            }
            return;
        }
        if (args.length < 3) {
            lang.send(sender, "npc-usage", "label", label);
            return;
        }
        NpcManager.Target target = npcs.resolve(args[1]);
        if (target == null) {
            lang.send(sender, "npc-not-found", "npc", args[1]);
            return;
        }
        if (args[2].equalsIgnoreCase("remove") || args[2].equalsIgnoreCase("none")) {
            if (npcs.unassign(target)) {
                lang.send(sender, "npc-removed", "npc", target.id(), "provider", target.provider().id());
            } else {
                lang.send(sender, "npc-not-found", "npc", args[1]);
            }
            return;
        }
        RigBlueprint bp = requireModel(sender, args, 2);
        if (bp == null) {
            return;
        }
        npcs.assign(target, bp.getId());
        lang.send(sender, "npc-assigned", "npc", target.id(), "provider", target.provider().id(), "model", bp.getId());
    }

    // ------------------------------------------------------------------ holograms

    private void holo(CommandSender sender, String label, String[] args) {
        HologramManager holos = plugin.getHologramManager();
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "create" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return;
                }
                if (args.length < 4) {
                    lang.send(sender, "holo-usage", "label", label);
                    return;
                }
                String id = args[2].toLowerCase(Locale.ROOT);
                if (!id.matches("[a-z0-9_-]+")) {
                    lang.send(sender, "holo-invalid-id", "id", args[2]);
                    return;
                }
                RigBlueprint bp = requireModel(sender, args, 3);
                if (bp == null) {
                    return;
                }
                HologramMode mode = args.length > 4 ? HologramMode.parse(args[4], null) : HologramMode.SPIN;
                if (mode == null) {
                    lang.send(sender, "holo-invalid-mode", "mode", args[4]);
                    return;
                }
                float scale = args.length > 5 ? Float.parseFloat(args[5]) : 1f;
                if (holos.get(id) != null) {
                    lang.send(sender, "holo-exists", "id", id);
                    return;
                }
                Location loc = player.getLocation();
                if (!plugin.getProtection().checkSpawn(player, loc)) {
                    return;
                }
                loc.setPitch(0f);
                loc.setYaw(MathUtil.wrapDegrees(loc.getYaw() + 180f));
                HologramRig h = holos.create(id, bp.getId(), loc, mode, Math.max(0.05f, scale));
                if (h == null) {
                    lang.send(sender, "holo-exists", "id", id);
                } else {
                    lang.send(sender, "holo-created", "id", id, "model", bp.getId(), "mode", mode.name().toLowerCase(Locale.ROOT));
                }
            }
            case "remove", "delete" -> {
                HologramRig h = requireHologram(sender, label, args);
                if (h != null && holos.remove(h.getId())) {
                    lang.send(sender, "holo-removed", "id", h.getId());
                }
            }
            case "movehere" -> {
                Player player = requirePlayer(sender);
                HologramRig h = player == null ? null : requireHologram(sender, label, args);
                if (h == null) {
                    return;
                }
                Location loc = player.getLocation();
                if (!plugin.getProtection().checkSpawn(player, loc)) {
                    return;
                }
                loc.setPitch(0f);
                loc.setYaw(MathUtil.wrapDegrees(loc.getYaw() + 180f));
                holos.move(h, loc);
                lang.send(sender, "holo-moved", "id", h.getId());
            }
            case "text" -> {
                HologramRig h = requireHologram(sender, label, args);
                if (h == null) {
                    return;
                }
                String text = joined(args, 3);
                boolean clear = text.isEmpty() || text.equalsIgnoreCase("none") || text.equalsIgnoreCase("clear");
                if (!clear && (text.toLowerCase(Locale.ROOT).startsWith("below "))) {
                    h.setTextPosition(HologramRig.TextPosition.BELOW);
                    text = text.substring(6);
                } else if (!clear && text.toLowerCase(Locale.ROOT).startsWith("above ")) {
                    h.setTextPosition(HologramRig.TextPosition.ABOVE);
                    text = text.substring(6);
                }
                h.setText(clear ? null : text);
                holos.update(h);
                lang.send(sender, clear ? "holo-text-cleared" : "holo-text-set", "id", h.getId());
            }
            case "command", "cmd" -> {
                HologramRig h = requireHologram(sender, label, args);
                if (h == null) {
                    return;
                }
                String cmd = joined(args, 3);
                boolean clear = cmd.isEmpty() || cmd.equalsIgnoreCase("none") || cmd.equalsIgnoreCase("clear");
                h.setCommand(clear ? null : cmd);
                holos.save();
                lang.send(sender, clear ? "holo-command-cleared" : "holo-command-set", "id", h.getId());
            }
            case "mode" -> {
                HologramRig h = requireHologram(sender, label, args);
                if (h == null) {
                    return;
                }
                HologramMode mode = args.length > 3 ? HologramMode.parse(args[3], null) : null;
                if (mode == null) {
                    lang.send(sender, "holo-invalid-mode", "mode", args.length > 3 ? args[3] : "?");
                    return;
                }
                h.setMode(mode);
                holos.update(h);
                lang.send(sender, "holo-mode-set", "id", h.getId(), "mode", mode.name().toLowerCase(Locale.ROOT));
            }
            case "list" -> {
                lang.send(sender, "holo-list-header", "count", holos.all().size());
                for (HologramRig h : holos.all()) {
                    Location l = h.getLocation();
                    Lang.sendRaw(sender, lang.format("holo-list-entry", "id", h.getId(), "model", h.getModelId(),
                            "mode", h.getMode().name().toLowerCase(Locale.ROOT),
                            "world", l.getWorld() == null ? "?" : l.getWorld().getName(),
                            "x", l.getBlockX(), "y", l.getBlockY(), "z", l.getBlockZ()));
                }
            }
            default -> lang.send(sender, "holo-usage", "label", label);
        }
    }

    private HologramRig requireHologram(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            lang.send(sender, "holo-usage", "label", label);
            return null;
        }
        HologramRig h = plugin.getHologramManager().get(args[2]);
        if (h == null) {
            lang.send(sender, "holo-not-found", "id", args[2]);
        }
        return h;
    }

    private static String joined(String[] args, int from) {
        if (args.length <= from) {
            return "";
        }
        return String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length)).trim();
    }

    // ------------------------------------------------------------------ pets

    private void pet(CommandSender sender, String label, String[] args) {
        PetManager pets = plugin.getPetManager();
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "gui";
        switch (action) {
            case "gui", "menu" -> {
                Player player = requirePlayer(sender);
                if (player != null) {
                    pets.openMenu(player);
                }
            }
            case "summon", "spawn" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return;
                }
                if (args.length < 3) {
                    lang.send(sender, "pet-usage", "label", label);
                    return;
                }
                if (!plugin.getProtection().checkSpawn(player, player.getLocation())) {
                    return;
                }
                String id = args[2].toLowerCase(Locale.ROOT);
                switch (pets.summon(player, id)) {
                    case SUCCESS -> lang.send(sender, "pet-summoned", "pet", id);
                    case NO_ACCESS -> lang.send(sender, "pet-no-access", "pet", id);
                    case UNKNOWN -> lang.send(sender, "pet-unknown", "pet", id);
                    case FAILED -> lang.send(sender, "pet-failed", "pet", id);
                }
            }
            case "ride", "mount" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return;
                }
                if (plugin.getMountManager().getRiddenEntity(player) != null) {
                    plugin.getMountManager().dismount(player);
                    lang.send(sender, "dismount-success");
                    return;
                }
                pets.sendRideResult(player, pets.ride(player, args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : null));
            }
            case "dismiss", "remove" -> {
                Player player = requirePlayer(sender);
                if (player != null) {
                    lang.send(sender, pets.dismiss(player) ? "pet-dismissed" : "pet-none");
                }
            }
            case "list" -> {
                Player player = requirePlayer(sender);
                if (player != null) {
                    List<String> ids = pets.available(player);
                    lang.send(sender, "pet-list", "pets", ids.isEmpty() ? "-" : String.join(", ", ids));
                }
            }
            case "assign", "unassign" -> {
                if (!sender.hasPermission("radiantengine.admin")) {
                    lang.send(sender, "admin-only");
                    return;
                }
                if (args.length < 4) {
                    lang.send(sender, "pet-usage", "label", label);
                    return;
                }
                String id = args[args.length - 1].toLowerCase(Locale.ROOT);
                List<String> targets = AccessList.parseTargets(joinRange(args, 2, args.length - 1));
                if (action.equals("assign")) {
                    if (pets.definition(id) == null) {
                        lang.send(sender, "pet-unknown", "pet", id);
                        return;
                    }
                    lang.send(sender, "pet-assigned", "pet", id, "count", pets.assign(targets, id));
                } else {
                    lang.send(sender, "pet-unassigned", "pet", id, "count", pets.unassign(targets, id));
                }
            }
            default -> lang.send(sender, "pet-usage", "label", label);
        }
    }

    // ------------------------------------------------------------------ cosmetics

    private void cosmetic(CommandSender sender, String label, String[] args) {
        CosmeticManager cosmetics = plugin.getCosmeticManager();
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "gui";
        switch (action) {
            case "gui", "menu" -> {
                Player player = requirePlayer(sender);
                if (player != null) {
                    cosmetics.openMenu(player);
                }
            }
            case "equip", "wear" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return;
                }
                if (args.length < 3) {
                    lang.send(sender, "cosmetic-usage", "label", label);
                    return;
                }
                String id = args[2].toLowerCase(Locale.ROOT);
                switch (cosmetics.equip(player, id)) {
                    case SUCCESS -> lang.send(sender, "cosmetic-equipped", "cosmetic", id);
                    case NO_ACCESS -> lang.send(sender, "cosmetic-no-access", "cosmetic", id);
                    case UNKNOWN -> lang.send(sender, "cosmetic-unknown", "cosmetic", id);
                    case LIMIT -> lang.send(sender, "cosmetic-limit", "max", cosmetics.getMaxEquipped());
                    case FAILED -> lang.send(sender, "cosmetic-failed", "cosmetic", id);
                }
            }
            case "unequip", "remove" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return;
                }
                if (args.length < 3 || args[2].equalsIgnoreCase("all")) {
                    int n = cosmetics.unequipAll(player);
                    lang.send(sender, n > 0 ? "cosmetic-unequipped-all" : "cosmetic-none", "count", n);
                    return;
                }
                String id = args[2].toLowerCase(Locale.ROOT);
                lang.send(sender, cosmetics.unequip(player, id) ? "cosmetic-unequipped" : "cosmetic-not-equipped", "cosmetic", id);
            }
            case "list" -> {
                Player player = requirePlayer(sender);
                if (player != null) {
                    List<String> ids = cosmetics.available(player);
                    lang.send(sender, "cosmetic-list", "cosmetics", ids.isEmpty() ? "-" : String.join(", ", ids));
                }
            }
            case "assign", "unassign" -> {
                if (!sender.hasPermission("radiantengine.admin")) {
                    lang.send(sender, "admin-only");
                    return;
                }
                if (args.length < 4) {
                    lang.send(sender, "cosmetic-usage", "label", label);
                    return;
                }
                String id = args[args.length - 1].toLowerCase(Locale.ROOT);
                List<String> targets = AccessList.parseTargets(joinRange(args, 2, args.length - 1));
                if (action.equals("assign")) {
                    if (cosmetics.definition(id) == null) {
                        lang.send(sender, "cosmetic-unknown", "cosmetic", id);
                        return;
                    }
                    lang.send(sender, "cosmetic-assigned", "cosmetic", id, "count", cosmetics.assign(targets, id));
                } else {
                    lang.send(sender, "cosmetic-unassigned", "cosmetic", id, "count", cosmetics.unassign(targets, id));
                }
            }
            default -> lang.send(sender, "cosmetic-usage", "label", label);
        }
    }

    /** Joins args[from, to) with commas (accepts "a,b,c" as well as "a, b, c"). */
    private static String joinRange(String[] args, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private void gui(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player != null) {
            RigMenu.open(plugin, player, 0);
        }
    }

    // ------------------------------------------------------------------ helpers

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player p) {
            return p;
        }
        lang.send(sender, "players-only");
        return null;
    }

    private RigBlueprint requireModel(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            lang.send(sender, "specify-model");
            return null;
        }
        RigBlueprint bp = plugin.getRegistry().get(args[index]);
        if (bp == null) {
            lang.send(sender, "unknown-model", "model", args[index]);
        }
        return bp;
    }

    private Player targetPlayer(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            if (!sender.hasPermission("radiantengine.command.disguise.others")) {
                lang.send(sender, "no-permission-others");
                return null;
            }
            Player p = Bukkit.getPlayerExact(args[index]);
            if (p == null) {
                lang.send(sender, "player-not-found", "player", args[index]);
            }
            return p;
        }
        return requirePlayer(sender);
    }

    /** Rig on the entity the player looks at, or on the player's own disguise. */
    private ActiveRig targetRig(CommandSender sender, String modelId) {
        String id = modelId.toLowerCase(Locale.ROOT);
        if (sender instanceof Player player) {
            Entity target = rayTrace(player, true);
            RiggedEntity me = target == null ? null : plugin.getRigManager().resolve(target);
            if (me != null && me.getModel(id) != null) {
                return me.getModel(id);
            }
            RiggedEntity self = plugin.getRigManager().get(player);
            if (self != null && self.getModel(id) != null) {
                return self.getModel(id);
            }
        }
        lang.send(sender, "target-model-missing", "model", id);
        return null;
    }

    private Entity rayTrace(Player player, boolean riggedOnly) {
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceEntities(eye, eye.getDirection(), 16.0, 0.35, e -> {
            if (e.equals(player)) {
                return false;
            }
            RiggedEntity owner = plugin.getRigManager().getByPart(e);
            if (owner != null && owner.getBase().equals(player)) {
                return false;
            }
            return !riggedOnly || plugin.getRigManager().resolve(e) != null;
        });
        return result == null ? null : result.getHitEntity();
    }

    // ------------------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> allowed = new ArrayList<>();
            for (String s : SUBCOMMANDS) {
                if (sender.hasPermission(permission(s))) {
                    allowed.add(s);
                }
            }
            return filter(allowed, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!sender.hasPermission(permission(sub))) {
            return Collections.emptyList();
        }
        List<String> models = new ArrayList<>(plugin.getRegistry().all().keySet());
        switch (sub) {
            case "reload" -> {
                return args.length == 2 ? filter(List.of("models", "config", "pack"), args[1]) : Collections.emptyList();
            }
            case "info", "apply", "disguise", "playanim", "stopanim", "tint" -> {
                if (args.length == 2) {
                    return filter(models, args[1]);
                }
            }
            case "spawn" -> {
                if (args.length == 2) {
                    return filter(models, args[1]);
                }
                if (args.length == 3) {
                    return filter(EntityCompat.entityTypeNames(), args[2]);
                }
            }
            case "summon" -> {
                if (args.length == 2) {
                    return filter(models, args[1]);
                }
                if (args.length >= 3 && args.length <= 5) {
                    return filter(List.of("~"), args[args.length - 1]);
                }
                if (args.length == 6 && sender instanceof Player p) {
                    return filter(List.of(String.valueOf(Math.round(p.getLocation().getYaw()))), args[5]);
                }
            }
            case "kill" -> {
                if (args.length == 2) {
                    List<String> options = new ArrayList<>(models);
                    options.addAll(List.of("10", "25", "50", "100"));
                    return filter(options, args[1]);
                }
                if (args.length == 3) {
                    return filter(List.of("10", "25", "50", "100"), args[2]);
                }
            }
            case "killall", "remove" -> {
                if (args.length == 2) {
                    return filter(List.of("10", "25", "50", "100"), args[1]);
                }
            }
            case "undisguise" -> {
                if (args.length == 2) {
                    return filter(onlineNames(), args[1]);
                }
            }
            case "selfview" -> {
                if (args.length == 2) {
                    return filter(List.of("full", "headless", "none"), args[1]);
                }
            }
            case "mount" -> {
                if (args.length == 2 && sender instanceof Player p) {
                    Entity target = rayTrace(p, true);
                    RiggedEntity me = target == null ? null : plugin.getRigManager().resolve(target);
                    List<String> seats = new ArrayList<>();
                    if (me != null) {
                        for (ActiveRig rig : me.models()) {
                            for (SeatBehavior s : rig.getSeats()) {
                                seats.add(s.getBone().getName());
                            }
                        }
                    }
                    return filter(seats, args[1]);
                }
            }
            case "npc" -> {
                if (args.length == 2) {
                    List<String> ids = new ArrayList<>();
                    ids.add("list");
                    for (NpcProvider provider : plugin.getNpcManager().getProviders()) {
                        try {
                            for (String id : provider.npcIds()) {
                                ids.add(provider.id() + ":" + id);
                            }
                        } catch (Throwable ignored) {
                            // provider not ready yet
                        }
                    }
                    return filter(ids, args[1]);
                }
                if (args.length == 3 && !args[1].equalsIgnoreCase("list")) {
                    List<String> options = new ArrayList<>(models);
                    options.add("remove");
                    return filter(options, args[2]);
                }
            }
            case "pet", "cosmetic" -> {
                boolean pet = sub.equals("pet");
                if (args.length == 2) {
                    List<String> actions = new ArrayList<>(List.of("gui", "list",
                            pet ? "summon" : "equip", pet ? "dismiss" : "unequip"));
                    if (pet) {
                        actions.add("ride");
                    }
                    if (sender.hasPermission("radiantengine.admin")) {
                        actions.add("assign");
                        actions.add("unassign");
                    }
                    return filter(actions, args[1]);
                }
                String action = args[1].toLowerCase(Locale.ROOT);
                Player self = sender instanceof Player p ? p : null;
                if (args.length == 3 && (action.equals("summon") || action.equals("equip") || action.equals("ride")) && self != null) {
                    return filter(pet ? plugin.getPetManager().available(self) : plugin.getCosmeticManager().available(self), args[2]);
                }
                if (args.length == 3 && action.equals("unequip") && self != null) {
                    List<String> worn = new ArrayList<>(plugin.getCosmeticManager().getEquipped(self));
                    worn.add("all");
                    return filter(worn, args[2]);
                }
                if ((action.equals("assign") || action.equals("unassign")) && sender.hasPermission("radiantengine.admin")) {
                    if (args.length == 3) {
                        String current = args[2];
                        int comma = current.lastIndexOf(',');
                        String head = comma < 0 ? "" : current.substring(0, comma + 1);
                        List<String> names = new ArrayList<>();
                        for (String n : onlineNames()) {
                            names.add(head + n);
                        }
                        return filter(names, current);
                    }
                    List<String> ids = new ArrayList<>(pet ? plugin.getPetManager().definedIds() : plugin.getCosmeticManager().definedIds());
                    ids.addAll(models);
                    return filter(new ArrayList<>(new java.util.LinkedHashSet<>(ids)), args[args.length - 1]);
                }
                return Collections.emptyList();
            }
            case "holo" -> {
                if (args.length == 2) {
                    return filter(List.of("create", "remove", "movehere", "text", "command", "mode", "list"), args[1]);
                }
                String action = args[1].toLowerCase(Locale.ROOT);
                List<String> ids = new ArrayList<>();
                plugin.getHologramManager().all().forEach(h -> ids.add(h.getId()));
                if (action.equals("create")) {
                    if (args.length == 4) {
                        return filter(models, args[3]);
                    }
                    if (args.length == 5) {
                        return filter(List.of("spin", "billboard", "static"), args[4]);
                    }
                    if (args.length == 6) {
                        return filter(List.of("0.5", "1", "1.5", "2"), args[5]);
                    }
                    return Collections.emptyList();
                }
                if (args.length == 3 && !action.equals("list")) {
                    return filter(ids, args[2]);
                }
                if (action.equals("mode") && args.length == 4) {
                    return filter(List.of("spin", "billboard", "static"), args[3]);
                }
                if (action.equals("text") && args.length == 4) {
                    return filter(List.of("above", "below", "none", "<white>"), args[3]);
                }
                if (action.equals("command") && args.length == 4) {
                    return filter(List.of("console:", "warp", "spawn", "none"), args[3]);
                }
                return Collections.emptyList();
            }
            case "list" -> {
                if (args.length == 2) {
                    int pages = Math.max(1, (models.size() + 9) / 10);
                    List<String> p = new ArrayList<>();
                    for (int i = 1; i <= pages; i++) {
                        p.add(String.valueOf(i));
                    }
                    return filter(p, args[1]);
                }
            }
            default -> {
            }
        }
        if (sub.equals("disguise") && args.length == 3) {
            return filter(onlineNames(), args[2]);
        }
        if (sub.equals("apply") && args.length == 3) {
            return filter(List.of("true", "false"), args[2]);
        }
        if (sub.equals("playanim") || sub.equals("stopanim")) {
            if (args.length == 3) {
                RigBlueprint bp = plugin.getRegistry().get(args[1]);
                return bp == null ? Collections.emptyList() : filter(new ArrayList<>(bp.getAnimations().keySet()), args[2]);
            }
            if (sub.equals("playanim")) {
                if (args.length == 4 || args.length == 5) {
                    return filter(List.of("0", "0.1", "0.25", "0.5"), args[args.length - 1]);
                }
                if (args.length == 6) {
                    return filter(List.of("0.5", "1", "1.5", "2"), args[5]);
                }
                if (args.length == 7) {
                    return filter(List.of("true", "false"), args[6]);
                }
            }
        }
        if (sub.equals("tint") && args.length == 3) {
            return filter(List.of("reset", "FF0000", "00FF00", "0000FF", "FFFF00", "FF7F7F"), args[2]);
        }
        return Collections.emptyList();
    }

    private static List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        Collections.sort(out);
        return out;
    }
}
