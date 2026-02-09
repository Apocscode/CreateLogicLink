package com.apocscode.logiclink.network;

import com.apocscode.logiclink.block.LogicLinkBlockEntity;
import com.apocscode.logiclink.block.LogicSensorBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static registry that tracks all Logic Link block entities by their
 * logistics network frequency UUID. Mirrors SensorNetwork but for links.
 * <p>
 * Also provides a utility method to gather all connected block positions
 * on a network and send highlight packets to a player.
 * </p>
 */
public class LinkNetwork {

    private static final Map<UUID, Set<WeakReference<LogicLinkBlockEntity>>> LINKS =
            new ConcurrentHashMap<>();

    /**
     * Register a Logic Link with a network frequency.
     */
    public static void register(UUID freq, LogicLinkBlockEntity link) {
        if (freq == null || link == null) return;
        LINKS.computeIfAbsent(freq, k -> ConcurrentHashMap.newKeySet())
                .add(new WeakReference<>(link));
    }

    /**
     * Unregister a Logic Link from a network frequency.
     */
    public static void unregister(UUID freq, LogicLinkBlockEntity link) {
        if (freq == null) return;
        Set<WeakReference<LogicLinkBlockEntity>> set = LINKS.get(freq);
        if (set != null) {
            set.removeIf(ref -> {
                LogicLinkBlockEntity be = ref.get();
                return be == null || be == link;
            });
            if (set.isEmpty()) {
                LINKS.remove(freq);
            }
        }
    }

    /**
     * Get all live Logic Links on a given network frequency.
     */
    public static List<LogicLinkBlockEntity> getLinks(UUID freq) {
        if (freq == null) return Collections.emptyList();
        Set<WeakReference<LogicLinkBlockEntity>> set = LINKS.get(freq);
        if (set == null) return Collections.emptyList();

        List<LogicLinkBlockEntity> result = new ArrayList<>();
        set.removeIf(ref -> {
            LogicLinkBlockEntity be = ref.get();
            if (be == null || be.isRemoved()) return true;
            result.add(be);
            return false;
        });

        if (set.isEmpty()) {
            LINKS.remove(freq);
        }
        return result;
    }

    /**
     * Clear all link registrations. Called on server shutdown.
     */
    public static void clear() {
        LINKS.clear();
    }

    /**
     * Gathers all block positions on a network (our blocks + Create's linked blocks)
     * and sends a highlight packet to the player.
     */
    public static void sendNetworkHighlight(Level level, UUID freq, Player player) {
        if (freq == null || level.isClientSide()) return;

        Set<BlockPos> positionSet = new HashSet<>();

        // Gather all Logic Links on this network
        for (LogicLinkBlockEntity link : getLinks(freq)) {
            positionSet.add(link.getBlockPos());
        }

        // Gather all Logic Sensors on this network
        for (LogicSensorBlockEntity sensor : SensorNetwork.getSensors(freq)) {
            positionSet.add(sensor.getBlockPos());
        }

        // Gather all Create logistics blocks on this network
        try {
            Collection<LogisticallyLinkedBehaviour> createLinks =
                    LogisticallyLinkedBehaviour.getAllPresent(freq, false);
            for (LogisticallyLinkedBehaviour behaviour : createLinks) {
                positionSet.add(behaviour.getPos());
            }
        } catch (Exception e) {
            // Silently handle if Create API unavailable
        }

        if (!positionSet.isEmpty() && player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new NetworkHighlightPayload(new ArrayList<>(positionSet)));
        }
    }
}
