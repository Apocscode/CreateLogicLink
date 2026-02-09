package com.apocscode.logiclink.peripheral;

import com.apocscode.logiclink.block.RedstoneControllerBlockEntity;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CC:Tweaked peripheral for the Redstone Controller block.
 * <p>
 * Provides programmatic control over Create's Redstone Link wireless network.
 * One Redstone Controller can manage unlimited frequency channels simultaneously,
 * each identified by a pair of item names (matching Create's two-slot system).
 * </p>
 *
 * <h3>Example Lua usage:</h3>
 * <pre>{@code
 * local rc = peripheral.wrap("redstone_controller")
 *
 * -- Transmit signal strength 15 on the (redstone, lever) channel
 * rc.setOutput("minecraft:redstone", "minecraft:lever", 15)
 *
 * -- Read incoming signal on a different channel
 * local power = rc.getInput("minecraft:blue_dye", "minecraft:stick")
 * print("Received: " .. power)
 *
 * -- List all active channels
 * for _, ch in ipairs(rc.getChannels()) do
 *     print(ch.item1 .. " + " .. ch.item2 .. " = " .. ch.mode .. " @ " .. ch.power)
 * end
 *
 * -- Kill switch: zero all transmit channels
 * rc.setAllOutputs(0)
 *
 * -- Clean up
 * rc.clearChannels()
 * }</pre>
 */
public class RedstoneControllerPeripheral implements IPeripheral {

    private final RedstoneControllerBlockEntity blockEntity;

    public RedstoneControllerPeripheral(RedstoneControllerBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public String getType() {
        return "redstone_controller";
    }

    // ==================== Lua API Methods ====================

    /**
     * Transmits a redstone signal (0–15) on a frequency channel.
     * Creates the channel if it doesn't exist. If the channel was previously
     * a receiver, it switches to transmitter mode.
     *
     * @param item1 Registry name of the first frequency item (e.g. "minecraft:redstone").
     * @param item2 Registry name of the second frequency item (e.g. "minecraft:lever").
     * @param power Signal strength to transmit (0–15).
     * @throws LuaException if items are invalid or power is out of range.
     */
    @LuaFunction(mainThread = true)
    public final void setOutput(String item1, String item2, int power) throws LuaException {
        validateItem(item1);
        validateItem(item2);
        if (power < 0 || power > 15) {
            throw new LuaException("Power must be 0-15");
        }
        blockEntity.setOutput(item1, item2, power);
    }

    /**
     * Reads the received redstone signal on a frequency channel.
     * Creates a receiver channel if it doesn't exist. If the channel was
     * previously a transmitter, it switches to receiver mode.
     * <p>
     * The returned value is the maximum signal strength from any transmitter
     * on this frequency within Create's link range.
     *
     * @param item1 Registry name of the first frequency item.
     * @param item2 Registry name of the second frequency item.
     * @return The received signal strength (0–15).
     * @throws LuaException if items are invalid.
     */
    @LuaFunction(mainThread = true)
    public final int getInput(String item1, String item2) throws LuaException {
        validateItem(item1);
        validateItem(item2);
        return blockEntity.getInput(item1, item2);
    }

    /**
     * Gets the current transmit power of a channel without modifying anything.
     * Returns 0 if the channel doesn't exist or is a receiver.
     *
     * @param item1 Registry name of the first frequency item.
     * @param item2 Registry name of the second frequency item.
     * @return The current transmit power (0–15), or 0.
     * @throws LuaException if items are invalid.
     */
    @LuaFunction
    public final int getOutput(String item1, String item2) throws LuaException {
        validateItem(item1);
        validateItem(item2);
        return blockEntity.getOutput(item1, item2);
    }

    /**
     * Removes a specific channel entirely, unregistering it from Create's
     * Redstone Link network. The frequency slot becomes available again.
     *
     * @param item1 Registry name of the first frequency item.
     * @param item2 Registry name of the second frequency item.
     * @throws LuaException if items are invalid.
     */
    @LuaFunction(mainThread = true)
    public final void removeChannel(String item1, String item2) throws LuaException {
        validateItem(item1);
        validateItem(item2);
        blockEntity.removeChannel(item1, item2);
    }

    /**
     * Returns a list of all active channels managed by this controller.
     * Each entry is a table with:
     * <ul>
     *   <li>{@code item1} — First frequency item registry name</li>
     *   <li>{@code item2} — Second frequency item registry name</li>
     *   <li>{@code mode} — "transmit" or "receive"</li>
     *   <li>{@code power} — Current signal strength (transmitted or received)</li>
     * </ul>
     *
     * @return A list of channel info tables.
     */
    @LuaFunction
    public final List<Map<String, Object>> getChannels() {
        return blockEntity.getChannelList();
    }

    /**
     * Sets all transmit channels to the given power level.
     * Receiver channels are not affected.
     * Useful as a "kill switch" to zero all outputs: {@code rc.setAllOutputs(0)}
     *
     * @param power Signal strength to set (0–15).
     * @throws LuaException if power is out of range.
     */
    @LuaFunction(mainThread = true)
    public final void setAllOutputs(int power) throws LuaException {
        if (power < 0 || power > 15) {
            throw new LuaException("Power must be 0-15");
        }
        blockEntity.setAllOutputs(power);
    }

    /**
     * Removes all channels, unregistering them from Create's Redstone Link network.
     */
    @LuaFunction(mainThread = true)
    public final void clearChannels() {
        blockEntity.clearChannels();
    }

    /**
     * Returns the position of this Redstone Controller block.
     *
     * @return A table with x, y, z coordinates.
     */
    @LuaFunction
    public final Map<String, Integer> getPosition() {
        Map<String, Integer> pos = new HashMap<>();
        pos.put("x", blockEntity.getBlockPos().getX());
        pos.put("y", blockEntity.getBlockPos().getY());
        pos.put("z", blockEntity.getBlockPos().getZ());
        return pos;
    }

    // ==================== Validation ====================

    /**
     * Validates that an item name refers to a real, non-air item in the registry.
     */
    private void validateItem(String itemName) throws LuaException {
        if (itemName == null || itemName.isEmpty()) {
            throw new LuaException("Item name cannot be empty");
        }
        ResourceLocation loc = ResourceLocation.tryParse(itemName);
        if (loc == null) {
            throw new LuaException("Invalid item name: " + itemName);
        }
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(loc));
        if (stack.isEmpty()) {
            throw new LuaException("Unknown item: " + itemName);
        }
    }

    // ==================== Peripheral Equality ====================

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        if (this == other) return true;
        if (!(other instanceof RedstoneControllerPeripheral o)) return false;
        return blockEntity.getBlockPos().equals(o.blockEntity.getBlockPos());
    }
}
