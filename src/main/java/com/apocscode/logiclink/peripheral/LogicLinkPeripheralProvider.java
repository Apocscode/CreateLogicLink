package com.apocscode.logiclink.peripheral;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.ModRegistry;
import com.apocscode.logiclink.block.LogicLinkBlockEntity;
import com.apocscode.logiclink.block.LogicSensorBlockEntity;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;

import net.minecraft.core.Direction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import org.jetbrains.annotations.Nullable;

/**
 * Registers the Logic Link and Logic Sensor peripherals with CC:Tweaked's
 * capability system.
 * <p>
 * When a ComputerCraft computer is placed adjacent to either block,
 * CC:Tweaked will discover the peripheral and make it available in Lua.
 * </p>
 */
@EventBusSubscriber(modid = LogicLink.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class LogicLinkPeripheralProvider {

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Register Logic Link peripheral
        event.registerBlockEntity(
                PeripheralCapability.get(),
                ModRegistry.LOGIC_LINK_BE.get(),
                LogicLinkPeripheralProvider::getLinkPeripheral
        );

        // Register Logic Sensor peripheral
        event.registerBlockEntity(
                PeripheralCapability.get(),
                ModRegistry.LOGIC_SENSOR_BE.get(),
                LogicLinkPeripheralProvider::getSensorPeripheral
        );

        LogicLink.LOGGER.info("Registered Logic Link and Logic Sensor peripherals with CC:Tweaked");
    }

    @Nullable
    private static IPeripheral getLinkPeripheral(LogicLinkBlockEntity blockEntity, @Nullable Direction direction) {
        return new LogicLinkPeripheral(blockEntity);
    }

    @Nullable
    private static IPeripheral getSensorPeripheral(LogicSensorBlockEntity blockEntity, @Nullable Direction direction) {
        return new LogicSensorPeripheral(blockEntity);
    }
}
