package com.apocscode.logiclink.peripheral;

import com.apocscode.logiclink.LogicLink;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class that reads data from Create mod block entities and NeoForge
 * capability-based blocks. Returns data as Lua-compatible Maps.
 * <p>
 * Supports:
 * <ul>
 *   <li>Kinetic blocks (speed, stress, capacity)</li>
 *   <li>Item inventories via IItemHandler capability</li>
 *   <li>Fluid tanks via IFluidHandler capability</li>
 *   <li>Create-specific block entities (Basin, Deployer, Blaze Burner, etc.)</li>
 * </ul>
 */
public class CreateBlockReader {

    // Cached reflection references for KineticBlockEntity
    private static boolean kineticReflectionInit = false;
    private static Class<?> kineticBEClass = null;
    private static Method getSpeedMethod = null;
    private static Method getTheoreticalSpeedMethod = null;
    private static Method isOverStressedMethod = null;
    private static Field capacityField = null;
    private static Field stressField = null;
    private static Method hasNetworkMethod = null;

    // Cached reflection for Blaze Burner
    private static boolean blazeReflectionInit = false;
    private static Class<?> blazeBurnerBEClass = null;

    /**
     * Read all available data from a block at the given position.
     *
     * @return A map of data, or null if no block entity exists there.
     */
    @Nullable
    public static Map<String, Object> readBlockData(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;

        Map<String, Object> data = new HashMap<>();

        // Block identification
        String blockId = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString();
        data.put("block", blockId);
        data.put("blockName", be.getBlockState().getBlock().getName().getString());

        // Position
        Map<String, Integer> position = new HashMap<>();
        position.put("x", pos.getX());
        position.put("y", pos.getY());
        position.put("z", pos.getZ());
        data.put("position", position);

        // Kinetic data (Create mod)
        readKineticData(be, data);

        // Blaze Burner data
        readBlazeData(be, data);

        // Item inventory via NeoForge capability
        readItemData(level, pos, data);

        // Fluid storage via NeoForge capability
        readFluidData(level, pos, data);

        return data;
    }

    /**
     * Read kinetic data from any KineticBlockEntity.
     * Uses reflection to support the Create mod without hard compile dependency on
     * internal protected fields.
     */
    private static void readKineticData(BlockEntity be, Map<String, Object> data) {
        initKineticReflection();
        if (kineticBEClass == null || !kineticBEClass.isInstance(be)) return;

        try {
            data.put("isKinetic", true);

            if (getSpeedMethod != null) {
                data.put("speed", ((Number) getSpeedMethod.invoke(be)).floatValue());
            }
            if (getTheoreticalSpeedMethod != null) {
                data.put("theoreticalSpeed", ((Number) getTheoreticalSpeedMethod.invoke(be)).floatValue());
            }
            if (isOverStressedMethod != null) {
                data.put("overStressed", (Boolean) isOverStressedMethod.invoke(be));
            }
            if (hasNetworkMethod != null) {
                data.put("hasKineticNetwork", (Boolean) hasNetworkMethod.invoke(be));
            }

            // Protected fields via reflection
            if (capacityField != null) {
                data.put("stressCapacity", capacityField.getFloat(be));
            }
            if (stressField != null) {
                data.put("networkStress", stressField.getFloat(be));
            }
        } catch (Exception e) {
            LogicLink.LOGGER.debug("Failed to read kinetic data: {}", e.getMessage());
        }
    }

