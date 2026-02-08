package com.apocscode.logiclink.network;

import com.apocscode.logiclink.block.LogicSensorBlockEntity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static registry that tracks all Logic Sensor block entities by their
 * logistics network frequency UUID. This allows the Logic Link peripheral
 * to discover all sensors on its network without world scanning.
 * <p>
 * Uses WeakReferences to avoid memory leaks — sensors that are unloaded
 * or removed are automatically cleaned up during queries.
 * </p>
 */
public class SensorNetwork {

    private static final Map<UUID, Set<WeakReference<LogicSensorBlockEntity>>> SENSORS =
            new ConcurrentHashMap<>();

    /**
     * Register a sensor with a network frequency.
     */
    public static void register(UUID freq, LogicSensorBlockEntity sensor) {
        if (freq == null || sensor == null) return;
        SENSORS.computeIfAbsent(freq, k -> ConcurrentHashMap.newKeySet())
                .add(new WeakReference<>(sensor));
    }

    /**
     * Unregister a sensor from a network frequency.
     */
    public static void unregister(UUID freq, LogicSensorBlockEntity sensor) {
        if (freq == null) return;
        Set<WeakReference<LogicSensorBlockEntity>> set = SENSORS.get(freq);
        if (set != null) {
            set.removeIf(ref -> {
                LogicSensorBlockEntity be = ref.get();
                return be == null || be == sensor;
            });
            if (set.isEmpty()) {
                SENSORS.remove(freq);
            }
        }
    }

    /**
     * Get all live sensors on a given network frequency.
     * Dead/removed references are cleaned up automatically.
     */
    public static List<LogicSensorBlockEntity> getSensors(UUID freq) {
        if (freq == null) return Collections.emptyList();
        Set<WeakReference<LogicSensorBlockEntity>> set = SENSORS.get(freq);
        if (set == null) return Collections.emptyList();

        List<LogicSensorBlockEntity> result = new ArrayList<>();
        set.removeIf(ref -> {
            LogicSensorBlockEntity be = ref.get();
            if (be == null || be.isRemoved()) return true;
            result.add(be);
            return false;
        });

        if (set.isEmpty()) {
            SENSORS.remove(freq);
        }
        return result;
    }

    /**
     * Clear all sensor registrations. Called on server shutdown.
     */
    public static void clear() {
        SENSORS.clear();
    }
}
