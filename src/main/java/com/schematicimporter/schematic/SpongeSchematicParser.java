package com.schematicimporter.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parses Sponge Schematic (.schem) files (versions 2 and 3) into {@link SchematicHolder}.
 *
 * <p><b>File format overview</b> (Sponge Schematic Specification):
 * <pre>
 * root CompoundTag {
 *   Version:  int (2 or 3)
 *   Width:    short
 *   Height:   short
 *   Length:   short
 *   Offset:   int[3] (optional, default {0,0,0})
 *   Blocks: CompoundTag {
 *     Palette:     CompoundTag { "blockStateString" -> paletteIndex:int, ... }
 *     Data:        byte[] (varint-encoded palette indices, one per block)
 *     BlockEntities: ListTag of CompoundTag with "Pos":int[3] and block entity data
 *   }
 *   Entities: ListTag of CompoundTag with "Pos":double[3] and entity data
 * }
 * </pre>
 * </p>
 *
 * <p><b>Critical correctness:</b> Block data uses Protocol Buffer varint encoding, NOT
 * one-byte-per-block. Values 0-127 encode in a single byte; values 128+ require two or
 * more bytes with the MSB as a continuation flag. Any schematic with 128+ unique block
 * states will silently corrupt if you read a single byte per palette index. The algorithm
 * in {@link #decodeVarints(byte[], int)} is the only correct approach.</p>
 *
 * <p><b>Coordinate formula:</b> During paste, world position =
 * {@code pasteOrigin + blockRelPos - spongeOffset} (PITFALL 7). The subtraction of
 * {@code spongeOffset} happens in {@code PasteExecutor}, NOT in this parser.</p>
 */
public class SpongeSchematicParser {

    private static final Logger LOGGER = LogManager.getLogger(SpongeSchematicParser.class);

    private SpongeSchematicParser() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Lazy metadata returned by {@link #peekMetadata(Path)}.
     * Reading only Width/Height/Length avoids constructing the full block list.
     */
    public record SchematicMetadata(int width, int height, int length) {}

    /**
     * Read only the dimensions from a {@code .schem} file without building the block list.
     *
     * <p>Used by {@code /schem list} to display dimensions cheaply. Does not parse
     * {@code Blocks.Data} or the palette.</p>
     *
     * @param path path to the compressed {@code .schem} file
     * @return metadata record with width, height, length
     * @throws IOException if the file cannot be read or is not valid compressed NBT
     */
    public static SchematicMetadata peekMetadata(Path path) throws IOException {
        CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        int width  = root.getShort("Width");
        int height = root.getShort("Height");
        int length = root.getShort("Length");
        return new SchematicMetadata(width, height, length);
    }

    /**
     * Fully parse a Sponge {@code .schem} file into a {@link SchematicHolder}.
     *
     * <p>Supports format versions 2 and 3. Both are handled identically as they use the
     * same NBT structure for the data this parser reads.</p>
     *
     * <p>Block states are resolved using {@code BuiltInRegistries.BLOCK} (available after
     * {@code Bootstrap.bootStrap()} has been called). The {@code registryAccess} parameter
     * is accepted for API symmetry with future extensions (e.g., datapack blocks) but is
     * not used in the current implementation.</p>
     *
     * @param path           path to the compressed {@code .schem} file
     * @param registryAccess registry access (accepted for API compatibility; vanilla block
     *                       lookup uses {@code BuiltInRegistries.BLOCK} directly)
     * @return fully parsed {@link SchematicHolder}
     * @throws IOException if the file cannot be read, is not valid compressed NBT, or
     *                     the file format is not recognised as a Sponge schematic
     */
    public static SchematicHolder parse(Path path, RegistryAccess registryAccess) throws IOException {
        CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        return parseFromCompoundTag(root);
    }

    /**
     * Decode a Protocol Buffer varint-encoded byte array into an array of integers.
     *
     * <p><b>Algorithm:</b> The MSB of each byte is a continuation flag. Bits 0-6 are
     * data bits. Accumulate data bits with 7-bit shifts until a byte with MSB clear is
     * encountered (end of current value). This handles values up to 2^28 - 1 correctly
     * (more than enough for any palette index).</p>
     *
     * <p>This method is {@code public static} so {@code VarintDecoderTest} can test it
     * directly as a unit (without needing a real .schem file).</p>
     *
     * @param data  raw varint-encoded bytes from the {@code Blocks.Data} NBT tag
     * @param count expected number of decoded integers (must equal width * height * length)
     * @return array of {@code count} decoded palette indices
     */
    public static int[] decodeVarints(byte[] data, int count) {
        int[] paletteIndices = new int[count];
        int i = 0, bytePos = 0;
        while (bytePos < data.length) {
            int value = 0, shift = 0;
            byte b;
            do {
                b = data[bytePos++];
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            paletteIndices[i++] = value;
        }
        return paletteIndices;
    }

    // =========================================================================
    // Internal parsing
    // =========================================================================

    /**
     * Parse a Sponge schematic from an already-decompressed root {@link CompoundTag}.
     *
     * <p>Extracted to allow future unit tests to bypass file I/O if needed.</p>
     */
    static SchematicHolder parseFromCompoundTag(CompoundTag root) {
        // Handle "Schematic" wrapper tag if present (some tools wrap the root)
        if (root.contains("Schematic")) {
            root = root.getCompound("Schematic");
        }

        // 1. Read dimensions
        int width  = root.getShort("Width");
        int height = root.getShort("Height");
        int length = root.getShort("Length");
        int totalBlocks = width * height * length;

        // 2. Read optional Offset [x, y, z] — default {0,0,0} if not present (PITFALL 7)
        int[] offset = root.contains("Offset") ? root.getIntArray("Offset") : new int[]{0, 0, 0};

        // 3. Detect format version and locate palette + block data
        //    v3: Palette and Data inside "Blocks" compound
        //    v2: Palette at root level, block data key is "BlockData" (not "Data")
        CompoundTag paletteNbt;
        byte[] data;
        CompoundTag blocksTag;

        if (root.contains("Blocks", Tag.TAG_COMPOUND)) {
            // Sponge Schematic v3
            blocksTag = root.getCompound("Blocks");
            paletteNbt = blocksTag.getCompound("Palette");
            data = blocksTag.getByteArray("Data");
            LOGGER.debug("SpongeSchematicParser: Detected v3 format");
        } else {
            // Sponge Schematic v2 — palette and block data at root level
            blocksTag = root;
            paletteNbt = root.getCompound("Palette");
            data = root.getByteArray("BlockData");
            LOGGER.debug("SpongeSchematicParser: Detected v2 format");
        }

        // 4. Build palette
        PaletteEntry[] palette = buildPalette(paletteNbt);
        // Log palette stats at INFO level for debugging
        int knownCount = 0, unknownPaletteCount = 0;
        for (PaletteEntry pe : palette) {
            if (pe.wasUnknown) unknownPaletteCount++;
            else knownCount++;
        }
        LOGGER.info("SpongeSchematicParser: Palette has {} entries ({} known, {} unknown), data has {} bytes for {} blocks",
            palette.length, knownCount, unknownPaletteCount, data.length, totalBlocks);

        // 5. Decode varint block data
        int[] paletteIndices = decodeVarints(data, totalBlocks);

        // 6. Build block placements from flat array
        //    Flat index i -> x = i % width, z = (i / width) % length, y = i / (width * length)
        List<BlockPlacement> blocks = new ArrayList<>(totalBlocks);
        for (int i = 0; i < totalBlocks; i++) {
            int x = i % width;
            int z = (i / width) % length;
            int y = i / (width * length);
            BlockPos relPos = new BlockPos(x, y, z);

            int idx = paletteIndices[i];
            PaletteEntry entry = (idx >= 0 && idx < palette.length) ? palette[idx] : null;

            BlockState blockState;
            boolean wasUnknown;
            String originalKey;

            if (entry == null || entry.wasUnknown) {
                blockState = Blocks.AIR.defaultBlockState();
                wasUnknown = true;
                originalKey = (entry != null) ? entry.originalKey : null;
            } else {
                blockState = entry.blockState;
                wasUnknown = false;
                originalKey = null;
            }

            blocks.add(new BlockPlacement(relPos, blockState, null, wasUnknown, originalKey));
        }

        // 7. Attach block entity NBT — find matching block by relative pos and replace
        if (blocksTag.contains("BlockEntities", Tag.TAG_LIST)) {
            ListTag blockEntitiesTag = blocksTag.getList("BlockEntities", Tag.TAG_COMPOUND);

            // Build pos -> list index map for fast lookup
            Map<BlockPos, Integer> posToIndex = new HashMap<>(blocks.size());
            for (int i = 0; i < blocks.size(); i++) {
                posToIndex.put(blocks.get(i).relativePos(), i);
            }

            for (int i = 0; i < blockEntitiesTag.size(); i++) {
                CompoundTag beTag = blockEntitiesTag.getCompound(i);
                int[] posArr = beTag.getIntArray("Pos");
                if (posArr.length < 3) continue;

                BlockPos bePos = new BlockPos(posArr[0], posArr[1], posArr[2]);
                Integer listIdx = posToIndex.get(bePos);
                if (listIdx == null) continue;

                // Copy and strip reserved tags (x, y, z, id) per BlockPlacement contract
                CompoundTag cleanedNbt = beTag.copy();
                cleanedNbt.remove("Pos");
                cleanedNbt.remove("Id");
                cleanedNbt.remove("x");
                cleanedNbt.remove("y");
                cleanedNbt.remove("z");

                // Replace the BlockPlacement with the same data + attached NBT
                BlockPlacement existing = blocks.get(listIdx);
                blocks.set(listIdx, new BlockPlacement(
                        existing.relativePos(),
                        existing.blockState(),
                        cleanedNbt.isEmpty() ? null : cleanedNbt,
                        existing.wasUnknown(),
                        existing.originalPaletteKey()
                ));
            }
        }

        // 8. Parse entities
        List<EntityPlacement> entities = new ArrayList<>();
        if (root.contains("Entities", Tag.TAG_LIST)) {
            ListTag entitiesTag = root.getList("Entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < entitiesTag.size(); i++) {
                CompoundTag entityEntry = entitiesTag.getCompound(i);

                // "Pos" is a ListTag of 3 DoubleTag for Sponge entities (relative positions)
                ListTag posList = entityEntry.getList("Pos", Tag.TAG_DOUBLE);
                if (posList.size() < 3) continue;
                double ex = posList.getDouble(0);
                double ey = posList.getDouble(1);
                double ez = posList.getDouble(2);
                Vec3 relPos = new Vec3(ex, ey, ez);

                // Copy entity NBT; strip UUID tags to prevent collisions on re-paste
                CompoundTag entityNbt = entityEntry.copy();
                entityNbt.remove("Pos"); // position is stored in relPos
                entityNbt.remove("UUID");
                entityNbt.remove("UUIDLeast");
                entityNbt.remove("UUIDMost");

                entities.add(new EntityPlacement(relPos, entityNbt));
            }
        }

        // 9. Return SchematicHolder with spongeOffset
        return new SchematicHolder(width, height, length, blocks, entities, offset);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Inverts the Sponge palette NBT (blockStateString -> index) into an indexed array.
     *
     * <p>The Sponge palette CompoundTag maps block state strings to palette indices (ints).
     * We invert it to {@code paletteIndex -> PaletteEntry} for O(1) lookup during block
     * data decode. Unknown/unresolvable block states are stored as wasUnknown=true entries.</p>
     *
     * <p>Block state strings may include properties: {@code "minecraft:chest[facing=north,type=single]"}.
     * We parse the block name and properties separately using {@code BuiltInRegistries.BLOCK}
     * and string splitting — NOT {@code BlockStateParser} (which requires a command context
     * and throws checked exceptions).</p>
     *
     * @param paletteNbt the {@code Blocks.Palette} CompoundTag
     * @return array indexed by palette index, each element a resolved {@link PaletteEntry}
     */
    private static PaletteEntry[] buildPalette(CompoundTag paletteNbt) {
        // Find the max palette index to size the array
        int maxIndex = 0;
        for (String key : paletteNbt.getAllKeys()) {
            int idx = paletteNbt.getInt(key);
            if (idx > maxIndex) maxIndex = idx;
        }

        PaletteEntry[] palette = new PaletteEntry[maxIndex + 1];

        for (String paletteString : paletteNbt.getAllKeys()) {
            int idx = paletteNbt.getInt(paletteString);

            // Parse block name and optional properties from string like:
            // "minecraft:stone" or "minecraft:chest[facing=north,type=single,waterlogged=false]"
            String blockName = paletteString;
            Map<String, String> properties = new HashMap<>();

            int bracketStart = paletteString.indexOf('[');
            if (bracketStart != -1 && paletteString.endsWith("]")) {
                blockName = paletteString.substring(0, bracketStart);
                String propsStr = paletteString.substring(bracketStart + 1, paletteString.length() - 1);
                for (String prop : propsStr.split(",")) {
                    int eq = prop.indexOf('=');
                    if (eq != -1) {
                        properties.put(prop.substring(0, eq).trim(), prop.substring(eq + 1).trim());
                    }
                }
            }

            // Look up block in the static built-in registry
            ResourceLocation rl = ResourceLocation.tryParse(blockName);
            if (rl == null) {
                LOGGER.warn("SpongeSchematicParser: Invalid resource location '{}' in palette — substituting air", blockName);
                palette[idx] = new PaletteEntry(Blocks.AIR.defaultBlockState(), true, paletteString);
                continue;
            }

            Optional<Block> blockOpt = BuiltInRegistries.BLOCK.getOptional(rl);
            if (blockOpt.isEmpty()) {
                LOGGER.debug("SpongeSchematicParser: Unknown block '{}' in palette — marking wasUnknown", paletteString);
                palette[idx] = new PaletteEntry(Blocks.AIR.defaultBlockState(), true, paletteString);
                continue;
            }

            Block block = blockOpt.get();
            BlockState state = block.defaultBlockState();

            // Apply block state properties
            for (Map.Entry<String, String> prop : properties.entrySet()) {
                state = applyProperty(state, prop.getKey(), prop.getValue());
            }

            palette[idx] = new PaletteEntry(state, false, null);
        }

        // Fill any gaps in the palette with null-safe fallback
        for (int i = 0; i < palette.length; i++) {
            if (palette[i] == null) {
                palette[i] = new PaletteEntry(Blocks.AIR.defaultBlockState(), true, null);
            }
        }

        return palette;
    }

    /**
     * Apply a single block state property by name and string value.
     *
     * <p>If the property name or value is not recognised, the original state is returned
     * unchanged (not a crash — malformed schematics should not crash the server).</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState applyProperty(BlockState state, String propName, String propValue) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(propName)) {
                Optional<?> parsed = property.getValue(propValue);
                if (parsed.isPresent()) {
                    return state.setValue((Property) property, (Comparable) parsed.get());
                } else {
                    LOGGER.debug("SpongeSchematicParser: Unknown value '{}' for property '{}' — ignoring",
                            propValue, propName);
                }
                break;
            }
        }
        return state;
    }

    /**
     * Resolved palette entry. Immutable value type.
     */
    private record PaletteEntry(
            BlockState blockState,
            boolean wasUnknown,
            String originalKey  // null when wasUnknown=false
    ) {}
}
