package com.apocscode.logiclink.client;

import com.apocscode.logiclink.block.LogicRemoteItem;
import com.apocscode.logiclink.client.gui.DigitIcon;
import com.apocscode.logiclink.client.gui.DigitIconRenderer;
import com.apocscode.logiclink.client.gui.JoystickIcon;
import com.apocscode.logiclink.client.gui.RemoteGuiTextures;
import com.apocscode.logiclink.client.gui.RemoteIcons;
import com.apocscode.logiclink.input.GamepadInputs;

import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.gui.element.GuiGameElement;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;

import org.joml.Vector3f;

import java.net.URI;

/**
 * Main GUI screen for the Logic Remote, opened via Shift + right-click.
 * <p>
 * Two-page tabbed GUI that is an exact visual copy of the CTC controller screen
 * (LogicRemoteConfigScreen), but without container/inventory slots.
 * <p>
 * Page 1 (Buttons tab): CTC background + 2 extra IconButtons:
 *   - Bug Report (opens GitHub issues)
 *   - Controls Page (switches to page 2)
 * <p>
 * Page 2 (Axes tab): Same CTC background, joystick visualization, no extra buttons.
 */
public class LogicRemoteScreen extends Screen {

    // ==================== Background textures ====================
    private final RemoteGuiTextures background0 = RemoteGuiTextures.LOGIC_REMOTE_0;
    private final RemoteGuiTextures background1 = RemoteGuiTextures.LOGIC_REMOTE_1;

    // ==================== Layout ====================
    private int guiLeft, guiTop;

    // ==================== Pages ====================
    /** false = page 1 (buttons tab), true = page 2 (axes tab). */
    private boolean isSecondPage = false;

    // ==================== CTC-standard widgets ====================
    private IconButton resetButton;
    private IconButton confirmButton;
    private IconButton refreshButton;
    private IconButton prevDeviceButton;
    private IconButton nextDeviceButton;
    private IconButton firstTabButton;
    private IconButton secondTabButton;

    // ==================== Page-1 extra buttons ====================
    private IconButton bugReportButton;
    private IconButton page2Button;

    // ==================== Joystick visualizations (page 2) ====================
    private JoystickIcon lStick;
    private JoystickIcon rStick;

    // ==================== Digit displays ====================
    private DigitIcon[] controllerDigits;
    private DigitIcon[] axisDigits;

    private static final int[] AXIS_DIGIT_POSITIONS = {
            19, 54,
            19, 64,
            19, 117,
            19, 127,
            162, 53,
            162, 116
    };

    /** The held Logic Remote item (for 3D render). */
    private ItemStack heldItem = ItemStack.EMPTY;

    public LogicRemoteScreen() {
        super(Component.literal("Logic Remote"));
    }

    // ==================== Init ====================

    @Override
    protected void init() {
        super.init();

        guiLeft = (this.width - background0.width) / 2;
        guiTop = (this.height - background0.height) / 2;

        int x = guiLeft;
        int y = guiTop;

        // Cache the held item for 3D render
        if (minecraft != null && minecraft.player != null) {
            heldItem = minecraft.player.getMainHandItem();
            if (!(heldItem.getItem() instanceof LogicRemoteItem)) {
                heldItem = minecraft.player.getOffhandItem();
            }
        }

        // ---- CTC-standard bottom-row buttons (exact positions from LogicRemoteConfigScreen) ----

        resetButton = new IconButton(x + background0.width - 62, y + background0.height - 24, AllIcons.I_TRASH);
        resetButton.withCallback(() -> {
            // No frequency slots to clear in this screen — no-op placeholder
        });
        resetButton.setToolTip(Component.literal("Reset"));

        confirmButton = new IconButton(x + background0.width - 33, y + background0.height - 24, AllIcons.I_CONFIRM);
        confirmButton.withCallback(this::onClose);
        confirmButton.setToolTip(Component.literal("Close"));

        refreshButton = new IconButton(x + background0.width - 91, y + background0.height - 24, AllIcons.I_REFRESH);
        refreshButton.withCallback(GamepadInputs::SearchGamepad);
        refreshButton.setToolTip(Component.translatable("logiclink.gui_button_refresh"));

        prevDeviceButton = new IconButton(x + background0.width - 120, y + background0.height - 24, AllIcons.I_MTD_LEFT);
        prevDeviceButton.withCallback(GamepadInputs::CycleGamepad);
        prevDeviceButton.setToolTip(Component.translatable("logiclink.gui_device_prev"));

        nextDeviceButton = new IconButton(x + background0.width - 149, y + background0.height - 24, AllIcons.I_MTD_RIGHT);
        nextDeviceButton.withCallback(GamepadInputs::CycleGamepad);
        nextDeviceButton.setToolTip(Component.translatable("logiclink.gui_device_next"));

        // ---- Tab buttons (exact positions from LogicRemoteConfigScreen) ----

        firstTabButton = new IconButton(x + 17, y + background0.height - 27, RemoteIcons.I_BUTTON);
        firstTabButton.withCallback(() -> {
            isSecondPage = false;
            updatePageVisibility();
        });
        firstTabButton.setToolTip(Component.translatable("logiclink.gui_tab_button"));

        secondTabButton = new IconButton(x + 42, y + background0.height - 27, RemoteIcons.I_AXES);
        secondTabButton.withCallback(() -> {
            isSecondPage = true;
            updatePageVisibility();
        });
        secondTabButton.setToolTip(Component.translatable("logiclink.gui_tab_axis"));

        // ---- Page-1 extra buttons (CTC-style IconButtons, positioned top-left below title) ----

        bugReportButton = new IconButton(x + background0.width - 62, y + 2, AllIcons.I_PRIORITY_VERY_HIGH);
        bugReportButton.withCallback(() -> {
            Util.getPlatform().openUri(URI.create("https://github.com/Apocscode/CreateLogicLink/issues"));
        });
        bugReportButton.setToolTip(Component.literal("Bug Report"));

        page2Button = new IconButton(x + background0.width - 33, y + 2, AllIcons.I_CONFIG_NEXT);
        page2Button.withCallback(() -> {
            isSecondPage = true;
            updatePageVisibility();
        });
        page2Button.setToolTip(Component.literal("Controls Page"));

        // ---- Register all widgets ----

        addRenderableWidget(resetButton);
        addRenderableWidget(confirmButton);
        addRenderableWidget(refreshButton);
        addRenderableWidget(prevDeviceButton);
        addRenderableWidget(nextDeviceButton);
        addRenderableWidget(firstTabButton);
        addRenderableWidget(secondTabButton);
        addRenderableWidget(bugReportButton);
        addRenderableWidget(page2Button);

        // ---- Joystick icons (visible on page 2 only) ----

        lStick = new JoystickIcon(x + 16, y + 26, RemoteIcons.I_LEFT_JOYSTICK);
        rStick = new JoystickIcon(x + 16, y + 89, RemoteIcons.I_RIGHT_JOYSTICK);

        addRenderableOnly(lStick);
        addRenderableOnly(rStick);

        // ---- Controller digit display (gamepad index, bottom of GUI) ----

        controllerDigits = new DigitIcon[2];
        for (int i = 0; i < controllerDigits.length; i++) {
            controllerDigits[i] = new DigitIcon(x + 107 + i * 6, y + 151,
                    DigitIconRenderer.D_DASH, new Vector3f(1, 0, 0));
            addRenderableOnly(controllerDigits[i]);
        }

        // ---- Axis digit displays (page 2 only) ----

        axisDigits = new DigitIcon[18];
        for (int i = 0; i < axisDigits.length; i++) {
            axisDigits[i] = new DigitIcon(
                    x + AXIS_DIGIT_POSITIONS[i / 3 * 2] + (i % 3) * 6,
                    y + AXIS_DIGIT_POSITIONS[i / 3 * 2 + 1],
                    DigitIconRenderer.D_DASH, new Vector3f(1, 0, 0));
            addRenderableOnly(axisDigits[i]);
        }

        updatePageVisibility();
    }

