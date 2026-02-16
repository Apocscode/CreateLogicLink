package com.apocscode.logiclink.block;

import com.apocscode.logiclink.client.LogicRemoteScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Client-only helper to open the Logic Remote screen for a Contraption Remote block.
 * Isolated to prevent server-side class loading of client GUI classes.
 */
public class ContraptionRemoteScreenOpener {

    public static void open(BlockPos blockPos) {
        Minecraft.getInstance().setScreen(new LogicRemoteScreen());
    }
}
