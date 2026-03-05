package com.apocscode.logiclink.peripheral;

import com.apocscode.logiclink.LogicLink;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Server-side reader that extracts full train network topology from Create's
 * GlobalRailwayManager via reflection. Produces compact NBT data for syncing
 * to the client for CTC-style map rendering.
 *
 * Data extracted:
 * - Track graph topology (nodes + edges with geometry)
 * - Station positions and state (present/imminent trains)
 * - Signal boundaries and state (green/red/yellow per side)
 * - Train positions, speed, heading, and navigation paths
 * - Observer positions and activated state
 * - Signal group occupancy (which track segments have trains)
 *
 * All coordinate data is projected to X-Z plane for 2D map display.
 */
public class TrainNetworkDataReader {

    // ==================== Limits ====================
    public static final int MAX_NODES = 16384;
    public static final int MAX_EDGES = 16384;
    public static final int MAX_CURVE_SAMPLES = 8;   // bezier sample points per curved edge
    public static final int MAX_STATIONS = 512;
    public static final int MAX_SIGNALS = 1024;
    public static final int MAX_TRAINS = 256;
    public static final int MAX_OBSERVERS = 256;

    // ==================== Rescan Change Detection ====================
    // Snapshot of previous scan for detecting network changes between scans.
    // When trains/stations/layout change, Check 10 alerts to re-evaluate signals.
    private static int prevScanTrainCount = -1;
    private static float prevScanMaxTrainLength = -1;
    private static int prevScanStationCount = -1;
    private static int prevScanJunctionCount = -1;

    // ==================== Reflection State ====================
    private static boolean reflectionInit = false;
    private static boolean reflectionOK = false;

    // Create.RAILWAYS → GlobalRailwayManager
    private static Field railwaysField;

    // GlobalRailwayManager
    private static Field gmTrainsField;           // Map<UUID, Train>
    private static Field gmTrackNetworksField;    // Map<UUID, TrackGraph>
    private static Field gmSignalGroupsField;     // Map<UUID, SignalEdgeGroup>

    // TrackGraph
    private static Field graphNodesField;         // Map<TrackNodeLocation, TrackNode>
    private static Field graphConnectionsField;   // Map<TrackNode, Map<TrackNode, TrackEdge>>
    private static Method graphGetPointsMethod;   // getPoints(EdgePointType) → Collection

    // TrackNode
    private static Method nodeGetLocationMethod;  // getLocation() → TrackNodeLocation
    private static Method nodeGetNetIdMethod;     // getNetId() → int

    // TrackNodeLocation
    private static Method nodeLocGetLocationMethod;  // getLocation() → Vec3 (world coords)
    private static Method nodeLocGetDimensionMethod; // getDimension() → ResourceKey<Level>

    // TrackEdge
    private static Method edgeGetLengthMethod;    // getLength() → double
    private static Method edgeIsTurnMethod;       // isTurn() → boolean
    private static Field edgeTurnField;           // BezierConnection turn
    private static Field edgeInterDimField;       // boolean interDimensional
    private static Method edgeGetPositionMethod;  // getPosition(TrackGraph, double t) → Vec3
    private static Field edgeEdgeDataField;       // EdgeData edgeData

    // EdgeData
    private static Field edgeDataSignalGroupField;  // UUID singleSignalGroup

    // EdgePointType constants
    private static Object epTypeStation;
    private static Object epTypeSignal;
    private static Object epTypeObserver;
    private static Class<?> edgePointTypeClass;

    // GlobalStation
    private static Field stationNameField;
    private static Method stationGetBlockEntityPosMethod;
    private static Method stationGetPresentTrainMethod;
    private static Method stationGetImminentTrainMethod;

    // TrackEdgePoint (base class for station/signal/observer)
    private static Field edgePointEdgeLocationField;  // Couple<TrackNodeLocation>
    private static Field edgePointPositionField;      // double position

    // SignalBoundary
    private static Field signalCachedStatesField;  // Couple<SignalState>
    private static Field signalTypesField;         // Couple<SignalType>
    private static Field signalBlockEntitiesField; // Couple<Map<BlockPos, Boolean>>

    // SignalEdgeGroup
    private static Field segTrainsField;     // Set<Train> trains
    private static Field segColorField;      // EdgeGroupColor color

    // Train
    private static Field trainNameField;
    private static Field trainIdField;
    private static Field trainSpeedField;
    private static Field trainDerailedField;
    private static Field trainCarriagesField;
    private static Field trainNavigationField;
    private static Field trainCurrentStationField;
    private static Field trainOccupiedSignalBlocksField;
    private static Field trainGraphField;
    private static Method trainGetPositionInDimMethod;
    private static Field trainRuntimeField;

    // Carriage (for actual train length measurement)
    private static Field carriageBogeySpacingField;  // int bogeySpacing
    private static Method runtimeGetScheduleMethod;
    private static Field runtimePausedField;
    private static Field runtimeCompletedField;
    private static Field runtimeCurrentEntryField;
    private static Field runtimeCurrentTitleField;
    private static Field runtimeStateField;
    private static Field scheduleEntriesField;
    private static Field scheduleCyclicField;

    // Navigation
    private static Field navDestinationField;
    private static Field navDistanceField;
    private static Field navCurrentPathField;  // List<Couple<TrackNode>>
    private static Method navIsActiveMethod;
    private static Field navWaitingForSignalField;  // Couple<UUID> — signal group train is waiting on
    private static Field navTicksWaitingField;      // int — ticks spent waiting at signal

    // ScheduleEntry — for iterating full schedule destinations
    private static Field scheduleEntryInstructionField;  // ScheduleInstruction instruction
    private static Field instructionDataField;           // CompoundTag data (contains "Text" = destination)

    // SignalBoundary — per-side signal group linkage
    private static Field signalBoundaryGroupsField;  // Couple<UUID> groups

    // TrackObserver
    private static Method observerIsActivatedMethod;
    private static Method observerGetBlockEntityPosMethod;
    private static Method observerGetFilterMethod;

    // Couple
    private static Method coupleGetFirstMethod;
    private static Method coupleGetSecondMethod;

    // Component
    private static Method componentGetStringMethod;

    // ==================== Public API ====================

    /**
     * Read the full network topology and return it as a CompoundTag
     * suitable for syncing to the client. Only reads data for the given
     * dimension (typically the dimension the monitor is in).
     *
     * @param level The server level (used for dimension filtering)
     * @return CompoundTag with all map data, or empty tag on failure
     */
    public static CompoundTag readNetworkMap(@Nullable Level level) {
        CompoundTag mapData = new CompoundTag();

        if (!ensureReflection()) {
            LogicLink.LOGGER.warn("TrainNetworkDataReader: Reflection not ready (init={}, ok={})",
                    reflectionInit, reflectionOK);
            return mapData;
        }

        try {
            Object manager = railwaysField.get(null);
            if (manager == null) {
                LogicLink.LOGGER.warn("TrainNetworkDataReader: Create.RAILWAYS is null");
                return mapData;
            }

            // We need to know which dimension to filter to
            String dimFilter = level != null
                    ? level.dimension().location().toString()
                    : "minecraft:overworld";
            mapData.putString("Dimension", dimFilter);

            // Read all graphs — build unified node index + edge list
            @SuppressWarnings("unchecked")
            Map<UUID, Object> graphs = (Map<UUID, Object>) gmTrackNetworksField.get(manager);
            if (graphs == null || graphs.isEmpty()) {
                LogicLink.LOGGER.warn("TrainNetworkDataReader: No track graphs found (graphs={})",
                        graphs == null ? "null" : "empty");
                return mapData;
            }
            LogicLink.LOGGER.info("TrainNetworkDataReader: Found {} track graphs, dimFilter={}",
                    graphs.size(), dimFilter);

            // Node map: world Vec3 → sequential int ID (for compact edge references)
            Map<String, Integer> nodeIdMap = new LinkedHashMap<>();
            ListTag nodeList = new ListTag();
            ListTag edgeList = new ListTag();
            ListTag curveList = new ListTag();

            // Signal group occupancy: groupUUID → occupied?
            Map<String, Boolean> signalGroupOccupied = new HashMap<>();
            readSignalGroupOccupancy(manager, signalGroupOccupied);

            for (Object graph : graphs.values()) {
                readGraph(graph, dimFilter, nodeIdMap, nodeList, edgeList,
                        curveList, signalGroupOccupied);
            }

            mapData.put("Nodes", nodeList);
            mapData.put("Edges", edgeList);
            mapData.put("Curves", curveList);

            // Compute bounds from nodes
            if (!nodeList.isEmpty()) {
                float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
                float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
                for (int i = 0; i < nodeList.size(); i++) {
                    CompoundTag n = nodeList.getCompound(i);
                    float nx = n.getFloat("x");
                    float nz = n.getFloat("z");
                    if (nx < minX) minX = nx;
                    if (nx > maxX) maxX = nx;
                    if (nz < minZ) minZ = nz;
                    if (nz > maxZ) maxZ = nz;
                }
                CompoundTag bounds = new CompoundTag();
                bounds.putFloat("minX", minX);
                bounds.putFloat("maxX", maxX);
                bounds.putFloat("minZ", minZ);
                bounds.putFloat("maxZ", maxZ);
                mapData.put("Bounds", bounds);
            }

            // Read stations, signals, observers, trains
            readAllStations(graphs, dimFilter, nodeIdMap, mapData);
            readAllSignals(graphs, dimFilter, nodeIdMap, mapData);
            readAllObservers(graphs, dimFilter, nodeIdMap, mapData);
            readAllTrains(manager, dimFilter, nodeIdMap, mapData);

            // Analyze signal coverage at junctions and detect stuck trains
            analyzeDiagnostics(mapData);

            int diagCount = mapData.contains("Diagnostics") ? mapData.getList("Diagnostics", 10).size() : 0;
            int sigCount = mapData.contains("Signals") ? mapData.getList("Signals", 10).size() : 0;
            int staCount = mapData.contains("Stations") ? mapData.getList("Stations", 10).size() : 0;
            int trnCount = mapData.contains("Trains") ? mapData.getList("Trains", 10).size() : 0;
            int obsCount = mapData.contains("Observers") ? mapData.getList("Observers", 10).size() : 0;
            LogicLink.LOGGER.info("TrainNetworkDataReader: Result -- {} nodes, {} edges, {} curves, {} stations, {} signals, {} trains, {} observers, {} diagnostics",
                    nodeList.size(), edgeList.size(), curveList.size(),
                    staCount, sigCount, trnCount, obsCount, diagCount);

            // Log each diagnostic detail at INFO level for debugging
            if (diagCount > 0) {
                ListTag diagList = mapData.getList("Diagnostics", 10);
                for (int di = 0; di < diagList.size(); di++) {
                    CompoundTag d = diagList.getCompound(di);
                    LogicLink.LOGGER.info("  Diagnostic #{}: type={}, severity={}, pos=({},{},{}), desc={}",
                            di + 1,
                            d.getString("type"),
                            d.getString("severity"),
                            String.format("%.1f", d.getFloat("x")),
                            String.format("%.1f", d.getFloat("y")),
                            String.format("%.1f", d.getFloat("z")),
                            d.getString("desc"));
                }
            }

            // Cap warnings
            if (nodeList.size() >= MAX_NODES) {
                LogicLink.LOGGER.warn("TrainNetworkDataReader: Node cap hit! {} nodes, max is {}. Track topology may be incomplete.", nodeList.size(), MAX_NODES);
            }
            if (edgeList.size() >= MAX_EDGES) {
                LogicLink.LOGGER.warn("TrainNetworkDataReader: Edge cap hit! {} edges, max is {}. Track topology may be incomplete.", edgeList.size(), MAX_EDGES);
            }
            if (staCount >= MAX_STATIONS) {
                LogicLink.LOGGER.warn("TrainNetworkDataReader: Station cap hit! {} stations, max is {}. Some stations may be missing.", staCount, MAX_STATIONS);
            }
            if (sigCount >= MAX_SIGNALS) {
                LogicLink.LOGGER.warn("TrainNetworkDataReader: Signal cap hit! {} signals, max is {}. Some signals may be missing from diagnostics.", sigCount, MAX_SIGNALS);
            }
            if (trnCount >= MAX_TRAINS) {
                LogicLink.LOGGER.warn("TrainNetworkDataReader: Train cap hit! {} trains, max is {}. Some trains may be missing.", trnCount, MAX_TRAINS);
            }
            if (obsCount >= MAX_OBSERVERS) {
                LogicLink.LOGGER.warn("TrainNetworkDataReader: Observer cap hit! {} observers, max is {}. Some observers may be missing.", obsCount, MAX_OBSERVERS);
            }

        } catch (Exception e) {
            LogicLink.LOGGER.warn("TrainNetworkDataReader: Failed to read map data: {}", e.getMessage(), e);
        }

        return mapData;
    }

    /**
     * Lightweight scan that returns only diagnostic information for the Signal Tablet.
     * Performs a full network scan but packages only the diagnostics + summary into
     * a CompoundTag suitable for storing in item NBT.
     *
     * @param level The server level
     * @return CompoundTag with diagnostics, or null if no network found
     */
    public static CompoundTag scanDiagnosticsOnly(@Nullable Level level) {
        CompoundTag fullMap = readNetworkMap(level);
        if (fullMap == null || fullMap.isEmpty()) return null;

        CompoundTag result = new CompoundTag();

        // Copy diagnostics
        if (fullMap.contains("Diagnostics")) {
            ListTag diags = fullMap.getList("Diagnostics", 10);
            result.put("Diagnostics", diags.copy());
            result.putInt("issueCount", diags.size());

            // Count by type
            int junctionCount = 0, noPathCount = 0, conflictCount = 0, pausedCount = 0, doneCount = 0;
            int invalidDestCount = 0, signalBlockedCount = 0, deadlockCount = 0;
            int routeUnsignaledCount = 0, unreachableCount = 0, missingRegularCount = 0;
            int networkChangedCount = 0, chainCorridorCount = 0;
            for (int i = 0; i < diags.size(); i++) {
                String type = diags.getCompound(i).getString("type");
                switch (type) {
                    case "JUNCTION_UNSIGNALED" -> junctionCount++;
                    case "NO_PATH" -> noPathCount++;
                    case "SIGNAL_CONFLICT" -> conflictCount++;
                    case "TRAIN_PAUSED" -> pausedCount++;
                    case "SCHEDULE_DONE" -> doneCount++;
                    case "SCHEDULE_INVALID_DEST" -> invalidDestCount++;
                    case "TRAIN_SIGNAL_BLOCKED" -> signalBlockedCount++;
                    case "DEADLOCK" -> deadlockCount++;
                    case "ROUTE_UNSIGNALED" -> routeUnsignaledCount++;
                    case "STATION_UNREACHABLE" -> unreachableCount++;
                    case "MISSING_REGULAR_SIGNAL" -> missingRegularCount++;
                    case "NETWORK_CHANGED" -> networkChangedCount++;
                    case "CHAIN_ONLY_CORRIDOR" -> chainCorridorCount++;
                }
            }
            result.putInt("junctionCount", junctionCount);
            result.putInt("noPathCount", noPathCount);
            result.putInt("conflictCount", conflictCount);
            result.putInt("pausedCount", pausedCount);
            result.putInt("doneCount", doneCount);
            result.putInt("invalidDestCount", invalidDestCount);
            result.putInt("signalBlockedCount", signalBlockedCount);
            result.putInt("deadlockCount", deadlockCount);
            result.putInt("routeUnsignaledCount", routeUnsignaledCount);
            result.putInt("unreachableCount", unreachableCount);
            result.putInt("missingRegularCount", missingRegularCount);
            result.putInt("networkChangedCount", networkChangedCount);
            result.putInt("chainCorridorCount", chainCorridorCount);
        } else {
            result.putInt("issueCount", 0);
        }

        // Copy summary stats
        if (fullMap.contains("MaxTrainLength")) result.putFloat("MaxTrainLength", fullMap.getFloat("MaxTrainLength"));
        if (fullMap.contains("MaxCarriages")) result.putInt("MaxCarriages", fullMap.getInt("MaxCarriages"));

        // Copy signal list for reference
        if (fullMap.contains("Signals")) {
            result.putInt("signalCount", fullMap.getList("Signals", 10).size());
        }
        if (fullMap.contains("Stations")) {
            result.putInt("stationCount", fullMap.getList("Stations", 10).size());
        }
        if (fullMap.contains("Trains")) {
            result.putInt("trainCount", fullMap.getList("Trains", 10).size());
        }

        int nodeCount = fullMap.contains("Nodes") ? fullMap.getList("Nodes", 10).size() : 0;
        int edgeCount = fullMap.contains("Edges") ? fullMap.getList("Edges", 10).size() : 0;
        result.putInt("nodeCount", nodeCount);
        result.putInt("edgeCount", edgeCount);

        return result;
    }

