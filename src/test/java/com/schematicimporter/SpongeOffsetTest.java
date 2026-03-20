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
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies sponge offset subtraction formula (Pitfall 7).
 *
 * <p>Formula: worldPos = pasteOrigin + blockRelPos - spongeOffset</p>
 * <p>Example: origin=(100,64,200), relPos=(3,1,5), spongeOffset={2,0,3}
 *    → worldPos = (101, 65, 202)</p>
 */
class SpongeOffsetTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.init();
    }

    @Test
    void spongeOffset_subtractedFromWorldPos() {
        // origin=(100,64,200), relPos=(3,1,5), spongeOffset={2,0,3}
        // expected worldPos = (100+3-2, 64+1-0, 200+5-3) = (101, 65, 202)
        BlockPos origin = new BlockPos(100, 64, 200);
        BlockPos relPos = new BlockPos(3, 1, 5);
        int[] spongeOffset = {2, 0, 3};

        BlockPlacement bp = new BlockPlacement(relPos, Blocks.STONE.defaultBlockState(), null, false, null);
        SchematicHolder holder = new SchematicHolder(4, 2, 6, List.of(bp), List.of(), spongeOffset);

        TestLevelStub stub = new TestLevelStub();
        PasteExecutor.execute(holder, origin, false, PasteFeedbackSink.noop(), stub);

        assertFalse(stub.setBlockCalls.isEmpty(), "setBlock must be called");

        BlockPos actualWorldPos = stub.setBlockCalls.get(0).pos();
        assertEquals(101, actualWorldPos.getX(), "worldX = 100+3-2 = 101");
        assertEquals(65, actualWorldPos.getY(), "worldY = 64+1-0 = 65");
        assertEquals(202, actualWorldPos.getZ(), "worldZ = 200+5-3 = 202");
    }

    @Test
    void zeroSpongeOffset_producesIdentityBehavior() {
        // When spongeOffset={0,0,0}: worldPos = origin + relPos (no change — backward compatible)
        BlockPos origin = new BlockPos(100, 64, 200);
        BlockPos relPos = new BlockPos(3, 1, 5);
        int[] spongeOffset = {0, 0, 0};

        BlockPlacement bp = new BlockPlacement(relPos, Blocks.STONE.defaultBlockState(), null, false, null);
        SchematicHolder holder = new SchematicHolder(4, 2, 6, List.of(bp), List.of(), spongeOffset);

        TestLevelStub stub = new TestLevelStub();
        PasteExecutor.execute(holder, origin, false, PasteFeedbackSink.noop(), stub);

        assertFalse(stub.setBlockCalls.isEmpty(), "setBlock must be called");

        BlockPos actualWorldPos = stub.setBlockCalls.get(0).pos();
        assertEquals(103, actualWorldPos.getX(), "worldX = 100+3-0 = 103");
        assertEquals(65, actualWorldPos.getY(), "worldY = 64+1-0 = 65");
        assertEquals(205, actualWorldPos.getZ(), "worldZ = 200+5-0 = 205");
    }

    @Test
    void spongeOffset_formulaDirectly() {
        // Pure formula verification without executor
        BlockPos origin = new BlockPos(100, 64, 200);
        BlockPos relPos = new BlockPos(3, 1, 5);
        int[] offset = {2, 0, 3};

        // worldPos = origin.offset(relPos).offset(-offset[0], -offset[1], -offset[2])
        BlockPos worldPos = origin.offset(relPos).offset(-offset[0], -offset[1], -offset[2]);

        assertEquals(101, worldPos.getX(), "100+3-2=101");
        assertEquals(65, worldPos.getY(), "64+1-0=65");
        assertEquals(202, worldPos.getZ(), "200+5-3=202");
    }
}
