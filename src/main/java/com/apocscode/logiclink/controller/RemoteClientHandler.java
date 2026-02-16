package com.apocscode.logiclink.controller;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.ModRegistry;
import com.apocscode.logiclink.client.MotorConfigScreen;
import com.apocscode.logiclink.input.ControllerOutput;
import com.apocscode.logiclink.input.GamepadInputs;
import com.apocscode.logiclink.network.MotorAxisPayload;
import com.apocscode.logiclink.network.RemoteAxisPayload;
import com.apocscode.logiclink.network.RemoteBindPayload;
import com.apocscode.logiclink.network.RemoteButtonPayload;
import com.apocscode.logiclink.network.SeatInputPayload;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.ControlsUtil;
import net.createmod.catnip.outliner.Outliner;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side handler for the Logic Remote controller.
 * Manages three modes: IDLE, ACTIVE, BIND.
 * <p>
 * ACTIVE: reads gamepad input, encodes it, sends packets to server.
 *   - Item mode: sends RemoteButton/RemoteAxis/MotorAxis packets via hub.
 *   - Block mode: sends SeatInputPayload to a Contraption Remote block.
 * BIND: outlines a redstone link block, waits for a button/axis press, then binds.
 */
public class RemoteClientHandler {

    public static final LayeredDraw.Layer OVERLAY = RemoteClientHandler::renderOverlay;

    public static Mode MODE = Mode.IDLE;
    public static final int PACKET_RATE = 5;
    public static short buttonStates = 0;
    public static int axisStates = 0;
    private static BlockPos selectedLocation = BlockPos.ZERO;
    private static int buttonPacketCooldown = 0;
    private static int axisPacketCooldown = 0;
    private static int motorAxisPacketCooldown = 0;
    private static boolean useLock = false;

    // WASD key state for motor axis control
    private static boolean keyW = false;
    private static boolean keyS = false;
    private static boolean keyA = false;
    private static boolean keyD = false;
    /** Cached axis config from the held item. */
    private static MotorConfigScreen.AxisSlot[] cachedAxisConfig = null;

    /** Block position when activated from a Contraption Remote block (null = item mode). */
    private static BlockPos activeBlockPos = null;

    // Shared output instance for encoding
    private static final ControllerOutput output = new ControllerOutput();

