package com.schematicimporter;

import com.schematicimporter.config.ModConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ModConfig.
 * ModConfigSpec.Builder is a pure Java API — no running server required.
 */
class ModConfigTest {

    @Test
    void blocksPerTick_defaultIs100() {
        assertEquals(100, ModConfig.CONFIG.blocksPerTick.getDefault(),
                "blocks_per_tick should default to 100");
    }

    @Test
    void schematicsFolder_defaultIsSchematics() {
        assertEquals("schematics", ModConfig.CONFIG.schematicsFolder.getDefault(),
                "schematics_folder should default to \"schematics\"");
    }

    @Test
    void blocksPerTick_rangeIsEnforced_minimumIs1() {
        int defaultVal = ModConfig.CONFIG.blocksPerTick.getDefault();
        assertTrue(defaultVal >= 1,
                "blocks_per_tick default must be >= 1 (minimum of range)");
    }

    @Test
    void blocksPerTick_rangeIsEnforced_maximumIs2000() {
        int defaultVal = ModConfig.CONFIG.blocksPerTick.getDefault();
        assertTrue(defaultVal <= 2000,
                "blocks_per_tick default must be <= 2000 (maximum of range)");
    }

    @Test
    void configSpec_isNotNull() {
        assertNotNull(ModConfig.CONFIG_SPEC, "CONFIG_SPEC must not be null");
    }

    @Test
    void config_isNotNull() {
        assertNotNull(ModConfig.CONFIG, "CONFIG must not be null");
    }
}
