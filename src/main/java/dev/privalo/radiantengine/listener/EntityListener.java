package dev.privalo.radiantengine.listener;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.animation.runtime.RigState;
import dev.privalo.radiantengine.model.ActiveRig;
import dev.privalo.radiantengine.model.RiggedEntity;
import dev.privalo.radiantengine.model.bone.SeatBehavior;
import org.bukkit.Bukkit;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.List;

/**
 * Redirects hitbox damage / clicks to the base entity, drives attack and hurt feedback, death animations and
 * chunk load / unload persistence.
 */
public final class EntityListener implements Listener {

    private final RadiantEngine plugin;

    public EntityListener(RadiantEngine plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ hitbox redirection

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPartDamaged(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        RiggedEntity me = plugin.getRigManager().getByPart(victim);
        if (me == null) {
            return;
        }
        event.setCancelled(true);
        Entity base = me.getBase();
        if (me.isDying() || !base.isValid()) {
            return;
        }
        Entity damager = event.getDamager();
        Entity attacker = damager;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            attacker = shooter;
        }
        if (attacker.equals(base)) {
            return;
        }
        if (attacker instanceof Player clicker && plugin.getNpcManager().handleInteract(base, clicker, false)) {
            return;
        }
        if (base instanceof Interaction) {
            return;
        }
        if (damager instanceof LivingEntity living && damager == attacker && base instanceof Damageable) {
            living.attack(base);
        } else if (base instanceof Damageable damageable) {
            damageable.damage(event.getDamage(), damager);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPartDamagedGeneric(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent) && plugin.getRigManager().getByPart(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPartInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        RiggedEntity me = plugin.getRigManager().getByPart(clicked);
        if (me == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Entity base = me.getBase();
        if (base.equals(player) || me.isDying()) {
            return;
        }
        if (plugin.getSettings().rightClickMount && !player.isSneaking()) {
            for (ActiveRig model : me.models()) {
                boolean hasFree = false;
                for (SeatBehavior seat : model.getSeats()) {
                    if (!seat.isOccupied()) {
                        hasFree = true;
                        break;
                    }
                }
                if (hasFree && plugin.getMountManager().mount(player, model, null, true)) {
                    return;
                }
            }
        }
        PlayerInteractEntityEvent redirected = new PlayerInteractEntityEvent(player, base, event.getHand());
        Bukkit.getPluginManager().callEvent(redirected);
    }

    /** Right clicks on a hologram (anchor or redirected from a part) run its command. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHologramInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (plugin.getHologramManager().handleInteract(event.getRightClicked(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** Holograms are invulnerable. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onHologramDamage(EntityDamageEvent event) {
        if (plugin.getHologramManager().isHologramBase(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** Clicks on an NPC rig (anchor base or redirected from a part) are forwarded to the NPC plugin. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onNpcRigInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (plugin.getNpcManager().handleInteract(event.getRightClicked(), event.getPlayer(), true)) {
            event.setCancelled(true);
        }
    }

    /** Left clicks on an NPC anchor base (packet NPC rigs). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onNpcRigAttack(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!plugin.getNpcManager().isNpcBase(victim) || plugin.getRigManager().getByPart(victim) != null) {
            return;
        }
        if (event.getDamager() instanceof Player player && plugin.getNpcManager().handleInteract(victim, player, false)) {
            event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPartInteractAt(PlayerInteractAtEntityEvent event) {
        if (plugin.getRigManager().getByPart(event.getRightClicked()) != null) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ feedback

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onModeledHurt(EntityDamageEvent event) {
        RiggedEntity me = plugin.getRigManager().get(event.getEntity());
        if (me == null) {
            return;
        }
        for (ActiveRig model : me.models()) {
            model.flashDamageTint();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onModeledAttack(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        RiggedEntity me = plugin.getRigManager().get(damager);
        if (me == null || me.isDying()) {
            return;
        }
        for (ActiveRig model : me.models()) {
            if (model.getStateController().hasAnimation(RigState.ATTACK)) {
                model.getStateController().play(RigState.ATTACK, true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        plugin.getRigManager().handleDeath(event.getEntity());
    }

    // ------------------------------------------------------------------ persistence

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        List<Entity> list = new ArrayList<>(event.getEntities());
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Entity e : list) {
                if (e.isValid()) {
                    plugin.getRigManager().restore(e);
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity e : event.getEntities()) {
            if (plugin.getRigManager().get(e) != null && !(e instanceof Player)) {
                plugin.getRigManager().unload(e);
            }
        }
    }
}
