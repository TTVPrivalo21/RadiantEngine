package dev.privalo.radiantengine.hook.papi;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.model.ActiveRig;
import dev.privalo.radiantengine.model.RiggedEntity;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * PlaceholderAPI expansion "radiant":
 * <ul>
 *   <li>{@code %radiant_active_models%} - live rigged entities</li>
 *   <li>{@code %radiant_loaded_blueprints%} - imported .bbmodel blueprints</li>
 *   <li>{@code %radiant_player_disguise%} - model the player is disguised as</li>
 *   <li>{@code %radiant_player_mount%} - model the player is riding</li>
 *   <li>{@code %radiant_npc_models%} - NPCs with an assigned model</li>
 * </ul>
 */
public final class RadiantExpansion extends PlaceholderExpansion {

    private final RadiantEngine plugin;

    public RadiantExpansion(RadiantEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "radiant";
    }

    @Override
    public String getAuthor() {
        return "Privalo21";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, String params) {
        String none = plugin.getSettings().placeholderNone;
        switch (params.toLowerCase(Locale.ROOT)) {
            case "active_models" -> {
                int count = 0;
                for (RiggedEntity rig : plugin.getRigManager().all()) {
                    if (!rig.isEmpty()) {
                        count++;
                    }
                }
                return String.valueOf(count);
            }
            case "loaded_blueprints" -> {
                return String.valueOf(plugin.getRegistry().all().size());
            }
            case "player_pet" -> {
                Player player = offline == null ? null : offline.getPlayer();
                String pet = player == null ? null : plugin.getPetManager().getEquipped(player);
                return pet == null ? none : pet;
            }
            case "player_cosmetics" -> {
                Player player = offline == null ? null : offline.getPlayer();
                java.util.List<String> worn = player == null ? java.util.List.of() : plugin.getCosmeticManager().getEquipped(player);
                return worn.isEmpty() ? none : String.join(", ", worn);
            }
            case "player_bedrock" -> {
                Player player = offline == null ? null : offline.getPlayer();
                return String.valueOf(player != null && plugin.getBedrock().isBedrock(player));
            }
            case "npc_models" -> {
                return String.valueOf(plugin.getNpcManager().countAssignments());
            }
            case "player_disguise" -> {
                Player player = offline == null ? null : offline.getPlayer();
                String model = player == null ? null : plugin.getDisguiseManager().getDisguiseModel(player);
                return model == null ? none : model;
            }
            case "player_mount" -> {
                Player player = offline == null ? null : offline.getPlayer();
                ActiveRig rig = player == null ? null : plugin.getMountManager().getMountedModel(player);
                return rig == null ? none : rig.getId();
            }
            default -> {
                return null;
            }
        }
    }
}
