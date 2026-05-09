package com.apocscode.logiclink.block;

import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.track.TrackTargetingBlockItem;

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
}