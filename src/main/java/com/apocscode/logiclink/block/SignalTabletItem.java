package com.apocscode.logiclink.block;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.peripheral.TrainNetworkDataReader;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.signal.SignalBlock;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackTargetingBlockItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * Handheld Signal Diagnostic Tablet.
 * Right-click to scan the train network and open a diagnostic screen
 * showing signal issues, conflicts, and suggested placements.
 * Updates automatically after signal repairs are made (re-scan on right-click).
 */
public class SignalTabletItem extends Item {

    /** NBT key for cached diagnostic data */
    public static final String TAG_SCAN_DATA = "SignalScanData";
    public static final String TAG_SCAN_TIME = "ScanTime";
    /** NBT key for queued pending suggestions waiting to be placed in waves */
    public static final String TAG_PENDING = "PendingSignals";
    public static final String TAG_TOTAL = "PendingTotal";
    /** How many signals are placed per shift-right-click wave */
    private static final int WAVE_SIZE = 5;
    private static final int AUTO_PLACE_TRACK_SEARCH_RADIUS = 12;
    private static final int AUTO_PLACE_TARGET_SEARCH_RADIUS = 5;
        private static final String[] NIXIE_TUBE_LIGHT_IDS = new String[] {
            "create:nixie_tube",
            "createselectronics:nixie_tube",
            "supplementaries:nixie_tube"
        };
        private static Block cachedNixieTubeLightBlock;
        private static boolean nixieTubeLightResolved;

