package com.apocscode.logiclink.block;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.track.TrackTargetingBlockItem;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Uses Create's native track-targeting item flow:
 * first click selects a track + direction (with Create arrow indicator),
 * second click places the block nearby.
 *
 * Create always removes TRACK_TARGETING_ITEM_SELECTED_POS/DIRECTION after
 * placement (that's what drives the arrow overlay). We save and restore them
 * so the arrow persists for rapid repeated flag placement on the same track.
 * Shift+click clearing is detected and skipped so the user can still clear.
 */
public class SignalDirectionFlagItem extends TrackTargetingBlockItem {

    public SignalDirectionFlagItem(Block block, Properties properties) {
        super(block, properties, EdgePointType.SIGNAL);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();
        Player player = context.getPlayer();
        var hand = context.getHand();

        // Save the current arrow-marker selection before Create's useOn clears it.
        BlockPos savedPos = stack.get(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_POS);
        Boolean savedDir = stack.get(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_DIRECTION);
        boolean hadSelection = savedPos != null;
        // Shift+click with a selection = intentional clear; don't restore afterward.
        boolean isClearing = hadSelection && player != null && player.isShiftKeyDown();

        InteractionResult result = super.useOn(context);

        ItemStack heldStack = player != null ? player.getItemInHand(hand) : stack;

        // After a successful placement Create removes the selection components.
        // Re-apply them so the arrow stays for the next placement on the same track.
        // Restore on both sides so the server doesn't immediately resync a cleared stack.
        boolean missingPos = !heldStack.has(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_POS);
        boolean missingDir = !heldStack.has(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_DIRECTION);
        if (!isClearing && hadSelection && (missingPos || (savedDir != null && missingDir))) {
            heldStack.set(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_POS, savedPos);
            if (savedDir != null)
                heldStack.set(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_DIRECTION, savedDir);
        }

        return result;
    }
}