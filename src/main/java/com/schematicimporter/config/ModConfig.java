package com.schematicimporter.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side configuration for SchematicImporter.
 * Config file: {@code <server>/config/schematicimporter-server.toml}
 * Not synced to clients — these are admin-only settings.
 */
public class ModConfig {

    public static final ModConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;

    /** How many blocks to place per server tick during async paste. Default: 100, range: 1–2000. */
    public final ModConfigSpec.IntValue blocksPerTick;

    /** Path to the schematics folder, relative to server root. Default: "schematics". */
    public final ModConfigSpec.ConfigValue<String> schematicsFolder;

    /** Maximum number of undo levels per player. Default: 5, range: 0–20. 0 disables undo. */
    public final ModConfigSpec.IntValue maxUndoLevels;

    private ModConfig(ModConfigSpec.Builder builder) {
        blocksPerTick = builder
                .comment("Blocks to place per server tick during async paste (default: 100, range: 1-2000)")
                .defineInRange("blocks_per_tick", 100, 1, 2000);

        schematicsFolder = builder
                .comment("Path to schematics folder, relative to server root (default: schematics)")
                .define("schematics_folder", "schematics");

        maxUndoLevels = builder
                .comment("Maximum undo levels per player (default: 5, range: 0-20, 0 disables undo)")
                .defineInRange("max_undo_levels", 5, 0, 20);
    }

    static {
        var pair = new ModConfigSpec.Builder().configure(ModConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }
}
