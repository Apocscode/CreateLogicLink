package com.apocscode.logiclink.client;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.ModRegistry;
import com.apocscode.logiclink.client.ponder.LogicLinkPonderPlugin;
import com.apocscode.logiclink.controller.RemoteClientHandler;

import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-side initialization for Logic Link mod.
 * Registers Ponder scenes, menu screens, block entity renderers,
 * and the Logic Remote controller overlay.
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
        event.register(ModRegistry.LOGIC_REMOTE_MENU.get(), LogicRemoteConfigScreen::new);
        LogicLink.LOGGER.info("Logic Link screens registered.");
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistry.TRAIN_MONITOR_BE.get(), TrainMonitorRenderer::new);
        LogicLink.LOGGER.info("Train Monitor renderer registered.");
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "remote_overlay"),
                RemoteClientHandler.OVERLAY
        );
        LogicLink.LOGGER.info("Logic Remote overlay registered.");
    }
}
