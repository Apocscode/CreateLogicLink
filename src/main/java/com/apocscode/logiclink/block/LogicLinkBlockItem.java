package com.apocscode.logiclink.block;

import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Custom BlockItem for the Logic Link block that supports Create's
 * logistics network linking system.
 * <p>
 * <b>Usage:</b>
 * <ol>
 *   <li>Hold the Logic Link item and right-click on any existing Stock Link
 *       (or any block with LogisticallyLinkedBehaviour) to copy its network frequency.</li>
 *   <li>The item will glow (enchantment glint) when tuned to a network.</li>
 *   <li>Place it on a surface — it will join the linked network.</li>
 *   <li>Right-click in air to clear the frequency.</li>
 *   <li>Shift+right-click to place without linking (creates a new unlinked block).</li>
 * </ol>
 */
public class LogicLinkBlockItem extends BlockItem {

    public LogicLinkBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    /**
     * Show enchantment glint when this item is tuned to a network.
     */
    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return isTuned(stack);
    }

    /**
     * Checks if this item has been tuned to a logistics network frequency.
     */
    public static boolean isTuned(ItemStack stack) {
        return stack.has(DataComponents.BLOCK_ENTITY_DATA);
    }

    /**
     * Gets the network frequency UUID stored on this item, or null if not tuned.
     */
    @Nullable
    public static UUID networkFromStack(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        if (!tag.hasUUID("Freq"))
            return null;
        return tag.getUUID("Freq");
    }

    /**
     * Show tooltip when tuned to a network frequency.
     */
    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext tooltipContext,
                                @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, tooltipContext, tooltipComponents, tooltipFlag);

        CompoundTag tag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        if (!tag.hasUUID("Freq"))
            return;

        tooltipComponents.add(Component.translatable("logiclink.linked.tooltip")
                .withStyle(ChatFormatting.GREEN));
        tooltipComponents.add(Component.translatable("logiclink.linked.tooltip_clear")
                .withStyle(ChatFormatting.GRAY));
    }

    /**
     * Right-click in air: clear the network frequency.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (isTuned(stack)) {
            if (level.isClientSide) {
                level.playSound(player, player.blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                        SoundSource.BLOCKS, 0.75f, 1.0f);
            } else {
                player.displayClientMessage(
                        Component.translatable("logiclink.linked.cleared")
                                .withStyle(ChatFormatting.YELLOW), true);
                stack.remove(DataComponents.BLOCK_ENTITY_DATA);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, usedHand);
    }

    /**
     * Right-click on a block:
     * - If targeting a block with LogisticallyLinkedBehaviour (Stock Link, etc.),
     *   copy its network frequency to this item.
     * - If shift-clicking, place normally (no linking).
     * - Otherwise place normally and show connection/new-network message.
     */
    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        Player player = context.getPlayer();

        if (player == null)
            return InteractionResult.FAIL;

        // Shift+right-click bypasses linking — just place the block
        if (player.isShiftKeyDown())
            return super.useOn(context);

        // Check if the target block has Create's LogisticallyLinkedBehaviour
        LogisticallyLinkedBehaviour link = BlockEntityBehaviour.get(level, pos, LogisticallyLinkedBehaviour.TYPE);
        boolean tuned = isTuned(stack);

        if (link != null) {
            // Target is a logistics-linked block — copy its frequency
            if (level.isClientSide)
                return InteractionResult.SUCCESS;
            if (!link.mayInteract(player)) {
                player.displayClientMessage(
                        Component.translatable("logiclink.linked.protected")
                                .withStyle(ChatFormatting.RED), true);
                return InteractionResult.SUCCESS;
            }

            assignFrequency(stack, player, link.freqId);
            return InteractionResult.SUCCESS;
        }

        // No linked block targeted — place the Logic Link block
        InteractionResult useOn = super.useOn(context);
        if (level.isClientSide || useOn == InteractionResult.FAIL)
            return useOn;

        player.displayClientMessage(tuned
                ? Component.translatable("logiclink.linked.connected").withStyle(ChatFormatting.GREEN)
                : Component.translatable("logiclink.linked.new_unlinked").withStyle(ChatFormatting.YELLOW),
                true);
        return useOn;
    }

    /**
     * Stores the given frequency UUID into the item's block entity data component,
     * so the LogicLinkBlockEntity will receive it when placed.
     */
    public static void assignFrequency(ItemStack stack, Player player, UUID frequency) {
        CompoundTag tag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        tag.putUUID("Freq", frequency);

        player.displayClientMessage(
                Component.translatable("logiclink.linked.tuned").withStyle(ChatFormatting.GREEN), true);

        BlockEntity.addEntityType(tag, com.apocscode.logiclink.ModRegistry.LOGIC_LINK_BE.get());
        stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag));
    }
}
