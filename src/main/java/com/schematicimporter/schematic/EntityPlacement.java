package com.schematicimporter.schematic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable record of one entity's placement data from a parsed schematic.
 *
 * <ul>
 *   <li>{@code relativePos} — position relative to schematic origin, in fractional blocks</li>
 *   <li>{@code entityNbt} — entity data with UUID stripped (fresh UUID assigned at spawn time)</li>
 * </ul>
 *
 * <p><b>Contract:</b> parsers must strip the {@code UUID} tag from {@code entityNbt} before
 * constructing this record. The executor generates a fresh UUID when spawning the entity to
 * prevent UUID collisions on repeated pastes of the same schematic.</p>
 */
public record EntityPlacement(
        Vec3 relativePos,
        CompoundTag entityNbt
) {}
