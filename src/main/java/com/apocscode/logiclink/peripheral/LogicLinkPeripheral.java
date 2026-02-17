package com.apocscode.logiclink.peripheral;

import com.apocscode.logiclink.block.CreativeLogicMotorBlockEntity;
import com.apocscode.logiclink.block.LogicLinkBlockEntity;
import com.apocscode.logiclink.block.LogicDriveBlockEntity;
import com.apocscode.logiclink.block.LogicSensorBlockEntity;
import com.apocscode.logiclink.block.RedstoneControllerBlockEntity;
import com.apocscode.logiclink.network.HubNetwork;
import com.apocscode.logiclink.network.IHubDevice;
import com.apocscode.logiclink.network.SensorNetwork;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            // Collect all link positions on our network as search anchors
            Collection<LogisticallyLinkedBehaviour> links =
                    LogisticallyLinkedBehaviour.getAllPresent(freqId, false);

            // Search chunks near each link for FactoryPanelBlockEntity instances
            Set<Long> searchedChunks = new HashSet<>();
            int chunkRadius = 4; // 4 chunks = 64 blocks from each link

            for (LogisticallyLinkedBehaviour link : links) {
                BlockPos linkPos = link.getPos();
                int centerCX = linkPos.getX() >> 4;
                int centerCZ = linkPos.getZ() >> 4;

                for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                    for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                        int cx = centerCX + dx;
                        int cz = centerCZ + dz;
                        long chunkKey = ChunkPos.asLong(cx, cz);
                        if (!searchedChunks.add(chunkKey)) continue;
                        if (!level.hasChunk(cx, cz)) continue;

                        LevelChunk chunk = (LevelChunk) level.getChunk(cx, cz);
                        for (BlockEntity be : chunk.getBlockEntities().values()) {
                            if (!(be instanceof FactoryPanelBlockEntity panelBE)) continue;

                            for (Map.Entry<FactoryPanelBlock.PanelSlot, FactoryPanelBehaviour> entry
                                    : panelBE.panels.entrySet()) {
                                try {
                                    FactoryPanelBehaviour panel = entry.getValue();
                                    if (panel == null || !panel.isActive()) continue;

                                    // Check if panel's network matches our logistics frequency
                                    if (panel.network == null || !panel.network.equals(freqId)) continue;

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
                                    gauge.put("slot", entry.getKey().name());

                                    // Position of the gauge panel
                                    Map<String, Integer> pos = new HashMap<>();
                                    pos.put("x", panelBE.getBlockPos().getX());
                                    pos.put("y", panelBE.getBlockPos().getY());
                                    pos.put("z", panelBE.getBlockPos().getZ());
                                    gauge.put("position", pos);

                                    gauges.add(gauge);
                                } catch (Exception e) {
                                    LOGGER.debug("[getGauges] Failed to read panel at {}: {}",
                                            panelBE.getBlockPos(), e.getMessage());
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[getGauges] Failed to enumerate gauges: {}", e.getMessage(), e);
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

    // ==================== Wireless Hub Methods ====================

    /**
     * Helper: resolves a device identifier (auto-ID like "sensor_0" or label) to a BlockEntity.
     */
    private BlockEntity resolveDevice(String id) throws LuaException {
        Level level = blockEntity.getLevel();
        if (level == null) throw new LuaException("World not available");

        List<BlockEntity> devices = HubNetwork.getAllDevices();

        // Build auto-ID counters
        Map<String, Integer> typeCounters = new HashMap<>();
        for (BlockEntity be : devices) {
            if (!(be instanceof IHubDevice hub)) continue;
            String type = hub.getDeviceType();
            int idx = typeCounters.getOrDefault(type, 0);
            typeCounters.put(type, idx + 1);

            String autoId = type + "_" + idx;
            String label = hub.getHubLabel();

            if (autoId.equals(id) || (!label.isEmpty() && label.equals(id))) {
                return be;
            }
        }
        throw new LuaException("Device not found: " + id);
    }

    /**
     * Returns a list of all hub devices within range.
     * Each entry has id, type, label, and position.
     *
     * @return A list of device info tables.
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getDevices() {
        Level level = blockEntity.getLevel();
        if (level == null) return new ArrayList<>();

        List<BlockEntity> devices = HubNetwork.getAllDevices();

        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Integer> typeCounters = new HashMap<>();

        for (BlockEntity be : devices) {
            if (!(be instanceof IHubDevice hub)) continue;

            String type = hub.getDeviceType();
            int idx = typeCounters.getOrDefault(type, 0);
            typeCounters.put(type, idx + 1);

            Map<String, Object> entry = new HashMap<>();
            entry.put("id", type + "_" + idx);
            entry.put("type", type);
            entry.put("label", hub.getHubLabel());

            // Dimension
            if (be.getLevel() != null) {
                entry.put("dimension", be.getLevel().dimension().location().toString());
            }

            Map<String, Integer> pos = new HashMap<>();
            pos.put("x", hub.getDevicePos().getX());
            pos.put("y", hub.getDevicePos().getY());
            pos.put("z", hub.getDevicePos().getZ());
            entry.put("position", pos);

            // Distance from hub (only meaningful in same dimension)
            if (be.getLevel() == blockEntity.getLevel()) {
                double dist = Math.sqrt(blockEntity.getBlockPos().distSqr(hub.getDevicePos()));
                entry.put("distance", Math.round(dist * 10.0) / 10.0);
            } else {
                entry.put("distance", -1); // cross-dimension
            }

            result.add(entry);
        }
        return result;
    }

    /**
     * Gets the current hub scanning range in blocks.
     *
     * @return The range (1-256, default 64).
     */
    @LuaFunction(mainThread = true)
    public final int getHubRange() {
        return blockEntity.getHubRange();
    }

    /**
     * Sets the hub scanning range in blocks.
     *
     * @param range The range (1-256).
     * @throws LuaException if range is out of bounds.
     */
    @LuaFunction(mainThread = true)
    public final void setHubRange(int range) throws LuaException {
        if (range < 1 || range > HubNetwork.MAX_RANGE) {
            throw new LuaException("Range must be 1-" + HubNetwork.MAX_RANGE);
        }
        blockEntity.setHubRange(range);
    }

    /**
     * Sets a label on a hub device for easier identification.
     *
     * @param deviceId The device auto-ID (e.g. "sensor_0") or current label.
     * @param label    The new label (max 32 chars, empty to clear).
     * @throws LuaException if the device is not found.
     */
    @LuaFunction(mainThread = true)
    public final void setDeviceLabel(String deviceId, String label) throws LuaException {
        BlockEntity be = resolveDevice(deviceId);
        if (!(be instanceof IHubDevice hub)) throw new LuaException("Not a hub device");
        if (label.length() > 32) label = label.substring(0, 32);
        hub.setHubLabel(label);
        be.setChanged(); // Mark dirty for NBT save
    }

    /**
     * Gets the label of a hub device.
     *
     * @param deviceId The device auto-ID or label.
     * @return The label, or empty string if none set.
     * @throws LuaException if the device is not found.
     */
    @LuaFunction(mainThread = true)
    public final String getDeviceLabel(String deviceId) throws LuaException {
        BlockEntity be = resolveDevice(deviceId);
        if (!(be instanceof IHubDevice hub)) throw new LuaException("Not a hub device");
        return hub.getHubLabel();
    }

    // ---- Remote Sensor Functions ----

    /**
     * Gets sensor data from a remote sensor via the hub.
     *
     * @param deviceId The sensor's auto-ID or label.
     * @return A table with the sensor's cached data, or nil if no data.
     * @throws LuaException if the device is not found or is not a sensor.
     */
    @LuaFunction(mainThread = true)
    @Nullable
    public final Map<String, Object> getRemoteSensorData(String deviceId) throws LuaException {
        BlockEntity be = resolveDevice(deviceId);
        if (!(be instanceof LogicSensorBlockEntity sensor)) {
            throw new LuaException("Device '" + deviceId + "' is not a sensor");
        }

        Map<String, Object> result = new HashMap<>();
        Map<String, Integer> pos = new HashMap<>();
        pos.put("x", sensor.getBlockPos().getX());
        pos.put("y", sensor.getBlockPos().getY());
        pos.put("z", sensor.getBlockPos().getZ());
        result.put("position", pos);

        BlockPos targetPos = sensor.getTargetPos();
        Map<String, Integer> target = new HashMap<>();
        target.put("x", targetPos.getX());
        target.put("y", targetPos.getY());
        target.put("z", targetPos.getZ());
        result.put("targetPosition", target);

        Map<String, Object> data = sensor.getCachedData();
        if (data != null) {
            result.put("data", data);
        }

        return result;
    }

    /**
     * Gets sensor data from all remote sensors within hub range.
     *
     * @return A list of sensor data tables.
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getAllRemoteSensorData() {
        Level level = blockEntity.getLevel();
        if (level == null) return new ArrayList<>();

        List<BlockEntity> devices = HubNetwork.getAllDevices();

        List<Map<String, Object>> result = new ArrayList<>();
        int sensorIdx = 0;

        for (BlockEntity be : devices) {
            if (!(be instanceof LogicSensorBlockEntity sensor)) continue;

            Map<String, Object> entry = new HashMap<>();
            entry.put("id", "sensor_" + sensorIdx);
            entry.put("label", sensor.getHubLabel());

            // Dimension
            if (sensor.getLevel() != null) {
                entry.put("dimension", sensor.getLevel().dimension().location().toString());
            }

            Map<String, Integer> pos = new HashMap<>();
            pos.put("x", sensor.getBlockPos().getX());
            pos.put("y", sensor.getBlockPos().getY());
            pos.put("z", sensor.getBlockPos().getZ());
            entry.put("position", pos);

            BlockPos targetPos = sensor.getTargetPos();
            Map<String, Integer> target = new HashMap<>();
            target.put("x", targetPos.getX());
            target.put("y", targetPos.getY());
            target.put("z", targetPos.getZ());
            entry.put("targetPosition", target);

            Map<String, Object> data = sensor.getCachedData();
            if (data != null) {
                entry.put("data", data);
            }

            result.add(entry);
            sensorIdx++;
        }
        return result;
    }

    // ---- Remote Train Control Functions ----

    /**
     * Cycles a track switch (Steam 'n' Rails) to its next state via a sensor
     * that is targeting a track switch block.
     * <p>
     * Usage: cycleTrackSwitch("sensor_7") -- where sensor_7 targets a track switch
     *
     * @param sensorId The sensor device ID or label targeting a track switch.
     * @return true if the switch was cycled successfully.
     * @throws LuaException if the sensor doesn't target a track switch.
     */
    @LuaFunction(mainThread = true)
    public final boolean cycleTrackSwitch(String sensorId) throws LuaException {
        BlockEntity be = resolveDevice(sensorId);
        if (!(be instanceof LogicSensorBlockEntity sensor)) {
            throw new LuaException("Device '" + sensorId + "' is not a sensor");
        }

        Level level = blockEntity.getLevel();
        if (level == null) throw new LuaException("World not loaded");

        BlockPos targetPos = sensor.getTargetPos();
        if (!CreateBlockReader.isTrackSwitch(level, targetPos)) {
            throw new LuaException("Sensor target is not a track switch");
        }

        return CreateBlockReader.cycleTrackSwitch(level, targetPos);
    }

    /**
     * Gets the current state of a track switch (Steam 'n' Rails) via a sensor.
     * <p>
     * Returns a table with: switchState, automatic, locked, isNormal,
     * isReverseLeft, isReverseRight, exitCount, analogOutput
     *
     * @param sensorId The sensor device ID or label targeting a track switch.
     * @return A table of switch state data.
     * @throws LuaException if the sensor doesn't target a track switch.
     */
    @LuaFunction(mainThread = true)
    @Nullable
    public final Map<String, Object> getTrackSwitchState(String sensorId) throws LuaException {
        BlockEntity be = resolveDevice(sensorId);
        if (!(be instanceof LogicSensorBlockEntity sensor)) {
            throw new LuaException("Device '" + sensorId + "' is not a sensor");
        }

        Level level = blockEntity.getLevel();
        if (level == null) throw new LuaException("World not loaded");

        BlockPos targetPos = sensor.getTargetPos();
        Map<String, Object> data = CreateBlockReader.readBlockData(level, targetPos);
        if (data == null || !data.containsKey("isTrackSwitch")) {
            throw new LuaException("Sensor target is not a track switch");
        }
        return data;
    }

    /**
     * Gets detailed train data from a station, signal, or observer via a sensor.
     * <p>
     * For stations: stationName, trainPresent, trainImminent, presentTrainName,
     * presentTrainSpeed, hasSchedule, assembling
     * For signals: signalState (red/yellow/green/invalid), powered, overlay
     * For observers: trainPassing, powered, filter, filterName
     *
     * @param sensorId The sensor device ID or label targeting a train block.
     * @return A table of train block data.
     * @throws LuaException if the sensor is invalid.
     */
    @LuaFunction(mainThread = true)
    @Nullable
    public final Map<String, Object> getTrainBlockData(String sensorId) throws LuaException {
        BlockEntity be = resolveDevice(sensorId);
        if (!(be instanceof LogicSensorBlockEntity sensor)) {
            throw new LuaException("Device '" + sensorId + "' is not a sensor");
        }

        Level level = blockEntity.getLevel();
        if (level == null) throw new LuaException("World not loaded");

        BlockPos targetPos = sensor.getTargetPos();
        Map<String, Object> data = CreateBlockReader.readBlockData(level, targetPos);
        if (data == null) {
            throw new LuaException("No block entity at sensor target");
        }
        return data;
    }

    // ---- Remote Motor Functions ----

    /**
     * Enables or disables a remote motor.
     *
     * @param deviceId The motor's auto-ID or label.
     * @param enabled  true to enable, false to disable.
     * @throws LuaException if the device is not found or is not a motor.
     */
    @LuaFunction(mainThread = true)
    public final void enableRemote(String deviceId, boolean enabled) throws LuaException {
        BlockEntity be = resolveDevice(deviceId);
        if (be instanceof CreativeLogicMotorBlockEntity motor) {
            motor.setEnabled(enabled);
        } else if (be instanceof LogicDriveBlockEntity motor) {
            motor.setMotorEnabled(enabled);
        } else {
            throw new LuaException("Device '" + deviceId + "' is not a motor");
        }
    }

    /**
     * Sets the speed of a remote creative motor.
     *
     * @param deviceId The creative motor's auto-ID or label.
     * @param speed    RPM value (-256 to 256).
     * @throws LuaException if the device is not a creative motor.
     */
    @LuaFunction(mainThread = true)
    public final void setRemoteSpeed(String deviceId, int speed) throws LuaException {
        BlockEntity be = resolveDevice(deviceId);
        if (be instanceof CreativeLogicMotorBlockEntity motor) {
            motor.setMotorSpeed(speed);
        } else {
            throw new LuaException("Device '" + deviceId + "' is not a creative motor (use setRemoteModifier for logic motors)");
        }
    }

    /**
     * Sets the speed modifier of a remote logic motor.
     *
     * @param deviceId The logic motor's auto-ID or label.
     * @param modifier Multiplier (-16.0 to 16.0).
     * @throws LuaException if the device is not a logic motor.
     */
    @LuaFunction(mainThread = true)
    public final void setRemoteModifier(String deviceId, double modifier) throws LuaException {
        BlockEntity be = resolveDevice(deviceId);
        if (be instanceof LogicDriveBlockEntity motor) {
            motor.setSpeedModifier((float) modifier);
        } else {
            throw new LuaException("Device '" + deviceId + "' is not a logic drive");
        }
    }

    /**
     * Sets the reversed state of a remote logic motor.
     *
     * @param deviceId The logic motor's auto-ID or label.
     * @param reversed true to reverse rotation direction.
     * @throws LuaException if the device is not a logic motor.
     */
    @LuaFunction(mainThread = true)
    public final void setRemoteReversed(String deviceId, boolean reversed) throws LuaException {
        BlockEntity be = resolveDevice(deviceId);
        if (be instanceof LogicDriveBlockEntity motor) {
            motor.setReversed(reversed);
        } else {
            throw new LuaException("Device '" + deviceId + "' is not a logic drive");
        }
    }

    /**
     * Gets comprehensive status info from a remote motor.
     *
     * @param deviceId The motor's auto-ID or label.
     * @return A table with motor status (type, enabled, speed, stress, sequence info).
     * @throws LuaException if the device is not a motor.
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> getRemoteMotorInfo(String deviceId) throws LuaException {
        BlockEntity be = resolveDevice(deviceId);
        Map<String, Object> info = new HashMap<>();

        if (be instanceof CreativeLogicMotorBlockEntity motor) {
            info.put("type", "creative_motor");
            info.put("enabled", motor.isEnabled());
            info.put("targetSpeed", motor.getMotorSpeed());
            info.put("actualSpeed", (double) motor.getActualSpeed());
            info.put("stressCapacity", (double) motor.getStressCapacityValue());
            info.put("stressUsage", (double) motor.getStressUsageValue());
            info.put("sequenceRunning", motor.isSequenceRunning());
            info.put("sequenceSize", motor.getSequenceSize());
        } else if (be instanceof LogicDriveBlockEntity motor) {
            info.put("type", "drive");
            info.put("enabled", motor.isMotorEnabled());
            info.put("modifier", (double) motor.getSpeedModifier());
            info.put("reversed", motor.isReversed());
            info.put("inputSpeed", (double) motor.getInputSpeed());
            info.put("outputSpeed", (double) motor.getOutputSpeed());
            info.put("stressCapacity", (double) motor.getStressCapacityValue());
            info.put("stressUsage", (double) motor.getStressUsageValue());
            info.put("sequenceRunning", motor.isSequenceRunning());
            info.put("sequenceSize", motor.getSequenceSize());
        } else {
            throw new LuaException("Device '" + deviceId + "' is not a motor");
        }

        return info;
    }

    // ---- Remote Redstone Functions ----

    /**
     * Sets a redstone output channel on a remote redstone controller.
     *
     * @param deviceId The controller's auto-ID or label.
     * @param item1    First frequency item (e.g. "minecraft:red_dye").
     * @param item2    Second frequency item.
     * @param power    Signal strength (0-15).
     * @throws LuaException if the device is not a redstone controller.
     */
    @LuaFunction(mainThread = true)
    public final void setRemoteRedstoneOutput(String deviceId, String item1, String item2, int power)
            throws LuaException {
        BlockEntity be = resolveDevice(deviceId);
        if (!(be instanceof RedstoneControllerBlockEntity controller)) {
            throw new LuaException("Device '" + deviceId + "' is not a redstone controller");
        }
        controller.setOutput(item1, item2, power);
    }

    /**
     * Gets a redstone input value from a channel on a remote redstone controller.
     *
     * @param deviceId The controller's auto-ID or label.
     * @param item1    First frequency item.
     * @param item2    Second frequency item.
     * @return The signal strength (0-15).
     * @throws LuaException if the device is not a redstone controller.
     */
    @LuaFunction(mainThread = true)
    public final int getRemoteRedstoneInput(String deviceId, String item1, String item2)
            throws LuaException {
        BlockEntity be = resolveDevice(deviceId);
        if (!(be instanceof RedstoneControllerBlockEntity controller)) {
            throw new LuaException("Device '" + deviceId + "' is not a redstone controller");
        }
        return controller.getInput(item1, item2);
    }

    /**
     * Gets all redstone channels on a remote redstone controller.
     *
     * @param deviceId The controller's auto-ID or label.
     * @return A list of channel info tables (item1, item2, mode, power).
     * @throws LuaException if the device is not a redstone controller.
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getRemoteRedstoneChannels(String deviceId) throws LuaException {
        BlockEntity be = resolveDevice(deviceId);
        if (!(be instanceof RedstoneControllerBlockEntity controller)) {
            throw new LuaException("Device '" + deviceId + "' is not a redstone controller");
        }
        return controller.getChannelList();
    }

    /**
     * Sets all transmit channels on a remote redstone controller to the same power level.
     *
     * @param deviceId The controller's auto-ID or label.
     * @param power    Signal strength (0-15).
     * @throws LuaException if the device is not a redstone controller.
     */
    @LuaFunction(mainThread = true)
    public final void setAllRemoteRedstoneOutputs(String deviceId, int power) throws LuaException {
        BlockEntity be = resolveDevice(deviceId);
        if (!(be instanceof RedstoneControllerBlockEntity controller)) {
            throw new LuaException("Device '" + deviceId + "' is not a redstone controller");
        }
        controller.setAllOutputs(power);
    }

    // ==================== Peripheral Equality ====================

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        if (this == other) return true;
        if (!(other instanceof LogicLinkPeripheral otherPeripheral)) return false;
        return blockEntity.getBlockPos().equals(otherPeripheral.blockEntity.getBlockPos());
    }
}
