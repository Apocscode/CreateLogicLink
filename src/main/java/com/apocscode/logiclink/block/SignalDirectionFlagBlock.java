package com.apocscode.logiclink.block;

import com.apocscode.logiclink.ModRegistry;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

public class SignalDirectionFlagBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<SignalDirectionFlagBlock> CODEC = simpleCodec(SignalDirectionFlagBlock::new);
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    public SignalDirectionFlagBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        // Allow placement near tracks and on solid blocks; flag BE resolves exact anchor.
        BlockState below = level.getBlockState(pos.below());
        return below.isFaceSturdy(level, pos.below(), Direction.UP) || below.getBlock() instanceof ITrackBlock;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return canSurvive(state, level, pos) ? state : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SignalDirectionFlagBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SignalDirectionFlagBlockEntity flagBe) {
            Player player = placer instanceof Player p ? p : null;
            CompoundTag blockEntityData = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
            boolean initializedFromTarget = flagBe.initializeFromTrackTargetingData(blockEntityData, player);
            if (!initializedFromTarget) {
                flagBe.initializeFromPlacement(player);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SignalDirectionFlagBlockEntity flagBe)) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            flagBe.resetAnchor();
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("[Signal Flag] anchor reset — right-click to re-anchor"));
            }
            return InteractionResult.SUCCESS;
        }

        flagBe.cycleMode(player);
        return InteractionResult.SUCCESS;
    }
}
