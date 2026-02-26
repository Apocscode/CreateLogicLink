package com.apocscode.logiclink.client;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side singleton that holds individually toggled signal highlight markers.
 * The tablet screen adds/removes markers here when the player clicks the highlight button.
 * SignalGhostRenderer reads from this manager to draw in-world boxes.
 *
 * This decouples highlights from the TrainMonitorBlockEntity — no need to be
 * near the monitor block; just stand near the target coordinates.
 */
public final class SignalHighlightManager {

    public static final int TYPE_SIGNAL   = 0;  // Regular signal (green)
    public static final int TYPE_CHAIN    = 1;  // Chain signal (cyan)
    public static final int TYPE_CONFLICT = 2;  // Conflict (red)

    public record Marker(int x, int y, int z, int type, float dirX, float dirZ) {
        /** Position-only key (for checking if ANY marker exists at this block). */
        public long posKey() {
            return ((long)(x + 30000000) << 36) | ((long)(y + 512) << 26) | ((long)(z + 30000000));
        }

        /**
         * Unique key including direction. Quantizes direction to 8 compass octants
         * so opposing directions get distinct keys but near-identical dirs merge.
         */
        public long uniqueKey() {
            int dirBits = 0;
            if (dirX != 0 || dirZ != 0) {
                // Quantize to 8 directions (0-7)
                double angle = Math.atan2(dirZ, dirX);
                dirBits = 1 + (((int) Math.round(angle / (Math.PI / 4)) % 8) + 8) % 8;
            }
            return (posKey() << 4) | dirBits;
        }

        /** Returns true if this marker has a valid track direction for arrow rendering. */
        public boolean hasDirection() {
            return dirX != 0 || dirZ != 0;
        }
    }

    private static final Map<Long, Marker> activeMarkers = new ConcurrentHashMap<>();

    /** Toggle a marker on/off. Returns true if now active, false if removed. */
    public static boolean toggle(int x, int y, int z, int type, float dirX, float dirZ) {
        Marker m = new Marker(x, y, z, type, dirX, dirZ);
        long key = m.uniqueKey();
        if (activeMarkers.containsKey(key)) {
            activeMarkers.remove(key);
            return false;
        } else {
            activeMarkers.put(key, m);
            return true;
        }
    }

    /** Toggle a marker on/off (no direction). */
    public static boolean toggle(int x, int y, int z, int type) {
        return toggle(x, y, z, type, 0, 0);
    }

    /** Check if a position is currently highlighted (any direction). */
    public static boolean isActive(int x, int y, int z) {
        // Check all possible direction bits (0-8)
        long baseKey = new Marker(x, y, z, 0, 0, 0).posKey() << 4;
        for (int d = 0; d <= 8; d++) {
            if (activeMarkers.containsKey(baseKey | d)) return true;
        }
        return false;
    }

    /** Get all active markers for the renderer. */
    public static Collection<Marker> getActiveMarkers() {
        return Collections.unmodifiableCollection(activeMarkers.values());
    }

    /** Clear all highlights. */
    public static void clearAll() {
        activeMarkers.clear();
    }

    public static int count() {
        return activeMarkers.size();
    }
}
