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

        for (SignalHighlightManager.Marker marker : markers) {
            double distSq = cam.distanceToSqr(marker.x() + 0.5, marker.y() + 0.5, marker.z() + 0.5);
            if (distSq > MAX_RENDER_DIST_SQ) continue;

            float r, g, b, a;
            switch (marker.type()) {
                case SignalHighlightManager.TYPE_CHAIN    -> { r = 0.0f;  g = 0.85f; b = 0.95f; }
                case SignalHighlightManager.TYPE_CONFLICT -> { r = 1.0f;  g = 0.2f;  b = 0.2f;  }
                default                                   -> { r = 0.1f;  g = 0.9f;  b = 0.2f;  }
            }

            a = (marker.type() == SignalHighlightManager.TYPE_CONFLICT)
                    ? (float) (Math.sin(now * 0.006) * 0.2 + 0.85)
                    : (pulse * 0.6f + 0.4f);

            double pad = 0.05;

            // Offset box to the right rail of the track
            // Create tracks have 2 rails within 1 block; signals go on the right side.
            // Right perpendicular of direction (dx,dz) is (dz, -dx) — 90° clockwise.
            // Offset 0.4 blocks to center on the right rail, box is 0.6 blocks wide.
            double offsetX = 0, offsetZ = 0;
            double boxSize = 1.0;  // default full-block box for no-direction markers
            if (marker.hasDirection()) {
                offsetX = marker.dirZ() * 0.4;   // right rail is ~0.4 blocks from center
                offsetZ = -marker.dirX() * 0.4;
                boxSize = 0.6;  // narrower box wrapping just the rail
            }

            // Box origin: marker position offset to right rail, centered on the rail
            double bx = marker.x() + 0.5 + offsetX - boxSize / 2.0;
            double bz = marker.z() + 0.5 + offsetZ - boxSize / 2.0;

            // Render outer box with multiple offset passes for thickness
            for (double off : thicknessOffsets) {
                double x0 = bx - pad + off, y0 = marker.y() - pad + off, z0 = bz - pad + off;
                double x1 = bx + boxSize + pad - off, y1 = marker.y() + 1.0 + pad - off, z1 = bz + boxSize + pad - off;
                LevelRenderer.renderLineBox(poseStack, lineConsumer,
                        x0, y0, z0, x1, y1, z1, r, g, b, a);
            }

            // Inner box for visual weight (also thickened)
            double inner = 0.1;
            for (double off : thicknessOffsets) {
                LevelRenderer.renderLineBox(poseStack, lineConsumer,
                        bx + inner + off, marker.y() + inner + off, bz + inner + off,
                        bx + boxSize - inner - off, marker.y() + 1.0 - inner - off, bz + boxSize - inner - off,
                        r, g, b, a * 0.7f);
            }

            // Cross pattern for conflicts
            if (marker.type() == SignalHighlightManager.TYPE_CONFLICT) {
                double crossTop = marker.y() + 1.0 + pad;
                double cm = boxSize * 0.2;  // cross margin
                LevelRenderer.renderLineBox(poseStack, lineConsumer,
                        bx + cm, crossTop - 0.01, bz + cm,
                        bx + boxSize - cm, crossTop, bz + boxSize - cm,
                        r, g, b, a);
            }

            // Direction arrow — flat on the Y=0.5 plane inside the box
            if (marker.hasDirection()) {
                renderDirectionArrow(poseStack, lineConsumer, marker, r, g, b, a);
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
     * Uses the marker's dirX/dirZ (normalized track direction from junction → branch).
     *
     * Arrow shape:
     *   - Main shaft line from tail to tip
     *   - Two angled barbs at the tip forming the arrowhead
     *   - A perpendicular cross-bar at the tail for clarity
     */
    private static void renderDirectionArrow(PoseStack poseStack, VertexConsumer consumer,
                                              SignalHighlightManager.Marker marker,
                                              float r, float g, float b, float a) {
        // Right-side offset (same as the box offset — 0.4 blocks to the right rail)
        double offsetX = marker.dirZ() * 0.4;
        double offsetZ = -marker.dirX() * 0.4;

        // Center of the offset rail position
        double cx = marker.x() + 0.5 + offsetX;
        double cy = marker.y() + 0.5;
        double cz = marker.z() + 0.5 + offsetZ;

        // Direction vector (already normalized)
        float dx = marker.dirX();
        float dz = marker.dirZ();

        // Perpendicular vector (rotated 90°)
        float px = -dz;
        float pz = dx;

        // Arrow geometry — scaled to fit inside the box
        double shaftLen = 0.38;   // half-length of the shaft
        double headLen = 0.15;    // arrowhead barb length
        double headWidth = 0.14;  // arrowhead barb spread
        double tailWidth = 0.12;  // tail cross-bar half-width

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
