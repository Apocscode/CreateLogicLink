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
 * Client→Server packet carrying 15 button states packed in a short.
 * Maps button states to Create Redstone Link frequencies via the held item's ghost inventory.
 */
public record RemoteButtonPayload(short buttonStates) implements CustomPacketPayload {

    public static final Type<RemoteButtonPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "remote_button"));

    public static final StreamCodec<FriendlyByteBuf, RemoteButtonPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RemoteButtonPayload decode(FriendlyByteBuf buf) {
                    return new RemoteButtonPayload(buf.readShort());
                }

                @Override
                public void encode(FriendlyByteBuf buf, RemoteButtonPayload payload) {
                    buf.writeShort(payload.buttonStates);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RemoteButtonPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (sp.isSpectator()) return;

            ItemStack heldItem = getControllerItem(sp);
            if (heldItem == null) return;

            Level world = sp.getCommandSenderWorld();
            UUID uniqueID = sp.getUUID();
            BlockPos pos = sp.blockPosition();

            ControllerOutput output = new ControllerOutput();
            output.decodeButtons(payload.buttonStates);

            ArrayList<Couple<Frequency>> buttonCouples = new ArrayList<>(15);
            ArrayList<Boolean> buttonValues = new ArrayList<>(15);

            for (int i = 0; i < 15; ++i) {
                boolean buttonValue = (payload.buttonStates & (1 << i)) != 0;
                Couple<Frequency> targetFreq = LogicRemoteItem.toFrequency(heldItem, i);
                int target = buttonCouples.indexOf(targetFreq);
                if (target >= 0) {
                    boolean other = buttonValues.get(target);
                    buttonValues.set(target, other || buttonValue);
                    continue;
                }
                buttonCouples.add(targetFreq);
                buttonValues.add(buttonValue);
            }

            RemoteServerHandler.receivePressed(world, pos, uniqueID, buttonCouples, buttonValues);
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
