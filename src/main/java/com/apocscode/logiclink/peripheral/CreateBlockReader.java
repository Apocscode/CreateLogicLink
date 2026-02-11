package com.apocscode.logiclink.peripheral;

import com.apocscode.logiclink.LogicLink;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class that reads data from Create mod block entities and NeoForge
 * capability-based blocks. Returns data as Lua-compatible Maps.
 * <p>
 * Supports:
 * <ul>
 *   <li>Kinetic blocks (speed, stress, capacity)</li>
 *   <li>Item inventories via IItemHandler capability</li>
 *   <li>Fluid tanks via IFluidHandler capability</li>
 *   <li>Create-specific block entities (Basin, Deployer, Blaze Burner, etc.)</li>
 * </ul>
 */
public class CreateBlockReader {

    // Cached reflection references for KineticBlockEntity
    private static boolean kineticReflectionInit = false;
    private static Class<?> kineticBEClass = null;
    private static Method getSpeedMethod = null;
    private static Method getTheoreticalSpeedMethod = null;
    private static Method isOverStressedMethod = null;
    private static Field capacityField = null;
    private static Field stressField = null;
    private static Method hasNetworkMethod = null;

    // Cached reflection for Blaze Burner
    private static boolean blazeReflectionInit = false;
    private static Class<?> blazeBurnerBEClass = null;

    // Cached reflection for Create Storage
    private static boolean storageReflectionInit = false;
    private static Class<?> storageControllerBEClass = null;
    private static Class<?> storageInterfaceBEClass = null;
    private static Class<?> storageNetworkClass = null;
    private static Class<?> storageNetworkItemClass = null;
    private static Class<?> simpleStorageBoxBEClass = null;
    private static Field controllerStorageNetworkField = null;
    private static Field interfaceControllerField = null;
    private static Field networkBoxesField = null;
    private static Field networkItemBoxEntityField = null;
    private static Field networkItemBlockPosField = null;
    private static Method controllerGetItemHandlerMethod = null;
    private static Method boxGetStoredAmountMethod = null;
    private static Method boxGetMaxItemCapacityMethod = null;
    private static Method boxGetCapacityUpgradesMethod = null;
    private static Method boxHasVoidUpgradeMethod = null;
    private static Method boxGetFilterItemMethod = null;
    private static Method boxGetItemHandlerMethod = null;

    // Cached reflection for Create train blocks
    private static boolean trainReflectionInit = false;
    // Station
    private static Class<?> stationBEClass = null;
    private static Class<?> globalStationClass = null;
    private static Method stationGetStationMethod = null;
    private static Method stationIsAssemblingMethod = null;
    private static Method stationGetPresentTrainMethod = null;
    private static Method stationGetImminentTrainMethod = null;
    // Signal
    private static Class<?> signalBEClass = null;
    private static Method signalGetStateMethod = null;
    private static Method signalIsPoweredMethod = null;
    private static Method signalGetOverlayMethod = null;
    // Observer
    private static Class<?> observerBEClass = null;
    private static Method observerGetFilterMethod = null;
    private static Field observerPassingTrainField = null;
    private static Method observerIsBlockPoweredMethod = null;
    // Train entity
    private static Class<?> trainClass = null;
    private static Field trainNameField = null;
    private static Field trainScheduleField = null;
    private static Field trainIdField = null;
    private static Field trainSpeedField = null;

    // Cached reflection for Steam 'n' Rails train blocks
    private static boolean railwaysReflectionInit = false;
    // Track Switch
    private static Class<?> trackSwitchBEClass = null;
    private static Method switchGetStateMethod = null;
    private static Method switchIsAutomaticMethod = null;
    private static Method switchIsLockedMethod = null;
    private static Method switchIsNormalMethod = null;
    private static Method switchIsReverseLeftMethod = null;
    private static Method switchIsReverseRightMethod = null;
    private static Method switchCycleStateMethod = null;
    private static Field switchExitCountField = null;
    private static Method switchGetAnalogOutputMethod = null;
    // Track Coupler
    private static Class<?> trackCouplerBEClass = null;
    private static Method couplerGetOperationModeMethod = null;
    private static Method couplerAreEdgePointsOkMethod = null;
    private static Method couplerGetEdgeSpacingMethod = null;
    private static Method couplerGetAnalogOutputMethod = null;
    private static Method couplerGetReportedPowerMethod = null;
    private static Method couplerGetAllowedOpModeMethod = null;
    // Semaphore
    private static Class<?> semaphoreBEClass = null;

    /**
     * Cycle a Steam 'n' Rails track switch to its next state.
     * @return true if the switch was cycled, false if not a switch or failed.
     */
    public static boolean cycleTrackSwitch(Level level, BlockPos pos) {
        initRailwaysReflection();
        if (trackSwitchBEClass == null || switchCycleStateMethod == null) return false;

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || !trackSwitchBEClass.isInstance(be)) return false;

