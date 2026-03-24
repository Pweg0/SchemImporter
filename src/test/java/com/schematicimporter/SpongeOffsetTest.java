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
 * Verifies sponge offset is IGNORED for predictable placement (Phase 1 hotfix).
 *
 * <p>The Sponge Offset tag is parsed but intentionally not applied during paste.
 * The structure always anchors at the player's feet (pasteOrigin + blockRelPos).
 * This avoids confusing placement behavior caused by inconsistent offset values
 * across different schematic editors.</p>
 *
 * <p>Formula: worldPos = pasteOrigin + blockRelPos (offset ignored)</p>
 */
class SpongeOffsetTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.init();
    }

    @Test
    void spongeOffset_ignoredForPredictablePlacement() {
        // origin=(100,64,200), relPos=(3,1,5), spongeOffset={-2,0,-3}
        // Offset is IGNORED: worldPos = origin + relPos = (103, 65, 205)
        BlockPos origin = new BlockPos(100, 64, 200);
        BlockPos relPos = new BlockPos(3, 1, 5);
        int[] spongeOffset = {-2, 0, -3};

        BlockPlacement bp = new BlockPlacement(relPos, Blocks.STONE.defaultBlockState(), null, false, null);
        SchematicHolder holder = new SchematicHolder(4, 2, 6, List.of(bp), List.of(), spongeOffset);

        TestLevelStub stub = new TestLevelStub();
        PasteExecutor.execute(holder, origin, false, PasteFeedbackSink.noop(), stub);

        assertFalse(stub.setBlockCalls.isEmpty(), "setBlock must be called");

        BlockPos actualWorldPos = stub.setBlockCalls.get(0).pos();
        assertEquals(103, actualWorldPos.getX(), "worldX = 100+3 = 103 (offset ignored)");
        assertEquals(65, actualWorldPos.getY(), "worldY = 64+1 = 65 (offset ignored)");
        assertEquals(205, actualWorldPos.getZ(), "worldZ = 200+5 = 205 (offset ignored)");
    }

    @Test
    void zeroSpongeOffset_producesIdentityBehavior() {
        BlockPos origin = new BlockPos(100, 64, 200);
        BlockPos relPos = new BlockPos(3, 1, 5);
        int[] spongeOffset = {0, 0, 0};

        BlockPlacement bp = new BlockPlacement(relPos, Blocks.STONE.defaultBlockState(), null, false, null);
        SchematicHolder holder = new SchematicHolder(4, 2, 6, List.of(bp), List.of(), spongeOffset);

        TestLevelStub stub = new TestLevelStub();
        PasteExecutor.execute(holder, origin, false, PasteFeedbackSink.noop(), stub);

        assertFalse(stub.setBlockCalls.isEmpty(), "setBlock must be called");

        BlockPos actualWorldPos = stub.setBlockCalls.get(0).pos();
        assertEquals(103, actualWorldPos.getX(), "worldX = 100+3+0 = 103");
        assertEquals(65, actualWorldPos.getY(), "worldY = 64+1+0 = 65");
        assertEquals(205, actualWorldPos.getZ(), "worldZ = 200+5+0 = 205");
    }

    @Test
    void spongeOffset_formulaDirectly_offsetIgnored() {
        BlockPos origin = new BlockPos(100, 64, 200);
        BlockPos relPos = new BlockPos(3, 1, 5);

        // Current formula: worldPos = origin.offset(relPos) — offset is NOT applied
        BlockPos worldPos = origin.offset(relPos);

        assertEquals(103, worldPos.getX(), "100+3=103");
        assertEquals(65, worldPos.getY(), "64+1=65");
        assertEquals(205, worldPos.getZ(), "200+5=205");
    }
}
