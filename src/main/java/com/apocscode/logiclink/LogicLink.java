package com.apocscode.logiclink;

import org.slf4j.Logger;

import com.apocscode.logiclink.network.SensorNetwork;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Create: Logic Link Peripheral
 *
 * Bridges Create mod's logistical system with ComputerCraft/CC:Tweaked,
 * providing programmable peripherals to monitor and control Create's
 * logistics network from ComputerCraft computers and turtles.
 */
@Mod(LogicLink.MOD_ID)
public class LogicLink {
    public static final String MOD_ID = "logiclink";
    public static final String MOD_NAME = "Create: Logic Link Peripheral";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LogicLink(IEventBus modEventBus, ModContainer modContainer) {
        // Register mod lifecycle events
        modEventBus.addListener(this::commonSetup);

        // Register blocks, items, and block entities
        ModRegistry.BLOCKS.register(modEventBus);
        ModRegistry.ITEMS.register(modEventBus);
        ModRegistry.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModRegistry.CREATIVE_MODE_TABS.register(modEventBus);

        // Register for server/game events
        NeoForge.EVENT_BUS.register(this);

        LOGGER.info("{} initializing...", MOD_NAME);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("{} common setup complete.", MOD_NAME);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("{} ready on server.", MOD_NAME);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        SensorNetwork.clear();
        LOGGER.info("{} sensor network cleared.", MOD_NAME);
    }
}
