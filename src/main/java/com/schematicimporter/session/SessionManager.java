package com.schematicimporter.session;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Singleton manager for per-player {@link PasteSession} objects.
 *
 * <p>Holds a {@code Map<UUID, PasteSession>} — one entry per online player who has
 * issued a {@code /schem} command. Entries are created on first access via
 * {@link #getOrCreate(UUID)} and removed on player disconnect via {@link #remove(UUID)}.</p>
 *
 * <p>Access via {@link #INSTANCE}. Not thread-safe — all access must occur on the
 * main server thread.</p>
 */
public class SessionManager {

    /** The singleton instance. */
    public static final SessionManager INSTANCE = new SessionManager();

    private final Map<UUID, PasteSession> sessions = new HashMap<>();

    private SessionManager() {}

    /**
     * Get the existing session for {@code playerId}, or create a new {@link PasteSession}
     * if none exists.
     *
     * @param playerId the player's UUID
     * @return the player's {@link PasteSession}, never null
     */
    public PasteSession getOrCreate(UUID playerId) {
        return sessions.computeIfAbsent(playerId, id -> new PasteSession());
    }

    /**
     * Remove the session for {@code playerId}.
     *
     * <p>Should be called when the player disconnects to release memory.</p>
     *
     * @param playerId the player's UUID
     */
    public void remove(UUID playerId) {
        sessions.remove(playerId);
    }

    /**
     * Return the number of active sessions (for monitoring/debugging).
     */
    public int size() {
        return sessions.size();
    }
}
