package dev.privalo.radiantengine.model;

import dev.privalo.radiantengine.RadiantEngine;
import dev.privalo.radiantengine.animation.molang.Molang;
import dev.privalo.radiantengine.animation.molang.MolangContext;
import dev.privalo.radiantengine.animation.runtime.AnimationHandler;
import dev.privalo.radiantengine.animation.runtime.AnimationProperty;
import dev.privalo.radiantengine.animation.runtime.RigState;
import dev.privalo.radiantengine.animation.runtime.StateController;
import dev.privalo.radiantengine.blueprint.BoneBlueprint;
import dev.privalo.radiantengine.blueprint.RigBlueprint;
import dev.privalo.radiantengine.blueprint.animation.EffectKeyframe;
import dev.privalo.radiantengine.blueprint.animation.LoopMode;
import dev.privalo.radiantengine.model.bone.BoneBehavior;
import dev.privalo.radiantengine.model.bone.ItemSlotBehavior;
import dev.privalo.radiantengine.model.bone.RigBone;
import dev.privalo.radiantengine.model.bone.NametagBehavior;
import dev.privalo.radiantengine.model.bone.RenderBehavior;
import dev.privalo.radiantengine.model.bone.SeatBehavior;
import dev.privalo.radiantengine.model.bone.SubHitboxBehavior;
import dev.privalo.radiantengine.util.EntityCompat;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One Blockbench model instance attached to a {@link RiggedEntity}.
 */
public final class ActiveRig implements AnimationHandler.EffectSink {

    private final RadiantEngine plugin;
    private final RiggedEntity owner;
    private final RigBlueprint blueprint;
    private final Map<String, RigBone> bones = new LinkedHashMap<>();
    private final List<RigBone> roots = new ArrayList<>();
    private final AnimationHandler animationHandler;
    private final StateController stateController;
    private final MolangContext molang = new MolangContext();
    private final Set<UUID> parts = new HashSet<>();
    private final Set<UUID> hiddenViewers = new HashSet<>();
    private final Set<UUID> headlessViewers = new HashSet<>();
    private final Map<String, String> nametags = new HashMap<>();
    private final Map<String, ItemStack> slotItems = new HashMap<>();

    private final Vector3f rootPosition = new Vector3f();
    private final Quaternionf rootRotation = new Quaternionf();
    private final Vector3f rootScale = new Vector3f(1, 1, 1);

    private float scale = 1f;
    private Color tint = Color.WHITE;
    private int damageTintTicks;
    private boolean hidden;
    private boolean lockPitch;
    private boolean lockYaw;
    private boolean dying;
    private boolean spawned;
    private boolean useBaseName = true;
    private float rootTilt;
    private float rootRoll;
    private org.bukkit.entity.Display.Billboard billboard = org.bukkit.entity.Display.Billboard.FIXED;
    private org.bukkit.entity.Display.Brightness brightness;
    private boolean glowing;
    private Color glowColor;
    private int lifeTicks;

    public ActiveRig(RadiantEngine plugin, RiggedEntity owner, RigBlueprint blueprint) {
        this.plugin = plugin;
        this.owner = owner;
        this.blueprint = blueprint;
        this.animationHandler = new AnimationHandler(this);
        this.stateController = new StateController(this);
        for (Map.Entry<String, String> v : blueprint.getVariables().entrySet()) {
            molang.setVariable(v.getKey(), Molang.compileSafe(v.getValue(), 0, null).eval(molang));
        }
        for (BoneBlueprint root : blueprint.getRoots()) {
            build(root, null);
        }
    }

