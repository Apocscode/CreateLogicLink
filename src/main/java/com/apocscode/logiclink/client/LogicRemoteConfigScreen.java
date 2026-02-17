package com.apocscode.logiclink.client;

import static com.simibubi.create.foundation.gui.AllGuiTextures.PLAYER_INVENTORY;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.joml.Vector3f;

import com.google.common.collect.ImmutableList;
import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.client.gui.DigitIcon;
import com.apocscode.logiclink.client.gui.DigitIconRenderer;
import com.apocscode.logiclink.client.gui.JoystickIcon;
import com.apocscode.logiclink.client.gui.RemoteGuiTextures;
import com.apocscode.logiclink.client.gui.RemoteIcons;
import com.apocscode.logiclink.controller.LogicRemoteMenu;
import com.apocscode.logiclink.input.GamepadInputs;
import com.simibubi.create.foundation.gui.AllIcons;
import net.createmod.catnip.gui.element.GuiGameElement;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;

/**
 * Configuration screen for the Logic Remote.
 * Two-page tabbed GUI matching CTC's controller screen:
 * - Page 0 (Buttons): 15 button frequency pair slots
 * - Page 1 (Axes): 10 axis frequency pair slots + joystick visualization
 * <p>
 * Port of CTC's TweakedLinkedControllerScreen using Create's AbstractSimiContainerScreen.
 */
public class LogicRemoteConfigScreen extends AbstractSimiContainerScreen<LogicRemoteMenu> {

    protected RemoteGuiTextures background0;
    protected RemoteGuiTextures background1;
    private List<Rect2i> extraAreas = Collections.emptyList();

    private IconButton resetButton;
    private IconButton confirmButton;
    private IconButton refreshButton;
    private IconButton prevDeviceButton;
    private IconButton nextDeviceButton;
    private IconButton firstTabButton;
    private IconButton secondTabButton;
    private IconButton thirdTabButton;
    private IconButton bugButton;
    private JoystickIcon lStick;
    private JoystickIcon rStick;
    private DigitIcon[] controllerDigits;
    private DigitIcon[] axisDigits;
    private boolean isSecondPage = false;

    public LogicRemoteConfigScreen(LogicRemoteMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.background0 = RemoteGuiTextures.LOGIC_REMOTE_0;
        this.background1 = RemoteGuiTextures.LOGIC_REMOTE_1;
    }

    private static final int[] axisDigitPositions = {
            19, 54,
            19, 64,
            19, 117,
            19, 127,
            162, 53,
            162, 116
    };

