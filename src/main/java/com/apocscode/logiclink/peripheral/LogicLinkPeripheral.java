package com.apocscode.logiclink.peripheral;

import com.apocscode.logiclink.block.LogicLinkBlockEntity;
import com.apocscode.logiclink.block.LogicSensorBlockEntity;
import com.apocscode.logiclink.network.SensorNetwork;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSupportBehaviour;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ItemLike;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CC:Tweaked peripheral for the Logic Link block.
 * <p>
 * Exposes Create's logistics network inventory data as Lua functions.
 * The Logic Link must be tuned to a logistics network (right-click a
 * Stock Link before placing) for inventory queries to return data.
 * </p>
 *
 * <h3>Example Lua usage:</h3>
 * <pre>{@code
 * local link = peripheral.wrap("logiclink")
 *
 * -- Check if linked to a network
 * if link.isLinked() then
 *     -- Get all items on the network
 *     local items = link.list()
 *     for _, item in ipairs(items) do
 *         print(item.name .. " x" .. item.count)
 *     end
 *
 *     -- Search for a specific item
 *     local count = link.getItemCount("minecraft:iron_ingot")
 *     print("Iron ingots: " .. count)
 * end
 * }</pre>
 */
public class LogicLinkPeripheral implements IPeripheral {

    private static final Logger LOGGER = LoggerFactory.getLogger("LogicLink");
    private final LogicLinkBlockEntity blockEntity;

