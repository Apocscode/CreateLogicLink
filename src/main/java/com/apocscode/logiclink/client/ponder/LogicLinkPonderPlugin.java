package com.apocscode.logiclink.client.ponder;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.ModRegistry;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;

/**
 * Ponder plugin for Logic Link mod. Registers all Ponder scenes and tags
 * so pressing (W) on our items opens animated tutorials with related block
 * icons on the left side panel.
 * <p>
 * Each tag uses a different block's item as its icon, so the left-side panel
 * of the Ponder UI shows a variety of related blocks (chests, shafts, basins,
 * stock links, redstone links, etc.) instead of just our own mod blocks.
 * </p>
 */
public class LogicLinkPonderPlugin implements PonderPlugin {

    // === Sensor left-panel tags (each shows a different related block icon) ===
    public static final ResourceLocation TAG_SENSOR_INVENTORIES =
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "sensor_inventories");
    public static final ResourceLocation TAG_SENSOR_KINETICS =
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "sensor_kinetics");
    public static final ResourceLocation TAG_SENSOR_FLUIDS =
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "sensor_fluids");
    public static final ResourceLocation TAG_SENSOR_HEAT =
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "sensor_heat");

    // === Logic Link left-panel tags ===
    public static final ResourceLocation TAG_LINK_LOGISTICS =
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "link_logistics");
    public static final ResourceLocation TAG_LINK_SENSORS =
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "link_sensors");
    public static final ResourceLocation TAG_LINK_COMPUTERS =
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "link_computers");

    // === Redstone Controller left-panel tags ===
    public static final ResourceLocation TAG_RC_REDSTONE_LINKS =
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "rc_redstone_links");
    public static final ResourceLocation TAG_RC_LAMPS =
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "rc_lamps");

    // === Motor left-panel tags ===
    public static final ResourceLocation TAG_MOTOR_KINETICS =
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "motor_kinetics");
    public static final ResourceLocation TAG_MOTOR_COMPUTERS =
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "motor_computers");
    public static final ResourceLocation TAG_MOTOR_SEQUENCES =
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "motor_sequences");

    // === Controller left-panel tags ===
    public static final ResourceLocation TAG_CTRL_GAMEPAD =
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "ctrl_gamepad");
    public static final ResourceLocation TAG_CTRL_REDSTONE =
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "ctrl_redstone");
    public static final ResourceLocation TAG_CTRL_MOTORS =
            ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "ctrl_motors");

    @Override
    public String getModId() {
        return LogicLink.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        LogicLinkPonderScenes.register(helper);
    }

    /** Helper to get a Create item by name from the registry. */
    private static Item createItem(String name) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", name));
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {

        // ================================================================
        // SENSOR TAGS — each shows a different block icon on the left panel
        // ================================================================

        // Inventories tag — chest icon
        helper.registerTag(TAG_SENSOR_INVENTORIES)
                .addToIndex()
                .item(Blocks.CHEST, true, false)
                .title("Inventory Blocks")
                .description("The Logic Sensor reads inventory contents from chests, barrels, vaults, depots, and more")
                .register();

        // Kinetics tag — shaft icon
        helper.registerTag(TAG_SENSOR_KINETICS)
                .addToIndex()
                .item(createItem("shaft"), true, false)
                .title("Kinetic Components")
                .description("The Logic Sensor reads RPM, stress, and overstressed status from kinetic blocks")
                .register();

        // Fluids tag — fluid tank icon
        helper.registerTag(TAG_SENSOR_FLUIDS)
                .addToIndex()
                .item(createItem("fluid_tank"), true, false)
                .title("Fluid Storage")
                .description("The Logic Sensor reads fluid type, amount, and capacity from tanks and basins")
                .register();

        // Heat tag — blaze burner icon
        helper.registerTag(TAG_SENSOR_HEAT)
                .addToIndex()
                .item(createItem("blaze_burner"), true, false)
                .title("Heat Sources")
                .description("The Logic Sensor reads heat level from Blaze Burners")
                .register();

        // ================================================================
        // LOGIC LINK TAGS
        // ================================================================

        // Logistics tag — stock link icon
        helper.registerTag(TAG_LINK_LOGISTICS)
                .addToIndex()
                .item(createItem("stock_link"), true, false)
                .title("Create Logistics")
                .description("The Logic Link bridges Create's logistics network with CC:Tweaked computers")
                .register();

        // Sensors tag — logic sensor icon
        helper.registerTag(TAG_LINK_SENSORS)
                .addToIndex()
                .item(ModRegistry.LOGIC_SENSOR_ITEM.get(), true, false)
                .title("Logic Sensors")
                .description("Logic Sensors provide wireless machine data to any Logic Link on the same network")
                .register();

        // Computers tag — CC computer block
        helper.registerTag(TAG_LINK_COMPUTERS)
                .addToIndex()
                .item(BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("computercraft", "computer_normal")), true, false)
                .title("CC:Tweaked Computers")
                .description("Place a CC:Tweaked computer adjacent to access the peripheral via Lua")
                .register();

        // ================================================================
        // REDSTONE CONTROLLER TAGS
        // ================================================================

        // Redstone Links tag — Create redstone link icon
        helper.registerTag(TAG_RC_REDSTONE_LINKS)
                .addToIndex()
                .item(createItem("redstone_link"), true, false)
                .title("Redstone Links")
                .description("The Redstone Controller transmits and receives on Create's Redstone Link frequencies")
                .register();

        // Lamps tag — redstone lamp icon
        helper.registerTag(TAG_RC_LAMPS)
                .addToIndex()
                .item(Blocks.REDSTONE_LAMP, true, false)
                .title("Redstone Outputs")
                .description("Control redstone-powered blocks like lamps and pistons wirelessly")
                .register();

        // ================================================================
        // MOTOR TAGS
        // ================================================================

        // Kinetics tag — shaft icon (different from sensor kinetics)
        helper.registerTag(TAG_MOTOR_KINETICS)
                .addToIndex()
                .item(createItem("shaft"), true, false)
                .title("Rotation Output")
                .description("Logic Motors and Drives generate or modify rotational force controlled by CC:Tweaked computers")
                .register();

        // Computers tag — CC computer icon
        helper.registerTag(TAG_MOTOR_COMPUTERS)
                .addToIndex()
                .item(BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("computercraft", "computer_normal")), true, false)
                .title("Computer Control")
                .description("Set speed, direction, and sequences from Lua programs")
                .register();

        // Sequences tag — clock icon for sequenced operations
        helper.registerTag(TAG_MOTOR_SEQUENCES)
                .addToIndex()
                .item(createItem("sequenced_gearshift"), true, false)
                .title("Sequenced Operations")
                .description("Program timed rotation sequences with rotate, wait, and speed steps")
                .register();

        // ================================================================
        // POPULATE TAGS WITH RELATED ITEMS
        // ================================================================

        // Inventory tag items
        helper.addToTag(TAG_SENSOR_INVENTORIES)
                .add(rl("minecraft", "chest"))
                .add(rl("minecraft", "barrel"))
                .add(rl("minecraft", "trapped_chest"))
                .add(rl("minecraft", "hopper"))
                .add(rl("minecraft", "furnace"))
                .add(rl("minecraft", "blast_furnace"))
                .add(rl("minecraft", "smoker"))
                .add(rl("minecraft", "brewing_stand"))
                .add(rl("create", "item_vault"))
                .add(rl("create", "depot"))
                .add(rl(LogicLink.MOD_ID, "logic_sensor"));

        // Kinetics tag items
        helper.addToTag(TAG_SENSOR_KINETICS)
                .add(rl("create", "shaft"))
                .add(rl("create", "mechanical_press"))
                .add(rl("create", "mechanical_mixer"))
                .add(rl("create", "millstone"))
                .add(rl("create", "mechanical_saw"))
                .add(rl("create", "encased_fan"))
                .add(rl(LogicLink.MOD_ID, "logic_sensor"));

        // Fluids tag items
        helper.addToTag(TAG_SENSOR_FLUIDS)
                .add(rl("create", "fluid_tank"))
                .add(rl("create", "basin"))
                .add(rl(LogicLink.MOD_ID, "logic_sensor"));

        // Heat tag items
        helper.addToTag(TAG_SENSOR_HEAT)
                .add(rl("create", "blaze_burner"))
                .add(rl(LogicLink.MOD_ID, "logic_sensor"));

        // Logistics tag items
        helper.addToTag(TAG_LINK_LOGISTICS)
                .add(rl("create", "stock_link"))
                .add(rl("create", "stock_ticker"))
                .add(rl("create", "packager"))
                .add(rl("create", "redstone_requester"))
                .add(rl(LogicLink.MOD_ID, "logic_link"))
                .add(rl(LogicLink.MOD_ID, "logic_sensor"));

        // Sensors tag items
        helper.addToTag(TAG_LINK_SENSORS)
                .add(rl(LogicLink.MOD_ID, "logic_sensor"))
                .add(rl(LogicLink.MOD_ID, "logic_link"));

        // Computers tag items
        helper.addToTag(TAG_LINK_COMPUTERS)
                .add(rl("computercraft", "computer_normal"))
                .add(rl("computercraft", "computer_advanced"))
                .add(rl("computercraft", "turtle_normal"))
                .add(rl("computercraft", "turtle_advanced"))
                .add(rl(LogicLink.MOD_ID, "logic_link"));

        // Redstone Links tag items
        helper.addToTag(TAG_RC_REDSTONE_LINKS)
                .add(rl("create", "redstone_link"))
                .add(rl(LogicLink.MOD_ID, "redstone_controller"));

        // Lamps tag items
        helper.addToTag(TAG_RC_LAMPS)
                .add(rl("minecraft", "redstone_lamp"))
                .add(rl("minecraft", "redstone_block"))
                .add(rl("minecraft", "lever"))
                .add(rl("minecraft", "piston"))
                .add(rl(LogicLink.MOD_ID, "redstone_controller"));

        // Motor kinetics tag items
        helper.addToTag(TAG_MOTOR_KINETICS)
                .add(rl("create", "shaft"))
                .add(rl("create", "cogwheel"))
                .add(rl("create", "large_cogwheel"))
                .add(rl("create", "gearbox"))
                .add(rl(LogicLink.MOD_ID, "creative_logic_motor"))
                .add(rl(LogicLink.MOD_ID, "logic_drive"));

        // Motor computers tag items
        helper.addToTag(TAG_MOTOR_COMPUTERS)
                .add(rl("computercraft", "computer_normal"))
                .add(rl("computercraft", "computer_advanced"))
                .add(rl(LogicLink.MOD_ID, "creative_logic_motor"))
                .add(rl(LogicLink.MOD_ID, "logic_drive"));

        // Motor sequences tag items
        helper.addToTag(TAG_MOTOR_SEQUENCES)
                .add(rl("create", "sequenced_gearshift"))
                .add(rl(LogicLink.MOD_ID, "creative_logic_motor"))
                .add(rl(LogicLink.MOD_ID, "logic_drive"));

        // ================================================================
        // CONTROLLER TAGS
        // ================================================================

        // Gamepad tag — controller/gamepad icon (use Create's controls item)
        helper.registerTag(TAG_CTRL_GAMEPAD)
                .addToIndex()
                .item(createItem("controls"), true, false)
                .title("Gamepad Input")
                .description("Map keyboard and Xbox-style gamepad input to Create machines in real time")
                .register();

        // Redstone tag — redstone link icon
        helper.registerTag(TAG_CTRL_REDSTONE)
                .addToIndex()
                .item(createItem("redstone_link"), true, false)
                .title("Redstone Channels")
                .description("8 auxiliary redstone channels with configurable power (1-15) and toggle/momentary modes")
                .register();

        // Motors tag — logic drive icon
        helper.registerTag(TAG_CTRL_MOTORS)
                .addToIndex()
                .item(ModRegistry.LOGIC_DRIVE_ITEM.get(), true, false)
                .title("Motor Control")
                .description("12 motor binding slots mapping gamepad directions to drives and motors")
                .register();

        // Gamepad tag items
        helper.addToTag(TAG_CTRL_GAMEPAD)
                .add(rl(LogicLink.MOD_ID, "logic_remote"))
                .add(rl(LogicLink.MOD_ID, "contraption_remote"));

        // Redstone tag items
        helper.addToTag(TAG_CTRL_REDSTONE)
                .add(rl("create", "redstone_link"))
                .add(rl(LogicLink.MOD_ID, "logic_remote"))
                .add(rl(LogicLink.MOD_ID, "contraption_remote"));

        // Motor control tag items
        helper.addToTag(TAG_CTRL_MOTORS)
                .add(rl(LogicLink.MOD_ID, "creative_logic_motor"))
                .add(rl(LogicLink.MOD_ID, "logic_drive"))
                .add(rl(LogicLink.MOD_ID, "logic_remote"))
                .add(rl(LogicLink.MOD_ID, "contraption_remote"));

        // ================================================================
        // ASSOCIATE TAGS WITH OUR BLOCKS (controls LEFT-side panel icons)
        // ================================================================

        // Logic Sensor: show inventory, kinetics, fluids, heat icons on left
        helper.addToComponent(rl(LogicLink.MOD_ID, "logic_sensor"))
                .add(TAG_SENSOR_INVENTORIES)
                .add(TAG_SENSOR_KINETICS)
                .add(TAG_SENSOR_FLUIDS)
                .add(TAG_SENSOR_HEAT);

        // Logic Link: show logistics, sensors, computers icons on left
        helper.addToComponent(rl(LogicLink.MOD_ID, "logic_link"))
                .add(TAG_LINK_LOGISTICS)
                .add(TAG_LINK_SENSORS)
                .add(TAG_LINK_COMPUTERS);

        // Redstone Controller: show redstone link, lamps icons on left
        helper.addToComponent(rl(LogicLink.MOD_ID, "redstone_controller"))
                .add(TAG_RC_REDSTONE_LINKS)
                .add(TAG_RC_LAMPS);

        // Creative Logic Motor: show kinetics, computers, sequences on left
        helper.addToComponent(rl(LogicLink.MOD_ID, "creative_logic_motor"))
                .add(TAG_MOTOR_KINETICS)
                .add(TAG_MOTOR_COMPUTERS)
                .add(TAG_MOTOR_SEQUENCES);

        // Logic Drive: show kinetics, computers, sequences on left
        helper.addToComponent(rl(LogicLink.MOD_ID, "logic_drive"))
                .add(TAG_MOTOR_KINETICS)
                .add(TAG_MOTOR_COMPUTERS)
                .add(TAG_MOTOR_SEQUENCES);

        // Contraption Remote: show gamepad, redstone, motors on left
        helper.addToComponent(rl(LogicLink.MOD_ID, "contraption_remote"))
                .add(TAG_CTRL_GAMEPAD)
                .add(TAG_CTRL_REDSTONE)
                .add(TAG_CTRL_MOTORS);
    }

    private static ResourceLocation rl(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
