# SchematicImporter

A lightweight, **server-side only** schematic importer for **NeoForge 1.21.1**. Paste `.schem` and `.nbt` schematics into your world without freezing the server — even on heavy modpacks like AllTheMods.

Built as a minimal alternative to WorldEdit for servers where WorldEdit's memory footprint causes crashes.

---

## Features

### Schematic Support
- **Sponge Schematic** (`.schem`) — v2 and v3 formats
- **Vanilla Structure** (`.nbt`) — structure block format
- Block entities preserved (chests, signs, spawners, mod machines)
- Entities imported (armor stands, item frames, mobs)
- Unknown/outdated blocks gracefully replaced with air + warning

### Async Paste Engine
- **Tick-spread placement** — blocks placed gradually across server ticks, zero lag spikes
- **Configurable speed** — `blocks_per_tick` in config (default 100, range 1–2000)
- **Live config** — change paste speed while a paste is running, no restart needed
- **Progress bar** — action bar shows `████░░░░ 45% | 450/1.0K | ~5s`
- **Cancel anytime** — `/schem cancel` stops immediately at the next tick
- Paste continues even if the player disconnects

### Rotation
- Rotate schematics **90°, 180°, or 270°** before pasting
- Block states rotated correctly (stairs, doors, pistons face the right way)
- Entity positions transformed to match

### Preview System
- **Auto-preview on load** — bounding box particles appear immediately
- **Color-coded edges** — red/orange floor, green pillars, blue ceiling
- **Corner markers** — bright yellow glow at all 8 vertices
- **Front face indicator** — cyan X marks the north face for rotation reference
- **Floor fill** — sparse green dots show the covered ground area
- **Adaptive density** — denser particles for small structures, sparser for large

### Placement Controls
- **Nudge** — adjust position north/south/east/west/up/down before pasting
- **Confirm** — paste at the previewed position
- **`--ignore-air`** — skip air blocks to preserve existing terrain
- **`--use-offset`** — apply the original Sponge offset for perspective-accurate placement

### Multi-Level Undo
- `/schem undo` restores the world to its state before the last paste
- Up to **5 undo levels** per player (configurable, max 20)
- Undo is also async — no lag spike when restoring

### Server-Friendly
- **Server-side only** — no client mod required for any player
- **OP-only** — all commands require permission level 2+
- **Lightweight** — minimal memory footprint compared to WorldEdit
- **AllTheMods tested** — built specifically for heavy modpack servers

---

## Commands

| Command | Description |
|---------|-------------|
| `/schem list` | List all schematics in the schematics folder |
| `/schem load <name>` | Load a schematic (auto-starts preview) |
| `/schem paste [--ignore-air] [--use-offset]` | Paste at current/preview position |
| `/schem cancel` | Cancel active paste or preview |
| `/schem rotate <90\|180\|270>` | Rotate the loaded schematic |
| `/schem preview` | Show/restart bounding box preview |
| `/schem nudge <direction> [amount]` | Adjust preview position |
| `/schem confirm [--ignore-air] [--use-offset]` | Paste at the previewed position |
| `/schem undo` | Undo the last paste operation |

**Directions for nudge:** `north`, `south`, `east`, `west`, `up`, `down`

---

## Quick Start

1. Drop the mod JAR into your server's `mods/` folder
2. Start the server — a `schematics/` folder is created automatically
3. Place your `.schem` or `.nbt` files in `schematics/`
4. In-game (as OP):

```
/schem list                    — see available schematics
/schem load mybuilding.schem   — load + auto preview
/schem rotate 90               — rotate if needed
/schem nudge north 5           — adjust position
/schem confirm                 — paste!
```

Or for a quick paste without preview:
```
/schem load mybuilding.schem
/schem paste
```

---

## Configuration

Config file: `config/schematicimporter-server.toml`

```toml
# Blocks placed per server tick during async paste
# Higher = faster paste, but more TPS impact
# Default: 100, Range: 1-2000
blocks_per_tick = 100

# Path to schematics folder (relative to server root)
schematics_folder = "schematics"

# Maximum undo levels per player (0 disables undo)
# Default: 5, Range: 0-20
max_undo_levels = 5
```

---

## Typical Workflow

```
1. /schem load spawn_castle.schem     — loads schematic, preview appears
2. /schem rotate 180                  — face the entrance toward spawn
3. /schem nudge up 3                  — raise it above ground
4. /schem nudge east 10               — center it on the road
5. /schem confirm --ignore-air        — paste, preserving terrain under air gaps
6. ...oops, wrong spot
7. /schem undo                        — world restored
```

---

## Supported Formats

| Format | Extension | Source |
|--------|-----------|--------|
| Sponge Schematic v2 | `.schem` | WorldEdit, Litematica, Amulet |
| Sponge Schematic v3 | `.schem` | WorldEdit 7.3+ |
| Vanilla Structure | `.nbt` | Structure blocks, Minecraft data packs |

> **Note:** Legacy `.schematic` (MCEdit format) is not supported. Convert to `.schem` using WorldEdit or Amulet.

---

## Requirements

- **Minecraft** 1.21.1
- **NeoForge** 21.1.x
- **Server-side only** — players do not need any client mod

---

## Building from Source

```bash
git clone https://github.com/Pweg0/SchemImporter.git
cd SchemImporter
./gradlew build
```

The built JAR will be in `build/libs/`.

---

## License

Open source. See [LICENSE](LICENSE) for details.
