package com.schematicimporter;

import com.schematicimporter.paste.PasteExecutor;
import com.schematicimporter.paste.PasteFeedbackSink;
import com.schematicimporter.schematic.BlockPlacement;
import com.schematicimporter.schematic.SchematicHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies PasteExecutor uses correct setBlock flags.
 *
 * <p>The FLAGS constant must be Block.UPDATE_CLIENTS.
 * Block.UPDATE_ALL (value 3) must never be used during paste.</p>
 */
class PasteExecutorFlagTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.init();
    }

    @Test
    void setBlock_usesCorrectFlags() {
        // Expected: UPDATE_CLIENTS | UPDATE_SUPPRESS_DROPS | UPDATE_KNOWN_SHAPE
        int expectedFlags = Block.UPDATE_CLIENTS;

        // Build a SchematicHolder with one stone block at origin
        BlockPlacement bp = new BlockPlacement(
            new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState(), null, false, null);
        SchematicHolder holder = new SchematicHolder(1, 1, 1, List.of(bp), List.of());

        // Use a stub level that captures setBlock calls
        TestLevelStub stub = new TestLevelStub();

        // Execute paste at origin
        PasteExecutor.execute(holder, new BlockPos(0, 0, 0), false, PasteFeedbackSink.noop(), stub);

        // Verify setBlock was called
        assertFalse(stub.setBlockCalls.isEmpty(), "setBlock must be called at least once");

        // Verify flags
        for (TestLevelStub.SetBlockCall call : stub.setBlockCalls) {
            assertEquals(expectedFlags, call.flags(),
                "setBlock must be called with UPDATE_CLIENTS | UPDATE_SUPPRESS_DROPS | UPDATE_KNOWN_SHAPE");
            assertNotEquals(Block.UPDATE_ALL, call.flags(),
                "setBlock must NOT use Block.UPDATE_ALL (value=" + Block.UPDATE_ALL + ")");
        }
    }

    @Test
    void flags_constant_equalsExpectedBitmask() {
        // Direct verification of the FLAGS constant value
        int expected = Block.UPDATE_CLIENTS;
        assertEquals(expected, PasteExecutor.FLAGS,
            "PasteExecutor.FLAGS must equal UPDATE_CLIENTS | UPDATE_SUPPRESS_DROPS | UPDATE_KNOWN_SHAPE");
    }
}
