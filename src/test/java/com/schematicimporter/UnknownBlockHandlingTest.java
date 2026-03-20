package com.schematicimporter;

import com.schematicimporter.schematic.BlockPlacement;
import com.schematicimporter.schematic.SchematicHolder;
import com.schematicimporter.schematic.SpongeSchematicParser;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dedicated tests for the unknown palette entry → wasUnknown=true BlockPlacement path.
 *
 * <p>When a schematic contains a palette entry that cannot be resolved to a vanilla
 * {@code BlockState} (e.g., a mod block not present at parse time), the parser must:
 * <ol>
 *   <li>Not crash or throw an exception</li>
 *   <li>Produce a {@code BlockPlacement} with {@code wasUnknown=true}</li>
 *   <li>Set {@code blockState} to {@code Blocks.AIR.defaultBlockState()} (never null)</li>
 *   <li>Set {@code originalPaletteKey} to the unresolvable string from the palette</li>
 * </ol>
 * </p>
 */
class UnknownBlockHandlingTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.init();
    }

    /**
     * Builds a 3-block Sponge v2 schematic where:
     * - Index 0 = "mymod:unknown_block" (unresolvable — wasUnknown=true expected)
     * - Index 1 = "minecraft:stone" (vanilla — wasUnknown=false expected)
     * - Block data: [unknown, stone, stone] → 1 unknown, 2 known
     */
    private Path buildSchematicWithUnknownBlock() throws IOException {
        int width = 3, height = 1, length = 1;

        CompoundTag root = new CompoundTag();
        root.putInt("Version", 2);
        root.putShort("Width", (short) width);
        root.putShort("Height", (short) height);
        root.putShort("Length", (short) length);

        CompoundTag blocksTag = new CompoundTag();
        CompoundTag palette = new CompoundTag();
        palette.putInt("mymod:unknown_block", 0);
        palette.putInt("minecraft:stone", 1);
        blocksTag.put("Palette", palette);

        // Data: [0, 1, 1] encoded as varints (all single-byte since values < 128)
        blocksTag.putByteArray("Data", new byte[]{0x00, 0x01, 0x01});

        root.put("Blocks", blocksTag);

        Path schematic = tempDir.resolve("unknown_block.schem");
        NbtIo.writeCompressed(root, schematic);
        return schematic;
    }

    @Test
    void unknownBlock_producesExactlyOneWasUnknownPlacement() throws IOException {
        Path schematic = buildSchematicWithUnknownBlock();
        SchematicHolder holder = SpongeSchematicParser.parse(schematic, RegistryAccess.EMPTY);

        List<BlockPlacement> unknown = holder.blocks().stream()
                .filter(BlockPlacement::wasUnknown)
                .toList();

        assertEquals(1, unknown.size(),
                "Exactly one BlockPlacement should have wasUnknown=true");
    }

    @Test
    void unknownBlock_originalPaletteKeyIsPreserved() throws IOException {
        Path schematic = buildSchematicWithUnknownBlock();
        SchematicHolder holder = SpongeSchematicParser.parse(schematic, RegistryAccess.EMPTY);

        BlockPlacement unknown = holder.blocks().stream()
                .filter(BlockPlacement::wasUnknown)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No wasUnknown BlockPlacement found"));

        assertEquals("mymod:unknown_block", unknown.originalPaletteKey(),
                "originalPaletteKey must be the unresolvable palette string");
    }

    @Test
    void unknownBlock_blockStateIsAirNotNull() throws IOException {
        Path schematic = buildSchematicWithUnknownBlock();
        SchematicHolder holder = SpongeSchematicParser.parse(schematic, RegistryAccess.EMPTY);

        BlockPlacement unknown = holder.blocks().stream()
                .filter(BlockPlacement::wasUnknown)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No wasUnknown BlockPlacement found"));

        assertNotNull(unknown.blockState(),
                "blockState for unknown block must not be null");
        assertEquals(Blocks.AIR.defaultBlockState(), unknown.blockState(),
                "blockState for unknown block must be Blocks.AIR.defaultBlockState()");
    }

    @Test
    void knownBlocks_allHaveWasUnknownFalse() throws IOException {
        Path schematic = buildSchematicWithUnknownBlock();
        SchematicHolder holder = SpongeSchematicParser.parse(schematic, RegistryAccess.EMPTY);

        List<BlockPlacement> known = holder.blocks().stream()
                .filter(b -> !b.wasUnknown())
                .toList();

        assertEquals(2, known.size(),
                "The two stone blocks should have wasUnknown=false");
        known.forEach(b -> assertFalse(b.wasUnknown(),
                "All known blocks must have wasUnknown=false"));
    }
}
