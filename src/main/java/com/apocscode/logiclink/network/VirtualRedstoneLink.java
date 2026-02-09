package com.apocscode.logiclink.network;

import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;

import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A virtual {@link IRedstoneLinkable} node that exists purely in memory,
 * allowing the Redstone Controller peripheral to participate in Create's
 * Redstone Link wireless network without placing physical Redstone Link blocks.
 * <p>
 * Each instance represents one channel (frequency pair) on the network,
 * acting as either a transmitter (sends signal 0–15) or a receiver
 * (reads signal from external transmitters).
 * </p>
 */
public class VirtualRedstoneLink implements IRedstoneLinkable {

    private final BlockEntity controller;
    private final String item1Name;
    private final String item2Name;
    private final Couple<Frequency> networkKey;
    private boolean listening;
    private int transmitPower;
    private volatile int receivedPower;
    private boolean alive = true;

    /**
     * Creates a new virtual redstone link node.
     *
     * @param controller  The block entity this link belongs to (for position and alive checks).
     * @param item1Name   Registry name of the first frequency item (e.g. "minecraft:redstone").
     * @param item2Name   Registry name of the second frequency item (e.g. "minecraft:lever").
     * @param listening   true = receiver, false = transmitter.
     * @param transmitPower Initial transmit power (only relevant for transmitters).
     */
    public VirtualRedstoneLink(BlockEntity controller, String item1Name, String item2Name,
                                boolean listening, int transmitPower) {
        this.controller = controller;
        this.item1Name = item1Name;
        this.item2Name = item2Name;
        this.listening = listening;
        this.transmitPower = transmitPower;
        this.receivedPower = 0;

        // Build the frequency pair key for Create's network
        Frequency freq1 = Frequency.of(createStack(item1Name));
        Frequency freq2 = Frequency.of(createStack(item2Name));
        this.networkKey = Couple.create(freq1, freq2);
    }

    /**
     * Creates an ItemStack from a registry name for Frequency construction.
     */
    private static ItemStack createStack(String itemName) {
        ResourceLocation loc = ResourceLocation.tryParse(itemName);
        if (loc == null) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(loc));
    }

    // ==================== IRedstoneLinkable Implementation ====================

    @Override
    public int getTransmittedStrength() {
        return listening ? 0 : transmitPower;
    }

    @Override
    public void setReceivedStrength(int power) {
        this.receivedPower = power;
    }

    @Override
    public boolean isListening() {
        return listening;
    }

    @Override
    public boolean isAlive() {
        return alive && !controller.isRemoved();
    }

    @Override
    public Couple<Frequency> getNetworkKey() {
        return networkKey;
    }

    @Override
    public BlockPos getLocation() {
        return controller.getBlockPos();
    }

    // ==================== Accessors ====================

    public String getItem1Name() {
        return item1Name;
    }

    public String getItem2Name() {
        return item2Name;
    }

    public int getTransmitPower() {
        return transmitPower;
    }

    public int getReceivedPower() {
        return receivedPower;
    }

    public void setTransmitPower(int power) {
        this.transmitPower = power;
    }

    public void setListening(boolean listening) {
        this.listening = listening;
    }

    /**
     * Marks this virtual link as dead. Create's network handler will
     * skip it on the next update cycle.
     */
    public void kill() {
        this.alive = false;
    }
}
