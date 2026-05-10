package com.apocscode.logiclink.block;

import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.track.TrackTargetingBlockItem;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/**
 * Uses Create's native track-targeting item flow:
 * first click selects a track + direction (with Create arrow indicator),
 * second click places the block nearby.
 */
public class SignalDirectionFlagItem extends TrackTargetingBlockItem {

    public SignalDirectionFlagItem(Block block, Properties properties) {
        super(block, properties, EdgePointType.SIGNAL);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        CompoundTag before = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        boolean hadTrackSelection = before.contains("TargetTrack");

        InteractionResult result = super.useOn(context);

        // Keep the selected track+direction on the item so the Create arrow marker
        // remains after each placement for rapid repeated flag placement.
        if (!context.getLevel().isClientSide && hadTrackSelection && result.consumesAction()) {
            CompoundTag after = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
            if (!after.contains("TargetTrack")) {
                stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(before));
            }
        }

        return result;
    }
}