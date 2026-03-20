# Roadmap: SchematicImporter

## Overview

SchematicImporter is built in three phases that mirror its pipeline structure. Phase 1 proves the parse-to-place pipeline is correct and makes the mod usable at all. Phase 2 delivers the primary value proposition — async tick-spread paste that won't freeze an AllTheMods server — along with the operational controls (cancel, progress) that make async paste safe to use in production. Phase 3 adds the operator quality-of-life features (rotation, preview, nudge) that make the tool pleasant to use but have no correctness dependency on earlier work.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Core Parse and Paste Pipeline** - Correct, synchronous parse-to-place pipeline for both file formats with OP-gated commands
- [ ] **Phase 2: Async Paste and Production Hardening** - Tick-spread async executor, cancel command, progress feedback — makes the mod safe on a loaded server
- [ ] **Phase 3: Operator Quality of Life** - Rotation, server-side preview, placement offset nudge

## Phase Details

### Phase 1: Core Parse and Paste Pipeline
**Goal**: Operators can load and paste both .schem and .nbt schematics correctly, including block entities, with all three critical correctness pitfalls solved (setBlock flags, chunk loading, varint decoding)
**Depends on**: Nothing (first phase)
**Requirements**: PARSE-01, PARSE-02, PARSE-03, PARSE-04, CMD-01, CMD-02, CMD-03, CMD-05, CMD-06, PASTE-03, PASTE-04, PASTE-05, PASTE-06, PASTE-07, PASTE-08, CFG-01, CFG-02
**Success Criteria** (what must be TRUE):
  1. An OP can run `/schem list` and see all .schem and .nbt files in the schematics folder with their sizes
  2. An OP can run `/schem load <name>` and `/schem paste` and a structure appears in the world at the operator's position with blocks and block entities (chests, signs) intact
  3. Pasting a structure with air blocks respects the `--ignore-air` flag — existing terrain is preserved when the flag is set
  4. Entities (armor stands, item frames) from the schematic appear in the world after paste
  5. The server does not crash, hang, or throw StackOverflowError when pasting a structure with modded blocks from AllTheMods palette
**Plans**: 5 plans

Plans:
- [x] 01-01-PLAN.md — Project scaffold, ModConfig, and core data types (SchematicHolder, BlockPlacement, EntityPlacement)
- [x] 01-02-PLAN.md — SpongeSchematicParser: .schem v2/v3 parsing with varint decode and unknown block handling
- [x] 01-03-PLAN.md — VanillaNbtParser: .nbt vanilla structure parsing via StructureTemplate
- [ ] 01-04-PLAN.md — Paste engine: SchematicLoader, PasteSession, SessionManager, PasteExecutor with correctness tests
- [ ] 01-05-PLAN.md — CommandHandler (/schem list/load/paste), translation files (en_us, pt_br), integration checkpoint

### Phase 2: Async Paste and Production Hardening
**Goal**: Paste operations are tick-spread across multiple server ticks at a configurable rate so that large structures (500+ blocks) do not cause noticeable TPS drops on a loaded AllTheMods server
**Depends on**: Phase 1
**Requirements**: PASTE-01, PASTE-02, CMD-04
**Success Criteria** (what must be TRUE):
  1. Pasting a 500+ block structure does not cause visible lag or TPS drop on the server — blocks appear gradually over multiple ticks rather than all at once
  2. An in-progress paste shows percentage complete and estimated time remaining in chat, and responds to `/schem cancel` by stopping immediately
  3. The blocks-per-tick rate is configurable in the server config file and takes effect without a server restart
**Plans**: TBD

### Phase 3: Operator Quality of Life
**Goal**: Operators can rotate schematics, preview the bounding box before committing, and nudge placement position — without requiring any client-side mod
**Depends on**: Phase 2
**Requirements**: PLACE-01, PLACE-02, PLACE-03, PLACE-04
**Success Criteria** (what must be TRUE):
  1. An OP can rotate a loaded schematic 90, 180, or 270 degrees before pasting and the placed structure faces the correct direction
  2. After loading a schematic, the OP can see a server-side bounding box outline (particles or marker entities) showing exactly where the structure will be placed
  3. The OP can nudge the placement position north/south/east/west/up/down before confirming, and the preview updates to reflect the new position
  4. The OP can confirm or cancel from the preview state before any blocks are actually placed
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Core Parse and Paste Pipeline | 3/5 | In Progress|  |
| 2. Async Paste and Production Hardening | 0/? | Not started | - |
| 3. Operator Quality of Life | 0/? | Not started | - |
