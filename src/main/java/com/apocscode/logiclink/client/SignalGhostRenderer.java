package com.apocscode.logiclink.client;

import com.apocscode.logiclink.LogicLink;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Client-side world renderer that draws highlight boxes at locations
 * toggled on via the Signal Diagnostic Tablet.
 *
 * Reads from SignalHighlightManager — no dependency on TrainMonitorBlockEntity
 * being in a loaded chunk. Just stand near the target coordinates.
 *
 * Colors:
 *  - GREEN:  regular signal suggested placement
 *  - CYAN:   chain signal suggested placement
 *  - RED:    conflict/improper signal (pulsing, with cross pattern)
 */
@EventBusSubscriber(modid = LogicLink.MOD_ID, value = Dist.CLIENT)
public class SignalGhostRenderer {

    private static final double MAX_RENDER_DIST_SQ = 128.0 * 128.0;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Collection<SignalHighlightManager.Marker> markers = SignalHighlightManager.getActiveMarkers();
        if (markers.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || mc.player == null) return;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        long now = System.currentTimeMillis();

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();

        float pulse = (float) (Math.sin(now * 0.004) * 0.15 + 0.35);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        RenderSystem.lineWidth(6.0f);
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());

        // Offsets to render multiple passes for thick outlines
        // (glLineWidth is capped at 1.0 on most modern GPUs)
        double[] thicknessOffsets = { 0.0, 0.005, -0.005, 0.01, -0.01, 0.015, -0.015 };

        // Group markers by block position so we can detect same-block pairs.
        // Per Create rules, two-way signals must be on the SAME track block,
        // so we render a single full-block box with both arrows for clarity.
        Map<Long, List<SignalHighlightManager.Marker>> byBlock = new HashMap<>();
        for (SignalHighlightManager.Marker marker : markers) {
            byBlock.computeIfAbsent(marker.posKey(), k -> new ArrayList<>()).add(marker);
        }

        for (List<SignalHighlightManager.Marker> group : byBlock.values()) {
            SignalHighlightManager.Marker first = group.get(0);
            double distSq = cam.distanceToSqr(first.x() + 0.5, first.y() + 0.5, first.z() + 0.5);
            if (distSq > MAX_RENDER_DIST_SQ) continue;

            // Use highest-priority type in the group for box color
            int groupType = first.type();
            for (SignalHighlightManager.Marker m : group) {
                if (m.type() > groupType) groupType = m.type();
            }

            float r, g, b, a;
            switch (groupType) {
                case SignalHighlightManager.TYPE_CHAIN    -> { r = 0.0f;  g = 0.85f; b = 0.95f; }
                case SignalHighlightManager.TYPE_CONFLICT -> { r = 1.0f;  g = 0.2f;  b = 0.2f;  }
                default                                   -> { r = 0.1f;  g = 0.9f;  b = 0.2f;  }
            }

            a = (groupType == SignalHighlightManager.TYPE_CONFLICT)
                    ? (float) (Math.sin(now * 0.006) * 0.2 + 0.85)
                    : (pulse * 0.6f + 0.4f);

            double pad = 0.05;
            boolean multiMarker = group.size() > 1;

            double bx0, bz0, bx1, bz1;
            if (groupType == SignalHighlightManager.TYPE_CONFLICT || multiMarker || !first.hasDirection()) {
                // Full-block box: conflict markers, multi-marker blocks (opposing signals),
                // or markers without direction
                bx0 = first.x();
                bz0 = first.z();
                bx1 = first.x() + 1.0;
                bz1 = first.z() + 1.0;
            } else {
                // Single marker with direction — right-half box
                float dx = first.dirX();
                float dz = first.dirZ();
                double rx = -dz;
                double rz = dx;
                double centerX = first.x() + 0.5 + rx * 0.25;
                double centerZ = first.z() + 0.5 + rz * 0.25;
                double halfPerp = 0.25;
                double halfPar = 0.45;
                double halfX = Math.abs(dx) * halfPar + Math.abs(rx) * halfPerp;
                double halfZ = Math.abs(dz) * halfPar + Math.abs(rz) * halfPerp;
                bx0 = centerX - halfX;
                bz0 = centerZ - halfZ;
                bx1 = centerX + halfX;
                bz1 = centerZ + halfZ;
            }

            // Render outer box with multiple offset passes for thickness
            for (double off : thicknessOffsets) {
                LevelRenderer.renderLineBox(poseStack, lineConsumer,
                        bx0 - pad + off, first.y() - pad + off, bz0 - pad + off,
                        bx1 + pad - off, first.y() + 1.0 + pad - off, bz1 + pad - off,
                        r, g, b, a);
            }

            // Inner box for visual weight (also thickened)
            double inner = 0.08;
            for (double off : thicknessOffsets) {
                LevelRenderer.renderLineBox(poseStack, lineConsumer,
                        bx0 + inner + off, first.y() + inner + off, bz0 + inner + off,
                        bx1 - inner - off, first.y() + 1.0 - inner - off, bz1 - inner - off,
                        r, g, b, a * 0.7f);
            }

            // Cross pattern for conflicts
            if (groupType == SignalHighlightManager.TYPE_CONFLICT) {
                double crossTop = first.y() + 1.0 + pad;
                double cmx = (bx1 - bx0) * 0.2;
                double cmz = (bz1 - bz0) * 0.2;
                LevelRenderer.renderLineBox(poseStack, lineConsumer,
                        bx0 + cmx, crossTop - 0.01, bz0 + cmz,
                        bx1 - cmx, crossTop, bz1 - cmz,
                        r, g, b, a);
            }

            // Direction arrows — render one per marker in the group.
            // For multi-marker groups (opposing signals), each arrow sits in its
            // own right-half of the full block, so both are visible side by side.
            for (SignalHighlightManager.Marker marker : group) {
                if (marker.hasDirection()) {
                    renderDirectionArrow(poseStack, lineConsumer, marker, multiMarker, r, g, b, a);
                }
            }
        }

        bufferSource.endBatch(RenderType.lines());

        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableCull();
        poseStack.popPose();
    }

    /**
     * Draws a direction arrow inside the highlight box.
     * Arrow sits on the horizontal Y=0.5 plane, pointing along the track direction.
     *
     * For single markers: arrow centered in the right-half box.
     * For multi-marker groups (opposing signals in same block): each arrow sits
     * in its own right-half so both arrows are visible side by side within the
     * single full-block box.
     *
     * Arrow shape:
     *   - Main shaft line from tail to tip
     *   - Two angled barbs at the tip forming the arrowhead
     *   - A perpendicular cross-bar at the tail for clarity
     */
    private static void renderDirectionArrow(PoseStack poseStack, VertexConsumer consumer,
                                              SignalHighlightManager.Marker marker,
                                              boolean multiMarker,
                                              float r, float g, float b, float a) {
        // Arrow always positioned in the right-half of the block for this direction.
        // In a multi-marker group, opposing arrows naturally sit on opposite halves.
        double rx = -marker.dirZ();  // right perpendicular
        double rz = marker.dirX();
        double cx = marker.x() + 0.5 + rx * 0.25;
        double cy = marker.y() + 0.5;
        double cz = marker.z() + 0.5 + rz * 0.25;

        // Direction vector (already normalized)
        float dx = marker.dirX();
        float dz = marker.dirZ();

        // Perpendicular vector — right side in Minecraft: (-dz, dx)
        float px = -dz;
        float pz = dx;

        // Arrow geometry — scaled to fit inside the half-block box
        double shaftLen = 0.32;   // half-length of the shaft
        double headLen = 0.13;    // arrowhead barb length
        double headWidth = 0.10;  // arrowhead barb spread
        double tailWidth = 0.08;  // tail cross-bar half-width

        // Shaft endpoints
        double tailX = cx - dx * shaftLen;
        double tailZ = cz - dz * shaftLen;
        double tipX = cx + dx * shaftLen;
        double tipZ = cz + dz * shaftLen;

        // Draw shaft line
        addLine(poseStack, consumer, tailX, cy, tailZ, tipX, cy, tipZ, r, g, b, a);

        // Arrowhead barbs
        double barbBaseX = tipX - dx * headLen;
        double barbBaseZ = tipZ - dz * headLen;

        double barbLeftX = barbBaseX + px * headWidth;
        double barbLeftZ = barbBaseZ + pz * headWidth;
        double barbRightX = barbBaseX - px * headWidth;
        double barbRightZ = barbBaseZ - pz * headWidth;

        addLine(poseStack, consumer, tipX, cy, tipZ, barbLeftX, cy, barbLeftZ, r, g, b, a);
        addLine(poseStack, consumer, tipX, cy, tipZ, barbRightX, cy, barbRightZ, r, g, b, a);

        // Connect barb tips for a solid arrowhead triangle
        addLine(poseStack, consumer, barbLeftX, cy, barbLeftZ, barbRightX, cy, barbRightZ, r, g, b, a);

        // Tail cross-bar (perpendicular line at the tail end)
        double tailLeftX = tailX + px * tailWidth;
        double tailLeftZ = tailZ + pz * tailWidth;
        double tailRightX = tailX - px * tailWidth;
        double tailRightZ = tailZ - pz * tailWidth;

        addLine(poseStack, consumer, tailLeftX, cy, tailLeftZ, tailRightX, cy, tailRightZ, r, g, b, a);

        // Duplicate arrow slightly above and below for vertical visibility
        double yOff = 0.25;
        addLine(poseStack, consumer, tailX, cy + yOff, tailZ, tipX, cy + yOff, tipZ, r, g, b, a * 0.5f);
        addLine(poseStack, consumer, tipX, cy + yOff, tipZ, barbLeftX, cy + yOff, barbLeftZ, r, g, b, a * 0.5f);
        addLine(poseStack, consumer, tipX, cy + yOff, tipZ, barbRightX, cy + yOff, barbRightZ, r, g, b, a * 0.5f);

        addLine(poseStack, consumer, tailX, cy - yOff, tailZ, tipX, cy - yOff, tipZ, r, g, b, a * 0.5f);
        addLine(poseStack, consumer, tipX, cy - yOff, tipZ, barbLeftX, cy - yOff, barbLeftZ, r, g, b, a * 0.5f);
        addLine(poseStack, consumer, tipX, cy - yOff, tipZ, barbRightX, cy - yOff, barbRightZ, r, g, b, a * 0.5f);
    }

    /**
     * Adds a single line segment to the vertex consumer.
     * Uses the line's direction as the normal (required by the lines RenderType).
     */
    private static void addLine(PoseStack poseStack, VertexConsumer consumer,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 float r, float g, float b, float a) {
        // Compute normal direction for the line
        float nx = (float)(x2 - x1);
        float ny = (float)(y2 - y1);
        float nz = (float)(z2 - z1);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 0.0001f) return;
        nx /= len; ny /= len; nz /= len;

        PoseStack.Pose pose = poseStack.last();
        consumer.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(r, g, b, a)
                .setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, (float) x2, (float) y2, (float) z2)
                .setColor(r, g, b, a)
                .setNormal(pose, nx, ny, nz);
    }
}
