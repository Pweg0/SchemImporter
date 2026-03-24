package com.schematicimporter.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.schematicimporter.schematic.BlockPlacement;
import com.schematicimporter.schematic.SchematicHolder;
import com.schematicimporter.schematic.SchematicLoader;
import com.schematicimporter.schematic.SchematicLoader.SchematicFileInfo;
import com.schematicimporter.schematic.SchematicRotator;
import com.schematicimporter.session.PasteSession;
import com.schematicimporter.session.SessionManager;
import com.schematicimporter.session.SessionState;
import com.schematicimporter.paste.PasteExecutor;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static com.schematicimporter.command.ServerTranslations.get;

@EventBusSubscriber(modid = "schematicimporter")
public class CommandHandler {

    private CommandHandler() {}

    /** Tab-complete suggestion provider that lists available schematic files. */
    private static final SuggestionProvider<CommandSourceStack> SCHEMATIC_SUGGESTIONS =
        (ctx, builder) -> {
            List<SchematicFileInfo> files = SchematicLoader.listSchematics(ctx.getSource().getServer());
            return SharedSuggestionProvider.suggest(
                files.stream().map(SchematicFileInfo::relativeName), builder);
        };

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
                        .suggests(SCHEMATIC_SUGGESTIONS)
                        .executes(ctx -> executeLoad(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("paste")
                    .executes(ctx -> executePaste(ctx.getSource(), false, false))
                    .then(Commands.literal("--ignore-air")
                        .executes(ctx -> executePaste(ctx.getSource(), true, false))
                        .then(Commands.literal("--use-offset")
                            .executes(ctx -> executePaste(ctx.getSource(), true, true))))
                    .then(Commands.literal("--use-offset")
                        .executes(ctx -> executePaste(ctx.getSource(), false, true))
                        .then(Commands.literal("--ignore-air")
                            .executes(ctx -> executePaste(ctx.getSource(), true, true)))))
                .then(Commands.literal("cancel")
                    .executes(ctx -> executeCancel(ctx.getSource())))
                .then(Commands.literal("rotate")
                    .then(Commands.argument("degrees", IntegerArgumentType.integer())
                        .executes(ctx -> executeRotate(ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "degrees")))))
                .then(Commands.literal("preview")
                    .executes(ctx -> executePreview(ctx.getSource())))
                .then(Commands.literal("nudge")
                    .then(Commands.literal("north")
                        .executes(ctx -> executeNudge(ctx.getSource(), "north", 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> executeNudge(ctx.getSource(), "north",
                                IntegerArgumentType.getInteger(ctx, "amount")))))
                    .then(Commands.literal("south")
                        .executes(ctx -> executeNudge(ctx.getSource(), "south", 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> executeNudge(ctx.getSource(), "south",
                                IntegerArgumentType.getInteger(ctx, "amount")))))
                    .then(Commands.literal("east")
                        .executes(ctx -> executeNudge(ctx.getSource(), "east", 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> executeNudge(ctx.getSource(), "east",
                                IntegerArgumentType.getInteger(ctx, "amount")))))
                    .then(Commands.literal("west")
                        .executes(ctx -> executeNudge(ctx.getSource(), "west", 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> executeNudge(ctx.getSource(), "west",
                                IntegerArgumentType.getInteger(ctx, "amount")))))
                    .then(Commands.literal("up")
                        .executes(ctx -> executeNudge(ctx.getSource(), "up", 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> executeNudge(ctx.getSource(), "up",
                                IntegerArgumentType.getInteger(ctx, "amount")))))
                    .then(Commands.literal("down")
                        .executes(ctx -> executeNudge(ctx.getSource(), "down", 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> executeNudge(ctx.getSource(), "down",
                                IntegerArgumentType.getInteger(ctx, "amount"))))))
                .then(Commands.literal("confirm")
                    .executes(ctx -> executeConfirm(ctx.getSource(), false, false))
                    .then(Commands.literal("--ignore-air")
                        .executes(ctx -> executeConfirm(ctx.getSource(), true, false))
                        .then(Commands.literal("--use-offset")
                            .executes(ctx -> executeConfirm(ctx.getSource(), true, true))))
                    .then(Commands.literal("--use-offset")
                        .executes(ctx -> executeConfirm(ctx.getSource(), false, true))
                        .then(Commands.literal("--ignore-air")
                            .executes(ctx -> executeConfirm(ctx.getSource(), true, true)))))
                .then(Commands.literal("undo")
                    .executes(ctx -> executeUndo(ctx.getSource())))
        );
    }

    private static int executeList(net.minecraft.commands.CommandSourceStack source) {
        MinecraftServer server = source.getServer();

        try {
            java.nio.file.Path root = SchematicLoader.getSchematicsRoot(server);
            if (!Files.isDirectory(root)) {
                Files.createDirectories(root);
            }
        } catch (IOException e) {
            // Non-fatal
        }

        List<SchematicFileInfo> files = SchematicLoader.listSchematics(server);

        if (files.isEmpty()) {
            source.sendFailure(
                Component.literal(get("schematicimporter.list.empty"))
                    .withStyle(ChatFormatting.YELLOW)
            );
            return 0;
        }

        // Header with separator
        source.sendSuccess(
            () -> Component.literal("--- ")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(get("schematicimporter.list.header", files.size()))
                    .withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" ---")
                    .withStyle(ChatFormatting.DARK_GRAY)),
            false
        );

        for (int i = 0; i < files.size(); i++) {
            final SchematicFileInfo fi = files.get(i);
            final int index = i + 1;
            final String loadCmd = "/schem load " + fi.relativeName();

            // Clickable name that auto-fills the load command
            MutableComponent nameComponent = Component.literal(fi.relativeName())
                .withStyle(Style.EMPTY
                    .withColor(ChatFormatting.GREEN)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, loadCmd))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Click to load\n")
                            .withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal(loadCmd)
                                .withStyle(ChatFormatting.GRAY)))));

            // Build: " 1. name  |  1.2 KB  |  10x5x10"
            MutableComponent line = Component.literal(" " + index + ". ")
                .withStyle(ChatFormatting.GRAY)
                .append(nameComponent)
                .append(Component.literal("  |  ")
                    .withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(formatSize(fi.fileSizeBytes()))
                    .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("  |  ")
                    .withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(fi.width() + "x" + fi.height() + "x" + fi.length())
                    .withStyle(ChatFormatting.WHITE));

            source.sendSuccess(() -> line, false);
        }

        // Footer hint
        source.sendSuccess(
            () -> Component.literal("Click a name or use ")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("/schem load <name>")
                    .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" (Tab to autocomplete)")
                    .withStyle(ChatFormatting.DARK_GRAY)),
            false
        );

        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    private static int executeLoad(net.minecraft.commands.CommandSourceStack source, String name) {
        MinecraftServer server = source.getServer();

        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(
                Component.literal(get("schematicimporter.error.player_only"))
                    .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        PasteSession session = SessionManager.INSTANCE.getOrCreate(player.getUUID());

        // Reject load during active paste (D-12)
        if (session.getState() == SessionState.PASTING) {
            source.sendFailure(
                Component.literal(get("schematicimporter.error.paste_in_progress"))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        SchematicHolder holder;
        try {
            holder = SchematicLoader.load(name, server);
        } catch (IOException e) {
            final String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            source.sendFailure(
                Component.literal(get("schematicimporter.error.load_failed", name, msg))
                    .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

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
            final int w = holder.width(), h = holder.height(), l = holder.length();
            source.sendSuccess(
                () -> Component.literal(get("schematicimporter.load.success", name, blockCount))
                    .withStyle(ChatFormatting.GREEN),
                false
            );

            // Auto-preview: enter preview at player's feet immediately after load
            session.startPreview(player.blockPosition());
            source.sendSuccess(
                () -> Component.literal(get("schematicimporter.preview.auto", w, h, l))
                    .withStyle(ChatFormatting.GRAY),
                false
            );
        } else {
            final String unknownList = String.join(", ", unknownNames);
            source.sendSuccess(
                () -> Component.literal(get("schematicimporter.warn.unknown_blocks", unknownList))
                    .withStyle(ChatFormatting.YELLOW),
                false
            );
            source.sendSuccess(
                () -> Component.literal(get("schematicimporter.warn.confirm_required"))
                    .withStyle(ChatFormatting.YELLOW),
                false
            );
            session.setPendingConfirmation(true);
        }

        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    private static int executePaste(net.minecraft.commands.CommandSourceStack source, boolean ignoreAir, boolean useOffset) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(
                Component.literal(get("schematicimporter.error.player_only"))
                    .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        PasteSession session = SessionManager.INSTANCE.getOrCreate(player.getUUID());

        // Prevent double-paste
        if (session.getState() == SessionState.PASTING) {
            source.sendFailure(
                Component.literal(get("schematicimporter.error.paste_in_progress"))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (session.getState() == SessionState.IDLE || session.getLoadedSchematic() == null) {
            source.sendFailure(
                Component.literal(get("schematicimporter.error.no_schematic_loaded"))
                    .withStyle(ChatFormatting.RED)
            );
            return 0;
        }

        if (session.isPendingConfirmation()) {
            session.setPendingConfirmation(false);
        }

        // If previewing, use the effective paste position (with nudge); otherwise use player position
        BlockPos baseOrigin = session.getState() == SessionState.PREVIEWING && session.getEffectivePastePos() != null
            ? session.getEffectivePastePos()
            : player.blockPosition();
        BlockPos origin = applyOffset(baseOrigin, session.getLoadedSchematic(), useOffset);
        ServerLevel level = (ServerLevel) player.level();

        final int totalBlocks = session.getLoadedSchematic().blocks().size();
        PasteExecutor.startAsync(session.getLoadedSchematic(), origin, ignoreAir, player.getUUID(), level);

        source.sendSuccess(
            () -> Component.literal(get("schematicimporter.paste.started", String.format("%,d", totalBlocks)))
                .withStyle(ChatFormatting.AQUA),
            false);

        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    private static int executeCancel(net.minecraft.commands.CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(
                Component.literal(get("schematicimporter.error.player_only"))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        PasteSession session = SessionManager.INSTANCE.getOrCreate(player.getUUID());
        if (session.getState() == SessionState.PREVIEWING) {
            session.cancelPreview();
            source.sendSuccess(
                () -> Component.literal(get("schematicimporter.preview.cancelled"))
                    .withStyle(ChatFormatting.YELLOW),
                false);
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        }

        if (session.getState() != SessionState.PASTING) {
            source.sendFailure(
                Component.literal(get("schematicimporter.error.no_active_paste"))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        PasteExecutor.requestCancel(player.getUUID());
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    private static int executeRotate(net.minecraft.commands.CommandSourceStack source, int degrees) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(
                Component.literal(get("schematicimporter.error.player_only"))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        PasteSession session = SessionManager.INSTANCE.getOrCreate(player.getUUID());
        if (session.getLoadedSchematic() == null ||
                (session.getState() != SessionState.LOADED && session.getState() != SessionState.PREVIEWING)) {
            source.sendFailure(
                Component.literal(get("schematicimporter.error.no_schematic_loaded"))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        Rotation rotation = switch (degrees) {
            case 90 -> Rotation.CLOCKWISE_90;
            case 180 -> Rotation.CLOCKWISE_180;
            case 270 -> Rotation.COUNTERCLOCKWISE_90;
            default -> null;
        };

        if (rotation == null) {
            source.sendFailure(
                Component.literal(get("schematicimporter.error.invalid_rotation"))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // Capture preview state before load resets it
        boolean wasPreviewing = session.getState() == SessionState.PREVIEWING;
        BlockPos previewPos = session.getEffectivePastePos();

        SchematicHolder rotated = SchematicRotator.rotate(session.getLoadedSchematic(), rotation);
        session.load(session.getLoadedName(), rotated);

        // Re-enter preview at the same position so particles update with new rotation
        if (wasPreviewing && previewPos != null) {
            session.startPreview(previewPos);
        }

        final int deg = degrees;
        source.sendSuccess(
            () -> Component.literal(get("schematicimporter.rotate.success", deg,
                rotated.width(), rotated.height(), rotated.length()))
                .withStyle(ChatFormatting.GREEN),
            false);

        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    private static int executePreview(net.minecraft.commands.CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(
                Component.literal(get("schematicimporter.error.player_only"))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        PasteSession session = SessionManager.INSTANCE.getOrCreate(player.getUUID());
        if (session.getLoadedSchematic() == null ||
                (session.getState() != SessionState.LOADED && session.getState() != SessionState.AWAITING_CONFIRMATION)) {
            source.sendFailure(
                Component.literal(get("schematicimporter.error.no_schematic_loaded"))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (session.getState() == SessionState.PASTING) {
            source.sendFailure(
                Component.literal(get("schematicimporter.error.paste_in_progress"))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        session.startPreview(player.blockPosition());

        SchematicHolder holder = session.getLoadedSchematic();
        source.sendSuccess(
            () -> Component.literal(get("schematicimporter.preview.started",
                holder.width(), holder.height(), holder.length()))
                .withStyle(ChatFormatting.AQUA),
            false);
        source.sendSuccess(
            () -> Component.literal(get("schematicimporter.preview.hint"))
                .withStyle(ChatFormatting.GRAY),
            false);

        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    private static int executeNudge(net.minecraft.commands.CommandSourceStack source, String direction, int amount) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(
                Component.literal(get("schematicimporter.error.player_only"))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        PasteSession session = SessionManager.INSTANCE.getOrCreate(player.getUUID());
        if (session.getState() != SessionState.PREVIEWING) {
            source.sendFailure(
                Component.literal(get("schematicimporter.error.not_previewing"))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        BlockPos delta = switch (direction) {
            case "north" -> new BlockPos(0, 0, -amount);
            case "south" -> new BlockPos(0, 0, amount);
            case "east" -> new BlockPos(amount, 0, 0);
            case "west" -> new BlockPos(-amount, 0, 0);
            case "up" -> new BlockPos(0, amount, 0);
            case "down" -> new BlockPos(0, -amount, 0);
            default -> BlockPos.ZERO;
        };

        session.nudge(delta);
        BlockPos effective = session.getEffectivePastePos();
        final String dir = direction;
        final int amt = amount;
        source.sendSuccess(
            () -> Component.literal(get("schematicimporter.nudge.success", dir, amt,
                effective.getX(), effective.getY(), effective.getZ()))
                .withStyle(ChatFormatting.GREEN),
            false);

        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    private static int executeConfirm(net.minecraft.commands.CommandSourceStack source, boolean ignoreAir, boolean useOffset) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(
                Component.literal(get("schematicimporter.error.player_only"))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        PasteSession session = SessionManager.INSTANCE.getOrCreate(player.getUUID());
        if (session.getState() != SessionState.PREVIEWING) {
            source.sendFailure(
                Component.literal(get("schematicimporter.error.not_previewing"))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        BlockPos origin = session.getEffectivePastePos();
        if (origin == null || session.getLoadedSchematic() == null) {
            source.sendFailure(
                Component.literal(get("schematicimporter.error.no_schematic_loaded"))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        origin = applyOffset(origin, session.getLoadedSchematic(), useOffset);
        ServerLevel level = (ServerLevel) player.level();
        final int totalBlocks = session.getLoadedSchematic().blocks().size();
        PasteExecutor.startAsync(session.getLoadedSchematic(), origin, ignoreAir, player.getUUID(), level);

        source.sendSuccess(
            () -> Component.literal(get("schematicimporter.paste.started", String.format("%,d", totalBlocks)))
                .withStyle(ChatFormatting.AQUA),
            false);

        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    private static int executeUndo(net.minecraft.commands.CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(
                Component.literal(get("schematicimporter.error.player_only"))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        PasteSession session = SessionManager.INSTANCE.getOrCreate(player.getUUID());
        if (session.getState() == SessionState.PASTING) {
            source.sendFailure(
                Component.literal(get("schematicimporter.error.paste_in_progress"))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        ServerLevel level = (ServerLevel) player.level();
        boolean started = PasteExecutor.startUndo(player.getUUID(), level);
        if (!started) {
            source.sendFailure(
                Component.literal(get("schematicimporter.error.no_undo"))
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        source.sendSuccess(
            () -> Component.literal(get("schematicimporter.undo.started"))
                .withStyle(ChatFormatting.AQUA),
            false);
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    /**
     * Apply the Sponge offset to the origin if --use-offset is set.
     * Offset values are typically negative (e.g., {-5, 0, -3}).
     */
    private static BlockPos applyOffset(BlockPos origin, SchematicHolder holder, boolean useOffset) {
        if (!useOffset) return origin;
        int[] offset = holder.spongeOffset();
        return origin.offset(offset[0], offset[1], offset[2]);
    }

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
