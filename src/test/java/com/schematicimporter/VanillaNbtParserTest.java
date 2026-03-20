package com.schematicimporter;

import com.schematicimporter.schematic.BlockPlacement;
import com.schematicimporter.schematic.EntityPlacement;
import com.schematicimporter.schematic.SchematicHolder;
import com.schematicimporter.schematic.VanillaNbtParser;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VanillaNbtParser.
 *
 * <p>Tests run without a live MinecraftServer. The vanilla .nbt fixture is built
 * programmatically via NbtIo.writeCompressed(), so no binary resource file is needed.</p>
 *
 * <p>Fixture layout:
 * <ul>
 *   <li>Size: [3, 2, 3] — 18 total positions</li>
 *   <li>Palette: [air(0), stone(1), chest(2)]</li>
 *   <li>stone at [1,0,1] — no block entity</li>
 *   <li>chest at [0,1,0] — block entity with Items[], stripped x/y/z/id</li>
 *   <li>armor_stand entity at relative [1.5, 0.0, 1.5]</li>
 *   <li>All other 16 positions are air</li>
 * </ul>
 * </p>
 */
class VanillaNbtParserTest {

    private static Path fixtureFile;

    // Expected dimensions for the fixture
    private static final int EXPECTED_WIDTH = 3;
    private static final int EXPECTED_HEIGHT = 2;
    private static final int EXPECTED_LENGTH = 3;
    private static final int EXPECTED_TOTAL = EXPECTED_WIDTH * EXPECTED_HEIGHT * EXPECTED_LENGTH; // 18

    @BeforeAll
    static void setup() throws IOException {
        // Initialize Minecraft + NeoForge FML registries for unit tests
        MinecraftTestBootstrap.init();

        // Build the fixture CompoundTag programmatically
        CompoundTag root = buildFixtureNbt();

        // Write to src/test/resources/fixtures/vanilla_structure.nbt (compressed)
        Path fixturesDir = Path.of("src/test/resources/fixtures");
        Files.createDirectories(fixturesDir);
        fixtureFile = fixturesDir.resolve("vanilla_structure.nbt");
        NbtIo.writeCompressed(root, fixtureFile);
    }

    /**
     * Builds a minimal vanilla structure NBT matching the vanilla .nbt format spec.
     *
     * <p>Structure:
     * <pre>
     * {
     *   "size": [3, 2, 3],
     *   "palette": [
     *     {"Name": "minecraft:air"},
     *     {"Name": "minecraft:stone"},
     *     {"Name": "minecraft:chest", "Properties": {"facing": "north", "type": "single", "waterlogged": "false"}}
     *   ],
     *   "blocks": [
     *     {"state": 1, "pos": [1, 0, 1]},
     *     {"state": 2, "pos": [0, 1, 0], "nbt": {"Items": [], "x": 0, "y": 1, "z": 0, "id": "minecraft:chest"}}
     *   ],
     *   "entities": [
     *     {"pos": [1.5, 0.0, 1.5], "blockPos": [1, 0, 1], "nbt": {"id": "minecraft:armor_stand", "UUID": [I; 1, 2, 3, 4]}}
     *   ]
     * }
     * </pre>
     */
    private static CompoundTag buildFixtureNbt() {
        CompoundTag root = new CompoundTag();

        // size: [3, 2, 3]
        ListTag sizeList = new ListTag();
        sizeList.add(IntTag.valueOf(EXPECTED_WIDTH));
        sizeList.add(IntTag.valueOf(EXPECTED_HEIGHT));
        sizeList.add(IntTag.valueOf(EXPECTED_LENGTH));
        root.put("size", sizeList);

        // palette: [air, stone, chest]
        ListTag palette = new ListTag();

        // palette[0] = air
        CompoundTag airEntry = new CompoundTag();
        airEntry.putString("Name", "minecraft:air");
        palette.add(airEntry);

        // palette[1] = stone
        CompoundTag stoneEntry = new CompoundTag();
        stoneEntry.putString("Name", "minecraft:stone");
        palette.add(stoneEntry);

        // palette[2] = chest (facing north)
        CompoundTag chestEntry = new CompoundTag();
        chestEntry.putString("Name", "minecraft:chest");
        CompoundTag chestProps = new CompoundTag();
        chestProps.putString("facing", "north");
        chestProps.putString("type", "single");
        chestProps.putString("waterlogged", "false");
        chestEntry.put("Properties", chestProps);
        palette.add(chestEntry);

        root.put("palette", palette);

        // blocks: stone at [1,0,1], chest at [0,1,0]
        ListTag blocks = new ListTag();

        // stone block entry
        CompoundTag stoneBlock = new CompoundTag();
        stoneBlock.putInt("state", 1);
        ListTag stonePos = new ListTag();
        stonePos.add(IntTag.valueOf(1));
        stonePos.add(IntTag.valueOf(0));
        stonePos.add(IntTag.valueOf(1));
        stoneBlock.put("pos", stonePos);
        blocks.add(stoneBlock);

        // chest block entry with block entity NBT
        CompoundTag chestBlock = new CompoundTag();
        chestBlock.putInt("state", 2);
        ListTag chestPos = new ListTag();
        chestPos.add(IntTag.valueOf(0));
        chestPos.add(IntTag.valueOf(1));
        chestPos.add(IntTag.valueOf(0));
        chestBlock.put("pos", chestPos);

        // block entity NBT for chest (includes x/y/z/id that parser must strip)
        CompoundTag chestNbt = new CompoundTag();
        chestNbt.put("Items", new ListTag());
        chestNbt.putInt("x", 0);
        chestNbt.putInt("y", 1);
        chestNbt.putInt("z", 0);
        chestNbt.putString("id", "minecraft:chest");
        chestBlock.put("nbt", chestNbt);

        blocks.add(chestBlock);
        root.put("blocks", blocks);

        // entities: armor_stand at relative [1.5, 0.0, 1.5]
        ListTag entities = new ListTag();
        CompoundTag armorStandEntry = new CompoundTag();

        ListTag entityPos = new ListTag();
        entityPos.add(DoubleTag.valueOf(1.5));
        entityPos.add(DoubleTag.valueOf(0.0));
        entityPos.add(DoubleTag.valueOf(1.5));
        armorStandEntry.put("pos", entityPos);

        ListTag entityBlockPos = new ListTag();
        entityBlockPos.add(IntTag.valueOf(1));
        entityBlockPos.add(IntTag.valueOf(0));
        entityBlockPos.add(IntTag.valueOf(1));
        armorStandEntry.put("blockPos", entityBlockPos);

        // entity NBT with UUID (parser must strip UUID key)
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putString("id", "minecraft:armor_stand");
        // UUID stored as int array in modern MC
        entityNbt.putIntArray("UUID", new int[]{1, 2, 3, 4});
        armorStandEntry.put("nbt", entityNbt);

        entities.add(armorStandEntry);
        root.put("entities", entities);

        return root;
    }

