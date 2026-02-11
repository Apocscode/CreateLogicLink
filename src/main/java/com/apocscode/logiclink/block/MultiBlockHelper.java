package com.apocscode.logiclink.block;

import com.apocscode.logiclink.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Multi-block formation for Train Monitors (CC-monitor style).
 * Scans in the plane of the monitor face: horizontally (right) and vertically (up).
 * Master is the top-left block from the viewer's perspective.
 * Maximum size: 10 wide × 10 tall.
 */
public class MultiBlockHelper {

    public static final int MAX_SIZE = 10;

    /**
     * Get [right, up] directions from the viewer's perspective looking at the monitor face.
     * "right" = horizontal scan direction, "up" = vertical scan direction (world UP).
     */
    public static Direction getRightDir(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.WEST;   // player looks south, right = west
            case SOUTH -> Direction.EAST;   // player looks north, right = east
            case EAST  -> Direction.NORTH;  // player looks west, right = north
            case WEST  -> Direction.SOUTH;  // player looks east, right = south
            default -> Direction.EAST;
        };
    }

    /**
     * Called when a monitor block is placed. Finds connected same-facing monitors
     * and forms the largest rectangular group.
     */
    public static void formMonitorGroup(Level level, BlockPos placedPos, Direction facing) {
        Set<BlockPos> connected = floodFill(level, placedPos, facing);
        assignGroups(level, connected, facing);
    }

    /**
     * Called when a monitor block is about to be destroyed.
     * Dissolves the group and reforms remaining blocks into new groups.
     */
    public static void dissolveAndReform(Level level, BlockPos removedPos, Direction facing) {
        Set<BlockPos> connected = floodFill(level, removedPos, facing);
        connected.remove(removedPos);

        if (connected.isEmpty()) return;

        // Find connected components without the removed block
        List<Set<BlockPos>> components = findConnectedComponents(connected, facing);
        for (Set<BlockPos> component : components) {
            assignGroups(level, component, facing);
        }
    }

    /**
     * Given a set of connected blocks, finds the largest rectangle and assigns master/slave.
     * Master = top-left from viewer's perspective (max Y, min "right" axis).
     */
    private static void assignGroups(Level level, Set<BlockPos> blocks, Direction facing) {
        if (blocks.isEmpty()) return;

        Direction right = getRightDir(facing);
        // Vertical axis is always Y (UP)

        // Find bounds
        int minCol = Integer.MAX_VALUE, maxCol = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

        BlockPos ref = blocks.iterator().next();

        for (BlockPos pos : blocks) {
            int col = getAxisDistance(ref, pos, right);
            minCol = Math.min(minCol, col);
            maxCol = Math.max(maxCol, col);
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
        }

        int gridW = maxCol - minCol + 1;
        int gridH = maxY - minY + 1;
        boolean[][] grid = new boolean[gridH][gridW];

        for (BlockPos pos : blocks) {
            int col = getAxisDistance(ref, pos, right) - minCol;
            int row = maxY - pos.getY(); // row 0 = top (highest Y)
            if (row >= 0 && row < gridH && col >= 0 && col < gridW) {
                grid[row][col] = true;
            }
        }

        // Find largest rectangle
        int bestArea = 0, bestR = 0, bestC = 0, bestW = 0, bestH = 0;

        for (int r = 0; r < gridH; r++) {
            for (int c = 0; c < gridW; c++) {
                if (!grid[r][c]) continue;

                int maxWidth = Math.min(MAX_SIZE, gridW - c);
                for (int h = 1; h <= Math.min(MAX_SIZE, gridH - r); h++) {
                    int widthThisRow = 0;
                    for (int cc = c; cc < c + maxWidth; cc++) {
                        if (grid[r + h - 1][cc]) widthThisRow++;
                        else break;
                    }
                    maxWidth = Math.min(maxWidth, widthThisRow);
                    if (maxWidth == 0) break;

                    int area = maxWidth * h;
                    if (area > bestArea) {
                        bestArea = area;
                        bestR = r;
                        bestC = c;
                        bestW = maxWidth;
                        bestH = h;
                    }
                }
            }
        }

        if (bestArea == 0) {
            for (BlockPos pos : blocks) {
                setMonitorData(level, pos, pos, 1, 1);
            }
            return;
        }

        // Master = top-left = highest Y, leftmost column
        // Grid row 0 = maxY, col 0 = minCol
        BlockPos masterPos = ref
                .relative(right, bestC + minCol)
                .atY(maxY - bestR);

        Set<BlockPos> inRect = new HashSet<>();
        for (int r = 0; r < bestH; r++) {
            for (int c = 0; c < bestW; c++) {
                BlockPos pos = masterPos
                        .relative(right, c)
                        .atY(masterPos.getY() - r);
                inRect.add(pos);
                setMonitorData(level, pos, masterPos, bestW, bestH);
            }
        }

        // Remaining blocks become standalone
        for (BlockPos pos : blocks) {
            if (!inRect.contains(pos)) {
                setMonitorData(level, pos, pos, 1, 1);
            }
        }
    }

    /**
     * Flood-fill to find all connected monitor blocks with same facing.
     * Scans horizontally (right/left) and vertically (up/down).
     */
    private static Set<BlockPos> floodFill(Level level, BlockPos start, Direction facing) {
        Set<BlockPos> result = new HashSet<>();
        if (!isMonitorWithFacing(level, start, facing)) return result;

        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start);
        result.add(start);

        Direction right = getRightDir(facing);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            // Scan 4 neighbors: right, left, up, down
            for (Direction dir : new Direction[]{right, right.getOpposite(), Direction.UP, Direction.DOWN}) {
                BlockPos neighbor = current.relative(dir);
                if (result.contains(neighbor)) continue;
                if (result.size() >= MAX_SIZE * MAX_SIZE) break;

                if (isMonitorWithFacing(level, neighbor, facing)) {
                    result.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return result;
    }

    private static List<Set<BlockPos>> findConnectedComponents(Set<BlockPos> positions, Direction facing) {
        List<Set<BlockPos>> components = new ArrayList<>();
        Set<BlockPos> remaining = new HashSet<>(positions);
        Direction right = getRightDir(facing);

        while (!remaining.isEmpty()) {
            Set<BlockPos> component = new HashSet<>();
            Queue<BlockPos> queue = new LinkedList<>();
            BlockPos start = remaining.iterator().next();
            queue.add(start);
            component.add(start);
            remaining.remove(start);

            while (!queue.isEmpty()) {
                BlockPos current = queue.poll();
                for (Direction dir : new Direction[]{right, right.getOpposite(), Direction.UP, Direction.DOWN}) {
                    BlockPos neighbor = current.relative(dir);
                    if (remaining.contains(neighbor)) {
                        component.add(neighbor);
                        remaining.remove(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
            components.add(component);
        }

        return components;
    }

    private static boolean isMonitorWithFacing(Level level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModRegistry.TRAIN_MONITOR_BLOCK.get())) return false;
        return state.getValue(TrainMonitorBlock.FACING) == facing;
    }

    private static int getAxisDistance(BlockPos from, BlockPos to, Direction axis) {
        return switch (axis) {
            case EAST  -> to.getX() - from.getX();
            case WEST  -> from.getX() - to.getX();
            case SOUTH -> to.getZ() - from.getZ();
            case NORTH -> from.getZ() - to.getZ();
            case UP    -> to.getY() - from.getY();
            case DOWN  -> from.getY() - to.getY();
        };
    }

    private static void setMonitorData(Level level, BlockPos pos, BlockPos masterPos, int width, int height) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TrainMonitorBlockEntity monitor) {
            monitor.setMasterData(masterPos, width, height);
        }
    }
}
