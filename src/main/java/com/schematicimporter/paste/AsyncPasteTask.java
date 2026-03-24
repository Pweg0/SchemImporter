package com.schematicimporter.paste;

import com.schematicimporter.schematic.BlockPlacement;
import com.schematicimporter.schematic.EntityPlacement;
import com.schematicimporter.schematic.SchematicHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Tick-spread async paste state container.
 *
 * <p>Holds all in-progress paste state and drains blocks tick-by-tick via {@link #tick(int)}.
 * The caller (server tick event) calls {@code tick(batchSize)} each tick and acts on the
 * returned {@link TickResult}.</p>
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>Cancel is checked at the START of tick() — each batch is atomic (D-04)</li>
 *   <li>Air filtering happens at construction time, not per-tick (for O(1) tick cost)</li>
 *   <li>Entities are placed only after all blocks are done</li>
 *   <li>batchSize is passed by the caller each tick — this class never reads config directly</li>
 * </ul>
 * </p>
 */
public class AsyncPasteTask {

    /**
     * Result returned by {@link #tick(int)} indicating what the paste engine should do next.
     */
    public enum TickResult {
        /** More blocks remain — call tick() again next server tick. */
        CONTINUE,
        /** All blocks (and entities) have been placed — paste is complete. */
        DONE,
        /** Cancel was requested — no more blocks will be placed. */
        CANCELLED
    }

    // ---- Immutable state set at construction ----

    /** Blocks not yet placed. Air blocks are pre-filtered if ignoreAir was true. */
    private final ArrayDeque<BlockPlacement> remaining;

    /** Entities to spawn after all blocks are placed. */
    private final List<EntityPlacement> entities;

    /** Set of chunk positions that were force-loaded for this paste. */
    private final Set<ChunkPos> forcedChunks;

    /** Paste origin in world coordinates. */
    private final BlockPos origin;

    /** UUID of the player who started this paste (for action bar feedback and cancel guard). */
    private final UUID playerUuid;

    /** Total blocks to place (after air filtering), set at construction for % calculations. */
    private final int totalBlocks;

    /** Timestamp when this task was constructed (ms since epoch). */
    private final long startTimeMs;

    /** Level operations for placing blocks and spawning entities. */
    private final PasteLevelOps ops;

    // ---- Mutable state ----

    /** Set to true by {@link #requestCancel()}. Checked at start of each tick(). */
    private boolean cancelRequested = false;

    /** Cumulative count of blocks placed across all ticks. */
    private int blocksPlaced = 0;

    /** Count of entities successfully spawned in {@link #placeEntities()}. */
    private int entitiesSpawnedCount = 0;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Create an AsyncPasteTask.
     *
     * @param holder       the fully-parsed schematic
     * @param origin       world position for the schematic origin (lower corner)
     * @param ignoreAir    if true, air blocks are filtered out at construction time
     * @param playerUuid   UUID of the initiating player
     * @param forcedChunks set of chunks that were force-loaded for this paste
     * @param ops          level operations interface
     */
    public AsyncPasteTask(
            SchematicHolder holder,
            BlockPos origin,
            boolean ignoreAir,
            UUID playerUuid,
            Set<ChunkPos> forcedChunks,
            PasteLevelOps ops
    ) {
        this.origin = origin;
        this.playerUuid = playerUuid;
        this.forcedChunks = forcedChunks;
        this.ops = ops;
        this.entities = holder.entities();
        this.startTimeMs = System.currentTimeMillis();

        // Pre-filter air blocks at construction — O(n) once rather than per-tick
        this.remaining = new ArrayDeque<>();
        for (BlockPlacement bp : holder.blocks()) {
            if (ignoreAir && bp.blockState().isAir()) {
                continue;
            }
            this.remaining.add(bp);
        }
        this.totalBlocks = this.remaining.size();
    }

    // =========================================================================
    // Core tick method
    // =========================================================================

    /**
     * Drain up to {@code batchSize} blocks from the queue and place them.
     *
     * <p>Cancel is checked at the START of tick() before placing any blocks.
     * Each batch is atomic — mid-batch cancellation is not supported.</p>
     *
     * @param batchSize maximum blocks to place in this tick
     * @return {@link TickResult#CANCELLED} if cancel was requested,
     *         {@link TickResult#DONE} if all blocks (and entities) are placed,
     *         {@link TickResult#CONTINUE} if more blocks remain
     */
    public TickResult tick(int batchSize) {
        // Check cancel FIRST — before placing any blocks this tick
        if (cancelRequested) {
            return TickResult.CANCELLED;
        }

        // If queue is already empty, place entities and finish
        if (remaining.isEmpty()) {
            placeEntities();
            return TickResult.DONE;
        }

        // Drain up to batchSize blocks from the queue
        int placed = 0;
        while (placed < batchSize && !remaining.isEmpty()) {
            BlockPlacement bp = remaining.poll();
            BlockPos worldPos = origin.offset(bp.relativePos());

            // Place the block
            ops.setBlock(worldPos, bp.blockState(), PasteExecutor.FLAGS);

            // Apply block entity NBT if present
            if (bp.blockEntityNbt() != null) {
                BlockEntity be = ops.getBlockEntity(worldPos);
                if (be != null) {
                    PasteExecutor.applyBlockEntityNbt(be, bp.blockEntityNbt().copy());
                    be.setChanged();
                }
            }

            blocksPlaced++;
            placed++;
        }

        // If queue is now empty, place entities and signal completion
        if (remaining.isEmpty()) {
            placeEntities();
            return TickResult.DONE;
        }

        return TickResult.CONTINUE;
    }

    // =========================================================================
    // Cancel
    // =========================================================================

    /**
     * Request cancellation of this paste task.
     *
     * <p>The cancel is checked at the start of the next {@link #tick(int)} call.
     * Blocks already placed before cancel remain in the world (no rollback).</p>
     */
    public void requestCancel() {
        this.cancelRequested = true;
    }

    // =========================================================================
    // Progress reporting
    // =========================================================================

    /**
     * Returns the cumulative number of blocks placed across all tick() calls.
     */
    public int blocksPlaced() {
        return blocksPlaced;
    }

    /**
     * Returns the total number of blocks to place (after air filtering at construction).
     */
    public int totalBlocks() {
        return totalBlocks;
    }

    /**
     * Returns the paste completion percentage (0–100).
     *
     * <p>Returns 100 for empty schematics (0 total blocks).</p>
     */
    public int progressPercent() {
        if (totalBlocks == 0) return 100;
        return blocksPlaced * 100 / totalBlocks;
    }

    /**
     * Returns the estimated seconds remaining until paste completion.
     *
     * @return -1 if no blocks have been placed yet (no elapsed time to extrapolate from),
     *         otherwise a positive integer ETA in seconds
     */
    public int etaSeconds() {
        if (blocksPlaced == 0) return -1;
        long elapsed = System.currentTimeMillis() - startTimeMs;
        double msPerBlock = (double) elapsed / blocksPlaced;
        int blocksRemaining = totalBlocks - blocksPlaced;
        return (int) Math.ceil((blocksRemaining * msPerBlock) / 1000.0);
    }

    /**
     * Format a block count as a human-readable string.
     *
     * <ul>
     *   <li>Under 1,000: plain integer (e.g. "999")</li>
     *   <li>1,000–999,999: K suffix with one decimal (e.g. "1.5K")</li>
     *   <li>1,000,000+: M suffix with one decimal (e.g. "1.0M")</li>
     * </ul>
     */
    public static String formatCount(int n) {
        if (n >= 1_000_000) return String.format(Locale.US, "%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format(Locale.US, "%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }

    /**
     * Build an action-bar progress component for the given task.
     *
     * <p>Format: {@code ████████░░░░░░░░ 45% | 450/1.0K | ~5s}</p>
     *
     * <p>The progress bar uses 8 unicode block characters (U+2588 full block,
     * U+2591 light shade) to represent the completion percentage.</p>
     *
     * @param task the task to report progress for
     * @return a {@link Component} suitable for action bar display
     */
    public static Component buildProgressComponent(AsyncPasteTask task) {
        int pct = task.progressPercent();
        int filled = (pct * 8) / 100;
        String bar = "\u2588".repeat(filled) + "\u2591".repeat(8 - filled);
        String counts = formatCount(task.blocksPlaced()) + "/" + formatCount(task.totalBlocks());
        String eta = task.etaSeconds() >= 0 ? "~" + task.etaSeconds() + "s" : "?s";
        return Component.literal(bar + " " + pct + "% | " + counts + " | " + eta);
    }

    // =========================================================================
    // Chunk management
    // =========================================================================

    /**
     * Release all force-loaded chunks for this paste task.
     *
     * <p>Should be called by the completion handler when tick() returns DONE or CANCELLED.</p>
     */
    public void releaseChunks() {
        for (ChunkPos cp : forcedChunks) {
            ops.setChunkForced(cp.x, cp.z, false);
        }
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    /** Returns the chunk set passed at construction. */
    public Set<ChunkPos> getForcedChunks() {
        return forcedChunks;
    }

    /** Returns the UUID of the player who started this paste. */
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    /** Returns true if cancel has been requested. */
    public boolean isCancelRequested() {
        return cancelRequested;
    }

    /** Returns the constructor timestamp (ms since epoch). */
    public long getStartTimeMs() {
        return startTimeMs;
    }

    /** Returns the count of entities placed by placeEntities(). */
    public int entitiesSpawned() {
        return entitiesSpawnedCount;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Spawn all entities for this schematic.
     *
     * <p>Called exactly once after all blocks are placed (when remaining queue is empty,
     * before returning DONE). Entity positions are computed as world coordinates from
     * the paste origin.</p>
     */
    private void placeEntities() {
        for (EntityPlacement ep : entities) {
            // World pos = lower corner of origin (as Vec3) + relative pos
            Vec3 worldEntityPos = Vec3.atLowerCornerOf(origin).add(ep.relativePos());

            // Copy entity NBT and set world position
            CompoundTag entityNbt = ep.entityNbt().copy();
            entityNbt.put("Pos", newDoubleList(worldEntityPos.x, worldEntityPos.y, worldEntityPos.z));
            // Remove UUID to prevent collisions on repeated pastes
            entityNbt.remove("UUID");
            entityNbt.remove("UUIDLeast");
            entityNbt.remove("UUIDMost");

            boolean spawned = ops.spawnEntityFromNbt(entityNbt);
            if (spawned) entitiesSpawnedCount++;
        }
    }

    /**
     * Build a {@link ListTag} of three {@link DoubleTag} values for an entity "Pos" tag.
     */
    private static ListTag newDoubleList(double x, double y, double z) {
        ListTag tag = new ListTag();
        tag.add(DoubleTag.valueOf(x));
        tag.add(DoubleTag.valueOf(y));
        tag.add(DoubleTag.valueOf(z));
        return tag;
    }
}
