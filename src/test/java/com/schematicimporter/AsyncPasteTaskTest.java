package com.schematicimporter;

import com.schematicimporter.paste.AsyncPasteTask;
import com.schematicimporter.schematic.BlockPlacement;
import com.schematicimporter.schematic.EntityPlacement;
import com.schematicimporter.schematic.SchematicHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Core unit tests for {@link AsyncPasteTask}: batch drain, completion,
 * block entity NBT, entity placement, and air filtering.
 */
class AsyncPasteTaskTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.init();
    }

    // ---- Helper ----

    private static SchematicHolder holderWithBlocks(int count) {
        List<BlockPlacement> blocks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            blocks.add(new BlockPlacement(
                new BlockPos(i, 0, 0),
                Blocks.STONE.defaultBlockState(),
                null, false, null));
        }
        return new SchematicHolder(count, 1, 1, blocks, List.of());
    }

    private AsyncPasteTask makeTask(SchematicHolder holder, boolean ignoreAir, TestLevelStub stub) {
        return new AsyncPasteTask(holder, BlockPos.ZERO, ignoreAir,
            UUID.randomUUID(), Set.of(), stub);
    }

    // ---- Tests ----

    @Test
    void testBatchDrain_exactBatchSize() {
        TestLevelStub stub = new TestLevelStub();
        AsyncPasteTask task = makeTask(holderWithBlocks(10), false, stub);

        AsyncPasteTask.TickResult result = task.tick(3);

        assertEquals(AsyncPasteTask.TickResult.CONTINUE, result);
        assertEquals(3, stub.setBlockCalls.size());
        assertEquals(3, task.blocksPlaced());
    }

    @Test
    void testBatchDrain_allBlocksInOneTick() {
        TestLevelStub stub = new TestLevelStub();
        AsyncPasteTask task = makeTask(holderWithBlocks(5), false, stub);

        AsyncPasteTask.TickResult result = task.tick(100);

        assertEquals(AsyncPasteTask.TickResult.DONE, result);
        assertEquals(5, stub.setBlockCalls.size());
        assertEquals(5, task.blocksPlaced());
    }

    @Test
    void testBatchDrain_emptySchematic() {
        TestLevelStub stub = new TestLevelStub();
        AsyncPasteTask task = makeTask(holderWithBlocks(0), false, stub);

        AsyncPasteTask.TickResult result = task.tick(100);

        assertEquals(AsyncPasteTask.TickResult.DONE, result);
        assertEquals(0, stub.setBlockCalls.size());
    }

    @Test
    void testBatchDrain_multipleTicks() {
        TestLevelStub stub = new TestLevelStub();
        AsyncPasteTask task = makeTask(holderWithBlocks(10), false, stub);

        assertEquals(AsyncPasteTask.TickResult.CONTINUE, task.tick(3)); // 3 placed
        assertEquals(3, task.blocksPlaced());

        assertEquals(AsyncPasteTask.TickResult.CONTINUE, task.tick(3)); // 6 placed
        assertEquals(6, task.blocksPlaced());

        assertEquals(AsyncPasteTask.TickResult.CONTINUE, task.tick(3)); // 9 placed
        assertEquals(9, task.blocksPlaced());

        assertEquals(AsyncPasteTask.TickResult.DONE, task.tick(3));     // 10 placed (only 1 remaining)
        assertEquals(10, task.blocksPlaced());
        assertEquals(10, stub.setBlockCalls.size());
    }

    @Test
    void testBlockEntityNbt_appliedDuringTick() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("CustomName", "TestChest");

        BlockPlacement bp = new BlockPlacement(
            new BlockPos(0, 0, 0),
            Blocks.CHEST.defaultBlockState(),
            nbt, false, null);
        SchematicHolder holder = new SchematicHolder(1, 1, 1, List.of(bp), List.of());

        TestLevelStub stub = new TestLevelStub();
        // Register a fake block entity at the world position so getBlockEntity returns non-null
        stub.registerBlockEntityAt(new BlockPos(0, 0, 0));

        AsyncPasteTask task = makeTask(holder, false, stub);
        AsyncPasteTask.TickResult result = task.tick(100);

        assertEquals(AsyncPasteTask.TickResult.DONE, result);
        TestLevelStub.FakeBlockEntity fbe = stub.getBlockEntityAt(new BlockPos(0, 0, 0));
        assertNotNull(fbe);
        assertTrue(fbe.loadCalled, "Block entity load must be called when blockEntityNbt is present");
        assertTrue(fbe.setChangedCalled, "setChanged must be called after loading block entity NBT");
    }

    @Test
    void testEntities_placedAfterAllBlocks() {
        // 2 blocks + 1 entity
        List<BlockPlacement> blocks = new ArrayList<>();
        blocks.add(new BlockPlacement(new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState(), null, false, null));
        blocks.add(new BlockPlacement(new BlockPos(1, 0, 0), Blocks.STONE.defaultBlockState(), null, false, null));

        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putString("id", "minecraft:armor_stand");
        EntityPlacement entity = new EntityPlacement(new Vec3(0.5, 0.0, 0.5), entityNbt);

        SchematicHolder holder = new SchematicHolder(2, 1, 1, blocks, List.of(entity));
        TestLevelStub stub = new TestLevelStub();
        AsyncPasteTask task = makeTask(holder, false, stub);

        // First tick: place 1 block — entity should NOT be spawned yet
        AsyncPasteTask.TickResult r1 = task.tick(1);
        assertEquals(AsyncPasteTask.TickResult.CONTINUE, r1);
        assertEquals(0, stub.capturedEntityNbts.size(), "Entities must not be spawned while blocks remain");
        assertEquals(0, task.entitiesSpawned());

        // Second tick: place remaining block — entity should now be spawned (DONE)
        AsyncPasteTask.TickResult r2 = task.tick(1);
        assertEquals(AsyncPasteTask.TickResult.DONE, r2);
        assertEquals(1, stub.capturedEntityNbts.size(), "Entity must be spawned when all blocks are placed");
        assertEquals(1, task.entitiesSpawned());
    }

    @Test
    void testIgnoreAir_filteredAtConstruction() {
        List<BlockPlacement> blocks = new ArrayList<>();
        blocks.add(new BlockPlacement(new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState(), null, false, null));
        blocks.add(new BlockPlacement(new BlockPos(1, 0, 0), Blocks.AIR.defaultBlockState(), null, false, null));
        blocks.add(new BlockPlacement(new BlockPos(2, 0, 0), Blocks.STONE.defaultBlockState(), null, false, null));
        blocks.add(new BlockPlacement(new BlockPos(3, 0, 0), Blocks.AIR.defaultBlockState(), null, false, null));
        blocks.add(new BlockPlacement(new BlockPos(4, 0, 0), Blocks.STONE.defaultBlockState(), null, false, null));

        SchematicHolder holder = new SchematicHolder(5, 1, 1, blocks, List.of());
        TestLevelStub stub = new TestLevelStub();

        // ignoreAir = true: should filter out 2 air blocks
        AsyncPasteTask task = new AsyncPasteTask(holder, BlockPos.ZERO, true,
            UUID.randomUUID(), Set.of(), stub);

        assertEquals(3, task.totalBlocks(), "Air blocks should be filtered at construction");

        // Place all — only 3 setBlock calls
        AsyncPasteTask.TickResult result = task.tick(100);
        assertEquals(AsyncPasteTask.TickResult.DONE, result);
        assertEquals(3, stub.setBlockCalls.size());
    }
}
