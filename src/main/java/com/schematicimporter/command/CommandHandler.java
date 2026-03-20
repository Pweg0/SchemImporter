package com.schematicimporter.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.schematicimporter.schematic.BlockPlacement;
import com.schematicimporter.schematic.SchematicHolder;
import com.schematicimporter.schematic.SchematicLoader;
import com.schematicimporter.schematic.SchematicLoader.SchematicFileInfo;
import com.schematicimporter.session.PasteSession;
import com.schematicimporter.session.SessionManager;
import com.schematicimporter.session.SessionState;
import com.schematicimporter.paste.PasteExecutor;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;


import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Registers the {@code /schem} command tree via the game event bus.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>{@code /schem list} — lists all schematics in the schematics folder</li>
 *   <li>{@code /schem load <name>} — loads a schematic into the operator's session</li>
 *   <li>{@code /schem paste} — pastes the loaded schematic at the operator's feet</li>
 *   <li>{@code /schem paste --ignore-air} — paste without replacing existing blocks with air</li>
 * </ul>
 *
 * <p>All subcommands require OP level 2+ via {@code .requires(src -> src.hasPermission(2))}.
 * All user-facing messages use {@link Component#translatable} — no hardcoded English strings.</p>
 *
 * <p>Registration pattern: {@code @EventBusSubscriber} defaults to the GAME bus in NeoForge 1.21.x,
 * which is where {@link RegisterCommandsEvent} is posted.</p>
 */
@EventBusSubscriber(modid = "schematicimporter")
public class CommandHandler {

    private CommandHandler() {}

    /**
     * Registers all {@code /schem} subcommands with the Brigadier dispatcher.
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("schem")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("list")
                    .executes(ctx -> executeList(ctx.getSource())))
                .then(Commands.literal("load")
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> executeLoad(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("paste")
                    .executes(ctx -> executePaste(ctx.getSource(), false))
                    .then(Commands.literal("--ignore-air")
                        .executes(ctx -> executePaste(ctx.getSource(), true))))
        );
    }

    // =========================================================================
    // /schem list
    // =========================================================================

    /**
     * Lists all schematic files found in the schematics folder with their dimensions
     * and file sizes.
     *
     * <p>Creates the schematics directory if it does not exist (first-run UX).</p>
     */
    private static int executeList(net.minecraft.commands.CommandSourceStack source) {
        MinecraftServer server = source.getServer();

        // Create schematics directory on first use
        try {
            java.nio.file.Path root = SchematicLoader.getSchematicsRoot(server);
            if (!Files.isDirectory(root)) {
                Files.createDirectories(root);
            }
        } catch (IOException e) {
            // Non-fatal — listSchematics will return empty list
        }

        List<SchematicFileInfo> files = SchematicLoader.listSchematics(server);

        if (files.isEmpty()) {
            source.sendFailure(
                Component.translatable("schematicimporter.list.empty")
                    .withStyle(ChatFormatting.YELLOW)
            );
            return 0;
        }

        // Send header
        source.sendSuccess(
            () -> Component.translatable("schematicimporter.list.header", files.size())
                .withStyle(ChatFormatting.AQUA),
            false
        );

        // Send one line per file
        for (SchematicFileInfo info : files) {
            final SchematicFileInfo fi = info;
            source.sendSuccess(
                () -> Component.translatable(
                    "schematicimporter.list.entry",
                    fi.relativeName(),
                    formatSize(fi.fileSizeBytes()),
                    fi.width(),
                    fi.height(),
                    fi.length()
                ).withStyle(ChatFormatting.WHITE),
                false
            );
        }

        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    // =========================================================================
    // /schem load <name>
    // =========================================================================

    /**
     * Loads a schematic file into the operator's session. If unknown blocks are
     * found, the session is put in {@link SessionState#AWAITING_CONFIRMATION} and
     * the operator must run {@code /schem paste} again to confirm the paste.
     */
    private static int executeLoad(net.minecraft.commands.CommandSourceStack source, String name) {
        MinecraftServer server = source.getServer();

        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(
                Component.translatable("schematicimporter.error.player_only")
                    .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        PasteSession session = SessionManager.INSTANCE.getOrCreate(player.getUUID());

        SchematicHolder holder;
        try {
            holder = SchematicLoader.load(name, server);
        } catch (IOException e) {
            final String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            source.sendFailure(
                Component.translatable("schematicimporter.error.load_failed", name, msg)
                    .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        // Collect unknown block palette keys
        List<String> unknownNames = new ArrayList<>();
        for (BlockPlacement bp : holder.blocks()) {
            if (bp.wasUnknown() && bp.originalPaletteKey() != null) {
                String key = bp.originalPaletteKey();
                if (!unknownNames.contains(key)) {
                    unknownNames.add(key);
                }
            }
        }

        session.load(name, holder);

        if (unknownNames.isEmpty()) {
            final int blockCount = holder.blocks().size();
            source.sendSuccess(
                () -> Component.translatable(
                    "schematicimporter.load.success",
                    name,
                    blockCount
                ).withStyle(ChatFormatting.GREEN),
                false
            );
        } else {
            // Warn about unknown blocks and require confirmation
            final String unknownList = String.join(", ", unknownNames);
            source.sendSuccess(
                () -> Component.translatable(
                    "schematicimporter.warn.unknown_blocks",
                    unknownList
                ).withStyle(ChatFormatting.YELLOW),
                false
            );
            source.sendSuccess(
                () -> Component.translatable("schematicimporter.warn.confirm_required")
                    .withStyle(ChatFormatting.YELLOW),
                false
            );
            session.setPendingConfirmation(true);
        }

        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    // =========================================================================
    // /schem paste [--ignore-air]
    // =========================================================================

    /**
     * Pastes the loaded schematic at the operator's feet.
     *
     * <p>If the session is in {@link SessionState#AWAITING_CONFIRMATION} (unknown blocks
     * were found during load), the first {@code /schem paste} call clears the confirmation
     * flag and proceeds with the paste — this is the "second paste confirms" flow.</p>
     */
    private static int executePaste(net.minecraft.commands.CommandSourceStack source, boolean ignoreAir) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(
                Component.translatable("schematicimporter.error.player_only")
                    .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        PasteSession session = SessionManager.INSTANCE.getOrCreate(player.getUUID());

        // Require a loaded schematic
        if (session.getState() == SessionState.IDLE || session.getLoadedSchematic() == null) {
            source.sendFailure(
                Component.translatable("schematicimporter.error.no_schematic_loaded")
                    .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        // Handle pending confirmation (unknown blocks warning): clear flag and proceed
        if (session.isPendingConfirmation()) {
            session.setPendingConfirmation(false);
        }

        BlockPos origin = player.blockPosition();
        ServerLevel level = (ServerLevel) player.level();

        PasteExecutor.execute(session.getLoadedSchematic(), origin, ignoreAir, source, level);

        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Format a byte count as a human-readable file size (e.g., "42 KB", "1.2 MB").
     *
     * @param bytes number of bytes
     * @return formatted string
     */
    private static String formatSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        } else if (bytes < 1024L * 1024L) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024L * 1024L * 1024L) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}