    // ==================== Graph Topology ====================

    @SuppressWarnings("unchecked")
    private static void readGraph(Object graph, String dimFilter,
                                   Map<String, Integer> nodeIdMap,
                                   ListTag nodeList, ListTag edgeList,
                                   ListTag curveList,
                                   Map<String, Boolean> signalGroupOccupied) {
        try {
            Map<?, ?> connections = (Map<?, ?>) graphConnectionsField.get(graph);
            if (connections == null) return;

            // First pass: collect all nodes that are in our dimension
            Set<Object> dimNodes = new HashSet<>();
            Map<?, ?> nodeMap = (Map<?, ?>) graphNodesField.get(graph);
            if (nodeMap == null) return;

            for (Object node : nodeMap.values()) {
                Vec3 worldPos = getNodeWorldPos(node);
                String dim = getNodeDimension(node);
                if (worldPos == null || dim == null) continue;
                if (!dim.equals(dimFilter)) continue;

                dimNodes.add(node);
                String key = nodeKey(worldPos);
                if (!nodeIdMap.containsKey(key) && nodeIdMap.size() < MAX_NODES) {
                    int id = nodeIdMap.size();
                    nodeIdMap.put(key, id);

                    CompoundTag nodeTag = new CompoundTag();
                    nodeTag.putInt("id", id);
                    nodeTag.putFloat("x", (float) worldPos.x);
                    nodeTag.putFloat("y", (float) worldPos.y);
                    nodeTag.putFloat("z", (float) worldPos.z);
                    nodeList.add(nodeTag);
                }
            }

            // Second pass: edges between dimension-local nodes
            Set<String> edgesSeen = new HashSet<>();
            boolean edgeLimitHit = false;

            for (Map.Entry<?, ?> entry : ((Map<Object, Object>) connections).entrySet()) {
                Object fromNode = entry.getKey();
                if (edgeLimitHit) break;
                if (!dimNodes.contains(fromNode)) continue;

                Vec3 fromPos = getNodeWorldPos(fromNode);
                if (fromPos == null) continue;
                String fromKey = nodeKey(fromPos);
                Integer fromId = nodeIdMap.get(fromKey);
                if (fromId == null) continue;

                Map<?, ?> toMap = (Map<?, ?>) entry.getValue();
                if (toMap == null) continue;

                for (Map.Entry<?, ?> toEntry : ((Map<Object, Object>) toMap).entrySet()) {
                    Object toNode = toEntry.getKey();
                    Object edge = toEntry.getValue();
                    if (!dimNodes.contains(toNode)) continue;

                    Vec3 toPos = getNodeWorldPos(toNode);
                    if (toPos == null) continue;
                    String toKey = nodeKey(toPos);
                    Integer toId = nodeIdMap.get(toKey);
                    if (toId == null) continue;

                    // Deduplicate (A→B same as B→A)
                    String edgeKey = Math.min(fromId, toId) + ":" + Math.max(fromId, toId);
                    if (edgesSeen.contains(edgeKey)) continue;
                    edgesSeen.add(edgeKey);

                    if (edgeList.size() >= MAX_EDGES) {
                        edgeLimitHit = true;
                        break;
                    }

                    CompoundTag edgeTag = new CompoundTag();
                    edgeTag.putInt("a", fromId);
                    edgeTag.putInt("b", toId);

                    // Length
                    double length = (double) edgeGetLengthMethod.invoke(edge);
                    edgeTag.putFloat("len", (float) length);

                    // Curved?
                    boolean curved = (boolean) edgeIsTurnMethod.invoke(edge);
                    edgeTag.putBoolean("curved", curved);

                    // Inter-dimensional?
                    boolean interDim = edgeInterDimField.getBoolean(edge);
                    edgeTag.putBoolean("interDim", interDim);

                    // Signal group on this edge → occupied?
                    boolean occupied = false;
                    try {
                        Object edgeData = edgeEdgeDataField.get(edge);
                        if (edgeData != null) {
                            Object groupUUID = edgeDataSignalGroupField.get(edgeData);
                            if (groupUUID != null) {
                                String groupId = groupUUID.toString();
                                edgeTag.putString("signalGroup", groupId);
                                occupied = signalGroupOccupied.getOrDefault(groupId, false);
                            }
                        }
                    } catch (Exception ignored) {}
                    edgeTag.putBoolean("occupied", occupied);

                    edgeList.add(edgeTag);

                    // If curved, sample bezier points for smooth rendering
                    if (curved && curveList.size() < MAX_EDGES) {
                        try {
                            CompoundTag curveSamples = new CompoundTag();
                            curveSamples.putInt("edge", edgeList.size() - 1);
                            ListTag points = new ListTag();

                            for (int s = 1; s < MAX_CURVE_SAMPLES; s++) {
                                double t = (double) s / MAX_CURVE_SAMPLES;
                                Vec3 pos = (Vec3) edgeGetPositionMethod.invoke(edge, graph, t);
                                if (pos != null) {
                                    CompoundTag pt = new CompoundTag();
                                    pt.putFloat("x", (float) pos.x);
                                    pt.putFloat("z", (float) pos.z);
                                    points.add(pt);
                                }
                            }
                            curveSamples.put("pts", points);
                            curveList.add(curveSamples);
                        } catch (Exception ignored) {}
                    }
                }
            }

        } catch (Exception e) {
            LogicLink.LOGGER.debug("TrainNetworkDataReader: Failed to read graph: {}", e.getMessage());
        }
    }

    // ==================== Station Data ====================

