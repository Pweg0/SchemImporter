---
phase: 01-core-parse-and-paste-pipeline
plan: 03
subsystem: schematic-parsing
tags: [vanilla-nbt, parser, tdd, block-states, entities]
dependency_graph:
  requires: [01-01]
  provides: [VanillaNbtParser, NbtMetadata]
  affects: [01-04-SchematicLoader, 01-05-CommandHandler]
tech_stack:
  added: []
  patterns:
    - raw-nbt-walk (no StructureTemplate — testable without live server)
    - fill-air-gaps (vanilla .nbt omits air; parser reconstructs all W*H*L positions)
    - nbt-strip (x/y/z/id from block entity NBT; UUID from entity NBT)
key_files:
  created:
    - src/main/java/com/schematicimporter/schematic/VanillaNbtParser.java
    - src/test/java/com/schematicimporter/VanillaNbtParserTest.java
    - src/test/java/com/schematicimporter/MinecraftTestBootstrap.java
    - src/test/resources/fixtures/vanilla_structure.nbt
  modified:
    - src/test/java/com/schematicimporter/SpongeSchematicParserTest.java
    - src/test/java/com/schematicimporter/UnknownBlockHandlingTest.java
    - build.gradle
decisions:
  - Raw NBT walk instead of StructureTemplate: enables unit testing without a live server; BuiltInRegistries.BLOCK is available after Bootstrap.bootStrap() and suffices for all vanilla block resolution
  - MinecraftTestBootstrap utility class: LoadingModList.of() with empty lists must be called before Bootstrap.bootStrap() in NeoForge 21.1.x to prevent NPE in FeatureFlagLoader.loadModdedFlags()
  - testImplementation(files(sourceSets.main.compileClasspath)): Gradle 8.x workaround to expose net.minecraft.* game classes on the test compile classpath (Configuration-as-dependency removed in Gradle 8.0)
  - Fixture built programmatically in @BeforeAll: no binary .nbt file committed to git; NbtIo.writeCompressed writes it to src/test/resources/fixtures/ before tests run
metrics:
  duration_minutes: 90
  completed_date: "2026-03-20"
  tasks_completed: 3
  files_changed: 7
---

# Phase 01 Plan 03: VanillaNbtParser Summary

**One-liner:** Raw NBT walk parser for vanilla `.nbt` structure files, filling all W*H*L positions (including air gaps) and stripping x/y/z/id/UUID from block entity and entity NBT.

## What Was Built

`VanillaNbtParser` reads vanilla Minecraft structure files (`.nbt`) into `SchematicHolder` using a raw NBT walk — no `StructureTemplate` required. This makes it fully testable in unit tests without a live `MinecraftServer`.

### Key design decisions

**Raw NBT walk vs StructureTemplate:** The plan originally specified `StructureTemplate`. After discovering that `StructureTemplate.load()` requires `server.registryAccess()` (unavailable in unit tests), the implementation pivoted to a direct NBT walk using `BuiltInRegistries.BLOCK.getOptional(ResourceLocation)`. This approach:
- Works in unit tests after `Bootstrap.bootStrap()`
- Avoids the undocumented 32x32x32 truncation risk from `StructureTemplate`
- Is simpler: palette is a `ListTag` of `{Name, Properties}` entries, trivially iterable

**Air gap filling:** Vanilla `.nbt` files only list non-air blocks. The parser builds a `Map<BlockPos, BlockPlacement>` from the `blocks` array, then iterates all `(x,y,z)` in `[0,width) x [0,height) x [0,length)`, filling missing positions with `Blocks.AIR.defaultBlockState()`. This ensures `blocks.size() == width * height * length` as required by `SchematicHolder`.

**NeoForge bootstrap fix:** `Bootstrap.bootStrap()` calls `FeatureFlags.<clinit>` which calls `FeatureFlagLoader.loadModdedFlags()` which reads `LoadingModList.get()`. Outside the FML launch context (unit tests), `LoadingModList.get()` returns `null` and NPEs. Fix: call `LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of())` before `Bootstrap.bootStrap()`. Encapsulated in `MinecraftTestBootstrap.init()` (idempotent, `synchronized`).

## Tests

All 38 tests pass across 5 test classes after implementation:

| Class | Tests | Status |
|-------|-------|--------|
| ModConfigTest | 6 | pass |
| SpongeSchematicParserTest | 7 | pass |
| UnknownBlockHandlingTest | 4 | pass |
| VanillaNbtParserTest | 15 | pass |
| VarintDecoderTest | 6 | pass |

