package com.schematicimporter;

import com.schematicimporter.paste.PasteLevelOps;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test stub implementing {@link PasteLevelOps} that captures calls for assertion.
 *
 * <p>Avoids the need to instantiate a real {@link net.minecraft.server.level.ServerLevel}
 * in unit tests. All calls are recorded and can be inspected after
 * {@link com.schematicimporter.paste.PasteExecutor#execute} returns.</p>
 *
 * <p>Usage:
 * <pre>
 *     TestLevelStub stub = new TestLevelStub();
 *     stub.registerBlockEntityAt(pos);  // if you want getBlockEntity() to return non-null
 *     PasteExecutor.execute(holder, origin, ignoreAir, PasteFeedbackSink.noop(), stub);
 *     assertEquals(expectedFlags, stub.setBlockCalls.get(0).flags());
 * </pre>
 * </p>
 */
public class TestLevelStub implements PasteLevelOps {

    // ---- Captured setBlock calls ----

    /** Immutable record of a single setBlock call. */
    public record SetBlockCall(BlockPos pos, BlockState state, int flags) {}

    /** All setBlock calls in order. */
    public final List<SetBlockCall> setBlockCalls = new ArrayList<>();

    // ---- Block entity simulation ----

    /** Block entities registered for specific positions. */
    private final Map<BlockPos, FakeBlockEntity> blockEntities = new HashMap<>();

    // ---- Entity NBT capture ----

    /**
     * Entity NBTs passed to {@link #spawnEntityFromNbt(CompoundTag)}.
     * These have "Pos" set to world coordinates.
     * Used by EntityPlacementTest to verify coordinate math.
     */
    public final List<CompoundTag> capturedEntityNbts = new ArrayList<>();

    // ---- PasteLevelOps implementation ----

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags) {
        setBlockCalls.add(new SetBlockCall(pos.immutable(), state, flags));
        return true;
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return blockEntities.get(pos);
    }

    @Override
    public void setChunkForced(int chunkX, int chunkZ, boolean force) {
        // No-op in test context — chunk loading not verified in unit tests
    }

    @Override
    public boolean spawnEntityFromNbt(CompoundTag entityNbt) {
        // Capture the NBT for assertion — no real entity creation needed in unit tests
        capturedEntityNbts.add(entityNbt.copy());
        return true;
    }

    // ---- Test helpers ----

    /**
     * Register a {@link FakeBlockEntity} that will be returned by {@code getBlockEntity(pos)}.
     *
     * <p>Call this before {@code PasteExecutor.execute()} to simulate a block entity
     * being present at a position after {@code setBlock}.</p>
     */
    public void registerBlockEntityAt(BlockPos pos) {
        blockEntities.put(pos.immutable(), new FakeBlockEntity());
    }

    /**
     * Get the {@link FakeBlockEntity} registered at a position (for assertion).
     */
    public @Nullable FakeBlockEntity getBlockEntityAt(BlockPos pos) {
        return blockEntities.get(pos);
    }

    // ---- Inner types ----

    /**
     * Fake block entity that records {@code load()} and {@code setChanged()} calls.
     *
     * <p>Extends {@link BlockEntity} using the CHEST type as a concrete instantiatable type.
     * The {@code level} field is null since we are not in a live server context — methods
     * that need it are not called during paste logic.</p>
     */
    public static class FakeBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {

        public boolean loadCalled = false;
        public boolean setChangedCalled = false;
        public @Nullable CompoundTag loadedNbt = null;

        public FakeBlockEntity() {
            super(
                net.minecraft.world.level.block.entity.BlockEntityType.CHEST,
                BlockPos.ZERO,
                net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState()
            );
        }

        @Override
        public void loadAdditional(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries) {
            this.loadCalled = true;
            this.loadedNbt = nbt.copy();
        }

        @Override
        public void setChanged() {
            this.setChangedCalled = true;
        }

        @Override
        protected void saveAdditional(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries) {
            // No-op — test stub only
        }
    }
}