    private void build(BoneBlueprint bp, RigBone parent) {
        RigBone bone = new RigBone(this, bp, parent);
        bones.put(bp.getName(), bone);
        if (parent == null) {
            roots.add(bone);
        }
        if (bp.isRenderable()) {
            bone.addBehavior(new RenderBehavior(bone));
        }
        if (bp.getItemSlot() != BoneBlueprint.ItemSlot.NONE) {
            bone.addBehavior(new ItemSlotBehavior(bone));
        }
        if (bp.isNametag()) {
            bone.addBehavior(new NametagBehavior(bone));
        }
        if (bp.isSubHitbox()) {
            bone.addBehavior(new SubHitboxBehavior(bone));
        }
        if (bp.isSeat()) {
            bone.addBehavior(new SeatBehavior(bone));
        }
        for (BoneBlueprint child : bp.getChildren()) {
            build(child, bone);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    void spawn(Location origin, float bodyYaw, float headYaw, float pitch) {
        computeTransforms(bodyYaw, headYaw, pitch);
        for (RigBone bone : bones.values()) {
            for (BoneBehavior b : bone.getBehaviors()) {
                b.spawn(origin);
            }
        }
        spawned = true;
        applyViewerRules();
        if (stateController.hasAnimation(RigState.SPAWN)) {
            stateController.play(RigState.SPAWN, true);
        }
    }

    void tick(Location origin, boolean originMoved, float bodyYaw, float headYaw, float pitch, MovementState movement) {
        if (!spawned) {
            return;
        }
        lifeTicks++;
        updateMolang(movement, bodyYaw, headYaw, pitch);
        stateController.tick(movement);
        animationHandler.tick(this);
        computeTransforms(bodyYaw, headYaw, pitch);
        for (RigBone bone : bones.values()) {
            for (BoneBehavior b : bone.getBehaviors()) {
                b.update(origin, originMoved);
            }
        }
        if (damageTintTicks > 0 && --damageTintTicks == 0) {
            refreshItems();
        }
    }

    private void updateMolang(MovementState movement, float bodyYaw, float headYaw, float pitch) {
        molang.lifeTime = lifeTicks / 20.0;
        molang.groundSpeed = movement.horizontalSpeed() * 20.0;
        molang.verticalSpeed = movement.verticalSpeed() * 20.0;
        molang.onGround = movement.onGround();
        molang.headPitch = pitch;
        molang.headYaw = dev.privalo.radiantengine.util.MathUtil.wrapDegrees(headYaw - bodyYaw);
        molang.bodyYaw = bodyYaw;
        Entity base = owner.getBase();
        if (base instanceof LivingEntity living) {
            molang.health = living.getHealth();
            molang.maxHealth = maxHealth(living);
        }
        if (base instanceof Player p) {
            molang.sneaking = p.isSneaking();
            molang.sprinting = p.isSprinting();
        }
    }

    private static org.bukkit.attribute.Attribute maxHealthAttribute;
    private static boolean maxHealthResolved;

    private static double maxHealth(LivingEntity living) {
        try {
            if (!maxHealthResolved) {
                maxHealthResolved = true;
                for (org.bukkit.attribute.Attribute attr : org.bukkit.Registry.ATTRIBUTE) {
                    String key = attr.getKey().getKey();
                    if (key.equals("max_health") || key.equals("generic.max_health")) {
                        maxHealthAttribute = attr;
                        break;
                    }
                }
            }
            org.bukkit.attribute.AttributeInstance a = maxHealthAttribute == null ? null : living.getAttribute(maxHealthAttribute);
            return a == null ? living.getHealth() : a.getValue();
        } catch (Throwable t) {
            return living.getHealth();
        }
    }

    private void computeTransforms(float bodyYaw, float headYaw, float pitch) {
        rootPosition.zero();
        if (lockYaw) {
            headYaw = bodyYaw;
        }
        dev.privalo.radiantengine.util.MathUtil.yaw(bodyYaw, rootRotation);
        if (Math.abs(rootTilt) > 1.0E-3f) {
            rootRotation.rotateX(dev.privalo.radiantengine.util.MathUtil.rad(rootTilt));
        }
        if (Math.abs(rootRoll) > 1.0E-3f) {
            rootRotation.rotateZ(dev.privalo.radiantengine.util.MathUtil.rad(rootRoll));
        }
        rootScale.set(scale, scale, scale);
        float maxYaw = plugin.getSettings().headMaxYaw;
        float relYaw = dev.privalo.radiantengine.util.MathUtil.clamp(
                dev.privalo.radiantengine.util.MathUtil.wrapDegrees(headYaw - bodyYaw), -maxYaw, maxYaw);
        float p = lockPitch ? 0f : dev.privalo.radiantengine.util.MathUtil.clamp(pitch, -90f, 90f);
        boolean applyHead = !dying;
        for (RigBone root : roots) {
            root.computeTransform(rootPosition, rootRotation, rootScale, animationHandler, molang, relYaw, p, applyHead);
        }
    }

    void destroy() {
        for (RigBone bone : bones.values()) {
            for (BoneBehavior b : bone.getBehaviors()) {
                try {
                    b.destroy();
                } catch (Throwable t) {
                    plugin.getLogger().warning("Error destruyendo parte de " + blueprint.getId() + ": " + t);
                }
            }
        }
        for (UUID id : new ArrayList<>(parts)) {
            plugin.getRigManager().unregisterPart(id);
        }
        parts.clear();
        spawned = false;
    }

    // ------------------------------------------------------------------ effects

    @Override
    public void onEffect(AnimationProperty property, EffectKeyframe keyframe) {
        Location origin = owner.getOrigin();
        if (origin == null || origin.getWorld() == null) {
            return;
        }
        for (EffectKeyframe.Entry entry : keyframe.entries()) {
            switch (keyframe.type()) {
                case SOUND -> {
                    String sound = entry.effect();
                    int dot = sound.indexOf(':');
                    String key = dot < 0 ? "minecraft:" + sound : sound;
                    origin.getWorld().playSound(origin, key, 1f, 1f);
                }
                case PARTICLE -> {
                    Particle particle = EntityCompat.particle(entry.effect());
                    if (particle == null || particle.getDataType() != Void.class) {
                        continue;
                    }
                    Location at = origin;
                    if (entry.locator() != null) {
                        RigBone bone = bones.get(entry.locator());
                        if (bone != null) {
                            at = bone.absoluteLocation(origin);
                        }
                    }
                    plugin.getBedrock().spawnParticle(origin.getWorld(), particle, at, 4, 0.05, 0.05, 0.05, 0.0, null);
                }
                case SCRIPT -> plugin.getScriptRunner().run(this, entry.locator(), entry.effect());
            }
        }
    }

    // ------------------------------------------------------------------ parts / visibility

    public void registerPart(Entity entity) {
        parts.add(entity.getUniqueId());
        plugin.getRigManager().registerPart(entity.getUniqueId(), owner);
        for (UUID viewer : hiddenViewers) {
            Player p = Bukkit.getPlayer(viewer);
            if (p != null) {
                p.hideEntity(plugin, entity);
            }
        }
        owner.applyPartRules(entity);
    }

    public void unregisterPart(Entity entity) {
        parts.remove(entity.getUniqueId());
        plugin.getRigManager().unregisterPart(entity.getUniqueId());
    }

    public List<Entity> getPartEntities() {
        List<Entity> list = new ArrayList<>();
        for (RigBone bone : bones.values()) {
            for (BoneBehavior b : bone.getBehaviors()) {
                list.addAll(b.entities());
            }
        }
        return list;
    }

    public void hideFrom(Player player) {
        if (hiddenViewers.add(player.getUniqueId())) {
            for (Entity e : getPartEntities()) {
                player.hideEntity(plugin, e);
            }
        }
    }

    public void showTo(Player player) {
        if (hiddenViewers.remove(player.getUniqueId())) {
            for (Entity e : getPartEntities()) {
                player.showEntity(plugin, e);
            }
        }
    }

    public Set<UUID> getHiddenViewers() {
        return Collections.unmodifiableSet(hiddenViewers);
    }

    /**
     * Sets how one player sees this model: everything, nothing, or everything except the head bones
     * (h_ and their children) and nametags, which is what a disguised player needs for a clean first person view.
     */
    public void setViewMode(Player player, dev.privalo.radiantengine.config.Settings.SelfView mode) {
        UUID id = player.getUniqueId();
        switch (mode) {
            case NONE -> {
                headlessViewers.remove(id);
                hideFrom(player);
            }
            case FULL -> {
                headlessViewers.remove(id);
                hiddenViewers.add(id);
                showTo(player);
            }
            case HEADLESS -> {
                hiddenViewers.add(id);
                showTo(player);
                headlessViewers.add(id);
                applyHeadless(player);
            }
        }
    }

    private void applyHeadless(Player player) {
        for (RigBone bone : bones.values()) {
            if (!isHeadPart(bone)) {
                continue;
            }
            for (BoneBehavior b : bone.getBehaviors()) {
                for (Entity e : b.entities()) {
                    player.hideEntity(plugin, e);
                }
            }
        }
    }

    private static boolean isHeadPart(RigBone bone) {
        if (bone.getBlueprint().isNametag()) {
            return true;
        }
        for (RigBone b = bone; b != null; b = b.getParent()) {
            if (b.getBlueprint().isHead()) {
                return true;
            }
        }
        return false;
    }

    /** Re-applies per-bone viewer rules after part entities were (re)spawned. */
    private void applyViewerRules() {
        for (UUID id : headlessViewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                applyHeadless(p);
            }
        }
    }

    // ------------------------------------------------------------------ items / tint

    public ItemStack createBoneItem(BoneBlueprint bone) {
        Color color = damageTintTicks > 0 ? plugin.getSettings().damageTintColor : tint;
        return plugin.getItemResolver().boneItem(blueprint, bone, color);
    }

    public ItemStack resolveSlotItem(RigBone bone) {
        ItemStack override = slotItems.get(bone.getName());
        if (override != null) {
            return override;
        }
        String link = plugin.getSettings().itemLinks.get(blueprint.getId() + "." + bone.getName());
        if (link != null) {
            ItemStack linked = plugin.getItemResolver().resolve(link);
            if (linked != null) {
                return linked;
            }
        }
        Entity base = owner.getBase();
        if (base instanceof LivingEntity living) {
            EntityEquipment eq = living.getEquipment();
            if (eq == null) {
                return null;
            }
            return switch (bone.getBlueprint().getItemSlot()) {
                case MAIN_HAND -> eq.getItemInMainHand();
                case OFF_HAND -> eq.getItemInOffHand();
                case HEAD -> eq.getHelmet();
                default -> null;
            };
        }
        return null;
    }

    public String resolveNametag(String bone) {
        String text = nametags.get(bone);
        if (text != null) {
            return text;
        }
        if (useBaseName) {
            Entity base = owner.getBase();
            if (base != null && base.getCustomName() != null && !(base instanceof Player)) {
                return base.getCustomName();
            }
        }
        return null;
    }

    /**
     * Visual style applied to every item display of the rig. A VERTICAL/CENTER billboard turns the whole model as
     * one piece towards each viewer, because all bone displays share the same origin.
     *
     * @param billboard  billboard mode (FIXED = normal)
     * @param brightness fixed light level, or null for world lighting
     * @param glowing    glow outline
     * @param glowColor  outline color (null = team/white)
     */
    public void setDisplayStyle(org.bukkit.entity.Display.Billboard billboard, org.bukkit.entity.Display.Brightness brightness,
                                boolean glowing, Color glowColor) {
        this.billboard = billboard == null ? org.bukkit.entity.Display.Billboard.FIXED : billboard;
        this.brightness = brightness;
        this.glowing = glowing;
        this.glowColor = glowColor;
        for (RigBone bone : bones.values()) {
            for (BoneBehavior b : bone.getBehaviors()) {
                if (b instanceof RenderBehavior render && render.getDisplay() != null && render.getDisplay().isValid()) {
                    styleDisplay(render.getDisplay());
                }
            }
        }
    }

    /** Applies the rig display style to one item display (called on spawn by render behaviors). */
    public void styleDisplay(org.bukkit.entity.ItemDisplay display) {
        display.setBillboard(billboard);
        display.setBrightness(brightness);
        display.setGlowing(glowing);
        display.setGlowColorOverride(glowing ? glowColor : null);
    }

    public org.bukkit.entity.Display.Billboard getBillboard() {
        return billboard;
    }

    public void refreshItems() {
        for (RigBone bone : bones.values()) {
            for (BoneBehavior b : bone.getBehaviors()) {
                b.refresh();
            }
        }
    }

    public void setTint(Color color) {
        this.tint = color == null ? Color.WHITE : color;
        refreshItems();
    }

    public Color getTint() {
        return tint;
    }

    public void flashDamageTint() {
        if (!plugin.getSettings().damageTint) {
            return;
        }
        boolean wasTinted = damageTintTicks > 0;
        damageTintTicks = plugin.getSettings().damageTintTicks;
        if (!wasTinted) {
            refreshItems();
        }
    }

    public void setBoneItem(String bone, ItemStack item) {
        if (item == null) {
            slotItems.remove(bone);
        } else {
            slotItems.put(bone, item.clone());
        }
        RigBone b = bones.get(bone);
        if (b != null) {
            for (BoneBehavior behavior : b.getBehaviors()) {
                behavior.refresh();
            }
        }
    }

    public void setNametag(String bone, String text) {
        if (text == null) {
            nametags.remove(bone);
        } else {
            nametags.put(bone, text);
        }
    }

    public void setBoneVisible(String bone, boolean visible) {
        RigBone b = bones.get(bone);
        if (b != null) {
            b.setVisible(visible);
        }
    }

    // ------------------------------------------------------------------ animation shortcuts

    public boolean playAnimation(String name, double lerpIn, double lerpOut, double speed, boolean force) {
        return animationHandler.playAnimation(name, lerpIn, lerpOut, speed, force);
    }

    public void stopAnimation(String name) {
        animationHandler.stopAnimation(name);
    }

    /** Starts the death animation, holding the last frame. Returns its length in ticks, or -1 if none. */
    int startDeath() {
        if (!stateController.hasAnimation(RigState.DEATH)) {
            return -1;
        }
        stateController.clearLoopState();
        dying = true;
        var s = stateController.settings(RigState.DEATH);
        var animation = blueprint.getAnimations().get(s.animation());
        AnimationProperty property = new AnimationProperty(animation, s.lerpIn(), s.lerpOut(), s.speed());
        property.setForceLoopMode(LoopMode.HOLD);
        animationHandler.playAnimation(property, true);
        double speed = Math.max(0.05, Math.abs(s.speed()));
        return (int) Math.ceil((animation.getLength() / speed + s.lerpIn() / speed) * 20.0) + 2;
    }

    // ------------------------------------------------------------------ getters

    public RadiantEngine getPlugin() {
        return plugin;
    }

    public RiggedEntity getOwner() {
        return owner;
    }

    public RigBlueprint getBlueprint() {
        return blueprint;
    }

    public String getId() {
        return blueprint.getId();
    }

    public Map<String, RigBone> getBones() {
        return Collections.unmodifiableMap(bones);
    }

    public RigBone getBone(String name) {
        return bones.get(name);
    }

    public AnimationHandler getAnimationHandler() {
        return animationHandler;
    }

    public StateController getStateController() {
        return stateController;
    }

    public MolangContext getMolang() {
        return molang;
    }

    public List<SeatBehavior> getSeats() {
        List<SeatBehavior> list = new ArrayList<>();
        for (RigBone bone : bones.values()) {
            SeatBehavior s = bone.getBehavior(SeatBehavior.class);
            if (s != null) {
                list.add(s);
            }
        }
        return list;
    }

    /** Extra pitch (degrees) applied to the whole rig after the body yaw (used by cosmetics while gliding). */
    public void setRootTilt(float degrees) {
        this.rootTilt = degrees;
    }

    /** Roll (degrees) around the forward axis applied after yaw and tilt; positive lifts the left side (banking right). */
    public void setRootRoll(float degrees) {
        this.rootRoll = degrees;
    }

    public float getRootRoll() {
        return rootRoll;
    }

    public float getRootTilt() {
        return rootTilt;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = Math.max(0.01f, scale);
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        if (this.hidden != hidden) {
            this.hidden = hidden;
            refreshItems();
        }
    }

    public boolean isLockPitch() {
        return lockPitch;
    }

    public void setLockPitch(boolean lockPitch) {
        this.lockPitch = lockPitch;
    }

    public boolean isLockYaw() {
        return lockYaw;
    }

    public void setLockYaw(boolean lockYaw) {
        this.lockYaw = lockYaw;
    }

    public boolean isDying() {
        return dying;
    }

    public boolean isUseBaseName() {
        return useBaseName;
    }

    public void setUseBaseName(boolean useBaseName) {
        this.useBaseName = useBaseName;
    }

    public boolean hasNametagBone() {
        for (RigBone b : bones.values()) {
            if (b.getBlueprint().isNametag()) {
                return true;
            }
        }
        return false;
    }
}