    // ---- peekMetadata tests (no MinecraftServer needed) -------------------------

    @Test
    void peekMetadata_returnsCorrectWidth() throws IOException {
        VanillaNbtParser.NbtMetadata meta = VanillaNbtParser.peekMetadata(fixtureFile);
        assertEquals(EXPECTED_WIDTH, meta.width(), "peekMetadata width should be " + EXPECTED_WIDTH);
    }

    @Test
    void peekMetadata_returnsCorrectHeight() throws IOException {
        VanillaNbtParser.NbtMetadata meta = VanillaNbtParser.peekMetadata(fixtureFile);
        assertEquals(EXPECTED_HEIGHT, meta.height(), "peekMetadata height should be " + EXPECTED_HEIGHT);
    }

    @Test
    void peekMetadata_returnsCorrectLength() throws IOException {
        VanillaNbtParser.NbtMetadata meta = VanillaNbtParser.peekMetadata(fixtureFile);
        assertEquals(EXPECTED_LENGTH, meta.length(), "peekMetadata length should be " + EXPECTED_LENGTH);
    }

    // ---- Full parse tests (no MinecraftServer — uses raw NBT walk) --------------

    private SchematicHolder loadFixture() throws IOException {
        // Pass null for server — VanillaNbtParser uses raw NBT walk (no StructureTemplate)
        return VanillaNbtParser.parse(fixtureFile, null);
    }

    @Test
    void parse_dimensions_matchFixture() throws IOException {
        SchematicHolder holder = loadFixture();
        assertAll(
            () -> assertEquals(EXPECTED_WIDTH, holder.width(), "width"),
            () -> assertEquals(EXPECTED_HEIGHT, holder.height(), "height"),
            () -> assertEquals(EXPECTED_LENGTH, holder.length(), "length")
        );
    }

    @Test
    void parse_blocksListSize_equalsWidthTimesHeightTimesLength() throws IOException {
        SchematicHolder holder = loadFixture();
        assertEquals(EXPECTED_TOTAL, holder.blocks().size(),
            "blocks list should contain all " + EXPECTED_TOTAL + " positions (including air)");
    }

    @Test
    void parse_noBlockPlacement_hasWasUnknownTrue() throws IOException {
        SchematicHolder holder = loadFixture();
        List<BlockPlacement> unknowns = holder.blocks().stream()
            .filter(BlockPlacement::wasUnknown)
            .toList();
        assertTrue(unknowns.isEmpty(),
            "No block should have wasUnknown=true for vanilla-only fixture; found: " + unknowns);
    }

