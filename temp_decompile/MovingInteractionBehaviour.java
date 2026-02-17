/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.tterrag.registrate.util.nullness.NonNullConsumer
 *  net.minecraft.core.BlockPos
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate$StructureBlockInfo
 *  org.apache.commons.lang3.tuple.MutablePair
 */
package com.simibubi.create.api.behaviour.interaction;

import com.simibubi.create.api.registry.SimpleRegistry;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.tterrag.registrate.util.nullness.NonNullConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.apache.commons.lang3.tuple.MutablePair;

public abstract class MovingInteractionBehaviour {
    public static final SimpleRegistry<Block, MovingInteractionBehaviour> REGISTRY = SimpleRegistry.create();

    public static <B extends Block> NonNullConsumer<? super B> interactionBehaviour(MovingInteractionBehaviour behaviour) {
        return b -> REGISTRY.register((Block)b, behaviour);
    }

    protected void setContraptionActorData(AbstractContraptionEntity contraptionEntity, int index, StructureTemplate.StructureBlockInfo info, MovementContext ctx) {
        contraptionEntity.getContraption().getActors().remove(index);
        contraptionEntity.getContraption().getActors().add(index, (MutablePair<StructureTemplate.StructureBlockInfo, MovementContext>)MutablePair.of((Object)info, (Object)ctx));
        if (contraptionEntity.level().isClientSide) {
            contraptionEntity.getContraption().invalidateClientContraptionChildren();
        }
    }

    protected void setContraptionBlockData(AbstractContraptionEntity contraptionEntity, BlockPos pos, StructureTemplate.StructureBlockInfo info) {
        if (contraptionEntity.level().isClientSide()) {
            return;
        }
        contraptionEntity.setBlock(pos, info);
    }

    public boolean handlePlayerInteraction(Player player, InteractionHand activeHand, BlockPos localPos, AbstractContraptionEntity contraptionEntity) {
        return true;
    }

    public void handleEntityCollision(Entity entity, BlockPos localPos, AbstractContraptionEntity contraptionEntity) {
    }
}
