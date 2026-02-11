package com.apocscode.logiclink.block;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.ModRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Block entity for the Train Controller.
 * Caches train network data server-side to avoid querying Create's data structures every tick.
 * Data is refreshed at a configurable interval (default: 20 ticks = 1 second).
 */
public class TrainControllerBlockEntity extends BlockEntity {

    /** Cached train data — list of maps, one per train */
    @Nullable private List<Map<String, Object>> cachedTrains = null;
    /** Cached station data — list of maps, one per station */
    @Nullable private List<Map<String, Object>> cachedStations = null;
    /** Cached signal data — list of maps, one per signal boundary */
    @Nullable private List<Map<String, Object>> cachedSignals = null;

    private int refreshTimer = 0;
    private int refreshInterval = 20; // ticks between cache refreshes (1 second default)
    private long lastRefreshTime = 0;

    public TrainControllerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModRegistry.TRAIN_CONTROLLER_BE.get(), pos, blockState);
    }

    // --- Cache Access ---

    @Nullable public List<Map<String, Object>> getCachedTrains() { return cachedTrains; }
    @Nullable public List<Map<String, Object>> getCachedStations() { return cachedStations; }
    @Nullable public List<Map<String, Object>> getCachedSignals() { return cachedSignals; }

    public void setCachedTrains(List<Map<String, Object>> trains) { this.cachedTrains = trains; }
    public void setCachedStations(List<Map<String, Object>> stations) { this.cachedStations = stations; }
    public void setCachedSignals(List<Map<String, Object>> signals) { this.cachedSignals = signals; }

    public long getLastRefreshTime() { return lastRefreshTime; }

    // --- Refresh Interval ---

    public int getRefreshInterval() { return refreshInterval; }
    public void setRefreshInterval(int ticks) {
        this.refreshInterval = Math.max(1, Math.min(200, ticks)); // 1-200 ticks (0.05s - 10s)
        setChanged();
    }

    // --- Server Tick ---

    public static void serverTick(Level level, BlockPos pos, BlockState state, TrainControllerBlockEntity be) {
        be.refreshTimer++;
        if (be.refreshTimer >= be.refreshInterval) {
            be.refreshTimer = 0;
            be.lastRefreshTime = level.getGameTime();
            // Actual data refresh happens lazily in the peripheral when queried,
            // or can be triggered by the peripheral's refresh method.
            // We just track timing here.
        }
    }

    /** Check if a cache refresh is due based on the timer */
    public boolean isRefreshDue() {
        return refreshTimer == 0;
    }

    /** Force invalidation of all caches */
    public void invalidateCaches() {
        cachedTrains = null;
        cachedStations = null;
        cachedSignals = null;
    }

    // --- NBT ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (refreshInterval != 20) {
            tag.putInt("RefreshInterval", refreshInterval);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        refreshInterval = tag.contains("RefreshInterval") ? tag.getInt("RefreshInterval") : 20;
        invalidateCaches();
    }
}
