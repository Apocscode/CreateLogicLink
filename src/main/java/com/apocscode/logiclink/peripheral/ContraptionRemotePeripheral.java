package com.apocscode.logiclink.peripheral;

import com.apocscode.logiclink.block.ContraptionRemoteBlockEntity;
import com.apocscode.logiclink.compat.TweakedControllerCompat;
import com.apocscode.logiclink.compat.TweakedControllerReader;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;

import net.minecraft.core.BlockPos;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CC:Tweaked peripheral for the Contraption Remote block.
 * Peripheral type: {@code "contraption_remote"}
 * <p>
 * Provides programmatic access to controller bindings and live input data.
 * Allows Lua scripts to read controller axes/buttons and manage target bindings.
 * </p>
 *
 * <h3>Example Lua usage:</h3>
 * <pre>{@code
 * local remote = peripheral.wrap("contraption_remote")
 *
 * -- Link to a lectern
 * remote.setLectern(100, 65, 200)
 *
 * -- Add drive targets
 * remote.addTarget(105, 65, 200, "drive")
 * remote.addTarget(110, 65, 200, "creative_motor")
 *
 * -- Read live input
 * print("Button A:", remote.getButton(1))
 * print("Left stick Y:", remote.getAxis(2))
 *
 * -- Check status
 * print("Active:", remote.isActive())
 * print("Targets:", #remote.getTargets())
 * }</pre>
 */
public class ContraptionRemotePeripheral implements IPeripheral {

    private final ContraptionRemoteBlockEntity blockEntity;

    public ContraptionRemotePeripheral(ContraptionRemoteBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public String getType() {
        return "contraption_remote";
    }

    // ==================== Lectern Binding ====================

    /**
     * Sets the linked Tweaked Lectern position (controller input source).
     * @param x X coordinate of the lectern.
     * @param y Y coordinate of the lectern.
     * @param z Z coordinate of the lectern.
     */
    @LuaFunction(mainThread = true)
    public final void setLectern(int x, int y, int z) {
        blockEntity.setLecternPos(new BlockPos(x, y, z));
    }

    /**
     * Gets the linked lectern position.
     * @return Map with x, y, z keys, or nil if no lectern is linked.
     */
    @LuaFunction(mainThread = true)
    public final @Nullable Map<String, Integer> getLectern() {
        BlockPos pos = blockEntity.getLecternPos();
        if (pos == null) return null;
        Map<String, Integer> result = new HashMap<>();
        result.put("x", pos.getX());
        result.put("y", pos.getY());
        result.put("z", pos.getZ());
        return result;
    }

    /**
     * Clears the lectern link.
     */
    @LuaFunction(mainThread = true)
    public final void clearLectern() {
        blockEntity.setLecternPos(null);
    }

    // ==================== Target Management ====================

    /**
     * Adds a drive or motor as a control target.
     * @param x X coordinate.
     * @param y Y coordinate.
     * @param z Z coordinate.
     * @param type "drive" or "creative_motor".
     * @return true if added successfully.
     */
    @LuaFunction(mainThread = true)
    public final boolean addTarget(int x, int y, int z, String type) throws LuaException {
        if (!"drive".equals(type) && !"creative_motor".equals(type)) {
            throw new LuaException("Type must be 'drive' or 'creative_motor'");
        }
        return blockEntity.addTarget(new BlockPos(x, y, z), type);
    }

    /**
     * Removes a target by position.
     * @param x X coordinate.
     * @param y Y coordinate.
     * @param z Z coordinate.
     */
    @LuaFunction(mainThread = true)
    public final void removeTarget(int x, int y, int z) {
        blockEntity.removeTarget(new BlockPos(x, y, z));
    }

    /**
     * Gets all bound targets.
     * @return List of tables with x, y, z, type keys.
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getTargets() {
        return blockEntity.getTargets().stream()
            .map(t -> {
                Map<String, Object> m = new HashMap<>();
                m.put("x", t.pos().getX());
                m.put("y", t.pos().getY());
                m.put("z", t.pos().getZ());
                m.put("type", t.type());
                return m;
            })
            .toList();
    }

    /**
     * Clears all bindings (lectern + targets).
     */
    @LuaFunction(mainThread = true)
    public final void clearAll() {
        blockEntity.clearAll();
    }

    // ==================== Live Controller Input ====================

    /**
     * Reads a button from the linked controller.
     * @param buttonIndex 1-based button index (1–15).
     * @return true if pressed, false if not or no active controller.
     */
    @LuaFunction(mainThread = true)
    public final boolean getButton(int buttonIndex) throws LuaException {
        buttonIndex--;
        if (buttonIndex < 0 || buttonIndex > 14) throw new LuaException("Index out of range: [1,15]");
        if (!TweakedControllerCompat.isLoaded()) return false;

        BlockPos lectern = blockEntity.getLecternPos();
        if (lectern == null || blockEntity.getLevel() == null) return false;

        TweakedControllerReader.ControllerData data =
            TweakedControllerReader.readFromLectern(blockEntity.getLevel(), lectern);
        if (data == null) return false;
        return data.getButton(buttonIndex);
    }

    /**
     * Reads an axis from the linked controller.
     * @param axisIndex 1-based axis index (1–6).
     * @return Axis value (-1.0 to 1.0 for sticks, 0.0 to 1.0 for triggers), or 0.0 if no controller.
     */
    @LuaFunction(mainThread = true)
    public final float getAxis(int axisIndex) throws LuaException {
        axisIndex--;
        if (axisIndex < 0 || axisIndex > 5) throw new LuaException("Index out of range: [1,6]");
        if (!TweakedControllerCompat.isLoaded()) return 0f;

        BlockPos lectern = blockEntity.getLecternPos();
        if (lectern == null || blockEntity.getLevel() == null) return 0f;

        TweakedControllerReader.ControllerData data =
            TweakedControllerReader.readFromLectern(blockEntity.getLevel(), lectern);
        if (data == null) return 0f;
        return data.getAxis(axisIndex);
    }

    /**
     * Checks whether the remote is actively controlling targets.
     * @return true if lectern has a user and targets are bound.
     */
    @LuaFunction(mainThread = true)
    public final boolean isActive() {
        return blockEntity.isActive();
    }

    /**
     * Checks if Create: Tweaked Controllers is installed.
     * @return true if CTC is available.
     */
    @LuaFunction(mainThread = true)
    public final boolean hasControllerSupport() {
        return TweakedControllerCompat.isLoaded();
    }

    // ==================== Peripheral Lifecycle ====================

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof ContraptionRemotePeripheral p && p.blockEntity == this.blockEntity;
    }
}
