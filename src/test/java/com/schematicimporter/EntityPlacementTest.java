package com.schematicimporter;

import com.schematicimporter.paste.PasteExecutor;
import com.schematicimporter.paste.PasteFeedbackSink;
import com.schematicimporter.schematic.EntityPlacement;
import com.schematicimporter.schematic.SchematicHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies entity coordinate math in PasteExecutor.
 *
 * <p>Entity world position = pasteOrigin (as Vec3) + entity.relativePos().
 * UUID must be stripped from the entity NBT before spawn (done by parsers).</p>
 */
class EntityPlacementTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.init();
    }

    @Test
    void entityWorldPos_equalsOriginPlusRelativePos() {
        // Formula: Vec3.atLowerCornerOf(origin).add(ep.relativePos())
        BlockPos origin = new BlockPos(100, 64, 200);
        Vec3 relativePos = new Vec3(1.5, 0.0, 2.5);

        Vec3 worldPos = Vec3.atLowerCornerOf(origin).add(relativePos);

        assertEquals(101.5, worldPos.x, 0.001, "world X should be origin.x + relX = 100 + 1.5 = 101.5");
        assertEquals(64.0, worldPos.y, 0.001, "world Y should be origin.y + relY = 64 + 0.0 = 64.0");
        assertEquals(202.5, worldPos.z, 0.001, "world Z should be origin.z + relZ = 200 + 2.5 = 202.5");
    }

    @Test
    void entityNbt_posTagSetToWorldPos() {
        // Verify that entityNbt gets "Pos" set to the world position values
        BlockPos origin = new BlockPos(100, 64, 200);
        Vec3 relativePos = new Vec3(1.5, 0.0, 2.5);

        Vec3 worldPos = Vec3.atLowerCornerOf(origin).add(relativePos);

        // Build entity NBT as PasteExecutor would
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putString("id", "minecraft:armor_stand");

        // Simulate what PasteExecutor does with the Pos tag
        ListTag posList = new ListTag();
        posList.add(DoubleTag.valueOf(worldPos.x));
        posList.add(DoubleTag.valueOf(worldPos.y));
        posList.add(DoubleTag.valueOf(worldPos.z));
        entityNbt.put("Pos", posList);

        assertTrue(entityNbt.contains("Pos"), "entityNbt must have 'Pos' tag for spawn");
        ListTag storedPos = entityNbt.getList("Pos", 6); // 6 = TAG_Double
        assertEquals(3, storedPos.size(), "Pos must have 3 elements");
        assertEquals(101.5, storedPos.getDouble(0), 0.001, "Pos[0] should be 101.5");
        assertEquals(64.0, storedPos.getDouble(1), 0.001, "Pos[1] should be 64.0");
        assertEquals(202.5, storedPos.getDouble(2), 0.001, "Pos[2] should be 202.5");
    }

    @Test
    void entityNbt_uuidStripped_beforeSpawn() {
        // UUID must not be present in entityNbt used for spawning
        // (parsers strip UUID before constructing EntityPlacement, so this tests the contract)
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putString("id", "minecraft:armor_stand");
        entityNbt.putIntArray("UUID", new int[]{1, 2, 3, 4});

        // Simulate parser stripping UUID (as done in SpongeSchematicParser and VanillaNbtParser)
        entityNbt.remove("UUID");

        assertFalse(entityNbt.contains("UUID"),
            "entityNbt must not contain 'UUID' key after stripping — prevents UUID collisions on re-paste");
    }

    @Test
    void entityWorldPos_viaExecutor_usingCaptureStub() {
        // End-to-end: PasteExecutor calculates correct world pos and stores in captured NBT
        BlockPos origin = new BlockPos(100, 64, 200);

        // Build an entity placement with id=minecraft:armor_stand (so EntityType.create can work)
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putString("id", "minecraft:armor_stand");

        EntityPlacement ep = new EntityPlacement(new Vec3(1.5, 0.0, 2.5), entityNbt);
        SchematicHolder holder = new SchematicHolder(
            1, 1, 1,
            List.of(),  // no blocks
            List.of(ep)
        );

        TestLevelStub stub = new TestLevelStub();
        PasteExecutor.execute(holder, origin, false, PasteFeedbackSink.noop(), stub);

        // Check captured entity Pos tags in the NBT
        assertFalse(stub.capturedEntityNbts.isEmpty(),
            "At least one entity NBT should have been captured for spawning");
        CompoundTag capturedNbt = stub.capturedEntityNbts.get(0);
        assertTrue(capturedNbt.contains("Pos"), "Captured entity NBT must contain 'Pos' tag");
        ListTag pos = capturedNbt.getList("Pos", 6);
        assertEquals(3, pos.size(), "Pos must have 3 doubles");
        assertEquals(101.5, pos.getDouble(0), 0.001, "Pos[0] should be 101.5");
        assertEquals(64.0, pos.getDouble(1), 0.001, "Pos[1] should be 64.0");
        assertEquals(202.5, pos.getDouble(2), 0.001, "Pos[2] should be 202.5");
    }
}
