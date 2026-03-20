package com.schematicimporter.paste;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Narrow interface for world operations performed by {@link PasteExecutor}.
 *
 * <p>This interface exists so that unit tests can provide a test stub without needing to
 * instantiate a real {@link net.minecraft.server.level.ServerLevel} (which requires a running
 * Minecraft server). Production code passes a {@link ServerLevelOpsAdapter} wrapping the live
 * {@code ServerLevel}.</p>
 *
 * <p>Only the methods actually called by {@link PasteExecutor} are declared here — not the
 * entire {@code Level} API.</p>
 */
public interface PasteLevelOps {

    /**
     * Place a block state at the given position.
     *
     * @param pos   world position
     * @param state block state to place
     * @param flags setBlock flags (see {@link net.minecraft.world.level.block.Block})
     * @return true if the block was placed successfully
     */
    boolean setBlock(BlockPos pos, BlockState state, int flags);

    /**
     * Get the block entity at a position, or {@code null} if none.
     *
     * @param pos world position
     * @return the {@link BlockEntity}, or {@code null}
     */
    @Nullable
    BlockEntity getBlockEntity(BlockPos pos);

    /**
     * Force or release a chunk.
     *
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     * @param force  true to force-load, false to release
     */
    void setChunkForced(int chunkX, int chunkZ, boolean force);

    /**
     * Spawn an entity from its NBT data (with "Pos" already set to world coordinates).
     *
     * <p>The implementation is responsible for calling {@code EntityType.create(nbt, level)}
     * and {@code level.addFreshEntity(entity)}. This is a single method to allow test stubs
     * to capture the NBT without needing a live {@code ServerLevel} for entity deserialization.</p>
     *
     * @param entityNbt entity NBT with "Pos" tag set to world coordinates; "UUID" already stripped
     * @return true if an entity was spawned; false if {@code EntityType.create} returned empty
     */
    boolean spawnEntityFromNbt(CompoundTag entityNbt);
}
