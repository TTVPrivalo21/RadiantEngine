package dev.privalo.radiantengine.npc;

import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * Bridge to one NPC plugin. Implementations reference the plugin API and are only created when it is enabled.
 */
public interface NpcProvider {

    /** Provider id used in npcs.yml and in "provider:id" specs (citizens, fancynpcs, znpcsplus). */
    String id();

    /** Returns the canonical id of an NPC (or null if it does not exist). */
    String canonical(String npcId);

    Collection<String> npcIds();

    /** Creates or refreshes the rig of an NPC. Does nothing if the NPC is not currently spawned/loaded. */
    void attach(String npcId, String modelId);

    /** Removes the rig of an NPC and shows the original NPC again. */
    void detach(String npcId);

    /** Periodic sync: re-creates missing rigs and follows NPC movement. */
    void tick(String npcId, String modelId);

    /**
     * Forwards a click on the rig (hitbox) to the NPC.
     *
     * @return true when the provider handled it (packet NPCs); false to let the regular entity event reach the NPC
     */
    boolean interact(String npcId, Player player, boolean rightClick);

    /** Removes runtime rigs on disable without touching npcs.yml. */
    void shutdown();
}
