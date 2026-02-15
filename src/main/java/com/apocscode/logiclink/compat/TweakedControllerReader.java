package com.apocscode.logiclink.compat;

import com.getitemfromblock.create_tweaked_controllers.block.TweakedLecternControllerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Reads controller state from a CTC Tweaked Lectern block entity.
 * This class is ONLY loaded when CTC is present — all references are
 * isolated here to prevent ClassNotFoundError.
 */
public class TweakedControllerReader {

    /**
     * Try to read controller data from a block entity at the given position.
     *
     * @return ControllerData if the BE is a Tweaked Lectern with a user, null otherwise.
     */
    public static ControllerData readFromLectern(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TweakedLecternControllerBlockEntity lectern)) return null;
        if (!lectern.hasUser()) return null;

        boolean[] buttons = new boolean[15];
        float[] axes = new float[6];
        for (int i = 0; i < 15; i++) {
            buttons[i] = lectern.GetButton(i);
        }
        for (int i = 0; i < 6; i++) {
            axes[i] = lectern.GetAxis(i);
        }

        return new ControllerData(buttons, axes, true);
    }

    /**
     * Check if the block entity at the given position is a Tweaked Lectern.
     */
    public static boolean isTweakedLectern(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof TweakedLecternControllerBlockEntity;
    }

    /**
     * Snapshot of controller state at a point in time.
     */
    public record ControllerData(boolean[] buttons, float[] axes, boolean hasUser) {

        /**
         * Get a button state (0-indexed, 0-14).
         */
        public boolean getButton(int index) {
            if (index < 0 || index >= buttons.length) return false;
            return buttons[index];
        }

        /**
         * Get an axis value (0-indexed, 0-5).
         * Sticks: -1.0 to 1.0, Triggers: 0.0 to 1.0
         */
        public float getAxis(int index) {
            if (index < 0 || index >= axes.length) return 0f;
            return axes[index];
        }
    }
}
