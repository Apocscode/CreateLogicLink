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

            // Try to find the block entity at the given position.
            // If the block is on a contraption (moved from its original position),
            // the block entity won't be found — just silently ignore.
            // Motor/aux control goes through MotorAxisPayload/AuxRedstonePayload directly.
            BlockEntity be = sp.level().getBlockEntity(payload.blockPos);
            if (be instanceof ContraptionRemoteBlockEntity remote) {
                // Validate: player must be within range when block is in-world
                if (sp.blockPosition().distSqr(payload.blockPos) > 64 * 64) return;
                remote.applyGamepadInput(payload.buttonStates, payload.axisStates);
            }
        });
    }
}
