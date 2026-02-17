package com.apocscode.logiclink.network;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.block.LogicRemoteItem;
import com.apocscode.logiclink.controller.ControlProfile;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client→Server packet to persist a ControlProfile to the player's held Logic Remote item.
 * <p>
 * ControlConfigScreen is a plain Screen (not a container), so item changes done client-side
 * are never synced to the server. This packet sends the serialized ControlProfile NBT
 * to the server which applies it to the held item, ensuring persistence across sessions.
 */
public record SaveControlProfilePayload(
        CompoundTag profileTag
) implements CustomPacketPayload {

    public static final Type<SaveControlProfilePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "save_control_profile"));

    public static final StreamCodec<FriendlyByteBuf, SaveControlProfilePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SaveControlProfilePayload decode(FriendlyByteBuf buf) {
                    return new SaveControlProfilePayload(buf.readNbt());
                }

                @Override
                public void encode(FriendlyByteBuf buf, SaveControlProfilePayload payload) {
                    buf.writeNbt(payload.profileTag);
                }
            };

    /**
     * Server-side handler — applies the ControlProfile NBT to the player's held Logic Remote.
     * Also writes the legacy AxisConfig format for backward compatibility with RemoteClientHandler.
     */
    public static void handle(SaveControlProfilePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;

            // Find held Logic Remote
            ItemStack held = sp.getMainHandItem();
            if (!(held.getItem() instanceof LogicRemoteItem)) {
                held = sp.getOffhandItem();
                if (!(held.getItem() instanceof LogicRemoteItem)) return;
            }

            // Load the profile from the received NBT
            ControlProfile profile = ControlProfile.load(payload.profileTag);

            // Save the ControlProfile to the item
            CompoundTag itemTag = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            itemTag.put("ControlProfile", profile.save());

            // Also write legacy AxisConfig for RemoteClientHandler compatibility
            net.minecraft.nbt.ListTag axisList = new net.minecraft.nbt.ListTag();
            for (int i = 0; i < Math.min(ControlProfile.MAX_MOTOR_BINDINGS, 8); i++) {
                CompoundTag slot = new CompoundTag();
                ControlProfile.MotorBinding mb = profile.getMotorBinding(i);
                if (mb.hasTarget()) {
                    slot.putInt("X", mb.targetPos.getX());
                    slot.putInt("Y", mb.targetPos.getY());
                    slot.putInt("Z", mb.targetPos.getZ());
                    slot.putString("Type", mb.targetType);
                    slot.putBoolean("Reversed", mb.reversed);
                    slot.putInt("Speed", mb.speed);
                    slot.putBoolean("Sequential", mb.sequential);
                    slot.putInt("Distance", mb.distance);
                }
                axisList.add(slot);
            }
            itemTag.put("AxisConfig", axisList);

            held.set(DataComponents.CUSTOM_DATA, CustomData.of(itemTag));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
