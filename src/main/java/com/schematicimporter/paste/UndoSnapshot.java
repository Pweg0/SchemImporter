package com.schematicimporter.paste;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;

/**
 * Immutable snapshot of the world state before a paste operation.
 *
 * <p>Stores the original block states and block entity NBT for every position
 * that was modified by a paste. Used by {@code /schem undo} to restore
 * the world to its previous state.</p>
 */
public record UndoSnapshot(
    List<BlockRecord> blocks,
    Set<ChunkPos> affectedChunks,
    int totalBlocks
) {

    /**
     * A single block's state before it was overwritten by a paste.
     */
    public record BlockRecord(
        BlockPos worldPos,
        BlockState originalState,
        @Nullable CompoundTag originalBlockEntityNbt
    ) {}
}
