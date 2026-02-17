package com.apocscode.logiclink.client;

import com.apocscode.logiclink.ModRegistry;
import com.apocscode.logiclink.block.ContraptionRemoteBlockEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/**
 * Block entity renderer for the Contraption Remote Control block.
 * <p>
 * Renders the 3D Logic Remote controller model on top of the block's
 * angled tray, exactly like CTC's TweakedLecternControllerRenderer renders
 * the Tweaked Linked Controller on its lectern.
 */
public class ContraptionRemoteRenderer extends SafeBlockEntityRenderer<ContraptionRemoteBlockEntity> {

    public ContraptionRemoteRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(ContraptionRemoteBlockEntity be, float partialTicks,
                              PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {

        // Create a Logic Remote stack for the item model lookup
        ItemStack stack = new ItemStack(ModRegistry.LOGIC_REMOTE_ITEM.get());
        ItemDisplayContext transformType = ItemDisplayContext.NONE;

        // Get the baked item model for the Logic Remote
        CustomRenderedItemModel mainModel = (CustomRenderedItemModel) Minecraft.getInstance()
                .getItemRenderer()
                .getModel(stack, be.getLevel(), null, 0);

        PartialItemModelRenderer renderer = PartialItemModelRenderer.of(
                stack, transformType, ms, buffer, overlay);

        // The controller is always "active" visually on the block (shows powered base + buttons)
        boolean active = true;
        boolean renderDepression = true;

        // Update block-mode animation targets from the BE's synced input state
        LogicRemoteItemRenderer.setBlockRenderState(
                be.getRenderButtonStates(), be.getRenderAxisStates());

        // Match the block's facing direction
        Direction facing = be.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var msr = TransformStack.of(ms);

        ms.pushPose();

        // Position the controller on the angled tray.
        // Center at (0.5, y, 0.5) BEFORE rotating so the model stays centered
        // regardless of facing direction (fixes direction-dependent offset).
        msr.translate(0.5, 1.25, 0.5);
        msr.rotateYDegrees(AngleHelper.horizontalAngle(facing) - 90.0f);
        // Offset forward onto the tray and tilt to match tray angle
        msr.translate(0.125, 0.0, 0.0);
        msr.rotateZDegrees(-22.0f);

        LogicRemoteItemRenderer.renderInLectern(
                stack, mainModel, renderer, transformType, ms, light, active, renderDepression);

        ms.popPose();
    }
}
