package com.apocscode.logiclink.client;

import com.apocscode.logiclink.LogicLink;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side renderer that draws outline boxes on all blocks connected to
 * a logistics network, matching Create's visual style: alternating blue
 * colors (0x708DAD / 0x90ADCD), actual block shapes, thin lines.
 */
@EventBusSubscriber(modid = LogicLink.MOD_ID, value = Dist.CLIENT)
public class NetworkHighlightRenderer {

    private static final List<BlockPos> highlightedPositions = new ArrayList<>();
    private static long lastUpdateTime = 0;
    private static final long TIMEOUT_MS = 500;

    // Create's alternating highlight colors
    private static final float COLOR1_R = 112f / 255f;  // 0x708DAD
    private static final float COLOR1_G = 141f / 255f;
    private static final float COLOR1_B = 173f / 255f;
    private static final float COLOR2_R = 144f / 255f;  // 0x90ADCD
    private static final float COLOR2_G = 173f / 255f;
    private static final float COLOR2_B = 205f / 255f;

    /**
     * Called from the payload handler to set which positions should be highlighted.
     */
    public static void setHighlightedPositions(List<BlockPos> positions) {
        highlightedPositions.clear();
        highlightedPositions.addAll(positions);
        lastUpdateTime = System.currentTimeMillis();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (highlightedPositions.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - lastUpdateTime > TIMEOUT_MS) {
            highlightedPositions.clear();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(2.0f);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        // Alternate between Create's two blue colors every ~8 game ticks
        long tick = (now / 50) % 16;
        float r = tick < 8 ? COLOR1_R : COLOR2_R;
        float g = tick < 8 ? COLOR1_G : COLOR2_G;
        float b = tick < 8 ? COLOR1_B : COLOR2_B;

        for (BlockPos pos : highlightedPositions) {
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getShape(level, pos);

            if (shape.isEmpty()) {
                // Fallback to full cube if shape is empty
                double d = -1.0 / 128.0;
                LevelRenderer.renderLineBox(
                        poseStack, consumer,
                        pos.getX() - d, pos.getY() - d, pos.getZ() - d,
                        pos.getX() + 1 + d, pos.getY() + 1 + d, pos.getZ() + 1 + d,
                        r, g, b, 1.0f
                );
            } else {
                // Render actual block shape AABBs (matches Create's outliner behavior)
                for (AABB aabb : shape.toAabbs()) {
                    double shrink = 1.0 / 128.0;
                    LevelRenderer.renderLineBox(
                            poseStack, consumer,
                            pos.getX() + aabb.minX - shrink,
                            pos.getY() + aabb.minY - shrink,
                            pos.getZ() + aabb.minZ - shrink,
                            pos.getX() + aabb.maxX + shrink,
                            pos.getY() + aabb.maxY + shrink,
                            pos.getZ() + aabb.maxZ + shrink,
                            r, g, b, 1.0f
                    );
                }
            }
        }

        bufferSource.endBatch(RenderType.lines());

        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableCull();

        poseStack.popPose();
    }
}
