package com.schematicimporter;

import com.schematicimporter.paste.AsyncPasteTask;
import com.schematicimporter.schematic.BlockPlacement;
import com.schematicimporter.schematic.SchematicHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AsyncPasteTask} progress reporting: formatCount, progressPercent,
 * etaSeconds, and buildProgressComponent.
 */
class AsyncPasteProgressTest {

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

    // ---- formatCount ----

    @Test
    void testFormatCount_under1000() {
        assertEquals("999", AsyncPasteTask.formatCount(999));
        assertEquals("0", AsyncPasteTask.formatCount(0));
        assertEquals("1", AsyncPasteTask.formatCount(1));
        assertEquals("500", AsyncPasteTask.formatCount(500));
    }

    @Test
    void testFormatCount_1K() {
        assertEquals("1.0K", AsyncPasteTask.formatCount(1000));
    }

    @Test
    void testFormatCount_1500() {
        assertEquals("1.5K", AsyncPasteTask.formatCount(1500));
    }

    @Test
    void testFormatCount_1M() {
        assertEquals("1.0M", AsyncPasteTask.formatCount(1_000_000));
    }

    @Test
    void testFormatCount_largeValues() {
        assertEquals("2.5M", AsyncPasteTask.formatCount(2_500_000));
        assertEquals("99.9K", AsyncPasteTask.formatCount(99_900));
    }

    // ---- progressPercent ----

    @Test
    void testProgressPercent_zero() {
        TestLevelStub stub = new TestLevelStub();
        AsyncPasteTask task = new AsyncPasteTask(holderWithBlocks(10), BlockPos.ZERO, false,
            UUID.randomUUID(), Set.of(), stub);

        assertEquals(0, task.progressPercent());
    }

    @Test
    void testProgressPercent_half() {
        TestLevelStub stub = new TestLevelStub();
        AsyncPasteTask task = new AsyncPasteTask(holderWithBlocks(10), BlockPos.ZERO, false,
            UUID.randomUUID(), Set.of(), stub);

        task.tick(5);
        assertEquals(50, task.progressPercent());
    }

    @Test
    void testProgressPercent_full() {
        TestLevelStub stub = new TestLevelStub();
        AsyncPasteTask task = new AsyncPasteTask(holderWithBlocks(10), BlockPos.ZERO, false,
            UUID.randomUUID(), Set.of(), stub);

        task.tick(100);
        assertEquals(100, task.progressPercent());
    }

    @Test
    void testProgressPercent_emptySchematic() {
        TestLevelStub stub = new TestLevelStub();
        AsyncPasteTask task = new AsyncPasteTask(holderWithBlocks(0), BlockPos.ZERO, false,
            UUID.randomUUID(), Set.of(), stub);

        assertEquals(100, task.progressPercent(), "Empty schematic should report 100%");
    }

    // ---- etaSeconds ----

    @Test
    void testEta_beforeAnyBlocks() {
        TestLevelStub stub = new TestLevelStub();
        AsyncPasteTask task = new AsyncPasteTask(holderWithBlocks(10), BlockPos.ZERO, false,
            UUID.randomUUID(), Set.of(), stub);

        assertEquals(-1, task.etaSeconds(), "ETA should be -1 before any blocks are placed");
    }

    // ---- buildProgressComponent ----

    @Test
    void testProgressBar_8chars() {
        TestLevelStub stub = new TestLevelStub();
        // 100 blocks, place ~45 to get ~45%
        AsyncPasteTask task = new AsyncPasteTask(holderWithBlocks(100), BlockPos.ZERO, false,
            UUID.randomUUID(), Set.of(), stub);
        task.tick(45);

        Component component = AsyncPasteTask.buildProgressComponent(task);
        String text = component.getString();

        // Count unicode block chars: U+2588 (full block) and U+2591 (light shade)
        long fullBlocks = text.chars().filter(c -> c == '\u2588').count();
        long lightShade = text.chars().filter(c -> c == '\u2591').count();

        assertEquals(8, fullBlocks + lightShade,
            "Progress bar must contain exactly 8 unicode block/shade characters, got: " + text);

        // At 45%, filled = (45 * 8) / 100 = 3
        assertEquals(3, fullBlocks, "45% should have 3 filled blocks");
        assertEquals(5, lightShade, "45% should have 5 empty blocks");

        // Must contain percentage and separator
        assertTrue(text.contains("45%"), "Should contain '45%', got: " + text);
        assertTrue(text.contains("|"), "Should contain '|' separators, got: " + text);

        // Must contain block counts
        assertTrue(text.contains("45/100"), "Should contain '45/100', got: " + text);
    }
}
