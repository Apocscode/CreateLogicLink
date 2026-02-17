package com.apocscode.logiclink.client;

import java.util.ArrayList;
import java.util.List;

import com.apocscode.logiclink.controller.RemoteClientHandler;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.animation.LerpedFloat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Custom item renderer for the Logic Remote.
 * <p>
 * Provides the CTC-style floating controller animation when active:
 * the controller rises up in first-person view so the player can see
 * button press animations. Mirrors Create's LinkedControllerItemRenderer
 * behaviour but checks LogicLink's {@link RemoteClientHandler#MODE}
 * instead of Create's LinkedControllerClientHandler.
 */
public class LogicRemoteItemRenderer extends CustomRenderedItemModelRenderer {

    // ---- Animation state (static, shared across all instances) ----

    /** Smooth 0→1 lerp controlling the floating-up animation. */
    static LerpedFloat equipProgress = LerpedFloat.linear().startWithValue(0);

    /** Per-button depression lerp (15 gamepad buttons). */
    static List<LerpedFloat> buttons = new ArrayList<>();

    static {
        for (int i = 0; i < 15; i++) {
            buttons.add(LerpedFloat.linear().startWithValue(0));
        }
    }

    // ---- Tick (called once per client tick from RemoteClientTickHandler) ----

    /**
     * Advances all animation lerps. Must be called every client tick.
     */
    public static void tick() {
        boolean active = RemoteClientHandler.MODE != RemoteClientHandler.Mode.IDLE;

        // Chase equip progress towards 1 (active) or 0 (idle)
        equipProgress.chase(active ? 1.0 : 0.0, 0.2f, LerpedFloat.Chaser.EXP);
        equipProgress.tickChaser();

        // Update button depression state
        if (active) {
            short states = RemoteClientHandler.buttonStates;
            for (int i = 0; i < buttons.size(); i++) {
                boolean pressed = (states & (1 << i)) != 0;
                buttons.get(i).chase(pressed ? 1.0 : 0.0, 0.4f, LerpedFloat.Chaser.EXP);
                buttons.get(i).tickChaser();
            }
        } else {
            // Relax all buttons back to 0 when idle
            for (LerpedFloat btn : buttons) {
                btn.chase(0, 0.3f, LerpedFloat.Chaser.EXP);
                btn.tickChaser();
            }
        }
    }

    /**
     * Instantly snaps all button animations back to zero.
     * Called from {@link RemoteClientHandler#onReset()}.
     */
    public static void resetButtons() {
        for (LerpedFloat btn : buttons) {
            btn.startWithValue(0);
        }
    }

    // ---- Render ----

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model,
                          PartialItemModelRenderer renderer,
                          ItemDisplayContext displayContext, PoseStack ms,
                          MultiBufferSource buffer, int light, int overlay) {

        float pt = AnimationTickHolder.getPartialTicks();
        var msr = TransformStack.of(ms);

        RemoteClientHandler.Mode mode = RemoteClientHandler.MODE;
        boolean active = mode != RemoteClientHandler.Mode.IDLE;

        boolean isFirstPerson =
                displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
             || displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;

        // ---- Floating-up animation (first-person only) ----
        if (isFirstPerson) {
            float equip = equipProgress.getValue(pt);
            boolean leftHand = displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
            int handMod = leftHand ? -1 : 1;

            // Translate upward and forward (same maths as Create's renderer)
            msr.translate(0.0f, equip / 4.0f, equip / 4.0f * handMod);
            // Rotate to face the player
            msr.rotateYDegrees(equip * -30.0f * handMod);
            msr.rotateZDegrees(equip * -30.0f);
        }

        // ---- BIND mode light flickering ----
        if (mode == RemoteClientHandler.Mode.BIND) {
            float sin = (float) Math.sin(AnimationTickHolder.getRenderTime() / 4.0);
            int i = (int) Mth.lerp((sin + 1) / 2.0f, 5, 15);
            light = i << 20;
        }

        // ---- Model rendering ----
        if (active) {
            // Render with a slight solid glow when active
            renderer.renderSolidGlowing(model.getOriginalModel(), light);
        } else {
            renderer.render(model.getOriginalModel(), light);
        }
    }
}
