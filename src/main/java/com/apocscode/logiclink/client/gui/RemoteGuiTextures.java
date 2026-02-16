package com.apocscode.logiclink.client.gui;

import com.apocscode.logiclink.LogicLink;
import net.createmod.catnip.gui.element.ScreenElement;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * GUI background textures for the Logic Remote configuration screen.
 * Matches CTC's ModGuiTextures layout (227×172 with two tabbed pages).
 */
public enum RemoteGuiTextures implements ScreenElement {
    LOGIC_REMOTE_0("logic_remote_0", 227, 172),
    LOGIC_REMOTE_1("logic_remote_1", 227, 172);

    public static final int FONT_COLOR = 0x575F7A;

    public final ResourceLocation location;
    public int width, height;
    public int startX, startY;

    RemoteGuiTextures(String path, int width, int height) {
        this(path, 0, 0, width, height);
    }

    RemoteGuiTextures(String path, int startX, int startY, int width, int height) {
        this.location = ResourceLocation.fromNamespaceAndPath(LogicLink.MOD_ID, "textures/gui/" + path + ".png");
        this.width = width;
        this.height = height;
        this.startX = startX;
        this.startY = startY;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void render(GuiGraphics graphics, int x, int y) {
        graphics.blit(location, x, y, 0, startX, startY, width, height, 256, 256);
    }
}
