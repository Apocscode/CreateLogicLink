package com.apocscode.logiclink.client.ponder;

import com.apocscode.logiclink.LogicLink;

import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Registers all Ponder scenes for Logic Link blocks.
 * Each block gets an "overview" scene with tag highlighting for related blocks.
 */
public class LogicLinkPonderScenes {

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {

        // Logic Link — logistics network bridge (highlights logistics tag)
        helper.addStoryBoard(
                ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "logic_link"),
                "logic_link/overview",
                LogicLinkSceneAnimations::logicLinkOverview,
                LogicLinkPonderPlugin.TAG_LINK_LOGISTICS
        );

        // Logic Sensor — machine data reader (highlights inventory tag)
        helper.addStoryBoard(
                ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "logic_sensor"),
                "logic_sensor/overview",
                LogicLinkSceneAnimations::logicSensorOverview,
                LogicLinkPonderPlugin.TAG_SENSOR_INVENTORIES
        );

        // Redstone Controller — wireless redstone control (highlights redstone links tag)
        helper.addStoryBoard(
                ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "redstone_controller"),
                "redstone_controller/overview",
                LogicLinkSceneAnimations::redstoneControllerOverview,
                LogicLinkPonderPlugin.TAG_RC_REDSTONE_LINKS
        );

        // Creative Logic Motor — CC-controlled rotation source (highlights kinetics tag)
        helper.addStoryBoard(
                ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "creative_logic_motor"),
                "creative_logic_motor/overview",
                LogicLinkSceneAnimations::creativeLogicMotorOverview,
                LogicLinkPonderPlugin.TAG_MOTOR_KINETICS
        );

        // Logic Motor — CC-controlled rotation modifier (highlights kinetics tag)
        helper.addStoryBoard(
                ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "logic_motor"),
                "logic_motor/overview",
                LogicLinkSceneAnimations::logicMotorOverview,
                LogicLinkPonderPlugin.TAG_MOTOR_KINETICS
        );
    }
}
