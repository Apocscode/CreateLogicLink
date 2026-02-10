package com.apocscode.logiclink.client;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.client.ponder.LogicLinkPonderPlugin;

import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-side initialization for Logic Link mod.
 * Registers Ponder scenes so pressing (W) on our items opens animated tutorials.
 */
@EventBusSubscriber(modid = LogicLink.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class LogicLinkClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            PonderIndex.addPlugin(new LogicLinkPonderPlugin());
            LogicLink.LOGGER.info("Logic Link Ponder scenes registered.");
        });
    }
}
