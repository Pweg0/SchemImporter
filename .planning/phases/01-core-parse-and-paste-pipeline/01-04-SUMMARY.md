---
phase: 01-core-parse-and-paste-pipeline
plan: "04"
subsystem: paste-engine
tags:
  - tdd
  - paste
  - session
  - schematic-loader
  - block-entity
  - entity-placement
  - chunk-management
dependency_graph:
  requires:
    - 01-02  # SpongeSchematicParser
    - 01-03  # VanillaNbtParser
  provides:
    - PasteExecutor.execute
    - SchematicLoader.load
    - SchematicLoader.listSchematics
    - SessionManager.getOrCreate
    - PasteSession state machine
  affects:
    - commands (Phase 1, Plan 05 — will wire these to /schem commands)
tech_stack:
  added:
    - PasteLevelOps interface (narrow world ops for testability)
    - PasteFeedbackSink interface (narrow feedback sink for testability)
    - ServerLevelOpsAdapter (bridges ServerLevel to PasteLevelOps)
    - TestLevelStub (pure-Java test stub implementing PasteLevelOps)
  patterns:
    - Interface adapter for ServerLevel testability without Mockito
    - TDD RED→GREEN with stub-based verification of setBlock args
    - PasteFeedbackSink.noop() for silent test execution
    - loadCustomOnly(nbt, RegistryAccess.EMPTY) for BE NBT application in 1.21.x
key_files:
  created:
    - src/main/java/com/schematicimporter/paste/PasteExecutor.java
    - src/main/java/com/schematicimporter/paste/PasteLevelOps.java
    - src/main/java/com/schematicimporter/paste/PasteFeedbackSink.java
    - src/main/java/com/schematicimporter/paste/ServerLevelOpsAdapter.java
    - src/main/java/com/schematicimporter/session/SessionState.java
    - src/main/java/com/schematicimporter/session/PasteSession.java
    - src/main/java/com/schematicimporter/session/SessionManager.java
    - src/main/java/com/schematicimporter/schematic/SchematicLoader.java
    - src/test/java/com/schematicimporter/PasteExecutorFlagTest.java
    - src/test/java/com/schematicimporter/IgnoreAirTest.java
    - src/test/java/com/schematicimporter/BlockEntityNbtTest.java
    - src/test/java/com/schematicimporter/EntityPlacementTest.java
    - src/test/java/com/schematicimporter/SpongeOffsetTest.java
    - src/test/java/com/schematicimporter/TestLevelStub.java
  modified: []
decisions:
  - "PasteExecutor accepts PasteLevelOps interface (not ServerLevel directly) to enable unit testing without Mockito or a live server; production path wraps ServerLevel in ServerLevelOpsAdapter"
  - "PasteLevelOps.spawnEntityFromNbt(CompoundTag) bundles entity creation + spawn as one operation so TestLevelStub can capture the NBT (with Pos already set) without needing EntityType.create to work"
  - "BlockEntity.loadCustomOnly(nbt, RegistryAccess.EMPTY) is the correct 1.21.x API for applying parsed schematic NBT — calls loadAdditional without resetting DataComponents"
  - "FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_KNOWN_SHAPE; UPDATE_ALL (value 3 = UPDATE_NEIGHBORS | UPDATE_CLIENTS) never used — prevents redstone cascades and TPS drops during large pastes"
  - "SchematicLoader.load uses normalize() + startsWith(root) path traversal guard — malicious ../.. paths rejected before file access"
metrics:
  duration_minutes: 25
  completed_date: "2026-03-20"
  tasks_completed: 1
  files_created: 14
  files_modified: 0
  tests_added: 14
  tests_passing: 14
---

# Phase 01 Plan 04: Paste Engine Summary

**One-liner:** Synchronous paste engine with `PasteExecutor` (correct setBlock flags, chunk force-loading, BE NBT via `loadCustomOnly`, Sponge offset subtraction), `SessionManager`/`PasteSession` state machine, and `SchematicLoader` file scanner — verified by 14 unit tests via `PasteLevelOps` stub.

## What Was Built

### Core Components

**PasteExecutor** (`src/main/java/com/schematicimporter/paste/PasteExecutor.java`)
- Public API: `execute(SchematicHolder, BlockPos, boolean ignoreAir, CommandSourceStack, ServerLevel)`
- Testable core: `execute(SchematicHolder, BlockPos, boolean ignoreAir, PasteFeedbackSink, PasteLevelOps)`
- FLAGS constant = `Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_KNOWN_SHAPE`
- Chunk force-loading in try/finally (force before loop, release in finally)
- Block entity NBT via `be.loadCustomOnly(nbt, RegistryAccess.EMPTY)` + `be.setChanged()`
- Entity spawn: `worldPos = Vec3.atLowerCornerOf(origin).add(ep.relativePos())`
- Sponge offset: `worldPos = origin.offset(relPos).offset(-offset[0], -offset[1], -offset[2])`
- Completion feedback: block count, elapsed time (seconds), entity count, unknown replaced count

**PasteLevelOps / PasteFeedbackSink** (`src/main/java/com/schematicimporter/paste/`)
- `PasteLevelOps`: `setBlock`, `getBlockEntity`, `setChunkForced`, `spawnEntityFromNbt`
- `PasteFeedbackSink`: `sendSuccess`, `sendFailure`, `of(CommandSourceStack)`, `noop()`
- `ServerLevelOpsAdapter`: bridges `ServerLevel` to `PasteLevelOps` for production
- `TestLevelStub` (test): captures `setBlock` calls (pos, state, flags), entity NBTs, block entity loads

