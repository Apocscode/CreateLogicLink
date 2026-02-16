package com.apocscode.logiclink.network;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.block.CreativeLogicMotorBlockEntity;
import com.apocscode.logiclink.block.LogicDriveBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client→Server packet sent when a player uses WASD keybinds while the
 * Logic Remote controller overlay is active.
 * <p>
 * Each axis slot (0-3) maps to one motor/drive target. The value is the
 * movement direction: +1 (forward / right), -1 (backward / left), 0 (stop).
 * <p>
 * For sequential movement, distance (1-100 meters) is included.
 */
public record MotorAxisPayload(
        BlockPos targetPos,
        String targetType,
        float axisValue,
        boolean sequential,
        int sequenceDistance
) implements CustomPacketPayload {

    public static final Type<MotorAxisPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "motor_axis"));

    public static final StreamCodec<FriendlyByteBuf, MotorAxisPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MotorAxisPayload decode(FriendlyByteBuf buf) {
                    BlockPos pos = buf.readBlockPos();
                    String type = buf.readUtf(32);
                    float value = buf.readFloat();
                    boolean seq = buf.readBoolean();
                    int dist = buf.readVarInt();
                    return new MotorAxisPayload(pos, type, value, seq, dist);
                }

                @Override
                public void encode(FriendlyByteBuf buf, MotorAxisPayload payload) {
                    buf.writeBlockPos(payload.targetPos);
                    buf.writeUtf(payload.targetType, 32);
                    buf.writeFloat(payload.axisValue);
                    buf.writeBoolean(payload.sequential);
                    buf.writeVarInt(payload.sequenceDistance);
                }
            };

    public static void handle(MotorAxisPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            Level level = sp.level();

            BlockEntity be = level.getBlockEntity(payload.targetPos);
            if (be == null || be.isRemoved()) return;

            if (be instanceof LogicDriveBlockEntity drive) {
                if (payload.sequential && payload.axisValue != 0) {
                    // Sequential: set modifier proportional to distance, run briefly
                    float modifier = Math.signum(payload.axisValue)
                            * Math.min(16.0f, payload.sequenceDistance);
                    drive.setSpeedModifier(modifier);
                    drive.setMotorEnabled(true);
                } else {
                    // Continuous: set modifier from axis, enable/disable based on value
                    float modifier = payload.axisValue * 16.0f;
                    drive.setSpeedModifier(modifier);
                    drive.setMotorEnabled(Math.abs(payload.axisValue) > 0.01f);
                }
            } else if (be instanceof CreativeLogicMotorBlockEntity motor) {
                if (payload.sequential && payload.axisValue != 0) {
                    int speed = (int) (Math.signum(payload.axisValue)
                            * Math.min(256, payload.sequenceDistance * 4));
                    motor.setMotorSpeed(speed);
                    motor.setEnabled(true);
                } else {
                    int speed = (int) (payload.axisValue * 256.0f);
                    motor.setMotorSpeed(speed);
                    motor.setEnabled(Math.abs(payload.axisValue) > 0.01f);
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
