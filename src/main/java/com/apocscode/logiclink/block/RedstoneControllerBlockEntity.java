package com.apocscode.logiclink.block;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.ModRegistry;
import com.apocscode.logiclink.network.HubNetwork;
import com.apocscode.logiclink.network.IHubDevice;
import com.apocscode.logiclink.network.VirtualRedstoneLink;
import com.simibubi.create.Create;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Block entity for the Redstone Controller block.
 * <p>
 * Manages virtual {@link VirtualRedstoneLink} nodes that participate in
 * Create's Redstone Link wireless network. Each channel is a frequency pair
 * (two item names) that can act as either a transmitter (sends signal 0–15)
 * or a receiver (reads signal from external transmitters).
 * </p>
 * <p>
 * Channels are created and managed entirely from Lua — there is no block GUI.
 * One Redstone Controller can manage unlimited simultaneous channels, replacing
 * dozens of physical Redstone Link blocks.
 * </p>
 */
public class RedstoneControllerBlockEntity extends BlockEntity implements IHubDevice, IHaveGoggleInformation {

    /** Live virtual redstone link channels, keyed by "item1|item2". */
    private final Map<String, VirtualRedstoneLink> channels = new HashMap<>();

    /** User-assigned label for hub identification. */
    private String hubLabel = "";

    /** Whether this device has registered with HubNetwork. */
    private boolean hubRegistered = false;

    /** Saved channel data from NBT, consumed on first tick. */
    private List<SavedChannel> pendingChannels = null;

    /** Whether we need to restore channels from NBT on next tick. */
    private boolean needsRegistration = false;

