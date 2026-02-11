package com.apocscode.logiclink.peripheral;

import com.apocscode.logiclink.block.LogicDriveBlockEntity;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;

import org.jetbrains.annotations.Nullable;

/**
 * CC:Tweaked peripheral for the Logic Drive.
 * Controls a rotation modifier that takes external kinetic input and
 * allows Lua scripts to gate, reverse, and scale the rotation.
 * <p>
 * Peripheral type: {@code "logic_drive"}
 * </p>
 *
 * <h3>Example Lua usage:</h3>
 * <pre>{@code
 * local drive = peripheral.wrap("logic_drive")
 * drive.enable()                   -- pass rotation through
 * drive.disable()                  -- disconnect (clutch off)
 * drive.setReversed(true)          -- reverse direction
 * drive.setModifier(2.0)           -- double speed
 *
 * -- Sequenced motion
 * drive.clearSequence()
 * drive.addRotateStep(90, 1.0)     -- 90° at 1× speed
 * drive.addWaitStep(20)            -- pause 1 second
 * drive.addRotateStep(-90, 2.0)    -- back 90° at 2× speed
 * drive.runSequence(false)         -- run once
 * }</pre>
 */
public class LogicDrivePeripheral implements IPeripheral {

    private final LogicDriveBlockEntity blockEntity;

    public LogicDrivePeripheral(LogicDriveBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public String getType() {
        return "logic_drive";
    }

    // ==================== Basic Control ====================

    /**
     * Enables the drive — allows rotation to pass through.
     */
    @LuaFunction(mainThread = true)
    public final void enable() {
        blockEntity.setMotorEnabled(true);
    }

    /**
     * Disables the drive — disconnects rotation (clutch off).
     */
    @LuaFunction(mainThread = true)
    public final void disable() {
        blockEntity.setMotorEnabled(false);
    }

    /**
     * Checks whether the drive is enabled.
     * @return true if rotation passes through.
     */
    @LuaFunction(mainThread = true)
    public final boolean isEnabled() {
        return blockEntity.isMotorEnabled();
    }

    /**
     * Sets whether output rotation is reversed.
     * @param reversed true to reverse, false for normal direction.
     */
    @LuaFunction(mainThread = true)
    public final void setReversed(boolean reversed) {
        blockEntity.setReversed(reversed);
    }

    /**
     * Checks if the drive is in reverse mode.
     * @return true if reversed.
     */
    @LuaFunction(mainThread = true)
    public final boolean isReversed() {
        return blockEntity.isReversed();
    }

    /**
     * Sets the speed modifier. Input rotation is multiplied by this value.
     * Examples: 0.5 = half speed, 1.0 = pass-through, 2.0 = double speed,
     * -1.0 = reverse at same speed.
     * Range: -16.0 to 16.0.
     * @param modifier The speed multiplier.
     */
    @LuaFunction(mainThread = true)
    public final void setModifier(double modifier) throws LuaException {
        if (modifier < -16.0 || modifier > 16.0) {
            throw new LuaException("Modifier must be between -16.0 and 16.0");
        }
        blockEntity.setSpeedModifier((float) modifier);
    }

    /**
     * Gets the current speed modifier.
     * @return The speed multiplier.
     */
    @LuaFunction(mainThread = true)
    public final float getModifier() {
        return blockEntity.getSpeedModifier();
    }

    /**
     * Gets the input rotation speed (from the external source).
     * @return Input RPM.
     */
    @LuaFunction(mainThread = true)
    public final float getInputSpeed() {
        return blockEntity.getInputSpeed();
    }

    /**
     * Gets the output rotation speed (after modifier is applied).
     * @return Output RPM.
     */
    @LuaFunction(mainThread = true)
    public final float getOutputSpeed() {
        return blockEntity.getOutputSpeed();
    }

    // ==================== Sequence Control ====================

    /**
     * Clears all sequence instructions and stops any running sequence.
     */
    @LuaFunction(mainThread = true)
    public final void clearSequence() {
        blockEntity.clearSequence();
    }

    /**
     * Adds a rotation step: rotate the given degrees with the given modifier.
     * Speed depends on input rotation × modifier.
     * Positive degrees = forward, negative = reverse.
     * @param degrees Degrees to rotate.
     * @param modifier Speed multiplier for this step (0.1-16.0).
     */
    @LuaFunction(mainThread = true)
    public final void addRotateStep(double degrees, double modifier) throws LuaException {
        if (modifier < 0.1 || modifier > 16.0) {
            throw new LuaException("Modifier must be between 0.1 and 16.0");
        }
        if (degrees == 0) {
            throw new LuaException("Degrees must be non-zero");
        }
        blockEntity.addRotateStep((float) degrees, (float) modifier);
    }

    /**
     * Adds a wait step: pause (disconnect rotation) for the given ticks.
     * 20 ticks = 1 second.
     * @param ticks Number of ticks to wait (1-6000).
     */
    @LuaFunction(mainThread = true)
    public final void addWaitStep(int ticks) throws LuaException {
        if (ticks < 1 || ticks > 6000) {
            throw new LuaException("Ticks must be between 1 and 6000 (5 minutes max)");
        }
        blockEntity.addWaitStep(ticks);
    }

    /**
     * Adds a modifier-change step: instantly changes the speed modifier.
     * @param modifier New speed multiplier (-16.0 to 16.0).
     */
    @LuaFunction(mainThread = true)
    public final void addModifierStep(double modifier) throws LuaException {
        if (modifier < -16.0 || modifier > 16.0) {
            throw new LuaException("Modifier must be between -16.0 and 16.0");
        }
        blockEntity.addModifierStep((float) modifier);
    }

    /**
     * Runs the queued sequence.
     * @param loop If true, loops forever until stopSequence() is called.
     */
    @LuaFunction(mainThread = true)
    public final void runSequence(boolean loop) throws LuaException {
        if (blockEntity.getSequenceSize() == 0) {
            throw new LuaException("No sequence steps queued — use addRotateStep/addWaitStep first");
        }
        blockEntity.runSequence(loop);
    }

    /**
     * Stops the currently running sequence.
     */
    @LuaFunction(mainThread = true)
    public final void stopSequence() {
        blockEntity.stopSequence();
    }

    /**
     * Checks if a sequence is currently running.
     * @return true if a sequence is in progress.
     */
    @LuaFunction(mainThread = true)
    public final boolean isSequenceRunning() {
        return blockEntity.isSequenceRunning();
    }

    /**
     * Gets the number of steps in the current sequence.
     * @return Number of queued steps.
     */
    @LuaFunction(mainThread = true)
    public final int getSequenceSize() {
        return blockEntity.getSequenceSize();
    }

    // ==================== IPeripheral ====================

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof LogicDrivePeripheral o
                && o.blockEntity.getBlockPos().equals(this.blockEntity.getBlockPos());
    }
}
