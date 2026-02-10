package com.apocscode.logiclink.block;

import com.apocscode.logiclink.ModRegistry;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Logic Motor (standard) — a CC:Tweaked-controlled rotation modifier.
 * Requires an external rotation source (shaft, water wheel, etc.) and
 * can modify, gate, or reverse that rotation via Lua scripts.
 * <p>
 * Functions like a programmable Clutch + Gearshift + Sequenced Gearshift
 * all in one, controlled from a CC:Tweaked computer. Uses Create's
 * gearshift-style model with a horizontal axis shaft passing through.
 * </p>
 * <p>
 * The block has a {@link #FACING} property indicating the <b>output</b>
 * direction. The opposite face is the <b>input</b> (drive) side.
 * Orange stripe = input, light blue stripe = CC-controlled output.
 * </p>
 * <h3>Capabilities:</h3>
 * <ul>
 *   <li>On/Off (clutch) — disconnect rotation</li>
 *   <li>Reverse — flip rotation direction</li>
 *   <li>Speed modifier — scale input speed (×0.5, ×1, ×2, etc.)</li>
 *   <li>Sequenced rotation — degree-based rotation steps</li>
 * </ul>
 */
public class LogicMotorBlock extends KineticBlock
        implements IBE<LogicMotorBlockEntity> {

    /**
     * The direction the output shaft faces. The opposite side is the input.
     * Orange marker = input (drive), light blue marker = output (CC-controlled).
     */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Whether the motor is in its "active" visual state. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 16, 16);

    public LogicMotorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Output faces where the player is looking
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    // ==================== Shape ====================

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // ==================== IRotate ====================

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        // Shaft passes through both ends of the horizontal axis
        return face.getAxis() == state.getValue(FACING).getAxis();
    }

    // ==================== IBE ====================

    @Override
    public Class<LogicMotorBlockEntity> getBlockEntityClass() {
        return LogicMotorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LogicMotorBlockEntity> getBlockEntityType() {
        return ModRegistry.LOGIC_MOTOR_BE.get();
    }

    // ==================== Kinetic equivalence ====================

    @Override
    protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
        // Only axis changes re-propagate the network; ACTIVE or facing-flip don't
        if (oldState.getValue(FACING).getAxis() != newState.getValue(FACING).getAxis())
            return false;
        return true;
    }

    // ==================== Rotation & Mirror ====================

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // ==================== Pathfinding ====================

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
