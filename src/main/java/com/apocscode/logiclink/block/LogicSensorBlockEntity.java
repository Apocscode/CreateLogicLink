package com.apocscode.logiclink.block;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.ModRegistry;
import com.apocscode.logiclink.network.HubNetwork;
import com.apocscode.logiclink.network.IHubDevice;
import com.apocscode.logiclink.network.SensorNetwork;
import com.apocscode.logiclink.peripheral.CreateBlockReader;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Block entity for the Logic Sensor block.
 * <p>
 * Stores a logistics network frequency UUID (same system as Logic Link and Stock Links).
 * When linked to a network, this sensor is discoverable by any Logic Link on the
 * same network. It periodically reads data from the adjacent Create machine block
 * and caches it for both wired (direct peripheral) and wireless (Logic Link) access.
 * </p>
 */
public class LogicSensorBlockEntity extends BlockEntity implements IHubDevice, IHaveGoggleInformation {

    /** The frequency ID linking this sensor to a Create logistics network. */
    @Nullable
    private UUID networkFrequency = null;

    /** User-assigned label for hub identification. */
    private String hubLabel = "";

    /** Whether this device has registered with HubNetwork. */
    private boolean hubRegistered = false;

    /** Cached data from the target (adjacent) block. */
    @Nullable
    private Map<String, Object> cachedData = null;

    /** Whether this sensor needs to register with SensorNetwork on next tick. */
    private boolean needsRegistration = false;

    /** Tick counter for periodic data refresh. */
    private int refreshTimer = 0;
    private static final int REFRESH_INTERVAL = 20; // Refresh every 1 second

    public LogicSensorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModRegistry.LOGIC_SENSOR_BE.get(), pos, blockState);
    }

    // ==================== Network Frequency ====================

    @Nullable
    public UUID getNetworkFrequency() {
        return networkFrequency;
    }

    public void setNetworkFrequency(@Nullable UUID frequency) {
        // Unregister from old frequency
        if (this.networkFrequency != null) {
            SensorNetwork.unregister(this.networkFrequency, this);
        }

        this.networkFrequency = frequency;
        this.cachedData = null;
        this.refreshTimer = 0;
        setChanged();

        // Register with new frequency
        if (frequency != null && level != null && !level.isClientSide()) {
            SensorNetwork.register(frequency, this);
        }

        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean isLinked() {
        return networkFrequency != null;
    }

    // ==================== Target Block Data ====================

    /**
     * Gets the position of the target block (the one the sensor is attached to).
     */
    public BlockPos getTargetPos() {
        return LogicSensorBlock.getTargetPos(worldPosition, getBlockState());
    }

    /**
     * Reads data from the target block using CreateBlockReader.
     * Must be called on the server thread.
     */
    @Nullable
    public Map<String, Object> readTargetData() {
        if (level == null || level.isClientSide()) return null;
        try {
            return CreateBlockReader.readBlockData(level, getTargetPos());
        } catch (Exception e) {
            LogicLink.LOGGER.debug("Failed to read sensor target data: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Gets the cached data from the last refresh cycle.
     */
    @Nullable
    public Map<String, Object> getCachedData() {
        return cachedData;
    }

    /**
     * Forces a fresh data read from the target block.
     */
    public void refreshData() {
        cachedData = readTargetData();
    }

    // ==================== Server Tick ====================

    public static void serverTick(Level level, BlockPos pos, BlockState state, LogicSensorBlockEntity be) {
        // Handle deferred registration (after world load)
        if (be.needsRegistration && be.networkFrequency != null) {
            SensorNetwork.register(be.networkFrequency, be);
            be.needsRegistration = false;
        }

        // Register with hub network (proximity-based, no frequency needed)
        if (!be.hubRegistered) {
            HubNetwork.register(be);
            be.hubRegistered = true;
        }

        // Periodically refresh cached data
        be.refreshTimer++;
        if (be.refreshTimer >= REFRESH_INTERVAL) {
            be.refreshTimer = 0;
            be.cachedData = be.readTargetData();
        }
    }

    // ==================== Cleanup ====================

    public void onRemoved() {
        if (networkFrequency != null) {
            SensorNetwork.unregister(networkFrequency, this);
        }
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
    public String getDeviceType() { return "sensor"; }

    @Override
    public BlockPos getDevicePos() { return getBlockPos(); }

    // ==================== Goggle Tooltip ====================

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ")
            .append(Component.literal("Logic Sensor").withStyle(ChatFormatting.WHITE)));

        BlockPos pos = getBlockPos();
        tooltip.add(Component.literal("    ")
            .append(Component.literal("Pos: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.WHITE)));

        if (!hubLabel.isEmpty()) {
            tooltip.add(Component.literal("    ")
                .append(Component.literal("Label: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(hubLabel).withStyle(ChatFormatting.AQUA)));
        }

        BlockPos target = getTargetPos();
        tooltip.add(Component.literal("    ")
            .append(Component.literal("Target: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(target.getX() + ", " + target.getY() + ", " + target.getZ()).withStyle(ChatFormatting.WHITE)));
        if (networkFrequency != null) {
            tooltip.add(Component.literal("    ")
                .append(Component.literal("Network: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Linked").withStyle(ChatFormatting.GREEN)));
        }
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
        if (networkFrequency != null) {
            tag.putUUID("Freq", networkFrequency);
        }
        if (!hubLabel.isEmpty()) {
            tag.putString("HubLabel", hubLabel);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("Freq")) {
            networkFrequency = tag.getUUID("Freq");
            // Defer registration to first tick (level may not be set yet)
            needsRegistration = true;
        } else {
            networkFrequency = null;
        }
        hubLabel = tag.getString("HubLabel");
        hubRegistered = false;
        cachedData = null;
    }
}
