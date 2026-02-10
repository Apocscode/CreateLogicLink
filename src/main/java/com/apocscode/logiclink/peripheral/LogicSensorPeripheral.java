package com.apocscode.logiclink.peripheral;

import com.apocscode.logiclink.block.LogicSensorBlockEntity;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * CC:Tweaked peripheral for the Logic Sensor block.
 * <p>
 * Provides direct wired access to the sensor's data when a computer is
 * placed adjacent to it. The sensor reads data from the Create machine
 * block it's attached to.
 * </p>
 *
 * <h3>Example Lua usage:</h3>
 * <pre>{@code
 * local sensor = peripheral.wrap("logicsensor")
 *
 * -- Get cached data (fast, refreshes every second)
 * local data = sensor.getData()
 * if data.isKinetic then
 *     print("Speed: " .. data.speed .. " RPM")
 * end
 *
 * -- Get fresh data (forces immediate read)
 * local fresh = sensor.getTargetData()
 * for _, item in ipairs(fresh.inventory or {}) do
 *     print(item.name .. " x" .. item.count)
 * end
 * }</pre>
 */
public class LogicSensorPeripheral implements IPeripheral {

    private final LogicSensorBlockEntity blockEntity;

    public LogicSensorPeripheral(LogicSensorBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public String getType() {
        return "logicsensor";
    }

    // ==================== Lua API Methods ====================

    /**
     * Returns whether this sensor is linked to a logistics network.
     */
    @LuaFunction(mainThread = true)
    public final boolean isLinked() {
        return blockEntity.isLinked();
    }

    /**
     * Returns the network frequency UUID as a string, or nil if not linked.
     */
    @LuaFunction(mainThread = true)
    @Nullable
    public final String getNetworkID() {
        if (blockEntity.getNetworkFrequency() == null) return null;
        return blockEntity.getNetworkFrequency().toString();
    }

    /**
     * Returns the position of this sensor block.
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Integer> getPosition() {
        Map<String, Integer> pos = new HashMap<>();
        pos.put("x", blockEntity.getBlockPos().getX());
        pos.put("y", blockEntity.getBlockPos().getY());
        pos.put("z", blockEntity.getBlockPos().getZ());
        return pos;
    }

    /**
     * Returns the position of the target block (the machine this sensor reads).
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Integer> getTargetPosition() {
        var targetPos = blockEntity.getTargetPos();
        Map<String, Integer> pos = new HashMap<>();
        pos.put("x", targetPos.getX());
        pos.put("y", targetPos.getY());
        pos.put("z", targetPos.getZ());
        return pos;
    }

    /**
     * Returns cached data from the target block.
     * This is fast (no world access) and refreshes automatically every second.
     * Returns an empty table if no data is available yet.
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> getData() {
        Map<String, Object> data = blockEntity.getCachedData();
        return data != null ? data : new HashMap<>();
    }

    /**
     * Forces an immediate fresh read from the target block and returns the data.
     * This must run on the main thread since it accesses the world.
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> getTargetData() {
        blockEntity.refreshData();
        Map<String, Object> data = blockEntity.getCachedData();
        return data != null ? data : new HashMap<>();
    }

    /**
     * Forces a refresh of the cached data.
     */
    @LuaFunction(mainThread = true)
    public final void refresh() {
        blockEntity.refreshData();
    }

    // ==================== Peripheral Equality ====================

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        if (this == other) return true;
        if (!(other instanceof LogicSensorPeripheral otherSensor)) return false;
        return blockEntity.getBlockPos().equals(otherSensor.blockEntity.getBlockPos());
    }
}
