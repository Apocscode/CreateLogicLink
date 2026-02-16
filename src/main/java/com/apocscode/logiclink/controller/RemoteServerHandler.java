package com.apocscode.logiclink.controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.IntAttached;
import net.createmod.catnip.data.WorldAttached;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;

/**
 * Server-side handler for the Logic Remote controller.
 * Manages IRedstoneLinkable entries in Create's redstone link network,
 * mirroring CTC's TweakedLinkedControllerServerHandler.
 * <p>
 * Button inputs create on/off (15-strength) signals.
 * Axis inputs create variable-strength (0-15) signals.
 * All entries timeout after 30 ticks of no input.
 */
public class RemoteServerHandler {

    public static WorldAttached<Map<UUID, Collection<ManualFrequency>>> receivedInputs =
            new WorldAttached<>($ -> new HashMap<>());
    public static WorldAttached<Map<UUID, ArrayList<ManualAxisFrequency>>> receivedAxes =
            new WorldAttached<>($ -> new HashMap<>());

    static final int TIMEOUT = 30;

    /**
     * Called every server tick to decrement timeouts and remove stale entries.
     */
    public static void tick(LevelAccessor world) {
        // Tick button entries
        Map<UUID, Collection<ManualFrequency>> map = receivedInputs.get(world);
        for (Iterator<Entry<UUID, Collection<ManualFrequency>>> iterator = map.entrySet().iterator();
             iterator.hasNext(); ) {
            Entry<UUID, Collection<ManualFrequency>> entry = iterator.next();
            Collection<ManualFrequency> list = entry.getValue();

            for (Iterator<ManualFrequency> entryIterator = list.iterator(); entryIterator.hasNext(); ) {
                ManualFrequency mf = entryIterator.next();
                mf.decrement();
                if (!mf.isAlive()) {
                    Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(world, mf);
                    entryIterator.remove();
                }
            }

            if (list.isEmpty()) iterator.remove();
        }

        // Tick axis entries
        Map<UUID, ArrayList<ManualAxisFrequency>> map2 = receivedAxes.get(world);
        for (Iterator<Entry<UUID, ArrayList<ManualAxisFrequency>>> iterator = map2.entrySet().iterator();
             iterator.hasNext(); ) {
            Entry<UUID, ArrayList<ManualAxisFrequency>> entry = iterator.next();
            ArrayList<ManualAxisFrequency> list = entry.getValue();

            for (Iterator<ManualAxisFrequency> entryIterator = list.iterator(); entryIterator.hasNext(); ) {
                ManualAxisFrequency maf = entryIterator.next();
                maf.decrement();
                if (!maf.isAlive()) {
                    Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(world, maf);
                    entryIterator.remove();
                } else {
                    Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(world, maf);
                }
            }

            if (list.isEmpty()) iterator.remove();
        }
    }

    /**
     * Process button press/release states from a controller.
     */
    public static void receivePressed(LevelAccessor world, BlockPos pos, UUID uniqueID,
                                       ArrayList<Couple<Frequency>> collect, ArrayList<Boolean> values) {
        Map<UUID, Collection<ManualFrequency>> map = receivedInputs.get(world);
        Collection<ManualFrequency> list = map.computeIfAbsent(uniqueID, $ -> new ArrayList<>());

        WithNext:
        for (int i = 0; i < collect.size(); i++) {
            for (Iterator<ManualFrequency> iterator = list.iterator(); iterator.hasNext(); ) {
                ManualFrequency entry = iterator.next();
                if (entry.getSecond().equals(collect.get(i))) {
                    if (!values.get(i))
                        entry.setFirst(0);
                    else
                        entry.updatePosition(pos);
                    continue WithNext;
                }
            }

            if (!values.get(i)) continue;

            ManualFrequency entry = new ManualFrequency(pos, collect.get(i));
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(world, entry);
            list.add(entry);

            // Award advancement for linked controller usage
            for (IRedstoneLinkable linkable : Create.REDSTONE_LINK_NETWORK_HANDLER.getNetworkOf(world, entry))
                if (linkable instanceof LinkBehaviour lb && lb.isListening())
                    com.simibubi.create.foundation.advancement.AllAdvancements.LINKED_CONTROLLER
                            .awardTo(world.getPlayerByUUID(uniqueID));
        }
    }

    /**
     * Process axis values from a controller.
     */
    public static void receiveAxis(LevelAccessor world, BlockPos pos, UUID uniqueID,
                                    ArrayList<Couple<Frequency>> collect, ArrayList<Byte> values) {
        Map<UUID, ArrayList<ManualAxisFrequency>> map = receivedAxes.get(world);
        ArrayList<ManualAxisFrequency> list = map.computeIfAbsent(uniqueID, $ -> new ArrayList<>(10));

        WithNext:
        for (int i = 0; i < collect.size(); i++) {
            for (Iterator<ManualAxisFrequency> iterator = list.iterator(); iterator.hasNext(); ) {
                ManualAxisFrequency entry = iterator.next();
                if (entry.getSecond().equals(collect.get(i))) {
                    entry.setLevel(values.get(i));
                    entry.updatePosition(pos);
                    continue WithNext;
                }
            }

            ManualAxisFrequency entry = new ManualAxisFrequency(pos, values.get(i), collect.get(i));
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(world, entry);
            list.add(entry);
        }
    }

    // ==================== Inner Classes ====================

    /**
     * Button frequency entry: transmits 0 or 15 signal strength.
     */
    static class ManualFrequency extends IntAttached<Couple<Frequency>> implements IRedstoneLinkable {
        private BlockPos pos;

        public ManualFrequency(BlockPos pos, Couple<Frequency> second) {
            super(TIMEOUT, second);
            this.pos = pos;
        }

        public void updatePosition(BlockPos pos) {
            this.pos = pos;
            setFirst(TIMEOUT);
        }

        @Override
        public int getTransmittedStrength() {
            return isAlive() ? 15 : 0;
        }

        @Override
        public boolean isAlive() {
            return getFirst() > 0;
        }

        @Override
        public BlockPos getLocation() {
            return pos;
        }

        @Override
        public void setReceivedStrength(int power) { }

        @Override
        public boolean isListening() {
            return false;
        }

        @Override
        public Couple<Frequency> getNetworkKey() {
            return getSecond();
        }
    }

    /**
     * Axis frequency entry: transmits variable signal strength (0-15).
     */
    static class ManualAxisFrequency extends IntAttached<Couple<Frequency>> implements IRedstoneLinkable {
        private BlockPos pos;
        private int level = 0;

        public ManualAxisFrequency(BlockPos pos, int level, Couple<Frequency> second) {
            super(TIMEOUT, second);
            this.pos = pos;
            this.level = level;
        }

        public void updatePosition(BlockPos pos) {
            this.pos = pos;
            setFirst(TIMEOUT);
        }

        public void setLevel(int level) {
            this.level = level;
        }

        @Override
        public int getTransmittedStrength() {
            return isAlive() ? level : 0;
        }

        @Override
        public boolean isAlive() {
            return getFirst() > 0;
        }

        @Override
        public BlockPos getLocation() {
            return pos;
        }

        @Override
        public void setReceivedStrength(int power) { }

        @Override
        public boolean isListening() {
            return false;
        }

        @Override
        public Couple<Frequency> getNetworkKey() {
            return getSecond();
        }
    }
}
