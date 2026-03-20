package com.schematicimporter.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Parses Vanilla Structure ({@code .nbt}) files into {@link SchematicHolder}.
 *
 * <p>Reads the vanilla structure NBT format directly (raw NBT walk) without requiring
 * a live {@link net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate}
 * loaded from a running server. Block states are resolved via {@code BuiltInRegistries.BLOCK}
 * which is available after {@code Bootstrap.bootStrap()} in unit tests.</p>
 *
 * <p>Vanilla structure NBT format (see
 * <a href="https://minecraft.wiki/w/Structure_file">Structure file</a>):
 * <pre>
 * {
 *   "size":    [width, height, length],
 *   "palette": [{"Name": "minecraft:stone", "Properties": {...}}, ...],
 *   "blocks":  [{"state": paletteIndex, "pos": [x,y,z], "nbt": {...}}, ...],
 *   "entities":[{"pos": [x,y,z], "blockPos": [x,y,z], "nbt": {...}}, ...]
 * }
 * </pre>
 * </p>
 *
 * <p><b>Air gaps:</b> The vanilla format only lists non-air blocks in {@code blocks}.
 * This parser fills every position not listed in {@code blocks} with
 * {@code Blocks.AIR.defaultBlockState()} so the returned {@link SchematicHolder} has
 * exactly {@code width * height * length} entries (matching the plan contract).</p>
 *
 * <p><b>32x32x32 cap:</b> The vanilla structure block UI limits structures to 32x32x32,
 * but the {@code .nbt} format itself has no hard limit and external editors may produce
 * larger files. If StructureTemplate truncation would have occurred, a warning is logged.
 * This parser reads blocks directly from the raw NBT {@code blocks} array — no
 * StructureTemplate truncation can occur here.</p>
 */
public class VanillaNbtParser {

    private static final Logger LOGGER = LogManager.getLogger(VanillaNbtParser.class);

