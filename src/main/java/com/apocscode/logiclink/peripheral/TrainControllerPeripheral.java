package com.apocscode.logiclink.peripheral;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.block.TrainControllerBlockEntity;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * CC:Tweaked peripheral for the Train Controller block.
 * Provides global read access to Create's train network data:
 * - All trains (name, speed, position, schedule, station, carriages)
 * - All stations (name, position, dimension, present/imminent trains)
 * - All signals (state, position, groups)
 * - All observers (activated, filter, current train)
 * <p>
 * Uses reflection to access Create's internal GlobalRailwayManager
 * without hard compile-time dependencies on internal APIs.
 */
public class TrainControllerPeripheral implements IPeripheral {

    private static final Logger LOGGER = LoggerFactory.getLogger("LogicLink/TrainController");

    private final TrainControllerBlockEntity blockEntity;

    // ---- Reflection cache ----
    private static boolean reflectionInit = false;

    // Create main handler
    private static Class<?> createClass = null;
    private static Field railwaysField = null; // Create.RAILWAYS

    // GlobalRailwayManager
    private static Field trainsField = null;       // Map<UUID, Train>
    private static Field trackNetworksField = null; // Map<UUID, TrackGraph>
    private static Field signalEdgeGroupsField = null; // Map<UUID, SignalEdgeGroup>

    // Train class
    private static Field trainNameField = null;     // Component name
    private static Field trainSpeedField = null;    // double speed
    private static Field trainTargetSpeedField = null;
    private static Field trainIdField = null;       // UUID id
    private static Field trainOwnerField = null;    // UUID owner
    private static Field trainCurrentStationField = null; // UUID currentStation
    private static Field trainDerailedField = null; // boolean derailed
    private static Field trainFuelTicksField = null; // int fuelTicks
    private static Field trainCarriagesField = null; // List<Carriage> carriages
    private static Field trainNavigationField = null; // Navigation navigation
    private static Field trainRuntimeField = null;  // ScheduleRuntime runtime
    private static Field trainThrottleField = null; // double throttle
    private static Field trainIconField = null;     // TrainIconType icon
    private static Method trainGetCurrentStationMethod = null; // getCurrentStation()
    private static Method trainMaxSpeedMethod = null;
    private static Method trainGetPresentDimensionsMethod = null;
    private static Method trainGetPositionInDimensionMethod = null;

    // Navigation class
    private static Field navDestinationField = null; // GlobalStation destination
    private static Field navDistToDestField = null;  // double distanceToDestination
    private static Method navIsActiveMethod = null;

    // ScheduleRuntime class
    private static Field runtimeScheduleField = null; // Schedule schedule
    private static Field runtimePausedField = null;   // boolean paused
    private static Field runtimeCompletedField = null; // boolean completed
    private static Field runtimeCurrentEntryField = null; // int currentEntry
    private static Field runtimeCurrentTitleField = null; // String currentTitle
    private static Field runtimeStateField = null;     // State state
    private static Method runtimeGetScheduleMethod = null;

    // Schedule class
    private static Field scheduleEntriesField = null; // List<ScheduleEntry> entries
    private static Field scheduleCyclicField = null;  // boolean cyclic

    // GlobalStation class
    private static Field stationNameField = null;    // String name
    private static Field stationAssemblingField = null; // boolean assembling
    private static Method stationGetPresentTrainMethod = null;
    private static Method stationGetImminentTrainMethod = null;
    private static Method stationGetBlockEntityPosMethod = null;
    private static Method stationGetBlockEntityDimensionMethod = null;

    // TrackGraph class
    private static Method graphGetPointsMethod = null;

    // EdgePointType class
    private static Object epTypeStation = null; // EdgePointType.STATION
    private static Object epTypeSignal = null;  // EdgePointType.SIGNAL
    private static Object epTypeObserver = null; // EdgePointType.OBSERVER

    // SignalBoundary class
    private static Field signalCachedStatesField = null;
    private static Field signalBlockEntitiesField = null;
    private static Field signalGroupsField = null;
    private static Field signalTypesField = null;

    // Couple class (Create's pair type)
    private static Method coupleGetFirstMethod = null;
    private static Method coupleGetSecondMethod = null;

    // TrackObserver class
    private static Method observerIsActivatedMethod = null;
    private static Method observerGetCurrentTrainMethod = null;
    private static Method observerGetFilterMethod = null;
    private static Method observerGetBlockEntityPosMethod = null;
    private static Method observerGetBlockEntityDimensionMethod = null;

