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
 * Verifies PASTE-02: blocksPerTick is read live per tick (passed as parameter),
 * not cached at task construction time.
 */
class AsyncPasteRateTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.init();
    }

    @Test
    void testVariableBatchSize_differentRatesPerTick() {
        // 20 blocks total
        List<BlockPlacement> blocks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            blocks.add(new BlockPlacement(
                new BlockPos(i, 0, 0),
                Blocks.STONE.defaultBlockState(),
                null, false, null));
        }
        SchematicHolder holder = new SchematicHolder(20, 1, 1, blocks, List.of());
        TestLevelStub stub = new TestLevelStub();
        AsyncPasteTask task = new AsyncPasteTask(holder, BlockPos.ZERO, false,
            UUID.randomUUID(), Set.of(), stub);

        // Tick 1: batch size 3
        AsyncPasteTask.TickResult r1 = task.tick(3);
        assertEquals(AsyncPasteTask.TickResult.CONTINUE, r1);
        assertEquals(3, task.blocksPlaced());

        // Tick 2: batch size 10 (simulating config change)
        AsyncPasteTask.TickResult r2 = task.tick(10);
        assertEquals(AsyncPasteTask.TickResult.CONTINUE, r2);
        assertEquals(13, task.blocksPlaced());

        // Tick 3: batch size 100 (more than remaining)
        AsyncPasteTask.TickResult r3 = task.tick(100);
        assertEquals(AsyncPasteTask.TickResult.DONE, r3);
        assertEquals(20, task.blocksPlaced());
    }
}
