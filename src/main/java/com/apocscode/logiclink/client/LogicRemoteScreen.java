package com.apocscode.logiclink.client;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.block.LogicRemoteItem;
import com.apocscode.logiclink.network.RemoteControlPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side GUI screen for the Logic Remote, styled after CTC's controller.
 * <p>
 * Provides virtual buttons and a speed slider to control bound
 * Logic Drives and Creative Logic Motors without needing CTC.
 * The GUI renders a controller-like interface with:
 * <ul>
 *   <li>Speed slider for drive modifier (-16 to 16) or motor speed (-256 to 256)</li>
 *   <li>Enable/Disable toggle button</li>
 *   <li>Reverse toggle button (drives only)</li>
 *   <li>Target list showing bound devices</li>
 * </ul>
 */
public class LogicRemoteScreen extends Screen {

    // ==================== Colors ====================
    private static final int BG_COLOR        = 0xDD1A1A24;
    private static final int BORDER_COLOR    = 0xFF4488CC;
    private static final int PANEL_BG        = 0xFF2A2A35;
    private static final int TITLE_COLOR     = 0xFF88CCFF;
    private static final int TEXT_COLOR      = 0xFFCCCCCC;
    private static final int GREEN           = 0xFF22CC55;
    private static final int RED             = 0xFFEE3333;
    private static final int YELLOW          = 0xFFFFAA00;
    private static final int CYAN            = 0xFF00CCEE;
    private static final int GRAY            = 0xFF666677;
    private static final int WHITE           = 0xFFFFFFFF;
    private static final int DARK_GRAY       = 0xFF333344;
    private static final int SLIDER_TRACK    = 0xFF3A3A4A;
    private static final int SLIDER_FILL     = 0xFF4488CC;
    private static final int SLIDER_THUMB    = 0xFFCCDDFF;
    private static final int BTN_ON          = 0xFF22AA44;
    private static final int BTN_OFF         = 0xFF664444;
    private static final int BTN_HOVER       = 0xFF5599CC;
    private static final int SECTION_BG      = 0xFF252535;

    // ==================== Layout ====================
    private int guiLeft, guiTop;
    private static final int GUI_WIDTH = 260;
    private static final int GUI_HEIGHT = 220;

    // ==================== Source ====================
    /** 0=held item, 1=block at blockPos */
    private final int source;
    private final BlockPos blockPos;

    // ==================== Control State ====================
    private float driveModifier = 0f;
    private boolean driveEnabled = false;
    private boolean driveReversed = false;
    private int motorSpeed = 0;
    private boolean motorEnabled = false;

    /** Whether user is dragging a slider. */
    private boolean draggingDriveSlider = false;
    private boolean draggingMotorSlider = false;

    /** Cached targets from NBT. */
    private final List<TargetDisplay> targets = new ArrayList<>();
    private boolean hasDrives = false;
    private boolean hasMotors = false;

    /**
     * Open screen for a held Logic Remote item.
     */
    public LogicRemoteScreen() {
        super(Component.literal("Logic Remote"));
        this.source = RemoteControlPayload.SOURCE_ITEM;
        this.blockPos = BlockPos.ZERO;
    }

    /**
     * Open screen for a Contraption Remote block at the given position.
     */
    public LogicRemoteScreen(BlockPos blockPos) {
        super(Component.literal("Contraption Remote"));
        this.source = RemoteControlPayload.SOURCE_BLOCK;
        this.blockPos = blockPos;
    }

    @Override
    protected void init() {
        super.init();
        guiLeft = (this.width - GUI_WIDTH) / 2;
        guiTop = (this.height - GUI_HEIGHT) / 2;

        // Read targets from item NBT or block entity
        loadTargets();
    }

