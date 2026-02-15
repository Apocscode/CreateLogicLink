package com.apocscode.logiclink.block;

import com.apocscode.logiclink.ModRegistry;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

/**
 * Contraption Remote Control Block — placeable on contraptions.
 * <p>
 * When a player seated on the same contraption right-clicks this block,
 * it opens a control GUI for bound drives and motors. The block relays
 * controller input from the seated player to the linked devices.
 * </p>
 * <p>
 * Can also be used stationary: right-click to open the binding GUI,
 * then sneak+click drives/motors to add them as targets (similar to
 * the Logic Remote item but as a block).
 * </p>
 */
public class ContraptionRemoteBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<ContraptionRemoteBlock> CODEC = simpleCodec(ContraptionRemoteBlock::new);

    /** Slightly smaller than full block for visual distinction. */
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public ContraptionRemoteBlock(Properties properties) {
        super(properties);
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
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // ==================== Block Entity ====================

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ContraptionRemoteBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModRegistry.CONTRAPTION_REMOTE_BE.get(),
            (lvl, pos, st, be) -> be.serverTick());
    }

    @SuppressWarnings("unchecked")
    @Nullable
    protected static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> givenType, BlockEntityType<E> expectedType, BlockEntityTicker<? super E> ticker) {
        return expectedType == givenType ? (BlockEntityTicker<A>) ticker : null;
    }

    // ==================== Interaction ====================

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                BlockHitResult hitResult) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ContraptionRemoteBlockEntity remote) {
            if (player instanceof ServerPlayer sp) {
                // Send status message showing current bindings
                remote.sendStatusToPlayer(sp);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ContraptionRemoteBlockEntity remote) {
            if (player instanceof ServerPlayer sp) {
                remote.sendStatusToPlayer(sp);
            }
        }
        return ItemInteractionResult.CONSUME;
    }
}
