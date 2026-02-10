package com.apocscode.logiclink.peripheral.storage;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;

import net.fxnt.fxntstorage.controller.StorageInterfaceEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * CC:Tweaked peripheral for Create Storage's Storage Interface block.
 * <p>
 * The Storage Interface connects to a Storage Controller and provides
 * remote access to the same storage network. This peripheral exposes
 * the identical inventory view as the controller peripheral, but through
 * the interface block — useful for distributed access points.
 * </p>
 *
 * <h3>Peripheral type: {@code "storage_interface"}</h3>
 *
 * <h3>Example Lua usage:</h3>
 * <pre>{@code
 * local iface = peripheral.find("storage_interface")
 *
 * -- Check if connected to a controller
 * if iface.isConnected() then
 *     local items = iface.list()
 *     for slot, item in pairs(items) do
 *         print(item.name .. " x" .. item.count)
 *     end
 * end
 * }</pre>
 */
public class StorageInterfacePeripheral implements IPeripheral {

    private final StorageInterfaceEntity blockEntity;

    public StorageInterfacePeripheral(StorageInterfaceEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public String getType() {
        return "storage_interface";
    }

    // ==================== Connection Info ====================

    /**
     * Returns whether this interface is connected to a Storage Controller.
     *
     * @return true if a controller is connected and valid
     */
    @LuaFunction(mainThread = true)
    public final boolean isConnected() {
        return blockEntity.controller != null;
    }

    /**
     * Returns the position of this interface block.
     *
     * @return table with x, y, z keys
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
     * Returns the position of the connected Storage Controller, or nil if not connected.
     *
     * @return table with x, y, z keys, or nil
     */
    @LuaFunction(mainThread = true)
    @Nullable
    public final Map<String, Integer> getControllerPosition() {
        if (blockEntity.controller == null) return null;
        Map<String, Integer> pos = new HashMap<>();
        pos.put("x", blockEntity.controller.getBlockPos().getX());
        pos.put("y", blockEntity.controller.getBlockPos().getY());
        pos.put("z", blockEntity.controller.getBlockPos().getZ());
        return pos;
    }

    // ==================== Inventory Access ====================

    /**
     * Returns the number of slots (boxes) available through the connected network.
     *
     * @return slot count, or 0 if not connected
     */
    @LuaFunction(mainThread = true)
    public final int size() {
        IItemHandlerModifiable handler = blockEntity.getItemHandler();
        return handler.getSlots();
    }

    /**
     * Lists all items accessible through the connected storage network.
     * Empty slots are omitted.
     *
     * @return table mapping 1-based slot numbers to item info tables
     */
    @LuaFunction(mainThread = true)
    public final Map<Integer, Map<String, Object>> list() {
        Map<Integer, Map<String, Object>> result = new HashMap<>();
        IItemHandlerModifiable handler = blockEntity.getItemHandler();
        int slots = handler.getSlots();

        for (int i = 0; i < slots; i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                result.put(i + 1, itemToMap(stack));
            }
        }
        return result;
    }

    /**
     * Returns detailed information about the item in the given slot (1-based).
     *
     * @param slot the 1-based slot number
     * @return item info table, or nil if empty
     * @throws LuaException if slot is out of range
     */
    @LuaFunction(mainThread = true)
    @Nullable
    public final Map<String, Object> getItemDetail(int slot) throws LuaException {
        IItemHandlerModifiable handler = blockEntity.getItemHandler();
        int index = slot - 1;
        if (index < 0 || index >= handler.getSlots()) {
            throw new LuaException("Slot " + slot + " out of range (1-" + handler.getSlots() + ")");
        }

        ItemStack stack = handler.getStackInSlot(index);
        if (stack.isEmpty()) return null;

        Map<String, Object> info = itemToMap(stack);
        info.put("maxStackSize", stack.getMaxStackSize());
        info.put("tags", getItemTags(stack));
        return info;
    }

    /**
     * Returns the number of boxes connected to the network (via the controller).
     *
     * @return box count, or 0 if not connected
     */
    @LuaFunction(mainThread = true)
    public final int getBoxCount() {
        if (blockEntity.controller == null || blockEntity.controller.storageNetwork == null) return 0;
        return blockEntity.controller.storageNetwork.boxes.size();
    }

    // ==================== Aggregate Queries ====================

    /**
     * Counts the total items of a specific type across the network.
     *
     * @param itemId the item registry name (e.g., "minecraft:iron_ingot")
     * @return total count
     */
    @LuaFunction(mainThread = true)
    public final int getItemCount(String itemId) {
        IItemHandlerModifiable handler = blockEntity.getItemHandler();
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem().builtInRegistryHolder().key().location().toString().equals(itemId)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Returns a summary of all unique items in the network.
     *
     * @return list of {name, displayName, count} tables
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getItemSummary() {
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, String> displayNames = new HashMap<>();
        IItemHandlerModifiable handler = blockEntity.getItemHandler();

        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                String id = stack.getItem().builtInRegistryHolder().key().location().toString();
                int[] data = counts.computeIfAbsent(id, k -> new int[]{0, 0});
                data[0] += stack.getCount();
                data[1]++;
                displayNames.putIfAbsent(id, stack.getHoverName().getString());
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", entry.getKey());
            item.put("displayName", displayNames.get(entry.getKey()));
            item.put("count", entry.getValue()[0]);
            item.put("boxes", entry.getValue()[1]);
            result.add(item);
        }
        return result;
    }

    /**
     * Searches for items matching a partial name or ID pattern.
     *
     * @param query the search string (case-insensitive)
     * @return list of matching item info tables
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> findItems(String query) throws LuaException {
        if (query == null || query.isEmpty()) {
            throw new LuaException("Search query cannot be empty");
        }

        String lowerQuery = query.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> result = new ArrayList<>();
        IItemHandlerModifiable handler = blockEntity.getItemHandler();

        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                String id = stack.getItem().builtInRegistryHolder().key().location().toString();
                String displayName = stack.getHoverName().getString();
                if (id.toLowerCase(Locale.ROOT).contains(lowerQuery) ||
                        displayName.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                    Map<String, Object> info = itemToMap(stack);
                    info.put("slot", i + 1);
                    result.add(info);
                }
            }
        }
        return result;
    }

    // ==================== Utility ====================

    private Map<String, Object> itemToMap(ItemStack stack) {
        Map<String, Object> info = new HashMap<>();
        info.put("name", stack.getItem().builtInRegistryHolder().key().location().toString());
        info.put("displayName", stack.getHoverName().getString());
        info.put("count", stack.getCount());
        info.put("maxCount", stack.getMaxStackSize());
        return info;
    }

    private List<String> getItemTags(ItemStack stack) {
        List<String> tags = new ArrayList<>();
        stack.getTags().forEach(tag -> tags.add(tag.location().toString()));
        return tags;
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        if (this == other) return true;
        if (!(other instanceof StorageInterfacePeripheral otherIface)) return false;
        return blockEntity.getBlockPos().equals(otherIface.blockEntity.getBlockPos());
    }
}
