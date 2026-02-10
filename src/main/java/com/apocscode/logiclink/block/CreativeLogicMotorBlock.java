package com.apocscode.logiclink.block;

import com.apocscode.logiclink.ModRegistry;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Creative Logic Motor — a CC:Tweaked-controlled rotation source with
 * unlimited stress capacity. Like Create's Creative Motor, it generates
 * rotation from nothing, but speed/direction/sequences are all
 * controlled via Lua scripts from an adjacent computer.
 * <p>
 * Place it facing the direction you want the shaft to output, then
 * attach a CC:Tweaked computer to any adjacent side.
 * </p>
 */
public class CreativeLogicMotorBlock extends DirectionalKineticBlock
        implements IBE<CreativeLogicMotorBlockEntity> {

    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 16, 16);

    public CreativeLogicMotorBlock(Properties properties) {
        super(properties);
    }

    // ==================== Shape ====================

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // ==================== Placement ====================

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getNearestLookingDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    // ==================== IRotate (shaft connectivity) ====================

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hideStressImpact() {
        return true; // Creative — unlimited stress capacity
    }

    // ==================== IBE (block entity binding) ====================

    @Override
    public Class<CreativeLogicMotorBlockEntity> getBlockEntityClass() {
        return CreativeLogicMotorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CreativeLogicMotorBlockEntity> getBlockEntityType() {
        return ModRegistry.CREATIVE_LOGIC_MOTOR_BE.get();
    }

    // ==================== Pathfinding ====================

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
