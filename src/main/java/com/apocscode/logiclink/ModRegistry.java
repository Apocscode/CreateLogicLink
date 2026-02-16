package com.apocscode.logiclink;

import com.apocscode.logiclink.block.CreativeLogicMotorBlock;
import com.apocscode.logiclink.block.CreativeLogicMotorBlockEntity;
import com.apocscode.logiclink.block.ContraptionRemoteBlock;
import com.apocscode.logiclink.block.ContraptionRemoteBlockEntity;
import com.apocscode.logiclink.block.LogicLinkBlock;
import com.apocscode.logiclink.block.LogicLinkBlockEntity;
import com.apocscode.logiclink.block.LogicLinkBlockItem;
import com.apocscode.logiclink.block.LogicDriveBlock;
import com.apocscode.logiclink.block.LogicDriveBlockEntity;
import com.apocscode.logiclink.block.LogicRemoteItem;
import com.apocscode.logiclink.block.LogicSensorBlock;
import com.apocscode.logiclink.block.LogicSensorBlockEntity;
import com.apocscode.logiclink.block.LogicSensorBlockItem;
import com.apocscode.logiclink.block.RedstoneControllerBlock;
import com.apocscode.logiclink.block.RedstoneControllerBlockEntity;
import com.apocscode.logiclink.block.TrainControllerBlock;
import com.apocscode.logiclink.block.TrainControllerBlockEntity;
import com.apocscode.logiclink.block.SignalTabletItem;
import com.apocscode.logiclink.block.TrainMonitorBlock;
import com.apocscode.logiclink.block.TrainMonitorBlockEntity;
import com.apocscode.logiclink.block.TrainMonitorMenu;
import com.apocscode.logiclink.controller.LogicRemoteMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.BlockItem;
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
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, LogicLink.MOD_ID);
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

    /**
     * The Redstone Controller block — a CC:Tweaked peripheral that provides
     * programmatic control over Create's Redstone Link wireless network.
     */
    public static final DeferredBlock<RedstoneControllerBlock> REDSTONE_CONTROLLER_BLOCK = BLOCKS.register(
            "redstone_controller",
            () -> new RedstoneControllerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.FIRE)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK))
    );

    /**
     * Creative Logic Motor — CC-controlled rotation source with unlimited stress.
     * Uses Create's Creative Motor model with emerald green tint.
     */
    public static final DeferredBlock<CreativeLogicMotorBlock> CREATIVE_LOGIC_MOTOR_BLOCK = BLOCKS.register(
            "creative_logic_motor",
            () -> new CreativeLogicMotorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.EMERALD)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion())
    );

    /**
     * Logic Drive (standard) — CC-controlled rotation modifier.
     * Takes external rotation input and modifies the output.
     * Uses Create's Gearshift model with cyan tint.
     */
    public static final DeferredBlock<LogicDriveBlock> LOGIC_DRIVE_BLOCK = BLOCKS.register(
            "logic_drive",
            () -> new LogicDriveBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion())
    );

    /**
     * Train Controller — CC-controlled peripheral for global train network monitoring.
     * Reads directly from Create's GlobalRailwayManager. No sensors needed.
     */
    public static final DeferredBlock<TrainControllerBlock> TRAIN_CONTROLLER_BLOCK = BLOCKS.register(
            "train_controller",
            () -> new TrainControllerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WARPED_NYLIUM)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK))
    );

    /**
     * Train Network Monitor — multi-block display (up to 10x10) showing live train data.
     * Supports all 6 facing directions. In-world TESR + right-click GUI.
     */
    public static final DeferredBlock<TrainMonitorBlock> TRAIN_MONITOR_BLOCK = BLOCKS.register(
            "train_monitor",
            () -> new TrainMonitorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion())
    );

    /**
     * Contraption Remote Control — placeable on contraptions or stationary.
     * Bridges CTC controller input to Logic Drives and Motors.
     * CC:Tweaked peripheral type: "contraption_remote".
     */
    public static final DeferredBlock<ContraptionRemoteBlock> CONTRAPTION_REMOTE_BLOCK = BLOCKS.register(
            "contraption_remote",
            () -> new ContraptionRemoteBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)
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

    /**
     * Standard BlockItem for the Redstone Controller (no frequency linking needed).
     */
    public static final DeferredItem<BlockItem> REDSTONE_CONTROLLER_ITEM = ITEMS.register(
            "redstone_controller",
            () -> new BlockItem(REDSTONE_CONTROLLER_BLOCK.get(), new Item.Properties())
    );

    /**
     * BlockItem for the Creative Logic Motor.
     */
    public static final DeferredItem<BlockItem> CREATIVE_LOGIC_MOTOR_ITEM = ITEMS.register(
            "creative_logic_motor",
            () -> new BlockItem(CREATIVE_LOGIC_MOTOR_BLOCK.get(), new Item.Properties())
    );

    /**
     * BlockItem for the Logic Drive (standard).
     */
    public static final DeferredItem<BlockItem> LOGIC_DRIVE_ITEM = ITEMS.register(
            "logic_drive",
            () -> new BlockItem(LOGIC_DRIVE_BLOCK.get(), new Item.Properties())
    );

    /**
     * BlockItem for the Train Controller.
     */
    public static final DeferredItem<BlockItem> TRAIN_CONTROLLER_ITEM = ITEMS.register(
            "train_controller",
            () -> new BlockItem(TRAIN_CONTROLLER_BLOCK.get(), new Item.Properties())
    );

    /**
     * BlockItem for the Train Network Monitor.
     */
    public static final DeferredItem<BlockItem> TRAIN_MONITOR_ITEM = ITEMS.register(
            "train_monitor",
            () -> new BlockItem(TRAIN_MONITOR_BLOCK.get(), new Item.Properties())
    );

    /**
     * Signal Diagnostic Tablet — handheld item that scans the train network
     * for signal issues and displays diagnostics. Updates after repairs.
     */
    public static final DeferredItem<SignalTabletItem> SIGNAL_TABLET_ITEM = ITEMS.register(
            "signal_tablet",
            () -> new SignalTabletItem(new Item.Properties())
    );

    /**
     * Logic Remote — handheld controller that bridges CTC input to drives/motors.
     * Sneak+click lectern to link input, click drive/motor to add target.
     */
    public static final DeferredItem<LogicRemoteItem> LOGIC_REMOTE_ITEM = ITEMS.register(
            "logic_remote",
            () -> new LogicRemoteItem(new Item.Properties().stacksTo(1))
    );

    /**
     * BlockItem for the Contraption Remote Control block.
     */
    public static final DeferredItem<BlockItem> CONTRAPTION_REMOTE_ITEM = ITEMS.register(
            "contraption_remote",
            () -> new BlockItem(CONTRAPTION_REMOTE_BLOCK.get(), new Item.Properties())
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RedstoneControllerBlockEntity>> REDSTONE_CONTROLLER_BE =
            BLOCK_ENTITY_TYPES.register(
                    "redstone_controller",
                    () -> BlockEntityType.Builder.of(
                            RedstoneControllerBlockEntity::new,
                            REDSTONE_CONTROLLER_BLOCK.get()
                    ).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CreativeLogicMotorBlockEntity>> CREATIVE_LOGIC_MOTOR_BE =
            BLOCK_ENTITY_TYPES.register(
                    "creative_logic_motor",
                    () -> {
                        BlockEntityType<CreativeLogicMotorBlockEntity>[] holder = new BlockEntityType[1];
                        holder[0] = BlockEntityType.Builder.of(
                                (pos, state) -> new CreativeLogicMotorBlockEntity(holder[0], pos, state),
                                CREATIVE_LOGIC_MOTOR_BLOCK.get()
                        ).build(null);
                        return holder[0];
                    }
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LogicDriveBlockEntity>> LOGIC_DRIVE_BE =
            BLOCK_ENTITY_TYPES.register(
                    "logic_drive",
                    () -> {
                        BlockEntityType<LogicDriveBlockEntity>[] holder = new BlockEntityType[1];
                        holder[0] = BlockEntityType.Builder.of(
                                (pos, state) -> new LogicDriveBlockEntity(holder[0], pos, state),
                                LOGIC_DRIVE_BLOCK.get()
                        ).build(null);
                        return holder[0];
                    }
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrainControllerBlockEntity>> TRAIN_CONTROLLER_BE =
            BLOCK_ENTITY_TYPES.register(
                    "train_controller",
                    () -> BlockEntityType.Builder.of(
                            TrainControllerBlockEntity::new,
                            TRAIN_CONTROLLER_BLOCK.get()
                    ).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrainMonitorBlockEntity>> TRAIN_MONITOR_BE =
            BLOCK_ENTITY_TYPES.register(
                    "train_monitor",
                    () -> BlockEntityType.Builder.of(
                            TrainMonitorBlockEntity::new,
                            TRAIN_MONITOR_BLOCK.get()
                    ).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ContraptionRemoteBlockEntity>> CONTRAPTION_REMOTE_BE =
            BLOCK_ENTITY_TYPES.register(
                    "contraption_remote",
                    () -> BlockEntityType.Builder.of(
                            ContraptionRemoteBlockEntity::new,
                            CONTRAPTION_REMOTE_BLOCK.get()
                    ).build(null)
            );

    // ==================== Menu Types ====================

    public static final DeferredHolder<MenuType<?>, MenuType<TrainMonitorMenu>> TRAIN_MONITOR_MENU =
            MENU_TYPES.register(
                    "train_monitor",
                    () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                            TrainMonitorMenu::new
                    )
            );

    /**
     * Logic Remote frequency configuration menu.
     * Ghost item menu with 50 slots for button/axis frequency pairs.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<LogicRemoteMenu>> LOGIC_REMOTE_MENU =
            MENU_TYPES.register(
                    "logic_remote",
                    () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                            LogicRemoteMenu::new
                    )
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
                        output.accept(REDSTONE_CONTROLLER_ITEM.get());
                        output.accept(CREATIVE_LOGIC_MOTOR_ITEM.get());
                        output.accept(LOGIC_DRIVE_ITEM.get());
                        output.accept(TRAIN_CONTROLLER_ITEM.get());
                        output.accept(TRAIN_MONITOR_ITEM.get());
                        output.accept(SIGNAL_TABLET_ITEM.get());
                        output.accept(LOGIC_REMOTE_ITEM.get());
                        output.accept(CONTRAPTION_REMOTE_ITEM.get());
                        // Patchouli guide book is added automatically by
                        // Patchouli's processCreativeTabs event via the
                        // "creative_tab" field in book.json
                    })
                    .build()
            );
}
