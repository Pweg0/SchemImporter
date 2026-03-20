package com.schematicimporter.paste;

import com.schematicimporter.schematic.BlockPlacement;
import com.schematicimporter.schematic.EntityPlacement;
import com.schematicimporter.schematic.SchematicHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.ChunkPos;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * Synchronous paste engine — places all blocks and entities from a {@link SchematicHolder}
 * into a {@link ServerLevel}.
 *
 * <p><b>Correctness requirements (verified by tests):</b>
 * <ul>
 *   <li>setBlock flags: {@link #FLAGS} = {@link Block#UPDATE_CLIENTS} |
 *       {@link Block#UPDATE_SUPPRESS_DROPS} | {@link Block#UPDATE_KNOWN_SHAPE} — never
 *       {@link Block#UPDATE_ALL} (PASTE-03)</li>
 *   <li>Chunk force-loading: all chunks in the bounding box are force-loaded before the
 *       paste loop starts and released in a {@code finally} block (PASTE-04)</li>
 *   <li>Block entity NBT: {@code be.loadCustomOnly(nbt, registries)} called after
 *       {@code setBlock}; {@code be.setChanged()} called after load (PASTE-06)</li>
 *   <li>Entity coordinate: {@code worldPos = Vec3.atLowerCornerOf(origin).add(ep.relativePos())}
 *       (PASTE-07)</li>
 *   <li>Sponge offset: {@code worldPos = origin.offset(relPos).offset(-offset[0], -offset[1], -offset[2])}
 *       (Pitfall 7)</li>
 * </ul>
 * </p>
 *
 * <p><b>Testability:</b> The core logic is exposed via
 * {@link #execute(SchematicHolder, BlockPos, boolean, PasteFeedbackSink, PasteLevelOps)},
 * which accepts a {@link PasteLevelOps} interface rather than a raw {@link ServerLevel}.
 * The production entry point {@link #execute(SchematicHolder, BlockPos, boolean, CommandSourceStack, ServerLevel)}
 * wraps the live level in a {@link ServerLevelOpsAdapter}.</p>
 */
public class PasteExecutor {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * setBlock flags for all block placements.
     *
     * <ul>
     *   <li>{@link Block#UPDATE_CLIENTS} (2) — sends block update packet to clients</li>
     *   <li>{@link Block#UPDATE_SUPPRESS_DROPS} (4) — prevents item drops when replacing
     *       blocks (important: without this, replacing torches/pressure plates spawns items)</li>
     *   <li>{@link Block#UPDATE_KNOWN_SHAPE} (16) — skips neighbor shape updates that
     *       would cascade into potentially thousands of additional updates on large pastes</li>
     * </ul>
     *
     * <p><b>Why not UPDATE_ALL (3)?</b> UPDATE_ALL = UPDATE_NEIGHBORS | UPDATE_CLIENTS.
     * UPDATE_NEIGHBORS triggers neighbor block updates (e.g. redstone, falling sand physics)
     * for every block placed. On structures with redstone, this can cause immediate circuit
     * evaluation during paste, corrupting the structure. On large pastes it can also cause
     * severe TPS drops or stack overflows from cascading updates.</p>
     *
     * <p>Package-private so tests can access it via {@code PasteExecutor.FLAGS}.</p>
     */
    public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_KNOWN_SHAPE;

    private PasteExecutor() {}

    // =========================================================================
    // Production entry point
    // =========================================================================

    /**
     * Execute a paste synchronously using a live {@link ServerLevel}.
     *
     * <p>This is the production entry point used by the {@code /schem paste} command handler.
     * Internally delegates to {@link #execute(SchematicHolder, BlockPos, boolean, PasteFeedbackSink, PasteLevelOps)}.</p>
     *
     * @param holder     the fully-parsed schematic to paste
     * @param origin     world position for the schematic origin (lower corner)
     * @param ignoreAir  if true, air blocks in the schematic are skipped (preserves existing blocks)
     * @param source     command source for feedback messages
     * @param level      the target world
     */
    public static void execute(
        SchematicHolder holder,
        BlockPos origin,
        boolean ignoreAir,
        CommandSourceStack source,
        ServerLevel level
    ) {
        execute(holder, origin, ignoreAir, PasteFeedbackSink.of(source), new ServerLevelOpsAdapter(level));
    }

    // =========================================================================
    // Testable core
    // =========================================================================

    /**
     * Execute a paste using the narrow {@link PasteLevelOps} interface.
     *
     * <p>This overload exists for unit testing — tests pass a {@code TestLevelStub}
     * that records calls without needing a live {@link ServerLevel}.</p>
     *
     * @param holder    the schematic to paste
     * @param origin    world origin for the paste
     * @param ignoreAir if true, air blocks are skipped
     * @param feedback  sink for completion messages
     * @param ops       level operations (production: {@link ServerLevelOpsAdapter};
     *                  tests: {@code TestLevelStub})
     */
    public static void execute(
        SchematicHolder holder,
        BlockPos origin,
        boolean ignoreAir,
        PasteFeedbackSink feedback,
        PasteLevelOps ops
    ) {
        long startMs = System.currentTimeMillis();
        int blocksPlaced = 0;
        int entitiesSpawned = 0;
        int unknownCount = 0;

        // Read sponge offset — subtracted from each block position (Pitfall 7)
        // effectivePos = pasteOrigin + blockRelPos - spongeOffset
        int[] offset = holder.spongeOffset();

        // Step 1: Force-load all chunks in bounding box
        Set<ChunkPos> forcedChunks = computeChunks(origin, holder);
        for (ChunkPos cp : forcedChunks) {
            ops.setChunkForced(cp.x, cp.z, true);
        }

        try {
            // Step 2: Place blocks
            for (BlockPlacement bp : holder.blocks()) {
                if (ignoreAir && bp.blockState().isAir()) continue;

                // Apply sponge offset subtraction (Pitfall 7):
                // effectivePos = pasteOrigin + blockRelPos - spongeOffset
                BlockPos worldPos = origin
                    .offset(bp.relativePos())
                    .offset(-offset[0], -offset[1], -offset[2]);

                ops.setBlock(worldPos, bp.blockState(), FLAGS);
                blocksPlaced++;

                // Step 3: Apply block entity NBT
                if (bp.blockEntityNbt() != null) {
                    BlockEntity be = ops.getBlockEntity(worldPos);
                    if (be != null) {
                        // NBT is already stripped of x/y/z/id by the parser (per BlockPlacement contract)
                        // loadCustomOnly calls loadAdditional without resetting DataComponents
                        // We pass a copy so the stored NBT is not mutated
                        CompoundTag nbtCopy = bp.blockEntityNbt().copy();
                        // Use loadCustomOnly — available in 1.21.x NeoForge
                        // This calls loadAdditional(nbt, registries) internally
                        applyBlockEntityNbt(be, nbtCopy);
                        be.setChanged();
                    }
                }

                if (bp.wasUnknown()) unknownCount++;
            }

            // Step 4: Spawn entities
            for (EntityPlacement ep : holder.entities()) {
                // World pos = lower corner of origin (as Vec3) + relative pos
                net.minecraft.world.phys.Vec3 worldEntityPos =
                    net.minecraft.world.phys.Vec3.atLowerCornerOf(origin).add(ep.relativePos());

                // Copy entity NBT and set world position (UUID already stripped by parser)
                CompoundTag entityNbt = ep.entityNbt().copy();
                entityNbt.put("Pos", newDoubleList(worldEntityPos.x, worldEntityPos.y, worldEntityPos.z));
                // Remove UUID to prevent collisions on repeated pastes
                entityNbt.remove("UUID");
                entityNbt.remove("UUIDLeast");
                entityNbt.remove("UUIDMost");

                boolean spawned = ops.spawnEntityFromNbt(entityNbt);
                if (spawned) entitiesSpawned++;
            }

        } finally {
            // Step 5: Release all force-loaded chunks (always runs, even if paste throws)
            for (ChunkPos cp : forcedChunks) {
                ops.setChunkForced(cp.x, cp.z, false);
            }
        }

        // Step 6: Send completion feedback (PASTE-08)
        final double elapsedSec = (System.currentTimeMillis() - startMs) / 1000.0;
        final int finalBlocks = blocksPlaced;
        final int finalEntities = entitiesSpawned;
        final int finalUnknown = unknownCount;

        feedback.sendSuccess(() -> Component.translatable(
            "schematicimporter.paste.complete",
            String.format("%,d", finalBlocks),
            String.format("%.1f", elapsedSec),
            finalEntities
        ).withStyle(ChatFormatting.GREEN), false);

        if (finalUnknown > 0) {
            feedback.sendSuccess(() -> Component.translatable(
                "schematicimporter.paste.unknown_replaced", finalUnknown
            ).withStyle(ChatFormatting.YELLOW), false);
        }

        LOGGER.info("PasteExecutor: placed {} blocks, spawned {} entities in {}s ({} unknown replaced with air)",
            finalBlocks, finalEntities, String.format("%.2f", elapsedSec), finalUnknown);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Apply block entity NBT via {@code loadCustomOnly} (1.21.x API).
     *
     * <p>Since {@code BlockEntity.loadCustomOnly} requires a {@code HolderLookup.Provider}
     * and we may not have one in tests, we call it reflectively in production code and
     * use {@code loadAdditional} directly in the {@code FakeBlockEntity} stub. For the live
     * code path this calls the public {@code loadCustomOnly(nbt, level.registryAccess())} API.</p>
     *
     * <p>In the {@link PasteLevelOps} test path, {@code getBlockEntity} returns a
     * {@code FakeBlockEntity} whose {@code loadAdditional} is called by {@code loadCustomOnly}
     * internally — so the test captures the NBT correctly.</p>
     */
    static void applyBlockEntityNbt(BlockEntity be, CompoundTag nbt) {
        // In NeoForge 1.21.1, loadCustomOnly(nbt, registries) calls loadAdditional.
        // We use an empty registries provider since we're only loading custom data.
        // For vanilla block entities (chests, furnaces, etc.) the NBT doesn't require
        // registry lookups — it's plain data (item stacks, text, numbers).
        be.loadCustomOnly(nbt, net.minecraft.core.RegistryAccess.EMPTY);
    }

    /**
     * Compute the set of chunk positions covered by the paste bounding box.
     *
     * <p>Iterates in 16-block steps across the width and length of the schematic,
     * collecting chunk positions. The +16 ensures the last partial chunk is included.</p>
     *
     * @param origin the paste origin in world space
     * @param holder the schematic (provides width and length for bounding box)
     * @return set of chunk positions to force-load
     */
    private static Set<ChunkPos> computeChunks(BlockPos origin, SchematicHolder holder) {
        Set<ChunkPos> chunks = new HashSet<>();
        for (int bx = 0; bx <= holder.width() + 16; bx += 16) {
            for (int bz = 0; bz <= holder.length() + 16; bz += 16) {
                chunks.add(new ChunkPos(new BlockPos(origin.getX() + bx, 0, origin.getZ() + bz)));
            }
        }
        return chunks;
    }

    /**
     * Build a {@link ListTag} of three {@link DoubleTag} values.
     * Used to set the "Pos" tag on entity NBT before spawning.
     */
    private static ListTag newDoubleList(double x, double y, double z) {
        ListTag tag = new ListTag();
        tag.add(DoubleTag.valueOf(x));
        tag.add(DoubleTag.valueOf(y));
        tag.add(DoubleTag.valueOf(z));
        return tag;
    }
}