    /**
     * Read blaze burner heat level.
     */
    private static void readBlazeData(BlockEntity be, Map<String, Object> data) {
        initBlazeReflection();
        if (blazeBurnerBEClass == null || !blazeBurnerBEClass.isInstance(be)) return;

        try {
            // BlazeBurnerBlockEntity stores heat level in the block state
            // The heat property is on the block: BlazeBurnerBlock.HEAT_LEVEL
            String blockId = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString();
            if (blockId.contains("blaze_burner")) {
                data.put("isBlazeBurner", true);
                // Try to read the heat level from block state properties
                try {
                    Class<?> blazeBlockClass = Class.forName("com.simibubi.create.content.processing.burner.BlazeBurnerBlock");
                    Field heatLevelField = blazeBlockClass.getDeclaredField("HEAT_LEVEL");
                    heatLevelField.setAccessible(true);
                    Object heatLevelProp = heatLevelField.get(null);
                    if (heatLevelProp instanceof net.minecraft.world.level.block.state.properties.EnumProperty<?> enumProp) {
                        Object value = be.getBlockState().getValue(enumProp);
                        data.put("heatLevel", value.toString().toLowerCase());
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            LogicLink.LOGGER.debug("Failed to read blaze data: {}", e.getMessage());
        }
    }

    /**
     * Read item inventory from NeoForge IItemHandler capability.
     * Works with any block that exposes item storage (basins, vaults, depots, chests, etc.)
     */
    private static void readItemData(Level level, BlockPos pos, Map<String, Object> data) {
        // Try all directions + null for the capability
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler == null) {
            // Try specific directions
            for (Direction dir : Direction.values()) {
                handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, dir);
                if (handler != null) break;
            }
        }
        if (handler == null) return;

        data.put("hasInventory", true);
        data.put("inventorySize", handler.getSlots());

        List<Map<String, Object>> items = new ArrayList<>();
        int totalItems = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", stack.getItem().builtInRegistryHolder().key().location().toString());
                item.put("displayName", stack.getHoverName().getString());
                item.put("count", stack.getCount());
                item.put("maxCount", stack.getMaxStackSize());
                item.put("slot", i);
                items.add(item);
                totalItems += stack.getCount();
            }
        }
        data.put("inventory", items);
        data.put("totalItems", totalItems);
    }

    /**
     * Read fluid storage from NeoForge IFluidHandler capability.
     * Works with any block that has fluid tanks (Create tanks, basins, etc.)
     */
    private static void readFluidData(Level level, BlockPos pos, Map<String, Object> data) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (handler == null) {
            for (Direction dir : Direction.values()) {
                handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, dir);
                if (handler != null) break;
            }
        }
        if (handler == null) return;

        data.put("hasFluidStorage", true);
        data.put("tankCount", handler.getTanks());

        List<Map<String, Object>> tanks = new ArrayList<>();
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack fluid = handler.getFluidInTank(i);
            int capacity = handler.getTankCapacity(i);

            Map<String, Object> tank = new HashMap<>();
            tank.put("tank", i);
            tank.put("capacity", capacity);

            if (!fluid.isEmpty()) {
                tank.put("fluid", BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString());
                tank.put("amount", fluid.getAmount());
                tank.put("percentage", capacity > 0 ? (double) fluid.getAmount() / capacity * 100.0 : 0.0);
            } else {
                tank.put("fluid", "empty");
                tank.put("amount", 0);
                tank.put("percentage", 0.0);
            }
            tanks.add(tank);
        }
        data.put("tanks", tanks);
    }

    // ==================== Reflection Init ====================

    private static void initKineticReflection() {
        if (kineticReflectionInit) return;
        kineticReflectionInit = true;

        try {
            kineticBEClass = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
            getSpeedMethod = kineticBEClass.getMethod("getSpeed");
            getTheoreticalSpeedMethod = kineticBEClass.getMethod("getTheoreticalSpeed");
            isOverStressedMethod = kineticBEClass.getMethod("isOverStressed");
            hasNetworkMethod = kineticBEClass.getMethod("hasNetwork");

            // Protected fields — need setAccessible
            try {
                capacityField = kineticBEClass.getDeclaredField("capacity");
                capacityField.setAccessible(true);
            } catch (NoSuchFieldException ignored) {}

            try {
                stressField = kineticBEClass.getDeclaredField("stress");
                stressField.setAccessible(true);
            } catch (NoSuchFieldException ignored) {}

            LogicLink.LOGGER.info("CreateBlockReader: KineticBlockEntity reflection initialized");
        } catch (ClassNotFoundException e) {
            LogicLink.LOGGER.debug("CreateBlockReader: KineticBlockEntity not found (Create not loaded?)");
        } catch (Exception e) {
            LogicLink.LOGGER.warn("CreateBlockReader: Failed to init kinetic reflection: {}", e.getMessage());
        }
    }

    private static void initBlazeReflection() {
        if (blazeReflectionInit) return;
        blazeReflectionInit = true;

        try {
            blazeBurnerBEClass = Class.forName(
                    "com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity");
            LogicLink.LOGGER.info("CreateBlockReader: BlazeBurnerBlockEntity reflection initialized");
        } catch (ClassNotFoundException e) {
            LogicLink.LOGGER.debug("CreateBlockReader: BlazeBurnerBlockEntity not found");
        }
    }
}
