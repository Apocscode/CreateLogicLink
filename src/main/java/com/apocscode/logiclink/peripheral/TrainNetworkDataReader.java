package com.apocscode.logiclink.peripheral;

import com.apocscode.logiclink.LogicLink;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
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
            for (int i = 0; i < diags.size(); i++) {
                String type = diags.getCompound(i).getString("type");
                switch (type) {
                    case "JUNCTION_UNSIGNALED" -> junctionCount++;
                    case "NO_PATH" -> noPathCount++;
                    case "SIGNAL_CONFLICT" -> conflictCount++;
                    case "TRAIN_PAUSED" -> pausedCount++;
                    case "SCHEDULE_DONE" -> doneCount++;
                }
            }
            result.putInt("junctionCount", junctionCount);
            result.putInt("noPathCount", noPathCount);
            result.putInt("conflictCount", conflictCount);
            result.putInt("pausedCount", pausedCount);
            result.putInt("doneCount", doneCount);
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
                mapData.put("Trains", trainList);
                return;
            }

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

                    // Carriages
                    List<?> carriages = (List<?>) trainCarriagesField.get(train);
                    tag.putInt("carriages", carriages != null ? carriages.size() : 0);

                    // Current station
                    Object curStation = trainCurrentStationField.get(train);
                    if (curStation != null) {
                        String stName = (String) stationNameField.get(curStation);
                        if (stName != null) tag.putString("currentStation", stName);
                    }

                    // World position — try to get for the target dimension
                    boolean posFound = false;
                    if (trainGetPositionInDimMethod != null) {
                        try {
                            // Get dimensions this train is present in
                            Object graphRef = trainGraphField.get(train);
                            // Try generic approach: the train stores a graph reference
                            // and we can attempt BlockPos from getPresentDimensions

                            // Direct approach: iterate known dimension keys
                            Method getPresentDims = train.getClass().getMethod("getPresentDimensions");
                            List<?> dims = (List<?>) getPresentDims.invoke(train);
                            if (dims != null) {
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
                                            break;
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    // If no position found, skip this train (not in this dimension)
                    if (!posFound && !derailed) {
                        // Still include derailed trains even without position
                        if (!derailed) continue;
                    }

                    // Navigation info
                    Object nav = trainNavigationField.get(train);
                    if (nav != null) {
                        boolean active = (boolean) navIsActiveMethod.invoke(nav);
                        tag.putBoolean("navigating", active);
                        if (active) {
                            Object dest = navDestinationField.get(nav);
                            if (dest != null) {
                                String destName = (String) stationNameField.get(dest);
                                if (destName != null) tag.putString("destination", destName);
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
                            }
                        }
                    } catch (Exception ignored) {}

                    trainList.add(tag);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            LogicLink.LOGGER.debug("TrainNetworkDataReader: Failed to read trains: {}", e.getMessage());
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

        // Compute max train length (in blocks) from carriage count
        // Each carriage is ~5 blocks (bogey spacing); use carriages * 5 + buffer
        int maxCarriages = 1;
        for (int i = 0; i < trains.size(); i++) {
            int c = trains.getCompound(i).getInt("carriages");
            if (c > maxCarriages) maxCarriages = c;
        }
        float maxTrainLength = maxCarriages * 5.0f + 3.0f; // ~5 blocks/car + 3 buffer
        mapData.putFloat("MaxTrainLength", maxTrainLength);
        mapData.putInt("MaxCarriages", maxCarriages);

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
                signaledEdges.add(Math.min(a, b) + ":" + Math.max(a, b));
            }
        }

        // Track junctions flagged by Check 1 to avoid conflicting Check 3 diagnostics
        Set<Integer> unsignaledJunctions = new HashSet<>();

        // === Check 1: Completely unsignaled junctions ===
        // Only flag junctions where NO branches have signals at all.
        // Partial coverage (some branches signaled, some not) is intentional in many
        // layouts — one-way branches, dead-end spurs, etc. We don't suggest specific
        // signal placements because Create signals are directional and segment-based;
        // the correct placement depends on traffic flow which we can't determine.
        for (Map.Entry<Integer, List<int[]>> entry : adjacency.entrySet()) {
            List<int[]> branches = entry.getValue();
            if (branches.size() < 3) continue; // not a junction

            int jId = entry.getKey();
            if (jId >= nodes.size()) continue;
            CompoundTag jNode = nodes.getCompound(jId);
            float jx = jNode.getFloat("x");
            float jy = jNode.getFloat("y");
            float jz = jNode.getFloat("z");

            // Count signaled branches
            int signaledCount = 0;
            for (int[] branch : branches) {
                String ek = Math.min(jId, branch[0]) + ":" + Math.max(jId, branch[0]);
                if (signaledEdges.contains(ek)) {
                    signaledCount++;
                }
            }

            // Only flag if NO branches have any signals at all
            if (signaledCount > 0) continue;

            CompoundTag diag = new CompoundTag();
            diag.putString("type", "JUNCTION_UNSIGNALED");
            diag.putString("severity", "WARN");
            diag.putFloat("x", jx);
            diag.putFloat("y", jy);
            diag.putFloat("z", jz);
            diag.putInt("branches", branches.size());
            diag.putInt("unsignaled", branches.size());

            // Per-branch suggestions: place chain signal on each branch,
            // offset a few blocks away from the junction toward the neighbor node.
            // Direction arrow points from the branch TOWARD the junction (entry direction).
            ListTag suggestions = new ListTag();
            for (int[] branch : branches) {
                int neighborId = branch[0];
                if (neighborId >= nodes.size()) continue;
                CompoundTag neighborNode = nodes.getCompound(neighborId);
                float nx = neighborNode.getFloat("x");
                float ny = neighborNode.getFloat("y");
                float nz = neighborNode.getFloat("z");

                // Direction from neighbor toward junction (entry direction for the arrow)
                float ddx = jx - nx;
                float ddy = jy - ny;
                float ddz = jz - nz;
                float dist = (float) Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                if (dist < 0.01f) continue;
                float ndx = ddx / dist; // normalized direction toward junction
                float ndz = ddz / dist;

                // Place suggestion a few blocks from the junction along this branch
                // (offset outward so the signal isn't right on top of the junction)
                float offset = Math.min(5.0f, dist * 0.4f); // 5 blocks or 40% of branch length
                float sugX = jx - ndx * offset;
                float sugY = jy - (ddy / dist) * offset;
                float sugZ = jz - ndz * offset;

                CompoundTag branchSug = new CompoundTag();
                branchSug.putInt("sx", Math.round(sugX));
                branchSug.putInt("sy", Math.round(sugY));
                branchSug.putInt("sz", Math.round(sugZ));
                branchSug.putString("signalType", "chain");
                // Arrow points toward junction (entry direction)
                branchSug.putFloat("sdx", ndx);
                branchSug.putFloat("sdz", ndz);
                String cardinal = getCardinalDir(ndx, ndz);
                branchSug.putString("dir", "Place chain signal — entry from " + cardinal);
                suggestions.add(branchSug);
            }
            diag.put("suggestions", suggestions);

            diag.putString("desc", branches.size() + "-way junction with no signals on any branch. "
                    + "Consider adding chain signals before the junction on entry branches.");

            unsignaledJunctions.add(jId);
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
                                s.putInt("sx", Math.round(jjx - bndx * boff));
                                s.putInt("sy", Math.round(jjy - (ddy2 / d2) * boff));
                                s.putInt("sz", Math.round(jjz - bndz * boff));
                                s.putString("signalType", "chain");
                                s.putFloat("sdx", bndx);
                                s.putFloat("sdz", bndz);
                                s.putString("dir", "Check junction signals \u2014 entry from " + getCardinalDir(bndx, bndz));
                                sug.add(s);
                            }
                        }
                        if (sug.isEmpty()) {
                            CompoundTag s = new CompoundTag();
                            s.putInt("sx", (int) jjx);
                            s.putInt("sy", (int) jjy);
                            s.putInt("sz", (int) jjz);
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

        for (int i = 0; i < signals.size(); i++) {
            CompoundTag sig = signals.getCompound(i);
            if (!sig.contains("edgeA") || !sig.contains("edgeB")) continue;

            int edgeACheck = sig.getInt("edgeA");
            int edgeBCheck = sig.getInt("edgeB");
            // Skip signals at junctions already flagged by Check 1 (unsignaled branches)
            // to avoid conflicting "add signal" + "fix existing signal" at same junction
            if (unsignaledJunctions.contains(edgeACheck) || unsignaledJunctions.contains(edgeBCheck)) continue;

            int edgeA = edgeACheck;
            int edgeB = edgeBCheck;
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
            if (sx == 0 && sz == 0) continue; // no valid position

            // For each side (F and B) of the signal boundary:
            // - "forward" side faces from edgeA toward edgeB
            // - "backward" side faces from edgeB toward edgeA
            // If the signal is on a junction entry (approaching junction), it should be chain (cross_signal)
            // If the signal is NOT on a junction edge, chain is unnecessary/wasteful

            // Side F: direction A→B (train approaching from A side)
            // If B is a junction, then side F is "entering junction" → should be chain
            boolean fShouldBeChain = bIsJunction;
            boolean fIsChain = typeF.equals("cross_signal");

            // Side B: direction B→A (train approaching from B side)
            // If A is a junction, then side B is "entering junction" → should be chain
            boolean bShouldBeChain = aIsJunction;
            boolean bIsChain = typeB.equals("cross_signal");

            List<String> issues = new ArrayList<>();

            // Check side F — only flag missing chain, not "unnecessary chain" on junction edges
            // (chain on the outward-facing side of a junction edge is harmless and normal)
            if (onJunctionEdge && fShouldBeChain && !fIsChain) {
                issues.add("Side F: regular signal should be CHAIN (protects junction at node " + edgeB + ")");
            }

            // Check side B
            if (onJunctionEdge && bShouldBeChain && !bIsChain) {
                issues.add("Side B: regular signal should be CHAIN (protects junction at node " + edgeA + ")");
            }

            // Note: "chain on non-junction edge" check removed — Create's graph can have
            // intermediate nodes between junctions and signals, causing false positives.
            // Chain signals on non-junction edges are harmless (just slightly conservative).

            if (!issues.isEmpty()) {
                CompoundTag diag = new CompoundTag();
                diag.putString("type", "SIGNAL_CONFLICT");
                diag.putString("severity", fShouldBeChain && !fIsChain || bShouldBeChain && !bIsChain ? "CRIT" : "WARN");
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
                if (fShouldBeChain && !fIsChain) {
                    conflictSug.putString("correctType", "chain");
                    conflictSug.putString("dir", "Replace with chain signal");
                } else if (bShouldBeChain && !bIsChain) {
                    conflictSug.putString("correctType", "chain");
                    conflictSug.putString("dir", "Replace with chain signal");
                } else if (!onJunctionEdge && (fIsChain || bIsChain)) {
                    conflictSug.putString("correctType", "signal");
                    conflictSug.putString("dir", "Replace with regular signal");
                } else {
                    conflictSug.putString("correctType", "signal");
                    conflictSug.putString("dir", "Change chain to regular signal");
                }
                suggestions.add(conflictSug);
                diag.put("suggestions", suggestions);

                diagnostics.add(diag);
            }
        }

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

    private static String getTrainNameStr(Object train) {
        try {
            Object nameComp = trainNameField.get(train);
            if (nameComp != null) {
                return (String) componentGetStringMethod.invoke(nameComp);
            }
        } catch (Exception ignored) {}
        return "Unknown";
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
            } catch (Exception ignored) {}

            // === Navigation ===
            Class<?> navClass = Class.forName("com.simibubi.create.content.trains.entity.Navigation");
            navDestinationField = navClass.getField("destination");
            navDistanceField = navClass.getField("distanceToDestination");
            navIsActiveMethod = navClass.getMethod("isActive");
            navCurrentPathField = navClass.getDeclaredField("currentPath");
            navCurrentPathField.setAccessible(true);

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
