package com.apocscode.logiclink.block;

import com.apocscode.logiclink.ModRegistry;
import com.apocscode.logiclink.entity.RemoteSeatEntity;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Contraption Remote Control Block — functions like Create's train controls.
 * <p>
 * Right-click to sit down on the block. While seated, the player's gamepad
 * input is read and sent to bound Logic Drives and Creative Logic Motors.
 * Sneak+right-click to view status and bindings.
 * </p>
 * <p>
 * The block spawns an invisible {@link RemoteSeatEntity} that the player
 * rides, similar to Create's SeatBlock mechanic.
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

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            // Block broken — discard any seat entities at this position
            if (!level.isClientSide) {
                List<RemoteSeatEntity> seats = level.getEntitiesOfClass(RemoteSeatEntity.class, new AABB(pos));
                for (RemoteSeatEntity seat : seats) {
                    seat.ejectPassengers();
                    seat.discard();
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
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
        if (player.isShiftKeyDown()) {
            // Sneak + right-click = show status in chat
            if (!level.isClientSide) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof ContraptionRemoteBlockEntity remote && player instanceof ServerPlayer sp) {
                    remote.sendStatusToPlayer(sp);
                }
            }
            return InteractionResult.SUCCESS;
        }

        // Right-click = sit down on the block
        if (!level.isClientSide) {
            sitDown(level, pos, player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ContraptionRemoteBlockEntity remote) {
                // Holding a Logic Remote? Let the item handle binding
                if (stack.getItem() instanceof LogicRemoteItem) {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }

                if (player.isShiftKeyDown()) {
                    remote.sendStatusToPlayer(sp);
                    return ItemInteractionResult.SUCCESS;
                }
            }
        }

        // Right-click with item (not shifting) = sit down
        if (!player.isShiftKeyDown() && !level.isClientSide) {
            sitDown(level, pos, player);
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // ==================== Seat Mechanic ====================

    /**
     * Checks if a RemoteSeatEntity already exists at this position.
     */
    public static boolean isSeatOccupied(Level level, BlockPos pos) {
        return !level.getEntitiesOfClass(RemoteSeatEntity.class, new AABB(pos)).isEmpty();
    }

    /**
     * Spawns an invisible RemoteSeatEntity and mounts the player on it.
     * If a seat already exists, ejects the current passenger and seats the new player.
     */
    public static void sitDown(Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide) return;

        // Check for existing seat entity
        List<RemoteSeatEntity> seats = level.getEntitiesOfClass(RemoteSeatEntity.class, new AABB(pos));
        if (!seats.isEmpty()) {
            RemoteSeatEntity existingSeat = seats.get(0);
            List<Entity> passengers = existingSeat.getPassengers();
            if (!passengers.isEmpty() && passengers.get(0) instanceof Player) {
                // Already occupied by a player — don't eject
                if (entity instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal("Seat is occupied!")
                            .withStyle(ChatFormatting.RED), true);
                }
                return;
            }
            // Seat exists but empty or has non-player — use it
            existingSeat.ejectPassengers();
            entity.startRiding(existingSeat);
            return;
        }

        // Create new seat entity
        RemoteSeatEntity seat = new RemoteSeatEntity(level);
        seat.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.addFreshEntity(seat);
        entity.startRiding(seat, true);

        if (entity instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.literal("Seated — gamepad controls active")
                    .withStyle(ChatFormatting.GREEN), true);
        }
    }
}