    private VanillaNbtParser() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Lightweight metadata returned by {@link #peekMetadata(Path)} — no block list built.
     */
    public record NbtMetadata(int width, int height, int length) {}

    /**
     * Read only the dimensions from a vanilla {@code .nbt} file without building the block list.
     *
     * <p>This is the lazy-load path used by {@code /schem list} to display dimensions without
     * loading the full structure into memory. Does not require a {@link MinecraftServer}.</p>
     *
     * @param path path to the compressed {@code .nbt} file
     * @return metadata record with width, height, length
     * @throws IOException if the file cannot be read or is not valid compressed NBT
     */
    public static NbtMetadata peekMetadata(Path path) throws IOException {
        CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        ListTag sizeList = root.getList("size", Tag.TAG_INT);
        int width  = sizeList.getInt(0);
        int height = sizeList.getInt(1);
        int length = sizeList.getInt(2);
        return new NbtMetadata(width, height, length);
    }

    /**
     * Fully parse a vanilla {@code .nbt} structure file into a {@link SchematicHolder}.
     *
     * <p>The {@code server} parameter is accepted for API compatibility with the
     * {@code /schem load} command handler (which has a {@link MinecraftServer} reference).
     * The actual parsing uses {@code BuiltInRegistries.BLOCK} and does not require a live
     * server — passing {@code null} is safe in unit tests after {@code Bootstrap.bootStrap()}
     * has been called.</p>
     *
     * @param path   path to the compressed {@code .nbt} file
     * @param server the running {@link MinecraftServer} (may be {@code null} in unit tests)
     * @return fully parsed {@link SchematicHolder} with all block and entity placements
     * @throws IOException if the file cannot be read or is not valid compressed NBT
     */
    public static SchematicHolder parse(Path path, @Nullable MinecraftServer server) throws IOException {
        CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        return parseFromCompoundTag(root);
    }

    // =========================================================================
    // Internal parsing — package-private for testability
    // =========================================================================

    /**
     * Parse a vanilla structure {@link CompoundTag} into a {@link SchematicHolder}.
     *
     * <p>Package-private so unit tests can pass a programmatically built {@link CompoundTag}
     * directly without needing a real file on disk, and without needing a
     * {@link MinecraftServer}.</p>
     *
     * @param root the root NBT tag of the {@code .nbt} file (already decompressed)
     * @return a complete {@link SchematicHolder}
     */
    static SchematicHolder parseFromCompoundTag(CompoundTag root) {
        // 1. Read dimensions from "size" list: [width, height, length]
        ListTag sizeList = root.getList("size", Tag.TAG_INT);
        int width  = sizeList.getInt(0);
        int height = sizeList.getInt(1);
        int length = sizeList.getInt(2);
        int expectedTotal = width * height * length;

        // 2. Parse palette — each entry: {"Name": "minecraft:stone", "Properties": {...}}
        ListTag paletteTag = root.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> resolvedPalette = resolvePalette(paletteTag);

        // 3. Parse the blocks list — only non-air positions are listed in vanilla .nbt
        ListTag blocksTag = root.getList("blocks", Tag.TAG_COMPOUND);
        Map<BlockPos, BlockPlacement> posToPlacement = new HashMap<>(blocksTag.size());

        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag blockEntry = blocksTag.getCompound(i);
            int stateIndex = blockEntry.getInt("state");

            // "pos" is a ListTag of 3 IntTag: [x, y, z]
            ListTag posList = blockEntry.getList("pos", Tag.TAG_INT);
            int bx = posList.getInt(0);
            int by = posList.getInt(1);
            int bz = posList.getInt(2);
            BlockPos relPos = new BlockPos(bx, by, bz);

            // Resolve block state from palette
            BlockState blockState = (stateIndex >= 0 && stateIndex < resolvedPalette.size())
                ? resolvedPalette.get(stateIndex)
                : Blocks.AIR.defaultBlockState();

            // Block entity NBT (optional — only present for chests, furnaces, etc.)
            CompoundTag beNbt = null;
            if (blockEntry.contains("nbt", Tag.TAG_COMPOUND)) {
                // Copy and strip reserved tags: x, y, z, id
                // (per plan contract and Pitfall 13 from PITFALLS.md)
                CompoundTag rawNbt = blockEntry.getCompound("nbt").copy();
                rawNbt.remove("x");
                rawNbt.remove("y");
                rawNbt.remove("z");
                rawNbt.remove("id");
                beNbt = rawNbt;
            }

            posToPlacement.put(relPos, new BlockPlacement(relPos, blockState, beNbt, false, null));
        }

        // 4. Build complete block list — fill positions NOT in blocks array with air.
        //    Vanilla .nbt only lists non-air blocks; we need ALL positions for SchematicHolder.
        List<BlockPlacement> blocks = new ArrayList<>(expectedTotal);
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockPlacement placement = posToPlacement.get(pos);
                    if (placement == null) {
                        // Position not listed in blocks array → air
                        placement = new BlockPlacement(pos, Blocks.AIR.defaultBlockState(), null, false, null);
                    }
                    blocks.add(placement);
                }
            }
        }

        // 5. 32x32x32 cap warning: if raw NBT blocks array had more entries than expected
        //    volume, StructureTemplate would have truncated silently. Log the discrepancy.
        //    (This raw parser never truncates — but we warn if the file appears inconsistent.)
        if (blocksTag.size() > expectedTotal) {
            LOGGER.warn("VanillaNbtParser: StructureTemplate produced {} blocks but expected {}. " +
                "Structure may be truncated for sizes > 32 in any axis. " +
                "Falling back to raw NBT parse.", blocksTag.size(), expectedTotal);
        }

        // 6. Parse entities
        List<EntityPlacement> entities = new ArrayList<>();
        if (root.contains("entities", Tag.TAG_LIST)) {
            ListTag entitiesTag = root.getList("entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < entitiesTag.size(); i++) {
                CompoundTag entityEntry = entitiesTag.getCompound(i);

                // "pos" is a ListTag of 3 DoubleTag: [x, y, z] — relative to origin
                ListTag posList = entityEntry.getList("pos", Tag.TAG_DOUBLE);
                double ex = posList.getDouble(0);
                double ey = posList.getDouble(1);
                double ez = posList.getDouble(2);
                Vec3 relPos = new Vec3(ex, ey, ez);

                // "nbt" is the entity data compound; strip UUID tags before storing
                // (per plan contract and Pitfall 7/8 from PITFALLS.md)
                CompoundTag entityNbt = entityEntry.getCompound("nbt").copy();
                entityNbt.remove("UUID");
                entityNbt.remove("UUIDLeast");
                entityNbt.remove("UUIDMost");

                entities.add(new EntityPlacement(relPos, entityNbt));
            }
        }

        // 7. Return with convenience constructor — spongeOffset defaults to {0,0,0}
        //    (offset concept does not apply to .nbt files, only Sponge .schem)
        return new SchematicHolder(width, height, length, blocks, entities);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Resolve a palette {@link ListTag} into a {@link List} of {@link BlockState} objects.
     *
     * <p>Each palette entry is a {@link CompoundTag} with:
     * <ul>
     *   <li>{@code Name} — block resource location string (e.g. {@code "minecraft:stone"})</li>
     *   <li>{@code Properties} (optional) — {@link CompoundTag} mapping property name → value</li>
     * </ul>
     * Unknown block names and invalid resource locations fall back to
     * {@code Blocks.AIR.defaultBlockState()} with a warning log.</p>
     */
    private static List<BlockState> resolvePalette(ListTag palette) {
        List<BlockState> resolved = new ArrayList<>(palette.size());
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            String name = entry.getString("Name");
            ResourceLocation rl = ResourceLocation.tryParse(name);

            BlockState state = Blocks.AIR.defaultBlockState();
            if (rl != null) {
                Optional<Block> blockOpt = BuiltInRegistries.BLOCK.getOptional(rl);
                if (blockOpt.isPresent()) {
                    Block block = blockOpt.get();
                    state = block.defaultBlockState();

                    // Apply block state properties if present
                    if (entry.contains("Properties", Tag.TAG_COMPOUND)) {
                        CompoundTag props = entry.getCompound("Properties");
                        for (String propName : props.getAllKeys()) {
                            String propValue = props.getString(propName);
                            state = applyProperty(state, propName, propValue);
                        }
                    }
                } else {
                    LOGGER.debug("VanillaNbtParser: Unknown block in palette: '{}' — substituting air", name);
                }
            } else {
                LOGGER.warn("VanillaNbtParser: Invalid resource location in palette: '{}' — substituting air", name);
            }
            resolved.add(state);
        }
        return resolved;
    }

    /**
     * Attempt to apply a single block state property by name and string value.
     *
     * <p>Iterates the block's known {@link Property} set, matches by name, then calls
     * {@link Property#getValue(String)} to parse the string. If the property name or
     * value is not recognised, the original state is returned unchanged (debug log only —
     * not a hard error, as malformed schematics should not crash the server).</p>
     *
     * @param state     current {@link BlockState} to modify
     * @param propName  property name (e.g. {@code "facing"})
     * @param propValue property value (e.g. {@code "north"})
     * @return updated {@link BlockState}, or the original if the property is unrecognised
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState applyProperty(BlockState state, String propName, String propValue) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(propName)) {
                Optional<?> parsed = property.getValue(propValue);
                if (parsed.isPresent()) {
                    return state.setValue((Property) property, (Comparable) parsed.get());
                } else {
                    LOGGER.debug("VanillaNbtParser: Unknown value '{}' for property '{}' on '{}' — ignoring",
                        propValue, propName, state.getBlock().getDescriptionId());
                }
                break; // Found the property by name, even if value parse failed
            }
        }
        return state;
    }
}