    private void loadTargets() {
        targets.clear();
        hasDrives = false;
        hasMotors = false;

        if (source == RemoteControlPayload.SOURCE_ITEM) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            ItemStack stack = mc.player.getMainHandItem();
            if (!(stack.getItem() instanceof LogicRemoteItem)) {
                stack = mc.player.getOffhandItem();
            }
            if (stack.isEmpty()) return;

            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (tag.contains("Targets")) {
                ListTag list = tag.getList("Targets", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag t = list.getCompound(i);
                    String type = t.getString("Type");
                    BlockPos pos = new BlockPos(t.getInt("X"), t.getInt("Y"), t.getInt("Z"));
                    targets.add(new TargetDisplay(pos, type));
                    if ("drive".equals(type)) hasDrives = true;
                    if ("creative_motor".equals(type)) hasMotors = true;
                }
            }
        }
        // For block source, we'd read from the block entity's synced data
        // The client has the BE via getUpdateTag
        if (source == RemoteControlPayload.SOURCE_BLOCK) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                var be = mc.level.getBlockEntity(blockPos);
                if (be instanceof com.apocscode.logiclink.block.ContraptionRemoteBlockEntity remote) {
                    for (var entry : remote.getTargets()) {
                        targets.add(new TargetDisplay(entry.pos(), entry.type()));
                        if ("drive".equals(entry.type())) hasDrives = true;
                        if ("creative_motor".equals(entry.type())) hasMotors = true;
                    }
                }
            }
        }
    }

    // ==================== Rendering ====================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // Background
        graphics.fill(guiLeft - 2, guiTop - 2, guiLeft + GUI_WIDTH + 2, guiTop + GUI_HEIGHT + 2, BORDER_COLOR);
        graphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, BG_COLOR);

        // Title
        graphics.drawCenteredString(font, this.title, guiLeft + GUI_WIDTH / 2, guiTop + 6, TITLE_COLOR);

        // Target count
        String targetStr = targets.size() + " target" + (targets.size() != 1 ? "s" : "") + " bound";
        graphics.drawString(font, targetStr, guiLeft + 8, guiTop + 20, targets.isEmpty() ? GRAY : CYAN, false);

        if (targets.isEmpty()) {
            graphics.drawCenteredString(font, "No targets bound", guiLeft + GUI_WIDTH / 2, guiTop + 80, GRAY);
            graphics.drawCenteredString(font, "Sneak + click a Drive or Motor", guiLeft + GUI_WIDTH / 2, guiTop + 96, GRAY);
            graphics.drawCenteredString(font, "to add targets", guiLeft + GUI_WIDTH / 2, guiTop + 108, GRAY);
            return;
        }

        int y = guiTop + 34;

        // ==================== Drive Controls ====================
        if (hasDrives) {
            y = renderDriveSection(graphics, mouseX, mouseY, y);
        }

        // ==================== Motor Controls ====================
        if (hasMotors) {
            y = renderMotorSection(graphics, mouseX, mouseY, y);
        }

        // ==================== Target List ====================
        renderTargetList(graphics, y + 4);
    }

    private int renderDriveSection(GuiGraphics graphics, int mouseX, int mouseY, int y) {
        int sectionX = guiLeft + 6;
        int sectionW = GUI_WIDTH - 12;

        // Section background
        graphics.fill(sectionX, y, sectionX + sectionW, y + 76, SECTION_BG);
        graphics.drawString(font, "Drive Control", sectionX + 4, y + 3, YELLOW, false);

        // Speed modifier slider
        int sliderX = sectionX + 8;
        int sliderY = y + 16;
        int sliderW = sectionW - 80;
        int sliderH = 12;

        renderSlider(graphics, sliderX, sliderY, sliderW, sliderH,
                driveModifier, -16f, 16f, "Modifier: " + String.format("%.1f", driveModifier));

        // Enable button
        int btnX = sectionX + sectionW - 64;
        int btnY = y + 14;
        boolean enableHover = isInside(mouseX, mouseY, btnX, btnY, 60, 16);
        graphics.fill(btnX, btnY, btnX + 60, btnY + 16, enableHover ? BTN_HOVER : (driveEnabled ? BTN_ON : BTN_OFF));
        graphics.drawCenteredString(font, driveEnabled ? "ENABLED" : "DISABLED",
                btnX + 30, btnY + 4, WHITE);

        // Reverse button
        int revBtnY = y + 34;
        boolean revHover = isInside(mouseX, mouseY, btnX, revBtnY, 60, 16);
        graphics.fill(btnX, revBtnY, btnX + 60, revBtnY + 16,
                revHover ? BTN_HOVER : (driveReversed ? YELLOW : DARK_GRAY));
        graphics.drawCenteredString(font, driveReversed ? "REVERSED" : "FORWARD",
                btnX + 30, revBtnY + 4, WHITE);

        // Speed preset buttons
        int presetY = y + 55;
        int[] presets = {-16, -8, -4, -1, 0, 1, 4, 8, 16};
        int presetBtnW = (sectionW - 16) / presets.length;
        for (int i = 0; i < presets.length; i++) {
            int px = sectionX + 8 + i * presetBtnW;
            boolean hover = isInside(mouseX, mouseY, px, presetY, presetBtnW - 2, 14);
            boolean active = Math.abs(driveModifier - presets[i]) < 0.1f;
            graphics.fill(px, presetY, px + presetBtnW - 2, presetY + 14,
                    hover ? BTN_HOVER : (active ? SLIDER_FILL : DARK_GRAY));
            graphics.drawCenteredString(font, String.valueOf(presets[i]),
                    px + (presetBtnW - 2) / 2, presetY + 3, WHITE);
        }

        return y + 80;
    }

    private int renderMotorSection(GuiGraphics graphics, int mouseX, int mouseY, int y) {
        int sectionX = guiLeft + 6;
        int sectionW = GUI_WIDTH - 12;

        // Section background
        graphics.fill(sectionX, y, sectionX + sectionW, y + 76, SECTION_BG);
        graphics.drawString(font, "Motor Control", sectionX + 4, y + 3, CYAN, false);

        // Speed slider
        int sliderX = sectionX + 8;
        int sliderY = y + 16;
        int sliderW = sectionW - 80;
        int sliderH = 12;

        renderSlider(graphics, sliderX, sliderY, sliderW, sliderH,
                motorSpeed, -256f, 256f, "Speed: " + motorSpeed + " RPM");

        // Enable button
        int btnX = sectionX + sectionW - 64;
        int btnY = y + 14;
        boolean enableHover = isInside(mouseX, mouseY, btnX, btnY, 60, 16);
        graphics.fill(btnX, btnY, btnX + 60, btnY + 16,
                enableHover ? BTN_HOVER : (motorEnabled ? BTN_ON : BTN_OFF));
        graphics.drawCenteredString(font, motorEnabled ? "ENABLED" : "DISABLED",
                btnX + 30, btnY + 4, WHITE);

        // Speed preset buttons
        int presetY = y + 34;
        int[] presets = {-256, -128, -64, -16, 0, 16, 64, 128, 256};
        int presetBtnW = (sectionW - 16) / presets.length;
        for (int i = 0; i < presets.length; i++) {
            int px = sectionX + 8 + i * presetBtnW;
            boolean hover = isInside(mouseX, mouseY, px, presetY, presetBtnW - 2, 14);
            boolean active = motorSpeed == presets[i];
            graphics.fill(px, presetY, px + presetBtnW - 2, presetY + 14,
                    hover ? BTN_HOVER : (active ? SLIDER_FILL : DARK_GRAY));
            String label = Math.abs(presets[i]) >= 100 ? String.valueOf(presets[i]) :
                    String.valueOf(presets[i]);
            graphics.drawCenteredString(font, label,
                    px + (presetBtnW - 2) / 2, presetY + 3, WHITE);
        }

        // Stop button
        int stopX = sectionX + 8;
        int stopY = y + 55;
        int stopW = sectionW - 16;
        boolean stopHover = isInside(mouseX, mouseY, stopX, stopY, stopW, 14);
        graphics.fill(stopX, stopY, stopX + stopW, stopY + 14,
                stopHover ? RED : 0xFF882222);
        graphics.drawCenteredString(font, "EMERGENCY STOP (Speed 0 + Disable)",
                stopX + stopW / 2, stopY + 3, WHITE);

        return y + 76;
    }

    private void renderSlider(GuiGraphics graphics, int x, int y, int w, int h,
                               float value, float min, float max, String label) {
        // Track
        graphics.fill(x, y, x + w, y + h, SLIDER_TRACK);

        // Fill from center
        float norm = (value - min) / (max - min); // 0 to 1
        int centerX = x + w / 2;
        int thumbX = x + (int)(norm * w);

        // Fill bar from center to current value
        if (thumbX > centerX) {
            graphics.fill(centerX, y + 1, thumbX, y + h - 1, SLIDER_FILL);
        } else if (thumbX < centerX) {
            graphics.fill(thumbX, y + 1, centerX, y + h - 1, SLIDER_FILL);
        }

        // Center line
        graphics.fill(centerX - 1, y, centerX + 1, y + h, GRAY);

        // Thumb
        graphics.fill(thumbX - 2, y - 1, thumbX + 2, y + h + 1, SLIDER_THUMB);

        // Label
        graphics.drawString(font, label, x, y - 10, TEXT_COLOR, false);
    }

    private void renderTargetList(GuiGraphics graphics, int startY) {
        int sectionX = guiLeft + 6;
        int sectionW = GUI_WIDTH - 12;
        int y = startY;

        graphics.drawString(font, "Bound Targets:", sectionX + 4, y, TEXT_COLOR, false);
        y += 12;

        int maxShow = Math.min(targets.size(), 4); // show up to 4
        for (int i = 0; i < maxShow; i++) {
            TargetDisplay t = targets.get(i);
            int color = "drive".equals(t.type) ? YELLOW : CYAN;
            String icon = "drive".equals(t.type) ? "[D]" : "[M]";
            graphics.drawString(font, icon + " " + t.type + " @ " + t.pos.toShortString(),
                    sectionX + 8, y, color, false);
            y += 10;
        }
        if (targets.size() > maxShow) {
            graphics.drawString(font, "... +" + (targets.size() - maxShow) + " more",
                    sectionX + 8, y, GRAY, false);
        }
    }

    // ==================== Input Handling ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int mX = (int)mouseX, mY = (int)mouseY;

        if (targets.isEmpty()) return super.mouseClicked(mouseX, mouseY, button);

        int y = guiTop + 34;

        if (hasDrives) {
            int sectionX = guiLeft + 6;
            int sectionW = GUI_WIDTH - 12;

            // Drive slider click
            int sliderX = sectionX + 8;
            int sliderY = y + 16;
            int sliderW = sectionW - 80;
            if (isInside(mX, mY, sliderX, sliderY - 2, sliderW, 16)) {
                draggingDriveSlider = true;
                updateDriveSlider(mouseX, sliderX, sliderW);
                return true;
            }

            // Enable button
            int btnX = sectionX + sectionW - 64;
            int btnY = y + 14;
            if (isInside(mX, mY, btnX, btnY, 60, 16)) {
                driveEnabled = !driveEnabled;
                sendControl(RemoteControlPayload.SET_DRIVE_ENABLED, driveEnabled ? 1f : 0f);
                return true;
            }

            // Reverse button
            int revBtnY = y + 34;
            if (isInside(mX, mY, btnX, revBtnY, 60, 16)) {
                driveReversed = !driveReversed;
                sendControl(RemoteControlPayload.SET_DRIVE_REVERSED, driveReversed ? 1f : 0f);
                return true;
            }

            // Speed presets
            int presetY = y + 55;
            int[] presets = {-16, -8, -4, -1, 0, 1, 4, 8, 16};
            int presetBtnW = (sectionW - 16) / presets.length;
            for (int i = 0; i < presets.length; i++) {
                int px = sectionX + 8 + i * presetBtnW;
                if (isInside(mX, mY, px, presetY, presetBtnW - 2, 14)) {
                    driveModifier = presets[i];
                    sendControl(RemoteControlPayload.SET_DRIVE_MODIFIER, driveModifier);
                    return true;
                }
            }

            y += 80;
        }

        if (hasMotors) {
            int sectionX = guiLeft + 6;
            int sectionW = GUI_WIDTH - 12;

            // Motor slider click
            int sliderX = sectionX + 8;
            int sliderY = y + 16;
            int sliderW = sectionW - 80;
            if (isInside(mX, mY, sliderX, sliderY - 2, sliderW, 16)) {
                draggingMotorSlider = true;
                updateMotorSlider(mouseX, sliderX, sliderW);
                return true;
            }

            // Enable button
            int btnX = sectionX + sectionW - 64;
            int btnY = y + 14;
            if (isInside(mX, mY, btnX, btnY, 60, 16)) {
                motorEnabled = !motorEnabled;
                sendControl(RemoteControlPayload.SET_MOTOR_ENABLED, motorEnabled ? 1f : 0f);
                return true;
            }

            // Speed presets
            int presetY = y + 34;
            int[] presets = {-256, -128, -64, -16, 0, 16, 64, 128, 256};
            int presetBtnW = (sectionW - 16) / presets.length;
            for (int i = 0; i < presets.length; i++) {
                int px = sectionX + 8 + i * presetBtnW;
                if (isInside(mX, mY, px, presetY, presetBtnW - 2, 14)) {
                    motorSpeed = presets[i];
                    sendControl(RemoteControlPayload.SET_MOTOR_SPEED, motorSpeed);
                    return true;
                }
            }

            // Emergency stop
            int stopX = sectionX + 8;
            int stopY = y + 55;
            int stopW = sectionW - 16;
            if (isInside(mX, mY, stopX, stopY, stopW, 14)) {
                motorSpeed = 0;
                motorEnabled = false;
                sendControl(RemoteControlPayload.SET_MOTOR_SPEED, 0f);
                sendControl(RemoteControlPayload.SET_MOTOR_ENABLED, 0f);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);

        int y = guiTop + 34;
        int sectionX = guiLeft + 6;
        int sectionW = GUI_WIDTH - 12;
        int sliderX = sectionX + 8;
        int sliderW = sectionW - 80;

        if (draggingDriveSlider && hasDrives) {
            updateDriveSlider(mouseX, sliderX, sliderW);
            return true;
        }

        if (draggingMotorSlider && hasMotors) {
            int motorSliderX = sliderX;
            updateMotorSlider(mouseX, motorSliderX, sliderW);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingDriveSlider) {
            draggingDriveSlider = false;
            sendControl(RemoteControlPayload.SET_DRIVE_MODIFIER, driveModifier);
        }
        if (draggingMotorSlider) {
            draggingMotorSlider = false;
            sendControl(RemoteControlPayload.SET_MOTOR_SPEED, motorSpeed);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Scroll over drive slider area
        if (hasDrives) {
            int sectionX = guiLeft + 6;
            int sectionW = GUI_WIDTH - 12;
            int sliderX = sectionX + 8;
            int sliderY = guiTop + 34 + 6;
            int sliderW = sectionW - 80;
            if (isInside((int) mouseX, (int) mouseY, sliderX, sliderY, sliderW, 22)) {
                driveModifier = clamp(driveModifier + (float) scrollY * 0.5f, -16f, 16f);
                // Quantize to 0.5
                driveModifier = Math.round(driveModifier * 2f) / 2f;
                sendControl(RemoteControlPayload.SET_DRIVE_MODIFIER, driveModifier);
                return true;
            }
        }
        // Scroll over motor slider area
        if (hasMotors) {
            int y = guiTop + 34 + (hasDrives ? 80 : 0);
            int sectionX = guiLeft + 6;
            int sectionW = GUI_WIDTH - 12;
            int sliderX = sectionX + 8;
            int sliderY = y + 6;
            int sliderW = sectionW - 80;
            if (isInside((int) mouseX, (int) mouseY, sliderX, sliderY, sliderW, 22)) {
                motorSpeed = (int) clamp(motorSpeed + (float) scrollY * 4f, -256f, 256f);
                sendControl(RemoteControlPayload.SET_MOTOR_SPEED, motorSpeed);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void updateDriveSlider(double mouseX, int sliderX, int sliderW) {
        float norm = (float) (mouseX - sliderX) / sliderW;
        norm = clamp(norm, 0f, 1f);
        driveModifier = -16f + norm * 32f;
        // Quantize to 0.5
        driveModifier = Math.round(driveModifier * 2f) / 2f;
    }

    private void updateMotorSlider(double mouseX, int sliderX, int sliderW) {
        float norm = (float) (mouseX - sliderX) / sliderW;
        norm = clamp(norm, 0f, 1f);
        motorSpeed = (int) (-256f + norm * 512f);
        // Quantize to whole RPM
        motorSpeed = Math.round(motorSpeed);
    }

    // ==================== Network ====================

    private void sendControl(int action, float value) {
        PacketDistributor.sendToServer(new RemoteControlPayload(source, blockPos, action, value));
    }

    // ==================== Utilities ====================

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean isInside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    private record TargetDisplay(BlockPos pos, String type) {}
}
