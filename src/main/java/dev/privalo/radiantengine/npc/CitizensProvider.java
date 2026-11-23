package dev.privalo.radiantengine.npc;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.model.RiggedEntity;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCDespawnEvent;
import net.citizensnpcs.api.event.NPCRemoveEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Citizens NPCs are real entities: the rig is attached directly to the NPC entity, so movement (paths, waypoints)
 * drives the idle/walk states and clicks reach Citizens through the normal entity events.
 */
final class CitizensProvider implements NpcProvider, Listener {

    private final RadiantEngine plugin;
    private final NpcManager manager;

    CitizensProvider(RadiantEngine plugin, NpcManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public String id() {
        return "citizens";
    }

    private NPC find(String npcId) {
        try {
            return CitizensAPI.getNPCRegistry().getById(Integer.parseInt(npcId.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String canonical(String npcId) {
        NPC npc = find(npcId);
        return npc == null ? null : String.valueOf(npc.getId());
    }

    @Override
    public Collection<String> npcIds() {
        List<String> ids = new ArrayList<>();
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            ids.add(String.valueOf(npc.getId()));
        }
        return ids;
    }

    @Override
    public void attach(String npcId, String modelId) {
        NPC npc = find(npcId);
        if (npc == null || !npc.isSpawned() || npc.getEntity() == null) {
            return;
        }
        Entity entity = npc.getEntity();
        RiggedEntity existing = plugin.getRigManager().get(entity);
        if (existing != null) {
            if (existing.getModel(modelId) != null && existing.getModels().size() == 1) {
                manager.bind(entity, id(), npcId);
                return;
            }
            plugin.getRigManager().unload(entity);
        }
        if (plugin.getRigManager().addModel(entity, modelId, false) == null) {
            return;
        }
        if (plugin.getSettings().npcHideOriginal && entity instanceof LivingEntity living) {
            living.setInvisible(true);
        }
        manager.bind(entity, id(), npcId);
    }

    @Override
    public void detach(String npcId) {
        NPC npc = find(npcId);
        if (npc == null || npc.getEntity() == null) {
            return;
        }
        Entity entity = npc.getEntity();
        plugin.getRigManager().unload(entity);
        if (entity instanceof LivingEntity living) {
            living.setInvisible(false);
        }
        manager.unbind(entity);
    }

    @Override
    public void tick(String npcId, String modelId) {
        NPC npc = find(npcId);
        if (npc == null || !npc.isSpawned() || npc.getEntity() == null) {
            return;
        }
        RiggedEntity rig = plugin.getRigManager().get(npc.getEntity());
        if (rig == null || rig.getModel(modelId) == null) {
            attach(npcId, modelId);
        }
    }

    @Override
    public boolean interact(String npcId, Player player, boolean rightClick) {
        return false;
    }

    @Override
    public void shutdown() {
        Collection<String> assigned = manager.getAssignments().getOrDefault(id(), java.util.Map.of()).keySet();
        for (String npcId : assigned) {
            NPC npc = find(npcId);
            if (npc != null && npc.getEntity() != null) {
                plugin.getRigManager().unload(npc.getEntity());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(NPCSpawnEvent event) {
        String npcId = String.valueOf(event.getNPC().getId());
        String model = manager.getModel(id(), npcId);
        if (model == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getRegistry().contains(model)) {
                attach(npcId, model);
            }
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDespawn(NPCDespawnEvent event) {
        Entity entity = event.getNPC().getEntity();
        if (entity != null && manager.isNpcBase(entity)) {
            plugin.getRigManager().unload(entity);
            manager.unbind(entity);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(NPCRemoveEvent event) {
        String npcId = String.valueOf(event.getNPC().getId());
        Entity entity = event.getNPC().getEntity();
        if (entity != null) {
            plugin.getRigManager().unload(entity);
            manager.unbind(entity);
        }
        manager.forget(id(), npcId);
    }

    /** Citizens NPC currently selected by a command sender (for /radiant apply). */
    static String selected(org.bukkit.command.CommandSender sender) {
        NPC npc = CitizensAPI.getDefaultNPCSelector().getSelected(sender);
        return npc == null ? null : String.valueOf(npc.getId());
    }
}
