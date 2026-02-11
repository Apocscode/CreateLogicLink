package com.apocscode.logiclink.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global proximity-based registry for all Logic Link hub-capable devices.
 * <p>
 * Unlike the frequency-based {@link SensorNetwork} and {@link LinkNetwork},
 * HubNetwork operates on proximity — any device within range of a Logic Link
 * is automatically discoverable. No frequency tuning or manual pairing needed.
 * </p>
 * <p>
 * Devices register on placement/load and unregister on removal. The Logic Link
 * peripheral queries this registry to find all devices within its configurable range.
 * Results are sorted by position (x, y, z) for stable auto-ID assignment.
 * </p>
 */
public class HubNetwork {

    /** All registered hub devices. Uses WeakReferences for automatic cleanup. */
    private static final Set<WeakReference<BlockEntity>> ALL_DEVICES = ConcurrentHashMap.newKeySet();

    /** Default hub scanning range in blocks. */
    public static final int DEFAULT_RANGE = 64;

    /** Maximum allowed hub range. */
    public static final int MAX_RANGE = 256;

    /**
     * Register a device with the hub network.
     * Safe to call multiple times — deduplicates automatically.
     *
     * @param device The block entity to register (must implement {@link IHubDevice}).
     */
    public static void register(BlockEntity device) {
        if (device == null) return;
        // Remove existing reference to avoid duplicates
        ALL_DEVICES.removeIf(ref -> {
            BlockEntity be = ref.get();
            return be == null || be == device;
        });
        ALL_DEVICES.add(new WeakReference<>(device));
    }

    /**
     * Unregister a device from the hub network.
     *
     * @param device The block entity to unregister.
     */
    public static void unregister(BlockEntity device) {
        if (device == null) return;
        ALL_DEVICES.removeIf(ref -> {
            BlockEntity be = ref.get();
            return be == null || be == device;
        });
    }

    /**
     * Get all live hub devices within range of a center position.
     * Dead/removed references are cleaned up automatically.
     * Results are sorted by position (x, y, z) for stable auto-ID assignment.
     *
     * @param level  The world/dimension to search in.
     * @param center The center position (typically the Logic Link's position).
     * @param range  Maximum distance in blocks.
     * @return Sorted list of block entities within range that implement {@link IHubDevice}.
     */
    public static List<BlockEntity> getDevicesInRange(Level level, BlockPos center, int range) {
        List<BlockEntity> result = new ArrayList<>();
        long rangeSq = (long) range * range;

        ALL_DEVICES.removeIf(ref -> {
            BlockEntity be = ref.get();
            if (be == null || be.isRemoved()) return true; // clean up dead refs
            if (be.getLevel() != level) return false; // different dimension, keep but skip
            if (!(be instanceof IHubDevice)) return false; // not a hub device, keep but skip
            if (be.getBlockPos().distSqr(center) <= rangeSq) {
                result.add(be);
            }
            return false;
        });

        // Sort by position for stable auto-ID assignment
        result.sort((a, b) -> {
            BlockPos pa = a.getBlockPos();
            BlockPos pb = b.getBlockPos();
            int cmp = Integer.compare(pa.getX(), pb.getX());
            if (cmp != 0) return cmp;
            cmp = Integer.compare(pa.getY(), pb.getY());
            if (cmp != 0) return cmp;
            return Integer.compare(pa.getZ(), pb.getZ());
        });

        return result;
    }

    /**
     * Get ALL live hub devices across all dimensions, with no range limit.
     * Dead/removed references are cleaned up automatically.
     * Results are sorted by dimension then position (x, y, z) for stable auto-ID assignment.
     *
     * @return Sorted list of all registered block entities that implement {@link IHubDevice}.
     */
    public static List<BlockEntity> getAllDevices() {
        List<BlockEntity> result = new ArrayList<>();

        ALL_DEVICES.removeIf(ref -> {
            BlockEntity be = ref.get();
            if (be == null || be.isRemoved()) return true;
            if (!(be instanceof IHubDevice)) return false;
            result.add(be);
            return false;
        });

        // Sort by dimension name, then position for stable auto-ID assignment
        result.sort((a, b) -> {
            // Dimension first
            String dimA = a.getLevel() != null ? a.getLevel().dimension().location().toString() : "";
            String dimB = b.getLevel() != null ? b.getLevel().dimension().location().toString() : "";
            int cmp = dimA.compareTo(dimB);
            if (cmp != 0) return cmp;
            // Then position
            BlockPos pa = a.getBlockPos();
            BlockPos pb = b.getBlockPos();
            cmp = Integer.compare(pa.getX(), pb.getX());
            if (cmp != 0) return cmp;
            cmp = Integer.compare(pa.getY(), pb.getY());
            if (cmp != 0) return cmp;
            return Integer.compare(pa.getZ(), pb.getZ());
        });

        return result;
    }

    /**
     * Clear all device registrations. Called on server shutdown.
     */
    public static void clear() {
        ALL_DEVICES.clear();
    }
}