    @Override
    protected void init() {
        setWindowSize(background0.width, background0.height + 4 + PLAYER_INVENTORY.getHeight());
        setWindowOffset(1, 0);
        super.init();

        int x = leftPos;
        int y = topPos;

        resetButton = new IconButton(x + background0.width - 62, y + background0.height - 24, AllIcons.I_TRASH);
        resetButton.withCallback(() -> {
            menu.clearContents();
            menu.sendClearPacket();
        });
        confirmButton = new IconButton(x + background0.width - 33, y + background0.height - 24, AllIcons.I_CONFIRM);
        confirmButton.withCallback(() -> {
            minecraft.player.closeContainer();
        });
        refreshButton = new IconButton(x + background0.width - 91, y + background0.height - 24, AllIcons.I_REFRESH);
        refreshButton.withCallback(() -> {
            GamepadInputs.SearchGamepad();
        });
        refreshButton.setToolTip(Component.translatable("logiclink.gui_button_refresh"));
        prevDeviceButton = new IconButton(x + background0.width - 120, y + background0.height - 24, AllIcons.I_MTD_LEFT);
        prevDeviceButton.withCallback(() -> {
            GamepadInputs.CycleGamepad();
        });
        prevDeviceButton.setToolTip(Component.translatable("logiclink.gui_device_prev"));
        nextDeviceButton = new IconButton(x + background0.width - 149, y + background0.height - 24, AllIcons.I_MTD_RIGHT);
        nextDeviceButton.withCallback(() -> {
            GamepadInputs.CycleGamepad();
        });
        nextDeviceButton.setToolTip(Component.translatable("logiclink.gui_device_next"));
        firstTabButton = new IconButton(x + 17, y + background0.height - 27, RemoteIcons.I_BUTTON);
        firstTabButton.withCallback(() -> {
            this.isSecondPage = false;
            menu.setPage(this.isSecondPage);
        });
        firstTabButton.setToolTip(Component.translatable("logiclink.gui_tab_button"));
        secondTabButton = new IconButton(x + 42, y + background0.height - 27, RemoteIcons.I_AXES);
        secondTabButton.withCallback(() -> {
            this.isSecondPage = true;
            menu.setPage(this.isSecondPage);
        });
        secondTabButton.setToolTip(Component.translatable("logiclink.gui_tab_axis"));
        thirdTabButton = new IconButton(x + 67, y + background0.height - 27, AllIcons.I_CONFIG_OPEN);
        thirdTabButton.withCallback(() -> {
            // Open the full Control Configuration screen
            minecraft.setScreen(new ControlConfigScreen());
        });
        thirdTabButton.setToolTip(Component.literal("Control Config"));
        bugButton = new IconButton(x + background0.width - 178, y + background0.height - 24, AllIcons.I_PRIORITY_VERY_HIGH);
        bugButton.withCallback(() -> {
            Util.getPlatform().openUri("https://github.com/Apocscode/CreateLogicLink/issues");
        });
        bugButton.setToolTip(Component.literal("Report a Bug"));

        addRenderableWidget(resetButton);
        addRenderableWidget(confirmButton);
        addRenderableWidget(refreshButton);
        addRenderableWidget(prevDeviceButton);
        addRenderableWidget(nextDeviceButton);
        addRenderableWidget(firstTabButton);
        addRenderableWidget(secondTabButton);
        addRenderableWidget(thirdTabButton);
        addRenderableWidget(bugButton);

        lStick = new JoystickIcon(x + 16, y + 26, RemoteIcons.I_LEFT_JOYSTICK);
        rStick = new JoystickIcon(x + 16, y + 89, RemoteIcons.I_RIGHT_JOYSTICK);

        controllerDigits = new DigitIcon[2];
        for (int i = 0; i < controllerDigits.length; i++) {
            controllerDigits[i] = new DigitIcon(x + 107 + i * 6, y + 151,
                    DigitIconRenderer.D_DASH, new Vector3f(1, 0, 0));
            addRenderableOnly(controllerDigits[i]);
        }

        axisDigits = new DigitIcon[18];
        for (int i = 0; i < axisDigits.length; i++) {
            axisDigits[i] = new DigitIcon(
                    x + axisDigitPositions[i / 3 * 2] + (i % 3) * 6,
                    y + axisDigitPositions[i / 3 * 2 + 1],
                    DigitIconRenderer.D_DASH, new Vector3f(1, 0, 0));
            addRenderableOnly(axisDigits[i]);
        }

        addRenderableOnly(lStick);
        addRenderableOnly(rStick);

        extraAreas = ImmutableList.of(
                new Rect2i(x + background0.width + 4, y + background0.height - 44, 64, 56));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        int invX = getLeftOfCentered(PLAYER_INVENTORY.getWidth());
        int invY = topPos + background0.height + 4;
        renderPlayerInventory(graphics, invX, invY);

        int x = leftPos;
        int y = topPos;

        // Update gamepad input for live visualization
        GamepadInputs.GetControls();

        if (isSecondPage) {
            background1.render(graphics, x, y);

            // Animate left joystick
            Vec2 v = new Vec2(GamepadInputs.axis[0], GamepadInputs.axis[1]);
            if (v.lengthSquared() > 1) v = v.normalized();
            lStick.move((int) (v.x * 10), (int) (v.y * 10));

            // Animate right joystick
            v = new Vec2(GamepadInputs.axis[2], GamepadInputs.axis[3]);
            if (v.lengthSquared() > 1) v = v.normalized();
            rStick.move((int) (v.x * 10), (int) (v.y * 10));

            lStick.visible = true;
            rStick.visible = true;

            // Update axis digit displays
            for (int i = 0; i < 6; i++) {
                float value = i < 4 ? Math.abs(GamepadInputs.axis[i]) : (GamepadInputs.axis[i] + 1) / 2;
                if (value < 0) value = 0;
                if (value > 1) value = 1;
                int index = Math.round(value * 15);
                if (i < 4 && GamepadInputs.axis[i] < 0 && index != 0) {
                    axisDigits[i * 3].setIcon(DigitIconRenderer.D_DASH);
                } else {
                    axisDigits[i * 3].setIcon(DigitIconRenderer.D_EMPTY);
                }
                axisDigits[i * 3 + 1].setIcon(DigitIconRenderer.D_NUMBERS[index / 10]);
                axisDigits[i * 3 + 2].setIcon(DigitIconRenderer.D_NUMBERS[index % 10]);
                for (int j = 0; j < 3; j++) {
                    axisDigits[i * 3 + j].visible = true;
                }
            }
        } else {
            background0.render(graphics, x, y);
            lStick.visible = false;
            rStick.visible = false;
            for (int i = 0; i < axisDigits.length; i++) {
                axisDigits[i].visible = false;
            }
        }

        // Gamepad index display
        MutableComponent text;
        int index = GamepadInputs.GetGamepadIndex();
        if (index < 0) {
            text = Component.translatable("logiclink.gui_gamepad_unavailable");
            controllerDigits[0].setIcon(DigitIconRenderer.D_DASH);
            controllerDigits[1].setIcon(DigitIconRenderer.D_DASH);
        } else {
            text = Component.translatable("logiclink.gui_gamepad_selected", "" + index);
            controllerDigits[0].setIcon(DigitIconRenderer.D_NUMBERS[index / 10]);
            controllerDigits[1].setIcon(DigitIconRenderer.D_NUMBERS[index % 10]);
        }
        controllerDigits[0].setToolTip(text);
        controllerDigits[1].setToolTip(text);

        graphics.drawString(font, title, x + 15, y + 4, 0xFFFFFF, false);

        // Render 3D controller item model
        GuiGameElement.of(menu.contentHolder)
                .<GuiGameElement.GuiRenderBuilder>at(x + background0.width - 4, y + background0.height - 56, -200)
                .scale(5)
                .render(graphics);
    }

