package dev.privalo.radiantengine.npc;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.actions.ActionTrigger;
import de.oliver.fancynpcs.api.events.NpcRemoveEvent;
import de.oliver.fancynpcs.api.events.NpcSpawnEvent;
import de.oliver.fancynpcs.api.events.NpcsLoadedEvent;
import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.model.ActiveRig;
import dev.privalo.radiantengine.model.RiggedEntity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FancyNpcs NPCs are packet entities: the rig lives on a non-persistent Interaction anchor that follows the NPC,
 * the original NPC is not sent to players (spawn event cancelled) and clicks are forwarded with
 * {@link Npc#interact(Player, ActionTrigger)} so dialogs, shops and commands keep working.
 */
final class FancyNpcsProvider implements NpcProvider, Listener {

    private final RadiantEngine plugin;
    private final NpcManager manager;
    private final Map<String, Entity> anchors = new HashMap<>();

    FancyNpcsProvider(RadiantEngine plugin, NpcManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public String id() {
        return "fancynpcs";
    }

    private Npc find(String npcId) {
        var npcManager = FancyNpcsPlugin.get().getNpcManager();
        Npc npc = npcManager.getNpc(npcId);
        return npc != null ? npc : npcManager.getNpcById(npcId);
    }

    @Override
    public String canonical(String npcId) {
        Npc npc = find(npcId);
        return npc == null ? null : npc.getData().getName();
    }

    @Override
    public Collection<String> npcIds() {
        List<String> ids = new ArrayList<>();
        for (Npc npc : FancyNpcsPlugin.get().getNpcManager().getAllNpcs()) {
            ids.add(npc.getData().getName());
        }
        return ids;
    }

    private static boolean loaded(Location loc) {
        return loc != null && loc.getWorld() != null && loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    @Override
    public void attach(String npcId, String modelId) {
        Npc npc = find(npcId);
        if (npc == null) {
            return;
        }
        Location loc = npc.getData().getLocation();
        if (!loaded(loc)) {
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
        ActiveRig rig = plugin.getRigManager().summonProp(loc.clone(), modelId, false);
        if (rig == null) {
            return;
        }
        Entity base = rig.getOwner().getBase();
        anchors.put(npcId, base);
        manager.bind(base, id(), npcId);
        if (plugin.getSettings().npcHideOriginal) {
            npc.removeForAll();
        }
    }

    @Override
    public void detach(String npcId) {
        Entity anchor = anchors.remove(npcId);
        if (anchor != null) {
            manager.unbind(anchor);
            plugin.getRigManager().removeRig(anchor, true);
        }
        Npc npc = find(npcId);
        if (npc != null && plugin.getSettings().npcHideOriginal) {
            Bukkit.getScheduler().runTask(plugin, npc::checkAndUpdateVisibilityForAll);
        }
    }

    @Override
    public void tick(String npcId, String modelId) {
        Npc npc = find(npcId);
        if (npc == null) {
            return;
        }
        Location loc = npc.getData().getLocation();
        Entity anchor = anchors.get(npcId);
        if (anchor == null || !anchor.isValid()) {
            attach(npcId, modelId);
            return;
        }
        if (loc != null && loc.getWorld() != null) {
            Location current = anchor.getLocation();
            if (!loc.getWorld().equals(current.getWorld()) || current.distanceSquared(loc) > 1.0E-4
                    || Math.abs(current.getYaw() - loc.getYaw()) > 0.5f) {
                anchor.teleport(loc.clone());
            }
        }
    }

    @Override
    public boolean interact(String npcId, Player player, boolean rightClick) {
        Npc npc = find(npcId);
        if (npc == null) {
            return false;
        }
        npc.interact(player, rightClick ? ActionTrigger.RIGHT_CLICK : ActionTrigger.LEFT_CLICK);
        return true;
    }

    @Override
    public void shutdown() {
        for (Map.Entry<String, Entity> e : new HashMap<>(anchors).entrySet()) {
            manager.unbind(e.getValue());
            plugin.getRigManager().removeRig(e.getValue(), true);
        }
        anchors.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNpcSpawn(NpcSpawnEvent event) {
        if (plugin.getSettings().npcHideOriginal
                && manager.getModel(id(), event.getNpc().getData().getName()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNpcsLoaded(NpcsLoadedEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> manager.applyProvider(this));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcRemove(NpcRemoveEvent event) {
        String npcId = event.getNpc().getData().getName();
        Entity anchor = anchors.remove(npcId);
        if (anchor != null) {
            manager.unbind(anchor);
            plugin.getRigManager().removeRig(anchor, true);
        }
        manager.forget(id(), npcId);
    }
}
