/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.createmod.catnip.data.Couple
 *  net.createmod.catnip.data.Iterate
 *  net.createmod.catnip.math.VecHelper
 *  net.createmod.catnip.platform.CatnipServices
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.client.player.RemotePlayer
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.core.Vec3i
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.LevelAccessor
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate$StructureBlockInfo
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.BlockHitResult
 *  net.minecraft.world.phys.Vec3
 *  net.minecraft.world.phys.shapes.VoxelShape
 *  net.neoforged.api.distmarker.Dist
 *  net.neoforged.api.distmarker.OnlyIn
 *  net.neoforged.bus.api.SubscribeEvent
 *  net.neoforged.fml.common.EventBusSubscriber
 *  net.neoforged.neoforge.client.event.InputEvent$InteractionKeyMappingTriggered
 *  net.neoforged.neoforge.event.tick.PlayerTickEvent$Post
 *  org.apache.commons.lang3.mutable.MutableObject
 *  org.jetbrains.annotations.Nullable
 */
package com.simibubi.create.content.contraptions;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ContraptionHandler;
import com.simibubi.create.content.contraptions.sync.ContraptionInteractionPacket;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.TrainRelocator;
import com.simibubi.create.foundation.utility.RaycastHelper;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Map;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(value={Dist.CLIENT})
public class ContraptionHandlerClient {
    @SubscribeEvent
    @OnlyIn(value=Dist.CLIENT)
    public static void preventRemotePlayersWalkingAnimations(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player instanceof RemotePlayer)) {
            return;
        }
        RemotePlayer remotePlayer = (RemotePlayer)player;
        CompoundTag data = remotePlayer.getPersistentData();
        if (!data.contains("LastOverrideLimbSwingUpdate")) {
            return;
        }
        int lastOverride = data.getInt("LastOverrideLimbSwingUpdate");
        data.putInt("LastOverrideLimbSwingUpdate", lastOverride + 1);
        if (lastOverride > 5) {
            data.remove("LastOverrideLimbSwingUpdate");
            data.remove("OverrideLimbSwing");
            return;
        }
        float limbSwing = data.getFloat("OverrideLimbSwing");
        remotePlayer.xo = remotePlayer.getX() - (double)(limbSwing / 4.0f);
        remotePlayer.zo = remotePlayer.getZ();
    }

    @SubscribeEvent
    @OnlyIn(value=Dist.CLIENT)
    public static void rightClickingOnContraptionsGetsHandledLocally(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        if (player.isSpectator()) {
            return;
        }
        if (mc.level == null) {
            return;
        }
        if (!event.isUseItem()) {
            return;
        }
        Couple<Vec3> rayInputs = ContraptionHandlerClient.getRayInputs(player);
        Vec3 origin = (Vec3)rayInputs.getFirst();
        Vec3 target = (Vec3)rayInputs.getSecond();
        AABB aabb = new AABB(origin, target).inflate(16.0);
        Collection contraptions = ((Map)ContraptionHandler.loadedContraptions.get((LevelAccessor)mc.level)).values();
        double bestDistance = Double.MAX_VALUE;
        BlockHitResult bestResult = null;
        AbstractContraptionEntity bestEntity = null;
        for (WeakReference ref : contraptions) {
            double distance;
            BlockHitResult rayTraceResult;
            AbstractContraptionEntity contraptionEntity = (AbstractContraptionEntity)((Object)ref.get());
            if (contraptionEntity == null || !contraptionEntity.getBoundingBox().intersects(aabb) || (rayTraceResult = ContraptionHandlerClient.rayTraceContraption(origin, target, contraptionEntity)) == null || (distance = contraptionEntity.toGlobalVector(rayTraceResult.getLocation(), 1.0f).distanceTo(origin)) > bestDistance) continue;
            bestResult = rayTraceResult;
            bestDistance = distance;
            bestEntity = contraptionEntity;
        }
        if (bestResult == null) {
            return;
        }
        InteractionHand hand = event.getHand();
        Direction face = bestResult.getDirection();
        BlockPos pos = bestResult.getBlockPos();
        if (bestEntity.handlePlayerInteraction((Player)player, pos, face, hand)) {
            CatnipServices.NETWORK.sendToServer((CustomPacketPayload)new ContraptionInteractionPacket(bestEntity, hand, pos, face));
        } else {
            ContraptionHandlerClient.handleSpecialInteractions(bestEntity, (Player)player, pos, face, hand);
        }
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    private static boolean handleSpecialInteractions(AbstractContraptionEntity contraptionEntity, Player player, BlockPos localPos, Direction side, InteractionHand interactionHand) {
        if (AllItems.WRENCH.isIn(player.getItemInHand(interactionHand)) && contraptionEntity instanceof CarriageContraptionEntity) {
            CarriageContraptionEntity car = (CarriageContraptionEntity)contraptionEntity;
            return TrainRelocator.carriageWrenched(car.toGlobalVector(VecHelper.getCenterOf((Vec3i)localPos), 1.0f), car);
        }
        return false;
    }

    @OnlyIn(value=Dist.CLIENT)
    public static Couple<Vec3> getRayInputs(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 origin = player.getEyePosition();
        double reach = player.blockInteractionRange();
        if (mc.hitResult != null && mc.hitResult.getLocation() != null) {
            reach = Math.min(mc.hitResult.getLocation().distanceTo(origin), reach);
        }
        Vec3 target = RaycastHelper.getTraceTarget((Player)player, reach, origin);
        return Couple.create((Object)origin, (Object)target);
    }

    @Nullable
    public static BlockHitResult rayTraceContraption(Vec3 origin, Vec3 target, AbstractContraptionEntity contraptionEntity) {
        MutableObject mutableResult;
        Contraption contraption;
        Vec3 localTarget;
        Vec3 localOrigin = contraptionEntity.toLocalVector(origin, 1.0f);
        RaycastHelper.PredicateTraceResult predicateResult = RaycastHelper.rayTraceUntil(localOrigin, localTarget = contraptionEntity.toLocalVector(target, 1.0f), arg_0 -> ContraptionHandlerClient.lambda$rayTraceContraption$0(contraption = contraptionEntity.getContraption(), localOrigin, localTarget, mutableResult = new MutableObject(), arg_0));
        if (predicateResult == null || predicateResult.missed()) {
            return null;
        }
        BlockHitResult rayTraceResult = (BlockHitResult)mutableResult.getValue();
        return rayTraceResult;
    }

    private static /* synthetic */ boolean lambda$rayTraceContraption$0(Contraption contraption, Vec3 localOrigin, Vec3 localTarget, MutableObject mutableResult, BlockPos p) {
        for (Direction d : Iterate.directions) {
            BlockHitResult rayTrace;
            BlockState state;
            VoxelShape raytraceShape;
            if (d == Direction.UP) continue;
            BlockPos pos = d == Direction.DOWN ? p : p.relative(d);
            StructureTemplate.StructureBlockInfo blockInfo = contraption.getBlocks().get(pos);
            if (blockInfo == null || (raytraceShape = (state = blockInfo.state()).getShape((BlockGetter)contraption.getContraptionWorld(), BlockPos.ZERO.below())).isEmpty() || contraption.isHiddenInPortal(pos) || (rayTrace = raytraceShape.clip(localOrigin, localTarget, pos)) == null) continue;
            mutableResult.setValue((Object)rayTrace);
            return true;
        }
        return false;
    }
}