    /**
     * Show/hide widgets based on the current page.
     */
    private void updatePageVisibility() {
        // Page-1 extra buttons only visible on page 1
        bugReportButton.visible = !isSecondPage;
        page2Button.visible = !isSecondPage;
    }

    // ==================== Rendering ====================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim background overlay
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        int x = guiLeft;
        int y = guiTop;

        // Update gamepad input for live visualization
        GamepadInputs.GetControls();

        if (isSecondPage) {
            // ---- Page 2: Axes tab ----
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
                float value = i < 4
                        ? Math.abs(GamepadInputs.axis[i])
                        : (GamepadInputs.axis[i] + 1) / 2;
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
            // ---- Page 1: Buttons tab ----
            background0.render(graphics, x, y);

            lStick.visible = false;
            rStick.visible = false;
            for (DigitIcon axisDigit : axisDigits) {
                axisDigit.visible = false;
            }
        }

        // ---- Gamepad index display (both pages) ----
        MutableComponent gpText;
        int gpIndex = GamepadInputs.GetGamepadIndex();
        if (gpIndex < 0) {
            gpText = Component.translatable("logiclink.gui_gamepad_unavailable");
            controllerDigits[0].setIcon(DigitIconRenderer.D_DASH);
            controllerDigits[1].setIcon(DigitIconRenderer.D_DASH);
        } else {
            gpText = Component.translatable("logiclink.gui_gamepad_selected", "" + gpIndex);
            controllerDigits[0].setIcon(DigitIconRenderer.D_NUMBERS[gpIndex / 10]);
            controllerDigits[1].setIcon(DigitIconRenderer.D_NUMBERS[gpIndex % 10]);
        }
        controllerDigits[0].setToolTip(gpText);
        controllerDigits[1].setToolTip(gpText);

        // ---- Title (same position as LogicRemoteConfigScreen) ----
        graphics.drawString(font, title, x + 15, y + 4, 0xFFFFFF, false);

        // ---- 3D controller item model (same position as LogicRemoteConfigScreen) ----
        if (!heldItem.isEmpty()) {
            GuiGameElement.of(heldItem)
                    .<GuiGameElement.GuiRenderBuilder>at(
                            x + background0.width - 4,
                            y + background0.height - 56,
                            -200)
                    .scale(5)
                    .render(graphics);
        }

        // Render widgets (IconButtons, digits, joysticks) on top
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // ==================== Lifecycle ====================

    @Override
    public void tick() {
        super.tick();
        // Close if item is no longer in hand
        if (minecraft != null && minecraft.player != null) {
            ItemStack main = minecraft.player.getMainHandItem();
            ItemStack off = minecraft.player.getOffhandItem();
            if (!(main.getItem() instanceof LogicRemoteItem)
                    && !(off.getItem() instanceof LogicRemoteItem)) {
                this.onClose();
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
