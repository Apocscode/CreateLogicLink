package com.apocscode.logiclink.block;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.ModRegistry;
import com.apocscode.logiclink.network.HubNetwork;
import com.apocscode.logiclink.network.LinkNetwork;

import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Block entity for the Logic Link block.
 * <p>
 * Stores a logistics network frequency UUID (the same system Create's
 * Stock Links use). When linked to a network, this block entity can
 * query the network's inventory summary through Create's LogisticsManager,
 * and the attached CC:Tweaked peripheral exposes that data as Lua functions.
 * </p>
 */
public class LogicLinkBlockEntity extends BlockEntity {

    /** The frequency ID linking this block to a Create logistics network. */
    @Nullable
    private UUID networkFrequency = null;

    /** Cached inventory summary from the logistics network. */
    @Nullable
    private InventorySummary cachedSummary = null;

    /** Whether this link needs to register with LinkNetwork on next tick. */
    private boolean needsRegistration = false;

    /** Tick counter for periodic network inventory refresh. */
    private int refreshTimer = 0;
    private static final int REFRESH_INTERVAL = 40; // Refresh every 2 seconds

    /** Hub scanning range for wireless device discovery. */
    private int hubRange = HubNetwork.DEFAULT_RANGE;

    public LogicLinkBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModRegistry.LOGIC_LINK_BE.get(), pos, blockState);
    }

    // ==================== Network Frequency ====================

    /**
     * Gets the logistics network frequency this block is linked to.
     */
    @Nullable
    public UUID getNetworkFrequency() {
        return networkFrequency;
    }

    /**
     * Sets the logistics network frequency. Called when the block is placed
     * with a tuned LogicLinkBlockItem (right-clicked on an existing Stock Link).
     */
    public void setNetworkFrequency(@Nullable UUID frequency) {
        // Unregister from old frequency
        if (this.networkFrequency != null) {
            LinkNetwork.unregister(this.networkFrequency, this);
        }

        this.networkFrequency = frequency;
        this.cachedSummary = null;
        this.refreshTimer = 0;
        setChanged();

        // Register with new frequency
        if (frequency != null && level != null && !level.isClientSide()) {
            LinkNetwork.register(frequency, this);
        }

        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Returns whether this block is linked to a logistics network.
     */
    public boolean isLinked() {
        return networkFrequency != null;
    }

    // ==================== Inventory Query ====================

    /**
     * Gets the current inventory summary of the linked logistics network.
     * Returns a cached result that refreshes every REFRESH_INTERVAL ticks.
     */
    @Nullable
    public InventorySummary getNetworkSummary() {
        if (!isLinked()) return null;
        return cachedSummary;
    }

    /**
     * Forces a fresh inventory summary from the logistics network.
     * This calls into Create's LogisticsManager.
     */
    public void refreshNetworkSummary() {
        if (!isLinked() || level == null || level.isClientSide()) return;

        try {
            cachedSummary = LogisticsManager.getSummaryOfNetwork(networkFrequency, false);
        } catch (Exception e) {
            LogicLink.LOGGER.debug("Failed to refresh network summary: {}", e.getMessage());
            cachedSummary = null;
        }
    }

    // ==================== Server Tick ====================

    /**
     * Server-side tick. Periodically refreshes the cached inventory summary
     * from the Create logistics network.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, LogicLinkBlockEntity be) {
        // Handle deferred registration (after world load)
        if (be.needsRegistration && be.networkFrequency != null) {
            LinkNetwork.register(be.networkFrequency, be);
            be.needsRegistration = false;
        }

        if (!be.isLinked()) return;

        be.refreshTimer++;
        if (be.refreshTimer >= REFRESH_INTERVAL) {
            be.refreshTimer = 0;
            be.refreshNetworkSummary();
        }
    }

    // ==================== Hub Range ====================

    /**
     * Gets the current hub scanning range in blocks.
     */
    public int getHubRange() {
        return hubRange;
    }

    /**
     * Sets the hub scanning range. Devices within this distance are discoverable.
     */
    public void setHubRange(int range) {
        this.hubRange = Math.max(1, Math.min(HubNetwork.MAX_RANGE, range));
        setChanged();
    }

    // ==================== Cleanup ====================

    /**
     * Called when the block is removed from the world.
     */
    public void onRemoved() {
        if (networkFrequency != null) {
            LinkNetwork.unregister(networkFrequency, this);
        }
    }

    // ==================== NBT Persistence ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (networkFrequency != null) {
            tag.putUUID("Freq", networkFrequency);
        }
        if (hubRange != HubNetwork.DEFAULT_RANGE) {
            tag.putInt("HubRange", hubRange);
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
        hubRange = tag.contains("HubRange") ? tag.getInt("HubRange") : HubNetwork.DEFAULT_RANGE;
        cachedSummary = null;
    }
}
