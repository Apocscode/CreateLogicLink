package com.apocscode.logiclink.network;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.block.ContraptionRemoteBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-Server packet carrying gamepad input for a Contraption Remote block.
 * <p>
 * Contains the block position, 15 button states (packed short), and
 * 6 axis values (packed int). The server validates the player is within
 * range of the block before applying input.
 */
public record SeatInputPayload(BlockPos blockPos, short buttonStates, int axisStates)
        implements CustomPacketPayload {

    public static final Type<SeatInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "seat_input"));

    public static final StreamCodec<FriendlyByteBuf, SeatInputPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SeatInputPayload decode(FriendlyByteBuf buf) {
                    BlockPos pos = buf.readBlockPos();
                    short buttons = buf.readShort();
                    int axes = buf.readInt();
                    return new SeatInputPayload(pos, buttons, axes);
                }

                @Override
                public void encode(FriendlyByteBuf buf, SeatInputPayload payload) {
                    buf.writeBlockPos(payload.blockPos);
                    buf.writeShort(payload.buttonStates);
                    buf.writeInt(payload.axisStates);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SeatInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (sp.isSpectator()) return;

            // Validate: player must be within range of the block (32 blocks)
            if (sp.blockPosition().distSqr(payload.blockPos) > 32 * 32) return;

            // Get the block entity and apply the input
            BlockEntity be = sp.level().getBlockEntity(payload.blockPos);
            if (be instanceof ContraptionRemoteBlockEntity remote) {
                remote.applyGamepadInput(payload.buttonStates, payload.axisStates);
            }
        });
    }
}
