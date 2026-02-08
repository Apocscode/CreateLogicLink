package com.apocscode.logiclink.block;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.ModRegistry;

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

    /** Tick counter for periodic network inventory refresh. */
    private int refreshTimer = 0;
    private static final int REFRESH_INTERVAL = 40; // Refresh every 2 seconds

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
        this.networkFrequency = frequency;
        this.cachedSummary = null;
        this.refreshTimer = 0;
        setChanged();
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
        if (!be.isLinked()) return;

        be.refreshTimer++;
        if (be.refreshTimer >= REFRESH_INTERVAL) {
            be.refreshTimer = 0;
            be.refreshNetworkSummary();
        }
    }

    // ==================== Cleanup ====================

    /**
     * Called when the block is removed from the world.
     */
    public void onRemoved() {
        // Nothing to unregister since we're read-only observers of the network
    }

    // ==================== NBT Persistence ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (networkFrequency != null) {
            tag.putUUID("Freq", networkFrequency);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("Freq")) {
            networkFrequency = tag.getUUID("Freq");
        } else {
            networkFrequency = null;
        }
        cachedSummary = null;
    }
}
