/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.InputConstants
 *  net.createmod.catnip.platform.CatnipServices
 *  net.minecraft.client.KeyMapping
 *  net.minecraft.client.Minecraft
 *  net.minecraft.core.BlockPos
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.world.level.LevelAccessor
 *  org.jetbrains.annotations.Nullable
 */
package com.simibubi.create.content.contraptions.actors.trainControls;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsInputPacket;
import com.simibubi.create.foundation.utility.ControlsUtil;
import com.simibubi.create.foundation.utility.CreateLang;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.LevelAccessor;
import org.jetbrains.annotations.Nullable;

public class ControlsHandler {
    public static Collection<Integer> currentlyPressed = new HashSet<Integer>();
    public static int PACKET_RATE = 5;
    private static int packetCooldown;
    private static WeakReference<AbstractContraptionEntity> entityRef;
    private static BlockPos controlsPos;

    public static void levelUnloaded(LevelAccessor level) {
        packetCooldown = 0;
        entityRef = new WeakReference<Object>(null);
        controlsPos = null;
        currentlyPressed.clear();
    }

    public static void startControlling(AbstractContraptionEntity entity, BlockPos controllerLocalPos) {
        entityRef = new WeakReference<AbstractContraptionEntity>(entity);
        controlsPos = controllerLocalPos;
        Minecraft.getInstance().player.displayClientMessage((Component)CreateLang.translateDirect("contraption.controls.start_controlling", entity.getContraptionName()), true);
    }

    public static void stopControlling() {
        ControlsUtil.getControls().forEach(kb -> kb.setDown(ControlsUtil.isActuallyPressed(kb)));
        AbstractContraptionEntity abstractContraptionEntity = (AbstractContraptionEntity)((Object)entityRef.get());
        if (!currentlyPressed.isEmpty() && abstractContraptionEntity != null) {
            CatnipServices.NETWORK.sendToServer((CustomPacketPayload)new ControlsInputPacket(currentlyPressed, false, abstractContraptionEntity.getId(), controlsPos, false));
        }
        packetCooldown = 0;
        entityRef = new WeakReference<Object>(null);
        controlsPos = null;
        currentlyPressed.clear();
        Minecraft.getInstance().player.displayClientMessage((Component)CreateLang.translateDirect("contraption.controls.stop_controlling", new Object[0]), true);
    }

    public static void tick() {
        AbstractContraptionEntity entity = (AbstractContraptionEntity)((Object)entityRef.get());
        if (entity == null) {
            return;
        }
        if (packetCooldown > 0) {
            --packetCooldown;
        }
        if (entity.isRemoved() || InputConstants.isKeyDown((long)Minecraft.getInstance().getWindow().getWindow(), (int)256)) {
            BlockPos pos = controlsPos;
            ControlsHandler.stopControlling();
            CatnipServices.NETWORK.sendToServer((CustomPacketPayload)new ControlsInputPacket(currentlyPressed, false, entity.getId(), pos, true));
            return;
        }
        List<KeyMapping> controls = ControlsUtil.getControls();
        HashSet<Integer> pressedKeys = new HashSet<Integer>();
        for (int i = 0; i < controls.size(); ++i) {
            if (!ControlsUtil.isActuallyPressed(controls.get(i))) continue;
            pressedKeys.add(i);
        }
        HashSet<Integer> newKeys = new HashSet<Integer>(pressedKeys);
        Collection<Integer> releasedKeys = currentlyPressed;
        newKeys.removeAll(releasedKeys);
        releasedKeys.removeAll(pressedKeys);
        if (!releasedKeys.isEmpty()) {
            CatnipServices.NETWORK.sendToServer((CustomPacketPayload)new ControlsInputPacket(releasedKeys, false, entity.getId(), controlsPos, false));
        }
        if (!newKeys.isEmpty()) {
            CatnipServices.NETWORK.sendToServer((CustomPacketPayload)new ControlsInputPacket(newKeys, true, entity.getId(), controlsPos, false));
            packetCooldown = PACKET_RATE;
        }
        if (packetCooldown == 0) {
            CatnipServices.NETWORK.sendToServer((CustomPacketPayload)new ControlsInputPacket(pressedKeys, true, entity.getId(), controlsPos, false));
            packetCooldown = PACKET_RATE;
        }
        currentlyPressed = pressedKeys;
        controls.forEach(kb -> kb.setDown(false));
    }

    @Nullable
    public static AbstractContraptionEntity getContraption() {
        return (AbstractContraptionEntity)((Object)entityRef.get());
    }

    @Nullable
    public static BlockPos getControlsPos() {
        return controlsPos;
    }

    static {
        entityRef = new WeakReference<Object>(null);
    }
}
