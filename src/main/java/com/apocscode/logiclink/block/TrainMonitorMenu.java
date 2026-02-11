package com.apocscode.logiclink.block;

import com.apocscode.logiclink.ModRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Menu container for the Train Network Monitor GUI.
 * Has no inventory slots — purely a data display interface.
 * Carries the master block position for the screen to read from.
 */
public class TrainMonitorMenu extends AbstractContainerMenu {

    private final BlockPos masterPos;

    /** Server-side constructor */
    public TrainMonitorMenu(int windowId, Inventory inv, BlockPos masterPos) {
        super(ModRegistry.TRAIN_MONITOR_MENU.get(), windowId);
        this.masterPos = masterPos;
    }

    /** Client-side constructor (from network buffer) */
    public TrainMonitorMenu(int windowId, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(windowId, inv, buf.readBlockPos());
    }

    public BlockPos getMasterPos() {
        return masterPos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
