---
phase: 01-core-parse-and-paste-pipeline
plan: "02"
subsystem: parsing
tags: [sponge-schematic, varint, nbt, neoforge, junit5, minecraft-bootstrap, registry]

# Dependency graph
requires:
  - plan: 01-01
    provides: "SchematicHolder, BlockPlacement, EntityPlacement records; NeoForge project scaffold with test classpath"
provides:
  - SpongeSchematicParser.parse(Path, RegistryAccess) — full .schem v2/v3 parse into SchematicHolder
  - SpongeSchematicParser.peekMetadata(Path) — lazy width/height/length read without block list
  - SpongeSchematicParser.decodeVarints(byte[], int) — public static varint decode for direct testing
  - 200-entry palette round-trip verified by VarintDecoderTest (6 tests)
  - VanillaNbtParser.parse(Path, MinecraftServer) — full .nbt parse (also delivered this plan)
  - VanillaNbtParser.peekMetadata(Path) — lazy .nbt metadata
  - MinecraftTestBootstrap helper: LoadingModList + SharedConstants + Bootstrap.bootStrap()
  - SpongeSchematicParserTest (7 tests), VarintDecoderTest (6), UnknownBlockHandlingTest (4)
  - VanillaNbtParserTest (15 tests)
affects: [01-03, 01-04, 01-05]

# Tech tracking
tech-stack:
  added:
    - "NbtIo.readCompressed(Path, NbtAccounter) / writeCompressed(CompoundTag, Path) — MC 1.21.1 API (Path, not File)"
    - "Bootstrap.bootStrap() + LoadingModList.of() — NeoForge FML bootstrap for unit tests"
    - "BuiltInRegistries.BLOCK.getOptional(ResourceLocation) — static block lookup without RegistryAccess"
    - "MinecraftTestBootstrap utility class — single-call test setup"
  patterns:
    - "Varint decode: MSB-continuation do-while loop (b & 0x80) != 0 — Sponge .schem block data format"
    - "Block state resolution: BuiltInRegistries.BLOCK used directly (not RegistryAccess param) so tests can pass RegistryAccess.EMPTY"
    - "Unknown block handling: wasUnknown=true + Blocks.AIR + originalPaletteKey preserved — no crash, no silent drop"
    - "Palette string parsing: blockName[prop=val,prop2=val2] split by bracket — avoids BlockStateParser checked exceptions"
    - "Test fixture creation: NbtIo.writeCompressed to @TempDir — no binary resource files needed"
    - "NeoForge unit test bootstrap: LoadingModList.of(empty lists) before Bootstrap.bootStrap() prevents FeatureFlagLoader NPE"
    - "files(sourceSets.main.compileClasspath) in build.gradle to expose Minecraft game classes to test compile classpath"

key-files:
  created:
    - "src/main/java/com/schematicimporter/schematic/SpongeSchematicParser.java"
    - "src/main/java/com/schematicimporter/schematic/VanillaNbtParser.java"
    - "src/test/java/com/schematicimporter/MinecraftTestBootstrap.java"
    - "src/test/java/com/schematicimporter/VarintDecoderTest.java"
    - "src/test/java/com/schematicimporter/SpongeSchematicParserTest.java"
    - "src/test/java/com/schematicimporter/UnknownBlockHandlingTest.java"
    - "src/test/resources/fixtures/vanilla_structure.nbt"
  modified:
    - "build.gradle — added files(sourceSets.main.compileClasspath) and files(sourceSets.main.runtimeClasspath) to test classpath"
    - "src/test/java/com/schematicimporter/VanillaNbtParserTest.java — use MinecraftTestBootstrap.init()"

key-decisions:
  - "Used BuiltInRegistries.BLOCK directly instead of routing through RegistryAccess parameter — allows tests to pass RegistryAccess.EMPTY and still resolve vanilla blocks after Bootstrap.bootStrap()"
  - "LoadingModList.of(empty lists) must be called before Bootstrap.bootStrap() in tests — NeoForge's FeatureFlagLoader.loadModdedFlags() calls LoadingModList.get().getModFiles() which NPEs if FML was never started"
  - "NbtIo API is Path-based in MC 1.21.1 — plan referenced .toFile() which is wrong; the actual signature is readCompressed(Path, NbtAccounter) and writeCompressed(CompoundTag, Path)"
  - "Minecraft game classes (NbtIo, CompoundTag, Bootstrap) are in build/moddev/artifacts/neoforge-21.1.220.jar, not in the Maven-resolved neoforge-universal.jar — tests need files(sourceSets.main.compileClasspath) in build.gradle"
  - "Block state strings in Sponge palette parsed manually with bracket split instead of BlockStateParser — avoids CommandSyntaxException checked exceptions in a non-command context"
  - "VanillaNbtParser also fully implemented in this plan (it was stubbed in 01-01, full impl written alongside SpongeSchematicParser to unblock VanillaNbtParserTest which was already written)"

