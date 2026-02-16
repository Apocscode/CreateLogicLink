package com.apocscode.logiclink.client.gui;

import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Ghost item slot with visibility toggle for two-page controller menu.
 * When inactive, the slot is hidden and non-interactable.
 */
public class ControllerItemSlot extends SlotItemHandler {
    protected boolean active = true;

    public ControllerItemSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
        super(itemHandler, index, xPosition, yPosition);
    }

    public ControllerItemSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition, boolean active) {
        super(itemHandler, index, xPosition, yPosition);
        this.active = active;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
