package com.schematicimporter.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable record of one block's placement data from a parsed schematic.
 *
 * <ul>
 *   <li>{@code relativePos} — position relative to schematic origin (0,0,0 = bounding-box corner)</li>
 *   <li>{@code blockState} — resolved BlockState, or {@code Blocks.AIR.defaultBlockState()} if unknown</li>
 *   <li>{@code blockEntityNbt} — stripped of x/y/z/id tags; null if this block has no block entity</li>
 *   <li>{@code wasUnknown} — true if the original palette entry could not be resolved</li>
 *   <li>{@code originalPaletteKey} — the raw string from the schematic palette (for warning display)</li>
 * </ul>
 *
 * <p><b>Contract:</b> parsers must strip x/y/z/id from {@code blockEntityNbt} before constructing
 * this record. The executor trusts the record to be clean and calls {@code blockEntity.load(nbt)}
 * directly without further stripping.</p>
 */
public record BlockPlacement(
        BlockPos relativePos,
        BlockState blockState,
        @Nullable CompoundTag blockEntityNbt,
        boolean wasUnknown,
        @Nullable String originalPaletteKey
) {}