patterns-established:
  - "Pattern: NeoForge unit tests that touch Blocks.* need LoadingModList.of() before Bootstrap.bootStrap() — see MinecraftTestBootstrap.init()"
  - "Pattern: Test classpath for Minecraft game classes requires files(sourceSets.main.compileClasspath) in build.gradle"
  - "Pattern: Sponge v2/v3 palette uses blockId[props] format — split on '[' for block name, then parse comma-separated key=value pairs"
  - "Pattern: decodeVarints() is public static for direct unit testing — avoids needing a full .schem file for varint correctness tests"

requirements-completed: [PARSE-01, PARSE-03, PARSE-04]

# Metrics
duration: 45min
completed: 2026-03-20
---

# Phase 01 Plan 02: SpongeSchematicParser Summary

**Sponge .schem v2/v3 parser with MSB-continuation varint decode, unknown-block tracking (wasUnknown=true + originalPaletteKey), spongeOffset storage, and lazy peekMetadata(); verified by 17 tests including 200-entry palette round-trip**

## Performance

- **Duration:** ~45 min
- **Started:** 2026-03-20T19:30:00Z
- **Completed:** 2026-03-20T20:15:00Z
- **Tasks:** 2 (RED + GREEN; REFACTOR not needed)
- **Files modified:** 8 created, 2 modified

## Accomplishments

- SpongeSchematicParser.java: full Sponge v2/v3 .schem parse with varint decode, wasUnknown handling, spongeOffset, block entities, entities
- VarintDecoderTest: all 6 varint cases pass including 200-entry palette round-trip (indices 128-199 use two-byte varints)
- SpongeSchematicParserTest: 7 tests covering dimensions, block count, no-unknown-blocks, varint edge case at index 128, Offset tag, peekMetadata
- UnknownBlockHandlingTest: 4 tests verifying unknown palette entry produces exactly 1 wasUnknown=true BlockPlacement with correct originalPaletteKey and AIR blockState
- VanillaNbtParser.java: full .nbt parse (size/palette/blocks/entities) with property resolution, block entity stripping, UUID stripping
- MinecraftTestBootstrap: LoadingModList.of() + SharedConstants + Bootstrap.bootStrap() pattern for all Minecraft-touching unit tests
- build.gradle: files(sourceSets.main.compileClasspath) makes Minecraft game classes available for test compilation

## Task Commits

This plan uses TDD. Commits are organized by phase:

1. **RED: Failing tests committed by prior agent** - `38768b8` (test)
2. **GREEN: Full SpongeSchematicParser + VanillaNbtParser implementation** - `2637726` (feat)
3. **CHORE: vanilla_structure.nbt fixture** - `535647c` (chore)

## Files Created/Modified

- `src/main/java/com/schematicimporter/schematic/SpongeSchematicParser.java` — Sponge v2/v3 parser with varint decode, wasUnknown handling, spongeOffset, peekMetadata
- `src/main/java/com/schematicimporter/schematic/VanillaNbtParser.java` — Vanilla .nbt parser (full implementation)
- `src/test/java/com/schematicimporter/MinecraftTestBootstrap.java` — Test bootstrap helper (LoadingModList + SharedConstants + Bootstrap)
- `src/test/java/com/schematicimporter/VarintDecoderTest.java` — 6 varint unit tests (pure byte array, no Minecraft)
- `src/test/java/com/schematicimporter/SpongeSchematicParserTest.java` — 7 integration tests with programmatic .schem fixtures
- `src/test/java/com/schematicimporter/UnknownBlockHandlingTest.java` — 4 unknown-block tests
- `src/test/java/com/schematicimporter/VanillaNbtParserTest.java` — Updated to use MinecraftTestBootstrap.init()
- `src/test/resources/fixtures/vanilla_structure.nbt` — Generated vanilla structure fixture
- `build.gradle` — Added files(sourceSets.main.compileClasspath) and files(sourceSets.main.runtimeClasspath)

## Decisions Made

1. **BuiltInRegistries.BLOCK instead of RegistryAccess**: The parser signature takes `RegistryAccess` for API completeness but internally uses `BuiltInRegistries.BLOCK` (static, always populated after `Bootstrap.bootStrap()`). This allows tests to pass `RegistryAccess.EMPTY` without breaking vanilla block resolution. The `RegistryAccess` parameter is reserved for future datapack block support.

2. **LoadingModList.of() for FML bootstrap in tests**: `Bootstrap.bootStrap()` triggers `Blocks.<clinit>` which calls NeoForge's `FeatureFlagLoader.loadModdedFlags()` which calls `LoadingModList.get().getModFiles()`. When FML is not running, `LoadingModList.get()` returns `null` (NPE). Fix: call `LoadingModList.of(empty lists)` first to give FML a non-null empty instance.

3. **NbtIo.writeCompressed(CompoundTag, Path) not .toFile()**: MC 1.21.1 changed the API to use `java.nio.file.Path` directly. The plan referenced the old `File`-based API. Updated all test fixture creation code.

