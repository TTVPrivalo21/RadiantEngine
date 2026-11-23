<p align="center">
  <img src="src/main/resources/pack/pack.png" width="320" alt="RadiantAchievements logo">
</p>

# RadiantAchievements

**Custom Texture Achievements & HUD Notification Engine for Minecraft**

RadiantAchievements is an open-source Minecraft plugin that replaces or extends vanilla advancements with fully configurable achievements, custom pixel-art textures, packet-based toasts, compact bossbar cards, center banners, custom sounds, styled chat announcements, persistent progress and a built-in resource pack server.

**Supported versions:** Paper (and forks such as Purpur / Pufferfish) 1.21.x – 26.x  
**Java:** 21+

![Java 21](https://img.shields.io/badge/Java-21-orange)
![Paper](https://img.shields.io/badge/Paper-1.21.x%20--%2026.x-blue)
![License](https://img.shields.io/badge/License-MIT-green)

> **Platform note:** Requires **Paper** or a Paper fork (Purpur, Pufferfish…). It uses the native Adventure / MiniMessage API built into Paper, which plain Spigot does not include.

---

## Features

- **5 Display Styles:** Packet-based native `TOAST`, compact `BOSSBAR_OVERLAY` cards, `CENTER_BANNER` ribbons, `TITLE` and `ACTIONBAR`.
- **Automatic Pixel-Art Generator:** Generates rarity pips, 16×16 medals, bossbar cards, title banners and textured GUI backgrounds automatically if no custom PNG is provided.
- **Built-in HTTP Resource Pack Server:** Serves `resourcepack.zip` with SHA-1 verification out of the box on port `8164` (or via an external URL / Oraxen merge).
- **19 Built-in Triggers:** Instant or cumulative progress (`target: N`) with material/entity/world filters and achievement prerequisites (`requires`).
- **Styled or Compact Chat:** Configurable `DESIGNED` hover banners or clean one-line `COMPACT` messages, public or private per achievement.
- **Layered Custom Sounds:** Vanilla or resource-pack sounds with `volume`, `pitch` and `delay-ticks`.
- **Flexible Rewards:** Console/player commands, Vault money, XP, custom items (`item-model`, `custom-model-data`, Oraxen), messages, potion effects and safe fireworks.
- **Textured `/ach` GUI:** 54-slot menu with custom background glyphs, filter tabs (`All` / `Unlocked` / `Pending`), progress bars and state icons.
- **Async Storage:** Supports `YAML`, `SQLITE` and `MYSQL` (HikariCP) with asynchronous loading and saving.
- **Integrations:** PlaceholderAPI, Vault, WorldGuard, MythicMobs, Citizens and Oraxen.
- **Developer API:** Full Java API (`AchievementAPI`) and cancellable events.

## Installation

1. Build the plugin with `mvn clean package` (or `bash build-local.sh` if Maven is not installed) → `target/RadiantAchievements-1.0.0.jar`.
2. Place the JAR inside your server's `plugins/` folder.
3. Start the server to generate `config.yml`, `designs.yml`, `achievements.yml`, `assets/` and `resourcepack.zip`.
4. Players automatically receive the generated resource pack on join via the **built-in HTTP server** (default port `8164`).
5. Edit your configurations and run `/ach reload` (or `/ach pack` after editing glyphs, icons or sounds to rebuild and resend the pack).

> On remote hosts, open TCP port `resource-pack.http.port` (`8164`) in your firewall/panel. Behind a proxy (Velocity / BungeeCord), set your public domain in `resource-pack.http.public-address`.

## Designs & Custom Glyphs (`designs.yml`)

Glyphs are registered into `minecraft:default` (configurable via `resource-pack.font`), allowing custom sprites to render in chat, titles, bossbars, inventory titles and PlaceholderAPI.

```yaml
glyphs:
  card_rare: { char: "\uE021", texture: "font/card_rare.png", height: 16, ascent: 10 }