    public static void toggleBindMode(BlockPos location) {
        Minecraft mc = Minecraft.getInstance();
        if (MODE == Mode.IDLE) {
            MODE = Mode.BIND;
            selectedLocation = location;
            useLock = true;
            if (mc.player != null) {
                AllSoundEvents.CONTROLLER_CLICK.playAt(mc.player.level(), mc.player.blockPosition(), 1f, 1f, true);
                mc.player.displayClientMessage(
                        Component.literal("Bind Mode \u2014 press a button or move an axis")
                                .withStyle(ChatFormatting.GOLD), true);
            }
        } else {
            MODE = Mode.IDLE;
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.literal("Bind Mode cancelled").withStyle(ChatFormatting.GRAY), true);
            }
            onReset();
        }
    }

    /**
     * Toggle controller mode for a held Logic Remote item.
     * Caches axis config from the item for WASD motor control.
     */
    public static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (MODE == Mode.IDLE) {
            MODE = Mode.ACTIVE;
            activeBlockPos = null; // Item mode
            // Cache axis config from held item
            if (mc.player != null) {
                ItemStack held = mc.player.getMainHandItem();
                if (!(held.getItem() instanceof com.apocscode.logiclink.block.LogicRemoteItem))
                    held = mc.player.getOffhandItem();
                cachedAxisConfig = MotorConfigScreen.getAxisConfigFromItem(held);
                AllSoundEvents.CONTROLLER_CLICK.playAt(mc.player.level(), mc.player.blockPosition(), 1f, .75f, true);
                mc.player.displayClientMessage(
                        Component.literal("Controller ").withStyle(ChatFormatting.GOLD)
                                .append(Component.literal("ACTIVE").withStyle(ChatFormatting.GREEN)),
                        true);
            }
        } else {
            MODE = Mode.IDLE;
            cachedAxisConfig = null;
            if (mc.player != null) {
                AllSoundEvents.CONTROLLER_CLICK.playAt(mc.player.level(), mc.player.blockPosition(), 1f, .5f, true);
                mc.player.displayClientMessage(
                        Component.literal("Controller ").withStyle(ChatFormatting.GOLD)
                                .append(Component.literal("IDLE").withStyle(ChatFormatting.GRAY)),
                        true);
            }
            onReset();
        }
    }

    /**
     * Toggle controller mode for a Contraption Remote block.
     * Gamepad input is sent as SeatInputPayload to the block entity.
     */
    public static void toggleForBlock(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (MODE == Mode.IDLE) {
            MODE = Mode.ACTIVE;
            activeBlockPos = pos;
            cachedAxisConfig = null;
            if (mc.player != null) {
                AllSoundEvents.CONTROLLER_CLICK.playAt(mc.player.level(), mc.player.blockPosition(), 1f, .75f, true);
                mc.player.displayClientMessage(
                        Component.literal("Controller ").withStyle(ChatFormatting.GOLD)
                                .append(Component.literal("ACTIVE").withStyle(ChatFormatting.GREEN)),
                        true);
            }
        } else {
            MODE = Mode.IDLE;
            cachedAxisConfig = null;
            if (mc.player != null) {
                AllSoundEvents.CONTROLLER_CLICK.playAt(mc.player.level(), mc.player.blockPosition(), 1f, .5f, true);
                mc.player.displayClientMessage(
                        Component.literal("Controller ").withStyle(ChatFormatting.GOLD)
                                .append(Component.literal("IDLE").withStyle(ChatFormatting.GRAY)),
                        true);
            }
            onReset();
        }
    }

    protected static void onReset() {
        selectedLocation = BlockPos.ZERO;
        buttonPacketCooldown = 0;
        axisPacketCooldown = 0;
        motorAxisPacketCooldown = 0;
        keyW = false;
        keyS = false;
        keyA = false;
        keyD = false;

        if (activeBlockPos != null) {
            // Block mode: send zero input to the block entity
            PacketDistributor.sendToServer(new SeatInputPayload(activeBlockPos, (short) 0, 0));
            activeBlockPos = null;
        } else {
            // Item mode: send zero button/axis packets via hub
            if (buttonStates != 0) {
                buttonStates = 0;
                PacketDistributor.sendToServer(new RemoteButtonPayload(buttonStates));
            }
            axisStates = 0;
            PacketDistributor.sendToServer(new RemoteAxisPayload(axisStates, false, null));
        }

        // Send zero to all configured motor axes so they stop
        if (cachedAxisConfig != null) {
            for (MotorConfigScreen.AxisSlot slot : cachedAxisConfig) {
                if (slot.hasTarget()) {
                    PacketDistributor.sendToServer(new MotorAxisPayload(
                            slot.targetPos, slot.targetType, 0f, slot.sequential, slot.distance));
                }
            }
        }

        buttonStates = 0;
        axisStates = 0;
    }

    /**
     * Called every client tick. Reads gamepad input and sends packets.
     */
    public static void tick() {
        if (MODE == Mode.IDLE) return;

        if (buttonPacketCooldown > 0) buttonPacketCooldown--;
        if (axisPacketCooldown > 0) axisPacketCooldown--;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (player.isSpectator()) {
            MODE = Mode.IDLE;
            onReset();
            return;
        }

        // Validate activation source
        if (activeBlockPos != null) {
            // Block mode: check player is within range
            if (player.blockPosition().distSqr(activeBlockPos) > 32 * 32) {
                MODE = Mode.IDLE;
                onReset();
                return;
            }
        } else {
            // Item mode: need held LogicRemoteItem
            ItemStack heldItem = player.getMainHandItem();
            if (!(heldItem.getItem() instanceof com.apocscode.logiclink.block.LogicRemoteItem)) {
                heldItem = player.getOffhandItem();
                if (!(heldItem.getItem() instanceof com.apocscode.logiclink.block.LogicRemoteItem)) {
                    MODE = Mode.IDLE;
                    onReset();
                    return;
                }
            }
        }

        if (mc.screen != null) {
            MODE = Mode.IDLE;
            onReset();
            return;
        }

        // ESC exits controller mode
        if (org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
            MODE = Mode.IDLE;
            onReset();
            return;
        }

        // Read gamepad and fill output
        GamepadInputs.GetControls();
        output.fillFromGamepad(false);

        if (MODE == Mode.ACTIVE) {
            short pressedKeys = output.encodeButtons();
            int axis = output.encodeAxis();

            if (activeBlockPos != null) {
                // ===== BLOCK MODE: send SeatInputPayload =====
                boolean changed = (pressedKeys != buttonStates) || (axis != axisStates);
                if (changed) {
                    if ((pressedKeys & ~buttonStates) != 0) {
                        AllSoundEvents.CONTROLLER_CLICK.playAt(player.level(), player.blockPosition(), 1f, .75f, true);
                    }
                    if ((buttonStates & ~pressedKeys) != 0) {
                        AllSoundEvents.CONTROLLER_CLICK.playAt(player.level(), player.blockPosition(), 1f, .5f, true);
                    }
                    PacketDistributor.sendToServer(new SeatInputPayload(activeBlockPos, pressedKeys, axis));
                    buttonPacketCooldown = PACKET_RATE;
                }
                if (buttonPacketCooldown == 0 && (pressedKeys != 0 || axis != 0)) {
                    PacketDistributor.sendToServer(new SeatInputPayload(activeBlockPos, pressedKeys, axis));
                    buttonPacketCooldown = PACKET_RATE;
                }
                buttonStates = pressedKeys;
                axisStates = axis;
            } else {
                // ===== ITEM MODE: send RemoteButton + RemoteAxis + MotorAxis =====
                if (pressedKeys != buttonStates) {
                    if ((pressedKeys & ~buttonStates) != 0) {
                        AllSoundEvents.CONTROLLER_CLICK.playAt(player.level(), player.blockPosition(), 1f, .75f, true);
                    }
                    if ((buttonStates & ~pressedKeys) != 0) {
                        AllSoundEvents.CONTROLLER_CLICK.playAt(player.level(), player.blockPosition(), 1f, .5f, true);
                    }
                    PacketDistributor.sendToServer(new RemoteButtonPayload(pressedKeys));
                    buttonPacketCooldown = PACKET_RATE;
                }
                if (buttonPacketCooldown == 0 && pressedKeys != 0) {
                    PacketDistributor.sendToServer(new RemoteButtonPayload(pressedKeys));
                    buttonPacketCooldown = PACKET_RATE;
                }
                buttonStates = pressedKeys;

                if (axis != axisStates) {
                    PacketDistributor.sendToServer(new RemoteAxisPayload(axis, false, null));
                    axisPacketCooldown = PACKET_RATE;
                }
                if (axisPacketCooldown == 0 && axis != 0) {
                    PacketDistributor.sendToServer(new RemoteAxisPayload(axis, false, null));
                    axisPacketCooldown = PACKET_RATE;
                }
                axisStates = axis;

                // ===== WASD Motor Axis Control =====
                if (motorAxisPacketCooldown > 0) motorAxisPacketCooldown--;
                if (cachedAxisConfig != null) {
                    long window = mc.getWindow().getWindow();
                    boolean newW = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
                    boolean newS = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS;
                    boolean newA = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS;
                    boolean newD = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS;

                    boolean wasdChanged = (newW != keyW) || (newS != keyS) || (newA != keyA) || (newD != keyD);
                    keyW = newW; keyS = newS; keyA = newA; keyD = newD;

                    if (wasdChanged || motorAxisPacketCooldown == 0) {
                        float[] values = {
                                keyW ? 1.0f : 0.0f,   // slot 0 = W (forward)
                                keyS ? -1.0f : 0.0f,   // slot 1 = S (reverse)
                                keyA ? 1.0f : 0.0f,    // slot 2 = A (right)
                                keyD ? -1.0f : 0.0f     // slot 3 = D (left)
                        };
                        for (int i = 0; i < MotorConfigScreen.MAX_AXIS_SLOTS; i++) {
                            MotorConfigScreen.AxisSlot slot = cachedAxisConfig[i];
                            if (slot.hasTarget() && (wasdChanged || values[i] != 0)) {
                                PacketDistributor.sendToServer(new MotorAxisPayload(
                                        slot.targetPos, slot.targetType,
                                        values[i], slot.sequential, slot.distance));
                            }
                        }
                        if (wasdChanged) {
                            motorAxisPacketCooldown = PACKET_RATE;
                        }
                    }
                }
            }
        }

        if (MODE == Mode.BIND) {
            // Outline the targeted redstone link block
            VoxelShape shape = mc.level.getBlockState(selectedLocation)
                    .getShape(mc.level, selectedLocation);
            if (!shape.isEmpty())
                Outliner.getInstance().showAABB("controller", shape.bounds()
                                .move(selectedLocation))
                        .colored(0x0104FF)
                        .lineWidth(1 / 16f);

            if (!ControlsUtil.isActuallyPressed(Minecraft.getInstance().options.keyUse)) useLock = false;
            if (useLock) return;

            // Check for button press to bind
            for (int i = 0; i < 15; i++) {
                if (!GamepadInputs.buttons[i]) continue;
                LinkBehaviour linkBehaviour = BlockEntityBehaviour.get(mc.level, selectedLocation, LinkBehaviour.TYPE);
                if (linkBehaviour != null) {
                    PacketDistributor.sendToServer(new RemoteBindPayload(i, selectedLocation));
                    player.displayClientMessage(
                            Component.literal("Bound " + GamepadInputs.GetButtonName(i) + " to link")
                                    .withStyle(ChatFormatting.GOLD), true);
                }
                MODE = Mode.IDLE;
                onReset();
                break;
            }

            // Check for axis input to bind
            for (int i = 0; i < 6; i++) {
                if ((i < 4 && Math.abs(GamepadInputs.axis[i]) > 0.8f) || (i >= 4 && GamepadInputs.axis[i] > 0)) {
                    LinkBehaviour linkBehaviour = BlockEntityBehaviour.get(mc.level, selectedLocation, LinkBehaviour.TYPE);
                    if (linkBehaviour != null) {
                        int a = i >= 4 ? i + 4 : i * 2 + (GamepadInputs.axis[i] < 0 ? 1 : 0);
                        PacketDistributor.sendToServer(new RemoteBindPayload(a + 15, selectedLocation));
                        player.displayClientMessage(
                                Component.literal("Bound " + GamepadInputs.GetAxisName(a) + " to link")
                                        .withStyle(ChatFormatting.GOLD), true);
                    }
                    MODE = Mode.IDLE;
                    onReset();
                    break;
                }
            }
        }
    }

    /**
     * Overlay renderer shown during BIND mode and ACTIVE mode.
     */
    public static void renderOverlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (MODE == Mode.IDLE) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        if (MODE == Mode.BIND) {
            graphics.pose().pushPose();
            Screen tooltipScreen = new Screen(CommonComponents.EMPTY) {};
            tooltipScreen.init(mc, screenW, screenH);

            List<Component> list = new ArrayList<>();
            list.add(Component.literal("Bind Mode").withStyle(ChatFormatting.GOLD));
            list.add(Component.literal("Press a button or move an axis to bind").withStyle(ChatFormatting.GRAY));

            int width = 0;
            int height = list.size() * mc.font.lineHeight;
            for (Component c : list) width = Math.max(width, mc.font.width(c));
            int x = (screenW / 3) - width / 2;
            int y = screenH - height - 24;

            graphics.renderComponentTooltip(mc.font, list, x, y);
            graphics.pose().popPose();
        }

        if (MODE == Mode.ACTIVE) {
            int panelW = 140;
            int panelH = 90;
            int px = (screenW - panelW) / 2;
            int py = screenH - panelH - 12;

            graphics.fill(px - 1, py - 1, px + panelW + 1, py + panelH + 1, 0xCC4488CC);
            graphics.fill(px, py, px + panelW, py + panelH, 0xCC1A1A28);

            String title = activeBlockPos != null ? "Remote Control" : "Logic Remote";
            graphics.drawCenteredString(mc.font,
                    Component.literal(title).withStyle(ChatFormatting.AQUA),
                    px + panelW / 2, py + 3, 0xFFFFFFFF);

            int centerX = px + panelW / 2;
            int keyY = py + 18;
            int keySize = 18;
            int gap = 2;

            renderKey(graphics, mc, centerX - keySize / 2, keyY, keySize, keySize, "W", keyW, 0);
            renderKey(graphics, mc, centerX - keySize - gap - keySize / 2, keyY + keySize + gap, keySize, keySize, "A", keyA, 2);
            renderKey(graphics, mc, centerX - keySize / 2, keyY + keySize + gap, keySize, keySize, "S", keyS, 1);
            renderKey(graphics, mc, centerX + gap + keySize / 2, keyY + keySize + gap, keySize, keySize, "D", keyD, 3);

            if (cachedAxisConfig != null) {
                int labelY = keyY + keySize * 2 + gap + 6;
                for (int i = 0; i < MotorConfigScreen.MAX_AXIS_SLOTS; i++) {
                    MotorConfigScreen.AxisSlot slot = cachedAxisConfig[i];
                    if (slot.hasTarget()) {
                        String icon = "drive".equals(slot.targetType) ? "D" : "M";
                        String mode = slot.sequential ? "seq:" + slot.distance + "m" : "cont";
                        String info = "[" + MotorConfigScreen.AXIS_KEYS[i] + "] " + icon + " " + mode;
                        int labelX = px + 4;
                        graphics.drawString(mc.font, info, labelX, labelY + i * 10, 0xFF88CCFF, false);
                    }
                }
            }

            graphics.drawCenteredString(mc.font,
                    Component.literal("[ESC] Close").withStyle(ChatFormatting.DARK_GRAY),
                    px + panelW / 2, py + panelH - 10, 0xFF666677);
        }
    }

    private static void renderKey(GuiGraphics g, Minecraft mc, int x, int y, int w, int h,
                                   String label, boolean pressed, int slotIndex) {
        int bg = pressed ? 0xFF4488CC : 0xFF333344;
        int border = pressed ? 0xFF66AAEE : 0xFF555566;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, border);
        g.fill(x, y, x + w, y + h, bg);
        g.drawCenteredString(mc.font, label, x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
        if (cachedAxisConfig != null && slotIndex < cachedAxisConfig.length
                && cachedAxisConfig[slotIndex].hasTarget()) {
            int dotColor = pressed ? 0xFF22FF55 : 0xFF888888;
            g.fill(x + w - 4, y + 1, x + w - 1, y + 4, dotColor);
        }
    }

    public enum Mode {
        IDLE,
        ACTIVE,
        BIND
    }
}
