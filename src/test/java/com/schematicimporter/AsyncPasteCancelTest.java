package com.schematicimporter;

import com.schematicimporter.paste.AsyncPasteTask;
import com.schematicimporter.schematic.BlockPlacement;
import com.schematicimporter.schematic.SchematicHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AsyncPasteTask#requestCancel()} behavior.
 */
class AsyncPasteCancelTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.init();
    }

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

    @Test
    void testCancel_stopsOnNextTick() {
        TestLevelStub stub = new TestLevelStub();
        AsyncPasteTask task = new AsyncPasteTask(holderWithBlocks(10), BlockPos.ZERO, false,
            UUID.randomUUID(), Set.of(), stub);

        // Cancel before any tick
        task.requestCancel();

        AsyncPasteTask.TickResult result = task.tick(5);
        assertEquals(AsyncPasteTask.TickResult.CANCELLED, result);
        assertEquals(0, stub.setBlockCalls.size(), "No blocks should be placed after cancel");
        assertEquals(0, task.blocksPlaced());
    }

    @Test
    void testCancel_blocksPlacedBeforeCancelRemain() {
        TestLevelStub stub = new TestLevelStub();
        AsyncPasteTask task = new AsyncPasteTask(holderWithBlocks(10), BlockPos.ZERO, false,
            UUID.randomUUID(), Set.of(), stub);

        // Place 3 blocks first
        AsyncPasteTask.TickResult r1 = task.tick(3);
        assertEquals(AsyncPasteTask.TickResult.CONTINUE, r1);
        assertEquals(3, task.blocksPlaced());

        // Now cancel
        task.requestCancel();

        // Next tick returns CANCELLED with no additional blocks placed
        AsyncPasteTask.TickResult r2 = task.tick(5);
        assertEquals(AsyncPasteTask.TickResult.CANCELLED, r2);
        assertEquals(3, task.blocksPlaced(), "Blocks placed before cancel must remain counted");
        assertEquals(3, stub.setBlockCalls.size(), "No additional setBlock calls after cancel");
    }

    @Test
    void testCancel_onlyAffectsOwnTask() {
        TestLevelStub stub1 = new TestLevelStub();
        TestLevelStub stub2 = new TestLevelStub();

        AsyncPasteTask task1 = new AsyncPasteTask(holderWithBlocks(10), BlockPos.ZERO, false,
            UUID.randomUUID(), Set.of(), stub1);
        AsyncPasteTask task2 = new AsyncPasteTask(holderWithBlocks(10), BlockPos.ZERO, false,
            UUID.randomUUID(), Set.of(), stub2);

        // Cancel task1 only
        task1.requestCancel();

        // task1 is cancelled
        assertEquals(AsyncPasteTask.TickResult.CANCELLED, task1.tick(5));

        // task2 continues normally
        assertEquals(AsyncPasteTask.TickResult.CONTINUE, task2.tick(5));
        assertEquals(5, task2.blocksPlaced());
    }
}
