package dev.privalo.radiantengine.blueprint;

import dev.privalo.radiantengine.blueprint.animation.BlueprintAnimation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fully parsed model: bone tree, animations, textures and hitboxes.
 */
public final class RigBlueprint {

    private final String id;
    private final Map<String, BoneBlueprint> bones = new LinkedHashMap<>();
    private final List<BoneBlueprint> roots = new ArrayList<>();
    private final Map<String, BlueprintAnimation> animations = new LinkedHashMap<>();
    private final List<TextureData> textures = new ArrayList<>();
    private final Map<String, String> variables = new LinkedHashMap<>();
    private Hitbox mainHitbox;
    private float maxHorizontalExtent = 1f;
    private float maxVerticalExtent = 2f;

    private int iconCustomModelData = -1;
    private String iconModelPath;

    /** CustomModelData of the generated whole-model icon (-1 when not generated). */
    public int getIconCustomModelData() {
        return iconCustomModelData;
    }

    public String getIconModelPath() {
        return iconModelPath;
    }

    public void setIcon(int customModelData, String modelPath) {
        this.iconCustomModelData = customModelData;
        this.iconModelPath = modelPath;
    }

    public RigBlueprint(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /** Flat map, parents always inserted before their children. */
    public Map<String, BoneBlueprint> getBones() {
        return bones;
    }

    public List<BoneBlueprint> getRoots() {
        return roots;
    }

    public Map<String, BlueprintAnimation> getAnimations() {
        return animations;
    }

    public List<TextureData> getTextures() {
        return textures;
    }

    /** animation_variable_placeholders: variable name -> Molang expression. */
    public Map<String, String> getVariables() {
        return variables;
    }

    public Hitbox getMainHitbox() {
        return mainHitbox;
    }

    public void setMainHitbox(Hitbox mainHitbox) {
        this.mainHitbox = mainHitbox;
    }

    public float getMaxHorizontalExtent() {
        return maxHorizontalExtent;
    }

    public float getMaxVerticalExtent() {
        return maxVerticalExtent;
    }

    public void setExtents(float horizontal, float vertical) {
        this.maxHorizontalExtent = horizontal;
        this.maxVerticalExtent = vertical;
    }

    public List<BoneBlueprint> getRenderableBones() {
        List<BoneBlueprint> list = new ArrayList<>();
        for (BoneBlueprint b : bones.values()) {
            if (b.isRenderable()) {
                list.add(b);
            }
        }
        return Collections.unmodifiableList(list);
    }

    public BoneBlueprint getFirstRenderableBone() {
        for (BoneBlueprint b : bones.values()) {
            if (b.isRenderable()) {
                return b;
            }
        }
        return null;
    }
}
