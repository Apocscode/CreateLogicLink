package com.apocscode.logiclink.network;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.block.LogicRemoteItem;
import com.apocscode.logiclink.controller.LogicRemoteMenu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client->Server packet requesting the server to open the Logic Remote
 * frequency configuration menu (ghost item slots for button/axis binds).
 * <p>
 * Sent when the player clicks "Freq Config" in the LogicRemoteScreen.
 * The server validates the player is holding a Logic Remote, then opens
 * the LogicRemoteMenu container screen.
 */
public record OpenFreqConfigPayload() implements CustomPacketPayload {

    public static final Type<OpenFreqConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "open_freq_config"));

    public static final StreamCodec<FriendlyByteBuf, OpenFreqConfigPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public OpenFreqConfigPayload decode(FriendlyByteBuf buf) {
                    return new OpenFreqConfigPayload();
                }

                @Override
                public void encode(FriendlyByteBuf buf, OpenFreqConfigPayload payload) {
                    // No data needed
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenFreqConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;

            // Find the Logic Remote in the player's hands
            ItemStack heldItem = serverPlayer.getMainHandItem();
            if (!(heldItem.getItem() instanceof LogicRemoteItem)) {
                heldItem = serverPlayer.getOffhandItem();
                if (!(heldItem.getItem() instanceof LogicRemoteItem)) return;
            }

            // Open the menu — this triggers LogicRemoteConfigScreen on the client
            final ItemStack controller = heldItem;
            serverPlayer.openMenu(
                    (LogicRemoteItem) controller.getItem(),
                    buf -> ItemStack.STREAM_CODEC.encode(buf, controller)
            );
        });
    }
}
