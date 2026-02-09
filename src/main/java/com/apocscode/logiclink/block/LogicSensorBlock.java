package com.apocscode.logiclink.block;

import com.apocscode.logiclink.ModRegistry;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

/**
 * The Logic Sensor block — a wireless peripheral that attaches flat to any
 * surface (floor, wall, ceiling) like Create's Stock Link, and reports data
 * from the adjacent block to the Logic Link over the logistics network.
 * <p>
 * Uses {@link FaceAttachedHorizontalDirectionalBlock} for proper surface
 * attachment with FACE (floor/wall/ceiling) + FACING (horizontal direction).
 * </p>
 * <p>
 * <b>Usage:</b>
 * <ol>
 *   <li>Right-click a Stock Link to copy its network frequency.</li>
 *   <li>Place on a Create machine — the sensor reads the machine it's attached to.</li>
 *   <li>The Logic Link can query all sensors on its network via getSensors().</li>
 *   <li>Or connect a CC:Tweaked computer directly to the sensor for wired access.</li>
 * </ol>
 */
public class LogicSensorBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<LogicSensorBlock> CODEC = simpleCodec(LogicSensorBlock::new);

    // Shape constants: 14x14 face, 5 pixels thick
    private static final VoxelShape SHAPE_FLOOR   = Block.box(1, 0, 1, 15, 5, 15);   // pad at y=0
    private static final VoxelShape SHAPE_CEILING = Block.box(1, 11, 1, 15, 16, 15);  // pad at y=16
    // Wall shapes keyed by FACING (the direction the sensor faces / connects toward)
    private static final VoxelShape SHAPE_WALL_NORTH = Block.box(1, 1, 0, 15, 15, 5);    // pad at z=0
    private static final VoxelShape SHAPE_WALL_SOUTH = Block.box(1, 1, 11, 15, 15, 16);  // pad at z=16
    private static final VoxelShape SHAPE_WALL_EAST  = Block.box(11, 1, 1, 16, 15, 15);  // pad at x=16
    private static final VoxelShape SHAPE_WALL_WEST  = Block.box(0, 1, 1, 5, 15, 15);    // pad at x=0

    public LogicSensorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACE, AttachFace.FLOOR)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING);
    }

    /**
     * Places the sensor on the exact surface that was clicked.
     * Uses the clicked face to determine FACE and FACING, rather than
     * vanilla's nearest-looking-direction iteration, so the sensor always
     * attaches to the intended surface and reads the correct target block.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        Direction horizontalFacing = context.getHorizontalDirection();

        AttachFace face;
        Direction facing;

        if (clickedFace == Direction.UP) {
            // Clicked top of block → sensor on floor
            face = AttachFace.FLOOR;
            facing = horizontalFacing;
        } else if (clickedFace == Direction.DOWN) {
            // Clicked bottom of block → sensor on ceiling
            face = AttachFace.CEILING;
            facing = horizontalFacing.getOpposite(); // Ceiling flip for visual consistency
        } else {
            // Clicked a horizontal face → sensor on wall
            face = AttachFace.WALL;
            facing = clickedFace.getOpposite(); // Sensor connects toward the clicked block
        }

        return this.defaultBlockState()
                .setValue(FACE, face)
                .setValue(FACING, facing);
    }

    /**
     * Always allow placement — no support block required (like Create's Stock Link).
     */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    /**
     * Gets the position of the block this sensor is reading from.
     * This is the block on the surface the sensor is attached to.
     */
    public static BlockPos getTargetPos(BlockPos sensorPos, BlockState sensorState) {
        AttachFace face = sensorState.getValue(FACE);
        if (face == AttachFace.FLOOR) {
            return sensorPos.below();
        } else if (face == AttachFace.CEILING) {
            return sensorPos.above();
        } else {
            // WALL — target is behind the sensor, in the FACING direction
            return sensorPos.relative(sensorState.getValue(FACING));
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        AttachFace face = state.getValue(FACE);
        if (face == AttachFace.FLOOR) {
            return SHAPE_FLOOR;
        } else if (face == AttachFace.CEILING) {
            return SHAPE_CEILING;
        } else {
            // WALL — shape determined by FACING (direction toward the wall)
            return switch (state.getValue(FACING)) {
                case NORTH -> SHAPE_WALL_NORTH;
                case SOUTH -> SHAPE_WALL_SOUTH;
                case EAST  -> SHAPE_WALL_EAST;
                case WEST  -> SHAPE_WALL_WEST;
                default    -> SHAPE_FLOOR;
            };
        }
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LogicSensorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> blockEntityType) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(blockEntityType, ModRegistry.LOGIC_SENSOR_BE.get(),
                LogicSensorBlockEntity::serverTick);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    protected static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> givenType, BlockEntityType<E> expectedType, BlockEntityTicker<? super E> ticker) {
        return expectedType == givenType ? (BlockEntityTicker<A>) ticker : null;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof LogicSensorBlockEntity be) {
                be.onRemoved();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