    public LogicLinkPeripheral(LogicLinkBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public String getType() {
        return "logiclink";
    }

    // ==================== Lua API Methods ====================

    /**
     * Returns whether this Logic Link is connected to a Create logistics network.
     *
     * @return true if linked to a network, false otherwise.
     */
    @LuaFunction(mainThread = true)
    public final boolean isLinked() {
        return blockEntity.isLinked();
    }

    /**
     * Returns the position of this Logic Link block.
     *
     * @return A table with x, y, z coordinates.
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
     * Returns the network frequency UUID as a string, or nil if not linked.
     *
     * @return The network UUID string, or null.
     */
    @LuaFunction(mainThread = true)
    @Nullable
    public final String getNetworkID() {
        if (blockEntity.getNetworkFrequency() == null) return null;
        return blockEntity.getNetworkFrequency().toString();
    }

    /**
     * Returns a list of all items available on the logistics network.
     * Each entry is a table with 'name' (registry name) and 'count' fields.
     *
     * @return A list of item tables, or an empty list if not linked.
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();

        InventorySummary summary = blockEntity.getNetworkSummary();
        if (summary == null) return result;

        for (BigItemStack bis : summary.getStacks()) {
            if (bis.stack.isEmpty()) continue;
            Map<String, Object> entry = new HashMap<>();
            entry.put("name", bis.stack.getItem().builtInRegistryHolder().key().location().toString());
            entry.put("count", bis.count);
            entry.put("displayName", bis.stack.getHoverName().getString());
            result.add(entry);
        }

        return result;
    }

    /**
     * Returns the count of a specific item on the logistics network.
     *
     * @param itemName The registry name (e.g. "minecraft:iron_ingot").
     * @return The total count of that item, or 0 if not found/not linked.
     */
    @LuaFunction(mainThread = true)
    public final int getItemCount(String itemName) {
        InventorySummary summary = blockEntity.getNetworkSummary();
        if (summary == null) return 0;

        for (BigItemStack bis : summary.getStacks()) {
            if (bis.stack.isEmpty()) continue;
            String registryName = bis.stack.getItem().builtInRegistryHolder().key().location().toString();
            if (registryName.equals(itemName)) {
                return bis.count;
            }
        }
        return 0;
    }

    /**
     * Returns the total number of distinct item types on the network.
     *
     * @return The number of unique item types.
     */
    @LuaFunction(mainThread = true)
    public final int getItemTypeCount() {
        InventorySummary summary = blockEntity.getNetworkSummary();
        if (summary == null) return 0;
        return summary.getStacks().size();
    }

    /**
     * Returns the total number of items (sum of all stacks) on the network.
     *
     * @return The total item count.
     */
    @LuaFunction(mainThread = true)
    public final int getTotalItemCount() {
        InventorySummary summary = blockEntity.getNetworkSummary();
        if (summary == null) return 0;

        return summary.getTotalCount();
    }

    /**
     * Forces a refresh of the cached inventory data from the network.
     * Normally the cache refreshes every 2 seconds automatically.
     */
    @LuaFunction(mainThread = true)
    public final void refresh() {
        blockEntity.refreshNetworkSummary();
    }

    /**
     * Returns network status information.
     *
     * @return A table with network info (linked, networkId, position, itemTypes, totalItems).
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> getNetworkInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("linked", blockEntity.isLinked());
        info.put("networkId", blockEntity.getNetworkFrequency() != null
                ? blockEntity.getNetworkFrequency().toString() : "none");
        info.put("position", getPosition());
        info.put("itemTypes", getItemTypeCount());
        info.put("totalItems", getTotalItemCount());
        return info;
    }

    // ==================== Network Topology Methods ====================

    /**
     * Returns all Factory Gauge panels on the logistics network.
     * Each gauge entry includes the item being monitored, target amount,
     * current stock level, restock status, and delivery address.
     *
     * @return A list of gauge data tables.
     * @throws LuaException if the block is not linked.
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getGauges() throws LuaException {
        if (!blockEntity.isLinked()) {
            throw new LuaException("Logic Link is not connected to a network");
        }

        UUID freqId = blockEntity.getNetworkFrequency();
        Level level = blockEntity.getLevel();
        if (level == null) return new ArrayList<>();

        List<Map<String, Object>> gauges = new ArrayList<>();

        try {
            Collection<LogisticallyLinkedBehaviour> links =
                    LogisticallyLinkedBehaviour.getAllPresent(freqId, false);

            for (LogisticallyLinkedBehaviour link : links) {
                BlockPos linkPos = link.getPos();

                // Check if this link has Factory Panel support
                FactoryPanelSupportBehaviour support =
                        BlockEntityBehaviour.get(level, linkPos, FactoryPanelSupportBehaviour.TYPE);
                if (support == null) continue;

                for (FactoryPanelPosition panelPos : support.getLinkedPanels()) {
                    try {
                        FactoryPanelBehaviour panel = FactoryPanelBehaviour.at(level, panelPos);
                        if (panel == null || !panel.isActive()) continue;

                        Map<String, Object> gauge = new HashMap<>();
                        ItemStack filter = panel.getFilter();

                        // Item info
                        if (!filter.isEmpty()) {
                            gauge.put("item", filter.getItem().builtInRegistryHolder()
                                    .key().location().toString());
                            gauge.put("itemDisplayName", filter.getHoverName().getString());
                        } else {
                            gauge.put("item", "none");
                            gauge.put("itemDisplayName", "");
                        }

                        // Target and stock levels
                        gauge.put("targetAmount", panel.count);
                        gauge.put("currentStock", panel.getLevelInStorage());
                        gauge.put("promised", panel.getPromised());

                        // Status flags
                        gauge.put("satisfied", panel.satisfied);
                        gauge.put("promisedSatisfied", panel.promisedSatisfied);
                        gauge.put("waitingForNetwork", panel.waitingForNetwork);
                        gauge.put("redstonePowered", panel.redstonePowered);
                        gauge.put("missingAddress", panel.isMissingAddress());

                        // Configuration
                        gauge.put("address", panel.recipeAddress);
                        gauge.put("slot", panelPos.slot().name());

                        // Position of the gauge panel
                        Map<String, Integer> pos = new HashMap<>();
                        pos.put("x", panelPos.pos().getX());
                        pos.put("y", panelPos.pos().getY());
                        pos.put("z", panelPos.pos().getZ());
                        gauge.put("position", pos);

                        gauges.add(gauge);
                    } catch (Exception e) {
                        LOGGER.debug("Failed to read gauge at {}: {}", panelPos, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to enumerate gauges: {}", e.getMessage());
        }

        return gauges;
    }

    /**
     * Returns all Stock Links on the logistics network with their type and position.
     * Useful for understanding network topology.
     *
     * @return A list of link data tables.
     * @throws LuaException if the block is not linked.
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getLinks() throws LuaException {
        if (!blockEntity.isLinked()) {
            throw new LuaException("Logic Link is not connected to a network");
        }

        UUID freqId = blockEntity.getNetworkFrequency();
        List<Map<String, Object>> links = new ArrayList<>();

        try {
            Collection<LogisticallyLinkedBehaviour> allLinks =
                    LogisticallyLinkedBehaviour.getAllPresent(freqId, false);

            for (LogisticallyLinkedBehaviour link : allLinks) {
                Map<String, Object> linkData = new HashMap<>();
                BlockPos pos = link.getPos();

                linkData.put("x", pos.getX());
                linkData.put("y", pos.getY());
                linkData.put("z", pos.getZ());
                linkData.put("redstonePower", link.redstonePower);

                // Determine link type
                String type = "unknown";
                if (link.blockEntity instanceof PackagerLinkBlockEntity) {
                    type = "packager_link";
                } else {
                    // Check block registry name for type hints
                    String blockName = BuiltInRegistries.BLOCK.getKey(
                            link.blockEntity.getBlockState().getBlock()).toString();
                    if (blockName.contains("redstone_requester")) type = "redstone_requester";
                    else if (blockName.contains("stock_ticker")) type = "stock_ticker";
                    else type = blockName;
                }
                linkData.put("type", type);

                links.add(linkData);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to enumerate links: {}", e.getMessage());
        }

        return links;
    }

    /**
     * Returns all Logic Sensors on the same logistics network.
     * Each sensor reports data from the Create machine it's attached to.
     *
     * @return A list of sensor data tables.
     * @throws LuaException if the block is not linked.
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getSensors() throws LuaException {
        if (!blockEntity.isLinked()) {
            throw new LuaException("Logic Link is not connected to a network");
        }

        UUID freqId = blockEntity.getNetworkFrequency();
        List<Map<String, Object>> sensors = new ArrayList<>();

        for (LogicSensorBlockEntity sensor : SensorNetwork.getSensors(freqId)) {
            Map<String, Object> sensorEntry = new HashMap<>();

            // Sensor position
            Map<String, Integer> pos = new HashMap<>();
            pos.put("x", sensor.getBlockPos().getX());
            pos.put("y", sensor.getBlockPos().getY());
            pos.put("z", sensor.getBlockPos().getZ());
            sensorEntry.put("position", pos);

            // Target position
            BlockPos targetPos = sensor.getTargetPos();
            Map<String, Integer> target = new HashMap<>();
            target.put("x", targetPos.getX());
            target.put("y", targetPos.getY());
            target.put("z", targetPos.getZ());
            sensorEntry.put("targetPosition", target);

            // Cached sensor data
            Map<String, Object> data = sensor.getCachedData();
            if (data != null) {
                sensorEntry.put("data", data);
            }

            sensors.add(sensorEntry);
        }

        return sensors;
    }

    // ==================== Item Request Methods ====================

    /**
     * Requests items from the logistics network to be sent to an address.
     * This works like a Stock Ticker or Redstone Requester — items are
     * packaged and delivered via the network (e.g. through Frogports).
     *
     * @param itemName The registry name of the item (e.g. "minecraft:iron_ingot").
     * @param count    The number of items to request.
     * @param address  The delivery address (must match a Packager's address).
     * @return true if the request was accepted by the network.
     * @throws LuaException if the block is not linked or the item is invalid.
     */
    @LuaFunction(mainThread = true)
    public final boolean requestItem(String itemName, int count, String address) throws LuaException {
        if (!blockEntity.isLinked()) {
            throw new LuaException("Logic Link is not connected to a network");
        }
        if (count <= 0) {
            throw new LuaException("Count must be greater than 0");
        }
        if (address == null || address.isEmpty()) {
            throw new LuaException("Address cannot be empty");
        }

        // Resolve item using BuiltInRegistries - same approach as Create's RedstoneRequesterPeripheral
        ResourceLocation resourceLocation = ResourceLocation.tryParse(itemName);
        if (resourceLocation == null) {
            throw new LuaException("Invalid item name: " + itemName);
        }
        ItemLike item = BuiltInRegistries.ITEM.get(resourceLocation);
        ItemStack itemStack = new ItemStack(item);
        if (itemStack.isEmpty()) {
            throw new LuaException("Unknown item: " + itemName);
        }

        // Build order using ArrayList (mutable) - matching Create's StockTicker pattern
        ArrayList<BigItemStack> orderStacks = new ArrayList<>();
        orderStacks.add(new BigItemStack(itemStack, count));
        PackageOrder order = new PackageOrder(orderStacks);

        UUID freqId = blockEntity.getNetworkFrequency();
        PackageOrderWithCrafts orderWithCrafts = PackageOrderWithCrafts.simple(order.stacks());
        return safeRequest(freqId, orderWithCrafts, address);
    }

    /**
     * Requests multiple item types from the logistics network in a single order.
     * Each entry in the items table should have 'name' (string) and 'count' (number).
     *
     * <pre>{@code
     * link.requestItems({
     *     { name = "minecraft:iron_ingot", count = 64 },
     *     { name = "minecraft:gold_ingot", count = 32 },
     * }, "Factory")
     * }</pre>
     *
     * @param items   A list of tables, each with 'name' and 'count' fields.
     * @param address The delivery address.
     * @return true if the request was accepted by the network.
     * @throws LuaException if the block is not linked or input is invalid.
     */
    @LuaFunction(mainThread = true)
    public final boolean requestItems(Map<?, ?> items, String address) throws LuaException {
        if (!blockEntity.isLinked()) {
            throw new LuaException("Logic Link is not connected to a network");
        }
        if (address == null || address.isEmpty()) {
            throw new LuaException("Address cannot be empty");
        }
        if (items == null || items.isEmpty()) {
            throw new LuaException("Items list cannot be empty");
        }

        ArrayList<BigItemStack> orderStacks = new ArrayList<>();

        for (Map.Entry<?, ?> entry : items.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> itemTable)) {
                throw new LuaException("Each item entry must be a table with 'name' and 'count'");
            }

            Object nameObj = itemTable.get("name");
            Object countObj = itemTable.get("count");

            if (!(nameObj instanceof String itemTableName)) {
                throw new LuaException("Item entry missing 'name' field");
            }
            if (countObj == null) {
                throw new LuaException("Item entry missing 'count' field");
            }

            int itemCount;
            if (countObj instanceof Number num) {
                itemCount = num.intValue();
            } else {
                throw new LuaException("Item 'count' must be a number");
            }

            if (itemCount <= 0) {
                throw new LuaException("Count must be greater than 0 for " + itemTableName);
            }

            // Resolve item using BuiltInRegistries - same as Create's approach
            ResourceLocation resourceLocation = ResourceLocation.tryParse(itemTableName);
            if (resourceLocation == null) {
                throw new LuaException("Invalid item name: " + itemTableName);
            }
            ItemLike item = BuiltInRegistries.ITEM.get(resourceLocation);
            ItemStack itemStack = new ItemStack(item);
            if (itemStack.isEmpty()) {
                throw new LuaException("Unknown item: " + itemTableName);
            }

            orderStacks.add(new BigItemStack(itemStack, itemCount));
        }

