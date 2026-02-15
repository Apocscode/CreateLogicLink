package com.apocscode.logiclink.compat;

import com.apocscode.logiclink.LogicLink;

import net.neoforged.fml.ModList;

/**
 * Compatibility layer for Create: Tweaked Controllers.
 * All CTC class references are isolated in {@link TweakedControllerReader}
 * to avoid ClassNotFoundError when CTC is not installed.
 */
public class TweakedControllerCompat {

    private static Boolean loaded = null;

    /**
     * Check if Create: Tweaked Controllers is installed.
     */
    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("create_tweaked_controllers");
            if (loaded) {
                LogicLink.LOGGER.info("Create: Tweaked Controllers detected — enabling controller integration");
            }
        }
        return loaded;
    }
}
