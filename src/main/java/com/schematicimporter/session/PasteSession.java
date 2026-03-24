package com.schematicimporter.session;

import com.schematicimporter.paste.AsyncPasteTask;
import com.schematicimporter.schematic.SchematicHolder;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Per-player paste session — tracks the loaded schematic and paste state.
 *
 * <p>State transitions:
 * <pre>
 *   IDLE → LOADED                     (via {@link #load})
 *   LOADED → AWAITING_CONFIRMATION    (via {@link #setPendingConfirmation(boolean)} true)
 *   AWAITING_CONFIRMATION → IDLE      (via {@link #reset} or after paste completes)
 *   LOADED → PREVIEWING               (via {@link #startPreview(BlockPos)})
 *   LOADED → PASTING                  (via {@link #startPasting(AsyncPasteTask)})
 *   PREVIEWING → PASTING              (via {@link #startPasting(AsyncPasteTask)} from confirm)
 *   PREVIEWING → LOADED               (via {@link #cancelPreview()})
 *   AWAITING_CONFIRMATION → PASTING   (via {@link #startPasting(AsyncPasteTask)})
 *   PASTING → IDLE                    (via {@link #completePaste()} or {@link #cancelPaste()})
 *   LOADED → IDLE                     (via {@link #reset})
 * </pre>
 * </p>
 *
 * <p>Not thread-safe — all access must occur on the main server thread.</p>
 */
public class PasteSession {

    private SessionState state = SessionState.IDLE;
    private @Nullable SchematicHolder loadedSchematic = null;
    private @Nullable String loadedName = null;
    private boolean pendingConfirmation = false;
    private @Nullable AsyncPasteTask activeTask = null;
    private @Nullable BlockPos previewOrigin = null;
    private BlockPos nudgeOffset = BlockPos.ZERO;

    public SessionState getState() {
        return state;
    }

    public @Nullable SchematicHolder getLoadedSchematic() {
        return loadedSchematic;
    }

    public @Nullable String getLoadedName() {
        return loadedName;
    }

    public boolean isPendingConfirmation() {
        return pendingConfirmation;
    }

    /**
     * Load a schematic into this session.
     *
     * <p>Transitions state to {@link SessionState#LOADED} and clears any pending confirmation.</p>
     *
     * @param name   the schematic file name (relative to schematics folder)
     * @param holder the fully-parsed schematic
     */
    public void load(String name, SchematicHolder holder) {
        this.loadedName = name;
        this.loadedSchematic = holder;
        this.state = SessionState.LOADED;
        this.pendingConfirmation = false;
    }

    /**
     * Set whether this session is awaiting confirmation.
     *
     * <p>When {@code true}, state transitions to {@link SessionState#AWAITING_CONFIRMATION}.
     * When {@code false}, state reverts to {@link SessionState#LOADED} (if a schematic is loaded).</p>
     *
     * @param value true to require confirmation before paste
     */
    public void setPendingConfirmation(boolean value) {
        this.pendingConfirmation = value;
        if (value) {
            this.state = SessionState.AWAITING_CONFIRMATION;
        } else if (this.loadedSchematic != null) {
            this.state = SessionState.LOADED;
        }
    }

    /**
     * Reset this session to IDLE, clearing the loaded schematic and name.
     *
     * <p>Called after a paste completes or the player disconnects.</p>
     */
    public void reset() {
        this.state = SessionState.IDLE;
        this.loadedSchematic = null;
        this.loadedName = null;
        this.pendingConfirmation = false;
        this.activeTask = null;
    }

    /**
     * Transition this session to {@link SessionState#PASTING} with the given task.
     *
     * <p>Called by the paste initiator after constructing an {@link AsyncPasteTask}
     * and registering it with the tick event.</p>
     *
     * @param task the active paste task
     */
    public void startPasting(AsyncPasteTask task) {
        this.activeTask = task;
        this.state = SessionState.PASTING;
    }

    /**
     * Transition this session from PASTING back to IDLE after the paste completes normally.
     *
     * <p>Clears the active task, loaded schematic, and all pending state.</p>
     */
    public void completePaste() {
        this.activeTask = null;
        this.state = SessionState.IDLE;
        this.loadedSchematic = null;
        this.loadedName = null;
        this.pendingConfirmation = false;
    }

    /**
     * Transition this session from PASTING back to IDLE after the paste is cancelled.
     *
     * <p>Clears the active task, loaded schematic, and all pending state.</p>
     */
    public void cancelPaste() {
        this.activeTask = null;
        this.state = SessionState.IDLE;
        this.loadedSchematic = null;
        this.loadedName = null;
        this.pendingConfirmation = false;
    }

    /**
     * Returns the currently active async paste task, or {@code null} if not in PASTING state.
     */
    public @Nullable AsyncPasteTask getActiveTask() {
        return activeTask;
    }

    // =========================================================================
    // Preview state
    // =========================================================================

    /**
     * Enter preview mode at the given origin position.
     *
     * <p>Transitions to {@link SessionState#PREVIEWING}. Resets nudge offset.</p>
     */
    public void startPreview(BlockPos origin) {
        this.previewOrigin = origin;
        this.nudgeOffset = BlockPos.ZERO;
        this.state = SessionState.PREVIEWING;
    }

    /**
     * Cancel preview and return to LOADED state.
     */
    public void cancelPreview() {
        this.previewOrigin = null;
        this.nudgeOffset = BlockPos.ZERO;
        this.state = SessionState.LOADED;
    }

    /**
     * Apply a nudge offset delta to the current preview position.
     */
    public void nudge(BlockPos delta) {
        this.nudgeOffset = this.nudgeOffset.offset(delta);
    }

    /**
     * Returns the effective paste position: previewOrigin + nudgeOffset.
     */
    public @Nullable BlockPos getEffectivePastePos() {
        if (previewOrigin == null) return null;
        return previewOrigin.offset(nudgeOffset);
    }

    public @Nullable BlockPos getPreviewOrigin() {
        return previewOrigin;
    }

    public BlockPos getNudgeOffset() {
        return nudgeOffset;
    }
}