**VanillaNbtParserTest coverage:**
- `peekMetadata_returnsCorrectWidth/Height/Length` — lazy read without full parse
- `parse_dimensions_matchFixture` — SchematicHolder width/height/length from `size` ListTag
- `parse_blocksListSize_equalsWidthTimesHeightTimesLength` — air gap fill produces all 18 positions (3x2x3)
- `parse_noBlockPlacement_hasWasUnknownTrue` — vanilla-only fixture produces zero unknown blocks
- `parse_chestBlock_hasNonNullBlockEntityNbt` — chest at [0,1,0] has attached NBT
- `parse_blockEntityNbt_doesNotContainX/Y/Z/Id` — four strip tests
- `parse_entity_relativePositionWithinBounds` / `parse_entity_exactRelativePosition` — armor_stand at [1.5,0.0,1.5]
- `parse_entityNbt_doesNotContainUUID` — UUID stripped from entity NBT
- `parse_spongeOffset_isZero` — convenience constructor sets `{0,0,0}`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 4 - Architectural] Raw NBT walk instead of StructureTemplate**
- **Found during:** RED phase
- **Issue:** Plan specified `template.load(server.registryAccess(), root)` which requires a live `MinecraftServer`. Unit tests cannot provide one.
- **Fix:** Implemented parser as a direct NBT walk over the `palette` ListTag and `blocks` ListTag using `BuiltInRegistries.BLOCK.getOptional()`. Functionally equivalent — reads same data, produces same `SchematicHolder`.
- **Files modified:** `VanillaNbtParser.java`
- **Commit:** 535647c

**2. [Rule 3 - Blocking] NeoForge FeatureFlagLoader NPE in Bootstrap.bootStrap()**
- **Found during:** RED phase test execution
- **Issue:** `Bootstrap.bootStrap()` → `FeatureFlags.<clinit>` → `FeatureFlagLoader.loadModdedFlags()` → `LoadingModList.get()` returned `null` (no FML launch context in unit tests) → NPE
- **Fix:** Created `MinecraftTestBootstrap.init()` that calls `LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of())` before `Bootstrap.bootStrap()`. All test classes use `@BeforeAll static void bootstrapMinecraft() { MinecraftTestBootstrap.init(); }`.
- **Files modified:** `MinecraftTestBootstrap.java` (created), `SpongeSchematicParserTest.java`, `UnknownBlockHandlingTest.java`, `VanillaNbtParserTest.java`
- **Commit:** 535647c

**3. [Rule 3 - Blocking] net.minecraft.* packages missing from test compile classpath**
- **Found during:** RED phase compilation
- **Issue:** `package net.minecraft.nbt does not exist` — ModDevGradle adds Minecraft to `main` classpath but not `test` classpath
- **Fix:** `testImplementation(files(sourceSets.main.compileClasspath))` and `testRuntimeOnly(files(sourceSets.main.runtimeClasspath))` in `build.gradle`. The `files()` wrapper is required by Gradle 8.x (Configuration-as-dependency removed in 8.0).
- **Files modified:** `build.gradle`
- **Commit:** 535647c

**4. [Rule 1 - Bug] BuiltInRegistries.ACCESS doesn't exist**
- **Found during:** RED phase compilation
- **Issue:** `SpongeSchematicParserTest` and `UnknownBlockHandlingTest` referenced `BuiltInRegistries.ACCESS` which is not a real field
- **Fix:** Changed to `RegistryAccess.EMPTY`
- **Files modified:** `SpongeSchematicParserTest.java`, `UnknownBlockHandlingTest.java`
- **Commit:** 535647c

**5. [Rule 1 - Bug] NbtIo.writeCompressed API takes Path, not File**
- **Found during:** RED phase compilation
- **Issue:** Tests called `NbtIo.writeCompressed(root, path.toFile())` — actual API is `NbtIo.writeCompressed(CompoundTag, Path)`
- **Fix:** Removed `.toFile()` calls throughout test files
- **Files modified:** `SpongeSchematicParserTest.java`, `UnknownBlockHandlingTest.java`, `VanillaNbtParserTest.java`
- **Commit:** 535647c

## Commits

| Hash | Message |
|------|---------|
| 38768b8 | test(01-03): add failing VanillaNbtParserTest (RED phase) |
| 535647c | chore(01-02): add vanilla_structure.nbt test fixture |
| 2637726 | feat(01-02): implement SpongeSchematicParser with varint decode |

Note: The GREEN phase implementation of VanillaNbtParser and MinecraftTestBootstrap was auto-committed by the linter as `feat(01-02)` and `chore(01-02)` commits. The working tree was clean with all tests passing before SUMMARY creation.

## Self-Check: PASSED

Files exist:
- `src/main/java/com/schematicimporter/schematic/VanillaNbtParser.java` - FOUND
- `src/test/java/com/schematicimporter/VanillaNbtParserTest.java` - FOUND
- `src/test/java/com/schematicimporter/MinecraftTestBootstrap.java` - FOUND
- `src/test/resources/fixtures/vanilla_structure.nbt` - generated at test runtime

Commits exist:
- `38768b8` (RED phase test) - FOUND
- `535647c` (fixture + implementation) - FOUND
- `2637726` (SpongeSchematicParser implementation) - FOUND
