package com.apocscode.logiclink.network;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.block.LogicRemoteItem;
import com.apocscode.logiclink.controller.ControlProfile;
import com.apocscode.logiclink.controller.ControlProfile.AuxBinding;
import com.apocscode.logiclink.controller.RemoteServerHandler;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;

/**
 * Client→Server packet for auxiliary redstone channel control.
 * Sends the state of 8 aux channels (on/off) from the controller.
 * <p>
 * Each aux channel maps to a Create Redstone Link frequency pair
 * configured in the ControlProfile. The signal strength is the
 * configured power level (1-15) when active, or 0 when inactive.
 */
public record AuxRedstonePayload(
        byte auxStates  // 8 bits, one per aux channel (bit 0 = channel 0, etc.)
) implements CustomPacketPayload {

    public static final Type<AuxRedstonePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "aux_redstone"));

    public static final StreamCodec<FriendlyByteBuf, AuxRedstonePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public AuxRedstonePayload decode(FriendlyByteBuf buf) {
                    return new AuxRedstonePayload(buf.readByte());
                }

                @Override
                public void encode(FriendlyByteBuf buf, AuxRedstonePayload payload) {
                    buf.writeByte(payload.auxStates);
                }
            };

    /**
     * Server-side handler — reads ControlProfile from the player's held item
     * and activates/deactivates redstone link signals for each aux channel.
     */
    public static void handle(AuxRedstonePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;

            LogicLink.LOGGER.info("[AuxRedstone] Received aux packet: states=0b{}", Integer.toBinaryString(payload.auxStates & 0xFF));

            // Get ControlProfile from held item
            ItemStack held = sp.getMainHandItem();
            if (!(held.getItem() instanceof LogicRemoteItem)) {
                held = sp.getOffhandItem();
                if (!(held.getItem() instanceof LogicRemoteItem)) {
                    LogicLink.LOGGER.warn("[AuxRedstone] Player not holding LogicRemote!");
                    return;
                }
            }

            ControlProfile profile = ControlProfile.load(
                    held.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getCompound("ControlProfile"));
            BlockPos playerPos = sp.blockPosition();

            // Build frequency lists and values for RemoteServerHandler
            ArrayList<Couple<Frequency>> freqList = new ArrayList<>();
            ArrayList<Boolean> valueList = new ArrayList<>();

            for (int i = 0; i < ControlProfile.MAX_AUX_BINDINGS; i++) {
                AuxBinding aux = profile.getAuxBinding(i);
                if (!aux.hasFrequency()) continue;

                boolean active = (payload.auxStates & (1 << i)) != 0;
                LogicLink.LOGGER.info("[AuxRedstone] Channel {} freq={}/{} active={} power={}",
                        i, aux.freqId1, aux.freqId2, active, aux.power);

                // Convert frequency IDs to Create Frequency objects
                try {
                    ItemStack freq1Stack = new ItemStack(
                            BuiltInRegistries.ITEM.get(ResourceLocation.parse(aux.freqId1)));
                    ItemStack freq2Stack = new ItemStack(
                            BuiltInRegistries.ITEM.get(ResourceLocation.parse(aux.freqId2)));

                    Couple<Frequency> freqPair = Couple.create(
                            Frequency.of(freq1Stack),
                            Frequency.of(freq2Stack));

                    freqList.add(freqPair);
                    valueList.add(active);
                } catch (Exception e) {
                    LogicLink.LOGGER.warn("Invalid aux frequency for channel {}: {}/{}", i, aux.freqId1, aux.freqId2);
                }
            }

            if (!freqList.isEmpty()) {
                LogicLink.LOGGER.info("[AuxRedstone] Sending {} freq pairs to RemoteServerHandler", freqList.size());
                RemoteServerHandler.receivePressed(
                        sp.level(), playerPos, sp.getUUID(), freqList, valueList);
            } else {
                LogicLink.LOGGER.warn("[AuxRedstone] No valid frequency pairs found in profile!");
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
