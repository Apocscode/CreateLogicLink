package com.apocscode.logiclink.controller;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.ModRegistry;
import com.apocscode.logiclink.input.ControllerOutput;
import com.apocscode.logiclink.input.GamepadInputs;
import com.apocscode.logiclink.network.RemoteAxisPayload;
import com.apocscode.logiclink.network.RemoteBindPayload;
import com.apocscode.logiclink.network.RemoteButtonPayload;
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
 * ACTIVE: reads gamepad input, encodes it, sends button/axis packets to server.
 * BIND: outlines a redstone link block, waits for a button/axis press, then binds.
 * <p>
 * Port of CTC's TweakedLinkedControllerClientHandler for standalone LogicLink use.
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
    private static boolean useLock = false;

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
                        Component.literal("Bind Mode — press a button or move an axis")
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

    public static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (MODE == Mode.IDLE) {
            MODE = Mode.ACTIVE;
            if (mc.player != null) {
                AllSoundEvents.CONTROLLER_CLICK.playAt(mc.player.level(), mc.player.blockPosition(), 1f, .75f, true);
                mc.player.displayClientMessage(
                        Component.literal("Controller ").withStyle(ChatFormatting.GOLD)
                                .append(Component.literal("ACTIVE").withStyle(ChatFormatting.GREEN)),
                        true);
            }
        } else {
            MODE = Mode.IDLE;
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

        if (buttonStates != 0) {
            buttonStates = 0;
            PacketDistributor.sendToServer(new RemoteButtonPayload(buttonStates));
        }
        axisStates = 0;
        PacketDistributor.sendToServer(new RemoteAxisPayload(axisStates, false, null));
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

        ItemStack heldItem = player.getMainHandItem();
        if (!(heldItem.getItem() instanceof com.apocscode.logiclink.block.LogicRemoteItem)) {
            heldItem = player.getOffhandItem();
            if (!(heldItem.getItem() instanceof com.apocscode.logiclink.block.LogicRemoteItem)) {
                MODE = Mode.IDLE;
                onReset();
                return;
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
            if (pressedKeys != buttonStates) {
                // Play click sounds
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

            int axis = output.encodeAxis();
            if (axis != axisStates) {
                PacketDistributor.sendToServer(new RemoteAxisPayload(axis, false, null));
                axisPacketCooldown = PACKET_RATE;
            }
            if (axisPacketCooldown == 0 && axis != 0) {
                PacketDistributor.sendToServer(new RemoteAxisPayload(axis, false, null));
                axisPacketCooldown = PACKET_RATE;
            }
            axisStates = axis;
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
     * Overlay renderer shown during BIND mode.
     */
    public static void renderOverlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (MODE != Mode.BIND) return;

        int width1 = mc.getWindow().getGuiScaledWidth();
        int height1 = mc.getWindow().getGuiScaledHeight();

        graphics.pose().pushPose();
        Screen tooltipScreen = new Screen(CommonComponents.EMPTY) {};
        tooltipScreen.init(mc, width1, height1);

        List<Component> list = new ArrayList<>();
        list.add(Component.literal("Bind Mode").withStyle(ChatFormatting.GOLD));
        list.add(Component.literal("Press a button or move an axis to bind").withStyle(ChatFormatting.GRAY));

        int width = 0;
        int height = list.size() * mc.font.lineHeight;
        for (Component c : list) width = Math.max(width, mc.font.width(c));
        int x = (width1 / 3) - width / 2;
        int y = height1 - height - 24;

        graphics.renderComponentTooltip(mc.font, list, x, y);
        graphics.pose().popPose();
    }

    public enum Mode {
        IDLE,
        ACTIVE,
        BIND
    }
}
