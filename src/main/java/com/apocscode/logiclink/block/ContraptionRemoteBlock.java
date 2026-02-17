package com.apocscode.logiclink.block;

import com.apocscode.logiclink.ModRegistry;
import com.apocscode.logiclink.controller.RemoteClientHandler;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Contraption Remote Control Block - functions like Create's train controls.
 * <p>
 * The player sits on a separate Create seat, then right-clicks this block's
 * hitbox to activate controller mode. While active, the player's gamepad
 * input is read and sent to bound Logic Drives and Creative Logic Motors.
 * Right-click while standing to view status and bindings.
 * </p>
 */
public class ContraptionRemoteBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<ContraptionRemoteBlock> CODEC = simpleCodec(ContraptionRemoteBlock::new);

    /**
     * Directional hitbox shapes matching the controls geometry.
     * Tightly wraps the control surface, side pillars, and base step
     * - similar to Create's ContraptionControlsBlock shape.
     */
    private static final Map<Direction, VoxelShape> SHAPES = createDirectionalShapes();

    private static Map<Direction, VoxelShape> createDirectionalShapes() {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);

        // NORTH: default model orientation - controls face north, back toward south
        map.put(Direction.NORTH, Shapes.or(
                Block.box(0, 0, 4, 16, 2, 16),      // base step
                Block.box(0, 2, 6, 2, 16, 16),       // left pillar
                Block.box(14, 2, 6, 16, 16, 16),     // right pillar
                Block.box(2, 2, 14, 14, 16, 16),     // back wall
                Block.box(2, 2, 7, 14, 12, 14)       // control/seat area
        ));

        // SOUTH: rotated 180 - controls face south
        map.put(Direction.SOUTH, Shapes.or(
                Block.box(0, 0, 0, 16, 2, 12),       // base step
                Block.box(14, 2, 0, 16, 16, 10),     // left pillar
                Block.box(0, 2, 0, 2, 16, 10),       // right pillar
                Block.box(2, 2, 0, 14, 16, 2),       // back wall
                Block.box(2, 2, 2, 14, 12, 9)        // control/seat area
        ));

        // EAST: rotated 90 - controls face east
        map.put(Direction.EAST, Shapes.or(
                Block.box(0, 0, 0, 12, 2, 16),       // base step
                Block.box(0, 2, 0, 10, 16, 2),       // left pillar
                Block.box(0, 2, 14, 10, 16, 16),     // right pillar
                Block.box(0, 2, 2, 2, 16, 14),       // back wall
                Block.box(2, 2, 2, 9, 12, 14)        // control/seat area
        ));

        // WEST: rotated 270 - controls face west
        map.put(Direction.WEST, Shapes.or(
                Block.box(4, 0, 0, 16, 2, 16),       // base step
                Block.box(6, 2, 14, 16, 16, 16),     // left pillar
                Block.box(6, 2, 0, 16, 16, 2),       // right pillar
                Block.box(14, 2, 2, 16, 16, 14),     // back wall
                Block.box(7, 2, 2, 14, 12, 14)       // control/seat area
        ));

        return map;
    }

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
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.NORTH));
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
        // Context-based interaction:
        //   Seated → right-click activates controller mode (no shift — shift dismounts seats)
        //   Standing → right-click shows status
        if (player.isPassenger()) {
            if (level.isClientSide) {
                activateControllerClient(pos);
            }
            return InteractionResult.SUCCESS;
        }

        // Standing: show status in chat
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ContraptionRemoteBlockEntity remote && player instanceof ServerPlayer sp) {
                remote.sendStatusToPlayer(sp);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hitResult) {
        // Holding a Logic Remote? Let the item handle binding
        if (stack.getItem() instanceof LogicRemoteItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // Context-based interaction:
        //   Seated → right-click activates controller mode
        //   Standing → right-click shows status
        if (player.isPassenger()) {
            if (level.isClientSide) {
                activateControllerClient(pos);
            }
            return ItemInteractionResult.SUCCESS;
        }

        // Standing: show status
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ContraptionRemoteBlockEntity remote) {
                remote.sendStatusToPlayer(sp);
            }
        }
        return ItemInteractionResult.SUCCESS;
    }

    // ==================== Controller Toggle ====================

    @OnlyIn(Dist.CLIENT)
    private static void activateControllerClient(BlockPos pos) {
        RemoteClientHandler.activateForBlock(pos);
    }
}
