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
        RenderSystem.lineWidth(3.0f);
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());

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
            double x0 = marker.x() - pad, y0 = marker.y() - pad, z0 = marker.z() - pad;
            double x1 = marker.x() + 1.0 + pad, y1 = marker.y() + 1.0 + pad, z1 = marker.z() + 1.0 + pad;

            // Outer box
            LevelRenderer.renderLineBox(poseStack, lineConsumer,
                    x0, y0, z0, x1, y1, z1, r, g, b, a);

            // Inner box for visual weight
            double inner = 0.15;
            LevelRenderer.renderLineBox(poseStack, lineConsumer,
                    marker.x() + inner, marker.y() + inner, marker.z() + inner,
                    marker.x() + 1.0 - inner, marker.y() + 1.0 - inner, marker.z() + 1.0 - inner,
                    r, g, b, a * 0.7f);

            // Cross pattern for conflicts
            if (marker.type() == SignalHighlightManager.TYPE_CONFLICT) {
                LevelRenderer.renderLineBox(poseStack, lineConsumer,
                        marker.x() + 0.25, y1 - 0.01, marker.z() + 0.25,
                        marker.x() + 0.75, y1, marker.z() + 0.75,
                        r, g, b, a);
            }
        }

        bufferSource.endBatch(RenderType.lines());

        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableCull();
        poseStack.popPose();
    }
}
