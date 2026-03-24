package com.schematicimporter;

import com.schematicimporter.schematic.SchematicHolder;
import com.schematicimporter.schematic.SpongeSchematicParser;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SpongeSchematicParser.
 *
 * <p>Fixtures are created programmatically using NbtIo.writeCompressed — no pre-built
 * binary files are needed, keeping tests self-contained and cross-platform.</p>
 *
 * <p>Requires Minecraft bootstrap ({@code Bootstrap.bootStrap()}) for block state
 * resolution via {@code BuiltInRegistries.BLOCK}.</p>
 */
class SpongeSchematicParserTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.init();
    }

    // ---------------------------------------------------------------------------
    //  Fixture builders
    // ---------------------------------------------------------------------------

    /**
     * Builds a minimal Sponge v2 schematic (2x2x2 = 8 blocks).
     * Palette: index 0 = "minecraft:air", index 1 = "minecraft:stone"
     * All 8 blocks are stone (palette index 1).
     * Includes one block entity (chest) at relative pos (0,0,0).
     * No entities list.
     */
    private Path buildSimpleV2Schem() throws IOException {
        int width = 2, height = 2, length = 2;
        int totalBlocks = width * height * length; // 8

        CompoundTag root = new CompoundTag();
        root.putInt("Version", 2);
        root.putShort("Width", (short) width);
        root.putShort("Height", (short) height);
        root.putShort("Length", (short) length);

        // Palette: 2 entries, well under 128 → single-byte varints
        CompoundTag blocksTag = new CompoundTag();
        CompoundTag palette = new CompoundTag();
        palette.putInt("minecraft:air", 0);
        palette.putInt("minecraft:stone", 1);
        blocksTag.put("Palette", palette);

        // Data: all 8 blocks = stone (index 1), encoded as varints
        // Each value is 1 = single byte 0x01
        byte[] data = new byte[totalBlocks];
        for (int i = 0; i < totalBlocks; i++) {
            data[i] = 0x01; // varint for 1
        }
        blocksTag.putByteArray("Data", data);

        // BlockEntities: one chest at pos (0,0,0)
        ListTag blockEntities = new ListTag();
        CompoundTag chest = new CompoundTag();
        chest.putIntArray("Pos", new int[]{0, 0, 0});
        chest.putString("Id", "minecraft:chest");
        chest.putString("CustomName", "{\"text\":\"Test Chest\"}");
        blockEntities.add(chest);
        blocksTag.put("BlockEntities", blockEntities);

        root.put("Blocks", blocksTag);

        // No Offset tag → should default to {0,0,0}

        Path schematic = tempDir.resolve("simple_v2.schem");
        NbtIo.writeCompressed(root, schematic);
        return schematic;
    }

    /**
     * Builds a Sponge v3 schematic with exactly 200 unique block states.
     * Palette indices 128-199 use two-byte varints (varint edge case).
     *
     * <p>Strategy: use 200 distinct block state strings. Indices 0-127 fit in
     * single-byte varints; indices 128-199 require two-byte varints. Each block
     * in the structure cycles through all 200 palette indices so the test can
     * verify that blocks decoded at index 128+ are non-air non-corrupt values.</p>
     */
    private Path buildLargePaletteV3Schem() throws IOException {
        // 200 blocks in a 200x1x1 strip
        int width = 200, height = 1, length = 1;
        int totalBlocks = width; // 200

        CompoundTag root = new CompoundTag();
        root.putInt("Version", 3);
        root.putShort("Width", (short) width);
        root.putShort("Height", (short) height);
        root.putShort("Length", (short) length);

        CompoundTag blocksTag = new CompoundTag();
        CompoundTag palette = new CompoundTag();

        // Build 200 distinct vanilla block state strings
        // We'll use wool colors (16), then terracotta (16), then concrete (16), etc.
        // For simplicity, use minecraft:stone for index 0, then 199 different blocks
        String[] blockStates = buildDistinct200BlockStates();
        assertEquals(200, blockStates.length, "Need exactly 200 distinct block states");

        for (int i = 0; i < 200; i++) {
            palette.putInt(blockStates[i], i);
        }
        blocksTag.put("Palette", palette);

        // Data: block at position i uses palette index i
        // Values 0-127: single-byte varint; values 128-199: two-byte varint
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < totalBlocks; i++) {
            writeVarint(baos, i);
        }
        blocksTag.putByteArray("Data", baos.toByteArray());

        root.put("Blocks", blocksTag);

        Path schematic = tempDir.resolve("large_palette_v3.schem");
        NbtIo.writeCompressed(root, schematic);
        return schematic;
    }

    /**
     * Builds a schematic with an Offset=[2,0,3] tag.
     */
    private Path buildSchematicWithOffset() throws IOException {
        int width = 1, height = 1, length = 1;

        CompoundTag root = new CompoundTag();
        root.putInt("Version", 2);
        root.putShort("Width", (short) width);
        root.putShort("Height", (short) height);
        root.putShort("Length", (short) length);
        root.putIntArray("Offset", new int[]{2, 0, 3});

        CompoundTag blocksTag = new CompoundTag();
        CompoundTag palette = new CompoundTag();
        palette.putInt("minecraft:air", 0);
        blocksTag.put("Palette", palette);
        blocksTag.putByteArray("Data", new byte[]{0x00}); // 1 block = air (index 0)
        root.put("Blocks", blocksTag);

        Path schematic = tempDir.resolve("with_offset.schem");
        NbtIo.writeCompressed(root, schematic);
        return schematic;
    }

    // ---------------------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------------------

    private static String[] buildDistinct200BlockStates() {
        String[] states = new String[200];
        int idx = 0;
        // First entry: air
        states[idx++] = "minecraft:air";
        // Wool: 16 colors
        String[] woolColors = {"white", "orange", "magenta", "light_blue", "yellow", "lime",
                "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        for (String c : woolColors) {
            if (idx < 200) states[idx++] = "minecraft:" + c + "_wool";
        }
        // Concrete: 16
        for (String c : woolColors) {
            if (idx < 200) states[idx++] = "minecraft:" + c + "_concrete";
        }
        // Concrete powder: 16
        for (String c : woolColors) {
            if (idx < 200) states[idx++] = "minecraft:" + c + "_concrete_powder";
        }
        // Stained glass: 16
        for (String c : woolColors) {
            if (idx < 200) states[idx++] = "minecraft:" + c + "_stained_glass";
        }
        // Stained glass pane: 16
        for (String c : woolColors) {
            if (idx < 200) states[idx++] = "minecraft:" + c + "_stained_glass_pane";
        }
        // Terracotta: 16
        for (String c : woolColors) {
            if (idx < 200) states[idx++] = "minecraft:" + c + "_terracotta";
        }
        // Glazed terracotta: 16
        for (String c : woolColors) {
            if (idx < 200) states[idx++] = "minecraft:" + c + "_glazed_terracotta";
        }
        // Shulker box: 16
        for (String c : woolColors) {
            if (idx < 200) states[idx++] = "minecraft:" + c + "_shulker_box";
        }
        // Beds: 16
        for (String c : woolColors) {
            if (idx < 200) states[idx++] = "minecraft:" + c + "_bed";
        }
        // Candles: 16
        for (String c : woolColors) {
            if (idx < 200) states[idx++] = "minecraft:" + c + "_candle";
        }
        // Banners: 16
        for (String c : woolColors) {
            if (idx < 200) states[idx++] = "minecraft:" + c + "_banner";
        }
        // Carpets: 16
        for (String c : woolColors) {
            if (idx < 200) states[idx++] = "minecraft:" + c + "_carpet";
        }
        // Fill up to 200 with common blocks
        String[] extras = {
            "minecraft:stone", "minecraft:grass_block", "minecraft:dirt", "minecraft:cobblestone",
            "minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks",
            "minecraft:jungle_planks", "minecraft:acacia_planks", "minecraft:dark_oak_planks",
            "minecraft:sand", "minecraft:gravel", "minecraft:gold_ore", "minecraft:iron_ore",
            "minecraft:coal_ore", "minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log",
            "minecraft:jungle_log", "minecraft:sponge", "minecraft:glass", "minecraft:lapis_ore",
            "minecraft:dispenser", "minecraft:sandstone", "minecraft:note_block",
            "minecraft:powered_rail", "minecraft:detector_rail", "minecraft:sticky_piston",
            "minecraft:cobweb", "minecraft:short_grass"
        };
        for (String s : extras) {
            if (idx < 200) states[idx++] = s;
        }
        // Safety: fill remaining with numbered stone variants to ensure exactly 200 distinct entries
        if (idx < 200) {
            // Use additional blocks as needed
            String[] more = {
                "minecraft:deepslate", "minecraft:calcite", "minecraft:tuff",
                "minecraft:dripstone_block", "minecraft:moss_block", "minecraft:rooted_dirt",
                "minecraft:raw_iron_block", "minecraft:raw_gold_block", "minecraft:raw_copper_block",
                "minecraft:copper_block", "minecraft:cut_copper", "minecraft:amethyst_block",
                "minecraft:budding_amethyst", "minecraft:andesite", "minecraft:granite",
                "minecraft:diorite", "minecraft:polished_andesite", "minecraft:polished_granite",
                "minecraft:polished_diorite", "minecraft:chiseled_stone_bricks",
                "minecraft:cracked_stone_bricks", "minecraft:mossy_stone_bricks",
                "minecraft:stone_bricks", "minecraft:nether_bricks", "minecraft:nether_quartz_ore",
                "minecraft:netherrack", "minecraft:soul_sand", "minecraft:soul_soil",
                "minecraft:basalt", "minecraft:blackstone"
            };
            for (String s : more) {
                if (idx < 200) states[idx++] = s;
            }
        }
        // Final safety: truncate or assert
        String[] result = new String[Math.min(idx, 200)];
        System.arraycopy(states, 0, result, 0, result.length);
        return result;
    }

    private static void writeVarint(java.io.OutputStream out, int value) throws IOException {
        do {
            int bits = value & 0x7F;
            value >>>= 7;
            if (value != 0) {
                bits |= 0x80;
            }
            out.write(bits);
        } while (value != 0);
    }

    // ---------------------------------------------------------------------------
    //  Tests: simple_v2.schem
    // ---------------------------------------------------------------------------

    @Test
    void simpleV2_dimensionsAreCorrect() throws IOException {
        Path schematic = buildSimpleV2Schem();
        SchematicHolder holder = SpongeSchematicParser.parse(schematic, RegistryAccess.EMPTY);
        assertEquals(2, holder.width(), "width should be 2");
        assertEquals(2, holder.height(), "height should be 2");
        assertEquals(2, holder.length(), "length should be 2");
    }

    @Test
    void simpleV2_blockCountEqualsVolume() throws IOException {
        Path schematic = buildSimpleV2Schem();
        SchematicHolder holder = SpongeSchematicParser.parse(schematic, RegistryAccess.EMPTY);
        int expectedCount = holder.width() * holder.height() * holder.length();
        assertEquals(expectedCount, holder.blocks().size(),
                "blocks list size must equal width * height * length");
    }

    @Test
    void simpleV2_noUnknownBlocks() throws IOException {
        Path schematic = buildSimpleV2Schem();
        SchematicHolder holder = SpongeSchematicParser.parse(schematic, RegistryAccess.EMPTY);
        long unknownCount = holder.blocks().stream()
                .filter(b -> b.wasUnknown())
                .count();
        assertEquals(0, unknownCount, "Simple v2 schematic with vanilla blocks should have 0 wasUnknown blocks");
    }

    // ---------------------------------------------------------------------------
    //  Tests: large_palette_v3.schem (varint edge case)
    // ---------------------------------------------------------------------------

    @Test
    void largePaletteV3_blockAtIndex128IsNonAir() throws IOException {
        Path schematic = buildLargePaletteV3Schem();
        SchematicHolder holder = SpongeSchematicParser.parse(schematic, RegistryAccess.EMPTY);

        // Block at flat index 128 should be the block from palette[128] — which is non-air
        // x=128 (since 200x1x1), y=0, z=0
        var blockAt128 = holder.blocks().stream()
                .filter(b -> b.relativePos().getX() == 128 && b.relativePos().getY() == 0 && b.relativePos().getZ() == 0)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No block found at position x=128,y=0,z=0"));

        assertFalse(blockAt128.wasUnknown(),
                "Block at palette index 128 should resolve to a known block state");
        assertNotNull(blockAt128.blockState(),
                "BlockState at index 128 must not be null");
    }

    @Test
    void largePaletteV3_allBlocksAbove127AreNonCorrupt() throws IOException {
        Path schematic = buildLargePaletteV3Schem();
        SchematicHolder holder = SpongeSchematicParser.parse(schematic, RegistryAccess.EMPTY);

        // All 200 blocks should be present; blocks at x=128..199 use two-byte varints
        assertEquals(200, holder.blocks().size(), "Should have exactly 200 blocks");

        long unknownAbove127 = holder.blocks().stream()
                .filter(b -> b.relativePos().getX() >= 128)
                .filter(b -> b.wasUnknown())
                .count();
        assertEquals(0, unknownAbove127,
                "All blocks with palette indices 128-199 must decode to valid vanilla block states (no wasUnknown)");
    }

    // ---------------------------------------------------------------------------
    //  Tests: Offset tag
    // ---------------------------------------------------------------------------

    @Test
    void offsetTag_storedInSpongeOffset() throws IOException {
        Path schematic = buildSchematicWithOffset();
        SchematicHolder holder = SpongeSchematicParser.parse(schematic, RegistryAccess.EMPTY);

        assertNotNull(holder.spongeOffset(), "spongeOffset must not be null");
        assertEquals(3, holder.spongeOffset().length, "spongeOffset must have 3 elements");
        assertEquals(2, holder.spongeOffset()[0], "spongeOffset[0] (x) should be 2");
        assertEquals(0, holder.spongeOffset()[1], "spongeOffset[1] (y) should be 0");
        assertEquals(3, holder.spongeOffset()[2], "spongeOffset[2] (z) should be 3");
    }

    // ---------------------------------------------------------------------------
    //  Tests: peekMetadata (lazy load — no blocks)
    // ---------------------------------------------------------------------------

    @Test
    void peekMetadata_returnsCorrectDimensions() throws IOException {
        Path schematic = buildSimpleV2Schem();
        SpongeSchematicParser.SchematicMetadata meta = SpongeSchematicParser.peekMetadata(schematic);
        assertEquals(2, meta.width(), "peekMetadata width should be 2");
        assertEquals(2, meta.height(), "peekMetadata height should be 2");
        assertEquals(2, meta.length(), "peekMetadata length should be 2");
    }
}
