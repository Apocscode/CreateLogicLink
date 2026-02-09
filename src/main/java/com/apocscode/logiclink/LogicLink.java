package com.apocscode.logiclink;

import org.slf4j.Logger;

import com.apocscode.logiclink.block.LogicLinkBlockItem;
import com.apocscode.logiclink.block.LogicSensorBlockItem;
import com.apocscode.logiclink.network.LinkNetwork;
import com.apocscode.logiclink.network.NetworkHighlightPayload;
import com.apocscode.logiclink.network.SensorNetwork;
import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

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

    /** Tick counter for throttling held-item highlight checks. */
    private int highlightTickCounter = 0;
    private static final int HIGHLIGHT_CHECK_INTERVAL = 5; // every 5 ticks (250ms)

    public LogicLink(IEventBus modEventBus, ModContainer modContainer) {
        // Register mod lifecycle events
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);

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

    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(MOD_ID);
        registrar.playToClient(
                NetworkHighlightPayload.TYPE,
                NetworkHighlightPayload.STREAM_CODEC,
                NetworkHighlightPayload::handle
        );
    }

    /**
     * Server-side player tick: when a player holds a tuned Logic Link or Logic Sensor
     * item, continuously send highlight positions for all blocks on that network.
     * This mirrors how Create highlights linked blocks when holding a tuned Stock Link.
     */
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        highlightTickCounter++;
        if (highlightTickCounter < HIGHLIGHT_CHECK_INTERVAL) return;
        highlightTickCounter = 0;

        // Check main hand and off hand for tuned items
        UUID freq = getHeldFrequency(sp.getMainHandItem());
        if (freq == null) {
            freq = getHeldFrequency(sp.getOffhandItem());
        }

        if (freq != null) {
            LinkNetwork.sendNetworkHighlight(sp.level(), freq, sp);
        }
    }

    /**
     * Extracts the network frequency UUID from a held item if it's a tuned
     * Logic Link or Logic Sensor block item.
     */
    private UUID getHeldFrequency(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() instanceof LogicLinkBlockItem) {
            return LogicLinkBlockItem.networkFromStack(stack);
        }
        if (stack.getItem() instanceof LogicSensorBlockItem) {
            return LogicSensorBlockItem.networkFromStack(stack);
        }
        return null;
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("{} ready on server.", MOD_NAME);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        SensorNetwork.clear();
        LinkNetwork.clear();
        LOGGER.info("{} networks cleared.", MOD_NAME);
    }
}
