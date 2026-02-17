/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  dev.engine_room.flywheel.lib.transform.PoseTransformStack
 *  dev.engine_room.flywheel.lib.transform.TransformStack
 *  net.createmod.catnip.animation.LerpedFloat
 *  net.createmod.catnip.animation.LerpedFloat$Chaser
 *  net.createmod.catnip.math.AngleHelper
 *  net.createmod.catnip.math.VecHelper
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.core.Direction$Axis
 *  net.minecraft.core.HolderLookup$Provider
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.DyeColor
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.LevelAccessor
 *  net.minecraft.world.level.block.entity.BlockEntityType
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.block.state.properties.Property
 *  net.minecraft.world.phys.Vec3
 */
package com.simibubi.create.content.contraptions.actors.contraptionControls;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllTags;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.foundation.utility.DyeHelper;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import java.util.List;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

public class ContraptionControlsBlockEntity
extends SmartBlockEntity {
    public FilteringBehaviour filtering;
    public boolean disabled;
    public boolean powered;
    public LerpedFloat indicator = LerpedFloat.angular().startWithValue(0.0);
    public LerpedFloat button = LerpedFloat.linear().startWithValue(0.0).chase(0.0, 0.125, LerpedFloat.Chaser.EXP);

    public ContraptionControlsBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        this.filtering = new FilteringBehaviour(this, new ControlsSlot());
        behaviours.add(this.filtering);
        this.filtering.setLabel(CreateLang.translateDirect("contraptions.contoller.target", new Object[0]));
        this.filtering.withPredicate(AllTags.AllItemTags.CONTRAPTION_CONTROLLED::matches);
    }

    public void pressButton() {
        this.button.setValue(1.0);
    }

    public void updatePoweredState() {
        if (this.level.isClientSide()) {
            return;
        }
        boolean powered = this.level.hasNeighborSignal(this.worldPosition);
        if (this.powered == powered) {
            return;
        }
        this.powered = powered;
        this.disabled = powered;
        this.notifyUpdate();
    }

    @Override
    public void initialize() {
        super.initialize();
        this.updatePoweredState();
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level.isClientSide()) {
            return;
        }
        this.tickAnimations();
        int value = this.disabled ? 180 : 0;
        this.indicator.setValue((double)value);
        this.indicator.updateChaseTarget((float)value);
    }

    public void tickAnimations() {
        this.button.tickChaser();
        this.indicator.tickChaser();
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.disabled = tag.getBoolean("Disabled");
        this.powered = tag.getBoolean("Powered");
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Disabled", this.disabled);
        tag.putBoolean("Powered", this.powered);
    }

    public static void sendStatus(Player player, ItemStack filter, boolean enabled) {
        MutableComponent state = CreateLang.translate("contraption.controls.actor_toggle." + (enabled ? "on" : "off"), new Object[0]).color(((Integer)DyeHelper.getDyeColors(enabled ? DyeColor.LIME : DyeColor.ORANGE).getFirst()).intValue()).component();
        if (filter.isEmpty()) {
            CreateLang.translate("contraption.controls.all_actor_toggle", state).sendStatus(player);
            return;
        }
        CreateLang.translate("contraption.controls.specific_actor_toggle", filter.getHoverName().getString(), state).sendStatus(player);
    }

    public static class ControlsSlot
    extends ValueBoxTransform.Sided {
        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            Direction facing = (Direction)state.getValue((Property)ControlsBlock.FACING);
            float yRot = AngleHelper.horizontalAngle((Direction)facing);
            return VecHelper.rotateCentered((Vec3)VecHelper.voxelSpace((double)8.0, (double)14.0, (double)5.5), (double)yRot, (Direction.Axis)Direction.Axis.Y);
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            Direction facing = (Direction)state.getValue((Property)ControlsBlock.FACING);
            float yRot = AngleHelper.horizontalAngle((Direction)facing);
            ((PoseTransformStack)TransformStack.of((PoseStack)ms).rotateYDegrees(yRot + 180.0f)).rotateXDegrees(67.5f);
        }

        @Override
        public float getScale() {
            return 0.508f;
        }

        @Override
        protected Vec3 getSouthLocation() {
            return Vec3.ZERO;
        }
    }
}
