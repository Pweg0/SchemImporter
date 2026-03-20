---
phase: 01-core-parse-and-paste-pipeline
plan: 05
subsystem: command
tags: [brigadier, neoforge, i18n, commands, translation]

# Dependency graph
requires:
  - phase: 01-core-parse-and-paste-pipeline
    plan: 04
    provides: PasteExecutor, SessionManager, SchematicLoader — all integration points wired here
provides:
  - CommandHandler with /schem list, /schem load, /schem paste, /schem paste --ignore-air
  - en_us.json with 11 translation keys (English)
  - pt_br.json with 11 translation keys (Brazilian Portuguese)
affects:
  - Phase 02 (async paste, progress feedback — will extend CommandHandler)
  - Phase 03 (rotation/flip — will add paste flags to CommandHandler)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "@EventBusSubscriber(modid) on GAME bus for RegisterCommandsEvent (NeoForge 1.21.x)"
    - "Brigadier nested literal for --ignore-air flag"
    - "Component.translatable() for all user-facing messages — never hardcoded strings"
    - "AWAITING_CONFIRMATION flow: /schem load warns, /schem paste second call confirms"

key-files:
  created:
    - src/main/java/com/schematicimporter/command/CommandHandler.java
    - src/main/resources/assets/schematicimporter/lang/en_us.json
    - src/main/resources/assets/schematicimporter/lang/pt_br.json
  modified: []

key-decisions:
  - "@EventBusSubscriber without bus= parameter defaults to GAME bus in NeoForge 1.21.x — eliminates deprecation warning from Bus.GAME"
  - "schematicimporter.error.player_only key added (not in plan) for non-player command sources — console ops get clear error"
  - "schematics directory auto-created on first /schem list if it does not exist"

patterns-established:
  - "Pattern 1: All /schem commands share a single .requires(src -> src.hasPermission(2)) at the root literal — subcommands inherit the gate automatically"
  - "Pattern 2: Unknown block confirmation uses isPendingConfirmation() on PasteSession — second /schem paste call clears flag and proceeds"

requirements-completed: [CMD-01, CMD-02, CMD-03, CMD-05, CMD-06, PASTE-05, PASTE-08]

# Metrics
duration: 15min
completed: 2026-03-20
---

# Phase 01 Plan 05: CommandHandler Integration Summary

**Brigadier /schem command tree with list/load/paste subcommands, OP level 2 gate, unknown-block confirmation flow, and full i18n in en_us + pt_br**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-03-20T20:15:43Z
- **Completed:** 2026-03-20T20:30:00Z (approx — paused at checkpoint)
- **Tasks:** 2/3 (Task 3 is checkpoint:human-verify — awaiting manual server test)
- **Files modified:** 3 created

## Accomplishments

- Full `/schem` Brigadier command tree registered via `@EventBusSubscriber` on the GAME bus
- Unknown block confirmation flow: `/schem load` warns + sets AWAITING_CONFIRMATION; `/schem paste` second call clears flag and proceeds
- 11 translation keys in both en_us.json and pt_br.json — all user messages go through `Component.translatable()`
- Build succeeds with zero warnings (`Bus.GAME` deprecation eliminated by dropping explicit bus param)

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement CommandHandler with all /schem subcommands** - `1b31307` (feat)
2. **Task 2: Create translation files (en_us.json and pt_br.json)** - `90025a0` (feat)
3. **Task 3: Human verify on live server** — CHECKPOINT — awaiting approval

## Files Created/Modified

- `src/main/java/com/schematicimporter/command/CommandHandler.java` — Brigadier command tree with list/load/paste subcommands, OP gating, unknown-block flow
- `src/main/resources/assets/schematicimporter/lang/en_us.json` — 11 English translation keys
- `src/main/resources/assets/schematicimporter/lang/pt_br.json` — 11 Brazilian Portuguese translation keys

## Decisions Made

- Used `@EventBusSubscriber(modid = "schematicimporter")` without explicit `bus` parameter — in NeoForge 1.21.x the default is the GAME bus, and specifying `Bus.GAME` triggers a deprecation warning
- Added `schematicimporter.error.player_only` key (not in plan) for the case where a console/command-block source issues `/schem load` or `/schem paste` — console operators get a clear error instead of an exception
- Schematics directory is auto-created on first `/schem list` call — improves first-run UX

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added player_only error for non-player command sources**
- **Found during:** Task 1 (CommandHandler implementation)
- **Issue:** `source.getPlayerOrException()` throws `CommandSyntaxException` if called from console. Plan didn't handle this case, leaving it as an unhandled exception.
- **Fix:** Added try/catch around `getPlayerOrException()` in both `executeLoad` and `executePaste`, sending `schematicimporter.error.player_only` translatable failure message.
- **Files modified:** CommandHandler.java, en_us.json, pt_br.json
- **Verification:** Build passes; key is present in both lang files.
- **Committed in:** `1b31307` (Task 1) and `90025a0` (Task 2)

---

**Total deviations:** 1 auto-fixed (missing critical — non-player error handling)
**Impact on plan:** Essential for correct behavior when console ops run /schem commands. No scope creep.

## Issues Encountered

None during automated tasks. Task 3 is a blocking human-verify checkpoint requiring live server test.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- All Phase 1 automated code is complete and building
- Manual server verification (Task 3 checkpoint) must pass before Phase 2 planning begins
- Phase 2 (async paste / tick-spread) can extend CommandHandler by adding `/schem status` and progress feedback

---
*Phase: 01-core-parse-and-paste-pipeline*
*Completed: 2026-03-20 (partial — awaiting checkpoint)*
