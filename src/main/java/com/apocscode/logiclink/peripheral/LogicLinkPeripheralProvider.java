package com.apocscode.logiclink.peripheral;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.ModRegistry;
import com.apocscode.logiclink.block.ContraptionRemoteBlockEntity;
import com.apocscode.logiclink.block.CreativeLogicMotorBlockEntity;
import com.apocscode.logiclink.block.LogicLinkBlockEntity;
import com.apocscode.logiclink.block.LogicDriveBlockEntity;
import com.apocscode.logiclink.block.LogicSensorBlockEntity;
import com.apocscode.logiclink.block.RedstoneControllerBlockEntity;
import com.apocscode.logiclink.block.TrainControllerBlockEntity;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;

import net.minecraft.core.Direction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
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

        // Register Redstone Controller peripheral
        event.registerBlockEntity(
                PeripheralCapability.get(),
                ModRegistry.REDSTONE_CONTROLLER_BE.get(),
                LogicLinkPeripheralProvider::getRedstoneControllerPeripheral
        );

        // Register Creative Logic Motor peripheral
        event.registerBlockEntity(
                PeripheralCapability.get(),
                ModRegistry.CREATIVE_LOGIC_MOTOR_BE.get(),
                LogicLinkPeripheralProvider::getCreativeLogicMotorPeripheral
        );

        // Register Logic Drive peripheral
        event.registerBlockEntity(
                PeripheralCapability.get(),
                ModRegistry.LOGIC_DRIVE_BE.get(),
                LogicLinkPeripheralProvider::getLogicDrivePeripheral
        );

        // Register Train Controller peripheral
        event.registerBlockEntity(
                PeripheralCapability.get(),
                ModRegistry.TRAIN_CONTROLLER_BE.get(),
                LogicLinkPeripheralProvider::getTrainControllerPeripheral
        );

        LogicLink.LOGGER.info("Registered all peripherals with CC:Tweaked (Logic Link, Sensor, Redstone Controller, Creative Motor, Drive, Train Controller, Contraption Remote)");

        // Register Contraption Remote peripheral
        event.registerBlockEntity(
                PeripheralCapability.get(),
                ModRegistry.CONTRAPTION_REMOTE_BE.get(),
                LogicLinkPeripheralProvider::getContraptionRemotePeripheral
        );

        // ==================== Optional: Create Storage Peripherals ====================
        if (ModList.get().isLoaded("fxntstorage")) {
            StoragePeripheralCompat.register(event);
            LogicLink.LOGGER.info("Create Storage (fxntstorage) detected — registered Storage Controller and Storage Interface peripherals");
        }
    }

    @Nullable
    private static IPeripheral getLinkPeripheral(LogicLinkBlockEntity blockEntity, @Nullable Direction direction) {
        return new LogicLinkPeripheral(blockEntity);
    }

    @Nullable
    private static IPeripheral getSensorPeripheral(LogicSensorBlockEntity blockEntity, @Nullable Direction direction) {
        return new LogicSensorPeripheral(blockEntity);
    }

    @Nullable
    private static IPeripheral getRedstoneControllerPeripheral(RedstoneControllerBlockEntity blockEntity, @Nullable Direction direction) {
        return new RedstoneControllerPeripheral(blockEntity);
    }

    @Nullable
    private static IPeripheral getCreativeLogicMotorPeripheral(CreativeLogicMotorBlockEntity blockEntity, @Nullable Direction direction) {
        return new CreativeLogicMotorPeripheral(blockEntity);
    }

    @Nullable
    private static IPeripheral getTrainControllerPeripheral(TrainControllerBlockEntity blockEntity, @Nullable Direction direction) {
        return new TrainControllerPeripheral(blockEntity);
    }

    @Nullable
    private static IPeripheral getLogicDrivePeripheral(LogicDriveBlockEntity blockEntity, @Nullable Direction direction) {
        return new LogicDrivePeripheral(blockEntity);
    }

    @Nullable
    private static IPeripheral getContraptionRemotePeripheral(ContraptionRemoteBlockEntity blockEntity, @Nullable Direction direction) {
        return new ContraptionRemotePeripheral(blockEntity);
    }
}
