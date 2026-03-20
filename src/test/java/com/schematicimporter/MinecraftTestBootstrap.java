package com.schematicimporter;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Utility class that bootstraps Minecraft + NeoForge FML for unit tests.
 *
 * <p>Minecraft's {@code Bootstrap.bootStrap()} triggers {@code Blocks.<clinit>} which calls
 * NeoForge's {@code FeatureFlagLoader.loadModdedFlags()} which reads
 * {@code LoadingModList.get().getModFiles()}. In a unit test (outside the FML launch context),
 * {@code LoadingModList.get()} returns {@code null} — causing an NPE.</p>
 *
 * <p>The fix: call {@code LoadingModList.of(...)} with empty lists BEFORE
 * {@code Bootstrap.bootStrap()}. This gives FML an initialized but empty mod list,
 * so {@code getModFiles()} returns an empty stream and {@code FeatureFlagLoader} does nothing.
 * All built-in Minecraft blocks/items then initialize normally.</p>
 *
 * <p>Call {@link #init()} exactly once per test JVM, typically from a {@code @BeforeAll} in
 * your test class.</p>
 */
public final class MinecraftTestBootstrap {

    private static final Logger LOGGER = LogManager.getLogger(MinecraftTestBootstrap.class);
    private static volatile boolean initialized = false;

    private MinecraftTestBootstrap() {}

    /**
     * Initialize Minecraft and NeoForge registries for unit tests.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    public static synchronized void init() {
        if (initialized) return;

        // Step 1: Initialize a minimal (empty) LoadingModList so NeoForge's
        // FeatureFlagLoader.loadModdedFlags() does not NPE on LoadingModList.get()
        LoadingModList.of(
                List.of(),   // plugins (no mod files)
                List.of(),   // modFiles
                List.of(),   // sortedList
                List.of(),   // modLoadingIssues
                Map.of()     // modDependencies
        );
        LOGGER.debug("MinecraftTestBootstrap: LoadingModList initialized with empty mod list");

        // Step 2: Detect Minecraft version (required by some registry code)
        SharedConstants.tryDetectVersion();

        // Step 3: Bootstrap all Minecraft registries (Blocks, Items, EntityTypes, etc.)
        Bootstrap.bootStrap();
        LOGGER.debug("MinecraftTestBootstrap: Bootstrap.bootStrap() completed");

        initialized = true;
    }
}
