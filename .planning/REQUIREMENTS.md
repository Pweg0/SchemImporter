# Requirements: SchematicImporter

**Defined:** 2026-03-20
**Core Value:** Import and paste schematics reliably without crashing the server or consuming excessive memory

## v1 Requirements

### Parsing

- [x] **PARSE-01**: Mod can parse .schem (Sponge Schematic v1/v2/v3) files from server schematics folder
- [x] **PARSE-02**: Mod can parse .nbt (Vanilla Structure) files from server schematics folder
- [x] **PARSE-03**: Parser correctly handles modded blocks from AllTheMods palette (not just vanilla)
- [x] **PARSE-04**: Lazy loading — read schematic metadata (dimensions, block count) without loading full block data into memory

### Commands

- [ ] **CMD-01**: `/schem list` shows all available schematics in the server folder with file sizes
- [ ] **CMD-02**: `/schem load <name>` loads a schematic into the operator's session
- [ ] **CMD-03**: `/schem paste` pastes the loaded schematic at the operator's current position
- [ ] **CMD-04**: `/schem cancel` cancels an in-progress async paste operation
- [ ] **CMD-05**: All commands restricted to OP level 2+ (server operators only)
- [x] **CMD-06**: Commands work server-side only — no client mod required

### Paste Engine

- [ ] **PASTE-01**: Async tick-spread paste — blocks placed across multiple ticks via ServerTickEvent.Post
- [ ] **PASTE-02**: Configurable blocks-per-tick rate (default conservative for AllTheMods load)
- [ ] **PASTE-03**: Correct setBlock flags to avoid neighbor-update cascade (no UPDATE_ALL)
- [ ] **PASTE-04**: Force-load chunks before pasting, release after completion
- [ ] **PASTE-05**: `--ignore-air` flag to skip air blocks and preserve existing terrain
- [ ] **PASTE-06**: Block entities (chests, signs, banners, etc.) correctly placed with NBT data
- [ ] **PASTE-07**: Entities (armor stands, item frames, mobs) imported from schematic
- [ ] **PASTE-08**: Progress feedback — show % complete and ETA in chat during paste

### Placement

- [ ] **PLACE-01**: Rotate schematic 90/180/270 degrees before pasting
- [ ] **PLACE-02**: Preview bounding box — show outline (particles/markers) of where the schematic will be placed
- [ ] **PLACE-03**: Confirm or cancel after preview before actual paste begins
- [ ] **PLACE-04**: Offset nudge — adjust paste position (north/south/east/west/up/down) before confirming

### Configuration

- [x] **CFG-01**: Server config file for blocks-per-tick rate
- [x] **CFG-02**: Configurable schematics folder path (default: `schematics/` in server root)

## v2 Requirements

### Enhanced Features

- **ENH-01**: `/schem info <name>` — show dimensions, block count, entity count without loading
- **ENH-02**: Undo last paste operation (single-level undo with block snapshot)
- **ENH-03**: Multiple concurrent paste sessions for different operators
- **ENH-04**: Flip/mirror schematic (horizontal/vertical)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Copy/cut operations (selecting in-world regions) | Import-only mod, not a world editor — keeps memory low |
| Multi-level undo/redo history | WorldEdit's biggest memory driver — contradicts lightweight goal |
| Brush tools, masks, patterns | WorldEdit territory — out of scope |
| .schematic (legacy MCEdit format) | Obsolete on 1.21.1 servers, modern formats cover 99% of community builds |
| Client-side mod rendering | Must be server-side only — no client installation required |
| LuckPerms integration | OP-only is sufficient for admin tool |
| Background thread world access | All setBlock must run on main thread — async means tick-spread only |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| PARSE-01 | Phase 1 | Complete |
| PARSE-02 | Phase 1 | Complete |
| PARSE-03 | Phase 1 | Complete |
| PARSE-04 | Phase 1 | Complete |
| CMD-01 | Phase 1 | Pending |
| CMD-02 | Phase 1 | Pending |
| CMD-03 | Phase 1 | Pending |
| CMD-04 | Phase 2 | Pending |
| CMD-05 | Phase 1 | Pending |
| CMD-06 | Phase 1 | Complete |
| PASTE-01 | Phase 2 | Pending |
| PASTE-02 | Phase 2 | Pending |
| PASTE-03 | Phase 1 | Pending |
| PASTE-04 | Phase 1 | Pending |
| PASTE-05 | Phase 1 | Pending |
| PASTE-06 | Phase 1 | Pending |
| PASTE-07 | Phase 1 | Pending |
| PASTE-08 | Phase 1 | Pending |
| PLACE-01 | Phase 3 | Pending |
| PLACE-02 | Phase 3 | Pending |
| PLACE-03 | Phase 3 | Pending |
| PLACE-04 | Phase 3 | Pending |
| CFG-01 | Phase 1 | Complete |
| CFG-02 | Phase 1 | Complete |

**Coverage:**
- v1 requirements: 24 total
- Mapped to phases: 24
- Unmapped: 0

---
*Requirements defined: 2026-03-20*
*Last updated: 2026-03-20 after roadmap creation*
