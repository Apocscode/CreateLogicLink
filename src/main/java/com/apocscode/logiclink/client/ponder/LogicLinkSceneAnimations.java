package com.apocscode.logiclink.client.ponder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Animated Ponder scenes for all Logic Link peripherals.
 * Each method is a {@code PonderStoryBoard} — it builds the animated tutorial
 * shown when pressing (W) while hovering over the item.
 */
public class LogicLinkSceneAnimations {

    /**
     * Logic Link overview: shows computer connection, network bridge functionality.
     * Schematic (7x4x7): Logic Link at (3,1,3), CC Computer at (4,1,3),
     * Monitor at (4,2,3), Stock Link at (1,1,3), Barrel at (1,1,1), Chest at (1,1,5).
     */
    public static void logicLinkOverview(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("logic_link_overview", "Logic Link — Logistics Network Bridge");
        scene.configureBasePlate(0, 0, 7);

        // Show base plate rising
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        // Show the network storage blocks first (barrel + chest)
        scene.world().showSection(util.select().position(1, 1, 1), Direction.DOWN);
        scene.world().showSection(util.select().position(1, 1, 5), Direction.DOWN);
        scene.idle(10);

        // Show the stock link (represents Create's logistics network)
        scene.world().showSection(util.select().position(1, 1, 3), Direction.DOWN);
        scene.idle(5);
        scene.overlay().showOutline(PonderPalette.BLUE, "network", util.select().position(1, 1, 3), 60);
        scene.overlay().showText(60)
                .text("Create's logistics network connects storage and packagers")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(1, 1, 3), Direction.UP));
        scene.idle(80);

        // Show Logic Link block dropping in
        scene.world().showSection(util.select().position(3, 1, 3), Direction.DOWN);
        scene.idle(10);

        // Show right-click action to copy frequency
        scene.overlay().showControls(
                util.vector().blockSurface(new BlockPos(3, 1, 3), Direction.UP),
                Pointing.DOWN, 50
        ).rightClick().withItem(new ItemStack(Items.ENDER_PEARL));
        scene.overlay().showOutline(PonderPalette.GREEN, "link", util.select().position(3, 1, 3), 80);
        scene.overlay().showText(80)
                .text("Right-click a Stock Link to copy its frequency, then place the Logic Link Hub")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(3, 1, 3), Direction.UP));
        scene.idle(100);

        // Show CC Computer appearing next to it
        scene.world().showSection(util.select().position(4, 1, 3), Direction.WEST);
        scene.world().showSection(util.select().position(4, 2, 3), Direction.WEST);
        scene.idle(15);

        scene.overlay().showOutline(PonderPalette.OUTPUT, "computer",
                util.select().position(4, 1, 3).add(util.select().position(4, 2, 3)), 80);
        scene.overlay().showText(80)
                .text("Place a CC:Tweaked computer adjacent — it detects the Logic Link Hub as a peripheral")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(4, 1, 3), Direction.EAST));
        scene.idle(100);

        // Show what you can do
        scene.overlay().showText(80)
                .text("14 Lua functions: list items, request deliveries, read sensors, monitor gauges")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(3, 1, 3), Direction.UP));
        scene.idle(100);

        // Highlight the whole network
        scene.overlay().showOutline(PonderPalette.BLUE, "all",
                util.select().position(1, 1, 1)
                        .add(util.select().position(1, 1, 5))
                        .add(util.select().position(1, 1, 3))
                        .add(util.select().position(3, 1, 3)), 80);
        scene.overlay().showText(80)
                .text("Access the entire network's inventory from a single computer")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().centerOf(2, 1, 3));
        scene.idle(100);
    }

    /**
     * Logic Sensor overview: shows multiple supported blocks with sensors attached.
     * Schematic (7x4x7): Chest(2,1,2)+sensor, Barrel(4,1,2)+sensor,
     * Shaft(2,1,4)+wall-sensor, Basin(4,1,4)+sensor.
     */
    public static void logicSensorOverview(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("logic_sensor_overview", "Logic Sensor — Machine Data Reader");
        scene.configureBasePlate(0, 0, 7);

        // Show base plate
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        // === Chest + sensor ===
        scene.world().showSection(util.select().position(2, 1, 2), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(2, 2, 2), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showOutline(PonderPalette.GREEN, "chest_sensor",
                util.select().position(2, 1, 2).add(util.select().position(2, 2, 2)), 60);
        scene.overlay().showText(60)
                .text("Attach to a chest to read its inventory")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(2, 2, 2), Direction.UP));
        scene.idle(80);

        // === Barrel + sensor ===
        scene.world().showSection(util.select().position(4, 1, 2), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(4, 2, 2), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showOutline(PonderPalette.GREEN, "barrel_sensor",
                util.select().position(4, 1, 2).add(util.select().position(4, 2, 2)), 60);
        scene.overlay().showText(60)
                .text("Works with barrels, vaults, and any container")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(4, 2, 2), Direction.UP));
        scene.idle(80);

        // === Shaft + wall-mounted sensor ===
        scene.world().showSection(util.select().position(2, 1, 4), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(3, 1, 4), Direction.EAST);
        scene.idle(10);

        scene.overlay().showOutline(PonderPalette.BLUE, "shaft_sensor",
                util.select().position(2, 1, 4).add(util.select().position(3, 1, 4)), 60);
        scene.overlay().showText(60)
                .text("Mount on Create machines to read kinetic speed and stress")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(2, 1, 4), Direction.UP));
        scene.idle(80);

        // === Basin + sensor ===
        scene.world().showSection(util.select().position(4, 1, 4), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(4, 2, 4), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showOutline(PonderPalette.MEDIUM, "basin_sensor",
                util.select().position(4, 1, 4).add(util.select().position(4, 2, 4)), 60);
        scene.overlay().showText(60)
                .text("Reads inventory and fluid data from basins and tanks")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(4, 2, 4), Direction.UP));
        scene.idle(80);

        // Highlight all sensors
        scene.overlay().showOutline(PonderPalette.GREEN, "all_sensors",
                util.select().position(2, 2, 2)
                        .add(util.select().position(4, 2, 2))
                        .add(util.select().position(3, 1, 4))
                        .add(util.select().position(4, 2, 4)), 80);
        scene.overlay().showText(80)
                .text("Link sensors to a network for wireless access through any Logic Link Hub")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().centerOf(3, 2, 3));
        scene.idle(100);

        // Function summary
        scene.overlay().showText(60)
                .text("7 Lua functions — getData(), getTargetData(), refresh(), and more")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(3, 1, 3));
        scene.idle(80);
    }

    /**
     * Redstone Controller overview: shows controlling multiple Redstone Links.
     * Schematic (7x3x7): Controller at (3,1,3), CC Computer at (4,1,3),
     * 4 Redstone Links at corners, 4 Redstone Lamps next to them.
     */
    public static void redstoneControllerOverview(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("redstone_controller_overview", "Redstone Controller — Wireless Redstone");
        scene.configureBasePlate(0, 0, 7);

        // Show base plate
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        // Show Redstone Controller at center
        scene.world().showSection(util.select().position(3, 1, 3), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showOutline(PonderPalette.RED, "controller", util.select().position(3, 1, 3), 60);
        scene.overlay().showText(80)
                .text("The Redstone Controller interfaces with Create's Redstone Link network")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(3, 1, 3), Direction.UP));
        scene.idle(100);

        // Show CC Computer adjacent
        scene.world().showSection(util.select().position(4, 1, 3), Direction.WEST);
        scene.idle(10);

        scene.overlay().showOutline(PonderPalette.OUTPUT, "computer", util.select().position(4, 1, 3), 50);
        scene.overlay().showText(60)
                .text("Control it from a CC:Tweaked computer via Lua scripts")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(4, 1, 3), Direction.EAST));
        scene.idle(80);

        // Show all 4 Redstone Links dropping in simultaneously
        scene.world().showSection(util.select().position(1, 1, 1), Direction.DOWN);
        scene.world().showSection(util.select().position(5, 1, 1), Direction.DOWN);
        scene.world().showSection(util.select().position(1, 1, 5), Direction.DOWN);
        scene.world().showSection(util.select().position(5, 1, 5), Direction.DOWN);
        scene.idle(15);

        // Highlight all links
        scene.overlay().showOutline(PonderPalette.BLUE, "link1", util.select().position(1, 1, 1), 80);
        scene.overlay().showOutline(PonderPalette.BLUE, "link2", util.select().position(5, 1, 1), 80);
        scene.overlay().showOutline(PonderPalette.BLUE, "link3", util.select().position(1, 1, 5), 80);
        scene.overlay().showOutline(PonderPalette.BLUE, "link4", util.select().position(5, 1, 5), 80);

        scene.overlay().showText(80)
                .text("Each frequency pair matches a physical Create Redstone Link")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(1, 1, 1), Direction.UP));
        scene.idle(100);

        // Show redstone lamps lighting up
        scene.world().showSection(util.select().position(1, 1, 0), Direction.SOUTH);
        scene.world().showSection(util.select().position(5, 1, 0), Direction.SOUTH);
        scene.world().showSection(util.select().position(1, 1, 6), Direction.NORTH);
        scene.world().showSection(util.select().position(5, 1, 6), Direction.NORTH);
        scene.idle(15);

        scene.overlay().showOutline(PonderPalette.RED, "lamps",
                util.select().position(1, 1, 0)
                        .add(util.select().position(5, 1, 0))
                        .add(util.select().position(1, 1, 6))
                        .add(util.select().position(5, 1, 6)), 80);
        scene.overlay().showText(80)
                .text("One block controls unlimited channels — replacing many physical Redstone Links")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(3, 1, 3), Direction.UP));
        scene.idle(100);

        // Show setOutput concept
        scene.overlay().showText(80)
                .text("setOutput() transmits, getInput() receives — all controlled from Lua")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(4, 1, 3), Direction.UP));
        scene.idle(100);

        // Function summary
        scene.overlay().showText(60)
                .text("8 Lua functions: setOutput, getInput, getChannels, setAllOutputs, and more")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(3, 1, 3), Direction.NORTH));
        scene.idle(80);
    }

    /**
     * Creative Logic Motor overview: shows a CC-controlled rotation source.
     * Schematic (7x4x7): Creative Logic Motor at (3,1,3), CC Computer at (4,1,3),
     * Shaft chain going left from motor, mechanical components at end.
     */
    public static void creativeLogicMotorOverview(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("creative_logic_motor_overview", "Creative Logic Motor — Programmable Rotation Source");
        scene.configureBasePlate(0, 0, 7);

        // Show base plate
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        // Show the Creative Logic Motor
        scene.world().showSection(util.select().position(3, 1, 3), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showOutline(PonderPalette.GREEN, "motor", util.select().position(3, 1, 3), 80);
        scene.overlay().showText(80)
                .text("The Creative Logic Motor is a CC-controlled rotation source with unlimited stress capacity")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(3, 1, 3), Direction.UP));
        scene.idle(100);

        // Show CC Computer next to it
        scene.world().showSection(util.select().position(4, 1, 3), Direction.WEST);
        scene.idle(10);

        scene.overlay().showOutline(PonderPalette.OUTPUT, "computer", util.select().position(4, 1, 3), 60);
        scene.overlay().showText(80)
                .text("Place a CC:Tweaked computer adjacent to control the motor via Lua")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(4, 1, 3), Direction.EAST));
        scene.idle(100);

        // Show shaft chain extending from the motor
        scene.world().showSection(util.select().position(2, 1, 3), Direction.EAST);
        scene.world().showSection(util.select().position(1, 1, 3), Direction.EAST);
        scene.idle(15);

        scene.overlay().showOutline(PonderPalette.BLUE, "shafts",
                util.select().position(2, 1, 3).add(util.select().position(1, 1, 3)), 60);
        scene.overlay().showText(80)
                .text("setSpeed(rpm) controls rotation from -256 to 256 RPM — no external power needed")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(2, 1, 3), Direction.UP));
        scene.idle(100);

        // Show a machine at the end of the shaft
        scene.world().showSection(util.select().position(0, 1, 3), Direction.EAST);
        scene.idle(10);

        scene.overlay().showOutline(PonderPalette.MEDIUM, "machine", util.select().position(0, 1, 3), 60);
        scene.overlay().showText(80)
                .text("Powers any Create machine — press, mixer, saw, fan, and more")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(0, 1, 3), Direction.UP));
        scene.idle(100);

        // Sequence concept
        scene.overlay().showText(80)
                .text("Program sequences: rotate a set number of degrees, wait, then change speed")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(3, 1, 3), Direction.UP));
        scene.idle(100);

        // Function summary
        scene.overlay().showText(60)
                .text("16 Lua functions: setSpeed, stop, rotate, addSequenceStep, runSequence, and more")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(3, 1, 3), Direction.NORTH));
        scene.idle(80);
    }

    /**
     * Logic Drive overview: shows a CC-controlled rotation modifier.
     * Schematic (7x4x7): External shaft input from left, Logic Drive at (3,1,3),
     * CC Computer at (3,1,4), output shaft to the right.
     */
    public static void logicDriveOverview(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("logic_drive_overview", "Logic Drive — Programmable Rotation Modifier");
        scene.configureBasePlate(0, 0, 7);

        // Show base plate
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        // Show the Logic Motor block first
        scene.world().showSection(util.select().position(3, 1, 3), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showOutline(PonderPalette.GREEN, "motor", util.select().position(3, 1, 3), 80);
        scene.overlay().showText(80)
                .text("The Logic Drive modifies rotation passing through it — controlled by a CC:Tweaked computer")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(3, 1, 3), Direction.UP));
        scene.idle(100);

        // Show drive input side (orange stripe)
        scene.world().showSection(util.select().position(2, 1, 3), Direction.EAST);
        scene.world().showSection(util.select().position(1, 1, 3), Direction.EAST);
        scene.world().showSection(util.select().position(0, 1, 3), Direction.EAST);
        scene.idle(15);

        scene.overlay().showOutline(PonderPalette.INPUT, "input_shaft",
                util.select().position(2, 1, 3).add(util.select().position(1, 1, 3)).add(util.select().position(0, 1, 3)), 80);
        scene.overlay().showText(80)
                .text("The ORANGE-marked side is the DRIVE INPUT — connect your power source (shaft, water wheel, etc.) here")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(1, 1, 3), Direction.UP));
        scene.idle(100);

        // Show CC output side (light blue stripe)
        scene.world().showSection(util.select().position(4, 1, 3), Direction.WEST);
        scene.world().showSection(util.select().position(5, 1, 3), Direction.WEST);
        scene.world().showSection(util.select().position(6, 1, 3), Direction.WEST);
        scene.idle(10);

        scene.overlay().showOutline(PonderPalette.OUTPUT, "output_shaft",
                util.select().position(4, 1, 3).add(util.select().position(5, 1, 3)).add(util.select().position(6, 1, 3)), 80);
        scene.overlay().showText(80)
                .text("The LIGHT BLUE side is the CC OUTPUT — modified rotation exits here to your machines")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(5, 1, 3), Direction.UP));
        scene.idle(100);

        // Show CC Computer below
        scene.world().showSection(util.select().position(3, 1, 4), Direction.NORTH);
        scene.idle(10);

        scene.overlay().showOutline(PonderPalette.OUTPUT, "computer", util.select().position(3, 1, 4), 60);
        scene.overlay().showText(80)
                .text("Attach a CC:Tweaked computer to control the drive via Lua peripheral functions")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(3, 1, 4), Direction.SOUTH));
        scene.idle(100);

        // Modifier concept
        scene.overlay().showText(80)
                .text("setModifier(2.0) doubles output speed, setModifier(-1.0) reverses it — like a programmable gearshift")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(3, 1, 3), Direction.UP));
        scene.idle(100);

        // Function summary
        scene.overlay().showText(60)
                .text("17 Lua functions: enable, disable, setReversed, setModifier, addRotateStep, runSequence, and more")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(3, 1, 3), Direction.NORTH));
        scene.idle(80);
    }

    /**
     * Contraption Remote overview: shows seat-based controller activation,
     * redstone link control, and motor binding.
     * Schematic (7x4x7): Contraption Remote at (3,1,3) facing south,
     * Create seat at (3,1,4), Redstone Link at (1,1,3), Lamp at (1,1,1),
     * Logic Drive at (5,1,3), Shaft at (6,1,3).
     */
    public static void contraptionRemoteOverview(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("contraption_remote_overview", "Contraption Remote — Gamepad Controller Block");
        scene.configureBasePlate(0, 0, 7);

        // Show base plate rising
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        // Show the Contraption Remote block
        scene.world().showSection(util.select().position(3, 1, 3), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.GREEN, "remote", util.select().position(3, 1, 3), 80);
        scene.overlay().showText(80)
                .text("The Contraption Remote maps keyboard and gamepad input to redstone links and motors")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(3, 1, 3), Direction.UP));
        scene.idle(100);

        // Show the seat
        scene.world().showSection(util.select().position(3, 1, 4), Direction.NORTH);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.BLUE, "seat", util.select().position(3, 1, 4), 60);
        scene.overlay().showText(60)
                .text("Place a Create seat nearby — sit down first, then right-click the controller to activate")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(3, 1, 4), Direction.UP));
        scene.idle(80);

        // Show shift-right-click to configure
        scene.overlay().showControls(
                util.vector().blockSurface(new BlockPos(3, 1, 3), Direction.UP),
                Pointing.DOWN, 50
        ).rightClick();
        scene.overlay().showText(60)
                .text("Shift + right-click while standing to open the config GUI — set motor bindings and aux redstone channels")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(3, 1, 3), Direction.NORTH));
        scene.idle(80);

        // Show the redstone link and lamp
        scene.world().showSection(util.select().position(1, 1, 3), Direction.DOWN);
        scene.world().showSection(util.select().position(1, 1, 1), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.RED, "redstone",
                util.select().position(1, 1, 3).add(util.select().position(1, 1, 1)), 80);
        scene.overlay().showText(80)
                .text("8 aux channels fire Create Redstone Link signals — power 1-15, toggle or momentary mode")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(1, 1, 3), Direction.UP));
        scene.idle(100);

        // Show the logic drive and shaft
        scene.world().showSection(util.select().position(5, 1, 3), Direction.DOWN);
        scene.world().showSection(util.select().position(6, 1, 3), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "motor",
                util.select().position(5, 1, 3).add(util.select().position(6, 1, 3)), 80);
        scene.overlay().showText(80)
                .text("12 motor slots map gamepad sticks and triggers to drives and motors with configurable speed")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(new BlockPos(5, 1, 3), Direction.UP));
        scene.idle(100);

        // Contraption usage
        scene.overlay().showOutline(PonderPalette.GREEN, "all",
                util.select().position(3, 1, 3)
                        .add(util.select().position(3, 1, 4))
                        .add(util.select().position(1, 1, 3))
                        .add(util.select().position(5, 1, 3)), 80);
        scene.overlay().showText(80)
                .text("Works on moving contraptions — assemble with Super Glue and control from the seat while moving")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().centerOf(3, 1, 3));
        scene.idle(100);
    }
}
