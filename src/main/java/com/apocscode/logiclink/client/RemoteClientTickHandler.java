package com.apocscode.logiclink.client;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.controller.RemoteClientHandler;
import com.apocscode.logiclink.controller.SeatInputHandler;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client-side tick handler for the Logic Remote controller.
 * Calls RemoteClientHandler.tick() every client tick to process
 * gamepad input and send button/axis packets while in ACTIVE mode.
 * Also advances the LogicRemoteItemRenderer animation each tick.
 * <p>
 * Separate from LogicLinkClientSetup because this subscribes to
 * NeoForge.EVENT_BUS (game events) rather than Bus.MOD (mod lifecycle).
 */
@EventBusSubscriber(modid = LogicLink.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class RemoteClientTickHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LogicRemoteItemRenderer.earlyTick();    // equip progress (before input)
        RemoteClientHandler.tick();
        SeatInputHandler.tick();
        LogicRemoteItemRenderer.tick();         // button depression (after input)
    }
}
