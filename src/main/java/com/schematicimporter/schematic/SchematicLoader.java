package com.schematicimporter.schematic;

import com.schematicimporter.config.ModConfig;
import com.schematicimporter.schematic.SpongeSchematicParser.SchematicMetadata;
import com.schematicimporter.schematic.VanillaNbtParser.NbtMetadata;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Scans the schematics folder and dispatches to the correct parser.
 *
 * <p>Used by:
 * <ul>
 *   <li>{@code /schem list} — calls {@link #listSchematics(MinecraftServer)} to display file names
 *       and dimensions cheaply (no block list built).</li>
 *   <li>{@code /schem load} — calls {@link #load(String, MinecraftServer)} to fully parse a file
 *       into a {@link SchematicHolder}.</li>
 * </ul>
 * </p>
 *
 * <p><b>Security:</b> {@link #load} normalizes the path and checks that it remains under the
 * schematics root. This prevents {@code ../} path traversal attacks where a malicious player
 * with command access could read arbitrary server files.</p>
 *
 * <p><b>Supported formats:</b>
 * <ul>
 *   <li>{@code .schem} — Sponge Schematic v2/v3, dispatched to {@link SpongeSchematicParser}</li>
 *   <li>{@code .nbt} — Vanilla Structure format, dispatched to {@link VanillaNbtParser}</li>
 * </ul>
 * </p>
 */
public class SchematicLoader {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SchematicLoader() {}

    /**
     * Lightweight metadata for a schematic file, returned by {@link #listSchematics}.
     *
     * <p>Only dimensions and file size are included — no block list is built during listing.</p>
     */
    public record SchematicFileInfo(
        String relativeName,
        long fileSizeBytes,
        int width,
        int height,
        int length
    ) {}

    /**
     * Scan the schematics folder recursively and return metadata for each file.
     *
     * <p>Uses {@link Files#walk} for recursive listing. Files with unreadable metadata
     * are silently skipped with a warning (not a fatal error).</p>
     *
     * @param server the running {@link MinecraftServer}
     * @return list of {@link SchematicFileInfo}, empty if the folder does not exist
     */
    public static List<SchematicFileInfo> listSchematics(MinecraftServer server) {
        Path root = getSchematicsRoot(server);
        if (!Files.isDirectory(root)) return List.of();

        List<SchematicFileInfo> result = new ArrayList<>();
        try (Stream<Path> walker = Files.walk(root)) {
            walker.filter(p -> {
                String name = p.getFileName().toString().toLowerCase();
                return name.endsWith(".schem") || name.endsWith(".nbt");
            }).forEach(p -> {
                try {
                    String relName = root.relativize(p).toString().replace('\\', '/');
                    long size = Files.size(p);
                    String lowerName = p.getFileName().toString().toLowerCase();
                    if (lowerName.endsWith(".schem")) {
                        SchematicMetadata meta = SpongeSchematicParser.peekMetadata(p);
                        result.add(new SchematicFileInfo(relName, size, meta.width(), meta.height(), meta.length()));
                    } else {
                        NbtMetadata meta = VanillaNbtParser.peekMetadata(p);
                        result.add(new SchematicFileInfo(relName, size, meta.width(), meta.height(), meta.length()));
                    }
                } catch (IOException e) {
                    LOGGER.warn("SchematicLoader: Failed to read metadata for {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            LOGGER.error("SchematicLoader: Failed to walk schematics directory '{}': {}", root, e.getMessage());
        }
        return result;
    }

    /**
     * Fully parse a schematic file by name (relative to the schematics folder).
     *
     * <p><b>Path traversal guard:</b> The name is resolved and normalized against the schematics
     * root. If the normalized path escapes the root directory (e.g. via {@code ../}),
     * an {@link IOException} is thrown immediately.</p>
     *
     * @param name   relative name, e.g. {@code "castle.schem"} or {@code "structures/tower.nbt"}
     * @param server the running {@link MinecraftServer}
     * @return fully parsed {@link SchematicHolder}
     * @throws IOException if the file does not exist, has an unknown format, or path traversal
     *                     is detected
     */
    public static SchematicHolder load(String name, MinecraftServer server) throws IOException {
        Path root = getSchematicsRoot(server);
        Path target = root.resolve(name).normalize();

        // Path traversal guard: normalized path must remain under the root
        if (!target.startsWith(root)) {
            throw new IOException("Path traversal attempt rejected: " + name);
        }

        if (!Files.exists(target)) {
            throw new IOException("Schematic not found: " + name);
        }

        String lowerName = name.toLowerCase();
        if (lowerName.endsWith(".schem")) {
            return SpongeSchematicParser.parse(target, server.registryAccess());
        } else if (lowerName.endsWith(".nbt")) {
            return VanillaNbtParser.parse(target, server);
        } else {
            throw new IOException("Unknown schematic format (must be .schem or .nbt): " + name);
        }
    }

    /**
     * Resolve the schematics folder path from config.
     *
     * @param server the running {@link MinecraftServer}
     * @return absolute path to the schematics folder
     */
    public static Path getSchematicsRoot(MinecraftServer server) {
        String folder = ModConfig.CONFIG.schematicsFolder.get();
        return server.getServerDirectory().resolve(folder);
    }
}