    public RedstoneControllerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModRegistry.REDSTONE_CONTROLLER_BE.get(), pos, blockState);
    }

    // ==================== Channel Key ====================

    /**
     * Creates a canonical key for a frequency pair.
     * Order matters — "redstone|lever" is a different channel from "lever|redstone",
     * matching Create's Couple semantics.
     */
    public static String channelKey(String item1, String item2) {
        return item1 + "|" + item2;
    }

    // ==================== Channel Operations ====================

    /**
     * Sets a transmit channel to the given power level (0–15).
     * Creates the channel if it doesn't exist.
     * If the channel was a receiver, switches it to transmitter mode.
     *
     * @param item1 Registry name of the first frequency item.
     * @param item2 Registry name of the second frequency item.
     * @param power Signal strength to transmit (0–15).
     */
    public void setOutput(String item1, String item2, int power) {
        String key = channelKey(item1, item2);
        VirtualRedstoneLink link = channels.get(key);

        if (link != null) {
            if (link.isListening()) {
                // Was a receiver — remove, switch to transmitter, re-register
                removeFromCreateNetwork(link);
                link.setListening(false);
                link.setTransmitPower(power);
                addToCreateNetwork(link);
            } else {
                // Already a transmitter — update power and notify network
                link.setTransmitPower(power);
                updateCreateNetwork(link);
            }
        } else {
            // Create new transmitter channel
            link = new VirtualRedstoneLink(this, item1, item2, false, power);
            channels.put(key, link);
            addToCreateNetwork(link);
        }
        setChanged();
    }

    /**
     * Gets the received signal strength on a channel.
     * Creates a receiver channel if it doesn't exist.
     * If the channel was a transmitter, switches it to receiver mode.
     *
     * @param item1 Registry name of the first frequency item.
     * @param item2 Registry name of the second frequency item.
     * @return The received signal strength (0–15).
     */
    public int getInput(String item1, String item2) {
        String key = channelKey(item1, item2);
        VirtualRedstoneLink link = channels.get(key);

        if (link != null) {
            if (!link.isListening()) {
                // Was a transmitter — switch to receiver
                removeFromCreateNetwork(link);
                link.setListening(true);
                link.setTransmitPower(0);
                addToCreateNetwork(link);
            }
            return link.getReceivedPower();
        } else {
            // Create new receiver channel
            link = new VirtualRedstoneLink(this, item1, item2, true, 0);
            channels.put(key, link);
            addToCreateNetwork(link);
            // addToNetwork calls updateNetworkOf, so receivedPower is already set
            return link.getReceivedPower();
        }
    }

    /**
     * Gets the current output power of a transmit channel without modifying anything.
     * Returns 0 if the channel doesn't exist or is a receiver.
     *
     * @param item1 Registry name of the first frequency item.
     * @param item2 Registry name of the second frequency item.
     * @return The current transmit power (0–15), or 0.
     */
    public int getOutput(String item1, String item2) {
        String key = channelKey(item1, item2);
        VirtualRedstoneLink link = channels.get(key);
        if (link == null || link.isListening()) return 0;
        return link.getTransmitPower();
    }

    /**
     * Removes a specific channel entirely, unregistering it from Create's network.
     *
     * @param item1 Registry name of the first frequency item.
     * @param item2 Registry name of the second frequency item.
     */
    public void removeChannel(String item1, String item2) {
        String key = channelKey(item1, item2);
        VirtualRedstoneLink link = channels.remove(key);
        if (link != null) {
            removeFromCreateNetwork(link);
            link.kill();
            setChanged();
        }
    }

    /**
     * Returns information about all active channels.
     *
     * @return A list of channel data tables for the Lua API.
     */
    public List<Map<String, Object>> getChannelList() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (VirtualRedstoneLink link : channels.values()) {
            Map<String, Object> info = new HashMap<>();
            info.put("item1", link.getItem1Name());
            info.put("item2", link.getItem2Name());
            info.put("mode", link.isListening() ? "receive" : "transmit");
            info.put("power", link.isListening() ? link.getReceivedPower() : link.getTransmitPower());
            result.add(info);
        }
        return result;
    }

    /**
     * Sets all transmit channels to the given power level.
     * Does not affect receiver channels.
     *
     * @param power Signal strength to set (0–15).
     */
    public void setAllOutputs(int power) {
        for (VirtualRedstoneLink link : channels.values()) {
            if (!link.isListening()) {
                link.setTransmitPower(power);
                updateCreateNetwork(link);
            }
        }
        setChanged();
    }

    /**
     * Removes all channels, unregistering them from Create's network.
     */
    public void clearChannels() {
        for (VirtualRedstoneLink link : channels.values()) {
            removeFromCreateNetwork(link);
            link.kill();
        }
        channels.clear();
        setChanged();
    }

    // ==================== Create Network Integration ====================

    private void addToCreateNetwork(VirtualRedstoneLink link) {
        if (level != null && !level.isClientSide()) {
            try {
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, link);
            } catch (Exception e) {
                LogicLink.LOGGER.error("Failed to add virtual redstone link to network: {}", e.getMessage());
            }
        }
    }

    private void removeFromCreateNetwork(VirtualRedstoneLink link) {
        if (level != null && !level.isClientSide()) {
            try {
                Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, link);
            } catch (Exception e) {
                LogicLink.LOGGER.error("Failed to remove virtual redstone link from network: {}", e.getMessage());
            }
        }
    }

    private void updateCreateNetwork(VirtualRedstoneLink link) {
        if (level != null && !level.isClientSide()) {
            try {
                Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, link);
            } catch (Exception e) {
                LogicLink.LOGGER.error("Failed to update virtual redstone link network: {}", e.getMessage());
            }
        }
    }

    // ==================== Server Tick ====================

    /**
     * Server-side tick. Handles deferred channel restoration after world load.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, RedstoneControllerBlockEntity be) {
        if (be.needsRegistration) {
            be.needsRegistration = false;
            be.restoreChannels();
        }

        // Register with hub network (proximity-based)
        if (!be.hubRegistered) {
            HubNetwork.register(be);
            be.hubRegistered = true;
        }
    }

    /**
     * Restores channels from saved NBT data and registers them with Create's network.
     * Called on the first server tick after world load.
     */
    private void restoreChannels() {
        if (pendingChannels == null) return;

        for (SavedChannel saved : pendingChannels) {
            // Validate items still exist (mod may have been removed)
            ResourceLocation loc1 = ResourceLocation.tryParse(saved.item1);
            ResourceLocation loc2 = ResourceLocation.tryParse(saved.item2);
            if (loc1 == null || loc2 == null) continue;
            if (new ItemStack(BuiltInRegistries.ITEM.get(loc1)).isEmpty()) continue;
            if (new ItemStack(BuiltInRegistries.ITEM.get(loc2)).isEmpty()) continue;

            VirtualRedstoneLink link = new VirtualRedstoneLink(
                    this, saved.item1, saved.item2, saved.listening, saved.power);
            channels.put(channelKey(saved.item1, saved.item2), link);
            addToCreateNetwork(link);
        }

        pendingChannels = null;
        LogicLink.LOGGER.debug("Restored {} redstone controller channels at {}",
                channels.size(), worldPosition);
    }

    // ==================== Cleanup ====================

    /**
     * Called when the block is removed from the world.
     * Unregisters all channels from Create's Redstone Link network.
     */
    public void onRemoved() {
        for (VirtualRedstoneLink link : channels.values()) {
            removeFromCreateNetwork(link);
            link.kill();
        }
        channels.clear();
        HubNetwork.unregister(this);
    }

    // ==================== IHubDevice ====================

    @Override
    public String getHubLabel() { return hubLabel; }

    @Override
    public void setHubLabel(String label) {
        this.hubLabel = label != null ? label : "";
        setChanged();
        // Sync to client for goggle tooltip
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public String getDeviceType() { return "redstone_controller"; }

    @Override
    public BlockPos getDevicePos() { return getBlockPos(); }

    // ==================== Goggle Tooltip ====================

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (!hubLabel.isEmpty()) {
            tooltip.add(Component.literal("    ")
                .append(Component.literal("Label: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(hubLabel).withStyle(ChatFormatting.AQUA)));
        }
        tooltip.add(Component.literal("    ")
            .append(Component.literal("Channels: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(String.valueOf(channels.size())).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" active").withStyle(ChatFormatting.GRAY)));
        return true;
    }

    // ==================== Client Sync ====================

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    // ==================== NBT Persistence ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        if (!hubLabel.isEmpty()) {
            tag.putString("HubLabel", hubLabel);
        }

        ListTag channelList = new ListTag();

        // Save live channels
        for (VirtualRedstoneLink link : channels.values()) {
            CompoundTag channelTag = new CompoundTag();
            channelTag.putString("Item1", link.getItem1Name());
            channelTag.putString("Item2", link.getItem2Name());
            channelTag.putBoolean("Listening", link.isListening());
            channelTag.putInt("Power", link.getTransmitPower());
            channelList.add(channelTag);
        }

        // Save pending channels if live channels haven't been restored yet
        if (channels.isEmpty() && pendingChannels != null) {
            for (SavedChannel saved : pendingChannels) {
                CompoundTag channelTag = new CompoundTag();
                channelTag.putString("Item1", saved.item1);
                channelTag.putString("Item2", saved.item2);
                channelTag.putBoolean("Listening", saved.listening);
                channelTag.putInt("Power", saved.power);
                channelList.add(channelTag);
            }
        }

        if (!channelList.isEmpty()) {
            tag.put("Channels", channelList);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        // Clear live channels (will be re-registered on first tick)
        channels.clear();

        hubLabel = tag.getString("HubLabel");
        hubRegistered = false;

        if (tag.contains("Channels")) {
            ListTag channelList = tag.getList("Channels", Tag.TAG_COMPOUND);
            pendingChannels = new ArrayList<>();
            for (int i = 0; i < channelList.size(); i++) {
                CompoundTag channelTag = channelList.getCompound(i);
                pendingChannels.add(new SavedChannel(
                        channelTag.getString("Item1"),
                        channelTag.getString("Item2"),
                        channelTag.getBoolean("Listening"),
                        channelTag.getInt("Power")
                ));
            }
            needsRegistration = true;
        }
    }

    // ==================== Saved Channel Data ====================

    /**
     * Immutable record for channel data loaded from NBT before
     * VirtualRedstoneLink objects can be created (level not yet set).
     */
    private record SavedChannel(String item1, String item2, boolean listening, int power) {
    }
}
