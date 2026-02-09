package com.apocscode.logiclink.network;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.client.NetworkHighlightRenderer;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Network packet sent from server to client containing the positions
 * of all blocks on a logistics network, used to render highlight outlines.
 */
public record NetworkHighlightPayload(List<BlockPos> positions) implements CustomPacketPayload {

    public static final Type<NetworkHighlightPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "highlight"));

    public static final StreamCodec<FriendlyByteBuf, NetworkHighlightPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public NetworkHighlightPayload decode(FriendlyByteBuf buf) {
                    int count = buf.readVarInt();
                    List<BlockPos> positions = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        positions.add(buf.readBlockPos());
                    }
                    return new NetworkHighlightPayload(positions);
                }

                @Override
                public void encode(FriendlyByteBuf buf, NetworkHighlightPayload payload) {
                    buf.writeVarInt(payload.positions.size());
                    for (BlockPos pos : payload.positions) {
                        buf.writeBlockPos(pos);
                    }
                }
            };

    /**
     * Client-side handler — stores the positions in the highlight renderer.
     */
    public static void handle(NetworkHighlightPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            NetworkHighlightRenderer.setHighlightedPositions(payload.positions());
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