    // Component -> getString
    private static Method componentGetStringMethod = null;

    public TrainControllerPeripheral(TrainControllerBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public String getType() {
        return "train_controller";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof TrainControllerPeripheral tcp
                && tcp.blockEntity == this.blockEntity;
    }

    // ================================================================
    // Lua Functions — Train Data
    // ================================================================

    /**
     * Get a list of all trains in the network.
     * Each train includes: name, id, speed, throttle, maxSpeed, derailed, fuelTicks,
     *   carriageCount, currentStation, dimension, position, schedule info.
     *
     * @return List of train data maps.
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getTrains() {
        initReflection();
        Level level = blockEntity.getLevel();
        if (level == null) return new ArrayList<>();

        try {
            Object manager = getManager(level);
            if (manager == null) return new ArrayList<>();

            @SuppressWarnings("unchecked")
            Map<UUID, Object> trains = (Map<UUID, Object>) trainsField.get(manager);
            if (trains == null) return new ArrayList<>();

            List<Map<String, Object>> result = new ArrayList<>();
            for (Object train : trains.values()) {
                result.add(readTrainData(train, level));
            }

            // Sort by name for stable ordering
            result.sort((a, b) -> {
                String na = String.valueOf(a.getOrDefault("name", ""));
                String nb = String.valueOf(b.getOrDefault("name", ""));
                return na.compareToIgnoreCase(nb);
            });

            return result;
        } catch (Exception e) {
            LOGGER.warn("Failed to get trains: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get data for a specific train by name (case-insensitive partial match).
     *
     * @param name The train name to search for.
     * @return Train data map, or nil if not found.
     */
    @LuaFunction(mainThread = true)
    public final @Nullable Map<String, Object> getTrain(String name) throws LuaException {
        initReflection();
        Level level = blockEntity.getLevel();
        if (level == null) throw new LuaException("World not available");

        try {
            Object manager = getManager(level);
            if (manager == null) throw new LuaException("Train manager not available");

            @SuppressWarnings("unchecked")
            Map<UUID, Object> trains = (Map<UUID, Object>) trainsField.get(manager);
            if (trains == null) return null;

            String searchLower = name.toLowerCase();
            for (Object train : trains.values()) {
                String trainName = getTrainName(train);
                if (trainName.toLowerCase().contains(searchLower)) {
                    return readTrainData(train, level);
                }
            }
            return null;
        } catch (LuaException le) {
            throw le;
        } catch (Exception e) {
            throw new LuaException("Failed to get train: " + e.getMessage());
        }
    }

    /**
     * Get a list of all stations in the network.
     * Each station includes: name, position, dimension, assembling,
     *   presentTrain, imminentTrain.
     *
     * @return List of station data maps.
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getStations() {
        initReflection();
        Level level = blockEntity.getLevel();
        if (level == null) return new ArrayList<>();

        try {
            Object manager = getManager(level);
            if (manager == null) return new ArrayList<>();

            List<Map<String, Object>> result = new ArrayList<>();
            Collection<Object> stations = getAllEdgePoints(manager, epTypeStation);

            for (Object station : stations) {
                result.add(readStationData(station));
            }

            result.sort((a, b) -> {
                String na = String.valueOf(a.getOrDefault("name", ""));
                String nb = String.valueOf(b.getOrDefault("name", ""));
                return na.compareToIgnoreCase(nb);
            });

            return result;
        } catch (Exception e) {
            LOGGER.warn("Failed to get stations: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get a list of all signal boundaries in the network.
     * Each signal includes: id, states (for each side), positions, types.
     *
     * @return List of signal data maps.
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getSignals() {
        initReflection();
        Level level = blockEntity.getLevel();
        if (level == null) return new ArrayList<>();

        try {
            Object manager = getManager(level);
            if (manager == null) return new ArrayList<>();

            List<Map<String, Object>> result = new ArrayList<>();
            Collection<Object> signals = getAllEdgePoints(manager, epTypeSignal);

            for (Object signal : signals) {
                result.add(readSignalData(signal));
            }

            return result;
        } catch (Exception e) {
            LOGGER.warn("Failed to get signals: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get a list of all track observers in the network.
     * Each observer includes: id, activated, currentTrain, position, dimension, filter.
     *
     * @return List of observer data maps.
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getObservers() {
        initReflection();
        Level level = blockEntity.getLevel();
        if (level == null) return new ArrayList<>();

        try {
            Object manager = getManager(level);
            if (manager == null) return new ArrayList<>();

            List<Map<String, Object>> result = new ArrayList<>();
            Collection<Object> observers = getAllEdgePoints(manager, epTypeObserver);

            for (Object observer : observers) {
                result.add(readObserverData(observer));
            }

            return result;
        } catch (Exception e) {
            LOGGER.warn("Failed to get observers: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get a summary of the entire train network.
     * Returns counts and overview data in a single call.
     *
     * @return Map with trainCount, stationCount, signalCount, observerCount, graphCount.
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> getNetworkOverview() {
        initReflection();
        Level level = blockEntity.getLevel();
        Map<String, Object> result = new HashMap<>();
        if (level == null) return result;

        try {
            Object manager = getManager(level);
            if (manager == null) return result;

            @SuppressWarnings("unchecked")
            Map<UUID, Object> trains = (Map<UUID, Object>) trainsField.get(manager);
            @SuppressWarnings("unchecked")
            Map<UUID, Object> graphs = (Map<UUID, Object>) trackNetworksField.get(manager);
            @SuppressWarnings("unchecked")
            Map<UUID, Object> signalGroups = (Map<UUID, Object>) signalEdgeGroupsField.get(manager);

            result.put("trainCount", trains != null ? trains.size() : 0);
            result.put("graphCount", graphs != null ? graphs.size() : 0);
            result.put("signalGroupCount", signalGroups != null ? signalGroups.size() : 0);

            // Count edge points
            Collection<Object> stations = getAllEdgePoints(manager, epTypeStation);
            Collection<Object> signals = getAllEdgePoints(manager, epTypeSignal);
            Collection<Object> observers = getAllEdgePoints(manager, epTypeObserver);

            result.put("stationCount", stations.size());
            result.put("signalCount", signals.size());
            result.put("observerCount", observers.size());

            // Train status summary
            int moving = 0, stopped = 0, stalled = 0, derailed = 0;
            if (trains != null) {
                for (Object train : trains.values()) {
                    try {
                        double speed = trainSpeedField.getDouble(train);
                        boolean isDerailed = trainDerailedField.getBoolean(train);
                        if (isDerailed) derailed++;
                        else if (Math.abs(speed) > 0.01) moving++;
                        else stopped++;
                    } catch (Exception ignored) {}
                }
            }
            result.put("trainsMoving", moving);
            result.put("trainsStopped", stopped);
            result.put("trainsDerailed", derailed);

        } catch (Exception e) {
            LOGGER.warn("Failed to get network overview: {}", e.getMessage());
        }
        return result;
    }

    /**
     * Get the number of trains in the network.
     *
     * @return The number of trains.
     */
    @LuaFunction(mainThread = true)
    public final int getTrainCount() {
        initReflection();
        Level level = blockEntity.getLevel();
        if (level == null) return 0;

        try {
            Object manager = getManager(level);
            if (manager == null) return 0;

            @SuppressWarnings("unchecked")
            Map<UUID, Object> trains = (Map<UUID, Object>) trainsField.get(manager);
            return trains != null ? trains.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Get the current refresh interval in ticks.
     *
     * @return Refresh interval (1-200 ticks).
     */
    @LuaFunction(mainThread = true)
    public final int getRefreshInterval() {
        return blockEntity.getRefreshInterval();
    }

    /**
     * Set the refresh interval in ticks.
     *
     * @param ticks Interval between cache refreshes (1-200, default 20).
     */
    @LuaFunction(mainThread = true)
    public final void setRefreshInterval(int ticks) {
        blockEntity.setRefreshInterval(ticks);
    }

    /**
     * Force an immediate cache refresh of all data.
     */
    @LuaFunction(mainThread = true)
    public final void refresh() {
        blockEntity.invalidateCaches();
    }

    // ================================================================
    // Internal — Data Reading
    // ================================================================

    private Map<String, Object> readTrainData(Object train, Level level) {
        Map<String, Object> data = new HashMap<>();


        try {
            // Basic info
            data.put("name", getTrainName(train));
            data.put("id", trainIdField.get(train).toString());
            data.put("speed", trainSpeedField.getDouble(train));
            data.put("targetSpeed", trainTargetSpeedField.getDouble(train));
            data.put("throttle", trainThrottleField.getDouble(train));
            data.put("derailed", trainDerailedField.getBoolean(train));
            data.put("fuelTicks", trainFuelTicksField.getInt(train));

            if (trainMaxSpeedMethod != null) {
                data.put("maxSpeed", ((Number) trainMaxSpeedMethod.invoke(train)).floatValue());
            }

            // Carriages
            List<?> carriages = (List<?>) trainCarriagesField.get(train);
            data.put("carriageCount", carriages != null ? carriages.size() : 0);

            // Current station
            Object currentStation = trainGetCurrentStationMethod.invoke(train);
            if (currentStation != null) {
                String stationName = (String) stationNameField.get(currentStation);
                data.put("currentStation", stationName);
            }

            // Owner
            UUID owner = (UUID) trainOwnerField.get(train);
            if (owner != null) data.put("owner", owner.toString());

            // Dimensions and position
            if (trainGetPresentDimensionsMethod != null) {
                @SuppressWarnings("unchecked")
                List<ResourceKey<Level>> dims = (List<ResourceKey<Level>>) trainGetPresentDimensionsMethod.invoke(train);
                if (dims != null && !dims.isEmpty()) {
                    List<String> dimNames = new ArrayList<>();
                    for (ResourceKey<Level> dim : dims) {
                        dimNames.add(dim.location().toString());

                        // Try to get position in this dimension
                        if (trainGetPositionInDimensionMethod != null) {
                            try {
                                @SuppressWarnings("unchecked")
                                Optional<BlockPos> posOpt = (Optional<BlockPos>) trainGetPositionInDimensionMethod.invoke(train, dim);
                                if (posOpt != null && posOpt.isPresent()) {
                                    BlockPos pos = posOpt.get();
                                    Map<String, Object> position = new HashMap<>();
                                    position.put("x", pos.getX());
                                    position.put("y", pos.getY());
                                    position.put("z", pos.getZ());
                                    position.put("dimension", dim.location().toString());
                                    data.put("position", position);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                    data.put("dimensions", dimNames);
                }
            }

            // Navigation info
            readNavigationData(train, data);

            // Schedule info
            readScheduleData(train, data);

        } catch (Exception e) {
            data.put("error", e.getMessage());
        }

        return data;
    }

    private void readNavigationData(Object train, Map<String, Object> data) {
        try {
            Object nav = trainNavigationField.get(train);
            if (nav == null) return;

            boolean active = (boolean) navIsActiveMethod.invoke(nav);
            data.put("navigating", active);

            if (active) {
                Object dest = navDestinationField.get(nav);
                if (dest != null) {
                    String destName = (String) stationNameField.get(dest);
                    data.put("destination", destName);
                }
                double dist = navDistToDestField.getDouble(nav);
                data.put("distanceToDestination", Math.round(dist * 10.0) / 10.0);
            }
        } catch (Exception ignored) {}
    }

    private void readScheduleData(Object train, Map<String, Object> data) {
        try {
            Object runtime = trainRuntimeField.get(train);
            if (runtime == null) return;

            Object schedule = runtimeGetScheduleMethod.invoke(runtime);
            boolean hasSchedule = schedule != null;
            data.put("hasSchedule", hasSchedule);

            if (hasSchedule) {
                data.put("schedulePaused", runtimePausedField.getBoolean(runtime));
                data.put("scheduleCompleted", runtimeCompletedField.getBoolean(runtime));
                data.put("scheduleEntry", runtimeCurrentEntryField.getInt(runtime));

                String title = (String) runtimeCurrentTitleField.get(runtime);
                if (title != null && !title.isEmpty()) {
                    data.put("scheduleTitle", title);
                }

                Object state = runtimeStateField.get(runtime);
                if (state != null) {
                    data.put("scheduleState", state.toString().toLowerCase());
                }

                // Count schedule entries
                List<?> entries = (List<?>) scheduleEntriesField.get(schedule);
                if (entries != null) {
                    data.put("scheduleEntryCount", entries.size());
                }
                data.put("scheduleCyclic", scheduleCyclicField.getBoolean(schedule));
            }
        } catch (Exception ignored) {}
    }

    private Map<String, Object> readStationData(Object station) {
        Map<String, Object> data = new HashMap<>();

        try {
            data.put("name", stationNameField.get(station));
            data.put("assembling", stationAssemblingField.getBoolean(station));

            // Position and dimension
            if (stationGetBlockEntityPosMethod != null) {
                BlockPos pos = (BlockPos) stationGetBlockEntityPosMethod.invoke(station);
                if (pos != null) {
                    Map<String, Integer> position = new HashMap<>();
                    position.put("x", pos.getX());
                    position.put("y", pos.getY());
                    position.put("z", pos.getZ());
                    data.put("position", position);
                }
            }

            if (stationGetBlockEntityDimensionMethod != null) {
                @SuppressWarnings("unchecked")
                ResourceKey<Level> dim = (ResourceKey<Level>) stationGetBlockEntityDimensionMethod.invoke(station);
                if (dim != null) {
                    data.put("dimension", dim.location().toString());
                }
            }

            // Present train
            Object presentTrain = stationGetPresentTrainMethod.invoke(station);
            if (presentTrain != null) {
                data.put("trainPresent", true);
                data.put("presentTrainName", getTrainName(presentTrain));
                data.put("presentTrainId", ((UUID) trainIdField.get(presentTrain)).toString());
            } else {
                data.put("trainPresent", false);
            }

            // Imminent train
            Object imminentTrain = stationGetImminentTrainMethod.invoke(station);
            if (imminentTrain != null) {
                data.put("trainImminent", true);
                data.put("imminentTrainName", getTrainName(imminentTrain));
                data.put("imminentTrainId", ((UUID) trainIdField.get(imminentTrain)).toString());
            } else {
                data.put("trainImminent", false);
            }

        } catch (Exception e) {
            data.put("error", e.getMessage());
        }

        return data;
    }

    private Map<String, Object> readSignalData(Object signal) {
        Map<String, Object> data = new HashMap<>();

        try {
            // Signal ID
            Field idField = signal.getClass().getSuperclass().getField("id");
            UUID id = (UUID) idField.get(signal);
            data.put("id", id.toString());

            // Cached states (Couple<SignalState>)
            if (signalCachedStatesField != null) {
                Object statesCouple = signalCachedStatesField.get(signal);
                if (statesCouple != null) {
                    Object first = coupleGetFirstMethod.invoke(statesCouple);
                    Object second = coupleGetSecondMethod.invoke(statesCouple);
                    data.put("stateForward", first != null ? first.toString().toLowerCase() : "unknown");
                    data.put("stateBackward", second != null ? second.toString().toLowerCase() : "unknown");
                }
            }

            // Signal types (Couple<SignalType>)
            if (signalTypesField != null) {
                Object typesCouple = signalTypesField.get(signal);
                if (typesCouple != null) {
                    Object first = coupleGetFirstMethod.invoke(typesCouple);
                    Object second = coupleGetSecondMethod.invoke(typesCouple);
                    data.put("typeForward", first != null ? first.toString().toLowerCase() : "unknown");
                    data.put("typeBackward", second != null ? second.toString().toLowerCase() : "unknown");
                }
            }

            // Block entity positions (Couple<Map<BlockPos, Boolean>>)
            if (signalBlockEntitiesField != null) {
                Object beCouple = signalBlockEntitiesField.get(signal);
                if (beCouple != null) {
                    List<Map<String, Integer>> positions = new ArrayList<>();
                    for (int side = 0; side < 2; side++) {
                        Object sideMap = side == 0
                                ? coupleGetFirstMethod.invoke(beCouple)
                                : coupleGetSecondMethod.invoke(beCouple);
                        if (sideMap instanceof Map<?, ?> posMap) {
                            for (Object key : posMap.keySet()) {
                                if (key instanceof BlockPos bp) {
                                    Map<String, Integer> pos = new HashMap<>();
                                    pos.put("x", bp.getX());
                                    pos.put("y", bp.getY());
                                    pos.put("z", bp.getZ());
                                    positions.add(pos);
                                }
                            }
                        }
                    }
                    if (!positions.isEmpty()) {
                        data.put("positions", positions);
                    }
                }
            }

        } catch (Exception e) {
            data.put("error", e.getMessage());
        }

        return data;
    }

    private Map<String, Object> readObserverData(Object observer) {
        Map<String, Object> data = new HashMap<>();

        try {
            // ID
            Field idField = observer.getClass().getSuperclass().getSuperclass().getField("id");
            UUID id = (UUID) idField.get(observer);
            data.put("id", id.toString());

            // Activated
            if (observerIsActivatedMethod != null) {
                data.put("activated", (boolean) observerIsActivatedMethod.invoke(observer));
            }

            // Current train
            if (observerGetCurrentTrainMethod != null) {
                UUID trainId = (UUID) observerGetCurrentTrainMethod.invoke(observer);
                if (trainId != null) {
                    data.put("currentTrainId", trainId.toString());
                }
            }

            // Position & Dimension
            if (observerGetBlockEntityPosMethod != null) {
                BlockPos pos = (BlockPos) observerGetBlockEntityPosMethod.invoke(observer);
                if (pos != null) {
                    Map<String, Integer> position = new HashMap<>();
                    position.put("x", pos.getX());
                    position.put("y", pos.getY());
                    position.put("z", pos.getZ());
                    data.put("position", position);
                }
            }

            if (observerGetBlockEntityDimensionMethod != null) {
                @SuppressWarnings("unchecked")
                ResourceKey<Level> dim = (ResourceKey<Level>) observerGetBlockEntityDimensionMethod.invoke(observer);
                if (dim != null) {
                    data.put("dimension", dim.location().toString());
                }
            }

            // Filter
            if (observerGetFilterMethod != null) {
                Object filter = observerGetFilterMethod.invoke(observer);
                if (filter != null) {
                    data.put("filter", filter.toString());
                }
            }

        } catch (Exception e) {
            data.put("error", e.getMessage());
        }

        return data;
    }

    // ================================================================
    // Internal — Create API Access
    // ================================================================

    private String getTrainName(Object train) {
        try {
            Object nameComponent = trainNameField.get(train);
            if (nameComponent != null && componentGetStringMethod != null) {
                return (String) componentGetStringMethod.invoke(nameComponent);
            }
        } catch (Exception ignored) {}
        return "Unknown";
    }

    @Nullable
    private Object getManager(Level level) {
        try {
            return railwaysField.get(null); // static field Create.RAILWAYS
        } catch (Exception e) {
            LOGGER.debug("Failed to get GlobalRailwayManager: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Collection<Object> getAllEdgePoints(Object manager, Object edgePointType) {
        List<Object> allPoints = new ArrayList<>();
        if (edgePointType == null || graphGetPointsMethod == null) return allPoints;

        try {
            Map<UUID, Object> graphs = (Map<UUID, Object>) trackNetworksField.get(manager);
            if (graphs == null) return allPoints;

            for (Object graph : graphs.values()) {
                try {
                    Collection<?> points = (Collection<?>) graphGetPointsMethod.invoke(graph, edgePointType);
                    if (points != null) {
                        allPoints.addAll(points);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to get edge points: {}", e.getMessage());
        }

        return allPoints;
    }

    // ================================================================
    // Reflection Initialization
    // ================================================================

    private static synchronized void initReflection() {
        if (reflectionInit) return;
        reflectionInit = true;

        try {
            // Create main class — access via static field Create.RAILWAYS
            createClass = Class.forName("com.simibubi.create.Create");
            railwaysField = createClass.getField("RAILWAYS");

            // GlobalRailwayManager
            Class<?> managerClass = Class.forName(
                    "com.simibubi.create.content.trains.GlobalRailwayManager");
            trainsField = managerClass.getField("trains");
            trackNetworksField = managerClass.getField("trackNetworks");
            signalEdgeGroupsField = managerClass.getField("signalEdgeGroups");

            // Train class
            Class<?> trainClass = Class.forName("com.simibubi.create.content.trains.entity.Train");
            trainNameField = trainClass.getField("name");
            trainSpeedField = trainClass.getField("speed");
            trainTargetSpeedField = trainClass.getField("targetSpeed");
            trainIdField = trainClass.getField("id");
            trainOwnerField = trainClass.getField("owner");
            trainCurrentStationField = trainClass.getField("currentStation");
            trainDerailedField = trainClass.getField("derailed");
            trainFuelTicksField = trainClass.getField("fuelTicks");
            trainCarriagesField = trainClass.getField("carriages");
            trainNavigationField = trainClass.getField("navigation");
            trainRuntimeField = trainClass.getField("runtime");
            trainThrottleField = trainClass.getField("throttle");
            trainGetCurrentStationMethod = trainClass.getMethod("getCurrentStation");
            trainMaxSpeedMethod = trainClass.getMethod("maxSpeed");
            trainGetPresentDimensionsMethod = trainClass.getMethod("getPresentDimensions");

            // getPositionInDimension takes a ResourceKey
            try {
                trainGetPositionInDimensionMethod = trainClass.getMethod("getPositionInDimension",
                        ResourceKey.class);
            } catch (NoSuchMethodException ignored) {}

            // Navigation
            Class<?> navClass = Class.forName("com.simibubi.create.content.trains.entity.Navigation");
            navDestinationField = navClass.getField("destination");
            navDistToDestField = navClass.getField("distanceToDestination");
            navIsActiveMethod = navClass.getMethod("isActive");

            // ScheduleRuntime
            Class<?> runtimeClass = Class.forName(
                    "com.simibubi.create.content.trains.schedule.ScheduleRuntime");
            runtimeScheduleField = runtimeClass.getField("schedule");
            runtimePausedField = runtimeClass.getField("paused");
            runtimeCompletedField = runtimeClass.getField("completed");
            runtimeCurrentEntryField = runtimeClass.getField("currentEntry");
            runtimeCurrentTitleField = runtimeClass.getField("currentTitle");
            runtimeStateField = runtimeClass.getField("state");
            runtimeGetScheduleMethod = runtimeClass.getMethod("getSchedule");

            // Schedule
            Class<?> scheduleClass = Class.forName(
                    "com.simibubi.create.content.trains.schedule.Schedule");
            scheduleEntriesField = scheduleClass.getField("entries");
            scheduleCyclicField = scheduleClass.getField("cyclic");

            // GlobalStation
            Class<?> stationClass = Class.forName(
                    "com.simibubi.create.content.trains.station.GlobalStation");
            stationNameField = stationClass.getField("name");
            stationAssemblingField = stationClass.getField("assembling");
            stationGetPresentTrainMethod = stationClass.getMethod("getPresentTrain");
            stationGetImminentTrainMethod = stationClass.getMethod("getImminentTrain");

            // SingleBlockEntityEdgePoint (parent of GlobalStation and TrackObserver)
            Class<?> singleBEEPClass = Class.forName(
                    "com.simibubi.create.content.trains.signal.SingleBlockEntityEdgePoint");
            stationGetBlockEntityPosMethod = singleBEEPClass.getMethod("getBlockEntityPos");
            stationGetBlockEntityDimensionMethod = singleBEEPClass.getMethod("getBlockEntityDimension");
            observerGetBlockEntityPosMethod = stationGetBlockEntityPosMethod;
            observerGetBlockEntityDimensionMethod = stationGetBlockEntityDimensionMethod;

            // TrackGraph
            Class<?> graphClass = Class.forName(
                    "com.simibubi.create.content.trains.graph.TrackGraph");
            Class<?> epTypeClass = Class.forName(
                    "com.simibubi.create.content.trains.graph.EdgePointType");
            graphGetPointsMethod = graphClass.getMethod("getPoints", epTypeClass);

            // EdgePointType constants
            epTypeStation = epTypeClass.getField("STATION").get(null);
            epTypeSignal = epTypeClass.getField("SIGNAL").get(null);
            epTypeObserver = epTypeClass.getField("OBSERVER").get(null);

            // SignalBoundary
            Class<?> signalClass = Class.forName(
                    "com.simibubi.create.content.trains.signal.SignalBoundary");
            signalCachedStatesField = signalClass.getField("cachedStates");
            signalBlockEntitiesField = signalClass.getField("blockEntities");
            signalGroupsField = signalClass.getField("groups");
            signalTypesField = signalClass.getField("types");

            // Couple class
            Class<?> coupleClass = Class.forName("net.createmod.catnip.data.Couple");
            coupleGetFirstMethod = coupleClass.getMethod("getFirst");
            coupleGetSecondMethod = coupleClass.getMethod("getSecond");

            // TrackObserver
            Class<?> observerClass = Class.forName(
                    "com.simibubi.create.content.trains.observer.TrackObserver");
            observerIsActivatedMethod = observerClass.getMethod("isActivated");
            observerGetCurrentTrainMethod = observerClass.getMethod("getCurrentTrain");
            observerGetFilterMethod = observerClass.getMethod("getFilter");

            // Component.getString()
            componentGetStringMethod = net.minecraft.network.chat.Component.class.getMethod("getString");

            LOGGER.info("TrainControllerPeripheral: Create train reflection initialized successfully");

        } catch (ClassNotFoundException e) {
            LOGGER.warn("TrainControllerPeripheral: Create train classes not found — {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.warn("TrainControllerPeripheral: Failed to init reflection — {}", e.getMessage());
        }
    }
}
