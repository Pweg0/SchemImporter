package com.schematicimporter.schematic;

import java.util.List;

/**
 * Immutable, fully-parsed schematic. No raw NBT retained after construction.
 *
 * <p>Both {@code SpongeSchematicParser} and {@code VanillaNbtParser} produce this type.
 * All downstream code (PasteExecutor, commands) only knows about {@code SchematicHolder}.</p>
 *
 * <ul>
 *   <li>{@code width/height/length} — bounding box dimensions in blocks</li>
 *   <li>{@code blocks} — ALL block placements including air (caller filters with --ignore-air)</li>
 *   <li>{@code entities} — entity placements with relative positions</li>
 *   <li>{@code spongeOffset} — the Offset int[3] from a Sponge schematic ([x, y, z]), defaults to {0,0,0}.
 *       During paste: {@code worldPos = pasteOrigin + blockRelPos - spongeOffset} (PITFALL 7).
 *       {@code VanillaNbtParser} always passes {@code new int[]{0,0,0}} — offset concept does not
 *       apply to .nbt files.</li>
 * </ul>
 */
public record SchematicHolder(
        int width,
        int height,
        int length,
        List<BlockPlacement> blocks,
        List<EntityPlacement> entities,
        int[] spongeOffset
) {
    /**
     * Convenience constructor for parsers that have no offset concept (e.g., VanillaNbtParser).
     * Sets {@code spongeOffset} to {@code {0, 0, 0}}.
     */
    public SchematicHolder(int width, int height, int length,
                           List<BlockPlacement> blocks, List<EntityPlacement> entities) {
        this(width, height, length, blocks, entities, new int[]{0, 0, 0});
    }
}
