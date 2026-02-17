package com.apocscode.logiclink.controller;

import com.apocscode.logiclink.ModRegistry;
import com.apocscode.logiclink.block.LogicRemoteItem;
import com.apocscode.logiclink.client.gui.ControllerItemSlot;
import com.simibubi.create.foundation.gui.menu.GhostItemMenu;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Ghost item menu for the Logic Remote frequency configuration.
 * 50 ghost slots: 15 buttons × 2 frequency items + 10 axes × 2 frequency items.
 * Two pages: buttons (page 0) and axes (page 1), toggled via tab buttons.
 * <p>
 * Port of CTC's TweakedLinkedControllerMenu using Create's GhostItemMenu.
 */
public class LogicRemoteMenu extends GhostItemMenu<ItemStack> {

    private boolean isSecondPage = false;

    /** Client-side constructor — matches IContainerFactory signature for IMenuTypeExtension */
    public LogicRemoteMenu(int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(ModRegistry.LOGIC_REMOTE_MENU.get(), id, inv, extraData);
    }

    /** Server-side constructor — used via LogicRemoteMenu.create() */
    public LogicRemoteMenu(MenuType<?> type, int id, Inventory inv, ItemStack filterItem) {
        super(type, id, inv, filterItem);
    }

    public static LogicRemoteMenu create(int id, Inventory inv, ItemStack filterItem) {
        return new LogicRemoteMenu(ModRegistry.LOGIC_REMOTE_MENU.get(), id, inv, filterItem);
    }

    /**
     * Toggle between button page (false) and axis page (true).
     */
    public void setPage(boolean second) {
        isSecondPage = second;
        int slotIndex = this.slots.size() - 50;
        for (int r = 0; r < 2; r++) {
            boolean isVisible = (isSecondPage && r == 1) || (!isSecondPage && r == 0);
            for (int index = 0; index < guiItemSlots[r].length; index += 2) {
                for (int row = 0; row < 2; ++row) {
                    ControllerItemSlot t = (ControllerItemSlot) (this.slots.get(slotIndex));
                    t.setActive(isVisible);
                    slotIndex++;
                }
            }
        }
    }

    @Override
    protected ItemStack createOnClient(RegistryFriendlyByteBuf extraData) {
        return ItemStack.STREAM_CODEC.decode(extraData);
    }

    @Override
    protected ItemStackHandler createGhostInventory() {
        return LogicRemoteItem.getFrequencyItems(contentHolder);
    }

    /**
     * Slot positions matching CTC's layout:
     * [0] = button page (15 pairs × 2 coords = 30 values)
     * [1] = axis page (10 pairs × 2 coords = 20 values)
     */
    protected static final int[][] guiItemSlots = {
            {
                    36, 34,
                    84, 34,
                    60, 34,
                    12, 34,
                    167, 97,
                    191, 97,
                    131, 34,
                    155, 34,
                    179, 34,
                    119, 97,
                    143, 97,
                    12, 97,
                    84, 97,
                    36, 97,
                    60, 97
            },
            {
                    48, 34,
                    72, 34,
                    96, 34,
                    120, 34,
                    48, 97,
                    72, 97,
                    96, 97,
                    120, 97,
                    191, 34,
                    191, 97
            }
    };

    @Override
    protected void addSlots() {
        addPlayerSlots(32, 194);

        int slot = 0;
        for (int r = 0; r < 2; r++) {
            boolean isVisible = (isSecondPage && r == 1) || (!isSecondPage && r == 0);
            for (int index = 0; index < guiItemSlots[r].length; index += 2) {
                int x = guiItemSlots[r][index];
                int y = guiItemSlots[r][index + 1];
                for (int row = 0; row < 2; ++row) {
                    addSlot(new ControllerItemSlot(ghostInventory, slot++, x, y + row * 18, isVisible));
                }
            }
        }
    }

    @Override
    protected void saveData(ItemStack contentHolder) {
        // IMPORTANT: Preserve existing CustomData (ControlProfile, AxisConfig, Hub info, etc.)
        // Only update the "Items" key for ghost slot frequencies
        CompoundTag tag = contentHolder.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.put("Items", ghostInventory.serializeNBT(playerInventory.player.registryAccess()));
        contentHolder.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    protected boolean allowRepeats() {
        return true;
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickTypeIn, Player player) {
        super.clicked(slotId, dragType, clickTypeIn, player);
    }

    @Override
    public boolean stillValid(Player playerIn) {
        return playerInventory.getSelected() == contentHolder;
    }
}
