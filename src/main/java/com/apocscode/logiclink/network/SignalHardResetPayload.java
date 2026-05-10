package com.apocscode.logiclink.network;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.peripheral.TrainNetworkDataReader;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Network packet to trigger hard reset of all conflicting signals.
 * Removes signals at positions identified in diagnostic data as conflicts,
 * then triggers full network rebuild via reconciliation.
 */
public record SignalHardResetPayload(CompoundTag diagnosticData) implements CustomPacketPayload {

    public static final Type<SignalHardResetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "signal_hard_reset"));

    public static final StreamCodec<FriendlyByteBuf, SignalHardResetPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SignalHardResetPayload decode(FriendlyByteBuf buf) {
                    CompoundTag tag = buf.readNbt();
                    return new SignalHardResetPayload(tag != null ? tag : new CompoundTag());
                }

                @Override
                public void encode(FriendlyByteBuf buf, SignalHardResetPayload payload) {
                    buf.writeNbt(payload.diagnosticData);
                }
            };

    /**
     * Server-side handler — removes conflicting signals based on diagnostic data.
     */
    public static void handle(SignalHardResetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null && context.player().level() instanceof ServerLevel serverLevel) {
                int removed = hardResetConflictingSignals(serverLevel, payload.diagnosticData);
                context.player().sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(
                                "\u00A7c[Hard Reset]\u00A7r Removed " + removed + " conflicting signals. Network will rebuild automatically."));
            }
        });
    }

    /**
     * Remove all signals at positions marked as conflicts in diagnostic data.
     * Returns the count of signals removed.
     */
    private static int hardResetConflictingSignals(ServerLevel level, CompoundTag diagnosticData) {
        int removed = 0;

        // Extract all conflict positions from diagnostics
        java.util.Set<BlockPos> conflictPositions = new java.util.HashSet<>();
        if (diagnosticData.contains("Diagnostics")) {
            ListTag diagnostics = diagnosticData.getList("Diagnostics", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < diagnostics.size(); i++) {
                CompoundTag diag = diagnostics.getCompound(i);
                String type = diag.getString("type");

                // Collect positions of conflicting signals
                if ("SIGNAL_CONFLICT".equals(type)) {
                    // Add the signal position itself
                    if (diag.contains("x") && diag.contains("y") && diag.contains("z")) {
                        BlockPos pos = new BlockPos(
                                (int) diag.getFloat("x"),
                                (int) diag.getFloat("y"),
                                (int) diag.getFloat("z")
                        );
                        conflictPositions.add(pos);
                    }

                    // Also add suggestions (positions to remove)
                    if (diag.contains("suggestions")) {
                        ListTag sug = diag.getList("suggestions", CompoundTag.TAG_COMPOUND);
                        for (int s = 0; s < sug.size(); s++) {
                            CompoundTag sp = sug.getCompound(s);
                            int sx = sp.getInt("sx");
                            int sy = sp.getInt("sy");
                            int sz = sp.getInt("sz");
                            conflictPositions.add(new BlockPos(sx, sy, sz));
                        }
                    }
                }
            }
        }

        // Remove all conflicting signal blocks
        for (BlockPos pos : conflictPositions) {
            if (level.getBlockEntity(pos) instanceof SignalBlockEntity) {
                level.destroyBlock(pos, false);
                removed++;
            }
        }

        return removed;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
