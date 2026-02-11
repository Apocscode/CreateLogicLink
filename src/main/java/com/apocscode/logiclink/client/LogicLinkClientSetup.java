package com.apocscode.logiclink.client;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.ModRegistry;
import com.apocscode.logiclink.client.ponder.LogicLinkPonderPlugin;

import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-side initialization for Logic Link mod.
 * Registers Ponder scenes, menu screens, and block entity renderers.
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

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistry.TRAIN_MONITOR_MENU.get(), TrainMonitorScreen::new);
        LogicLink.LOGGER.info("Train Monitor screen registered.");
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistry.TRAIN_MONITOR_BE.get(), TrainMonitorRenderer::new);
        LogicLink.LOGGER.info("Train Monitor renderer registered.");
    }
}
