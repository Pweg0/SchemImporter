package com.schematicimporter;

import com.schematicimporter.paste.PasteExecutor;
import com.schematicimporter.paste.PasteFeedbackSink;
import com.schematicimporter.schematic.BlockPlacement;
import com.schematicimporter.schematic.SchematicHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that PasteExecutor skips air blocks when ignoreAir=true.
 *
 * <p>Given: 3 blocks [stone, air, stone]. When ignoreAir=true: 2 setBlock calls.
 * When ignoreAir=false: 3 setBlock calls.</p>
 */
class IgnoreAirTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.init();
    }

    @Test
    void ignoreAirTrue_skipsAirBlocks() {
        // Build: stone at (0,0,0), air at (1,0,0), stone at (2,0,0)
        List<BlockPlacement> placements = List.of(
            new BlockPlacement(new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState(), null, false, null),
            new BlockPlacement(new BlockPos(1, 0, 0), Blocks.AIR.defaultBlockState(), null, false, null),
            new BlockPlacement(new BlockPos(2, 0, 0), Blocks.STONE.defaultBlockState(), null, false, null)
        );
        SchematicHolder holder = new SchematicHolder(3, 1, 1, placements, List.of());

        TestLevelStub stub = new TestLevelStub();
        PasteExecutor.execute(holder, new BlockPos(0, 0, 0), true, PasteFeedbackSink.noop(), stub);

        // Only 2 non-air blocks should be placed
        assertEquals(2, stub.setBlockCalls.size(),
            "With ignoreAir=true, only 2 non-air blocks should be placed");
    }

    @Test
    void ignoreAirFalse_placesAllBlocks() {
        // Same 3 blocks but ignoreAir=false — all 3 should be placed
        List<BlockPlacement> placements = List.of(
            new BlockPlacement(new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState(), null, false, null),
            new BlockPlacement(new BlockPos(1, 0, 0), Blocks.AIR.defaultBlockState(), null, false, null),
            new BlockPlacement(new BlockPos(2, 0, 0), Blocks.STONE.defaultBlockState(), null, false, null)
        );
        SchematicHolder holder = new SchematicHolder(3, 1, 1, placements, List.of());

        TestLevelStub stub = new TestLevelStub();
        PasteExecutor.execute(holder, new BlockPos(0, 0, 0), false, PasteFeedbackSink.noop(), stub);

        // All 3 blocks (including air) should be placed
        assertEquals(3, stub.setBlockCalls.size(),
            "With ignoreAir=false, all 3 blocks (including air) should be placed");
    }
}