    public SignalTabletItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level instanceof ServerLevel serverLevel) {
            CompoundTag wrapper = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

            boolean hasPending = wrapper.contains(TAG_PENDING)
                    && !wrapper.getList(TAG_PENDING, CompoundTag.TAG_COMPOUND).isEmpty();

            if (player.isShiftKeyDown() || !hasPending) {
                // Shift-right-click OR no pending queue: scan and reset queue
                CompoundTag scanData = TrainNetworkDataReader.scanDiagnosticsOnly(serverLevel);
                if (scanData != null) {
                    scanData.putDouble("playerX", player.getX());
                    scanData.putDouble("playerY", player.getY());
                    scanData.putDouble("playerZ", player.getZ());

                    ListTag queue = buildPendingQueue(scanData);
                    wrapper = new CompoundTag();
                    wrapper.put(TAG_SCAN_DATA, scanData);
                    wrapper.putLong(TAG_SCAN_TIME, level.getGameTime());
                    wrapper.put(TAG_PENDING, queue);
                    wrapper.putInt(TAG_TOTAL, queue.size());
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(wrapper));
                    player.setItemInHand(hand, stack);

                    player.sendSystemMessage(Component.literal(
                            "\u00A7a[Signal Tablet]\u00A7r Scan: " + scanData.getInt("issueCount")
                            + " issue(s). " + queue.size() + " signal(s) queued — right-click again to place"));
                } else {
                    player.sendSystemMessage(Component.literal(
                            "\u00A7c[Signal Tablet]\u00A7r No train network found"));
                }
            } else {
                // Right-click with pending queue: place next wave
                if (!player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.literal(
                            "\u00A7c[Signal Tablet]\u00A7r Auto-place requires operator permission level 2"));
                } else {
                    ListTag pending = wrapper.getList(TAG_PENDING, CompoundTag.TAG_COMPOUND);
                    int total = wrapper.getInt(TAG_TOTAL);

                    LogicLink.LOGGER.info("[Signal Tablet] Starting wave, pending={}, total={}", pending.size(), total);
                    AutoPlaceResult result = placeWave(serverLevel, player, hand, pending);

                    int after = pending.size();
                    int done = total - after;
                    wrapper.put(TAG_PENDING, pending);
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(wrapper));
                    player.setItemInHand(hand, stack);

                    player.sendSystemMessage(Component.literal(
                            "\u00A7b[Signal Tablet]\u00A7r Wave " + done + "/" + total
                            + " — placed " + result.placed()
                            + ", blocked " + result.blocked()
                            + ", no-track " + result.noTrack()
                            + ", failed " + result.failed()
                            + (after == 0 ? " \u00A7a— ALL DONE" : " \u00A77— " + after + " remaining")));
                }
            }
        }

        if (level.isClientSide) {
            openTabletScreen(stack);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** Flatten all signal/chain suggestions from scan data into a queue sorted nearest-to-player first. */
    private static ListTag buildPendingQueue(CompoundTag scanData) {
        double px = scanData.getDouble("playerX");
        double py = scanData.getDouble("playerY");
        double pz = scanData.getDouble("playerZ");

        List<CompoundTag> list = new java.util.ArrayList<>();
        ListTag diagnostics = scanData.getList("Diagnostics", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < diagnostics.size(); i++) {
            ListTag suggestions = diagnostics.getCompound(i).getList("suggestions", CompoundTag.TAG_COMPOUND);
            for (int j = 0; j < suggestions.size(); j++) {
                CompoundTag sug = suggestions.getCompound(j);
                String t = sug.getString("signalType");
                if ("signal".equals(t) || "chain".equals(t)) {
                    list.add(sug.copy());
                }
            }
        }

        // Sort by distance to player so nearby signals are placed first
        list.sort(Comparator.comparingDouble(sug -> {
            double dx = sug.getInt("sx") - px;
            double dy = sug.getInt("sy") - py;
            double dz = sug.getInt("sz") - pz;
            return dx * dx + dy * dy + dz * dz;
        }));

        ListTag queue = new ListTag();
        list.forEach(queue::add);
        return queue;
    }

    /**
     * Pop up to WAVE_SIZE entries from the front of {@code pending} and process them.
     * Entries that fail (no-track / fail) are dropped; the caller decides whether to
     * retry them on the next wave.
     */
    private AutoPlaceResult placeWave(ServerLevel level, Player player, InteractionHand hand, ListTag pending) {
        int placed = 0, retyped = 0, alreadyCorrect = 0, blocked = 0, noTrack = 0, failed = 0, skipped = 0;

        int count = Math.min(WAVE_SIZE, pending.size());
        // Work from the front: collect this wave then remove them
        List<CompoundTag> wave = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            wave.add(pending.getCompound(i).copy());
        }
        // Remove the wave entries from the list (front to back, accounting for index shift)
        for (int i = count - 1; i >= 0; i--) {
            pending.remove(i);
        }

        for (CompoundTag suggestion : wave) {
            String signalType = suggestion.getString("signalType");
            BlockPos targetPos = new BlockPos(
                    suggestion.getInt("sx"),
                    suggestion.getInt("sy"),
                    suggestion.getInt("sz"));
            boolean wantChain = "chain".equals(signalType);

            if (level.getBlockEntity(targetPos) instanceof SignalBlockEntity be) {
                if (setSignalMode(be, targetPos, wantChain)) retyped++;
                else alreadyCorrect++;
                continue;
            }

            PlacementCandidate candidate = findPlacementCandidate(level, targetPos,
                    suggestion.getFloat("sdx"), suggestion.getFloat("sdz"));
            if (candidate == null) {
                noTrack++;
                continue;
            }

            PlacementAttempt attempt = placeSignal(level, player, hand, targetPos, candidate);
            if (attempt == PlacementAttempt.BLOCKED) {
                blocked++;
                continue;
            }
            if (attempt == PlacementAttempt.FAILED) {
                failed++;
                continue;
            }

            placed++;
            if (level.getBlockEntity(targetPos) instanceof SignalBlockEntity be
                    && setSignalMode(be, targetPos, wantChain)) {
                retyped++;
            }
        }

        return new AutoPlaceResult(placed, retyped, alreadyCorrect, blocked, noTrack, failed, skipped);
    }

    private PlacementCandidate findPlacementCandidate(ServerLevel level, BlockPos targetPos, float dx, float dz) {
        Vec3 desired = new Vec3(dx, 0, dz);
        int trackBlocks = 0;
        int junctionTracks = 0;
        int invalidOverlaps = 0;

        PlacementCandidate best = null;
        for (int dxScan = -AUTO_PLACE_TRACK_SEARCH_RADIUS; dxScan <= AUTO_PLACE_TRACK_SEARCH_RADIUS; dxScan++) {
            for (int dyScan = -2; dyScan <= 2; dyScan++) {
                for (int dzScan = -AUTO_PLACE_TRACK_SEARCH_RADIUS; dzScan <= AUTO_PLACE_TRACK_SEARCH_RADIUS; dzScan++) {
                    BlockPos pos = targetPos.offset(dxScan, dyScan, dzScan);
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof ITrackBlock trackBlock))
                        continue;

                    trackBlocks++;
                    List<Vec3> trackAxes = trackBlock.getTrackAxes(level, pos, state);
                    if (trackAxes.size() != 1) {
                        junctionTracks++;
                        continue;
                    }

                    PlacementCandidate candidate = createPlacementCandidate(level, pos, targetPos, desired);
                    if (candidate == null) {
                        invalidOverlaps++;
                        continue;
                    }

                    if (best == null || candidate.distanceScore() < best.distanceScore()) {
                        best = candidate;
                    }
                }
            }
        }

        if (best == null) {
            LogicLink.LOGGER.warn("findPlacementCandidate: none near {} (tracks={}, junctionTracks={}, overlapRejected={}, radius={})",
                    targetPos, trackBlocks, junctionTracks, invalidOverlaps, AUTO_PLACE_TRACK_SEARCH_RADIUS);
        }

        return best;
    }

    private PlacementCandidate createPlacementCandidate(ServerLevel level, BlockPos trackPos, BlockPos targetPos, Vec3 desired) {
        BlockState trackState = level.getBlockState(trackPos);
        ITrackBlock trackBlock = (ITrackBlock) trackState.getBlock();
        List<Vec3> trackAxes = trackBlock.getTrackAxes(level, trackPos, trackState);
        if (trackAxes.size() != 1) {
            return null;
        }

        Vec3 axis = trackAxes.get(0).normalize();
        boolean front = desired.lengthSqr() == 0 || axis.dot(desired.normalize()) >= 0;
        TrackTargetingBlockItem.OverlapResult overlap = getOverlap(level, trackPos, front);
        if (overlap != TrackTargetingBlockItem.OverlapResult.VALID) {
            boolean alternateFront = !front;
            TrackTargetingBlockItem.OverlapResult alternateOverlap = getOverlap(level, trackPos, alternateFront);
            if (alternateOverlap != TrackTargetingBlockItem.OverlapResult.VALID) {
                return null;
            }
            front = alternateFront;
        }

        double score = targetPos.distSqr(trackPos);
        return new PlacementCandidate(trackPos, front, score);
    }

    private TrackTargetingBlockItem.OverlapResult getOverlap(ServerLevel level, BlockPos trackPos, boolean front) {
        final TrackTargetingBlockItem.OverlapResult[] overlapHolder = new TrackTargetingBlockItem.OverlapResult[1];
        TrackTargetingBlockItem.withGraphLocation(level, trackPos, front, null, EdgePointType.SIGNAL,
                (overlap, location) -> overlapHolder[0] = overlap);
        return overlapHolder[0];
    }

    private PlacementAttempt placeSignal(ServerLevel level, Player player, InteractionHand hand, BlockPos targetPos,
                                PlacementCandidate candidate) {
        // Determine the track axis, then derive the right-perpendicular (horizontal).
        // "Right" = UP × axis, so standing at the track facing along axis, right is to the right.
        BlockPos trackPos = candidate.trackPos();
        BlockState trackState = level.getBlockState(trackPos);
        ITrackBlock trackBlock = (ITrackBlock) trackState.getBlock();
        List<Vec3> axes = trackBlock.getTrackAxes(level, trackPos, trackState);
        Vec3 axis = axes.isEmpty() ? new Vec3(1, 0, 0) : axes.get(0).normalize();

        // right = UP cross axis (right-hand rule: right side when facing along axis)
        Vec3 right = new Vec3(0, 1, 0).cross(axis).normalize();
        int rx = (int) Math.round(right.x);
        int rz = (int) Math.round(right.z);
        // If axis is purely vertical rx==rz==0; fall back to X offset
        if (rx == 0 && rz == 0) { rx = 1; }

        // Candidate positions in priority order.
        // Tie horizontal side to travel direction so opposite directions on the same
        // track block naturally prefer opposite sides.
        int side = candidate.front() ? 1 : -1;
        int[][] offsets = {
            { side * rx * 4, 2, side * rz * 4 },
            {-side * rx * 4, 2,-side * rz * 4 },
            { side * rx * 4, 3, side * rz * 4 },
            {-side * rx * 4, 3,-side * rz * 4 },
        };

        BlockPos placementPos = null;
        for (int[] off : offsets) {
            BlockPos check = trackPos.offset(off[0], off[1], off[2]);
            if (check.equals(trackPos)) continue;
            if (level.getBlockState(check).getBlock() instanceof ITrackBlock) continue;
            if (!level.getBlockState(check).canBeReplaced()) continue;
            placementPos = check;
            break;
        }

        if (placementPos == null) {
            LogicLink.LOGGER.warn("placeSignal: no replaceable blocks near {}", targetPos);
            return PlacementAttempt.BLOCKED;
        }

        try {
            // Place the signal block
            BlockState signalState = AllBlocks.TRACK_SIGNAL.getDefaultState();
            level.setBlockAndUpdate(placementPos, signalState);

            BlockEntity be = level.getBlockEntity(placementPos);
            if (!(be instanceof SignalBlockEntity sbe)) {
                LogicLink.LOGGER.warn("placeSignal: no SignalBlockEntity at {} after placement", placementPos);
                level.removeBlock(placementPos, false);
                return PlacementAttempt.FAILED;
            }

            // TrackTargetingBehaviour.targetTrack is RELATIVE to the signal's own position.
            // Both fields are private — set via reflection, then call createEdgePoint() to
            // register the signal with Create's train graph.
                // Use the same NBT initialization path Create itself uses.
                // SmartBlockEntity.loadAdditional() → read() → TrackTargetingBehaviour.read()
                // sets targetTrack (relative offset) and targetDirection.
                BlockPos relativeTrack = candidate.trackPos().subtract(placementPos);
                CompoundTag beNbt = new CompoundTag();
                beNbt.put("TargetTrack", NbtUtils.writeBlockPos(relativeTrack));
                beNbt.putBoolean("TargetDirection", candidate.front());
                sbe.loadWithComponents(beNbt, level.registryAccess());

                // Register the signal with the track graph immediately (tick() will retry if null)
                Object linked = sbe.edgePoint.createEdgePoint();
                sbe.setChanged();
                level.sendBlockUpdated(placementPos, signalState, signalState, 3);

                if (linked == null) {
                LogicLink.LOGGER.warn("placeSignal: placed but createEdgePoint()=null at {}, track={}, front={} — tick() will retry",
                    placementPos, candidate.trackPos(), candidate.front());
                } else {
                LogicLink.LOGGER.info("placeSignal: placed+linked signal at {}, track={}, front={}",
                    placementPos, candidate.trackPos(), candidate.front());
                }
            placeNixieTubeLight(level, placementPos);
            return PlacementAttempt.SUCCESS;

        } catch (Exception e) {
            LogicLink.LOGGER.warn("placeSignal: exception at {}: {}", placementPos, e.toString());
            level.removeBlock(placementPos, false);
            return PlacementAttempt.FAILED;
        }
    }

    private void placeNixieTubeLight(ServerLevel level, BlockPos signalPos) {
        Block lightBlock = resolveNixieTubeLightBlock();
        if (lightBlock == null)
            return;

        // Place light directly on top of the signal.
        BlockPos lightPos = signalPos.above();
        if (!level.getBlockState(lightPos).canBeReplaced())
            return;

        BlockState lightState = lightBlock.defaultBlockState();
        level.setBlockAndUpdate(lightPos, lightState);
        level.sendBlockUpdated(lightPos, lightState, lightState, 3);
    }

    private static Block resolveNixieTubeLightBlock() {
        if (nixieTubeLightResolved)
            return cachedNixieTubeLightBlock;

        nixieTubeLightResolved = true;

        for (String id : NIXIE_TUBE_LIGHT_IDS) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null)
                continue;
            Block block = BuiltInRegistries.BLOCK.getOptional(rl).orElse(null);
            if (block != null && block != Blocks.AIR) {
                cachedNixieTubeLightBlock = block;
                LogicLink.LOGGER.info("SignalTablet: using Nixie Tube block {}", rl);
                return cachedNixieTubeLightBlock;
            }
        }

        // Fallback: best-effort lookup when pack uses a different namespace/path.
        for (ResourceLocation key : BuiltInRegistries.BLOCK.keySet()) {
            String path = key.getPath();
            if (path.contains("nixie") && path.contains("tube")) {
                Block block = BuiltInRegistries.BLOCK.getOptional(key).orElse(null);
                if (block != null && block != Blocks.AIR) {
                    cachedNixieTubeLightBlock = block;
                    LogicLink.LOGGER.info("SignalTablet: using fallback Nixie Tube block {}", key);
                    return cachedNixieTubeLightBlock;
                }
            }
        }

        LogicLink.LOGGER.warn("SignalTablet: Nixie Tube block not found; skipping light placement");
        return null;
    }

    private boolean hasForeignTrackNearby(ServerLevel level, BlockPos placementPos, BlockPos expectedTrackPos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos nearby = placementPos.offset(dx, dy, dz);
                    if (nearby.equals(expectedTrackPos))
                        continue;
                    if (level.getBlockState(nearby).getBlock() instanceof ITrackBlock)
                        return true;
                }
            }
        }
        return false;
    }

    private boolean setSignalMode(SignalBlockEntity signalBlockEntity, BlockPos pos, boolean wantChain) {
        SignalBoundary signal = signalBlockEntity.getSignal();
        if (signal == null) {
            return false;
        }

        SignalBlock.SignalType currentType = signal.getTypeFor(pos);
        SignalBlock.SignalType desiredType = wantChain
                ? SignalBlock.SignalType.CROSS_SIGNAL
                : SignalBlock.SignalType.ENTRY_SIGNAL;
        if (currentType == desiredType) {
            return false;
        }

        signal.cycleSignalType(pos);
        return signal.getTypeFor(pos) == desiredType;
    }

    private void openTabletScreen(ItemStack stack) {
        com.apocscode.logiclink.client.SignalTabletScreen.openFromItem(stack);
    }

    private record PlacementCandidate(BlockPos trackPos, boolean front, double distanceScore) {
    }

    private enum PlacementAttempt {
        SUCCESS,
        BLOCKED,
        FAILED
    }

    private record AutoPlaceResult(int placed, int retyped, int alreadyCorrect, int blocked, int noTrack,
                                   int failed, int skipped) {
    }

    /** Extract scan data from the item's CustomData component. */
    public static CompoundTag getScanData(ItemStack stack) {
        CustomData cd = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag wrapper = cd.copyTag();
        if (wrapper.contains(TAG_SCAN_DATA)) {
            return wrapper.getCompound(TAG_SCAN_DATA);
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tips, TooltipFlag flag) {
        tips.add(Component.literal("\u00A77Right-click to scan signal network"));
        tips.add(Component.literal("\u00A78Shift-right-click to auto-place suggested signals (OP only)"));
        CompoundTag data = getScanData(stack);
        if (data != null) {
            int issues = data.getInt("issueCount");
            if (issues > 0) {
                tips.add(Component.literal("\u00A7c" + issues + " issue(s) detected"));
            } else {
                tips.add(Component.literal("\u00A7aNo issues detected"));
            }
        } else {
            tips.add(Component.literal("\u00A78Not yet scanned"));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Enchantment glint when issues are found
        CompoundTag data = getScanData(stack);
        return data != null && data.getInt("issueCount") > 0;
    }
}