    @SuppressWarnings("unchecked")
    private static void readAllStations(Map<UUID, Object> graphs, String dimFilter,
                                         Map<String, Integer> nodeIdMap,
                                         CompoundTag mapData) {
        ListTag stationList = new ListTag();

        try {
            for (Object graph : graphs.values()) {
                Collection<?> stations = (Collection<?>) graphGetPointsMethod.invoke(graph, epTypeStation);
                if (stations == null) continue;

                for (Object station : stations) {
                    if (stationList.size() >= MAX_STATIONS) break;

                    try {
                        // Check dimension
                        BlockPos pos = (BlockPos) stationGetBlockEntityPosMethod.invoke(station);
                        // We include all stations — position check is best-effort

                        CompoundTag tag = new CompoundTag();
                        String name = (String) stationNameField.get(station);
                        tag.putString("name", name != null ? name : "Unknown");

                        if (pos != null) {
                            tag.putFloat("x", pos.getX());
                            tag.putFloat("z", pos.getZ());
                            tag.putFloat("y", pos.getY());
                        }

                        // Edge location for precise map placement
                        try {
                            Object edgeLoc = edgePointEdgeLocationField.get(station);
                            double edgePos = edgePointPositionField.getDouble(station);
                            tag.putFloat("edgePos", (float) edgePos);
                            if (edgeLoc != null) {
                                Object locA = coupleGetFirstMethod.invoke(edgeLoc);
                                Object locB = coupleGetSecondMethod.invoke(edgeLoc);
                                if (locA != null && locB != null) {
                                    Vec3 posA = (Vec3) nodeLocGetLocationMethod.invoke(locA);
                                    Vec3 posB = (Vec3) nodeLocGetLocationMethod.invoke(locB);
                                    if (posA != null && posB != null) {
                                        // Interpolate exact position on edge
                                        // edgePos is absolute distance, need to normalize
                                        double dx = posB.x - posA.x;
                                        double dz = posB.z - posA.z;
                                        double edgeLen = Math.sqrt(dx * dx + dz * dz);
                                        if (edgeLen > 0.01) {
                                            double t = Math.min(1.0, edgePos / edgeLen);
                                            tag.putFloat("mapX", (float)(posA.x + dx * t));
                                            tag.putFloat("mapZ", (float)(posA.z + dz * t));
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignored) {}

                        // Present/imminent train
                        Object present = stationGetPresentTrainMethod.invoke(station);
                        if (present != null) {
                            tag.putString("trainPresent", getTrainNameStr(present));
                        }
                        Object imminent = stationGetImminentTrainMethod.invoke(station);
                        if (imminent != null) {
                            tag.putString("trainImminent", getTrainNameStr(imminent));
                        }

                        stationList.add(tag);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            LogicLink.LOGGER.debug("TrainNetworkDataReader: Failed to read stations: {}", e.getMessage());
        }

        mapData.put("Stations", stationList);
    }

    // ==================== Signal Data ====================

    @SuppressWarnings("unchecked")
    private static void readAllSignals(Map<UUID, Object> graphs, String dimFilter,
                                        Map<String, Integer> nodeIdMap,
                                        CompoundTag mapData) {
        ListTag signalList = new ListTag();

        try {
            for (Object graph : graphs.values()) {
                Collection<?> signals = (Collection<?>) graphGetPointsMethod.invoke(graph, epTypeSignal);
                if (signals == null) continue;

                for (Object signal : signals) {
                    if (signalList.size() >= MAX_SIGNALS) break;

                    try {
                        CompoundTag tag = new CompoundTag();

                        // States (Couple<SignalState>)
                        Object statesCouple = signalCachedStatesField.get(signal);
                        if (statesCouple != null) {
                            Object sf = coupleGetFirstMethod.invoke(statesCouple);
                            Object sb = coupleGetSecondMethod.invoke(statesCouple);
                            tag.putString("stateF", sf != null ? sf.toString().toLowerCase() : "unknown");
                            tag.putString("stateB", sb != null ? sb.toString().toLowerCase() : "unknown");
                        }

                        // Types
                        Object typesCouple = signalTypesField.get(signal);
                        if (typesCouple != null) {
                            Object tf = coupleGetFirstMethod.invoke(typesCouple);
                            Object tb = coupleGetSecondMethod.invoke(typesCouple);
                            tag.putString("typeF", tf != null ? tf.toString().toLowerCase() : "entry_signal");
                            tag.putString("typeB", tb != null ? tb.toString().toLowerCase() : "entry_signal");
                        }

                        // Block entity positions (for map placement)
                        Object beCouple = signalBlockEntitiesField.get(signal);
                        if (beCouple != null) {
                            Object first = coupleGetFirstMethod.invoke(beCouple);
                            if (first instanceof Map<?, ?> posMap && !posMap.isEmpty()) {
                                BlockPos bp = (BlockPos) posMap.keySet().iterator().next();
                                tag.putFloat("x", bp.getX());
                                tag.putFloat("y", bp.getY());
                                tag.putFloat("z", bp.getZ());
                            }
                        }

                        // Edge location for precise placement
                        try {
                            Object edgeLoc = edgePointEdgeLocationField.get(signal);
                            if (edgeLoc != null) {
                                Object locA = coupleGetFirstMethod.invoke(edgeLoc);
                                Object locB = coupleGetSecondMethod.invoke(edgeLoc);
                                double edgePos = edgePointPositionField.getDouble(signal);
                                if (locA != null && locB != null) {
                                    Vec3 posA = (Vec3) nodeLocGetLocationMethod.invoke(locA);
                                    Vec3 posB = (Vec3) nodeLocGetLocationMethod.invoke(locB);
                                    if (posA != null && posB != null) {
                                        double dx = posB.x - posA.x;
                                        double dz = posB.z - posA.z;
                                        double edgeLen = Math.sqrt(dx * dx + dz * dz);
                                        if (edgeLen > 0.01) {
                                            double t = Math.min(1.0, edgePos / edgeLen);
                                            tag.putFloat("mapX", (float)(posA.x + dx * t));
                                            tag.putFloat("mapZ", (float)(posA.z + dz * t));
                                            // Normal direction for signal orientation
                                            tag.putFloat("dirX", (float)(dx / edgeLen));
                                            tag.putFloat("dirZ", (float)(dz / edgeLen));
                                        }
                                        // Map signal to edge node IDs for junction diagnostics
                                        Integer idA = nodeIdMap.get(nodeKey(posA));
                                        Integer idB = nodeIdMap.get(nodeKey(posB));
                                        if (idA != null && idB != null) {
                                            tag.putInt("edgeA", idA);
                                            tag.putInt("edgeB", idB);
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignored) {}

                        // Fallback: if block entity position is (0,0,0) but we have
                        // edge-interpolated mapX/mapZ, use those as the position.
                        // This happens when the signal's chunk isn't loaded.
                        if (tag.getFloat("x") == 0 && tag.getFloat("z") == 0
                                && tag.contains("mapX") && tag.contains("mapZ")) {
                            tag.putFloat("x", tag.getFloat("mapX"));
                            tag.putFloat("z", tag.getFloat("mapZ"));
                            // Y is not available from edge interpolation, estimate from edge nodes
                            if (tag.contains("edgeA") && tag.contains("edgeB")) {
                                ListTag nodesList = mapData.contains("Nodes") ? mapData.getList("Nodes", 10) : null;
                                if (nodesList != null) {
                                    int eA = tag.getInt("edgeA");
                                    int eB = tag.getInt("edgeB");
                                    if (eA < nodesList.size() && eB < nodesList.size()) {
                                        float yA = nodesList.getCompound(eA).getFloat("y");
                                        float yB = nodesList.getCompound(eB).getFloat("y");
                                        tag.putFloat("y", (yA + yB) / 2.0f);
                                    }
                                }
                            }
                        }

                        // Log each signal at INFO level for debugging
                        LogicLink.LOGGER.info("  Signal#{} pos=({},{},{}) typeF='{}' typeB='{}' edgeA={} edgeB={}",
                                signalList.size(),
                                String.format("%.1f", tag.getFloat("x")),
                                String.format("%.1f", tag.getFloat("y")),
                                String.format("%.1f", tag.getFloat("z")),
                                tag.contains("typeF") ? tag.getString("typeF") : "MISSING",
                                tag.contains("typeB") ? tag.getString("typeB") : "MISSING",
                                tag.contains("edgeA") ? tag.getInt("edgeA") : -1,
                                tag.contains("edgeB") ? tag.getInt("edgeB") : -1);

                        signalList.add(tag);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            LogicLink.LOGGER.debug("TrainNetworkDataReader: Failed to read signals: {}", e.getMessage());
        }

        mapData.put("Signals", signalList);
    }

    // ==================== Observer Data ====================

    @SuppressWarnings("unchecked")
    private static void readAllObservers(Map<UUID, Object> graphs, String dimFilter,
                                          Map<String, Integer> nodeIdMap,
                                          CompoundTag mapData) {
        ListTag observerList = new ListTag();

        try {
            if (epTypeObserver == null) {
                mapData.put("Observers", observerList);
                return;
            }

            for (Object graph : graphs.values()) {
                Collection<?> observers = (Collection<?>) graphGetPointsMethod.invoke(graph, epTypeObserver);
                if (observers == null) continue;

                for (Object observer : observers) {
                    if (observerList.size() >= MAX_OBSERVERS) break;

                    try {
                        CompoundTag tag = new CompoundTag();
                        boolean activated = (boolean) observerIsActivatedMethod.invoke(observer);
                        tag.putBoolean("activated", activated);

                        BlockPos pos = (BlockPos) observerGetBlockEntityPosMethod.invoke(observer);
                        if (pos != null) {
                            tag.putFloat("x", pos.getX());
                            tag.putFloat("z", pos.getZ());
                        }

                        observerList.add(tag);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            LogicLink.LOGGER.debug("TrainNetworkDataReader: Failed to read observers: {}", e.getMessage());
        }

        mapData.put("Observers", observerList);
    }

    // ==================== Train Position Data ====================

    @SuppressWarnings("unchecked")
    private static void readAllTrains(Object manager, String dimFilter,
                                       Map<String, Integer> nodeIdMap,
                                       CompoundTag mapData) {
        ListTag trainList = new ListTag();

        try {
            Map<UUID, Object> trains = (Map<UUID, Object>) gmTrainsField.get(manager);
            if (trains == null) {
                LogicLink.LOGGER.info("[LogicLink] readAllTrains: gmTrainsField returned null");
                mapData.put("Trains", trainList);
                return;
            }
            LogicLink.LOGGER.info("[LogicLink] readAllTrains: Found {} train objects, dimFilter='{}'",
                    trains.size(), dimFilter);

            for (Object train : trains.values()) {
                if (trainList.size() >= MAX_TRAINS) break;

                try {
                    CompoundTag tag = new CompoundTag();

                    // Name
                    tag.putString("name", getTrainNameStr(train));

                    // ID
                    UUID id = (UUID) trainIdField.get(train);
                    if (id != null) tag.putString("id", id.toString().substring(0, 8));

                    // Speed
                    double speed = trainSpeedField.getDouble(train);
                    tag.putDouble("speed", speed);

                    // Derailed
                    boolean derailed = trainDerailedField.getBoolean(train);
                    tag.putBoolean("derailed", derailed);

                    // Carriages — compute actual train length from bogey spacing
                    List<?> carriages = (List<?>) trainCarriagesField.get(train);
                    int carriageCount = carriages != null ? carriages.size() : 0;
                    tag.putInt("carriages", carriageCount);

                    // Measure actual train length from Carriage.bogeySpacing
                    float trainLength = 0;
                    StringBuilder spacingDebug = new StringBuilder();
                    if (carriages != null && carriageBogeySpacingField != null) {
                        for (Object carriage : carriages) {
                            try {
                                int spacing = carriageBogeySpacingField.getInt(carriage);
                                if (spacingDebug.length() > 0) spacingDebug.append("+");
                                spacingDebug.append(spacing);
                                // Single-bogey carriages have spacing=0, occupy ~3 blocks
                                trainLength += Math.max(spacing, 3);
                            } catch (Exception e) {
                                trainLength += 5; // fallback per carriage
                                spacingDebug.append("ERR");
                            }
                        }
                        // Add 1-block buffer for leading/trailing overhang
                        trainLength += 1;
                    } else {
                        // Fallback heuristic if reflection unavailable
                        trainLength = carriageCount * 5.0f + 3.0f;
                    }
                    tag.putFloat("trainLength", trainLength);
                    String tName = getTrainNameStr(train);
                    LogicLink.LOGGER.debug("[LogicLink] Train '{}': {} carriages, bogeySpacings=[{}], length={}",
                            tName, carriageCount, spacingDebug, (int) trainLength);

                    // Current station — may be a GlobalStation object or a UUID reference
                    try {
                        Object curStation = trainCurrentStationField.get(train);
                        if (curStation != null) {
                            if (curStation instanceof UUID) {
                                // Create 1.21.1 stores UUID reference; use toString for now
                                tag.putString("currentStationId", curStation.toString().substring(0, 8));
                            } else {
                                // Direct GlobalStation object
                                String stName = (String) stationNameField.get(curStation);
                                if (stName != null) tag.putString("currentStation", stName);
                            }
                        }
                    } catch (Exception e) {
                        LogicLink.LOGGER.debug("[LogicLink] Train station lookup failed: {}", e.getMessage());
                    }

                    // World position — try to get for the target dimension
                    boolean posFound = false;
                    String trainName = getTrainNameStr(train);
                    if (trainGetPositionInDimMethod != null) {
                        try {
                            // Direct approach: iterate known dimension keys
                            Method getPresentDims = train.getClass().getMethod("getPresentDimensions");
                            List<?> dims = (List<?>) getPresentDims.invoke(train);
                            if (dims != null) {
                                LogicLink.LOGGER.info("[LogicLink] Train '{}': presentDimensions={}", trainName, dims);
                                for (Object dim : dims) {
                                    @SuppressWarnings("unchecked")
                                    ResourceKey<Level> dimKey = (ResourceKey<Level>) dim;
                                    if (dimKey.location().toString().equals(dimFilter)) {
                                        Optional<BlockPos> posOpt =
                                                (Optional<BlockPos>) trainGetPositionInDimMethod.invoke(train, dimKey);
                                        if (posOpt != null && posOpt.isPresent()) {
                                            BlockPos pos = posOpt.get();
                                            tag.putFloat("x", pos.getX());
                                            tag.putFloat("z", pos.getZ());
                                            tag.putFloat("y", pos.getY());
                                            posFound = true;
                                            LogicLink.LOGGER.info("[LogicLink] Train '{}': position=({},{},{})",
                                                    trainName, pos.getX(), pos.getY(), pos.getZ());
                                            break;
                                        } else {
                                            LogicLink.LOGGER.info("[LogicLink] Train '{}': getPositionInDimension returned empty for '{}'",
                                                    trainName, dimFilter);
                                        }
                                    }
                                }
                            } else {
                                LogicLink.LOGGER.info("[LogicLink] Train '{}': getPresentDimensions returned null", trainName);
                            }
                        } catch (NoSuchMethodException e) {
                            LogicLink.LOGGER.warn("[LogicLink] Train '{}': getPresentDimensions method not found: {}", trainName, e.getMessage());
                        } catch (Exception e) {
                            LogicLink.LOGGER.warn("[LogicLink] Train '{}': position lookup failed: {}", trainName, e.getMessage());
                        }
                    } else {
                        LogicLink.LOGGER.info("[LogicLink] Train '{}': trainGetPositionInDimMethod is null, skipping position lookup", trainName);
                    }

                    if (!posFound) {
                        LogicLink.LOGGER.info("[LogicLink] Train '{}': no position found, including anyway (derailed={})", trainName, derailed);
                    }

                    // Navigation info
                    Object nav = trainNavigationField.get(train);
                    if (nav != null) {
                        boolean active = (boolean) navIsActiveMethod.invoke(nav);
                        tag.putBoolean("navigating", active);
                        if (active) {
                            try {
                                Object dest = navDestinationField.get(nav);
                                if (dest != null) {
                                    if (dest instanceof UUID) {
                                        tag.putString("destinationId", dest.toString().substring(0, 8));
                                    } else {
                                        String destName = (String) stationNameField.get(dest);
                                        if (destName != null) tag.putString("destination", destName);
                                    }
                                }
                            } catch (Exception e) {
                                LogicLink.LOGGER.debug("[LogicLink] Nav destination lookup failed: {}", e.getMessage());
                            }
                            double dist = navDistanceField.getDouble(nav);
                            tag.putDouble("distance", dist);

                            // Navigation path (sequence of node positions for route overlay)
                            try {
                                List<?> path = (List<?>) navCurrentPathField.get(nav);
                                if (path != null && !path.isEmpty()) {
                                    ListTag pathNodes = new ListTag();
                                    int pathLimit = Math.min(path.size(), 64);
                                    for (int i = 0; i < pathLimit; i++) {
                                        Object couple = path.get(i);
                                        Object nodeA = coupleGetFirstMethod.invoke(couple);
                                        Object nodeB = coupleGetSecondMethod.invoke(couple);
                                        Vec3 posA = getNodeWorldPos(nodeA);
                                        Vec3 posB = getNodeWorldPos(nodeB);
                                        if (posA != null && posB != null) {
                                            CompoundTag seg = new CompoundTag();
                                            seg.putFloat("ax", (float) posA.x);
                                            seg.putFloat("az", (float) posA.z);
                                            seg.putFloat("bx", (float) posB.x);
                                            seg.putFloat("bz", (float) posB.z);
                                            // Include node IDs for route analysis
                                            Integer aId = nodeIdMap.get(nodeKey(posA));
                                            Integer bId = nodeIdMap.get(nodeKey(posB));
                                            if (aId != null) seg.putInt("aId", aId);
                                            if (bId != null) seg.putInt("bId", bId);
                                            pathNodes.add(seg);
                                        }
                                    }
                                    if (!pathNodes.isEmpty()) {
                                        tag.put("path", pathNodes);
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }

                    // Occupied signal blocks
                    try {
                        Map<?, ?> occupied = (Map<?, ?>) trainOccupiedSignalBlocksField.get(train);
                        if (occupied != null && !occupied.isEmpty()) {
                            ListTag groups = new ListTag();
                            for (Object key : occupied.keySet()) {
                                CompoundTag g = new CompoundTag();
                                g.putString("g", key.toString());
                                groups.add(g);
                            }
                            tag.put("occupiedGroups", groups);
                        }
                    } catch (Exception ignored) {}

                    // Signal blocking info — which signal group is this train waiting on?
                    try {
                        Object nav2 = trainNavigationField.get(train);
                        if (nav2 != null && navWaitingForSignalField != null) {
                            Object waitingCouple = navWaitingForSignalField.get(nav2);
                            if (waitingCouple != null) {
                                Object waitGroup = coupleGetFirstMethod.invoke(waitingCouple);
                                if (waitGroup != null) {
                                    tag.putString("waitingForSignal", waitGroup.toString());
                                }
                                if (navTicksWaitingField != null) {
                                    int ticks = navTicksWaitingField.getInt(nav2);
                                    if (ticks > 0) {
                                        tag.putInt("ticksWaitingForSignal", ticks);
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}

                    // Schedule info (for detailed diagnostics)
                    try {
                        Object runtime = trainRuntimeField.get(train);
                        if (runtime != null) {
                            Object schedule = runtimeGetScheduleMethod.invoke(runtime);
                            tag.putBoolean("hasSchedule", schedule != null);

                            if (schedule != null) {
                                try {
                                    tag.putBoolean("schedulePaused", runtimePausedField.getBoolean(runtime));
                                    tag.putBoolean("scheduleCompleted", runtimeCompletedField.getBoolean(runtime));
                                    tag.putInt("scheduleEntry", runtimeCurrentEntryField.getInt(runtime));

                                    Object state = runtimeStateField.get(runtime);
                                    if (state != null) tag.putString("scheduleState", state.toString().toLowerCase());

                                    String title = (String) runtimeCurrentTitleField.get(runtime);
                                    if (title != null && !title.isEmpty()) tag.putString("scheduleTitle", title);

                                    List<?> entries = (List<?>) scheduleEntriesField.get(schedule);
                                    if (entries != null) {
                                        tag.putInt("scheduleEntryCount", entries.size());
                                        tag.putBoolean("scheduleCyclic", scheduleCyclicField.getBoolean(schedule));
                                    }
                                } catch (Exception ignored2) {}

                                // Extract ALL schedule entry destinations for validation
                                try {
                                    if (scheduleEntryInstructionField != null && instructionDataField != null) {
                                        List<?> allEntries = (List<?>) scheduleEntriesField.get(schedule);
                                        if (allEntries != null) {
                                            ListTag schedDests = new ListTag();
                                            for (int ei = 0; ei < allEntries.size(); ei++) {
                                                Object entry = allEntries.get(ei);
                                                Object instr = scheduleEntryInstructionField.get(entry);
                                                if (instr == null) continue;
                                                CompoundTag instrData = (CompoundTag) instructionDataField.get(instr);
                                                if (instrData != null && instrData.contains("Text")) {
                                                    CompoundTag destTag = new CompoundTag();
                                                    destTag.putInt("step", ei);
                                                    destTag.putString("dest", instrData.getString("Text"));
                                                    schedDests.add(destTag);
                                                }
                                            }
                                            if (!schedDests.isEmpty()) {
                                                tag.put("scheduleDestinations", schedDests);
                                            }
                                        }
                                    }
                                } catch (Exception ignored3) {}
                            }
                        }
                    } catch (Exception ignored) {}

                    trainList.add(tag);
                } catch (Exception e) {
                    String tName = "unknown";
                    try { tName = getTrainNameStr(train); } catch (Exception ignored2) {}
                    LogicLink.LOGGER.error("[LogicLink] Train '{}' processing FAILED: {} at {}",
                            tName, e.getMessage(), e.getStackTrace().length > 0 ? e.getStackTrace()[0] : "??");
                }
            }
        } catch (Exception e) {
            LogicLink.LOGGER.error("TrainNetworkDataReader: Failed to read trains: {}", e.getMessage(), e);
        }

        mapData.put("Trains", trainList);
    }

    // ==================== Signal Group Occupancy ====================

    @SuppressWarnings("unchecked")
    private static void readSignalGroupOccupancy(Object manager,
                                                  Map<String, Boolean> result) {
        try {
            Map<UUID, Object> groups = (Map<UUID, Object>) gmSignalGroupsField.get(manager);
            if (groups == null) return;

            for (Map.Entry<UUID, Object> entry : groups.entrySet()) {
                try {
                    Set<?> trains = (Set<?>) segTrainsField.get(entry.getValue());
                    result.put(entry.getKey().toString(), trains != null && !trains.isEmpty());
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    // ==================== Helper Methods ====================

    private static Vec3 getNodeWorldPos(Object node) {
        try {
            Object location = nodeGetLocationMethod.invoke(node);
            if (location == null) return null;
            return (Vec3) nodeLocGetLocationMethod.invoke(location);
        } catch (Exception e) {
            return null;
        }
    }

    private static String getNodeDimension(Object node) {
        try {
            Object location = nodeGetLocationMethod.invoke(node);
            if (location == null) return null;
            @SuppressWarnings("unchecked")
            ResourceKey<Level> dim = (ResourceKey<Level>) nodeLocGetDimensionMethod.invoke(location);
            return dim != null ? dim.location().toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String nodeKey(Vec3 pos) {
        // Use 2-decimal precision for dedup (half-block positions)
        return String.format("%.2f:%.2f:%.2f", pos.x, pos.y, pos.z);
    }

    // ==================== Signal & Routing Diagnostics ====================

    /**
     * Analyze the collected network data for signal issues that could cause
     * "no path" errors in train schedules. Checks:
     * 1. Junctions (3+ way intersections) with missing signal coverage
     * 2. Trains stuck without navigation (no-path heuristic)
     * 3. Wrong-type signals at junctions (regular vs chain)
     * 4. Full schedule validation (ALL destinations checked against known stations)
     * 5. Signal-blocked train detection (using Navigation.waitingForSignal)
     * 6. Deadlock detection (mutual signal group blocking)
     * 7. Route signal coverage (unsignaled junctions along active navigation path)
     * 8. Network connectivity (BFS reachability between all stations)
     * Produces suggested signal placement coordinates for each issue.
     *
     * Signal type logic (per Create wiki):
     * - CHAIN SIGNAL: placed on junction ENTRY branches (prevents trains from
     *   blocking the junction — only enters if it can also exit)
     * - REGULAR SIGNAL: placed on junction EXIT (marks section boundary after junction)
     * - Distance from junction based on max train length so the longest train
     *   fits between signal and junction node without blocking.
     */
    private static void analyzeDiagnostics(CompoundTag mapData) {
        ListTag diagnostics = new ListTag();

        if (!mapData.contains("Nodes") || !mapData.contains("Edges")) {
            mapData.put("Diagnostics", diagnostics);
            return;
        }

        ListTag nodes = mapData.getList("Nodes", 10);
        ListTag edges = mapData.getList("Edges", 10);
        ListTag signals = mapData.contains("Signals") ? mapData.getList("Signals", 10) : new ListTag();
        ListTag trains = mapData.contains("Trains") ? mapData.getList("Trains", 10) : new ListTag();

        // Compute max train length from actual measured lengths (or fallback to heuristic)
        int maxCarriages = 1;
        float maxTrainLength = 8.0f; // minimum assumption: 1 carriage
        String maxTrainName = "";
        for (int i = 0; i < trains.size(); i++) {
            CompoundTag t = trains.getCompound(i);
            int c = t.getInt("carriages");
            if (c > maxCarriages) maxCarriages = c;
            float len = t.getFloat("trainLength");
            if (len > maxTrainLength) {
                maxTrainLength = len;
                maxTrainName = t.contains("name") ? t.getString("name") : "";
            }
        }
        mapData.putFloat("MaxTrainLength", maxTrainLength);
        mapData.putInt("MaxCarriages", maxCarriages);
        if (!maxTrainName.isEmpty()) {
            mapData.putString("MaxTrainName", maxTrainName);
        }

        // Build adjacency: nodeId -> list of {neighborId, edgeIndex}
        Map<Integer, List<int[]>> adjacency = new HashMap<>();
        for (int i = 0; i < edges.size(); i++) {
            CompoundTag edge = edges.getCompound(i);
            int a = edge.getInt("a");
            int b = edge.getInt("b");
            adjacency.computeIfAbsent(a, k -> new ArrayList<>()).add(new int[]{b, i});
            adjacency.computeIfAbsent(b, k -> new ArrayList<>()).add(new int[]{a, i});
        }

        // Build set of edges that have signals on them (by "min:max" node ID key)
        Set<String> signaledEdges = new HashSet<>();
        for (int i = 0; i < signals.size(); i++) {
            CompoundTag sig = signals.getCompound(i);
            if (sig.contains("edgeA") && sig.contains("edgeB")) {
                int a = sig.getInt("edgeA");
                int b = sig.getInt("edgeB");
                String ek = Math.min(a, b) + ":" + Math.max(a, b);
                signaledEdges.add(ek);
            }
        }
        LogicLink.LOGGER.info("Signaled edges set (direct): {}", signaledEdges);

        // Build extended set: for each junction, walk outward along each branch up to 3 hops.
        // If any edge along that path has a signal, mark the junction branch as "signaled".
        // This handles Create's intermediate graph nodes between junctions and signal blocks.
        // Key format: "junctionId:neighborId" — same as what Check 1 looks for.
        Set<String> junctionBranchSignaled = new HashSet<>();
        for (Map.Entry<Integer, List<int[]>> entry : adjacency.entrySet()) {
            List<int[]> branches = entry.getValue();
            if (branches.size() < 3) continue; // only junctions

            int jId = entry.getKey();

            for (int[] branch : branches) {
                int neighborId = branch[0];
                String branchKey = Math.min(jId, neighborId) + ":" + Math.max(jId, neighborId);

                // Check direct edge first
                if (signaledEdges.contains(branchKey)) {
                    junctionBranchSignaled.add(branchKey);
                    continue;
                }

                // Walk outward from junction along this branch: prev -> current -> next
                // Stop at other junctions or after 3 hops
                int prev = jId;
                int current = neighborId;
                boolean found = false;
                for (int hop = 0; hop < 3; hop++) {
                    List<int[]> currentBranches = adjacency.get(current);
                    if (currentBranches == null) break;

                    // If current node IS a junction, stop — that's a different junction's domain
                    if (hop > 0 && currentBranches.size() >= 3) break;

                    // Check if the edge from prev to current has a signal
                    String ek = Math.min(prev, current) + ":" + Math.max(prev, current);
                    if (signaledEdges.contains(ek)) {
                        found = true;
                        break;
                    }

                    // Walk to the next node (skip back to prev)
                    int next = -1;
                    for (int[] nb : currentBranches) {
                        if (nb[0] != prev) {
                            next = nb[0];
                            break;
                        }
                    }
                    if (next < 0) break;
                    prev = current;
                    current = next;
                }
                if (found) {
                    junctionBranchSignaled.add(branchKey);
                }
            }
        }
        LogicLink.LOGGER.info("Junction branch signaled (with walk): {}", junctionBranchSignaled);

        // === Check 1: Unsignaled junction branches ===
        // Flag diverging branches at junctions that have NO signal at all.
        // Branches that already have a signal (even wrong type) are skipped here —
        // Check 3 independently handles wrong-type signal detection.
        // This separation prevents overlap: placing a chain signal on an unsignaled
        // branch clears Check 1 cleanly without triggering Check 3.
        for (Map.Entry<Integer, List<int[]>> entry : adjacency.entrySet()) {
            List<int[]> branches = entry.getValue();
            if (branches.size() < 3) continue; // not a junction

            int jId = entry.getKey();
            if (jId >= nodes.size()) continue;
            CompoundTag jNode = nodes.getCompound(jId);
            float jx = jNode.getFloat("x");
            float jy = jNode.getFloat("y");
            float jz = jNode.getFloat("z");

            CompoundTag diag = new CompoundTag();
            diag.putString("type", "JUNCTION_UNSIGNALED");
            diag.putString("severity", "WARN");
            diag.putFloat("x", jx);
            diag.putFloat("y", jy);
            diag.putFloat("z", jz);
            diag.putInt("branches", branches.size());
            diag.putInt("unsignaled", branches.size());

            // Compute normalized direction for each branch (toward junction = entry direction)
            List<float[]> branchDirs = new ArrayList<>(); // [ndx, ndz, dist, nx, ny, nz] per branch
            List<Integer> branchNeighborIds = new ArrayList<>(); // parallel list: neighbor node ID per branch
            for (int[] branch : branches) {
                int neighborId = branch[0];
                if (neighborId >= nodes.size()) continue;
                CompoundTag neighborNode = nodes.getCompound(neighborId);
                float nx = neighborNode.getFloat("x");
                float ny = neighborNode.getFloat("y");
                float nz = neighborNode.getFloat("z");
                float ddx = jx - nx;
                float ddy = jy - ny;
                float ddz = jz - nz;
                float dist = (float) Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                if (dist < 0.01f) continue;
                branchDirs.add(new float[]{ ddx / dist, ddz / dist, dist, nx, ny, nz });
                branchNeighborIds.add(neighborId);
            }

            // Detect collinear branch pairs (through-lines) via dot product.
            // For ANY junction size (3+way), find all roughly-opposite branch pairs.
            // Uses greedy matching: most collinear pair claimed first, then next unclaimed.
            // Through-line branches do NOT need chain signals — trains flow straight
            // through without needing junction protection on the main line.
            Set<Integer> throughBranches = new HashSet<>();
            {
                float[][] dirs = branchDirs.toArray(new float[0][]);
                List<float[]> pairDots = new ArrayList<>();
                for (int pi = 0; pi < dirs.length; pi++) {
                    for (int pj = pi + 1; pj < dirs.length; pj++) {
                        float dot = dirs[pi][0] * dirs[pj][0] + dirs[pi][1] * dirs[pj][1];
                        pairDots.add(new float[]{dot, pi, pj});
                    }
                }
                pairDots.sort((a, b) -> Float.compare(a[0], b[0]));
                Set<Integer> claimed = new HashSet<>();
                for (float[] pair : pairDots) {
                    if (pair[0] >= -0.7f) break;
                    int pi = (int) pair[1];
                    int pj = (int) pair[2];
                    if (claimed.contains(pi) || claimed.contains(pj)) continue;
                    throughBranches.add(pi);
                    throughBranches.add(pj);
                    claimed.add(pi);
                    claimed.add(pj);
                }
            }

            // CROSSING DETECTION: If ALL branches are paired as through-lines,
            // this is a crossing junction (e.g. two perpendicular tracks crossing).
            // Crossings need chain signals on ALL entries — trains on one track
            // can collide with trains on the other. The through-line skip only
            // makes sense when there's a mix of through and diverging branches
            // (e.g. T-junctions where the main line goes straight through).
            if (throughBranches.size() == branchDirs.size() && branchDirs.size() >= 4) {
                LogicLink.LOGGER.info("[LogicLink]   All {} branches are through-line — treating as CROSSING (all need signals)",
                        branchDirs.size());
                throughBranches.clear();
            }

            // Signal suggestions: diverging branches need chain signals.
            // Through-line branches (straight through the junction) do NOT need
            // chain signals — adding chain on through-lines creates chain→chain
            // corridors between junctions that cause Create NO_PATH errors.
            // Exception: at crossing junctions (all-through), ALL branches need signals.
            //
            // Per branch: 1 chain signal (close to junction, facing toward junction)
            //             1 regular signal (further out, waiting/queue point)
            ListTag suggestions = new ListTag();
            LogicLink.LOGGER.info("[LogicLink] Junction node {} at ({}, {}, {}) with {} branches, {} through-line",
                    jId, Mth.floor(jx), Mth.floor(jy), Mth.floor(jz),
                    branchDirs.size(), throughBranches.size());

            for (int bi = 0; bi < branchDirs.size(); bi++) {
                float[] bd = branchDirs.get(bi);
                float ndx = bd[0], ndz = bd[1], dist = bd[2];
                float nx = bd[3], ny = bd[4], nz = bd[5];
                int neighborId2 = branchNeighborIds.get(bi);
                boolean isThrough = throughBranches.contains(bi);

                // Skip through-line branches entirely — they don't need junction signals.
                // Adding chain signals on through-lines creates chain-only corridors
                // between adjacent junctions, which breaks Create's pathfinder.
                if (isThrough) {
                    LogicLink.LOGGER.info("[LogicLink]   Branch {} to node {} ({},{},{}), dist={} — through-line, skipped",
                            bi, neighborId2, (int)nx, (int)ny, (int)nz, String.format("%.1f", dist));
                    continue;
                }

                // Place chain signal far enough from junction for train clearance.
                float idealOffset = Math.max(3.0f, maxTrainLength * 0.5f);
                float offset = Math.min(idealOffset, dist * 0.8f);
                float sugX = jx - ndx * offset;
                float sugY = jy - ((jy - ny) / dist) * offset;
                float sugZ = jz - ndz * offset;

                String cardinal = getCardinalDir(ndx, ndz);
                boolean clearanceOk = dist >= maxTrainLength;
                String clearanceNote = clearanceOk
                        ? ""
                        : " [!] Edge " + (int) dist + "b < max train " + (int) maxTrainLength + "b";

                LogicLink.LOGGER.info("[LogicLink]   Branch {} to node {} ({},{},{}), dist={} — diverging",
                        bi, neighborId2, (int)nx, (int)ny, (int)nz, String.format("%.1f", dist));

                // Skip branches that already have ANY signal (within 3 hops)
                String branchEdgeKey = Math.min(jId, neighborId2) + ":" + Math.max(jId, neighborId2);
                boolean alreadySignaled = junctionBranchSignaled.contains(branchEdgeKey);
                if (alreadySignaled) continue;

                int bsx = Mth.floor(sugX), bsy = Mth.floor(sugY), bsz = Mth.floor(sugZ);

                // === Chain signal on diverging branch entry ===
                // Prevents train from entering junction if it can't exit.
                // Place on the diverging branch, facing toward the junction.
                CompoundTag branchSug = new CompoundTag();
                branchSug.putInt("sx", bsx);
                branchSug.putInt("sy", bsy);
                branchSug.putInt("sz", bsz);
                branchSug.putString("signalType", "chain");
                branchSug.putFloat("sdx", ndx);
                branchSug.putFloat("sdz", ndz);
                branchSug.putFloat("edgeLength", dist);
                branchSug.putFloat("maxTrainLength", maxTrainLength);
                branchSug.putBoolean("clearanceWarning", !clearanceOk);
                branchSug.putString("dir", "Chain signal — diverging entry from " + cardinal + clearanceNote);

                if (!isDuplicateSuggestion(suggestions, bsx, bsy, bsz, "chain", ndx, ndz)) {
                    suggestions.add(branchSug);
                }

                // === Regular signal further out (waiting/queue point) ===
                // Creates a block section boundary where trains can stop and wait.
                // This MUST be a regular signal (not chain) to terminate chain look-ahead.
                float regularOffset = offset + Math.max(3.0f, maxTrainLength * 0.5f);
                if (regularOffset < dist * 0.95f) {
                    float regX = jx - ndx * regularOffset;
                    float regY = jy - ((jy - ny) / dist) * regularOffset;
                    float regZ = jz - ndz * regularOffset;
                    int rsx = Mth.floor(regX), rsy = Mth.floor(regY), rsz = Mth.floor(regZ);

                    if (!isDuplicateSuggestion(suggestions, rsx, rsy, rsz, "signal", ndx, ndz)) {
                        CompoundTag regSug = new CompoundTag();
                        regSug.putInt("sx", rsx);
                        regSug.putInt("sy", rsy);
                        regSug.putInt("sz", rsz);
                        regSug.putString("signalType", "signal");
                        regSug.putFloat("sdx", ndx);
                        regSug.putFloat("sdz", ndz);
                        regSug.putString("dir", "Regular signal — waiting point from " + cardinal);
                        suggestions.add(regSug);
                    }
                }
            }
            diag.put("suggestions", suggestions);

            // Skip diagnostic entirely if no suggestions remain
            if (suggestions.isEmpty()) continue;

            int unsignaledBranchCount = 0;
            for (int i = 0; i < suggestions.size(); i++) {
                if (suggestions.getCompound(i).getString("signalType").equals("chain")) {
                    unsignaledBranchCount++;
                }
            }
            String trainInfo = maxTrainName.isEmpty()
                    ? " (max train: " + (int) maxTrainLength + "b)"
                    : " (max train: " + (int) maxTrainLength + "b — '" + maxTrainName + "')";
            int divergingCount = branchDirs.size() - throughBranches.size();
            diag.putString("desc", branchDirs.size() + "-way junction: " + unsignaledBranchCount
                    + " unsignaled diverging branch(es) need chain signals (of "
                    + divergingCount + " diverging, " + throughBranches.size() / 2 + " through-line)."
                    + trainInfo);

            diagnostics.add(diag);
        }

        // === Check 2: Trains stuck without navigation (detailed diagnostics) ===
        // Build set of known station names for destination cross-referencing
        Set<String> knownStationNames = new HashSet<>();
        ListTag stationsList = mapData.contains("Stations") ? mapData.getList("Stations", 10) : new ListTag();
        for (int i = 0; i < stationsList.size(); i++) {
            String sn = stationsList.getCompound(i).getString("name");
            if (sn != null && !sn.isEmpty()) knownStationNames.add(sn);
        }

        for (int i = 0; i < trains.size(); i++) {
            CompoundTag train = trains.getCompound(i);
            if (train.getBoolean("derailed")) continue;
            if (!train.getBoolean("hasSchedule")) continue;

            double speed = Math.abs(train.getDouble("speed"));
            boolean navigating = train.getBoolean("navigating");
            boolean atStation = train.contains("currentStation");

            // Skip trains that are moving, actively navigating, or parked at a station
            if (speed > 0.01 || navigating || atStation) continue;

            // Train has schedule, not moving, not navigating, not at station — diagnose why
            String trainName = train.getString("name");
            boolean paused = train.getBoolean("schedulePaused");
            boolean completed = train.getBoolean("scheduleCompleted");
            String schedState = train.contains("scheduleState") ? train.getString("scheduleState") : "";
            String schedTitle = train.contains("scheduleTitle") ? train.getString("scheduleTitle") : "";
            int schedEntry = train.getInt("scheduleEntry");
            int schedEntryCount = train.contains("scheduleEntryCount") ? train.getInt("scheduleEntryCount") : 0;
            boolean cyclic = train.getBoolean("scheduleCyclic");

            CompoundTag diag = new CompoundTag();
            diag.putString("trainName", trainName);
            if (train.contains("x")) {
                diag.putFloat("x", train.getFloat("x"));
                diag.putFloat("y", train.contains("y") ? train.getFloat("y") : 0);
                diag.putFloat("z", train.getFloat("z"));
            }

            // Differentiate the cause
            if (paused) {
                // Schedule manually paused — informational, not an error
                diag.putString("type", "TRAIN_PAUSED");
                diag.putString("severity", "INFO");
                String stepInfo = schedEntryCount > 0
                        ? " (step " + (schedEntry + 1) + "/" + schedEntryCount + ")"
                        : "";
                diag.putString("desc", "Schedule paused" + stepInfo);
                diag.putString("detail", !schedTitle.isEmpty()
                        ? "Current step: " + schedTitle
                        : "Resume schedule to continue operation");
            } else if (completed && !cyclic) {
                // Non-cyclic schedule finished — informational
                diag.putString("type", "SCHEDULE_DONE");
                diag.putString("severity", "INFO");
                diag.putString("desc", "Schedule completed (non-cyclic, " + schedEntryCount + " steps)");
                diag.putString("detail", "Set schedule to cyclic or assign a new schedule");
            } else {
                // True stuck train — try to determine why
                diag.putString("type", "NO_PATH");
                diag.putString("severity", "CRIT");

                // Build a detailed cause description
                StringBuilder cause = new StringBuilder();
                StringBuilder detail = new StringBuilder();

                // Include all schedule destinations for debugging
                if (train.contains("scheduleDestinations")) {
                    ListTag dests = train.getList("scheduleDestinations", 10);
                    if (!dests.isEmpty()) {
                        StringBuilder destList = new StringBuilder();
                        for (int di = 0; di < dests.size(); di++) {
                            CompoundTag d = dests.getCompound(di);
                            if (destList.length() > 0) destList.append(" → ");
                            String destName = d.getString("station");
                            boolean isRegex = d.contains("isRegex") && d.getBoolean("isRegex");
                            destList.append(isRegex ? "*" + destName + "*" : destName);
                        }
                        detail.append("Route: ").append(destList);
                    }
                }

                // Check if the train's schedule title hints at a destination
                if (!schedTitle.isEmpty()) {
                    cause.append("Stuck at step: ").append(schedTitle);
                    // Check if the destination station exists
                    // schedTitle often looks like "Travel to StationName" or just the station name
                    boolean destFound = false;
                    for (String sName : knownStationNames) {
                        if (schedTitle.contains(sName)) {
                            destFound = true;
                            break;
                        }
                    }
                    if (!destFound && !knownStationNames.isEmpty()) {
                        detail.append("Destination may not exist — check station name spelling");
                    }
                } else {
                    cause.append("No navigation path found");
                }

                // Check if nearest junction has signal issues
                if (train.contains("x")) {
                    float tx = train.getFloat("x");
                    float tz = train.getFloat("z");
                    float bestDist = Float.MAX_VALUE;
                    int bestJunction = -1;
                    for (Map.Entry<Integer, List<int[]>> jEntry : adjacency.entrySet()) {
                        if (jEntry.getValue().size() < 3) continue;
                        int jId = jEntry.getKey();
                        if (jId >= nodes.size()) continue;
                        CompoundTag jNode = nodes.getCompound(jId);
                        float ddx = jNode.getFloat("x") - tx;
                        float ddz = jNode.getFloat("z") - tz;
                        float d = ddx * ddx + ddz * ddz;
                        if (d < bestDist) {
                            bestDist = d;
                            bestJunction = jId;
                        }
                    }
                    if (bestJunction >= 0 && bestDist < 10000) { // within ~100 blocks
                        CompoundTag jNode = nodes.getCompound(bestJunction);
                        float jjx = jNode.getFloat("x");
                        float jjy = jNode.getFloat("y");
                        float jjz = jNode.getFloat("z");
                        int jBranchCount = adjacency.get(bestJunction).size();

                        // Check if this junction has signals
                        int jId2 = bestJunction;
                        int jSigCount = 0;
                        for (int[] br : adjacency.get(jId2)) {
                            String ek = Math.min(jId2, br[0]) + ":" + Math.max(jId2, br[0]);
                            if (signaledEdges.contains(ek)) jSigCount++;
                        }

                        if (jSigCount == 0) {
                            if (detail.length() > 0) detail.append("; ");
                            detail.append("Nearest junction (").append(jBranchCount)
                                  .append("-way @ ").append((int) jjx).append(",").append((int) jjz)
                                  .append(") has NO signals — add chain signals");
                        } else if (jSigCount < jBranchCount) {
                            if (detail.length() > 0) detail.append("; ");
                            detail.append("Nearest junction @ ").append((int) jjx).append(",").append((int) jjz)
                                  .append(" has ").append(jSigCount).append("/").append(jBranchCount)
                                  .append(" signaled branches — check coverage");
                        }

                        // Generate per-branch suggestions for this junction
                        List<int[]> jBranches = adjacency.get(bestJunction);
                        ListTag sug = new ListTag();
                        if (jBranches != null) {
                            for (int[] br : jBranches) {
                                int nId = br[0];
                                if (nId >= nodes.size()) continue;
                                CompoundTag nNode = nodes.getCompound(nId);
                                float nnx = nNode.getFloat("x");
                                float nny = nNode.getFloat("y");
                                float nnz = nNode.getFloat("z");
                                float ddx2 = jjx - nnx;
                                float ddy2 = jjy - nny;
                                float ddz2 = jjz - nnz;
                                float d2 = (float) Math.sqrt(ddx2 * ddx2 + ddy2 * ddy2 + ddz2 * ddz2);
                                if (d2 < 0.01f) continue;
                                float bndx = ddx2 / d2;
                                float bndz = ddz2 / d2;
                                float boff = Math.min(5.0f, d2 * 0.4f);
                                CompoundTag s = new CompoundTag();
                                s.putInt("sx", Mth.floor(jjx - bndx * boff));
                                s.putInt("sy", Mth.floor(jjy - (ddy2 / d2) * boff));
                                s.putInt("sz", Mth.floor(jjz - bndz * boff));
                                s.putString("signalType", "chain");
                                s.putFloat("sdx", bndx);
                                s.putFloat("sdz", bndz);
                                s.putString("dir", "Check junction signals \u2014 entry from " + getCardinalDir(bndx, bndz));
                                sug.add(s);
                            }
                        }
                        if (sug.isEmpty()) {
                            CompoundTag s = new CompoundTag();
                            s.putInt("sx", Mth.floor(jjx));
                            s.putInt("sy", Mth.floor(jjy));
                            s.putInt("sz", Mth.floor(jjz));
                            s.putString("signalType", "chain");
                            s.putString("dir", "Check junction signals");
                            sug.add(s);
                        }
                        diag.put("suggestions", sug);
                    }
                }

                // Fallback detail if nothing specific found
                if (detail.length() == 0) {
                    detail.append("Check track connections, signal placement, and station name spelling");
                }
                // If nearest junction has full signal coverage, suggest manual restart
                if (detail.toString().contains("Route:") && !detail.toString().contains("NO signals")
                        && !detail.toString().contains("signaled branches")) {
                    detail.append("; Try: pick up train and re-place, or toggle schedule off/on");
                }

                diag.putString("desc", cause.toString());
                diag.putString("detail", detail.toString());
            }

            diagnostics.add(diag);
        }

        // === Check 3: Existing signals with wrong type (conflict/improper) ===
        // Identify junction node IDs (3+ connections)
        Set<Integer> junctionNodes = new HashSet<>();
        for (Map.Entry<Integer, List<int[]>> entry : adjacency.entrySet()) {
            if (entry.getValue().size() >= 3) {
                junctionNodes.add(entry.getKey());
            }
        }

        // Pre-scan: For each junction edge, check if ANY signal on that edge already
        // has chain on any side. If so, other signals on the same edge are
        // block-section boundaries (intentionally regular) and should NOT be
        // flagged as conflicts.
        // Direction-agnostic: Create's Couple ordering for edgeLocation vs types
        // is not guaranteed to be consistent across signals, so we cannot reliably
        // determine which "side" (F/B) physically faces which direction. Instead,
        // we check: does this signal have chain on ANY side?
        Set<String> edgeHasChainAnySide = new HashSet<>();
        for (int i = 0; i < signals.size(); i++) {
            CompoundTag sig = signals.getCompound(i);
            if (!sig.contains("edgeA") || !sig.contains("edgeB")) continue;
            int a = sig.getInt("edgeA");
            int b = sig.getInt("edgeB");
            String tF = sig.contains("typeF") ? sig.getString("typeF") : "entry_signal";
            String tB = sig.contains("typeB") ? sig.getString("typeB") : "entry_signal";
            if (tF.equals("cross_signal") || tB.equals("cross_signal")) {
                String ek = Math.min(a, b) + ":" + Math.max(a, b);
                edgeHasChainAnySide.add(ek);
            }
        }

        for (int i = 0; i < signals.size(); i++) {
            CompoundTag sig = signals.getCompound(i);
            if (!sig.contains("edgeA") || !sig.contains("edgeB")) continue;

            int edgeA = sig.getInt("edgeA");
            int edgeB = sig.getInt("edgeB");
            String typeF = sig.contains("typeF") ? sig.getString("typeF") : "entry_signal";
            String typeB = sig.contains("typeB") ? sig.getString("typeB") : "entry_signal";

            // Determine if this edge connects to a junction
            boolean aIsJunction = junctionNodes.contains(edgeA);
            boolean bIsJunction = junctionNodes.contains(edgeB);
            boolean onJunctionEdge = aIsJunction || bIsJunction;

            // Get signal world position
            float sx = sig.contains("x") ? sig.getFloat("x") : 0;
            float sy = sig.contains("y") ? sig.getFloat("y") : 0;
            float sz = sig.contains("z") ? sig.getFloat("z") : 0;
            // Use mapX/mapZ as fallback if block entity pos is (0,0,0)
            if (sx == 0 && sz == 0) {
                if (sig.contains("mapX") && sig.contains("mapZ")) {
                    sx = sig.getFloat("mapX");
                    sz = sig.getFloat("mapZ");
                } else {
                    continue; // no valid position at all
                }
            }

            if (onJunctionEdge) {
                LogicLink.LOGGER.debug("[LogicLink] Check3 Signal#{} @ ({},{},{}) edgeA={} edgeB={} typeF='{}' typeB='{}' aIsJ={} bIsJ={}",
                        i, (int) sx, (int) sy, (int) sz, edgeA, edgeB, typeF, typeB, aIsJunction, bIsJunction);
            }

            // Direction-agnostic signal conflict check.
            // Create's Couple ordering for types vs edgeLocation can vary per signal
            // depending on placement direction, so we don't assume F↔A or F↔B mapping.
            // Instead: check if the signal has chain on at least one side.
            // If yes — the signal is protecting this junction. No flag.
            // If no — both sides regular on a junction edge = conflict.

            String ek = Math.min(edgeA, edgeB) + ":" + Math.max(edgeA, edgeB);
            boolean hasAnyChain = typeF.equals("cross_signal") || typeB.equals("cross_signal");
            boolean partnerHasChain = edgeHasChainAnySide.contains(ek);

            List<String> issues = new ArrayList<>();

            // Only flag if signal has NO chain AND no other signal on this edge has chain.
            // Signals that are all-regular on a junction edge when another signal on the
            // same edge IS chain are intentional block-section boundaries.
            if (onJunctionEdge && !hasAnyChain && !partnerHasChain) {
                // Build descriptive message with junction locations
                StringBuilder msg = new StringBuilder();
                if (aIsJunction && bIsJunction) {
                    msg.append("regular signal on edge between 2 junctions — needs chain on each junction-facing side");
                } else if (aIsJunction) {
                    msg.append("regular signal should be CHAIN (protects junction at node ").append(edgeA).append(")");
                } else {
                    msg.append("regular signal should be CHAIN (protects junction at node ").append(edgeB).append(")");
                }
                msg.append(". Use wrench to toggle chain on the side facing approaching trains");
                issues.add(msg.toString());
            }

            // Note: "chain on non-junction edge" check removed — Create's graph can have
            // intermediate nodes between junctions and signals, causing false positives.
            // Chain signals on non-junction edges are harmless (just slightly conservative).

            if (!issues.isEmpty()) {
                CompoundTag diag = new CompoundTag();
                diag.putString("type", "SIGNAL_CONFLICT");
                diag.putString("severity", "CRIT");
                diag.putFloat("x", sx);
                diag.putFloat("y", sy);
                diag.putFloat("z", sz);

                StringBuilder descBuilder = new StringBuilder("Signal conflict: ");
                for (int j = 0; j < issues.size(); j++) {
                    if (j > 0) descBuilder.append("; ");
                    descBuilder.append(issues.get(j));
                }
                diag.putString("desc", descBuilder.toString());

                // Suggestion: the signal's own position, flagged as "conflict"
                ListTag suggestions = new ListTag();
                CompoundTag conflictSug = new CompoundTag();
                conflictSug.putInt("sx", (int) sx);
                conflictSug.putInt("sy", (int) sy);
                conflictSug.putInt("sz", (int) sz);
                conflictSug.putString("signalType", "conflict");
                // Copy edge direction from existing signal data for arrow rendering
                if (sig.contains("dirX") && sig.contains("dirZ")) {
                    conflictSug.putFloat("sdx", sig.getFloat("dirX"));
                    conflictSug.putFloat("sdz", sig.getFloat("dirZ"));
                } else if (edgeA < nodes.size() && edgeB < nodes.size()) {
                    // Fallback: compute direction from edge node positions
                    CompoundTag nodeA = nodes.getCompound(edgeA);
                    CompoundTag nodeB = nodes.getCompound(edgeB);
                    float fdx = nodeB.getFloat("x") - nodeA.getFloat("x");
                    float fdz = nodeB.getFloat("z") - nodeA.getFloat("z");
                    float flen = (float) Math.sqrt(fdx * fdx + fdz * fdz);
                    if (flen > 0.01f) {
                        conflictSug.putFloat("sdx", fdx / flen);
                        conflictSug.putFloat("sdz", fdz / flen);
                    }
                }

                // Determine what the correct type should be
                conflictSug.putString("correctType", "chain");
                conflictSug.putString("dir", "Use wrench to toggle chain on junction-facing side");
                suggestions.add(conflictSug);
                diag.put("suggestions", suggestions);

                diagnostics.add(diag);
            }
        }

        // === Check 4: Full Schedule Validation ===
        // Check ALL schedule entry destinations against known station names.
        // Catches misspelled or non-existent destinations BEFORE the train gets stuck.
        for (int i = 0; i < trains.size(); i++) {
            CompoundTag train = trains.getCompound(i);
            if (!train.contains("scheduleDestinations")) continue;

            ListTag dests = train.getList("scheduleDestinations", 10);
            String trainName = train.getString("name");

            for (int d = 0; d < dests.size(); d++) {
                CompoundTag destTag = dests.getCompound(d);
                String dest = destTag.getString("dest");
                int step = destTag.getInt("step");

                if (dest.isEmpty()) continue;

                // Check if destination matches any known station (supports * wildcard)
                boolean found = matchesAnyStation(dest, knownStationNames);

                if (!found) {
                    CompoundTag diag2 = new CompoundTag();
                    diag2.putString("type", "SCHEDULE_INVALID_DEST");
                    diag2.putString("severity", "CRIT");
                    diag2.putString("trainName", trainName);
                    if (train.contains("x")) {
                        diag2.putFloat("x", train.getFloat("x"));
                        diag2.putFloat("y", train.contains("y") ? train.getFloat("y") : 0);
                        diag2.putFloat("z", train.getFloat("z"));
                    }

                    // Find closest matching station name for suggestion
                    String closest = findClosestStation(dest, knownStationNames);
                    String suggestion = closest != null
                            ? " — did you mean '" + closest + "'?"
                            : " — no similar station found";

                    diag2.putString("desc", "Schedule step " + (step + 1) + ": destination '"
                            + dest + "' matches no station" + suggestion);
                    diag2.putString("detail", "Train '" + trainName
                            + "' schedule references a non-existent station. "
                            + "Check spelling or place a station with this name.");

                    diagnostics.add(diag2);
                }
            }
        }

        // === Check 5: Signal-Blocked Train Detection ===
        // Directly identify trains waiting at signal boundaries using Navigation.waitingForSignal
        // instead of the speed=0 heuristic. Includes wait duration.
        for (int i = 0; i < trains.size(); i++) {
            CompoundTag train = trains.getCompound(i);
            if (!train.contains("waitingForSignal")) continue;

            String trainName = train.getString("name");
            String signalGroup = train.getString("waitingForSignal");
            int ticksWaiting = train.contains("ticksWaitingForSignal")
                    ? train.getInt("ticksWaitingForSignal") : 0;
            float seconds = ticksWaiting / 20.0f;

            // Only flag if waiting for a meaningful duration (> 5 seconds)
            if (seconds < 5.0f) continue;

            CompoundTag diag2 = new CompoundTag();
            diag2.putString("type", "TRAIN_SIGNAL_BLOCKED");
            diag2.putString("severity", seconds > 60 ? "CRIT" : "WARN");
            diag2.putString("trainName", trainName);
            if (train.contains("x")) {
                diag2.putFloat("x", train.getFloat("x"));
                diag2.putFloat("y", train.contains("y") ? train.getFloat("y") : 0);
                diag2.putFloat("z", train.getFloat("z"));
            }

            String timeStr = seconds >= 60
                    ? String.format("%.0fm %.0fs", Math.floor(seconds / 60), seconds % 60)
                    : String.format("%.0fs", seconds);
            String groupShort = signalGroup.length() > 8
                    ? signalGroup.substring(0, 8) : signalGroup;

            diag2.putString("desc", "Blocked at signal for " + timeStr);
            diag2.putString("detail", "Train '" + trainName + "' waiting on signal group "
                    + groupShort + " — track section ahead occupied");

            diagnostics.add(diag2);
        }

        // === Check 6: Deadlock Detection ===
        // Detect mutual signal blocking: train A waits on group G1 (occupied by B),
        // train B waits on group G2 (occupied by A) → deadlock.
        Map<String, String> trainWaitsOnGroup = new HashMap<>();  // trainId → groupId
        Map<String, Set<String>> groupOccupiedByTrains = new HashMap<>();  // groupId → trainIds

        for (int i = 0; i < trains.size(); i++) {
            CompoundTag train = trains.getCompound(i);
            String trainId = train.contains("id") ? train.getString("id") : "";
            if (trainId.isEmpty()) continue;

            if (train.contains("waitingForSignal")) {
                trainWaitsOnGroup.put(trainId, train.getString("waitingForSignal"));
            }

            if (train.contains("occupiedGroups")) {
                ListTag groups = train.getList("occupiedGroups", 10);
                for (int g = 0; g < groups.size(); g++) {
                    String gId = groups.getCompound(g).getString("g");
                    groupOccupiedByTrains.computeIfAbsent(gId, k -> new HashSet<>()).add(trainId);
                }
            }
        }

        // Detect cycles
        Set<String> deadlockedTrains = new HashSet<>();
        for (Map.Entry<String, String> waiting : trainWaitsOnGroup.entrySet()) {
            String trainA = waiting.getKey();
            String groupWaited = waiting.getValue();

            Set<String> occupiers = groupOccupiedByTrains.getOrDefault(groupWaited, Collections.emptySet());
            for (String trainB : occupiers) {
                if (trainB.equals(trainA)) continue;

                String groupBWaits = trainWaitsOnGroup.get(trainB);
                if (groupBWaits == null) continue;

                Set<String> groupBOccupiers = groupOccupiedByTrains.getOrDefault(
                        groupBWaits, Collections.emptySet());
                if (groupBOccupiers.contains(trainA)) {
                    deadlockedTrains.add(trainA);
                    deadlockedTrains.add(trainB);
                }
            }
        }

        if (!deadlockedTrains.isEmpty()) {
            for (int i = 0; i < trains.size(); i++) {
                CompoundTag train = trains.getCompound(i);
                String trainId = train.contains("id") ? train.getString("id") : "";
                if (!deadlockedTrains.contains(trainId)) continue;

                CompoundTag diag2 = new CompoundTag();
                diag2.putString("type", "DEADLOCK");
                diag2.putString("severity", "CRIT");
                diag2.putString("trainName", train.getString("name"));
                if (train.contains("x")) {
                    diag2.putFloat("x", train.getFloat("x"));
                    diag2.putFloat("y", train.contains("y") ? train.getFloat("y") : 0);
                    diag2.putFloat("z", train.getFloat("z"));
                }
                diag2.putString("desc", "Deadlock: mutually blocked with another train");
                diag2.putString("detail", "Two or more trains are each waiting on signal groups "
                        + "occupied by the other — manual intervention required");

                diagnostics.add(diag2);
            }
        }

        // === Check 7: Route Signal Coverage ===
        // For navigating trains, check if their path passes through unsignaled junctions.
        // Uses node IDs embedded in path segments for precise junction matching.
        for (int i = 0; i < trains.size(); i++) {
            CompoundTag train = trains.getCompound(i);
            if (!train.getBoolean("navigating") || !train.contains("path")) continue;

            ListTag path = train.getList("path", 10);
            String trainName = train.getString("name");

            // Collect node IDs along the route from path segment aId/bId fields
            Set<Integer> routeNodeIds = new LinkedHashSet<>();
            for (int p = 0; p < path.size(); p++) {
                CompoundTag seg = path.getCompound(p);
                if (seg.contains("aId")) routeNodeIds.add(seg.getInt("aId"));
                if (seg.contains("bId")) routeNodeIds.add(seg.getInt("bId"));
            }

            // Check each junction along the route for missing signals
            for (int nodeId : routeNodeIds) {
                List<int[]> nodeBranches = adjacency.get(nodeId);
                if (nodeBranches == null || nodeBranches.size() < 3) continue; // not a junction

                int totalBranches = nodeBranches.size();
                int signaledCount = 0;
                for (int[] br : nodeBranches) {
                    String ek = Math.min(nodeId, br[0]) + ":" + Math.max(nodeId, br[0]);
                    if (signaledEdges.contains(ek)) signaledCount++;
                }

                if (signaledCount < totalBranches) {
                    if (nodeId >= nodes.size()) continue;
                    CompoundTag jNode = nodes.getCompound(nodeId);

                    CompoundTag diag2 = new CompoundTag();
                    diag2.putString("type", "ROUTE_UNSIGNALED");
                    diag2.putString("severity", "WARN");
                    diag2.putString("trainName", trainName);
                    diag2.putFloat("x", jNode.getFloat("x"));
                    diag2.putFloat("y", jNode.getFloat("y"));
                    diag2.putFloat("z", jNode.getFloat("z"));
                    diag2.putString("desc", "Junction on route of '" + trainName + "' has "
                            + signaledCount + "/" + totalBranches + " signaled branches");
                    diag2.putString("detail", "This junction is along the active navigation path"
                            + " — missing signals may cause path-finding failures");

                    diagnostics.add(diag2);
                }
            }
        }

        // === Check 8: Network Connectivity ===
        // BFS from each station node to verify all stations are reachable from each other.
        // Flags disconnected track networks that prevent trains from routing.
        ListTag stationList2 = mapData.contains("Stations")
                ? mapData.getList("Stations", 10) : new ListTag();
        Map<String, Integer> stationToNode = new LinkedHashMap<>();

        for (int i = 0; i < stationList2.size(); i++) {
            CompoundTag sta = stationList2.getCompound(i);
            String name = sta.getString("name");
            float sx = sta.getFloat("x"), sz = sta.getFloat("z");

            // Find nearest node to this station
            int bestNode = -1;
            float bestDist = Float.MAX_VALUE;
            for (int n = 0; n < nodes.size(); n++) {
                CompoundTag node = nodes.getCompound(n);
                float ddx = node.getFloat("x") - sx;
                float ddz = node.getFloat("z") - sz;
                float d2 = ddx * ddx + ddz * ddz;
                if (d2 < bestDist) {
                    bestDist = d2;
                    bestNode = n;
                }
            }
            if (bestNode >= 0 && bestDist < 2500) { // within ~50 blocks
                stationToNode.put(name, bestNode);
            }
        }

        if (stationToNode.size() >= 2) {
            // BFS from first station to find all reachable nodes
            Iterator<Map.Entry<String, Integer>> stIt = stationToNode.entrySet().iterator();
            Map.Entry<String, Integer> rootEntry = stIt.next();
            String rootStation = rootEntry.getKey();
            Set<Integer> reachable = bfsReachable(rootEntry.getValue(), adjacency);

            while (stIt.hasNext()) {
                Map.Entry<String, Integer> entry = stIt.next();
                int nodeId = entry.getValue();
                if (reachable.contains(nodeId)) continue;

                CompoundTag diag2 = new CompoundTag();
                diag2.putString("type", "STATION_UNREACHABLE");
                diag2.putString("severity", "WARN");

                // Find station position
                for (int si = 0; si < stationList2.size(); si++) {
                    CompoundTag sTag = stationList2.getCompound(si);
                    if (entry.getKey().equals(sTag.getString("name"))) {
                        diag2.putFloat("x", sTag.getFloat("x"));
                        diag2.putFloat("y", sTag.contains("y") ? sTag.getFloat("y") : 0);
                        diag2.putFloat("z", sTag.getFloat("z"));
                        break;
                    }
                }

                diag2.putString("desc", "Station '" + entry.getKey()
                        + "' unreachable from '" + rootStation + "'");
                diag2.putString("detail", "These stations are on disconnected track networks"
                        + " — no path exists between them");

                diagnostics.add(diag2);
            }
        }

        // === Check 9: Missing Regular Signals (Block Section Boundaries) ===
        // After junctions, trains need regular (entry) signals to define block sections.
        // For each junction, check if there's a regular signal on the EXIT side of each branch.
        // "Exit" means the side facing AWAY from the junction — trains leaving the junction
        // should pass through a regular signal to create a new block section.
        //
        // Without these, the entire stretch between junctions is one giant signal block,
        // meaning only one train can use it at a time.
        //
        // Logic: for each junction branch, walk outward. If we find a chain signal but
        // no regular signal within a reasonable distance, suggest placing one.
        // Also suggest regular signals on straight (non-junction) segments that have
        // no signals at all and are longer than a threshold.

        // First: build a set of edges that have regular (entry_signal) signals
        Set<String> regularSignaledEdges = new HashSet<>();
        Set<String> chainSignaledEdges = new HashSet<>();
        for (int i = 0; i < signals.size(); i++) {
            CompoundTag sig = signals.getCompound(i);
            if (!sig.contains("edgeA") || !sig.contains("edgeB")) continue;
            int a = sig.getInt("edgeA");
            int b = sig.getInt("edgeB");
            String ek = Math.min(a, b) + ":" + Math.max(a, b);
            String typeF = sig.contains("typeF") ? sig.getString("typeF") : "entry_signal";
            String typeB = sig.contains("typeB") ? sig.getString("typeB") : "entry_signal";
            if (typeF.equals("entry_signal") || typeB.equals("entry_signal")) {
                regularSignaledEdges.add(ek);
            }
            if (typeF.equals("cross_signal") || typeB.equals("cross_signal")) {
                chainSignaledEdges.add(ek);
            }
        }

        // For each junction, check exit sides of branches for regular signals
        for (Map.Entry<Integer, List<int[]>> entry : adjacency.entrySet()) {
            List<int[]> branches = entry.getValue();
            if (branches.size() < 3) continue; // junctions only

            int jId = entry.getKey();
            if (jId >= nodes.size()) continue;
            CompoundTag jNode = nodes.getCompound(jId);
            float jx = jNode.getFloat("x");
            float jy = jNode.getFloat("y");
            float jz = jNode.getFloat("z");

            // For each branch, walk outward past the chain signal zone.
            // If there's a chain signal but no regular signal between this junction
            // and the next junction (or end of track), suggest placing one.
            for (int[] branch : branches) {
                int neighborId = branch[0];
                if (neighborId >= nodes.size()) continue;
                CompoundTag neighborNode = nodes.getCompound(neighborId);

                // Walk outward from junction along this branch
                int prev = jId;
                int current = neighborId;
                boolean foundChain = false;
                boolean foundRegular = false;
                float walkDist = 0;
                int lastNodeBeforeNextJunction = neighborId;

                for (int hop = 0; hop < 5; hop++) {
                    String ek = Math.min(prev, current) + ":" + Math.max(prev, current);
                    if (chainSignaledEdges.contains(ek)) foundChain = true;
                    if (regularSignaledEdges.contains(ek)) foundRegular = true;

                    // Accumulate distance
                    if (prev < nodes.size() && current < nodes.size()) {
                        CompoundTag pn = nodes.getCompound(prev);
                        CompoundTag cn = nodes.getCompound(current);
                        float ddx = cn.getFloat("x") - pn.getFloat("x");
                        float ddy = cn.getFloat("y") - pn.getFloat("y");
                        float ddz = cn.getFloat("z") - pn.getFloat("z");
                        walkDist += (float) Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                    }

                    List<int[]> currentBranches = adjacency.get(current);
                    if (currentBranches == null) break;

                    // Stop at next junction
                    if (currentBranches.size() >= 3) break;

                    lastNodeBeforeNextJunction = current;

                    // Find next node (not going back)
                    int next = -1;
                    for (int[] nb : currentBranches) {
                        if (nb[0] != prev) {
                            next = nb[0];
                            break;
                        }
                    }
                    if (next < 0) break;
                    prev = current;
                    current = next;
                }

                // If there's a chain signal on this branch but no regular signal,
                // suggest placing a regular signal after the chain signal position
                // (further from junction, to create a block section boundary)
                if (foundChain && !foundRegular && walkDist > 6.0f) {
                    // Suggest placement: past the chain signal, closer to the far end
                    float ndx = neighborNode.getFloat("x") - jx;
                    float ndy = neighborNode.getFloat("y") - jy;
                    float ndz = neighborNode.getFloat("z") - jz;
                    float dist = (float) Math.sqrt(ndx * ndx + ndy * ndy + ndz * ndz);
                    if (dist < 0.01f) continue;

                    // Place regular signal further out than where chain signal would be
                    float chainOffset = Math.min(Math.max(3.0f, maxTrainLength * 0.5f), dist * 0.8f);
                    float regularOffset = chainOffset + 3.0f; // a few blocks past the chain
                    if (regularOffset > walkDist * 0.9f) regularOffset = walkDist * 0.7f;
                    if (regularOffset < chainOffset + 1.0f) continue; // not enough room

                    float sugX = jx + (ndx / dist) * regularOffset;
                    float sugY = jy + (ndy / dist) * regularOffset;
                    float sugZ = jz + (ndz / dist) * regularOffset;

                    CompoundTag diag = new CompoundTag();
                    diag.putString("type", "MISSING_REGULAR_SIGNAL");
                    diag.putString("severity", "INFO");
                    diag.putFloat("x", sugX);
                    diag.putFloat("y", sugY);
                    diag.putFloat("z", sugZ);
                    String cardinal = getCardinalDir(ndx / dist, ndz / dist);
                    diag.putString("desc", "No regular signal after junction exit (" + cardinal
                            + ") — add one to create a block section boundary");
                    diag.putString("detail", "Chain signal found but no regular signal between "
                            + "this junction and the next. Without a regular signal, "
                            + "the entire segment is one block — only one train can use it.");

                    ListTag suggestions = new ListTag();
                    CompoundTag sug = new CompoundTag();
                    sug.putInt("sx", Mth.floor(sugX));
                    sug.putInt("sy", Mth.floor(sugY));
                    sug.putInt("sz", Mth.floor(sugZ));
                    sug.putString("signalType", "signal");
                    sug.putFloat("sdx", ndx / dist);
                    sug.putFloat("sdz", ndz / dist);
                    sug.putString("dir", "Place regular signal — exit toward " + cardinal);
                    suggestions.add(sug);
                    diag.put("suggestions", suggestions);

                    diagnostics.add(diag);
                }
            }
        }

        // === Check 10: Network Change Detection (Rescan Alerts) ===
        // Compare current scan with previous snapshot to detect changes that
        // may require signal re-evaluation (train length, station, layout changes).
        {
            int currentTrainCount = trains.size();
            float currentMaxLen = maxTrainLength;
            int currentStationCount = mapData.contains("Stations") ? mapData.getList("Stations", 10).size() : 0;
            int currentJunctionCount = 0;
            for (Map.Entry<Integer, List<int[]>> adjEntry : adjacency.entrySet()) {
                if (adjEntry.getValue().size() >= 3) currentJunctionCount++;
            }

            if (prevScanTrainCount >= 0) { // not first scan
                List<String> changes = new ArrayList<>();
                if (currentTrainCount != prevScanTrainCount) {
                    changes.add("Trains: " + prevScanTrainCount + " -> " + currentTrainCount);
                }
                if (Math.abs(currentMaxLen - prevScanMaxTrainLength) > 0.5f) {
                    changes.add("Max train length: " + (int) prevScanMaxTrainLength + "b -> " + (int) currentMaxLen + "b");
                }
                if (currentStationCount != prevScanStationCount) {
                    changes.add("Stations: " + prevScanStationCount + " -> " + currentStationCount);
                }
                if (currentJunctionCount != prevScanJunctionCount) {
                    changes.add("Junctions: " + prevScanJunctionCount + " -> " + currentJunctionCount);
                }

                if (!changes.isEmpty()) {
                    CompoundTag changeDiag = new CompoundTag();
                    changeDiag.putString("type", "NETWORK_CHANGED");
                    // Determine severity based on what changed
                    String severity = "INFO";
                    String desc = "Network changes detected — review signal placement";
                    if (currentMaxLen > prevScanMaxTrainLength + 0.5f) {
                        severity = "WARN";
                        desc = "Longer train detected — signal spacing may need updating";
                    } else if (currentJunctionCount > prevScanJunctionCount) {
                        severity = "WARN";
                        desc = "New junctions detected — check signal coverage";
                    } else if (currentTrainCount > 0 && prevScanTrainCount == 0) {
                        severity = "WARN";
                        desc = "Trains added to network — verify signal placement";
                    }
                    changeDiag.putString("severity", severity);
                    changeDiag.putString("desc", desc);

                    StringBuilder detail = new StringBuilder();
                    for (int ci = 0; ci < changes.size(); ci++) {
                        if (ci > 0) detail.append("; ");
                        detail.append(changes.get(ci));
                    }
                    changeDiag.putString("detail", detail.toString());

                    diagnostics.add(changeDiag);
                }
            }

            // Update snapshot for next scan
            prevScanTrainCount = currentTrainCount;
            prevScanMaxTrainLength = currentMaxLen;
            prevScanStationCount = currentStationCount;
            prevScanJunctionCount = currentJunctionCount;
        }

        // === Check 11: Chain-Only Corridor Detection ===
        // If the path between two adjacent junctions has ONLY chain (cross) signals
        // and ZERO regular (entry) signals, Create's pathfinder cannot find a
        // terminating block section → trains get NO_PATH.
        // Chain signals look ahead to the next REGULAR signal as a stopping point.
        // Without a regular signal somewhere in the chain, they loop infinitely.
        {
            Set<String> checkedPairs = new HashSet<>();
            for (Map.Entry<Integer, List<int[]>> adj1 : adjacency.entrySet()) {
                if (adj1.getValue().size() < 3) continue; // junction A
                int jA = adj1.getKey();

                for (int[] branch : adj1.getValue()) {
                    // Walk from junction A along this branch to find junction B
                    int prev = jA;
                    int cur = branch[0];
                    boolean hasChain = false;
                    boolean hasRegular = false;
                    List<String> pathEdges = new ArrayList<>();

                    for (int hop = 0; hop < 10; hop++) {
                        String ek = Math.min(prev, cur) + ":" + Math.max(prev, cur);
                        pathEdges.add(ek);
                        if (chainSignaledEdges.contains(ek)) hasChain = true;
                        if (regularSignaledEdges.contains(ek)) hasRegular = true;

                        List<int[]> curBranches = adjacency.get(cur);
                        if (curBranches == null) break;

                        // Reached another junction?
                        if (curBranches.size() >= 3) {
                            int jB = cur;
                            String pairKey = Math.min(jA, jB) + ":" + Math.max(jA, jB);
                            if (!checkedPairs.contains(pairKey) && hasChain && !hasRegular) {
                                checkedPairs.add(pairKey);

                                // Chain-only corridor found!
                                CompoundTag corDiag = new CompoundTag();
                                corDiag.putString("type", "CHAIN_ONLY_CORRIDOR");
                                corDiag.putString("severity", "CRIT");

                                // Position at midpoint
                                if (jA < nodes.size() && jB < nodes.size()) {
                                    CompoundTag nA = nodes.getCompound(jA);
                                    CompoundTag nB = nodes.getCompound(jB);
                                    corDiag.putFloat("x", (nA.getFloat("x") + nB.getFloat("x")) / 2);
                                    corDiag.putFloat("y", (nA.getFloat("y") + nB.getFloat("y")) / 2);
                                    corDiag.putFloat("z", (nA.getFloat("z") + nB.getFloat("z")) / 2);
                                }

                                corDiag.putString("desc", "Chain-only corridor between two junctions — "
                                        + "no regular signal terminator. Trains will report NO_PATH.");
                                corDiag.putString("detail", "All signals between these junctions are "
                                        + "chain (cross) type. Chain signals look ahead for a regular "
                                        + "signal as a stopping point. Without one, Create's pathfinder "
                                        + "cannot resolve the route. Convert at least one chain signal "
                                        + "to regular, or add a regular signal on this segment.");
                                diagnostics.add(corDiag);

                                LogicLink.LOGGER.warn("[LogicLink] CHAIN_ONLY_CORRIDOR between "
                                        + "junctions {} and {} ({} edges, all chain, no regular)",
                                        jA, jB, pathEdges.size());
                            }
                            break;
                        }

                        // Continue walk (straight through 2-way node)
                        int next = -1;
                        for (int[] nb : curBranches) {
                            if (nb[0] != prev) { next = nb[0]; break; }
                        }
                        if (next < 0) break;
                        prev = cur;
                        cur = next;
                    }
                }
            }
        }

        // === Debug: dump all diagnostics for troubleshooting ===
        LogicLink.LOGGER.debug("[LogicLink] ===== DIAGNOSTICS DUMP ({} total) =====", diagnostics.size());
        for (int di = 0; di < diagnostics.size(); di++) {
            CompoundTag d = diagnostics.getCompound(di);
            String dtype = d.getString("type");
            String dsev = d.getString("severity");
            String ddesc = d.getString("desc");
            float dx = d.contains("x") ? d.getFloat("x") : 0;
            float dz = d.contains("z") ? d.getFloat("z") : 0;
            StringBuilder sugStr = new StringBuilder();
            if (d.contains("suggestions")) {
                ListTag sl = d.getList("suggestions", 10);
                for (int si = 0; si < sl.size(); si++) {
                    CompoundTag s = sl.getCompound(si);
                    if (sugStr.length() > 0) sugStr.append(" | ");
                    sugStr.append(s.getString("signalType")).append("@")
                          .append(s.getInt("sx")).append(",").append(s.getInt("sy")).append(",").append(s.getInt("sz"))
                          .append(" [").append(s.getString("dir")).append("]");
                }
            }
            LogicLink.LOGGER.debug("[LogicLink] Diag#{}: {} {} @ ({},{}) -- {} -- sugs: {}",
                    di, dsev, dtype, (int) dx, (int) dz, ddesc, sugStr);
        }
        LogicLink.LOGGER.debug("[LogicLink] ===== END DIAGNOSTICS DUMP =====");

        mapData.put("Diagnostics", diagnostics);
    }

    private static String getCardinalDir(float dx, float dz) {
        double angle = Math.atan2(dz, dx) * 180.0 / Math.PI;
        if (angle < 0) angle += 360;
        if (angle < 22.5 || angle >= 337.5) return "E";
        if (angle < 67.5) return "SE";
        if (angle < 112.5) return "S";
        if (angle < 157.5) return "SW";
        if (angle < 202.5) return "W";
        if (angle < 247.5) return "NW";
        if (angle < 292.5) return "N";
        return "NE";
    }

    /**
     * Check if a signal suggestion already exists at the same position, type, and
     * similar direction. Two signals at the same position are duplicates only if
     * they are the same type AND face within 90 degrees of each other.
     * Opposite-facing signals at the same position are valid (two-way pair).
     */
    private static boolean isDuplicateSuggestion(ListTag suggestions, int sx, int sy, int sz,
                                                  String signalType, float sdx, float sdz) {
        for (int i = 0; i < suggestions.size(); i++) {
            CompoundTag existing = suggestions.getCompound(i);
            if (existing.getInt("sx") == sx && existing.getInt("sy") == sy && existing.getInt("sz") == sz
                    && existing.getString("signalType").equals(signalType)) {
                float exDx = existing.contains("sdx") ? existing.getFloat("sdx") : 0;
                float exDz = existing.contains("sdz") ? existing.getFloat("sdz") : 0;
                float dot = sdx * exDx + sdz * exDz;
                if (dot > 0.1f) return true; // same-ish direction = duplicate
            }
        }
        return false;
    }

    private static String getTrainNameStr(Object train) {
        try {
            Object nameComp = trainNameField.get(train);
            if (nameComp != null) {
                return (String) componentGetStringMethod.invoke(nameComp);
            }
        } catch (Exception ignored) {}
        return "Unknown";
    }

    // ==================== Schedule & Route Analysis Helpers ====================

    /**
     * Check if a schedule destination pattern matches any known station name.
     * Supports Create's wildcard matching (e.g., "Station *" matches "Station 1").
     */
    private static boolean matchesAnyStation(String pattern, Set<String> stationNames) {
        if (stationNames.contains(pattern)) return true;

        // Create uses * as glob wildcard
        if (pattern.contains("*")) {
            String regex = "^" + pattern.replace(".", "\\.").replace("*", ".*") + "$";
            try {
                for (String name : stationNames) {
                    if (name.matches(regex)) return true;
                }
            } catch (Exception ignored) {}
        }

        return false;
    }

    /**
     * Find the closest matching station name using Levenshtein distance.
     * Returns null if no station is reasonably close (within 40% of target length).
     */
    private static String findClosestStation(String target, Set<String> stationNames) {
        if (stationNames.isEmpty()) return null;

        String closest = null;
        int bestDist = Integer.MAX_VALUE;
        String targetLower = target.toLowerCase();

        for (String name : stationNames) {
            int dist = levenshteinDistance(targetLower, name.toLowerCase());
            if (dist < bestDist) {
                bestDist = dist;
                closest = name;
            }
        }

        // Only suggest if reasonably close
        return bestDist <= Math.max(3, target.length() * 0.4) ? closest : null;
    }

    /**
     * Compute Levenshtein edit distance between two strings.
     */
    private static int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(dp[i - 1][j] + 1,
                        Math.min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost));
            }
        }
        return dp[a.length()][b.length()];
    }

    /**
     * BFS to find all nodes reachable from a starting node via the adjacency graph.
     */
    private static Set<Integer> bfsReachable(int startNode,
                                              Map<Integer, List<int[]>> adjacency) {
        Set<Integer> visited = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();
        queue.add(startNode);
        visited.add(startNode);

        while (!queue.isEmpty()) {
            int current = queue.poll();
            List<int[]> neighbors = adjacency.get(current);
            if (neighbors == null) continue;
            for (int[] neighbor : neighbors) {
                if (visited.add(neighbor[0])) {
                    queue.add(neighbor[0]);
                }
            }
        }

        return visited;
    }

    // ==================== Reflection Initialization ====================

    private static boolean ensureReflection() {
        if (!reflectionInit) initReflection();
        return reflectionOK;
    }

    private static synchronized void initReflection() {
        if (reflectionInit) return;
        reflectionInit = true;

        try {
            // === Create.RAILWAYS ===
            Class<?> createClass = Class.forName("com.simibubi.create.Create");
            railwaysField = createClass.getField("RAILWAYS");

            Object testManager = railwaysField.get(null);
            if (testManager == null) {
                LogicLink.LOGGER.warn("TrainNetworkDataReader: Create.RAILWAYS is null, will retry");
                reflectionInit = false;
                return;
            }

            // === GlobalRailwayManager ===
            Class<?> gmClass = testManager.getClass();
            gmTrainsField = gmClass.getField("trains");
            gmTrackNetworksField = gmClass.getField("trackNetworks");
            gmSignalGroupsField = gmClass.getField("signalEdgeGroups");

            // === TrackGraph ===
            Class<?> graphClass = Class.forName("com.simibubi.create.content.trains.graph.TrackGraph");
            graphNodesField = graphClass.getDeclaredField("nodes");
            graphNodesField.setAccessible(true);
            graphConnectionsField = graphClass.getDeclaredField("connectionsByNode");
            graphConnectionsField.setAccessible(true);

            edgePointTypeClass = Class.forName("com.simibubi.create.content.trains.graph.EdgePointType");
            graphGetPointsMethod = graphClass.getMethod("getPoints", edgePointTypeClass);

            // EdgePointType constants
            epTypeStation = edgePointTypeClass.getField("STATION").get(null);
            epTypeSignal = edgePointTypeClass.getField("SIGNAL").get(null);
            try {
                epTypeObserver = edgePointTypeClass.getField("OBSERVER").get(null);
            } catch (Exception ignored) {
                LogicLink.LOGGER.debug("TrainNetworkDataReader: OBSERVER edge point type not found");
            }

            // === TrackNode ===
            Class<?> nodeClass = Class.forName("com.simibubi.create.content.trains.graph.TrackNode");
            nodeGetLocationMethod = nodeClass.getMethod("getLocation");
            nodeGetNetIdMethod = nodeClass.getMethod("getNetId");

            // === TrackNodeLocation ===
            Class<?> nodeLocClass = Class.forName("com.simibubi.create.content.trains.graph.TrackNodeLocation");
            nodeLocGetLocationMethod = nodeLocClass.getMethod("getLocation");
            nodeLocGetDimensionMethod = nodeLocClass.getMethod("getDimension");

            // === TrackEdge ===
            Class<?> edgeClass = Class.forName("com.simibubi.create.content.trains.graph.TrackEdge");
            edgeGetLengthMethod = edgeClass.getMethod("getLength");
            edgeIsTurnMethod = edgeClass.getMethod("isTurn");
            edgeTurnField = edgeClass.getDeclaredField("turn");
            edgeTurnField.setAccessible(true);
            edgeInterDimField = edgeClass.getDeclaredField("interDimensional");
            edgeInterDimField.setAccessible(true);
            edgeEdgeDataField = edgeClass.getDeclaredField("edgeData");
            edgeEdgeDataField.setAccessible(true);

            // Edge getPosition(TrackGraph, double) — for bezier sampling
            try {
                edgeGetPositionMethod = edgeClass.getMethod("getPosition", graphClass, double.class);
            } catch (NoSuchMethodException ignored) {
                LogicLink.LOGGER.debug("TrainNetworkDataReader: edge.getPosition not found, curves won't sample");
            }

            // === EdgeData ===
            Class<?> edgeDataClass = Class.forName("com.simibubi.create.content.trains.graph.EdgeData");
            edgeDataSignalGroupField = edgeDataClass.getDeclaredField("singleSignalGroup");
            edgeDataSignalGroupField.setAccessible(true);

            // === TrackEdgePoint (base class) ===
            Class<?> edgePointClass = Class.forName("com.simibubi.create.content.trains.signal.TrackEdgePoint");
            edgePointEdgeLocationField = edgePointClass.getDeclaredField("edgeLocation");
            edgePointEdgeLocationField.setAccessible(true);
            edgePointPositionField = edgePointClass.getDeclaredField("position");
            edgePointPositionField.setAccessible(true);

            // === GlobalStation ===
            Class<?> stationClass = Class.forName("com.simibubi.create.content.trains.station.GlobalStation");
            stationNameField = stationClass.getField("name");
            stationGetBlockEntityPosMethod = stationClass.getMethod("getBlockEntityPos");
            stationGetPresentTrainMethod = stationClass.getMethod("getPresentTrain");
            stationGetImminentTrainMethod = stationClass.getMethod("getImminentTrain");

            // === SignalBoundary ===
            Class<?> signalClass = Class.forName("com.simibubi.create.content.trains.signal.SignalBoundary");
            signalCachedStatesField = signalClass.getField("cachedStates");
            signalTypesField = signalClass.getField("types");
            signalBlockEntitiesField = signalClass.getField("blockEntities");

            // === SignalEdgeGroup ===
            Class<?> segClass = Class.forName("com.simibubi.create.content.trains.signal.SignalEdgeGroup");
            segTrainsField = segClass.getDeclaredField("trains");
            segTrainsField.setAccessible(true);
            try {
                segColorField = segClass.getDeclaredField("color");
                segColorField.setAccessible(true);
            } catch (Exception ignored) {}

            // === Couple ===
            Class<?> coupleClass = Class.forName("net.createmod.catnip.data.Couple");
            coupleGetFirstMethod = coupleClass.getMethod("getFirst");
            coupleGetSecondMethod = coupleClass.getMethod("getSecond");

            // === Train ===
            Class<?> trainClass = Class.forName("com.simibubi.create.content.trains.entity.Train");
            trainNameField = trainClass.getField("name");
            trainIdField = trainClass.getField("id");
            trainSpeedField = trainClass.getField("speed");
            trainDerailedField = trainClass.getField("derailed");
            trainCarriagesField = trainClass.getField("carriages");
            trainNavigationField = trainClass.getField("navigation");
            trainCurrentStationField = trainClass.getField("currentStation");
            trainGraphField = trainClass.getDeclaredField("graph");
            trainGraphField.setAccessible(true);

            // === Carriage (for actual train length from bogey spacing) ===
            try {
                Class<?> carriageClass = Class.forName("com.simibubi.create.content.trains.entity.Carriage");
                carriageBogeySpacingField = carriageClass.getField("bogeySpacing");
            } catch (Exception e) {
                LogicLink.LOGGER.debug("TrainNetworkDataReader: Carriage.bogeySpacing not found, using heuristic");
            }

            // ScheduleRuntime — for detailed train status diagnostics
            trainRuntimeField = trainClass.getField("runtime");
            Class<?> runtimeClass = Class.forName("com.simibubi.create.content.trains.schedule.ScheduleRuntime");
            runtimeGetScheduleMethod = runtimeClass.getMethod("getSchedule");
            runtimePausedField = runtimeClass.getField("paused");
            runtimeCompletedField = runtimeClass.getField("completed");
            runtimeCurrentEntryField = runtimeClass.getField("currentEntry");
            runtimeCurrentTitleField = runtimeClass.getField("currentTitle");
            runtimeStateField = runtimeClass.getField("state");

            Class<?> scheduleClass = Class.forName(
                    "com.simibubi.create.content.trains.schedule.Schedule");
            scheduleEntriesField = scheduleClass.getField("entries");
            scheduleCyclicField = scheduleClass.getField("cyclic");

            try {
                trainOccupiedSignalBlocksField = trainClass.getField("occupiedSignalBlocks");
            } catch (Exception ignored) {
                try {
                    trainOccupiedSignalBlocksField = trainClass.getDeclaredField("occupiedSignalBlocks");
                    trainOccupiedSignalBlocksField.setAccessible(true);
                } catch (Exception ignored2) {}
            }

            try {
                trainGetPositionInDimMethod = trainClass.getMethod("getPositionInDimension",
                        ResourceKey.class);
                LogicLink.LOGGER.info("[LogicLink] Reflection: Train.getPositionInDimension resolved OK");
            } catch (Exception e) {
                LogicLink.LOGGER.warn("[LogicLink] Reflection: Train.getPositionInDimension NOT found: {}", e.getMessage());
                // Try alternative method names that Create might use
                try {
                    trainGetPositionInDimMethod = trainClass.getMethod("getBlockPosInDimension",
                            ResourceKey.class);
                    LogicLink.LOGGER.info("[LogicLink] Reflection: Train.getBlockPosInDimension resolved as fallback");
                } catch (Exception e2) {
                    LogicLink.LOGGER.warn("[LogicLink] Reflection: No position-in-dimension method found at all");
                    // Log all available methods for debugging
                    StringBuilder methods = new StringBuilder();
                    for (Method m : trainClass.getMethods()) {
                        if (m.getName().toLowerCase().contains("pos") || m.getName().toLowerCase().contains("dim")) {
                            methods.append(m.getName()).append("(");
                            for (Class<?> p : m.getParameterTypes()) methods.append(p.getSimpleName()).append(",");
                            methods.append(") ");
                        }
                    }
                    LogicLink.LOGGER.info("[LogicLink] Train class position/dim methods: {}", methods);
                }
            }

            // === Navigation ===
            Class<?> navClass = Class.forName("com.simibubi.create.content.trains.entity.Navigation");
            navDestinationField = navClass.getField("destination");
            navDistanceField = navClass.getField("distanceToDestination");
            navIsActiveMethod = navClass.getMethod("isActive");
            navCurrentPathField = navClass.getDeclaredField("currentPath");
            navCurrentPathField.setAccessible(true);

            // Navigation — signal blocking detection
            try {
                navWaitingForSignalField = navClass.getDeclaredField("waitingForSignal");
                navWaitingForSignalField.setAccessible(true);
            } catch (Exception e) {
                LogicLink.LOGGER.debug("TrainNetworkDataReader: Navigation.waitingForSignal not found");
            }
            try {
                navTicksWaitingField = navClass.getDeclaredField("ticksWaitingForSignal");
                navTicksWaitingField.setAccessible(true);
            } catch (Exception e) {
                LogicLink.LOGGER.debug("TrainNetworkDataReader: Navigation.ticksWaitingForSignal not found");
            }

            // === ScheduleEntry — for full schedule iteration ===
            try {
                Class<?> entryClass = Class.forName(
                        "com.simibubi.create.content.trains.schedule.ScheduleEntry");
                scheduleEntryInstructionField = entryClass.getField("instruction");

                Class<?> instrClass = Class.forName(
                        "com.simibubi.create.content.trains.schedule.ScheduleInstruction");
                instructionDataField = instrClass.getDeclaredField("data");
                instructionDataField.setAccessible(true);
            } catch (Exception e) {
                LogicLink.LOGGER.debug("TrainNetworkDataReader: ScheduleEntry/Instruction reflection failed: {}",
                        e.getMessage());
            }

            // === SignalBoundary per-side groups ===
            try {
                signalBoundaryGroupsField = Class.forName(
                        "com.simibubi.create.content.trains.signal.SignalBoundary")
                        .getField("groups");
            } catch (Exception e) {
                LogicLink.LOGGER.debug("TrainNetworkDataReader: SignalBoundary.groups not found");
            }

            // === TrackObserver ===
            Class<?> observerClass = Class.forName("com.simibubi.create.content.trains.observer.TrackObserver");
            observerIsActivatedMethod = observerClass.getMethod("isActivated");

            // SingleBlockEntityEdgePoint — parent of station and observer
            Class<?> singleBEEPClass = Class.forName(
                    "com.simibubi.create.content.trains.signal.SingleBlockEntityEdgePoint");
            observerGetBlockEntityPosMethod = singleBEEPClass.getMethod("getBlockEntityPos");

            try {
                observerGetFilterMethod = observerClass.getMethod("getFilter");
            } catch (Exception ignored) {}

            // === Component ===
            componentGetStringMethod = Component.class.getMethod("getString");

            reflectionOK = true;
            LogicLink.LOGGER.info("TrainNetworkDataReader: Reflection initialized — full topology access OK");

        } catch (ClassNotFoundException e) {
            LogicLink.LOGGER.warn("TrainNetworkDataReader: Create classes not found — {}", e.getMessage());
            reflectionOK = false;
        } catch (Exception e) {
            LogicLink.LOGGER.error("TrainNetworkDataReader: Reflection init failed", e);
            reflectionOK = false;
        }
    }
}
