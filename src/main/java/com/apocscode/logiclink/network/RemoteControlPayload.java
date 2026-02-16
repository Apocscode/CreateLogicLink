package com.apocscode.logiclink.network;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.block.ContraptionRemoteBlockEntity;
import com.apocscode.logiclink.block.CreativeLogicMotorBlockEntity;
import com.apocscode.logiclink.block.LogicDriveBlockEntity;
import com.apocscode.logiclink.block.LogicRemoteItem;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Client→Server packet sent when a player uses the Logic Remote GUI controls.
 * <p>
 * Supports two sources:
 * <ul>
 *   <li>{@code source=0 (ITEM)}: reads targets from the player's held LogicRemoteItem</li>
 *   <li>{@code source=1 (BLOCK)}: reads targets from a ContraptionRemoteBlockEntity at {@code blockPos}</li>
 * </ul>
 * <p>
 * Actions:
 * <ul>
 *   <li>0 = SET_DRIVE_MODIFIER (value = -16.0 to 16.0)</li>
 *   <li>1 = SET_DRIVE_ENABLED (value: 0=off, 1=on)</li>
 *   <li>2 = SET_DRIVE_REVERSED (value: 0=normal, 1=reversed)</li>
 *   <li>3 = SET_MOTOR_SPEED (value = -256 to 256)</li>
 *   <li>4 = SET_MOTOR_ENABLED (value: 0=off, 1=on)</li>
 *   <li>5 = SET_AUX_TOGGLE (value: slot index 0-3, +0.5 if enabling)</li>
 * </ul>
 */
public record RemoteControlPayload(int source, BlockPos blockPos, int action, float value)
        implements CustomPacketPayload {

    // Source types
    public static final int SOURCE_ITEM = 0;
    public static final int SOURCE_BLOCK = 1;

    // Actions
    public static final int SET_DRIVE_MODIFIER = 0;
    public static final int SET_DRIVE_ENABLED = 1;
    public static final int SET_DRIVE_REVERSED = 2;
    public static final int SET_MOTOR_SPEED = 3;
    public static final int SET_MOTOR_ENABLED = 4;
    public static final int SET_AUX_TOGGLE = 5;

    public static final Type<RemoteControlPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "remote_control"));

    public static final StreamCodec<FriendlyByteBuf, RemoteControlPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RemoteControlPayload decode(FriendlyByteBuf buf) {
                    int source = buf.readVarInt();
                    BlockPos pos = buf.readBlockPos();
                    int action = buf.readVarInt();
                    float value = buf.readFloat();
                    return new RemoteControlPayload(source, pos, action, value);
                }

                @Override
                public void encode(FriendlyByteBuf buf, RemoteControlPayload payload) {
                    buf.writeVarInt(payload.source);
                    buf.writeBlockPos(payload.blockPos);
                    buf.writeVarInt(payload.action);
                    buf.writeFloat(payload.value);
                }
            };

    /**
     * Server-side handler — applies the control action to targets.
     */
    public static void handle(RemoteControlPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            Level level = sp.level();

            List<TargetInfo> targets;
            if (payload.source == SOURCE_ITEM) {
                targets = getTargetsFromHeldItem(sp);
            } else if (payload.source == SOURCE_BLOCK) {
                targets = getTargetsFromBlock(level, payload.blockPos);
            } else {
                return;
            }

            if (targets.isEmpty()) return;

            for (TargetInfo target : targets) {
                applyAction(level, target, payload.action, payload.value);
            }
        });
    }

    private static void applyAction(Level level, TargetInfo target, int action, float value) {
        BlockEntity be = level.getBlockEntity(target.pos);
        if (be == null || be.isRemoved()) return;

        switch (action) {
            case SET_DRIVE_MODIFIER -> {
                if (be instanceof LogicDriveBlockEntity drive) {
                    drive.setSpeedModifier(value);
                }
            }
            case SET_DRIVE_ENABLED -> {
                if (be instanceof LogicDriveBlockEntity drive) {
                    drive.setMotorEnabled(value > 0.5f);
                }
            }
            case SET_DRIVE_REVERSED -> {
                if (be instanceof LogicDriveBlockEntity drive) {
                    drive.setReversed(value > 0.5f);
                }
            }
            case SET_MOTOR_SPEED -> {
                if (be instanceof CreativeLogicMotorBlockEntity motor) {
                    motor.setMotorSpeed((int) value);
                }
            }
            case SET_MOTOR_ENABLED -> {
                if (be instanceof CreativeLogicMotorBlockEntity motor) {
                    motor.setEnabled(value > 0.5f);
                }
            }
            case SET_AUX_TOGGLE -> {
                // Auxiliary redstone link toggle.
                // value encodes: slot index (int part) + enabled (0.5 fractional).
                int slot = (int) value;
                boolean enabled = (value - slot) > 0.25f;
                // TODO: Wire to Create redstone link frequency system.
                // For now, this is a placeholder for future redstone link integration.
                LogicLink.LOGGER.debug("Aux slot {} toggled to {}", slot, enabled);
            }
        }
    }

    /** Read targets from the LogicRemoteItem held in the player's hand. */
    private static List<TargetInfo> getTargetsFromHeldItem(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof LogicRemoteItem)) {
            stack = player.getOffhandItem();
            if (!(stack.getItem() instanceof LogicRemoteItem)) {
                return List.of();
            }
        }

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return readTargetsFromTag(tag);
    }

    /** Read targets from a ContraptionRemoteBlockEntity. */
    private static List<TargetInfo> getTargetsFromBlock(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ContraptionRemoteBlockEntity remote)) return List.of();

        List<TargetInfo> result = new ArrayList<>();
        for (ContraptionRemoteBlockEntity.TargetEntry entry : remote.getTargets()) {
            result.add(new TargetInfo(entry.pos(), entry.type()));
        }
        return result;
    }

    private static List<TargetInfo> readTargetsFromTag(CompoundTag tag) {
        List<TargetInfo> result = new ArrayList<>();
        if (!tag.contains("Targets")) return result;
        ListTag list = tag.getList("Targets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            result.add(new TargetInfo(
                new BlockPos(t.getInt("X"), t.getInt("Y"), t.getInt("Z")),
                t.getString("Type")
            ));
        }
        return result;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private record TargetInfo(BlockPos pos, String type) {}
}
