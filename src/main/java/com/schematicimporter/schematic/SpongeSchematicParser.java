package com.schematicimporter.schematic;

import net.minecraft.core.RegistryAccess;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Parses Sponge Schematic (.schem) files (v1/v2/v3) into {@link SchematicHolder}.
 *
 * <p><b>Stub:</b> Full implementation is in Plan 01-02. This stub exists solely to allow
 * Plan 01-03 (VanillaNbtParser) tests to compile while Plan 01-02 is implemented in parallel
 * (both are Wave 2). Public method signatures match what tests expect.</p>
 */
public class SpongeSchematicParser {

    private SpongeSchematicParser() {}

    /**
     * Lazy metadata returned by {@link #peekMetadata(Path)} — no block list constructed.
     *
     * <p><b>Stub type:</b> Full record is delivered in Plan 01-02.</p>
     */
    public record SchematicMetadata(int width, int height, int length) {}

    /**
     * Decode a Protocol Buffer varint-encoded byte array into an array of integers.
     *
     * <p>The Sponge .schem block data array uses MSB-continuation encoding: values 0-127
     * fit in one byte; values 128+ require multiple bytes with the high bit set as a
     * continuation flag.</p>
     *
     * <p><b>Stub implementation:</b> throws {@link UnsupportedOperationException}.
     * Full implementation is delivered in Plan 01-02.</p>
     *
     * @param data   raw varint-encoded bytes from {@code Blocks.Data} tag
     * @param count  expected number of decoded integers
     * @return decoded palette indices array of length {@code count}
     */
    public static int[] decodeVarints(byte[] data, int count) {
        throw new UnsupportedOperationException("SpongeSchematicParser.decodeVarints not yet implemented — see Plan 01-02");
    }

    /**
     * Read only width, height, and length from a .schem file without building the block list.
     *
     * <p><b>Stub implementation:</b> throws {@link UnsupportedOperationException}.
     * Full implementation is delivered in Plan 01-02.</p>
     *
     * @param path path to the .schem file
     */
    public static SchematicMetadata peekMetadata(Path path) throws IOException {
        throw new UnsupportedOperationException("SpongeSchematicParser.peekMetadata not yet implemented — see Plan 01-02");
    }

    /**
     * Parse a .schem file into a {@link SchematicHolder}.
     *
     * <p><b>Stub implementation:</b> throws {@link UnsupportedOperationException}.
     * Full implementation is delivered in Plan 01-02.</p>
     *
     * @param path           path to the .schem file
     * @param registryAccess registry access for block state resolution (e.g. {@code RegistryAccess.EMPTY})
     */
    public static SchematicHolder parse(Path path, RegistryAccess registryAccess) throws IOException {
        throw new UnsupportedOperationException("SpongeSchematicParser.parse not yet implemented — see Plan 01-02");
    }
}