    @Test
    void parse_chestBlock_hasNonNullBlockEntityNbt() throws IOException {
        SchematicHolder holder = loadFixture();
        // Chest is at [0, 1, 0]
        Optional<BlockPlacement> chestOpt = holder.blocks().stream()
            .filter(bp -> bp.relativePos().getX() == 0
                && bp.relativePos().getY() == 1
                && bp.relativePos().getZ() == 0)
            .findFirst();
        assertTrue(chestOpt.isPresent(), "Chest block at [0,1,0] should be present");
        assertNotNull(chestOpt.get().blockEntityNbt(),
            "Chest block should have non-null blockEntityNbt");
    }

    @Test
    void parse_blockEntityNbt_doesNotContainX() throws IOException {
        SchematicHolder holder = loadFixture();
        Optional<BlockPlacement> chestOpt = holder.blocks().stream()
            .filter(bp -> bp.blockEntityNbt() != null)
            .findFirst();
        assertTrue(chestOpt.isPresent(), "Should find block with blockEntityNbt");
        assertFalse(chestOpt.get().blockEntityNbt().contains("x"),
            "blockEntityNbt must not contain 'x' key after stripping");
    }

    @Test
    void parse_blockEntityNbt_doesNotContainY() throws IOException {
        SchematicHolder holder = loadFixture();
        Optional<BlockPlacement> chestOpt = holder.blocks().stream()
            .filter(bp -> bp.blockEntityNbt() != null)
            .findFirst();
        assertTrue(chestOpt.isPresent(), "Should find block with blockEntityNbt");
        assertFalse(chestOpt.get().blockEntityNbt().contains("y"),
            "blockEntityNbt must not contain 'y' key after stripping");
    }

    @Test
    void parse_blockEntityNbt_doesNotContainZ() throws IOException {
        SchematicHolder holder = loadFixture();
        Optional<BlockPlacement> chestOpt = holder.blocks().stream()
            .filter(bp -> bp.blockEntityNbt() != null)
            .findFirst();
        assertTrue(chestOpt.isPresent(), "Should find block with blockEntityNbt");
        assertFalse(chestOpt.get().blockEntityNbt().contains("z"),
            "blockEntityNbt must not contain 'z' key after stripping");
    }

    @Test
    void parse_blockEntityNbt_doesNotContainId() throws IOException {
        SchematicHolder holder = loadFixture();
        Optional<BlockPlacement> chestOpt = holder.blocks().stream()
            .filter(bp -> bp.blockEntityNbt() != null)
            .findFirst();
        assertTrue(chestOpt.isPresent(), "Should find block with blockEntityNbt");
        assertFalse(chestOpt.get().blockEntityNbt().contains("id"),
            "blockEntityNbt must not contain 'id' key after stripping");
    }

    @Test
    void parse_entity_relativePositionWithinBounds() throws IOException {
        SchematicHolder holder = loadFixture();
        assertFalse(holder.entities().isEmpty(), "Fixture should contain at least one entity");
        EntityPlacement ep = holder.entities().get(0);
        // armor_stand at [1.5, 0.0, 1.5] — must be within [0, width] x [0, height] x [0, length]
        assertTrue(ep.relativePos().x >= 0 && ep.relativePos().x <= EXPECTED_WIDTH,
            "entity X=" + ep.relativePos().x + " should be in [0," + EXPECTED_WIDTH + "]");
        assertTrue(ep.relativePos().y >= 0 && ep.relativePos().y <= EXPECTED_HEIGHT,
            "entity Y=" + ep.relativePos().y + " should be in [0," + EXPECTED_HEIGHT + "]");
        assertTrue(ep.relativePos().z >= 0 && ep.relativePos().z <= EXPECTED_LENGTH,
            "entity Z=" + ep.relativePos().z + " should be in [0," + EXPECTED_LENGTH + "]");
    }

    @Test
    void parse_entity_exactRelativePosition() throws IOException {
        SchematicHolder holder = loadFixture();
        assertFalse(holder.entities().isEmpty(), "Fixture should contain at least one entity");
        EntityPlacement ep = holder.entities().get(0);
        assertEquals(1.5, ep.relativePos().x, 0.001, "entity X should be 1.5");
        assertEquals(0.0, ep.relativePos().y, 0.001, "entity Y should be 0.0");
        assertEquals(1.5, ep.relativePos().z, 0.001, "entity Z should be 1.5");
    }

    @Test
    void parse_entityNbt_doesNotContainUUID() throws IOException {
        SchematicHolder holder = loadFixture();
        assertFalse(holder.entities().isEmpty(), "Fixture should contain at least one entity");
        CompoundTag entityNbt = holder.entities().get(0).entityNbt();
        assertFalse(entityNbt.contains("UUID"),
            "entityNbt must not contain 'UUID' key after stripping");
    }

    @Test
    void parse_spongeOffset_isZero() throws IOException {
        SchematicHolder holder = loadFixture();
        int[] offset = holder.spongeOffset();
        assertArrayEquals(new int[]{0, 0, 0}, offset,
            "VanillaNbtParser must use convenience constructor with spongeOffset={0,0,0}");
    }
}
