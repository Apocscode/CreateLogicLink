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
 * Logic Drive — a CC:Tweaked-controlled survival rotation generator.
 * Reads rotation from an adjacent kinetic source on the input face and
 * independently generates modified rotation on the output face.
 * <p>
 * Architecture: <b>Consumer + Generator</b><br>
 * The input side senses adjacent rotation without kinetic connection.
 * The output side generates rotation as an independent kinetic source
 * with 256 SU stress capacity. Because the two sides are on separate
 * kinetic networks, direction changes never cause speed conflicts.
 * </p>
 * <p>
 * The block has a {@link #FACING} property indicating the <b>output</b>
 * direction. The opposite face is the <b>input</b> (sensor) side.
 * Blue stripe = input (sensor), orange stripe = CC-controlled output.
 * </p>
 * <h3>Capabilities:</h3>
 * <ul>
 *   <li>On/Off (clutch) — stop generating rotation</li>
 *   <li>Reverse — flip output rotation direction</li>
 *   <li>Speed modifier — scale input speed (×0.5, ×1, ×2, etc.)</li>
 *   <li>Sequenced rotation — degree-based rotation steps</li>
 *   <li>Survival stress — 256 SU capacity, respects Create stress rules</li>
 * </ul>
 */
public class LogicDriveBlock extends KineticBlock
        implements IBE<LogicDriveBlockEntity> {

    /**
     * The direction the output shaft faces. The opposite side is the input.
     * Orange marker = input (drive), light blue marker = output (CC-controlled).
     */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Whether the drive is in its "active" visual state. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 16, 16);

    public LogicDriveBlock(Properties properties) {
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
        // Output shaft only — the input side reads the neighbor's speed without
        // a kinetic connection, so direction changes never cause speed conflicts.
        return face == state.getValue(FACING);
    }

    // ==================== IBE ====================

    @Override
    public Class<LogicDriveBlockEntity> getBlockEntityClass() {
        return LogicDriveBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LogicDriveBlockEntity> getBlockEntityType() {
        return ModRegistry.LOGIC_DRIVE_BE.get();
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
