package com.schematicimporter.preview;

import com.schematicimporter.schematic.SchematicHolder;
import com.schematicimporter.session.PasteSession;
import com.schematicimporter.session.SessionManager;
import com.schematicimporter.session.SessionState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

/**
 * Renders a rich bounding box preview using server-side particles.
 *
 * <p>Visual features:
 * <ul>
 *   <li>Red/orange edges on the floor (footprint) for ground area</li>
 *   <li>Green vertical pillar edges at the 4 corners</li>
 *   <li>Blue/cyan edges on the ceiling for top plane</li>
 *   <li>Larger bright particles at the 8 corner vertices</li>
 *   <li>Cyan "front face" indicator on the north face (Z=min) to show rotation</li>
 *   <li>Sparse floor fill particles showing the covered area</li>
 * </ul>
 *
 * <p>Sent every 3 ticks for smoother appearance. Only targets the previewing player.</p>
 */
@EventBusSubscriber(modid = "schematicimporter")
public class PreviewRenderer {

    // ---- Particle colors ----
    /** Floor edges — warm red/orange to stand out on grass/stone */
    private static final DustParticleOptions FLOOR = new DustParticleOptions(
        new Vector3f(1.0f, 0.4f, 0.1f), 1.2f);

    /** Vertical pillar edges — bright green */
    private static final DustParticleOptions PILLAR = new DustParticleOptions(
        new Vector3f(0.2f, 1.0f, 0.3f), 1.0f);

    /** Ceiling edges — cool blue/cyan */
    private static final DustParticleOptions CEILING = new DustParticleOptions(
        new Vector3f(0.2f, 0.6f, 1.0f), 1.0f);

    /** Corner markers — bright white/yellow, larger */
    private static final DustParticleOptions CORNER = new DustParticleOptions(
        new Vector3f(1.0f, 1.0f, 0.4f), 2.0f);

    /** Front face indicator — cyan to show "north" face (Z=min) */
    private static final DustParticleOptions FRONT = new DustParticleOptions(
        new Vector3f(0.0f, 1.0f, 1.0f), 1.5f);

    /** Floor fill — dim green dots for area coverage */
    private static final DustParticleOptions FLOOR_FILL = new DustParticleOptions(
        new Vector3f(0.3f, 0.7f, 0.3f), 0.6f);

    private static final int PARTICLE_INTERVAL_TICKS = 3;

    private static int tickCounter = 0;