        try {
            return (Boolean) switchCycleStateMethod.invoke(be);
        } catch (Exception e) {
            LogicLink.LOGGER.debug("Failed to cycle track switch: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if a block at the given position is a Steam 'n' Rails track switch.
     */
    public static boolean isTrackSwitch(Level level, BlockPos pos) {
        initRailwaysReflection();
        if (trackSwitchBEClass == null) return false;
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && trackSwitchBEClass.isInstance(be);
    }

    /**
     * Read all available data from a block at the given position.
     *
     * @return A map of data, or null if no block entity exists there.
     */
    @Nullable
    public static Map<String, Object> readBlockData(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;

        Map<String, Object> data = new HashMap<>();

        // Block identification
        String blockId = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString();
        data.put("block", blockId);
        data.put("blockName", be.getBlockState().getBlock().getName().getString());

        // Position
        Map<String, Integer> position = new HashMap<>();
        position.put("x", pos.getX());
        position.put("y", pos.getY());
        position.put("z", pos.getZ());
        data.put("position", position);

        // Kinetic data (Create mod)
        readKineticData(be, data);

        // Blaze Burner data
        readBlazeData(be, data);

        // Create train blocks (Station, Signal, Observer)
        readTrainData(be, data);

        // Steam 'n' Rails blocks (Track Switch, Track Coupler, Semaphore)
        readRailwaysData(be, data);

        // Create Storage data (controller, interface, box)
        readStorageData(be, level, data);

        // Item inventory via NeoForge capability
        readItemData(level, pos, data);

        // Fluid storage via NeoForge capability
        readFluidData(level, pos, data);

        return data;
    }

    /**
     * Read kinetic data from any KineticBlockEntity.
     * Uses reflection to support the Create mod without hard compile dependency on
     * internal protected fields.
     */
    private static void readKineticData(BlockEntity be, Map<String, Object> data) {
        initKineticReflection();
        if (kineticBEClass == null || !kineticBEClass.isInstance(be)) return;

        try {
            data.put("isKinetic", true);

            if (getSpeedMethod != null) {
                data.put("speed", ((Number) getSpeedMethod.invoke(be)).floatValue());
            }
            if (getTheoreticalSpeedMethod != null) {
                data.put("theoreticalSpeed", ((Number) getTheoreticalSpeedMethod.invoke(be)).floatValue());
            }
            if (isOverStressedMethod != null) {
                data.put("overStressed", (Boolean) isOverStressedMethod.invoke(be));
            }
            if (hasNetworkMethod != null) {
                data.put("hasKineticNetwork", (Boolean) hasNetworkMethod.invoke(be));
            }

            // Protected fields via reflection
            if (capacityField != null) {
                data.put("stressCapacity", capacityField.getFloat(be));
            }
            if (stressField != null) {
                data.put("networkStress", stressField.getFloat(be));
            }
        } catch (Exception e) {
            LogicLink.LOGGER.debug("Failed to read kinetic data: {}", e.getMessage());
        }
    }

    /**
     * Read blaze burner heat level.
     */
    private static void readBlazeData(BlockEntity be, Map<String, Object> data) {
        initBlazeReflection();
        if (blazeBurnerBEClass == null || !blazeBurnerBEClass.isInstance(be)) return;

        try {
            // BlazeBurnerBlockEntity stores heat level in the block state
            // The heat property is on the block: BlazeBurnerBlock.HEAT_LEVEL
            String blockId = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString();
            if (blockId.contains("blaze_burner")) {
                data.put("isBlazeBurner", true);
                // Try to read the heat level from block state properties
                try {
                    Class<?> blazeBlockClass = Class.forName("com.simibubi.create.content.processing.burner.BlazeBurnerBlock");
                    Field heatLevelField = blazeBlockClass.getDeclaredField("HEAT_LEVEL");
                    heatLevelField.setAccessible(true);
                    Object heatLevelProp = heatLevelField.get(null);
                    if (heatLevelProp instanceof net.minecraft.world.level.block.state.properties.EnumProperty<?> enumProp) {
                        Object value = be.getBlockState().getValue(enumProp);
                        data.put("heatLevel", value.toString().toLowerCase());
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            LogicLink.LOGGER.debug("Failed to read blaze data: {}", e.getMessage());
        }
    }

    /**
     * Read data from Create core train blocks: Station, Signal, Observer.
     * Uses reflection to avoid hard compile dependency.
     */
    private static void readTrainData(BlockEntity be, Map<String, Object> data) {
        initTrainReflection();

        try {
            // === Track Station ===
            if (stationBEClass != null && stationBEClass.isInstance(be)) {
                data.put("isTrainStation", true);

                // Station name & assembly mode
                if (stationGetStationMethod != null) {
                    Object station = stationGetStationMethod.invoke(be);
                    if (station != null && globalStationClass != null) {
                        Field nameField = globalStationClass.getField("name");
                        String stationName = (String) nameField.get(station);
                        data.put("stationName", stationName != null ? stationName : "");

                        // Present train
                        if (stationGetPresentTrainMethod != null) {
                            Object presentTrain = stationGetPresentTrainMethod.invoke(station);
                            data.put("trainPresent", presentTrain != null);
                            if (presentTrain != null) {
                                readTrainInfo(presentTrain, data, "present");
                            }
                        }

                        // Imminent train (approaching)
                        if (stationGetImminentTrainMethod != null) {
                            Object imminentTrain = stationGetImminentTrainMethod.invoke(station);
                            data.put("trainImminent", imminentTrain != null);
                            if (imminentTrain != null && !data.containsKey("presentTrainName")) {
                                readTrainInfo(imminentTrain, data, "imminent");
                            }
                        }
                    }
                }

                if (stationIsAssemblingMethod != null) {
                    data.put("assembling", (Boolean) stationIsAssemblingMethod.invoke(be));
                }
                return;
            }

            // === Track Signal ===
            if (signalBEClass != null && signalBEClass.isInstance(be)) {
                data.put("isTrainSignal", true);

                if (signalGetStateMethod != null) {
                    Object state = signalGetStateMethod.invoke(be);
                    data.put("signalState", state != null ? state.toString().toLowerCase() : "unknown");
                }
                if (signalIsPoweredMethod != null) {
                    data.put("powered", (Boolean) signalIsPoweredMethod.invoke(be));
                }
                if (signalGetOverlayMethod != null) {
                    Object overlay = signalGetOverlayMethod.invoke(be);
                    data.put("overlay", overlay != null ? overlay.toString().toLowerCase() : "none");
                }
                return;
            }

            // === Track Observer ===
            if (observerBEClass != null && observerBEClass.isInstance(be)) {
                data.put("isTrackObserver", true);

                // Is a train currently passing?
                if (observerPassingTrainField != null) {
                    Object uuid = observerPassingTrainField.get(be);
                    data.put("trainPassing", uuid != null);
                }
                if (observerIsBlockPoweredMethod != null) {
                    data.put("powered", (Boolean) observerIsBlockPoweredMethod.invoke(be));
                }
                // Filter item
                if (observerGetFilterMethod != null) {
                    Object filterStack = observerGetFilterMethod.invoke(be);
                    if (filterStack instanceof ItemStack fs && !fs.isEmpty()) {
                        data.put("filter", fs.getItem().builtInRegistryHolder().key().location().toString());
                        data.put("filterName", fs.getHoverName().getString());
                    }
                }
                return;
            }
        } catch (Exception e) {
            LogicLink.LOGGER.debug("Failed to read train data: {}", e.getMessage());
        }
    }

    /**
     * Read train entity info (name, speed, schedule presence, ID).
     */
    private static void readTrainInfo(Object train, Map<String, Object> data, String prefix) {
        try {
            if (trainNameField != null) {
                Object nameComp = trainNameField.get(train);
                if (nameComp instanceof net.minecraft.network.chat.Component comp) {
                    data.put(prefix + "TrainName", comp.getString());
                }
            }
            if (trainIdField != null) {
                Object id = trainIdField.get(train);
                if (id != null) {
                    data.put(prefix + "TrainId", id.toString());
                }
            }
            if (trainSpeedField != null) {
                data.put(prefix + "TrainSpeed", trainSpeedField.getDouble(train));
            }
            if (trainScheduleField != null) {
                Object schedule = trainScheduleField.get(train);
                data.put("hasSchedule", schedule != null);
            }
        } catch (Exception e) {
            LogicLink.LOGGER.debug("Failed to read train info: {}", e.getMessage());
        }
    }

    /**
     * Read data from Steam 'n' Rails blocks: Track Switch, Track Coupler, Semaphore.
     * Uses reflection to avoid hard compile dependency on the railways mod.
     */
    private static void readRailwaysData(BlockEntity be, Map<String, Object> data) {
        initRailwaysReflection();

        try {
            // === Track Switch ===
            if (trackSwitchBEClass != null && trackSwitchBEClass.isInstance(be)) {
                data.put("isTrackSwitch", true);

                if (switchGetStateMethod != null) {
                    Object state = switchGetStateMethod.invoke(be);
                    data.put("switchState", state != null ? state.toString().toLowerCase() : "unknown");
                }
                if (switchIsAutomaticMethod != null) {
                    data.put("automatic", (Boolean) switchIsAutomaticMethod.invoke(be));
                }
                if (switchIsLockedMethod != null) {
                    data.put("locked", (Boolean) switchIsLockedMethod.invoke(be));
                }
                if (switchIsNormalMethod != null) {
                    data.put("isNormal", (Boolean) switchIsNormalMethod.invoke(be));
                }
                if (switchIsReverseLeftMethod != null) {
                    data.put("isReverseLeft", (Boolean) switchIsReverseLeftMethod.invoke(be));
                }
                if (switchIsReverseRightMethod != null) {
                    data.put("isReverseRight", (Boolean) switchIsReverseRightMethod.invoke(be));
                }
                if (switchExitCountField != null) {
                    data.put("exitCount", switchExitCountField.getInt(be));
                }
                if (switchGetAnalogOutputMethod != null) {
                    data.put("analogOutput", (int) switchGetAnalogOutputMethod.invoke(be));
                }
                return;
            }

            // === Track Coupler ===
            if (trackCouplerBEClass != null && trackCouplerBEClass.isInstance(be)) {
                data.put("isTrackCoupler", true);

                if (couplerGetOperationModeMethod != null) {
                    Object mode = couplerGetOperationModeMethod.invoke(be);
                    data.put("operationMode", mode != null ? mode.toString().toLowerCase() : "none");
                }
                if (couplerAreEdgePointsOkMethod != null) {
                    data.put("edgePointsOk", (Boolean) couplerAreEdgePointsOkMethod.invoke(be));
                }
                if (couplerGetEdgeSpacingMethod != null) {
                    data.put("edgeSpacing", (int) couplerGetEdgeSpacingMethod.invoke(be));
                }
                if (couplerGetReportedPowerMethod != null) {
                    data.put("powered", (Boolean) couplerGetReportedPowerMethod.invoke(be));
                }
                if (couplerGetAllowedOpModeMethod != null) {
                    Object allowed = couplerGetAllowedOpModeMethod.invoke(be);
                    data.put("allowedMode", allowed != null ? allowed.toString().toLowerCase() : "unknown");
                }
                if (couplerGetAnalogOutputMethod != null) {
                    data.put("analogOutput", (int) couplerGetAnalogOutputMethod.invoke(be));
                }
                return;
            }

            // === Semaphore ===
            if (semaphoreBEClass != null && semaphoreBEClass.isInstance(be)) {
                data.put("isSemaphore", true);
                // Semaphore state is primarily visual — read blockstate
                try {
                    var blockState = be.getBlockState();
                    for (var prop : blockState.getProperties()) {
                        data.put(prop.getName(), blockState.getValue(prop).toString().toLowerCase());
                    }
                } catch (Exception ignored) {}
                return;
            }
        } catch (Exception e) {
            LogicLink.LOGGER.debug("Failed to read railways data: {}", e.getMessage());
        }
    }

    /**
     * Read item inventory from NeoForge IItemHandler capability.
     * Works with any block that exposes item storage (basins, vaults, depots, chests, etc.)
     */
    private static void readItemData(Level level, BlockPos pos, Map<String, Object> data) {
        // Try all directions + null for the capability
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler == null) {
            // Try specific directions
            for (Direction dir : Direction.values()) {
                handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, dir);
                if (handler != null) break;
            }
        }
        if (handler == null) return;

        data.put("hasInventory", true);
        data.put("inventorySize", handler.getSlots());

        List<Map<String, Object>> items = new ArrayList<>();
        int totalItems = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", stack.getItem().builtInRegistryHolder().key().location().toString());
                item.put("displayName", stack.getHoverName().getString());
                item.put("count", stack.getCount());
                item.put("maxCount", stack.getMaxStackSize());
                item.put("slot", i);
                items.add(item);
                totalItems += stack.getCount();
            }
        }
        data.put("inventory", items);
        data.put("totalItems", totalItems);
    }

    /**
     * Read fluid storage from NeoForge IFluidHandler capability.
     * Works with any block that has fluid tanks (Create tanks, basins, etc.)
     */
    private static void readFluidData(Level level, BlockPos pos, Map<String, Object> data) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (handler == null) {
            for (Direction dir : Direction.values()) {
                handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, dir);
                if (handler != null) break;
            }
        }
        if (handler == null) return;

