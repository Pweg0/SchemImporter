package com.schematicimporter.paste;

import com.schematicimporter.config.ModConfig;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player stack of {@link UndoSnapshot}s for multi-level undo.
 *
 * <p>Each player has an independent undo stack. The stack depth is controlled
 * by {@link ModConfig#maxUndoLevels}. When the stack is full, the oldest
 * snapshot is discarded to make room for the new one.</p>
 *
 * <p>Not thread-safe — all access must occur on the main server thread.</p>
 */
public class UndoManager {

    public static final UndoManager INSTANCE = new UndoManager();

    private final Map<UUID, Deque<UndoSnapshot>> stacks = new HashMap<>();

    private UndoManager() {}

    /**
     * Push a new snapshot onto the player's undo stack.
     * Evicts the oldest snapshot if the stack exceeds max_undo_levels.
     */
    public void push(UUID playerUuid, UndoSnapshot snapshot) {
        Deque<UndoSnapshot> stack = stacks.computeIfAbsent(playerUuid, k -> new ArrayDeque<>());
        int maxLevels = ModConfig.CONFIG.maxUndoLevels.get();
        while (stack.size() >= maxLevels) {
            stack.removeFirst(); // Discard oldest
        }
        stack.addLast(snapshot);
    }

    /**
     * Pop the most recent snapshot from the player's undo stack.
     *
     * @return the snapshot, or null if no undo is available
     */
    public @Nullable UndoSnapshot pop(UUID playerUuid) {
        Deque<UndoSnapshot> stack = stacks.get(playerUuid);
        if (stack == null || stack.isEmpty()) return null;
        return stack.removeLast();
    }

    /**
     * Returns how many undo levels are available for the given player.
     */
    public int size(UUID playerUuid) {
        Deque<UndoSnapshot> stack = stacks.get(playerUuid);
        return stack == null ? 0 : stack.size();
    }

    /**
     * Clear all undo data for a player (e.g., on disconnect).
     */
    public void clear(UUID playerUuid) {
        stacks.remove(playerUuid);
    }
}
