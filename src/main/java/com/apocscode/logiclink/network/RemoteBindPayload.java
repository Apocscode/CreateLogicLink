package com.apocscode.logiclink.network;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.block.LogicRemoteItem;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client→Server packet sent when the player binds a button/axis to a Redstone Link.
 * Copies the link's frequency pair into the controller's ghost inventory at the given slot.
 */
public record RemoteBindPayload(int buttonIndex, BlockPos linkLocation) implements CustomPacketPayload {

    public static final Type<RemoteBindPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "remote_bind"));

    public static final StreamCodec<FriendlyByteBuf, RemoteBindPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RemoteBindPayload decode(FriendlyByteBuf buf) {
                    int button = buf.readVarInt();
                    BlockPos pos = buf.readBlockPos();
                    return new RemoteBindPayload(button, pos);
                }

                @Override
                public void encode(FriendlyByteBuf buf, RemoteBindPayload payload) {
                    buf.writeVarInt(payload.buttonIndex);
                    buf.writeBlockPos(payload.linkLocation);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RemoteBindPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (sp.isSpectator()) return;

            ItemStack heldItem = getControllerItem(sp);
            if (heldItem == null) return;

            LinkBehaviour linkBehaviour = BlockEntityBehaviour.get(sp.level(), payload.linkLocation, LinkBehaviour.TYPE);
            if (linkBehaviour == null) return;

            ItemStackHandler frequencyItems = LogicRemoteItem.getFrequencyItems(heldItem);
            linkBehaviour.getNetworkKey()
                    .forEachWithContext((f, first) ->
                            frequencyItems.setStackInSlot(
                                    payload.buttonIndex * 2 + (first ? 0 : 1),
                                    f.getStack().copy()));

            // Save back to item
            CompoundTag tag = heldItem.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            tag.put("Items", frequencyItems.serializeNBT(sp.registryAccess()));
            heldItem.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        });
    }

    private static ItemStack getControllerItem(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof LogicRemoteItem) return main;
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof LogicRemoteItem) return off;
        return null;
    }
}
