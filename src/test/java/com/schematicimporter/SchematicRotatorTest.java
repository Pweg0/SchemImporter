package com.schematicimporter;

import com.schematicimporter.schematic.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SchematicRotator}: position transforms, dimension swaps,
 * block state rotation, and entity position rotation.
 */
class SchematicRotatorTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.init();
    }

    // ---- Block position rotation ----

    @Test
    void testRotate90_blockPosition() {
        // width=4, length=6, block at (2, 3, 1)
        // 90° CW: (length-1-z, y, x) = (6-1-1, 3, 2) = (4, 3, 2)
        BlockPos result = SchematicRotator.rotateBlockPos(new BlockPos(2, 3, 1), 4, 6, Rotation.CLOCKWISE_90);
        assertEquals(new BlockPos(4, 3, 2), result);
    }

    @Test
    void testRotate180_blockPosition() {
        // width=4, length=6, block at (2, 3, 1)
        // 180°: (width-1-x, y, length-1-z) = (3-2, 3, 5-1) = (1, 3, 4)
        BlockPos result = SchematicRotator.rotateBlockPos(new BlockPos(2, 3, 1), 4, 6, Rotation.CLOCKWISE_180);
        assertEquals(new BlockPos(1, 3, 4), result);
    }

    @Test
    void testRotate270_blockPosition() {
        // width=4, length=6, block at (2, 3, 1)
        // 270° CW: (z, y, width-1-x) = (1, 3, 4-1-2) = (1, 3, 1)
        BlockPos result = SchematicRotator.rotateBlockPos(new BlockPos(2, 3, 1), 4, 6, Rotation.COUNTERCLOCKWISE_90);
        assertEquals(new BlockPos(1, 3, 1), result);
    }

    @Test
    void testRotateNone_blockPosition() {
        BlockPos pos = new BlockPos(2, 3, 1);
        BlockPos result = SchematicRotator.rotateBlockPos(pos, 4, 6, Rotation.NONE);
        assertEquals(pos, result);
    }

    // ---- Dimension swaps ----

    @Test
    void testRotate90_dimensionsSwap() {
        SchematicHolder holder = new SchematicHolder(4, 5, 6, List.of(), List.of());
        SchematicHolder rotated = SchematicRotator.rotate(holder, Rotation.CLOCKWISE_90);
        assertEquals(6, rotated.width(), "90° rotation: new width = old length");
        assertEquals(5, rotated.height(), "height unchanged");
        assertEquals(4, rotated.length(), "90° rotation: new length = old width");
    }

    @Test
    void testRotate180_dimensionsSame() {
        SchematicHolder holder = new SchematicHolder(4, 5, 6, List.of(), List.of());
        SchematicHolder rotated = SchematicRotator.rotate(holder, Rotation.CLOCKWISE_180);
        assertEquals(4, rotated.width(), "180° rotation: width unchanged");
        assertEquals(5, rotated.height(), "height unchanged");
        assertEquals(6, rotated.length(), "180° rotation: length unchanged");
    }

    @Test
    void testRotate270_dimensionsSwap() {
        SchematicHolder holder = new SchematicHolder(4, 5, 6, List.of(), List.of());
        SchematicHolder rotated = SchematicRotator.rotate(holder, Rotation.COUNTERCLOCKWISE_90);
        assertEquals(6, rotated.width(), "270° rotation: new width = old length");
        assertEquals(5, rotated.height(), "height unchanged");
        assertEquals(4, rotated.length(), "270° rotation: new length = old width");
    }

    // ---- Full rotation roundtrip ----

    @Test
    void testRotate360_isIdentity() {
        BlockPlacement bp = new BlockPlacement(
            new BlockPos(2, 1, 3), Blocks.STONE.defaultBlockState(), null, false, null);
        SchematicHolder holder = new SchematicHolder(5, 4, 7, List.of(bp), List.of());

        // Rotate 90° four times = identity
        SchematicHolder r = holder;
        for (int i = 0; i < 4; i++) {
            r = SchematicRotator.rotate(r, Rotation.CLOCKWISE_90);
        }

        assertEquals(holder.width(), r.width());
        assertEquals(holder.height(), r.height());
        assertEquals(holder.length(), r.length());
        assertEquals(1, r.blocks().size());
        assertEquals(bp.relativePos(), r.blocks().get(0).relativePos(),
            "4x 90° rotation should return to original position");
    }

    // ---- Block state rotation ----

    @Test
    void testBlockStateRotation_directionalBlock() {
        // OAK_STAIRS has a 'facing' property — rotation should change it
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState();
        BlockPlacement bp = new BlockPlacement(new BlockPos(0, 0, 0), stairs, null, false, null);
        SchematicHolder holder = new SchematicHolder(1, 1, 1, List.of(bp), List.of());

        SchematicHolder rotated = SchematicRotator.rotate(holder, Rotation.CLOCKWISE_90);
        BlockState rotatedState = rotated.blocks().get(0).blockState();

        // The state should be different after rotation (facing changed)
        assertEquals(stairs.rotate(Rotation.CLOCKWISE_90), rotatedState,
            "Block state must be rotated via BlockState.rotate()");
    }

    // ---- Entity position rotation ----

    @Test
    void testRotate90_entityPosition() {
        // width=4, length=6, entity at (1.5, 0.0, 2.5)
        // 90° CW: (length-z, y, x) = (6-2.5, 0, 1.5) = (3.5, 0, 1.5)
        Vec3 result = SchematicRotator.rotateEntityPos(new Vec3(1.5, 0.0, 2.5), 4, 6, Rotation.CLOCKWISE_90);
        assertEquals(3.5, result.x, 0.001);
        assertEquals(0.0, result.y, 0.001);
        assertEquals(1.5, result.z, 0.001);
    }

    @Test
    void testRotate90_entityInHolder() {
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putString("id", "minecraft:armor_stand");
        EntityPlacement ep = new EntityPlacement(new Vec3(1.5, 0.0, 2.5), entityNbt);
        SchematicHolder holder = new SchematicHolder(4, 3, 6, List.of(), List.of(ep));

        SchematicHolder rotated = SchematicRotator.rotate(holder, Rotation.CLOCKWISE_90);
        assertEquals(1, rotated.entities().size());
        Vec3 pos = rotated.entities().get(0).relativePos();
        assertEquals(3.5, pos.x, 0.001);
        assertEquals(0.0, pos.y, 0.001);
        assertEquals(1.5, pos.z, 0.001);
    }

    // ---- Rotation.NONE passthrough ----

    @Test
    void testRotateNone_returnsSameHolder() {
        SchematicHolder holder = new SchematicHolder(4, 5, 6, List.of(), List.of());
        SchematicHolder result = SchematicRotator.rotate(holder, Rotation.NONE);
        assertSame(holder, result, "NONE rotation should return the same object");
    }
}
