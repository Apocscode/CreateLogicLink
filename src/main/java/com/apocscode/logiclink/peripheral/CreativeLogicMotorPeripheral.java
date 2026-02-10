package com.apocscode.logiclink.peripheral;

import com.apocscode.logiclink.block.CreativeLogicMotorBlockEntity;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;

import org.jetbrains.annotations.Nullable;

/**
 * CC:Tweaked peripheral for the Creative Logic Motor.
 * Provides Lua functions to control a kinetic rotation source:
 * set speed, on/off, reverse, and programmable sequences.
 * <p>
 * Peripheral type: {@code "creative_logic_motor"}
 * </p>
 *
 * <h3>Example Lua usage:</h3>
 * <pre>{@code
 * local motor = peripheral.wrap("creative_logic_motor")
 * motor.enable()
 * motor.setSpeed(64)          -- 64 RPM forward
 * motor.setSpeed(-128)        -- 128 RPM reverse
 * motor.stop()                -- 0 RPM
 *
 * -- Sequenced rotation
 * motor.clearSequence()
 * motor.addRotateStep(90, 32)     -- 90° at 32 RPM
 * motor.addWaitStep(20)           -- pause 1 second
 * motor.addRotateStep(-90, 64)    -- back 90° at 64 RPM
 * motor.runSequence(false)        -- run once
 * motor.runSequence(true)         -- loop forever
 * }</pre>
 */
public class CreativeLogicMotorPeripheral implements IPeripheral {

    private final CreativeLogicMotorBlockEntity blockEntity;

    public CreativeLogicMotorPeripheral(CreativeLogicMotorBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public String getType() {
        return "creative_logic_motor";
    }

    // ==================== Basic Control ====================

    /**
     * Sets the motor speed in RPM. Range: -256 to 256.
     * Positive = forward, negative = reverse.
     * @param speed The target RPM.
     */
    @LuaFunction(mainThread = true)
    public final void setSpeed(int speed) throws LuaException {
        if (speed < -256 || speed > 256) {
            throw new LuaException("Speed must be between -256 and 256");
        }
        blockEntity.setMotorSpeed(speed);
    }

    /**
     * Gets the current target speed in RPM.
     * @return The target RPM (may differ from actual if overstressed).
     */
    @LuaFunction(mainThread = true)
    public final int getSpeed() {
        return blockEntity.getMotorSpeed();
    }

    /**
     * Gets the actual network rotation speed (after stress/load).
     * @return Actual RPM as seen by the kinetic network.
     */
    @LuaFunction(mainThread = true)
    public final float getActualSpeed() {
        return blockEntity.getActualSpeed();
    }

    /**
     * Enables the motor (starts generating rotation at the set speed).
     */
    @LuaFunction(mainThread = true)
    public final void enable() {
        blockEntity.setEnabled(true);
    }

    /**
     * Disables the motor (stops all rotation, speed goes to 0).
     */
    @LuaFunction(mainThread = true)
    public final void disable() {
        blockEntity.setEnabled(false);
    }

    /**
     * Stops the motor (sets speed to 0 and disables).
     */
    @LuaFunction(mainThread = true)
    public final void stop() {
        blockEntity.setMotorSpeed(0);
        blockEntity.setEnabled(false);
    }

    /**
     * Checks whether the motor is currently enabled.
     * @return true if enabled and generating rotation.
     */
    @LuaFunction(mainThread = true)
    public final boolean isEnabled() {
        return blockEntity.isEnabled();
    }

    /**
     * Checks whether the motor is currently running (enabled and speed != 0).
     * @return true if generating non-zero rotation.
     */
    @LuaFunction(mainThread = true)
    public final boolean isRunning() {
        return blockEntity.isEnabled() && blockEntity.getMotorSpeed() != 0;
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
     * Adds a rotation step: rotate the given degrees at the given speed.
     * Positive degrees = forward direction, negative = reverse.
     * @param degrees Degrees to rotate (e.g. 90, 180, 360, -90).
     * @param speed RPM for this rotation step (1-256).
     */
    @LuaFunction(mainThread = true)
    public final void addRotateStep(double degrees, int speed) throws LuaException {
        if (speed < 1 || speed > 256) {
            throw new LuaException("Speed must be between 1 and 256");
        }
        if (degrees == 0) {
            throw new LuaException("Degrees must be non-zero");
        }
        blockEntity.addRotateStep((float) degrees, speed);
    }

    /**
     * Adds a wait step: pause for the given number of ticks.
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
     * Adds a speed-change step: instantly changes the continuous speed.
     * @param speed New RPM (-256 to 256).
     */
    @LuaFunction(mainThread = true)
    public final void addSpeedStep(int speed) throws LuaException {
        if (speed < -256 || speed > 256) {
            throw new LuaException("Speed must be between -256 and 256");
        }
        blockEntity.addSpeedStep(speed);
    }

    /**
     * Runs the queued sequence.
     * @param loop If true, loops the sequence forever until stopSequence() is called.
     */
    @LuaFunction(mainThread = true)
    public final void runSequence(boolean loop) throws LuaException {
        if (blockEntity.getSequenceSize() == 0) {
            throw new LuaException("No sequence steps queued — use addRotateStep/addWaitStep first");
        }
        blockEntity.runSequence(loop);
    }

    /**
     * Stops the currently running sequence (if any).
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
        return other instanceof CreativeLogicMotorPeripheral o
                && o.blockEntity.getBlockPos().equals(this.blockEntity.getBlockPos());
    }
}
