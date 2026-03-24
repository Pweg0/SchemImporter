package com.schematicimporter.session;

/**
 * State machine states for a per-player paste session.
 *
 * <ul>
 *   <li>{@link #IDLE} — no schematic loaded, no pending action</li>
 *   <li>{@link #LOADED} — schematic loaded and ready to paste; player may issue /schem paste</li>
 *   <li>{@link #AWAITING_CONFIRMATION} — large structure detected; waiting for /schem confirm</li>
 *   <li>{@link #PREVIEWING} — bounding box preview active; player may nudge, confirm, or cancel</li>
 *   <li>{@link #PASTING} — async paste in progress; blocks are being placed tick-by-tick</li>
 * </ul>
 */
public enum SessionState {
    /** No schematic loaded. Default state. */
    IDLE,

    /** Schematic loaded and ready to paste. */
    LOADED,

    /** Waiting for player confirmation before paste (large structure guard). */
    AWAITING_CONFIRMATION,

    /** Preview bounding box shown; awaiting confirm, nudge, or cancel. */
    PREVIEWING,

    /** Async paste in progress. */
    PASTING
}