    @Override
    protected void containerTick() {
        if (!ItemStack.matches(menu.player.getMainHandItem(), menu.contentHolder))
            menu.player.closeContainer();
        super.containerTick();
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        if (!menu.getCarried().isEmpty() || this.hoveredSlot == null
                || this.hoveredSlot.hasItem() || hoveredSlot.container == menu.playerInventory) {
            super.renderTooltip(graphics, x, y);
            return;
        }
        graphics.renderComponentTooltip(font,
                addToTooltip(new LinkedList<>(), hoveredSlot.getSlotIndex()), x, y);
    }

    private List<Component> addToTooltip(List<Component> list, int slot) {
        if (slot < 0 || slot >= 50) return list;
        if (slot >= 30) {
            list.add(Component.translatable("logiclink.logic_remote.frequency_slot_" + ((slot % 2) + 1),
                    GamepadInputs.GetAxisName((slot - 30) / 2)).withStyle(ChatFormatting.GOLD));
        } else {
            list.add(Component.translatable("logiclink.logic_remote.frequency_slot_" + ((slot % 2) + 1),
                    GamepadInputs.GetButtonName(slot / 2)).withStyle(ChatFormatting.GOLD));
        }
        return list;
    }

    @Override
    public List<Rect2i> getExtraAreas() {
        return extraAreas;
    }
}
