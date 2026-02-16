package com.apocscode.logiclink.network;

import java.util.ArrayList;
import java.util.UUID;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.block.LogicRemoteItem;
import com.apocscode.logiclink.controller.RemoteServerHandler;
import com.apocscode.logiclink.input.ControllerOutput;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client→Server packet carrying 6 axis values packed in an int (+ optional full precision floats).
 * Maps axis states to Create Redstone Link frequencies via the held item's ghost inventory.
 * <p>
 * Axis encoding: 4 joystick axes (5 bits each: 1 sign + 4 magnitude) + 2 triggers (4 bits each).
 * 10 logical axis channels: LX+, LX-, LY+, LY-, RX+, RX-, RY+, RY-, LT, RT
 */
public record RemoteAxisPayload(int encodedAxis, boolean hasFullAxis, float[] fullAxis)
        implements CustomPacketPayload {

    public static final Type<RemoteAxisPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "remote_axis"));

    public static final StreamCodec<FriendlyByteBuf, RemoteAxisPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RemoteAxisPayload decode(FriendlyByteBuf buf) {
                    boolean hasFull = buf.readBoolean();
                    float[] full = null;
                    if (hasFull) {
                        full = new float[6];
                        for (int i = 0; i < 6; i++) full[i] = buf.readFloat();
                    }
                    int encoded = buf.readInt();
                    return new RemoteAxisPayload(encoded, hasFull, full);
                }

                @Override
                public void encode(FriendlyByteBuf buf, RemoteAxisPayload payload) {
                    buf.writeBoolean(payload.hasFullAxis);
                    if (payload.hasFullAxis && payload.fullAxis != null) {
                        for (int i = 0; i < 6; i++) buf.writeFloat(payload.fullAxis[i]);
                    }
                    buf.writeInt(payload.encodedAxis);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RemoteAxisPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (sp.isSpectator()) return;

            ItemStack heldItem = getControllerItem(sp);
            if (heldItem == null) return;

            Level world = sp.getCommandSenderWorld();
            UUID uniqueID = sp.getUUID();
            BlockPos pos = sp.blockPosition();

            ControllerOutput output = new ControllerOutput();
            output.decodeAxis(payload.encodedAxis);

            // Decode 10 logical axis channels from 6 physical axes
            ArrayList<Couple<Frequency>> axisCouples = new ArrayList<>(10);
            ArrayList<Byte> axisValues = new ArrayList<>(10);

            for (byte i = 0; i < 10; ++i) {
                byte dt = 0;
                if (i < 8) {
                    // Joystick axes: split into positive/negative channels
                    boolean hasHighBit = (output.axis[i / 2] & 0x10) != 0;
                    if ((i % 2 == 1) == hasHighBit) {
                        dt = (byte) (output.axis[i / 2] & 0x0f);
                    }
                } else {
                    // Triggers: direct mapping
                    dt = output.axis[i - 4];
                }
                Couple<Frequency> targetFreq = LogicRemoteItem.toFrequency(heldItem, i + 15);
                int target = axisCouples.indexOf(targetFreq);
                if (target >= 0) {
                    byte other = axisValues.get(target);
                    axisValues.set(target, dt > other ? dt : other);
                    continue;
                }
                axisCouples.add(targetFreq);
                axisValues.add(dt);
            }

            RemoteServerHandler.receiveAxis(world, pos, uniqueID, axisCouples, axisValues);
        });
    }

    private static ItemStack getControllerItem(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof LogicRemoteItem) return main;
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof LogicRemoteItem) return off;
        return null;
    }
}
