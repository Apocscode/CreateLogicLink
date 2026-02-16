package com.apocscode.logiclink.controller;

import com.apocscode.logiclink.entity.RemoteSeatEntity;
import com.apocscode.logiclink.input.ControllerOutput;
import com.apocscode.logiclink.input.GamepadInputs;
import com.apocscode.logiclink.network.SeatInputPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side handler that reads gamepad input while the player is
 * seated on a Contraption Remote block (riding a RemoteSeatEntity).
 * <p>
 * Called every client tick by RemoteClientTickHandler.
 * When the player is seated, reads the gamepad, encodes button/axis state,
 * and sends SeatInputPayload packets to the server.
 */
public class SeatInputHandler {

    private static final int PACKET_RATE = 5;

    private static short lastButtons = 0;
    private static int lastAxes = 0;
    private static int buttonCooldown = 0;
    private static int axisCooldown = 0;

    private static final ControllerOutput output = new ControllerOutput();

    /**
     * Called every client tick. Checks if the player is seated on a
     * RemoteSeatEntity and routes gamepad input if so.
     */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // Check if player is riding a RemoteSeatEntity
        if (!(player.getVehicle() instanceof RemoteSeatEntity seat)) {
            // Not seated — reset state if we were active
            if (lastButtons != 0 || lastAxes != 0) {
                reset(BlockPos.ZERO);
            }
            return;
        }

        BlockPos blockPos = seat.blockPosition();

        if (buttonCooldown > 0) buttonCooldown--;
        if (axisCooldown > 0) axisCooldown--;

        // Read gamepad
        GamepadInputs.GetControls();
        output.fillFromGamepad(false);

        short buttons = output.encodeButtons();
        int axes = output.encodeAxis();

        // Send button updates on change or on cooldown refresh
        if (buttons != lastButtons) {
            PacketDistributor.sendToServer(new SeatInputPayload(blockPos, buttons, axes));
            buttonCooldown = PACKET_RATE;
            axisCooldown = PACKET_RATE;
        } else if (buttonCooldown == 0 && buttons != 0) {
            PacketDistributor.sendToServer(new SeatInputPayload(blockPos, buttons, axes));
            buttonCooldown = PACKET_RATE;
        }

        // Send axis updates on change or on cooldown refresh
        if (axes != lastAxes && buttonCooldown > 0) {
            // Already sent above with button packet
        } else if (axes != lastAxes) {
            PacketDistributor.sendToServer(new SeatInputPayload(blockPos, buttons, axes));
            axisCooldown = PACKET_RATE;
        } else if (axisCooldown == 0 && axes != 0) {
            PacketDistributor.sendToServer(new SeatInputPayload(blockPos, buttons, axes));
            axisCooldown = PACKET_RATE;
        }

        lastButtons = buttons;
        lastAxes = axes;
    }

    private static void reset(BlockPos blockPos) {
        if (lastButtons != 0 || lastAxes != 0) {
            // Send zero state to clear server-side
            PacketDistributor.sendToServer(new SeatInputPayload(blockPos, (short) 0, 0));
        }
        lastButtons = 0;
        lastAxes = 0;
        buttonCooldown = 0;
        axisCooldown = 0;
    }
}
