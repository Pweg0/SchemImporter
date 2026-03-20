---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: unknown
stopped_at: Completed 01-03-PLAN.md (VanillaNbtParser)
last_updated: "2026-03-20T19:58:40.931Z"
progress:
  total_phases: 3
  completed_phases: 0
  total_plans: 5
  completed_plans: 3
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-20)

**Core value:** Import and paste schematics reliably without crashing the server or consuming excessive memory
**Current focus:** Phase 01 — core-parse-and-paste-pipeline

## Current Position

Phase: 01 (core-parse-and-paste-pipeline) — EXECUTING
Plan: 4 of 5

## Performance Metrics

**Velocity:**

- Total plans completed: 0
- Average duration: —
- Total execution time: 0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

**Recent Trend:**

- Last 5 plans: —
- Trend: —

*Updated after each plan completion*
| Phase 01-core-parse-and-paste-pipeline P01 | 15 | 2 tasks | 13 files |
| Phase 01-core-parse-and-paste-pipeline P02 | 45 | 2 tasks | 9 files |
| Phase 01 P03 | 90 | 3 tasks | 7 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [Setup]: Use schematic4j 1.1.0 via jarJar for .schem parsing; vanilla StructureTemplate/NbtIo for .nbt — no fat-JAR shading
- [Setup]: All setBlock must run on main server thread; "async" means tick-spread via ServerTickEvent.Post only
- [Setup]: Use Block.UPDATE_CLIENTS | UPDATE_SUPPRESS_DROPS | UPDATE_KNOWN_SHAPE flags — never UPDATE_ALL
- [Phase 01-core-parse-and-paste-pipeline]: ModConfigSpec is in net.neoforged.neoforge.common (not net.neoforged.fml.config) in NeoForge 21.1.220
- [Phase 01-core-parse-and-paste-pipeline]: Unit tests using ModConfigSpec need nightconfig 3.8.3 and FML loader 4.0.42 on testRuntimeOnly classpath to run outside the game environment
- [Phase 01-core-parse-and-paste-pipeline]: displayTest=IGNORE_SERVER_VERSION in neoforge.mods.toml instead of dist=DEDICATED_SERVER on @Mod to support singleplayer logical server
- [Phase 01-core-parse-and-paste-pipeline]: SpongeSchematicParser uses BuiltInRegistries.BLOCK directly (not RegistryAccess param) — allows tests to pass RegistryAccess.EMPTY while resolving vanilla blocks after Bootstrap.bootStrap()
- [Phase 01-core-parse-and-paste-pipeline]: NeoForge unit tests touching Blocks.* require LoadingModList.of(empty lists) before Bootstrap.bootStrap() — FeatureFlagLoader.loadModdedFlags() NPEs otherwise; MinecraftTestBootstrap helper encapsulates this pattern
- [Phase 01-core-parse-and-paste-pipeline]: Minecraft game classes (NbtIo, CompoundTag, Bootstrap) are in ModDevGradle merged jar, not Maven neoforge-universal.jar — test classpath needs files(sourceSets.main.compileClasspath) in build.gradle
- [Phase 01-core-parse-and-paste-pipeline]: Raw NBT walk instead of StructureTemplate: unit-testable without live server; BuiltInRegistries.BLOCK suffices for vanilla block resolution
- [Phase 01-core-parse-and-paste-pipeline]: MinecraftTestBootstrap: LoadingModList.of() with empty lists before Bootstrap.bootStrap() prevents NPE in NeoForge FeatureFlagLoader during unit tests
- [Phase 01-core-parse-and-paste-pipeline]: testImplementation(files(sourceSets.main.compileClasspath)): Gradle 8.x workaround to expose net.minecraft.* on test classpath

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 1]: schematic4j last released Dec 2023 — review open GitHub issues before starting; be prepared to fork for targeted fix if needed
- [Phase 1]: StructureTemplate behavior above 32x32x32 is undocumented — test with large .nbt files and fall back to custom NBT parser if truncation observed
- [Phase 2]: ForcedChunkManager edge case (GitHub #8099) — validate NeoForge 1.21.1 behavior with minimal test before relying on it in production
- [Phase 3]: BlockState.rotate() is a no-op for mod blocks that don't override it — test against common ATM mods (Create, IE, Thermal, Mekanism) before shipping

## Session Continuity

Last session: 2026-03-20T19:58:40.926Z
Stopped at: Completed 01-03-PLAN.md (VanillaNbtParser)
Resume file: None