        UUID freqId = blockEntity.getNetworkFrequency();
        PackageOrder order = new PackageOrder(orderStacks);
        PackageOrderWithCrafts orderWithCrafts = PackageOrderWithCrafts.simple(order.stacks());
        return safeRequest(freqId, orderWithCrafts, address);
    }

    // ==================== Safe Request Helper ====================

    // Cached reflection references for Create Factory Logistics / Abstractions compatibility
    private static boolean reflectionInitialized = false;
    private static Method genericBroadcastMethod = null;
    private static Method genericOrderOfMethod = null;

    /**
     * Initializes reflection handles for GenericLogisticsManager from
     * Create Factory Abstractions. This mod (when present alongside
     * Create Factory Logistics) provides the working request pathway.
     *
     * Factory Logistics @Overwrites PackagerBlockEntity.attemptToSend()
     * to throw UnsupportedOperationException("Not implemented"), replacing
     * the entire send path with attemptToSendGeneric(). The original
     * LogisticsManager.broadcastPackageRequest() still calls the broken
     * attemptToSend(), so we must route through GenericLogisticsManager
     * which uses the working attemptToSendGeneric() path.
     */
    private static void initReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;

        try {
            // GenericLogisticsManager.broadcastPackageRequest(UUID, RequestType, GenericOrder, IdentifiedInventory, String)
            Class<?> glmClass = Class.forName(
                    "ru.zznty.create_factory_abstractions.generic.support.GenericLogisticsManager");
            Class<?> genericOrderClass = Class.forName(
                    "ru.zznty.create_factory_abstractions.generic.support.GenericOrder");

            // GenericOrder.of(PackageOrderWithCrafts) - converts Create's order to generic
            genericOrderOfMethod = genericOrderClass.getMethod("of", PackageOrderWithCrafts.class);

            // broadcastPackageRequest(UUID, RequestType, GenericOrder, IdentifiedInventory, String)
            Class<?> identifiedInvClass = Class.forName(
                    "com.simibubi.create.content.logistics.packager.IdentifiedInventory");
            genericBroadcastMethod = glmClass.getMethod("broadcastPackageRequest",
                    UUID.class, RequestType.class, genericOrderClass, identifiedInvClass, String.class);

            LOGGER.info("safeRequest: Factory Abstractions detected — using GenericLogisticsManager path");
        } catch (ClassNotFoundException e) {
            LOGGER.info("safeRequest: Factory Abstractions not present — will use standard path");
        } catch (Exception e) {
            LOGGER.warn("safeRequest: Failed to init Factory Abstractions reflection: {}", e.getMessage());
        }
    }

    /**
     * Sends a package request, automatically routing through the correct pathway
     * depending on which mods are installed.
     *
     * <p>Create Factory Logistics replaces PackagerBlockEntity.attemptToSend() with
     * a stub that throws "Not implemented", redirecting all packaging through its
     * generic abstraction layer (GenericLogisticsManager → attemptToSendGeneric).
     * We detect this and call GenericLogisticsManager via reflection.</p>
     *
     * <p>If Factory Logistics is not installed, we fall back to the standard
     * LogisticsManager.broadcastPackageRequest() which works fine on vanilla Create.</p>
     */
    private boolean safeRequest(UUID freqId, PackageOrderWithCrafts order, String address) throws LuaException {
        if (order.isEmpty()) {
            throw new LuaException("Order is empty");
        }

        initReflection();

        LOGGER.info("safeRequest: freqId={}, address='{}', stacks={}",
                freqId, address, order.stacks().size());

        // Strategy 1: Use GenericLogisticsManager if Factory Abstractions is present
        // This is the path that Factory Logistics' rewritten RedstoneRequester uses
        if (genericBroadcastMethod != null && genericOrderOfMethod != null) {
            try {
                LOGGER.info("safeRequest: Using GenericLogisticsManager (Factory Abstractions path)");

                // Convert PackageOrderWithCrafts → GenericOrder
                Object genericOrder = genericOrderOfMethod.invoke(null, order);

                // Call GenericLogisticsManager.broadcastPackageRequest(freqId, type, order, null, address)
                Object result = genericBroadcastMethod.invoke(null,
                        freqId, RequestType.PLAYER, genericOrder, null, address);

                boolean success = (Boolean) result;
                LOGGER.info("safeRequest: GenericLogisticsManager returned {}", success);

                if (!success) {
                    throw new LuaException("Request returned false — items may not be available or packagers are busy");
                }
                return true;
            } catch (LuaException e) {
                throw e;
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause();
                LOGGER.error("safeRequest: GenericLogisticsManager call failed:", cause);
                throw new LuaException("Request failed: " + (cause != null ? cause.getMessage() : e.getMessage()));
            } catch (Exception e) {
                LOGGER.error("safeRequest: GenericLogisticsManager reflection failed:", e);
                throw new LuaException("Request failed: " + e.getMessage());
            }
        }

        // Strategy 2: Standard LogisticsManager path (works on vanilla Create without Factory Logistics)
        LOGGER.info("safeRequest: Using standard LogisticsManager path");
        try {
            boolean result = LogisticsManager.broadcastPackageRequest(
                    freqId, RequestType.PLAYER, order, null, address);
            LOGGER.info("safeRequest: LogisticsManager returned {}", result);
            if (!result) {
                throw new LuaException("Request returned false — items may not be available or packagers are busy");
            }
            return true;
        } catch (LuaException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("safeRequest: LogisticsManager call failed:", e);
            throw new LuaException("Request failed: " + e.getMessage());
        }
    }

    // ==================== Peripheral Equality ====================

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        if (this == other) return true;
        if (!(other instanceof LogicLinkPeripheral otherPeripheral)) return false;
        return blockEntity.getBlockPos().equals(otherPeripheral.blockEntity.getBlockPos());
    }
}