    private PreviewRenderer() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter % PARTICLE_INTERVAL_TICKS != 0) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            PasteSession session = SessionManager.INSTANCE.getOrCreate(player.getUUID());
            if (session.getState() != SessionState.PREVIEWING) continue;

            SchematicHolder holder = session.getLoadedSchematic();
            BlockPos pastePos = session.getEffectivePastePos();
            if (holder == null || pastePos == null) continue;

            ServerLevel level = (ServerLevel) player.level();
            renderPreview(player, level, pastePos,
                holder.width(), holder.height(), holder.length());
        }
    }

    private static void renderPreview(ServerPlayer player, ServerLevel level,
                                        BlockPos origin, int w, int h, int l) {
        double x0 = origin.getX();
        double y0 = origin.getY();
        double z0 = origin.getZ();
        double x1 = x0 + w;
        double y1 = y0 + h;
        double z1 = z0 + l;

        // Adaptive spacing: denser for small structures, sparser for large
        double spacing = computeSpacing(w, h, l);

        // ---- Floor edges (red/orange) ----
        sendLine(level, player, FLOOR, x0, y0, z0, x1, y0, z0, spacing);
        sendLine(level, player, FLOOR, x0, y0, z1, x1, y0, z1, spacing);
        sendLine(level, player, FLOOR, x0, y0, z0, x0, y0, z1, spacing);
        sendLine(level, player, FLOOR, x1, y0, z0, x1, y0, z1, spacing);

        // ---- Ceiling edges (blue/cyan) ----
        sendLine(level, player, CEILING, x0, y1, z0, x1, y1, z0, spacing);
        sendLine(level, player, CEILING, x0, y1, z1, x1, y1, z1, spacing);
        sendLine(level, player, CEILING, x0, y1, z0, x0, y1, z1, spacing);
        sendLine(level, player, CEILING, x1, y1, z0, x1, y1, z1, spacing);

        // ---- Vertical pillars at 4 corners (green) ----
        sendLine(level, player, PILLAR, x0, y0, z0, x0, y1, z0, spacing);
        sendLine(level, player, PILLAR, x1, y0, z0, x1, y1, z0, spacing);
        sendLine(level, player, PILLAR, x0, y0, z1, x0, y1, z1, spacing);
        sendLine(level, player, PILLAR, x1, y0, z1, x1, y1, z1, spacing);

        // ---- 8 corner markers (bright yellow, large) ----
        sendCorner(level, player, x0, y0, z0);
        sendCorner(level, player, x1, y0, z0);
        sendCorner(level, player, x0, y0, z1);
        sendCorner(level, player, x1, y0, z1);
        sendCorner(level, player, x0, y1, z0);
        sendCorner(level, player, x1, y1, z0);
        sendCorner(level, player, x0, y1, z1);
        sendCorner(level, player, x1, y1, z1);

        // ---- Front face indicator (cyan, Z=min face) ----
        // Two diagonal lines on the front face to mark rotation
        sendLine(level, player, FRONT, x0, y0, z0, x1, y1, z0, spacing);
        sendLine(level, player, FRONT, x1, y0, z0, x0, y1, z0, spacing);

        // ---- Floor fill (sparse green dots showing covered area) ----
        double fillSpacing = Math.max(2.0, spacing * 3);
        sendFloorFill(level, player, x0, y0, z0, x1, z1, fillSpacing);
    }

    /**
     * Compute adaptive spacing based on structure size.
     * Smaller structures get denser particles (0.5), larger ones sparser (up to 2.0).
     */
    private static double computeSpacing(int w, int h, int l) {
        int maxDim = Math.max(w, Math.max(h, l));
        if (maxDim <= 10) return 0.5;
        if (maxDim <= 30) return 1.0;
        if (maxDim <= 60) return 1.5;
        return 2.0;
    }

    /**
     * Send a bright corner marker (multiple particles for visibility).
     */
    private static void sendCorner(ServerLevel level, ServerPlayer player,
                                     double x, double y, double z) {
        // Send 3 particles slightly offset for a "glow" effect
        level.sendParticles(player, CORNER, true, x, y, z, 1, 0, 0, 0, 0);
        level.sendParticles(player, CORNER, true, x, y + 0.15, z, 1, 0, 0, 0, 0);
    }

    /**
     * Send sparse particles across the floor plane to show the covered area.
     */
    private static void sendFloorFill(ServerLevel level, ServerPlayer player,
                                        double x0, double y, double z0,
                                        double x1, double z1, double spacing) {
        // Limit total fill particles to avoid lag on huge structures
        int maxParticles = 200;
        int count = 0;
        for (double x = x0 + spacing; x < x1 && count < maxParticles; x += spacing) {
            for (double z = z0 + spacing; z < z1 && count < maxParticles; z += spacing) {
                level.sendParticles(player, FLOOR_FILL, true, x, y + 0.05, z, 1, 0, 0, 0, 0);
                count++;
            }
        }
    }

    /**
     * Send particles along a line with the given color and spacing.
     */
    private static void sendLine(ServerLevel level, ServerPlayer player,
                                  DustParticleOptions particle,
                                  double x0, double y0, double z0,
                                  double x1, double y1, double z1,
                                  double spacing) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.01) {
            level.sendParticles(player, particle, true, x0, y0, z0, 1, 0, 0, 0, 0);
            return;
        }

        int steps = Math.max(1, (int) Math.ceil(length / spacing));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double px = x0 + dx * t;
            double py = y0 + dy * t;
            double pz = z0 + dz * t;
            level.sendParticles(player, particle, true, px, py, pz, 1, 0, 0, 0, 0);
        }
    }
}
