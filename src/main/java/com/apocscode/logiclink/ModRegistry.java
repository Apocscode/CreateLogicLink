package com.apocscode.logiclink;

import com.apocscode.logiclink.block.LogicLinkBlock;
import com.apocscode.logiclink.block.LogicLinkBlockEntity;
import com.apocscode.logiclink.block.LogicLinkBlockItem;
import com.apocscode.logiclink.block.LogicSensorBlock;
import com.apocscode.logiclink.block.LogicSensorBlockEntity;
import com.apocscode.logiclink.block.LogicSensorBlockItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Central registry for all blocks, items, block entities, and creative tabs
 * in the Logic Link mod.
 */
public class ModRegistry {

    // ==================== Deferred Registers ====================
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LogicLink.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LogicLink.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LogicLink.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LogicLink.MOD_ID);

    // ==================== Blocks ====================

    /**
     * The Logic Link block — an emerald green block that connects to
     * Create's logistics network and provides a CC:Tweaked peripheral.
     * Uses TERRACOTTA_GREEN to distinguish from Create's TERRACOTTA_BLUE Stock Link.
     */
    public static final DeferredBlock<LogicLinkBlock> LOGIC_LINK_BLOCK = BLOCKS.register(
            "logic_link",
            () -> new LogicLinkBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.EMERALD)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK))
    );

    /**
     * The Logic Sensor block — a thin wireless peripheral that attaches to
     * Create machines and reports their data to the Logic Link over the network.
     */
    public static final DeferredBlock<LogicSensorBlock> LOGIC_SENSOR_BLOCK = BLOCKS.register(
            "logic_sensor",
            () -> new LogicSensorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(2.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.COPPER_GRATE)
                    .noOcclusion())
    );

    // ==================== Items ====================

    /**
     * Custom BlockItem that supports right-click linking to Create logistics networks.
     */
    public static final DeferredItem<LogicLinkBlockItem> LOGIC_LINK_ITEM = ITEMS.register(
            "logic_link",
            () -> new LogicLinkBlockItem(LOGIC_LINK_BLOCK.get(), new Item.Properties())
    );

    /**
     * Custom BlockItem for the Logic Sensor with frequency linking support.
     */
    public static final DeferredItem<LogicSensorBlockItem> LOGIC_SENSOR_ITEM = ITEMS.register(
            "logic_sensor",
            () -> new LogicSensorBlockItem(LOGIC_SENSOR_BLOCK.get(), new Item.Properties())
    );

    // ==================== Block Entities ====================

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LogicLinkBlockEntity>> LOGIC_LINK_BE =
            BLOCK_ENTITY_TYPES.register(
                    "logic_link",
                    () -> BlockEntityType.Builder.of(
                            LogicLinkBlockEntity::new,
                            LOGIC_LINK_BLOCK.get()
                    ).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LogicSensorBlockEntity>> LOGIC_SENSOR_BE =
            BLOCK_ENTITY_TYPES.register(
                    "logic_sensor",
                    () -> BlockEntityType.Builder.of(
                            LogicSensorBlockEntity::new,
                            LOGIC_SENSOR_BLOCK.get()
                    ).build(null)
            );

    // ==================== Creative Mode Tabs ====================

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LOGIC_LINK_TAB =
            CREATIVE_MODE_TABS.register("logiclink_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.logiclink"))
                    .withTabsBefore(CreativeModeTabs.REDSTONE_BLOCKS)
                    .icon(() -> LOGIC_LINK_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(LOGIC_LINK_ITEM.get());
                        output.accept(LOGIC_SENSOR_ITEM.get());
                    })
                    .build()
            );
}
