package com.apocscode.logiclink.peripheral;

import com.apocscode.logiclink.peripheral.storage.StorageControllerPeripheral;
import com.apocscode.logiclink.peripheral.storage.StorageInterfacePeripheral;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;

import net.fxnt.fxntstorage.controller.StorageControllerEntity;
import net.fxnt.fxntstorage.controller.StorageInterfaceEntity;
import net.fxnt.fxntstorage.init.ModBlockEntities;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import org.jetbrains.annotations.Nullable;

/**
 * Registers CC:Tweaked peripherals for Create Storage (fxntstorage) blocks.
 * <p>
 * This class is <b>only loaded when fxntstorage is present</b>. The caller
 * must guard with {@code ModList.get().isLoaded("fxntstorage")} before
 * invoking {@link #register(RegisterCapabilitiesEvent)} — this ensures
 * the JVM never attempts to load this class (and its Create Storage imports)
 * when the mod isn't installed.
 * </p>
 */
public class StoragePeripheralCompat {

    /**
     * Registers peripheral capabilities for Create Storage's block entities.
     * Called conditionally from {@link LogicLinkPeripheralProvider}.
     */
    public static void register(RegisterCapabilitiesEvent event) {
        // Storage Controller — full network access peripheral
        event.registerBlockEntity(
                PeripheralCapability.get(),
                ModBlockEntities.STORAGE_CONTROLLER_ENTITY.get(),
                StoragePeripheralCompat::getControllerPeripheral
        );

        // Storage Interface — remote network access peripheral
        event.registerBlockEntity(
                PeripheralCapability.get(),
                ModBlockEntities.STORAGE_INTERFACE_ENTITY.get(),
                StoragePeripheralCompat::getInterfacePeripheral
        );
    }

    @Nullable
    private static IPeripheral getControllerPeripheral(StorageControllerEntity blockEntity, @Nullable Direction direction) {
        return new StorageControllerPeripheral(blockEntity);
    }

    @Nullable
    private static IPeripheral getInterfacePeripheral(StorageInterfaceEntity blockEntity, @Nullable Direction direction) {
        return new StorageInterfacePeripheral(blockEntity);
    }
}
