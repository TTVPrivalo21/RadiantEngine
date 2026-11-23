# RadiantEngine

**3D Model Engine for Minecraft**

RadiantEngine is a Minecraft plugin that allows you to load, animate and display Blockbench models using Minecraft's Display Entities.

Models are loaded directly from `.bbmodel` files, preserving their bone hierarchy, animations, textures and transformations. No NMS, ProtocolLib or ArmorStands required.

**Supported versions:** Paper / Spigot 1.21.x – 26.x  
**Java:** 21+

![Java 21](https://img.shields.io/badge/Java-21-orange)
![Paper / Spigot](https://img.shields.io/badge/Paper%20%7C%20Spigot-1.21.x%20--%2026.x-blue)
![License](https://img.shields.io/badge/License-MIT-green)

---

## Features

- **Blockbench support:** Load `.bbmodel` files with bone hierarchies, pivots, rotations, face UVs and embedded textures.
- **Animation system:** Supports linear, catmull-rom, bezier and step interpolation, with `loop`, `once` and `hold` playback modes.
- **Display Entity rendering:** Models are rendered using `ItemDisplay` entities and native transformations.
- **Bone transformations:** Position, rotation and scale are inherited through the model hierarchy using quaternions.
- **Animation layers:** Play multiple animations with fade-in, fade-out and override support.
- **Automatic states:** `idle`, `walk`, `jump`, `fly`, `attack`, `death` and `spawn`.
- **Hitboxes:** Interaction entities for damage handling and player interaction.
- **Player disguises:** Apply models to players, including movement, head tracking, jumping and attacks.
- **Mounts:** Driver and passenger seats with WASD movement and jumping.
- **3D holograms:** Animated, rotating and player-facing models with text and commands.
- **Nametags:** Custom labels using TextDisplay entities.
- **Resource pack generation:** Generates model assets, textures, item definitions and pack files automatically.
- **Integrations:** Oraxen, MythicMobs, Citizens, FancyNpcs, ZNPCsPlus, WorldGuard and PlaceholderAPI.
- **MiniMessage:** Configurable messages and formatting.

## Installation

1. Download the latest `RadiantEngine-x.y.z.jar`.
2. Place the JAR inside your server's `plugins` folder.
3. Start the server to generate the configuration files.
4. Place your Blockbench `.bbmodel` files inside:

   `plugins/RadiantEngine/blueprints/`

   Subfolders are supported.

5. Run `/radiant reload`.
6. Distribute the generated resource pack located at:

   `plugins/RadiantEngine/resource_pack.zip`

   If Oraxen is installed, you can use its generated resource pack instead.

## Bone Tags

RadiantEngine uses bone names and prefixes to identify special model components.

| Bone name / Prefix | Description |
|---|---|
| `hitbox` | Main hitbox. Its pivot determines the eye height. |
| `h_` | Head bone. Follows the entity's look direction. |
| `g_` | Ghost bone. Not rendered, but its children are animated. |
| `mount` | Driver seat with WASD and jump controls. |
| `p_` | Passenger seat. |
| `tag_` | Nametag attachment point. |
| `ir_` | Right-hand item attachment. |
| `il_` | Left-hand item attachment. |
| `ih_` | Head item attachment. |
| `item_` | Custom item attachment through the API, item-links or Oraxen. |
| `b_`, `ob_`, `hitbox_`, `subhitbox_` | Additional hitboxes attached to bones. |

## Commands

Main command: `/radiant`

Aliases: `/re`, `/rengine`

| Command | Description | Permission |
|---|---|---|
| `/radiant reload [models\|config\|pack]` | Reload models, configuration or resource pack. | `radiantengine.command.reload` |
| `/radiant list [page]` | List loaded models. | `radiantengine.command.list` |
| `/radiant info <model>` | Display model information. | `radiantengine.command.list` |
| `/radiant spawn <model> [entity]` | Spawn an entity with a model. | `radiantengine.command.spawn` |
| `/radiant summon <model> [x y z [yaw]]` | Spawn a static model. | `radiantengine.command.summon` |
| `/radiant apply <model> [hideBase]` | Apply a model to a targeted entity or selected Citizens NPC. | `radiantengine.command.apply` |
| `/radiant remove [radius]` | Remove nearby models. | `radiantengine.command.remove` |
| `/radiant kill [model] [radius]` | Remove active rigs. | `radiantengine.command.kill` |
| `/radiant killall [radius]` | Remove all rigs and orphaned parts. | `radiantengine.command.kill` |
| `/radiant disguise <model> [player]` | Apply a player disguise. | `radiantengine.command.disguise` |
| `/radiant undisguise [player]` | Remove a player disguise. | `radiantengine.command.disguise` |
| `/radiant selfview <full\|headless\|none>` | Configure your own disguise visibility. | `radiantengine.command.disguise` |
| `/radiant playanim <model> <anim> [lerpIn] [lerpOut] [vel] [force]` | Play a model animation. | `radiantengine.command.playanim` |
| `/radiant stopanim` | Stop an animation. | `radiantengine.command.playanim` |
| `/radiant tint <model> <RRGGBB\|reset>` | Change the model tint. | `radiantengine.command.tint` |
| `/radiant mount [seat]` | Mount a model. | `radiantengine.command.mount` |
| `/radiant dismount` | Dismount a model. | `radiantengine.command.mount` |
| `/radiant npc <id> <model\|remove>` | Assign or remove an NPC model. | `radiantengine.command.npc` |
| `/radiant npc list` | List registered NPC models. | `radiantengine.command.npc` |
| `/radiant gui` | Open the model selection menu. | `radiantengine.command.gui` |

Additional permissions:

- `radiantengine.*` — All permissions.
- `radiantengine.admin` — Administrator access.
- `radiantengine.command.disguise.others` — Manage other players' disguises.
- `radiantengine.bypass.region` — Bypass region restrictions.

## 3D Holograms

RadiantEngine includes a hologram system that uses Blockbench models. Holograms are persistent and saved in `holograms.yml`.

### Hologram modes

| Mode | Description |
|---|---|
| `spin` | Rotates around the Y axis and floats using a sine animation. |
| `billboard` | Always faces each player individually. |
| `static` | Keeps its original orientation. |

Additional features:

- Cyan tint and maximum brightness (`15/15`).
- Optional glowing outline.
- Particle ring around the base.
- Text above or below the model.
- MiniMessage and PlaceholderAPI support.
- Right-click command execution.
- No gravity, AI or damage.

### Examples

```text
/radiant holo create portal_spawn portal spin 1.5
/radiant holo text portal_spawn <gradient:#00E5FF:#3B82F6>Travel to Lobby</gradient>
/radiant holo command portal_spawn warp lobby
/radiant holo command tienda console:shop open {player}
/radiant holo movehere portal_spawn
/radiant holo mode portal_spawn billboard
/radiant holo remove portal_spawn
```

### Hologram commands

| Command | Permission |
|---|---|
| `/radiant holo create <id> <model> [spin\|billboard\|static] [scale]` | `radiantengine.command.holo` |
| `/radiant holo remove <id>` | `radiantengine.command.holo` |
| `/radiant holo movehere <id>` | `radiantengine.command.holo` |
| `/radiant holo list` | `radiantengine.command.holo` |
| `/radiant holo text <id> [above\|below] <text\|none>` | `radiantengine.command.holo` |
| `/radiant holo command <id> <command\|none>` | `radiantengine.command.holo` |
| `/radiant holo mode <id> <spin\|billboard\|static>` | `radiantengine.command.holo` |

### Hologram API

```java
HologramRig holo = RadiantAPI.spawnHologram(
    "portal_spawn",
    location,
    "portal",
    HologramMode.SPIN,
    1.5f
);

RadiantAPI.setHologramText(
    "portal_spawn",
    "<aqua>Travel to Lobby"
);

RadiantAPI.setHologramCommand(
    "portal_spawn",
    "warp lobby"
);

RadiantAPI.removeHologram("portal_spawn");
```

## Integrations

### Citizens, FancyNpcs and ZNPCsPlus

Models can be assigned directly to NPCs from supported NPC plugins.

```text
/radiant npc 12 knight
/radiant npc fancynpcs:guide wizard
/radiant npc znpcsplus:shop golem
/radiant npc 12 remove
```

NPC assignments are saved in `npcs.yml` and restored after server restarts and chunk reloads.

RadiantEngine automatically handles the `idle` and `walk` animations when supported by the NPC's movement state.

Interactions with the model's hitbox are forwarded to the underlying NPC.

### WorldGuard

RadiantEngine adds two WorldGuard flags:

| Flag | Default | Description |
|---|---|---|
| `radiant-allow-spawn` | `allow` | Controls model spawning. |
| `radiant-allow-mounts` | `allow` | Controls model mounting. |

Example:

```text
/rg flag spawn radiant-allow-spawn deny
/rg flag spawn radiant-allow-mounts deny
```

### PlaceholderAPI

Available placeholders:

| Placeholder | Description |
|---|---|
| `%radiant_active_models%` | Number of active model entities. |
| `%radiant_loaded_blueprints%` | Number of loaded blueprints. |
| `%radiant_player_disguise%` | Current player disguise. |
| `%radiant_player_mount%` | Current mounted model. |
| `%radiant_npc_models%` | Number of NPCs with assigned models. |

### Oraxen

When Oraxen is installed, RadiantEngine generates:

- `plugins/Oraxen/items/radiantengine_models.yml`
- Model assets inside `plugins/Oraxen/pack/assets/<namespace>/`

Oraxen items can be linked to `item_` bones through `item-links` or the API.

### MythicMobs

RadiantEngine provides MythicMobs skills for model handling.

```yaml
Skills:
  - model{m=dragon} @self ~onSpawn
  - state{m=dragon;s=roar;li=0.1;lo=0.2} @self ~onAttack
  - tint{m=dragon;c=FF0000} @self
  - mountmodel{m=dragon;d=true} @trigger ~onInteract
  - disguise{m=dragon} @trigger
```

Available targeters:

- `@ModelPassengers`
- `@ModelDriver`
- `@MountedModel`

## Developer API

RadiantEngine provides a Java API for other plugins.

Add RadiantEngine as a `provided` dependency and declare it in your `plugin.yml` using `depend` or `softdepend`.

### Examples

```java
import dev.privalo.radiantengine.api.RadiantAPI;
import dev.privalo.radiantengine.model.ActiveRig;

// Spawn a mob with a model
ActiveRig dragon = RadiantAPI.spawnRig(
    location,
    "dragon",
    EntityType.HUSK
);

RadiantAPI.playAnimation(dragon, "roar");

// Spawn a static model
RadiantAPI.spawnRig(location, "statue");

// Apply a model to an existing entity
RadiantAPI.applyRig(zombie, "knight");

// Change model tint
RadiantAPI.setTint(
    zombie,
    "knight",
    Color.fromRGB(0xFF5555)
);

// Apply a player disguise
RadiantAPI.applyDisguise(player, "werewolf");

// Remove disguise
RadiantAPI.removeDisguise(player);

// Get an active rig
ActiveRig rig = RadiantAPI.getActiveRig(entity, "knight");

// Remove model
RadiantAPI.removeRig(entity);
```

### Events

Available API events:

- `BlueprintLoadEvent`
- `RigAttachEvent`
- `RigAnimationStartEvent` (cancellable)
- `RigAnimationEndEvent`

## Building

Requirements:

- Java 21
- Maven

Clone the repository and run:

```bash
mvn package
```

The compiled JAR will be generated inside:

```text
target/RadiantEngine-<version>.jar
```

## License

RadiantEngine is licensed under the [MIT License](LICENSE).

Copyright © 2026 Privalo21.