package com.apocscode.logiclink.client;

import java.util.ArrayList;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.controller.RemoteClientHandler;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.animation.LerpedFloat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Custom item renderer for the Logic Remote — exact CTC parity.
 * <p>
 * Replicates CTC's TweakedLinkedControllerItemRenderer behaviour:
 * floating controller with individual button/joystick/trigger depression
 * and glowing powered base when active.
 */
public class LogicRemoteItemRenderer extends CustomRenderedItemModelRenderer {

    // ---- Partial Models (matching CTC's exact model structure) ----

    /** Powered base (body + antennas with glow textures, no buttons). */
    protected static final PartialModel BASE = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "item/logic_remote/powered"));

    /** Generic small button partial. */
    protected static final PartialModel BUTTON = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "item/logic_remote/button"));

    /** Joystick partial (stem + nub, origin at 8,8,8 for rotation). */
    protected static final PartialModel JOYSTICK = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "item/logic_remote/joystick"));

    /** Trigger partial (1×1×2 side element). */
    protected static final PartialModel TRIGGER = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "item/logic_remote/trigger"));

    /** Face buttons: Down (A), Right (B), Left (X), Up (Y) — Xbox layout. */
    protected static final PartialModel BUTTON_DOWN = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "item/logic_remote/button_a"));
    protected static final PartialModel BUTTON_RIGHT = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "item/logic_remote/button_b"));
    protected static final PartialModel BUTTON_LEFT = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "item/logic_remote/button_x"));
    protected static final PartialModel BUTTON_UP = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "item/logic_remote/button_y"));

    // ---- Animation state ----

    static LerpedFloat equipProgress = LerpedFloat.linear().startWithValue(0);
    static ArrayList<LerpedFloat> buttons = new ArrayList<>(15);

    // Block-mode (Contraption Remote) animation state — separate from item-mode
    static ArrayList<LerpedFloat> blockButtons = new ArrayList<>(15);
    private static short blockButtonStates = 0;
    private static int blockAxisStates = 0;

    // Joystick axis tilt animation (4 axes: LX, LY, RX, RY) — item-mode and block-mode
    static LerpedFloat[] joystickAxes = new LerpedFloat[4];
    static LerpedFloat[] blockJoystickAxes = new LerpedFloat[4];

    /**
     * Button/element positions on the controller model.
     * Indices 0-3: D-pad (not used as offsets here, 0-3 face buttons rendered at model origin).
     * Indices 4-5: triggers (LT/RT).
     * Indices 6-8, 11-14: small buttons (Back/Start/Guide/DPadUp/DPadDown/DPadLeft/DPadRight).
     * Indices 5-6: joystick centers.
     * Indices 11-12: trigger positions.
     */
    private static final Vec3[] positionList = {
        new Vec3(3, 0.9, 11.5).multiply(0.0625, 0.0625, 0.0625),  // 0: bumper L
        new Vec3(3, 0.9, 2.5).multiply(0.0625, 0.0625, 0.0625),   // 1: bumper R
        new Vec3(6, 1.0, 8.5).multiply(0.0625, 0.0625, 0.0625),   // 2: back
        new Vec3(6, 1.0, 6.5).multiply(0.0625, 0.0625, 0.0625),   // 3: start
        new Vec3(5, 1.0, 7.5).multiply(0.0625, 0.0625, 0.0625),   // 4: guide
        new Vec3(6, 0.5, 11.5).multiply(0.0625, 0.0625, 0.0625),  // 5: joystick L center
        new Vec3(9, 0.5, 5.5).multiply(0.0625, 0.0625, 0.0625),   // 6: joystick R center
        new Vec3(8, 1.0, 9.5).multiply(0.0625, 0.0625, 0.0625),   // 7: dpad up
        new Vec3(9, 1.0, 8.5).multiply(0.0625, 0.0625, 0.0625),   // 8: dpad right
        new Vec3(10, 1.0, 9.5).multiply(0.0625, 0.0625, 0.0625),  // 9: dpad down
        new Vec3(9, 1.0, 10.5).multiply(0.0625, 0.0625, 0.0625),  // 10: dpad left
        new Vec3(3, -0.1, 11.5).multiply(0.0625, 0.0625, 0.0625), // 11: trigger L pos
        new Vec3(3, -0.1, 2.5).multiply(0.0625, 0.0625, 0.0625),  // 12: trigger R pos
    };

    static {
        for (int i = 0; i < 15; i++) {
            buttons.add(LerpedFloat.linear().startWithValue(0));
            blockButtons.add(LerpedFloat.linear().startWithValue(0));
        }
        for (int i = 0; i < 4; i++) {
            joystickAxes[i] = LerpedFloat.linear().startWithValue(0);
            blockJoystickAxes[i] = LerpedFloat.linear().startWithValue(0);
        }
    }

    // ---- Tick ----

    /**
     * Advance equip animation — called every client tick (early).
     */
    public static void earlyTick() {
        if (Minecraft.getInstance().isPaused()) return;
        boolean active = RemoteClientHandler.MODE != RemoteClientHandler.Mode.IDLE;
        equipProgress.chase(active ? 1.0 : 0.0, 0.2f, LerpedFloat.Chaser.EXP);
        equipProgress.tickChaser();
    }

    /**
     * Advance button depression and joystick axis lerps — called every client tick.
     */
    public static void tick() {
        if (Minecraft.getInstance().isPaused()) return;

        // Item-mode: chase button states from RemoteClientHandler
        if (RemoteClientHandler.MODE != RemoteClientHandler.Mode.IDLE) {
            short states = RemoteClientHandler.buttonStates;
            for (int i = 0; i < buttons.size(); i++) {
                LerpedFloat btn = buttons.get(i);
                boolean pressed = (states & (1 << i)) != 0;
                btn.chase(pressed ? 1.0 : 0.0, 0.4f, LerpedFloat.Chaser.EXP);
                btn.tickChaser();
            }
            // Item-mode: chase joystick axis tilt from RemoteClientHandler
            tickJoystickAxes(joystickAxes, RemoteClientHandler.axisStates);
        }

        // Block-mode: chase button states from synced BE state
        for (int i = 0; i < blockButtons.size(); i++) {
            LerpedFloat btn = blockButtons.get(i);
            boolean pressed = (blockButtonStates & (1 << i)) != 0;
            btn.chase(pressed ? 1.0 : 0.0, 0.4f, LerpedFloat.Chaser.EXP);
            btn.tickChaser();
        }
        // Block-mode: chase joystick axis tilt from synced BE state
        tickJoystickAxes(blockJoystickAxes, blockAxisStates);
    }

    /**
     * Decode packed axis int and chase joystick tilt LerpedFloats.
     * Axes 0-3 are 5-bit encoded: bits 0-3 = magnitude (0-15), bit 4 = sign (negative).
     * Decoded to float range -1.0 to 1.0.
     */
    private static void tickJoystickAxes(LerpedFloat[] axes, int packedAxes) {
        for (int i = 0; i < 4; i++) {
            int raw = (packedAxes >>> (i * 5)) & 0x1F;
            int magnitude = raw & 0x0F;
            boolean negative = (raw & 0x10) != 0;
            float value = magnitude / 15.0f;
            if (negative) value = -value;
            axes[i].chase(value, 0.4f, LerpedFloat.Chaser.EXP);
            axes[i].tickChaser();
        }
    }

    public static void resetButtons() {
        for (LerpedFloat btn : buttons) {
            btn.startWithValue(0);
        }
    }

    /**
     * Set the block-mode button/axis target states (called from ContraptionRemoteRenderer).
     * The block-mode LerpedFloats in tick() will chase these targets.
     */
    public static void setBlockRenderState(short buttonStates, int axisStates) {
        blockButtonStates = buttonStates;
        blockAxisStates = axisStates;
    }

    // ---- Render entry points ----

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model,
                          PartialItemModelRenderer renderer,
                          ItemDisplayContext transformType, PoseStack ms,
                          MultiBufferSource buffer, int light, int overlay) {
        renderNormal(stack, model, renderer, transformType, ms, light);
    }

    protected static void renderNormal(ItemStack stack, CustomRenderedItemModel model,
                                       PartialItemModelRenderer renderer,
                                       ItemDisplayContext transformType,
                                       PoseStack ms, int light) {
        render(stack, model, renderer, transformType, ms, light, RenderType.NORMAL, false, false);
    }

    /**
     * Called from ContraptionRemoteRenderer to render the controller on the block tray.
     */
    public static void renderInLectern(ItemStack stack, CustomRenderedItemModel model,
                                       PartialItemModelRenderer renderer,
                                       ItemDisplayContext transformType,
                                       PoseStack ms, int light,
                                       boolean active, boolean renderDepression) {
        render(stack, model, renderer, transformType, ms, light, RenderType.LECTERN, active, renderDepression);
    }

    // ---- Core render logic (matches CTC exactly) ----

    protected static void render(ItemStack stack, CustomRenderedItemModel model,
                                 PartialItemModelRenderer renderer,
                                 ItemDisplayContext transformType,
                                 PoseStack ms, int light,
                                 RenderType renderType,
                                 boolean active, boolean renderDepression) {

        float pt = AnimationTickHolder.getPartialTicks();
        var msr = TransformStack.of(ms);

        ms.pushPose();

        Minecraft mc = Minecraft.getInstance();

        // ---- First-person floating transform ----
        if (renderType == RenderType.NORMAL && mc.player != null) {
            boolean rightHanded = mc.options.mainHand().get() == HumanoidArm.RIGHT;
            ItemDisplayContext mainHand = rightHanded
                    ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                    : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
            ItemDisplayContext offHand = rightHanded
                    ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                    : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;

            active = false;
            boolean noControllerInMain = !(mc.player.getMainHandItem().getItem()
                    instanceof com.apocscode.logiclink.block.LogicRemoteItem);

            if (transformType == mainHand || (transformType == offHand && noControllerInMain)) {
                float equip = equipProgress.getValue(pt);
                int handModifier = transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? -1 : 1;

                if ((transformType == mainHand && mc.player.getOffhandItem().isEmpty())
                        || (transformType == offHand && mc.player.getMainHandItem().isEmpty())) {
                    // Single-hand: larger displacement
                    msr.translate(0.1f * equip, equip / 3.0f, equip * handModifier * 0.93106616f);
                    msr.rotateZDegrees(equip * -10.0f);
                } else {
                    // Dual-hand: smaller displacement
                    msr.translate(0.0f, equip / 4.0f, equip / 4.0f * handModifier);
                    msr.rotateYDegrees(equip * -30.0f * handModifier);
                    msr.rotateZDegrees(equip * -30.0f);
                }
                active = true;
            }

            // GUI: show powered state if player is holding active controller
            if (transformType == ItemDisplayContext.GUI) {
                if (stack == mc.player.getMainHandItem()) active = true;
                if (stack == mc.player.getOffhandItem() && noControllerInMain) active = true;
            }

            active &= RemoteClientHandler.MODE != RemoteClientHandler.Mode.IDLE;
            renderDepression = true;
        }

        // ---- Idle state: render complete model (with buttons baked in) ----
        if (!active) {
            renderer.render(model.getOriginalModel(), light);
            ms.popPose();
            return;
        }

        // ---- Active state: render powered base + animated buttons ----
        renderer.render(BASE.get(), light);

        float s = 0.0625f;
        float b = s * -0.75f;
        int index = 0;

        // Select the correct button LerpedFloat list:
        // LECTERN mode uses blockButtons (driven by BE sync), NORMAL uses buttons (driven by RemoteClientHandler)
        ArrayList<LerpedFloat> activeButtons = (renderType == RenderType.LECTERN) ? blockButtons : buttons;

        // Bind mode light flicker
        if (renderType == RenderType.NORMAL
                && RemoteClientHandler.MODE == RemoteClientHandler.Mode.BIND) {
            float sin = Mth.sin(AnimationTickHolder.getRenderTime() / 4.0f);
            int i = (int) Mth.lerp((sin + 1.0f) / 2.0f, 5.0f, 15.0f);
            light = i << 20;
        }

        ms.pushPose();

        // Face buttons 0-3 (A/B/X/Y) — rendered at their model-baked positions
        BakedModel button = BUTTON_DOWN.get();
        renderButton(renderer, ms, light, pt, button, b, index++, renderDepression, false, activeButtons);
        button = BUTTON_RIGHT.get();
        renderButton(renderer, ms, light, pt, button, b, index++, renderDepression, false, activeButtons);
        button = BUTTON_LEFT.get();
        renderButton(renderer, ms, light, pt, button, b, index++, renderDepression, false, activeButtons);
        button = BUTTON_UP.get();
        renderButton(renderer, ms, light, pt, button, b, index++, renderDepression, false, activeButtons);

        // Buttons 4-5: triggers (LB/RB bumpers) using trigger model with position offset
        button = TRIGGER.get();
        while (index < 6) {
            ms.pushPose();
            msr.translate(positionList[index - 4]);
            renderButton(renderer, ms, light, pt, button, b, index, renderDepression, true, activeButtons);
            ms.popPose();
            index++;
        }

        // Buttons 6-14: small buttons (skip 9,10 = joystick presses, handled by joystick render)
        button = BUTTON.get();
        while (index < 15) {
            if (index != 9 && index != 10) {
                ms.pushPose();
                msr.translate(positionList[index - 4]);
                renderButton(renderer, ms, light, pt, button, b, index, renderDepression, false, activeButtons);
                ms.popPose();
            }
            index++;
        }

        // Joysticks (left and right) — rendered with axis tilt animation
        button = JOYSTICK.get();
        renderJoystick(renderer, ms, light, pt, button, b, renderDepression, false, activeButtons,
                renderType == RenderType.LECTERN ? blockJoystickAxes : joystickAxes);
        renderJoystick(renderer, ms, light, pt, button, b, renderDepression, true, activeButtons,
                renderType == RenderType.LECTERN ? blockJoystickAxes : joystickAxes);

        ms.popPose();
        ms.popPose();
    }

    // ---- Individual element render helpers ----

    protected static void renderButton(PartialItemModelRenderer renderer, PoseStack ms,
                                       int light, float pt, BakedModel button,
                                       float b, int index,
                                       boolean renderDepression, boolean isSideway,
                                       ArrayList<LerpedFloat> buttonList) {
        ms.pushPose();
        if (renderDepression) {
            float depression = b * buttonList.get(index).getValue(pt);
            if (isSideway) {
                ms.translate(-depression, 0, 0);
            } else {
                ms.translate(0, depression, 0);
            }
        }
        renderer.renderSolid(button, light);
        ms.popPose();
    }

    protected static void renderJoystick(PartialItemModelRenderer renderer, PoseStack ms,
                                         int light, float pt, BakedModel joystick,
                                         float b, boolean renderDepression, boolean isRight,
                                         ArrayList<LerpedFloat> buttonList,
                                         LerpedFloat[] axisLerps) {
        ms.pushPose();
        // Translate the joystick model origin to the correct position on the controller
        Vec3 pos = positionList[isRight ? 6 : 5].subtract(0.46875, 0.46875, 0.46875);
        ms.translate(pos.x, pos.y, pos.z);
        ms.pushPose();

        // Joystick press depression (buttons index 9 = left stick, 10 = right stick)
        if (renderDepression) {
            float depression = b * buttonList.get(isRight ? 10 : 9).getValue(pt);
            ms.translate(0, depression, 0);
        }

        // Joystick tilt based on axis input
        // Left stick: axes 0 (X) and 1 (Y). Right stick: axes 2 (X) and 3 (Y).
        // Model coordinates: X runs top→bottom on controller face, Z runs right→left.
        //   rotateX (around X/up-down axis) → tilts stick left/right
        //   rotateZ (around Z/left-right axis) → tilts stick forward/back
        float maxTilt = 15.0f; // degrees
        int xIdx = isRight ? 2 : 0;
        int yIdx = isRight ? 3 : 1;
        float tiltX = -axisLerps[xIdx].getValue(pt) * maxTilt; // X input → rotateX (left/right tilt, negated for model coords)
        float tiltZ = -axisLerps[yIdx].getValue(pt) * maxTilt; // Y input → rotateZ (forward/back tilt)

        if (tiltX != 0 || tiltZ != 0) {
            var msr = TransformStack.of(ms);
            // Pivot around the joystick base (center of the 1x1 nub at model space 0.5, 0.5, 0.5)
            ms.translate(0.03125, 0.03125, 0.03125); // half a pixel to center
            msr.rotateZDegrees(tiltZ);
            msr.rotateXDegrees(tiltX);
            ms.translate(-0.03125, -0.03125, -0.03125);
        }

        renderer.renderSolid(joystick, light);
        ms.popPose();
        ms.popPose();
    }

    // ---- Render type enum ----

    protected enum RenderType {
        NORMAL,
        LECTERN
    }
}
