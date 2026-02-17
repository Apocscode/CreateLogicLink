/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.buffer.ByteBuf
 *  net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders
 *  net.createmod.catnip.net.base.BasePacketPayload$PacketTypeProvider
 *  net.createmod.catnip.net.base.ServerboundPacketPayload
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Position
 *  net.minecraft.core.Vec3i
 *  net.minecraft.network.codec.ByteBufCodecs
 *  net.minecraft.network.codec.StreamCodec
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.LevelAccessor
 *  net.minecraft.world.phys.Vec3
 */
package com.simibubi.create.content.contraptions.actors.trainControls;

import com.simibubi.create.AllPackets;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsServerHandler;
import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;

public record ControlsInputPacket(List<Integer> activatedButtons, boolean press, int contraptionEntityId, BlockPos controlsPos, boolean stopControlling) implements ServerboundPacketPayload
{
    public static final StreamCodec<ByteBuf, ControlsInputPacket> STREAM_CODEC = StreamCodec.composite((StreamCodec)CatnipStreamCodecBuilders.list((StreamCodec)ByteBufCodecs.VAR_INT), ControlsInputPacket::activatedButtons, (StreamCodec)ByteBufCodecs.BOOL, ControlsInputPacket::press, (StreamCodec)ByteBufCodecs.INT, ControlsInputPacket::contraptionEntityId, (StreamCodec)BlockPos.STREAM_CODEC, ControlsInputPacket::controlsPos, (StreamCodec)ByteBufCodecs.BOOL, ControlsInputPacket::stopControlling, ControlsInputPacket::new);

    public ControlsInputPacket(Collection<Integer> activatedButtons, boolean press, int contraptionEntityId, BlockPos controlsPos, boolean stopControlling) {
        this(List.copyOf(activatedButtons), press, contraptionEntityId, controlsPos, stopControlling);
    }

    public void handle(ServerPlayer player) {
        Level world = player.getCommandSenderWorld();
        UUID uniqueID = player.getUUID();
        if (player.isSpectator() && this.press) {
            return;
        }
        Entity entity = world.getEntity(this.contraptionEntityId);
        if (!(entity instanceof AbstractContraptionEntity)) {
            return;
        }
        AbstractContraptionEntity ace = (AbstractContraptionEntity)entity;
        if (this.stopControlling) {
            ace.stopControlling(this.controlsPos);
            return;
        }
        if (ace.toGlobalVector(Vec3.atCenterOf((Vec3i)this.controlsPos), 0.0f).closerThan((Position)player.position(), 16.0)) {
            ControlsServerHandler.receivePressed((LevelAccessor)world, ace, this.controlsPos, uniqueID, this.activatedButtons, this.press);
        }
    }

    public BasePacketPayload.PacketTypeProvider getTypeProvider() {
        return AllPackets.CONTROLS_INPUT;
    }
}
