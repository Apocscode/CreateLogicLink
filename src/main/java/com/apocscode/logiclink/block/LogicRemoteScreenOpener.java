package com.apocscode.logiclink.block;

import com.apocscode.logiclink.client.LogicRemoteScreen;
import net.minecraft.client.Minecraft;

/**
 * Client-only helper to open the Logic Remote screen.
 * Isolated in its own class so that {@link LogicRemoteItem} never
 * directly references client-only classes, preventing class loading
 * crashes on dedicated servers.
 */
public class LogicRemoteScreenOpener {

    public static void open() {
        Minecraft.getInstance().setScreen(new LogicRemoteScreen());
    }
}
