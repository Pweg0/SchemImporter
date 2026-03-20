package com.schematicimporter;

import com.schematicimporter.paste.PasteExecutor;
import com.schematicimporter.paste.PasteFeedbackSink;
import com.schematicimporter.schematic.BlockPlacement;
import com.schematicimporter.schematic.SchematicHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies PasteExecutor applies block entity NBT correctly after setBlock.
 *
 * <p>After setBlock: getBlockEntity is called, be.load(nbt) is called, be.setChanged() is called.
 * The NBT passed to load must NOT contain "x", "y", "z", or "id".</p>
 */
class BlockEntityNbtTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.init();
    }

    @Test
    void blockEntityLoad_calledAfterSetBlock() {
        // Build a BlockPlacement with blockEntityNbt = {Items: []} (no x/y/z/id)
        CompoundTag beNbt = new CompoundTag();
        beNbt.put("Items", new ListTag());
        beNbt.putString("CustomName", "Test");

        BlockPlacement bp = new BlockPlacement(
            new BlockPos(0, 0, 0),
            Blocks.CHEST.defaultBlockState(),
            beNbt,
            false,
            null
        );
        SchematicHolder holder = new SchematicHolder(1, 1, 1, List.of(bp), List.of());

        TestLevelStub stub = new TestLevelStub();
        stub.registerBlockEntityAt(new BlockPos(0, 0, 0));

        PasteExecutor.execute(holder, new BlockPos(0, 0, 0), false, PasteFeedbackSink.noop(), stub);

        TestLevelStub.FakeBlockEntity fbe = stub.getBlockEntityAt(new BlockPos(0, 0, 0));
        assertNotNull(fbe, "TestLevelStub must provide a FakeBlockEntity at the block position");
        assertTrue(fbe.loadCalled, "be.load() must be called after setBlock for a BlockPlacement with blockEntityNbt");
    }

    @Test
    void blockEntitySetChanged_calledAfterLoad() {
        CompoundTag beNbt = new CompoundTag();
        beNbt.put("Items", new ListTag());

        BlockPlacement bp = new BlockPlacement(
            new BlockPos(0, 0, 0),
            Blocks.CHEST.defaultBlockState(),
            beNbt,
            false,
            null
        );
        SchematicHolder holder = new SchematicHolder(1, 1, 1, List.of(bp), List.of());

        TestLevelStub stub = new TestLevelStub();
        stub.registerBlockEntityAt(new BlockPos(0, 0, 0));

        PasteExecutor.execute(holder, new BlockPos(0, 0, 0), false, PasteFeedbackSink.noop(), stub);

        TestLevelStub.FakeBlockEntity fbe = stub.getBlockEntityAt(new BlockPos(0, 0, 0));
        assertNotNull(fbe, "FakeBlockEntity must exist");
        assertTrue(fbe.setChangedCalled, "be.setChanged() must be called after load()");
    }

    @Test
    void blockEntityNbt_doesNotContainXYZOrId() {
        // Even if NBT comes in without x/y/z/id, verify the NBT passed to load is clean
        // (Tests the "no stripping needed" path — plan says parsers strip before this point)
        CompoundTag beNbt = new CompoundTag();
        beNbt.put("Items", new ListTag());
        beNbt.putString("CustomName", "Test");
        // Do NOT add x/y/z/id — per contract they are stripped by the parser

        BlockPlacement bp = new BlockPlacement(
            new BlockPos(0, 0, 0),
            Blocks.CHEST.defaultBlockState(),
            beNbt,
            false,
            null
        );
        SchematicHolder holder = new SchematicHolder(1, 1, 1, List.of(bp), List.of());

        TestLevelStub stub = new TestLevelStub();
        stub.registerBlockEntityAt(new BlockPos(0, 0, 0));

        PasteExecutor.execute(holder, new BlockPos(0, 0, 0), false, PasteFeedbackSink.noop(), stub);

        TestLevelStub.FakeBlockEntity fbe = stub.getBlockEntityAt(new BlockPos(0, 0, 0));
        assertNotNull(fbe, "FakeBlockEntity must exist");
        assertNotNull(fbe.loadedNbt, "load() must have been called with NBT");
        assertFalse(fbe.loadedNbt.contains("x"), "NBT must not contain 'x'");
        assertFalse(fbe.loadedNbt.contains("y"), "NBT must not contain 'y'");
        assertFalse(fbe.loadedNbt.contains("z"), "NBT must not contain 'z'");
        assertFalse(fbe.loadedNbt.contains("id"), "NBT must not contain 'id'");
    }
}
