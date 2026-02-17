/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.base.Objects
 *  net.minecraft.core.BlockPos
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.player.Player
 */
package com.simibubi.create.content.contraptions.actors.trainControls;

import com.google.common.base.Objects;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

public class ControlsInteractionBehaviour
extends MovingInteractionBehaviour {
    @Override
    public boolean handlePlayerInteraction(Player player, InteractionHand activeHand, BlockPos localPos, AbstractContraptionEntity contraptionEntity) {
        if (AllItems.WRENCH.isIn(player.getItemInHand(activeHand))) {
            return false;
        }
        UUID currentlyControlling = contraptionEntity.getControllingPlayer().orElse(null);
        if (currentlyControlling != null) {
            contraptionEntity.stopControlling(localPos);
            if (Objects.equal((Object)currentlyControlling, (Object)player.getUUID())) {
                return true;
            }
        }
        if (!contraptionEntity.startControlling(localPos, player)) {
            return false;
        }
        contraptionEntity.setControllingPlayer(player.getUUID());
        if (player.level().isClientSide) {
            ControlsHandler.startControlling(contraptionEntity, localPos);
        }
        return true;
    }
}
