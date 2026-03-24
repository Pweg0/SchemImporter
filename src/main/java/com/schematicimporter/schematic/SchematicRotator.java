package com.schematicimporter.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Rotates a {@link SchematicHolder} by 90, 180, or 270 degrees clockwise (Y-axis).
 *
 * <p>Block positions are transformed using standard 2D rotation on the XZ plane.
 * Block states are rotated via {@link net.minecraft.world.level.block.state.BlockState#rotate(Rotation)},
 * which handles directional properties (facing, axis, etc.) for all vanilla blocks.
 * Entity positions are transformed similarly.</p>
 *
 * <p>Rotation formulas (CW when viewed from above):
 * <ul>
 *   <li>90°:  (x, z) → (length-1-z, x)  — width↔length swap</li>
 *   <li>180°: (x, z) → (width-1-x, length-1-z) — same dimensions</li>
 *   <li>270°: (x, z) → (z, width-1-x)  — width↔length swap</li>
 * </ul>
 * Y coordinate is unchanged for all rotations.</p>
 */
public final class SchematicRotator {

    private SchematicRotator() {}

    /**
     * Rotate a schematic by the given angle.
     *
     * @param holder the source schematic
     * @param rotation the rotation to apply (NONE returns the holder unchanged)
     * @return a new SchematicHolder with rotated blocks, entities, and updated dimensions
     */
    public static SchematicHolder rotate(SchematicHolder holder, Rotation rotation) {
        if (rotation == Rotation.NONE) return holder;

        int oldW = holder.width();
        int oldH = holder.height();
        int oldL = holder.length();

        int newW, newL;
        if (rotation == Rotation.CLOCKWISE_180) {
            newW = oldW;
            newL = oldL;
        } else {
            // 90 or 270: width and length swap
            newW = oldL;
            newL = oldW;
        }

        // Rotate blocks
        List<BlockPlacement> rotatedBlocks = new ArrayList<>(holder.blocks().size());
        for (BlockPlacement bp : holder.blocks()) {
            BlockPos newPos = rotateBlockPos(bp.relativePos(), oldW, oldL, rotation);
            rotatedBlocks.add(new BlockPlacement(
                newPos,
                bp.blockState().rotate(rotation),
                bp.blockEntityNbt(),
                bp.wasUnknown(),
                bp.originalPaletteKey()
            ));
        }

        // Rotate entities
        List<EntityPlacement> rotatedEntities = new ArrayList<>(holder.entities().size());
        for (EntityPlacement ep : holder.entities()) {
            Vec3 newPos = rotateEntityPos(ep.relativePos(), oldW, oldL, rotation);
            rotatedEntities.add(new EntityPlacement(newPos, ep.entityNbt()));
        }

        return new SchematicHolder(newW, oldH, newL, rotatedBlocks, rotatedEntities, holder.spongeOffset());
    }

    /**
     * Rotate a block position on the XZ plane.
     */
    public static BlockPos rotateBlockPos(BlockPos pos, int width, int length, Rotation rotation) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(length - 1 - z, y, x);
            case CLOCKWISE_180 -> new BlockPos(width - 1 - x, y, length - 1 - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z, y, width - 1 - x);
            default -> pos;
        };
    }

    /**
     * Rotate an entity position (fractional) on the XZ plane.
     */
    public static Vec3 rotateEntityPos(Vec3 pos, int width, int length, Rotation rotation) {
        double x = pos.x;
        double y = pos.y;
        double z = pos.z;

        return switch (rotation) {
            case CLOCKWISE_90 -> new Vec3(length - z, y, x);
            case CLOCKWISE_180 -> new Vec3(width - x, y, length - z);
            case COUNTERCLOCKWISE_90 -> new Vec3(z, y, width - x);
            default -> pos;
        };
    }
}
