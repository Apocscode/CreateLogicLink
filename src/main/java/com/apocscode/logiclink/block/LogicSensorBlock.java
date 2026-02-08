package com.apocscode.logiclink.block;

import com.apocscode.logiclink.ModRegistry;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

/**
 * The Logic Sensor block — a wireless peripheral that attaches to Create
 * machines and reports their data to the Logic Link over the logistics network.
 * <p>
 * Can also function as a standalone CC:Tweaked wired peripheral when placed
 * adjacent to a computer. Attaches to any block face (6 directions).
 * </p>
 * <p>
 * <b>Usage:</b>
 * <ol>
 *   <li>Right-click a Stock Link to copy its network frequency (same as Logic Link).</li>
 *   <li>Place on a Create machine — the sensor reads the machine it's attached to.</li>
 *   <li>The Logic Link can query all sensors on its network via getSensors().</li>
 *   <li>Or connect a CC:Tweaked computer directly to the sensor for wired access.</li>
 * </ol>
 */
public class LogicSensorBlock extends DirectionalBlock implements EntityBlock {

    public static final MapCodec<LogicSensorBlock> CODEC = simpleCodec(LogicSensorBlock::new);

    // Per-direction shapes — 12x12 centered, 3 pixels thick against the attached face
    private static final VoxelShape SHAPE_SOUTH = Block.box(2, 2, 0, 14, 14, 3);
    private static final VoxelShape SHAPE_NORTH = Block.box(2, 2, 13, 14, 14, 16);
    private static final VoxelShape SHAPE_UP    = Block.box(2, 0, 2, 14, 3, 14);
    private static final VoxelShape SHAPE_DOWN  = Block.box(2, 13, 2, 14, 16, 14);
    private static final VoxelShape SHAPE_EAST  = Block.box(0, 2, 2, 3, 14, 14);
    private static final VoxelShape SHAPE_WEST  = Block.box(13, 2, 2, 16, 14, 14);

    public LogicSensorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * Place facing away from the clicked face.
     * The sensor attaches to the target block and faces outward.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getClickedFace());
    }

    /**
     * Gets the position of the block this sensor is reading from.
     * This is the block on the opposite side of the sensor's facing direction.
     */
    public static BlockPos getTargetPos(BlockPos sensorPos, BlockState sensorState) {
        return sensorPos.relative(sensorState.getValue(FACING).getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case NORTH -> SHAPE_NORTH;
            case UP -> SHAPE_UP;
            case DOWN -> SHAPE_DOWN;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
        };
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
