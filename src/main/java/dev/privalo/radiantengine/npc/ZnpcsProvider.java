package dev.privalo.radiantengine.npc;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.model.ActiveRig;
import dev.privalo.radiantengine.model.RiggedEntity;
import lol.pyr.znpcsplus.api.NpcApiProvider;
import lol.pyr.znpcsplus.api.event.NpcSpawnEvent;
import lol.pyr.znpcsplus.api.interaction.InteractionAction;
import lol.pyr.znpcsplus.api.interaction.InteractionType;
import lol.pyr.znpcsplus.api.npc.Npc;
import lol.pyr.znpcsplus.api.npc.NpcEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * ZNPCsPlus NPCs are packet entities: the rig lives on a non-persistent Interaction anchor at the NPC location, the
 * original NPC is not sent to players (spawn event cancelled) and clicks run the NPC's own interaction actions.
 */
final class ZnpcsProvider implements NpcProvider, Listener {

    private final RadiantEngine plugin;
    private final NpcManager manager;
    private final Map<String, Entity> anchors = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();

    ZnpcsProvider(RadiantEngine plugin, NpcManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public String id() {
        return "znpcsplus";
    }

    private NpcEntry find(String npcId) {
        return NpcApiProvider.get().getNpcRegistry().getById(npcId);
    }

    @Override
    public String canonical(String npcId) {
        NpcEntry entry = find(npcId);
        return entry == null ? null : entry.getId();
    }

    @Override
    public Collection<String> npcIds() {
        return new ArrayList<>(NpcApiProvider.get().getNpcRegistry().getAllIds());
    }

    private static Location location(Npc npc) {
        World world = npc.getWorld();
        if (world == null || npc.getLocation() == null) {
            return null;
        }
        return npc.getLocation().toBukkitLocation(world);
    }

    @Override
    public void attach(String npcId, String modelId) {
        NpcEntry entry = find(npcId);
        if (entry == null) {
            return;
        }
        Npc npc = entry.getNpc();
        Location loc = location(npc);
        if (loc == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            return;
        }
        Entity anchor = anchors.get(npcId);
        if (anchor != null && anchor.isValid()) {
            RiggedEntity rig = plugin.getRigManager().get(anchor);
            if (rig != null && rig.getModel(modelId) != null) {
                return;
            }
            manager.unbind(anchor);
            plugin.getRigManager().removeRig(anchor, true);
        }
        ActiveRig rig = plugin.getRigManager().summonProp(loc, modelId, false);
        if (rig == null) {
            return;
        }
        Entity base = rig.getOwner().getBase();
        anchors.put(npcId, base);
        manager.bind(base, id(), npcId);
        if (plugin.getSettings().npcHideOriginal) {
            for (Player viewer : new ArrayList<>(npc.getViewers())) {
                npc.hide(viewer);
            }
        }
    }

    @Override
    public void detach(String npcId) {
        Entity anchor = anchors.remove(npcId);
        if (anchor != null) {
            manager.unbind(anchor);
            plugin.getRigManager().removeRig(anchor, true);
        }
        NpcEntry entry = find(npcId);
        if (entry == null || !plugin.getSettings().npcHideOriginal) {
            return;
        }
        Npc npc = entry.getNpc();
        Location loc = location(npc);
        if (loc == null) {
            return;
        }
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) < 48 * 48 && !npc.isVisibleTo(p)) {
                npc.show(p);
            }
        }
    }

    @Override
    public void tick(String npcId, String modelId) {
        NpcEntry entry = find(npcId);
        if (entry == null) {
            return;
        }
        Npc npc = entry.getNpc();
        Entity anchor = anchors.get(npcId);
        if (anchor == null || !anchor.isValid()) {
            attach(npcId, modelId);
            return;
        }
        Location loc = location(npc);
        if (loc != null) {
            Location current = anchor.getLocation();
            if (!loc.getWorld().equals(current.getWorld()) || current.distanceSquared(loc) > 1.0E-4
                    || Math.abs(current.getYaw() - loc.getYaw()) > 0.5f) {
                anchor.teleport(loc);
            }
        }
        if (plugin.getSettings().npcHideOriginal) {
            for (Player viewer : new ArrayList<>(npc.getViewers())) {
                npc.hide(viewer);
            }
        }
    }

    @Override
    public boolean interact(String npcId, Player player, boolean rightClick) {
        NpcEntry entry = find(npcId);
        if (entry == null) {
            return false;
        }
        InteractionType clickType = rightClick ? InteractionType.RIGHT_CLICK : InteractionType.LEFT_CLICK;
        long now = System.currentTimeMillis();
        for (InteractionAction action : entry.getNpc().getActions()) {
            InteractionType type = action.getInteractionType();
            if (type != InteractionType.ANY_CLICK && type != clickType) {
                continue;
            }
            String key = player.getUniqueId() + ":" + action.getUuid();
            Long last = cooldowns.get(key);
            if (last != null && now - last < action.getCooldown()) {
                continue;
            }
            cooldowns.put(key, now);
            long delay = Math.max(0, action.getDelay());
            if (delay > 0) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        action.run(player);
                    }
                }, delay);
            } else {
                action.run(player);
            }
        }
        return true;
    }

    @Override
    public void shutdown() {
        for (Entity anchor : new ArrayList<>(anchors.values())) {
            manager.unbind(anchor);
            plugin.getRigManager().removeRig(anchor, true);
        }
        anchors.clear();
        cooldowns.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNpcSpawn(NpcSpawnEvent event) {
        if (plugin.getSettings().npcHideOriginal && manager.getModel(id(), event.getEntry().getId()) != null) {
            event.setCancelled(true);
        }
    }
}
