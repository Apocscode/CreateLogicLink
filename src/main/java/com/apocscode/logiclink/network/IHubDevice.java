package com.apocscode.logiclink.network;

import net.minecraft.core.BlockPos;

/**
 * Interface for block entities that participate in the Logic Link wireless hub network.
 * Implemented by Logic Sensors, Redstone Controllers, and Logic Motors.
 * <p>
 * Devices implementing this interface are automatically discoverable by any Logic Link
 * within range, enabling wireless monitoring and control without wired connections
 * or network frequency pairing.
 * </p>
 *
 * <h3>Identification:</h3>
 * Each device gets an auto-generated ID based on type + index (e.g. "sensor_0", "motor_1").
 * Users can assign custom labels via Lua for meaningful names (e.g. "boiler_temp", "mixer_speed").
 * Labels persist in NBT and are shown in Create's goggle overlay tooltip.
 */
public interface IHubDevice {

    /**
     * Gets the user-assigned label for this device.
     * Labels allow Lua scripts to reference devices by meaningful names
     * instead of auto-generated IDs.
     *
     * @return The label, or empty string if not set.
     */
    String getHubLabel();

    /**
     * Sets the user-assigned label for this device.
     * Labels are persisted in NBT and shown in goggle tooltips.
     *
     * @param label The label to set (max 32 characters, empty to clear).
     */
    void setHubLabel(String label);

    /**
     * Gets the device type identifier used for auto-ID generation.
     * Auto-IDs are formatted as "{type}_{index}" (e.g. "sensor_0", "motor_1").
     *
     * @return One of: "sensor", "creative_motor", "motor", "redstone_controller"
     */
    String getDeviceType();

    /**
     * Gets the block position of this device.
     */
    BlockPos getDevicePos();
}
