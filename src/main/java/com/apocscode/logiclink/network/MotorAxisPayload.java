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
 * Client-Server packet sent when a player uses WASD keybinds while the
 * Logic Remote controller overlay is active.
 * <p>
 * Each axis slot (0-3) maps to one motor/drive target. The axisValue is the
 * movement direction: +1 (forward), -1 (backward), 0 (stop).
 * The configuredSpeed is the RPM set in the Motor Config GUI (1-256).
 * Direction reversal is already baked into axisValue by RemoteClientHandler.
 */
public record MotorAxisPayload(
        BlockPos targetPos,
        String targetType,
        float axisValue,
        int configuredSpeed,
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
                    int speed = buf.readVarInt();
                    boolean seq = buf.readBoolean();
                    int dist = buf.readVarInt();
                    return new MotorAxisPayload(pos, type, value, speed, seq, dist);
                }

                @Override
                public void encode(FriendlyByteBuf buf, MotorAxisPayload payload) {
                    buf.writeBlockPos(payload.targetPos);
                    buf.writeUtf(payload.targetType, 32);
                    buf.writeFloat(payload.axisValue);
                    buf.writeVarInt(payload.configuredSpeed);
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

            int speed = Math.max(1, Math.min(256, payload.configuredSpeed));

            if (be instanceof LogicDriveBlockEntity drive) {
                if (Math.abs(payload.axisValue) > 0.01f) {
                    // Scale RPM (1-256) down to drive modifier range (0-16)
                    float modifier = Math.signum(payload.axisValue)
                            * Math.max(1.0f, speed / 16.0f);
                    drive.setSpeedModifier(modifier);
                    drive.setMotorEnabled(true);
                } else {
                    drive.setSpeedModifier(0);
                    drive.setMotorEnabled(false);
                }
            } else if (be instanceof CreativeLogicMotorBlockEntity motor) {
                if (Math.abs(payload.axisValue) > 0.01f) {
                    int motorSpeed = (int) (Math.signum(payload.axisValue) * speed);
                    motor.setMotorSpeed(motorSpeed);
                    motor.setEnabled(true);
                } else {
                    motor.setMotorSpeed(0);
                    motor.setEnabled(false);
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