4. **Manual bracket-split for palette string parsing**: Sponge palette entries like `"minecraft:chest[facing=north,type=single]"` could be parsed via `BlockStateParser.parseForBlock()`, but that throws `CommandSyntaxException` (checked exception) and requires a command context. Simple bracket splitting is more testable and avoids the exception handling overhead.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] NbtIo API uses Path not File in MC 1.21.1**
- **Found during:** RED phase (test compilation)
- **Issue:** Plan specified `NbtIo.readCompressed(path.toFile(), ...)` and tests used `NbtIo.writeCompressed(root, schematic.toFile())`. MC 1.21.1 uses `readCompressed(Path, NbtAccounter)` and `writeCompressed(CompoundTag, Path)`.
- **Fix:** Updated all call sites to use `Path` directly.
- **Files modified:** Test fixture builders in `SpongeSchematicParserTest.java`, `UnknownBlockHandlingTest.java`, `VanillaNbtParserTest.java`
- **Verification:** `./gradlew compileTestJava` exits 0

**2. [Rule 3 - Blocking] NeoForge FeatureFlagLoader NPE when FML not initialized**
- **Found during:** GREEN phase (test execution)
- **Issue:** `Bootstrap.bootStrap()` causes `NullPointerException` in `FeatureFlagLoader.loadModdedFlags()` because `LoadingModList.get()` returns null outside FML launch context.
- **Fix:** Call `LoadingModList.of(empty lists)` before `Bootstrap.bootStrap()`. Created `MinecraftTestBootstrap` helper to encapsulate the three-step sequence.
- **Files modified:** `src/test/java/com/schematicimporter/MinecraftTestBootstrap.java` (created), updated `@BeforeAll` in test classes
- **Verification:** All 32 tests pass; `./gradlew test` exits 0

**3. [Rule 3 - Blocking] Minecraft game classes not on test compile classpath**
- **Found during:** RED phase (test compilation)
- **Issue:** `CompoundTag`, `NbtIo`, `Bootstrap`, `BuiltInRegistries` are in `build/moddev/artifacts/neoforge-21.1.220.jar` (ModDevGradle merged jar). The test classpath uses Maven-resolved `neoforge-universal.jar` which contains only NeoForge-specific classes, not Minecraft game classes.
- **Fix:** Added `testImplementation(files(sourceSets.main.compileClasspath))` and `testRuntimeOnly(files(sourceSets.main.runtimeClasspath))` to `build.gradle`.
- **Files modified:** `build.gradle`
- **Verification:** `./gradlew compileTestJava` exits 0; all Minecraft imports resolve

**4. [Rule 2 - Missing Critical] VanillaNbtParser stub was unworkable stub**
- **Found during:** GREEN phase
- **Issue:** `VanillaNbtParser.java` was a stub throwing `UnsupportedOperationException`. `VanillaNbtParserTest` (already written and committed) would only pass with a real implementation.
- **Fix:** Implemented full `VanillaNbtParser` alongside `SpongeSchematicParser` since both parsers are in the same wave (Wave 2) and the test infrastructure was already in place.
- **Files modified:** `src/main/java/com/schematicimporter/schematic/VanillaNbtParser.java`
- **Verification:** All 15 `VanillaNbtParserTest` tests pass

---

**Total deviations:** 4 auto-fixed (1 bug — API signature, 3 blocking — FML NPE, classpath, stub unworkable)
**Impact on plan:** All auto-fixes necessary for correctness. No scope creep. VanillaNbtParser delivery was technically out-of-scope for 01-02 but required to satisfy the already-committed tests.

## Issues Encountered

- Windows file locking on `build/test-results/test/binary/output.bin` when running tests in rapid succession. Workaround: delete the test-results directory before re-running. This is a Gradle daemon behavior on Windows.

## Next Phase Readiness

- `SpongeSchematicParser.parse(Path, RegistryAccess)` ready for use by `/schem load` command handler
- `SpongeSchematicParser.peekMetadata(Path)` ready for `/schem list` command handler
- `VanillaNbtParser.parse(Path, MinecraftServer)` ready for `.nbt` file loading
- All 32 tests pass; `./gradlew test` exits 0
- `MinecraftTestBootstrap.init()` available for any future test class needing Minecraft registries

---
*Phase: 01-core-parse-and-paste-pipeline*
*Completed: 2026-03-20*

## Self-Check: PASSED

All created files verified to exist on disk. Commits 2637726, 535647c, 38768b8 verified in git log. `./gradlew test` exits 0. All 32 tests pass (6 VarintDecoderTest, 7 SpongeSchematicParserTest, 4 UnknownBlockHandlingTest, 15 VanillaNbtParserTest). Key patterns verified in SpongeSchematicParser.java: varint loop (b & 0x80) != 0, wasUnknown, peekMetadata, spongeOffset.
