package com.schematicimporter;

import com.schematicimporter.config.ModConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.common.Mod;

/**
 * Main entry point for the SchematicImporter mod.
 * Runs on both dedicated server and singleplayer logical server.
 * No {@code dist} restriction — use {@code displayTest = "IGNORE_SERVER_VERSION"} in
 * neoforge.mods.toml to allow vanilla clients to join without mod-mismatch errors.
 */
@Mod("schematicimporter")
public class SchematicImporterMod {

    public SchematicImporterMod(IEventBus modBus, ModContainer container) {
        // Register server-side config (per-world, not synced to clients)
        container.registerConfig(Type.SERVER, ModConfig.CONFIG_SPEC);
    }
}