        data.put("hasFluidStorage", true);
        data.put("tankCount", handler.getTanks());

        List<Map<String, Object>> tanks = new ArrayList<>();
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack fluid = handler.getFluidInTank(i);
            int capacity = handler.getTankCapacity(i);

            Map<String, Object> tank = new HashMap<>();
            tank.put("tank", i);
            tank.put("capacity", capacity);

            if (!fluid.isEmpty()) {
                tank.put("fluid", BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString());
                tank.put("amount", fluid.getAmount());
                tank.put("percentage", capacity > 0 ? (double) fluid.getAmount() / capacity * 100.0 : 0.0);
            } else {
                tank.put("fluid", "empty");
                tank.put("amount", 0);
                tank.put("percentage", 0.0);
            }
            tanks.add(tank);
        }
        data.put("tanks", tanks);
    }

    /**
     * Read Create Storage data from StorageController, StorageInterface, or SimpleStorageBox.
     * Uses reflection to avoid hard dependency on the Create Storage mod.
     * Provides full network stats: box details, capacity percentages, filters, upgrades.
     */
    private static void readStorageData(BlockEntity be, Level level, Map<String, Object> data) {
        initStorageReflection();
        if (storageControllerBEClass == null) return;

        try {
            // === Storage Controller ===
            if (storageControllerBEClass.isInstance(be)) {
                data.put("isStorageController", true);
                readStorageControllerData(be, data);
                return;
            }

            // === Storage Interface ===
            if (storageInterfaceBEClass != null && storageInterfaceBEClass.isInstance(be)) {
                data.put("isStorageInterface", true);
                Object controller = interfaceControllerField != null ? interfaceControllerField.get(be) : null;
                data.put("isConnected", controller != null);
                if (controller != null) {
                    // Read the controller's position
                    if (controller instanceof BlockEntity controllerBE) {
                        Map<String, Integer> ctrlPos = new HashMap<>();
                        ctrlPos.put("x", controllerBE.getBlockPos().getX());
                        ctrlPos.put("y", controllerBE.getBlockPos().getY());
                        ctrlPos.put("z", controllerBE.getBlockPos().getZ());
                        data.put("controllerPosition", ctrlPos);
                    }
                    // Read the full network data from the connected controller
                    readStorageControllerData(controller, data);
                }
                return;
            }

            // === Simple Storage Box ===
            if (simpleStorageBoxBEClass != null && simpleStorageBoxBEClass.isInstance(be)) {
                data.put("isStorageBox", true);
                readSingleBoxData(be, data);
            }
        } catch (Exception e) {
            LogicLink.LOGGER.debug("Failed to read storage data: {}", e.getMessage());
        }
    }

    /**
     * Read full network data from a StorageControllerEntity instance.
     */
    private static void readStorageControllerData(Object controllerBE, Map<String, Object> data) {
        try {
            Object storageNetwork = controllerStorageNetworkField != null
                    ? controllerStorageNetworkField.get(controllerBE) : null;

            if (storageNetwork == null || networkBoxesField == null) {
                data.put("isConnected", false);
                data.put("boxCount", 0);
                data.put("totalStored", 0);
                data.put("totalCapacity", 0);
                data.put("usagePercent", 0.0);
                return;
            }

            @SuppressWarnings("unchecked")
            List<?> boxes = (List<?>) networkBoxesField.get(storageNetwork);
            data.put("isConnected", !boxes.isEmpty());
            data.put("boxCount", boxes.size());

            long totalStored = 0;
            long totalCapacity = 0;
            int totalUniqueItems = 0;
            Map<String, long[]> itemTotals = new HashMap<>();
            List<Map<String, Object>> boxList = new ArrayList<>();

            for (int i = 0; i < boxes.size(); i++) {
                Object networkItem = boxes.get(i);
                Object boxEntity = networkItemBoxEntityField != null
                        ? networkItemBoxEntityField.get(networkItem) : null;
                Object boxBlockPos = networkItemBlockPosField != null
                        ? networkItemBlockPosField.get(networkItem) : null;

                if (boxEntity == null) continue;

                Map<String, Object> boxInfo = new HashMap<>();
                boxInfo.put("slot", i + 1);

                // Position
                if (boxBlockPos instanceof BlockPos bPos) {
                    Map<String, Integer> pos = new HashMap<>();
                    pos.put("x", bPos.getX());
                    pos.put("y", bPos.getY());
                    pos.put("z", bPos.getZ());
                    boxInfo.put("position", pos);
                }

                // Storage stats
                int stored = boxGetStoredAmountMethod != null
                        ? (int) boxGetStoredAmountMethod.invoke(boxEntity) : 0;
                int maxCap = boxGetMaxItemCapacityMethod != null
                        ? (int) boxGetMaxItemCapacityMethod.invoke(boxEntity) : 0;
                int capUpgrades = boxGetCapacityUpgradesMethod != null
                        ? (int) boxGetCapacityUpgradesMethod.invoke(boxEntity) : 0;
                boolean voidUpgrade = boxHasVoidUpgradeMethod != null
                        && (boolean) boxHasVoidUpgradeMethod.invoke(boxEntity);

                boxInfo.put("storedAmount", stored);
                boxInfo.put("maxCapacity", maxCap);
                boxInfo.put("usagePercent", maxCap > 0 ? Math.round((double) stored / maxCap * 1000.0) / 10.0 : 0.0);
                boxInfo.put("capacityUpgrades", capUpgrades);
                boxInfo.put("hasVoidUpgrade", voidUpgrade);

                totalStored += stored;
                totalCapacity += maxCap;

                // Filter
                if (boxGetFilterItemMethod != null) {
                    Object filterObj = boxGetFilterItemMethod.invoke(boxEntity);
                    if (filterObj instanceof ItemStack filterStack && !filterStack.isEmpty()) {
                        String filterId = filterStack.getItem().builtInRegistryHolder().key().location().toString();
                        boxInfo.put("filter", filterId);
                        boxInfo.put("filterDisplayName", filterStack.getHoverName().getString());
                    }
                }

                // Current stored item
                if (boxGetItemHandlerMethod != null) {
                    Object handler = boxGetItemHandlerMethod.invoke(boxEntity);
                    if (handler instanceof IItemHandler itemHandler && itemHandler.getSlots() > 0) {
                        ItemStack stack = itemHandler.getStackInSlot(0);
                        if (!stack.isEmpty()) {
                            String itemId = stack.getItem().builtInRegistryHolder().key().location().toString();
                            boxInfo.put("item", itemId);
                            boxInfo.put("itemDisplayName", stack.getHoverName().getString());
                            boxInfo.put("count", stack.getCount());

                            // Track totals per item type
                            long[] counts = itemTotals.computeIfAbsent(itemId, k -> new long[]{0, 0});
                            counts[0] += stored;
                            counts[1]++;
                        }
                    }
                }

                boxList.add(boxInfo);
            }

            data.put("boxes", boxList);
            data.put("totalStored", totalStored);
            data.put("totalCapacity", totalCapacity);
            data.put("usagePercent", totalCapacity > 0
                    ? Math.round((double) totalStored / totalCapacity * 1000.0) / 10.0 : 0.0);

            // Item summary with per-item stats
            List<Map<String, Object>> itemSummary = new ArrayList<>();
            for (Map.Entry<String, long[]> entry : itemTotals.entrySet()) {
                Map<String, Object> itemInfo = new HashMap<>();
                itemInfo.put("name", entry.getKey());
                itemInfo.put("totalStored", entry.getValue()[0]);
                itemInfo.put("boxCount", (int) entry.getValue()[1]);
                itemSummary.add(itemInfo);
            }
            data.put("itemSummary", itemSummary);
            data.put("uniqueItemCount", itemTotals.size());

        } catch (Exception e) {
            LogicLink.LOGGER.debug("Failed to read storage controller data: {}", e.getMessage());
        }
    }

    /**
     * Read data from a single SimpleStorageBoxEntity.
     */
    private static void readSingleBoxData(Object boxEntity, Map<String, Object> data) {
        try {
            int stored = boxGetStoredAmountMethod != null
                    ? (int) boxGetStoredAmountMethod.invoke(boxEntity) : 0;
            int maxCap = boxGetMaxItemCapacityMethod != null
                    ? (int) boxGetMaxItemCapacityMethod.invoke(boxEntity) : 0;
            int capUpgrades = boxGetCapacityUpgradesMethod != null
                    ? (int) boxGetCapacityUpgradesMethod.invoke(boxEntity) : 0;
            boolean voidUpgrade = boxHasVoidUpgradeMethod != null
                    && (boolean) boxHasVoidUpgradeMethod.invoke(boxEntity);

            data.put("storedAmount", stored);
            data.put("maxCapacity", maxCap);
            data.put("usagePercent", maxCap > 0 ? Math.round((double) stored / maxCap * 1000.0) / 10.0 : 0.0);
            data.put("capacityUpgrades", capUpgrades);
            data.put("hasVoidUpgrade", voidUpgrade);

            // Filter
            if (boxGetFilterItemMethod != null) {
                Object filterObj = boxGetFilterItemMethod.invoke(boxEntity);
                if (filterObj instanceof ItemStack filterStack && !filterStack.isEmpty()) {
                    data.put("filter", filterStack.getItem().builtInRegistryHolder().key().location().toString());
                    data.put("filterDisplayName", filterStack.getHoverName().getString());
                }
            }

            // Current stored item
            if (boxGetItemHandlerMethod != null) {
                Object handler = boxGetItemHandlerMethod.invoke(boxEntity);
                if (handler instanceof IItemHandler itemHandler && itemHandler.getSlots() > 0) {
                    ItemStack stack = itemHandler.getStackInSlot(0);
                    if (!stack.isEmpty()) {
                        data.put("item", stack.getItem().builtInRegistryHolder().key().location().toString());
                        data.put("itemDisplayName", stack.getHoverName().getString());
                        data.put("count", stack.getCount());
                    }
                }
            }
        } catch (Exception e) {
            LogicLink.LOGGER.debug("Failed to read storage box data: {}", e.getMessage());
        }
    }

    // ==================== Reflection Init ====================

    private static void initKineticReflection() {
        if (kineticReflectionInit) return;
        kineticReflectionInit = true;

        try {
            kineticBEClass = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
            getSpeedMethod = kineticBEClass.getMethod("getSpeed");
            getTheoreticalSpeedMethod = kineticBEClass.getMethod("getTheoreticalSpeed");
            isOverStressedMethod = kineticBEClass.getMethod("isOverStressed");
            hasNetworkMethod = kineticBEClass.getMethod("hasNetwork");

            // Protected fields — need setAccessible
            try {
                capacityField = kineticBEClass.getDeclaredField("capacity");
                capacityField.setAccessible(true);
            } catch (NoSuchFieldException ignored) {}

            try {
                stressField = kineticBEClass.getDeclaredField("stress");
                stressField.setAccessible(true);
            } catch (NoSuchFieldException ignored) {}

            LogicLink.LOGGER.info("CreateBlockReader: KineticBlockEntity reflection initialized");
        } catch (ClassNotFoundException e) {
            LogicLink.LOGGER.debug("CreateBlockReader: KineticBlockEntity not found (Create not loaded?)");
        } catch (Exception e) {
            LogicLink.LOGGER.warn("CreateBlockReader: Failed to init kinetic reflection: {}", e.getMessage());
        }
    }

    private static void initBlazeReflection() {
        if (blazeReflectionInit) return;
        blazeReflectionInit = true;

        try {
            blazeBurnerBEClass = Class.forName(
                    "com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity");
            LogicLink.LOGGER.info("CreateBlockReader: BlazeBurnerBlockEntity reflection initialized");
        } catch (ClassNotFoundException e) {
            LogicLink.LOGGER.debug("CreateBlockReader: BlazeBurnerBlockEntity not found");
        }
    }

    private static void initTrainReflection() {
        if (trainReflectionInit) return;
        trainReflectionInit = true;

        try {
            // Station
            stationBEClass = Class.forName(
                    "com.simibubi.create.content.trains.station.StationBlockEntity");
            globalStationClass = Class.forName(
                    "com.simibubi.create.content.trains.station.GlobalStation");
            stationGetStationMethod = stationBEClass.getMethod("getStation");
            stationIsAssemblingMethod = stationBEClass.getMethod("isAssembling");
            stationGetPresentTrainMethod = globalStationClass.getMethod("getPresentTrain");
            stationGetImminentTrainMethod = globalStationClass.getMethod("getImminentTrain");

            // Signal
            signalBEClass = Class.forName(
                    "com.simibubi.create.content.trains.signal.SignalBlockEntity");
            signalGetStateMethod = signalBEClass.getMethod("getState");
            signalIsPoweredMethod = signalBEClass.getMethod("isPowered");
            signalGetOverlayMethod = signalBEClass.getMethod("getOverlay");

            // Observer
            observerBEClass = Class.forName(
                    "com.simibubi.create.content.trains.observer.TrackObserverBlockEntity");
            observerGetFilterMethod = observerBEClass.getMethod("getFilter");
            observerPassingTrainField = observerBEClass.getField("passingTrainUUID");
            observerIsBlockPoweredMethod = observerBEClass.getMethod("isBlockPowered");

            // Train entity (for reading train info from station)
            trainClass = Class.forName(
                    "com.simibubi.create.content.trains.entity.Train");
            trainNameField = trainClass.getField("name");
            trainIdField = trainClass.getField("id");
            trainSpeedField = trainClass.getField("speed");
            trainScheduleField = trainClass.getField("runtime");

            LogicLink.LOGGER.info("CreateBlockReader: Train block reflection initialized");
        } catch (ClassNotFoundException e) {
            LogicLink.LOGGER.debug("CreateBlockReader: Train classes not found: {}", e.getMessage());
        } catch (Exception e) {
            LogicLink.LOGGER.warn("CreateBlockReader: Failed to init train reflection: {}", e.getMessage());
        }
    }

    private static void initRailwaysReflection() {
        if (railwaysReflectionInit) return;
        railwaysReflectionInit = true;

        try {
            // Track Switch
            trackSwitchBEClass = Class.forName(
                    "com.railwayteam.railways.content.switches.TrackSwitchBlockEntity");
            switchGetStateMethod = trackSwitchBEClass.getMethod("getState");
            switchIsAutomaticMethod = trackSwitchBEClass.getMethod("isAutomatic");
            switchIsLockedMethod = trackSwitchBEClass.getMethod("isLocked");
            switchIsNormalMethod = trackSwitchBEClass.getMethod("isNormal");
            switchIsReverseLeftMethod = trackSwitchBEClass.getMethod("isReverseLeft");
            switchIsReverseRightMethod = trackSwitchBEClass.getMethod("isReverseRight");
            switchExitCountField = trackSwitchBEClass.getDeclaredField("exitCount");
            switchExitCountField.setAccessible(true);
            switchGetAnalogOutputMethod = trackSwitchBEClass.getMethod("getTargetAnalogOutput");
            switchCycleStateMethod = trackSwitchBEClass.getDeclaredMethod("cycleState");
            switchCycleStateMethod.setAccessible(true);

            // Track Coupler
            trackCouplerBEClass = Class.forName(
                    "com.railwayteam.railways.content.coupling.coupler.TrackCouplerBlockEntity");
            couplerGetOperationModeMethod = trackCouplerBEClass.getMethod("getOperationMode");
            couplerAreEdgePointsOkMethod = trackCouplerBEClass.getMethod("areEdgePointsOk");
            couplerGetEdgeSpacingMethod = trackCouplerBEClass.getMethod("getEdgeSpacing");
            couplerGetReportedPowerMethod = trackCouplerBEClass.getMethod("getReportedPower");
            couplerGetAllowedOpModeMethod = trackCouplerBEClass.getMethod("getAllowedOperationMode");
            couplerGetAnalogOutputMethod = trackCouplerBEClass.getMethod("getTargetAnalogOutput");

            // Semaphore
            semaphoreBEClass = Class.forName(
                    "com.railwayteam.railways.content.semaphore.SemaphoreBlockEntity");

            LogicLink.LOGGER.info("CreateBlockReader: Steam 'n' Rails reflection initialized");
        } catch (ClassNotFoundException e) {
            LogicLink.LOGGER.debug("CreateBlockReader: Steam 'n' Rails not found (railways not loaded)");
            trackSwitchBEClass = null;
            trackCouplerBEClass = null;
        } catch (Exception e) {
            LogicLink.LOGGER.warn("CreateBlockReader: Failed to init railways reflection: {}", e.getMessage());
        }
    }

    private static void initStorageReflection() {
        if (storageReflectionInit) return;
        storageReflectionInit = true;

        try {
            // Core classes
            storageControllerBEClass = Class.forName(
                    "net.fxnt.fxntstorage.controller.StorageControllerEntity");
            storageInterfaceBEClass = Class.forName(
                    "net.fxnt.fxntstorage.controller.StorageInterfaceEntity");
            storageNetworkClass = Class.forName(
                    "net.fxnt.fxntstorage.storage_network.StorageNetwork");
            storageNetworkItemClass = Class.forName(
                    "net.fxnt.fxntstorage.storage_network.StorageNetwork$StorageNetworkItem");
            simpleStorageBoxBEClass = Class.forName(
                    "net.fxnt.fxntstorage.simple_storage.SimpleStorageBoxEntity");

            // Controller fields
            controllerStorageNetworkField = storageControllerBEClass.getField("storageNetwork");
            controllerGetItemHandlerMethod = storageControllerBEClass.getMethod("getItemHandler");

            // Interface fields
            interfaceControllerField = storageInterfaceBEClass.getField("controller");

            // StorageNetwork fields
            networkBoxesField = storageNetworkClass.getField("boxes");

            // StorageNetworkItem fields
            networkItemBoxEntityField = storageNetworkItemClass.getField("simpleStorageBoxEntity");
            networkItemBlockPosField = storageNetworkItemClass.getField("blockPos");

            // SimpleStorageBoxEntity methods
            boxGetStoredAmountMethod = simpleStorageBoxBEClass.getMethod("getStoredAmount");
            boxGetMaxItemCapacityMethod = simpleStorageBoxBEClass.getMethod("getMaxItemCapacity");
            boxGetCapacityUpgradesMethod = simpleStorageBoxBEClass.getMethod("getCapacityUpgrades");
            boxHasVoidUpgradeMethod = simpleStorageBoxBEClass.getMethod("hasVoidUpgrade");
            boxGetFilterItemMethod = simpleStorageBoxBEClass.getMethod("getFilterItem");
            boxGetItemHandlerMethod = simpleStorageBoxBEClass.getMethod("getItemHandler");

            LogicLink.LOGGER.info("CreateBlockReader: Create Storage reflection initialized");
        } catch (ClassNotFoundException e) {
            LogicLink.LOGGER.debug("CreateBlockReader: Create Storage not found (fxntstorage not loaded)");
            storageControllerBEClass = null;
        } catch (Exception e) {
            LogicLink.LOGGER.warn("CreateBlockReader: Failed to init storage reflection: {}", e.getMessage());
            storageControllerBEClass = null;
        }
    }
}
