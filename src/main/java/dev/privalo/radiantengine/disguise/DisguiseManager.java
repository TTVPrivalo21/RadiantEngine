package dev.privalo.radiantengine.disguise;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.model.ActiveRig;
import dev.privalo.radiantengine.model.RiggedEntity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import dev.privalo.radiantengine.config.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Player disguises (morphs). The player becomes the base entity of the model, so movement, look direction, jumps
 * and attacks are synchronized automatically by the model ticker. The disguised player does not see their own
 * model (unless configured) so it never blocks the first person view.
 */
public final class DisguiseManager {

    private final RadiantEngine plugin;
    private final Set<UUID> invisibilityApplied = new HashSet<>();
    private final Set<UUID> hiddenPlayers = new HashSet<>();
    private final Map<UUID, Settings.SelfView> selfViews = new HashMap<>();

    public DisguiseManager(RadiantEngine plugin) {
        this.plugin = plugin;
    }

    public ActiveRig disguise(Player player, String modelId) {
        if (!plugin.getRegistry().contains(modelId)) {
            return null;
        }
        RiggedEntity existing = plugin.getRigManager().get(player);
        if (existing != null) {
            for (String id : new ArrayList<>(existing.getModels().keySet())) {
                existing.removeModel(id);
            }
        }
        ActiveRig model = plugin.getRigManager().addModel(player, modelId, false);
        if (model == null) {
            return null;
        }
        RiggedEntity me = plugin.getRigManager().get(player);
        me.setDisguise(true);
        hidePlayer(player);
        applySelfVisibility(player, me);
        if (plugin.getSettings().disguisePersist) {
            plugin.getKeys().setString(player, plugin.getKeys().disguise, model.getId());
        }
        return model;
    }

    public boolean undisguise(Player player) {
        RiggedEntity me = plugin.getRigManager().get(player);
        boolean was = me != null && me.isDisguise();
        if (me != null) {
            plugin.getRigManager().unload(player);
        }
        showPlayer(player);
        plugin.getKeys().setString(player, plugin.getKeys().disguise, null);
        return was;
    }

    public boolean isDisguised(Player player) {
        RiggedEntity me = plugin.getRigManager().get(player);
        return me != null && me.isDisguise() && !me.isEmpty();
    }

    public String getDisguiseModel(Player player) {
        RiggedEntity me = plugin.getRigManager().get(player);
        if (me == null || !me.isDisguise() || me.isEmpty()) {
            return null;
        }
        return me.getModels().keySet().iterator().next();
    }

    /** Applies the player's self view mode (config default or /radiant selfview) to their disguise model. */
    public void applySelfVisibility(Player player, RiggedEntity me) {
        Settings.SelfView mode = getSelfView(player);
        for (ActiveRig model : me.models()) {
            model.setViewMode(player, mode);
        }
    }

    public Settings.SelfView getSelfView(Player player) {
        Settings.SelfView mode = selfViews.get(player.getUniqueId());
        return mode != null ? mode : plugin.getSettings().disguiseSelfView;
    }

    public void setSelfView(Player player, Settings.SelfView mode) {
        selfViews.put(player.getUniqueId(), mode);
        RiggedEntity me = plugin.getRigManager().get(player);
        if (me != null && me.isDisguise()) {
            applySelfVisibility(player, me);
        }
    }

    private void hidePlayer(Player player) {
        if (plugin.getSettings().disguiseUseHide) {
            hiddenPlayers.add(player.getUniqueId());
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player)) {
                    other.hideEntity(plugin, player);
                }
            }
        } else {
            if (!player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                invisibilityApplied.add(player.getUniqueId());
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION,
                    0, true, false, false));
        }
    }

    private void showPlayer(Player player) {
        if (hiddenPlayers.remove(player.getUniqueId())) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player)) {
                    other.showEntity(plugin, player);
                }
            }
        }
        if (invisibilityApplied.remove(player.getUniqueId())) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
    }

    public void handleJoin(Player player) {
        for (UUID hidden : hiddenPlayers) {
            Player d = Bukkit.getPlayer(hidden);
            if (d != null && !d.equals(player)) {
                player.hideEntity(plugin, d);
            }
        }
        String stored = plugin.getKeys().string(player, plugin.getKeys().disguise);
        if (stored != null && plugin.getSettings().disguisePersist) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && plugin.getRegistry().contains(stored)) {
                    disguise(player, stored);
                }
            }, 5L);
        }
    }

    public void handleQuit(Player player) {
        RiggedEntity me = plugin.getRigManager().get(player);
        if (me != null) {
            plugin.getRigManager().unload(player);
        }
        if (hiddenPlayers.remove(player.getUniqueId())) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                other.showEntity(plugin, player);
            }
        }
        if (invisibilityApplied.remove(player.getUniqueId())) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (hiddenPlayers.contains(p.getUniqueId()) || invisibilityApplied.contains(p.getUniqueId())) {
                showPlayer(p);
            }
        }
    }
}