**SessionState / PasteSession / SessionManager** (`src/main/java/com/schematicimporter/session/`)
- `SessionState` enum: `IDLE` / `LOADED` / `AWAITING_CONFIRMATION`
- `PasteSession`: `load(name, holder)`, `setPendingConfirmation(bool)`, `reset()`
- `SessionManager.INSTANCE`: `getOrCreate(UUID)`, `remove(UUID)` singleton

**SchematicLoader** (`src/main/java/com/schematicimporter/schematic/SchematicLoader.java`)
- `listSchematics(MinecraftServer)`: recursive `Files.walk`, returns `SchematicFileInfo` list with dimensions
- `load(String name, MinecraftServer)`: `normalize() + startsWith(root)` path traversal guard, dispatches to `SpongeSchematicParser` or `VanillaNbtParser`
- `getSchematicsRoot(MinecraftServer)`: resolves from `ModConfig.CONFIG.schematicsFolder`

### Test Infrastructure

**TestLevelStub** (`src/test/java/com/schematicimporter/TestLevelStub.java`)
- Pure Java implementation of `PasteLevelOps`
- Captures: `setBlockCalls` (List of `{pos, state, flags}`), `capturedEntityNbts`
- `registerBlockEntityAt(pos)` → `FakeBlockEntity` (extends real `BlockEntity` with CHEST type)
- `FakeBlockEntity` overrides `loadAdditional` and `setChanged` to record calls and NBT

## Tests Added

| Test Class | Tests | Verifies |
|---|---|---|
| `PasteExecutorFlagTest` | 2 | `FLAGS` == `UPDATE_CLIENTS\|UPDATE_SUPPRESS_DROPS\|UPDATE_KNOWN_SHAPE`; != `UPDATE_ALL` |
| `IgnoreAirTest` | 2 | `ignoreAir=true` skips air (2 setBlock calls); `ignoreAir=false` places all 3 |
| `BlockEntityNbtTest` | 3 | `be.load()` called after setBlock; `be.setChanged()` called; NBT has no x/y/z/id |
| `EntityPlacementTest` | 4 | `worldPos = origin + relPos`; Pos tag set in NBT; UUID stripped |
| `SpongeOffsetTest` | 3 | `worldPos = origin + relPos - spongeOffset`; identity with `{0,0,0}` offset |
| **Total** | **14** | **all passing** |

## Verification Results

- `./gradlew test` → BUILD SUCCESSFUL (14 new tests + all existing tests pass)
- `PasteExecutor.java` contains `UPDATE_SUPPRESS_DROPS` ✓
- `PasteExecutor.java` contains `spongeOffset` ✓
- `PasteExecutor.java` contains `offset(-offset[0], -offset[1], -offset[2])` ✓
- `PasteExecutor.java` contains `setChunkForced` ✓
- `PasteExecutor.java` contains `be.setChanged()` ✓
- `PasteExecutor.java` contains `finally {` ✓
- `SchematicLoader.java` contains `startsWith(root)` ✓

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Interface] Added PasteLevelOps/PasteFeedbackSink interfaces for testability**
- **Found during:** Task implementation
- **Issue:** Plan specified `execute(SchematicHolder, BlockPos, boolean, CommandSourceStack, ServerLevel)` as the public API. Unit tests cannot instantiate a real `ServerLevel` without a running Minecraft server. Mockito is not on the test classpath.
- **Fix:** Extracted `PasteLevelOps` (world operations) and `PasteFeedbackSink` (feedback) interfaces. `PasteExecutor` gains an internal overload accepting these interfaces; the public `ServerLevel` overload delegates to it via `ServerLevelOpsAdapter`. The public API is preserved exactly as specified.
- **Files modified:** `PasteExecutor.java`, `PasteLevelOps.java`, `PasteFeedbackSink.java`, `ServerLevelOpsAdapter.java` (new), `TestLevelStub.java` (new)

**2. [Rule 1 - Bug] Used `loadCustomOnly` instead of `load()` for BlockEntity NBT**
- **Found during:** Implementation
- **Issue:** Plan said `be.load(nbt)` but in NeoForge 1.21.1, `BlockEntity` has no single-argument `load(CompoundTag)` method. The public API is `loadCustomOnly(CompoundTag, HolderLookup.Provider)` (calls `loadAdditional` without DataComponent reset) and `loadWithComponents(CompoundTag, HolderLookup.Provider)`.
- **Fix:** Used `be.loadCustomOnly(nbt, RegistryAccess.EMPTY)` which correctly calls `loadAdditional` for vanilla data (chests, furnaces) without needing a live registry. Recorded as decision.

**3. [Rule 2 - Missing] `spawnEntityFromNbt` encapsulates entity creation**
- **Found during:** EntityPlacementTest design
- **Issue:** `EntityType.create(nbt, level)` requires a live `ServerLevel` type parameter, making the entity creation path untestable via `PasteLevelOps` if the method accepted a raw `Entity`.
- **Fix:** `PasteLevelOps.spawnEntityFromNbt(CompoundTag)` handles the full create+spawn cycle. `ServerLevelOpsAdapter` calls `EntityType.create(nbt, level)` + `level.addFreshEntity()`. `TestLevelStub` captures the NBT for assertion without calling `EntityType.create`.

## Self-Check: PASSED

All 14 created files found on disk. Both commits (64f1c7e RED, fe3a226 GREEN) confirmed in git history. Full test suite passes with 0 failures.
