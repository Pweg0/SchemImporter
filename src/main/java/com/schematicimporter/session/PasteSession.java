package com.schematicimporter.session;

import com.schematicimporter.schematic.SchematicHolder;
import org.jetbrains.annotations.Nullable;

/**
 * Per-player paste session — tracks the loaded schematic and paste state.
 *
 * <p>State transitions:
 * <pre>
 *   IDLE → LOADED        (via {@link #load})
 *   LOADED → AWAITING_CONFIRMATION  (via {@link #setPendingConfirmation(boolean)} true)
 *   AWAITING_CONFIRMATION → IDLE   (via {@link #reset} or after paste completes)
 *   LOADED → IDLE        (via {@link #reset})
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
    }
}
