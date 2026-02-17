package com.apocscode.logiclink.network;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.block.ContraptionRemoteBlockEntity;
import com.apocscode.logiclink.controller.ControlProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client→Server packet to persist a ControlProfile to a Contraption Remote block entity.
 * <p>
 * Similar to SaveControlProfilePayload but targets a block at a specific position
 * instead of a held item.
 */
public record SaveBlockProfilePayload(
        BlockPos blockPos,
        CompoundTag profileTag
) implements CustomPacketPayload {

    public static final Type<SaveBlockProfilePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "save_block_profile"));

    public static final StreamCodec<FriendlyByteBuf, SaveBlockProfilePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SaveBlockProfilePayload decode(FriendlyByteBuf buf) {
                    return new SaveBlockProfilePayload(buf.readBlockPos(), buf.readNbt());
                }

                @Override
                public void encode(FriendlyByteBuf buf, SaveBlockProfilePayload payload) {
                    buf.writeBlockPos(payload.blockPos);
                    buf.writeNbt(payload.profileTag);
                }
            };

    /**
     * Server-side handler — applies the ControlProfile to the Contraption Remote block entity.
     */
    public static void handle(SaveBlockProfilePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;

            // Security: check distance
            if (sp.blockPosition().distSqr(payload.blockPos) > 64 * 64) {
                LogicLink.LOGGER.warn("[SaveBlockProfile] Player {} too far from block at {}",
                        sp.getName().getString(), payload.blockPos);
                return;
            }

            BlockEntity be = sp.level().getBlockEntity(payload.blockPos);
            if (!(be instanceof ContraptionRemoteBlockEntity remote)) {
                LogicLink.LOGGER.warn("[SaveBlockProfile] No ContraptionRemoteBlockEntity at {}",
                        payload.blockPos);
                return;
            }

            ControlProfile profile = ControlProfile.load(payload.profileTag);
            remote.setControlProfile(profile);
            LogicLink.LOGGER.info("[SaveBlockProfile] Saved profile to block at {} for {}",
                    payload.blockPos, sp.getName().getString());
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
