package com.apocscode.logiclink.client;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.block.LogicRemoteItem;
import com.apocscode.logiclink.network.OpenFreqConfigPayload;
import com.apocscode.logiclink.network.RemoteControlPayload;

import com.apocscode.logiclink.client.gui.RemoteGuiTextures;

import net.minecraft.Util;
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

import java.net.URI;

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

    // ==================== Colors (Create-style warm palette) ====================
    private static final int BG_COLOR        = 0xEE8B8279;  // warm brownish panel
    private static final int BORDER_COLOR    = 0xFF5A524A;  // Create-style dark border
    private static final int PANEL_BG        = 0xFFA49A8E;  // inner panel warm gray
    private static final int TITLE_COLOR     = 0xFF575F7A;  // CTC font color
    private static final int TEXT_COLOR      = 0xFF575F7A;  // CTC font color
    private static final int GREEN           = 0xFF5CB85C;
    private static final int RED             = 0xFFD9534F;
    private static final int YELLOW          = 0xFFF0AD4E;
    private static final int CYAN            = 0xFF5BC0DE;
    private static final int GRAY            = 0xFF7A7268;
    private static final int WHITE           = 0xFFFFFFFF;
    private static final int DARK_GRAY       = 0xFF6B6358;
    private static final int SLIDER_TRACK    = 0xFF7A7268;
    private static final int SLIDER_FILL     = 0xFF5BC0DE;
    private static final int SLIDER_THUMB    = 0xFFE8E0D4;
    private static final int BTN_ON          = 0xFF5CB85C;
    private static final int BTN_OFF         = 0xFF8B5A5A;
    private static final int BTN_HOVER       = 0xFF8A8070;
    private static final int SECTION_BG      = 0xFF968C80;

    // ==================== Layout ====================
    private int guiLeft, guiTop;
    private static final int GUI_WIDTH = 260;
    private static final int GUI_HEIGHT = 260;

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

    // ==================== Auxiliary Keybind Slots ====================
    private static final int AUX_SLOT_COUNT = 4;
    private static final String[] AUX_LABELS = {"AUX 1", "AUX 2", "AUX 3", "AUX 4"};
    private static final int[] AUX_COLORS = {YELLOW, CYAN, GREEN, RED};
    private final boolean[] auxEnabled = new boolean[AUX_SLOT_COUNT];

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

        // Background — render CTC texture as decorative panel, then overlay our panels
        int texW = RemoteGuiTextures.LOGIC_REMOTE_0.width;
        int texH = RemoteGuiTextures.LOGIC_REMOTE_0.height;
        int texX = guiLeft + (GUI_WIDTH - texW) / 2;
        int texY = guiTop;
        RemoteGuiTextures.LOGIC_REMOTE_0.render(graphics, texX, texY);

        // Extended panel below texture for additional controls
        if (GUI_HEIGHT > texH) {
            graphics.fill(guiLeft - 2, guiTop + texH, guiLeft + GUI_WIDTH + 2, guiTop + GUI_HEIGHT + 2, BORDER_COLOR);
            graphics.fill(guiLeft, guiTop + texH, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, BG_COLOR);
        }

        // Title
        graphics.drawCenteredString(font, this.title, guiLeft + GUI_WIDTH / 2, guiTop + 6, TITLE_COLOR);

        // Bug Report button (top-right)
        int bugBtnX = guiLeft + GUI_WIDTH - 76;
        int bugBtnY = guiTop + 4;
        boolean bugHover = isInside(mouseX, mouseY, bugBtnX, bugBtnY, 72, 12);
        graphics.fill(bugBtnX, bugBtnY, bugBtnX + 72, bugBtnY + 12, bugHover ? BTN_HOVER : DARK_GRAY);
        graphics.drawCenteredString(font, "\u26A0 Bug Report", bugBtnX + 36, bugBtnY + 2, bugHover ? WHITE : YELLOW);

        // Motor Config button (top-left)
        int motorBtnX = guiLeft + 4;
        int motorBtnY = guiTop + 4;
        boolean motorHover = isInside(mouseX, mouseY, motorBtnX, motorBtnY, 76, 12);
        graphics.fill(motorBtnX, motorBtnY, motorBtnX + 76, motorBtnY + 12, motorHover ? BTN_HOVER : DARK_GRAY);
        graphics.drawCenteredString(font, "\u2699 Motor Config", motorBtnX + 38, motorBtnY + 2, motorHover ? WHITE : CYAN);

        // Freq Config button (below Motor Config)
        int freqBtnX = guiLeft + 4;
        int freqBtnY = guiTop + 4 + 14;
        boolean freqHover = isInside(mouseX, mouseY, freqBtnX, freqBtnY, 76, 12);
        graphics.fill(freqBtnX, freqBtnY, freqBtnX + 76, freqBtnY + 12, freqHover ? BTN_HOVER : DARK_GRAY);
        graphics.drawCenteredString(font, "\u266A Freq Config", freqBtnX + 38, freqBtnY + 2, freqHover ? WHITE : YELLOW);

        // Target count (positioned right of the Freq Config button to avoid overlap)
        String targetStr = targets.size() + " target" + (targets.size() != 1 ? "s" : "") + " bound";
        graphics.drawString(font, targetStr, guiLeft + 84, guiTop + 20, targets.isEmpty() ? GRAY : CYAN, false);

        if (targets.isEmpty()) {
            graphics.drawCenteredString(font, "No targets bound", guiLeft + GUI_WIDTH / 2, guiTop + 80, GRAY);
            graphics.drawCenteredString(font, "Link to a Hub (Shift+click)", guiLeft + GUI_WIDTH / 2, guiTop + 96, GRAY);
            graphics.drawCenteredString(font, "then configure in Motor Config", guiLeft + GUI_WIDTH / 2, guiTop + 108, GRAY);

            // Still render aux buttons even with no targets
            renderAuxButtons(graphics, mouseX, mouseY, guiTop + 130);
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

        // ==================== Auxiliary Keybind Slots ====================
        y = renderAuxButtons(graphics, mouseX, mouseY, y + 4);

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

    /**
     * Renders 4 auxiliary keybind toggle buttons in a horizontal row.
     * These are intended for redstone link control.
     */
    private int renderAuxButtons(GuiGraphics graphics, int mouseX, int mouseY, int y) {
        int sectionX = guiLeft + 6;
        int sectionW = GUI_WIDTH - 12;

        graphics.fill(sectionX, y, sectionX + sectionW, y + 30, SECTION_BG);
        graphics.drawString(font, "Auxiliary (Redstone Links)", sectionX + 4, y + 2, TEXT_COLOR, false);

        int btnW = (sectionW - 20) / AUX_SLOT_COUNT;
        int btnY = y + 13;
        for (int i = 0; i < AUX_SLOT_COUNT; i++) {
            int bx = sectionX + 6 + i * (btnW + 4);
            boolean hover = isInside(mouseX, mouseY, bx, btnY, btnW, 14);
            int bgColor = hover ? BTN_HOVER : (auxEnabled[i] ? BTN_ON : BTN_OFF);
            graphics.fill(bx, btnY, bx + btnW, btnY + 14, bgColor);

            // Left accent line
            graphics.fill(bx, btnY, bx + 2, btnY + 14, AUX_COLORS[i]);

            String label = AUX_LABELS[i] + (auxEnabled[i] ? " ON" : "");
            graphics.drawCenteredString(font, label, bx + btnW / 2, btnY + 3, WHITE);
        }

        return y + 34;
    }

    // ==================== Input Handling ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int mX = (int)mouseX, mY = (int)mouseY;

        // Bug Report button
        int bugBtnX = guiLeft + GUI_WIDTH - 76;
        int bugBtnY = guiTop + 4;
        if (isInside(mX, mY, bugBtnX, bugBtnY, 72, 12)) {
            Util.getPlatform().openUri(URI.create("https://github.com/Apocscode/CreateLogicLink/issues"));
            return true;
        }

        // Motor Config button
        int motorBtnX = guiLeft + 4;
        int motorBtnY = guiTop + 4;
        if (isInside(mX, mY, motorBtnX, motorBtnY, 76, 12)) {
            minecraft.setScreen(new MotorConfigScreen(this));
            return true;
        }

        // Freq Config button
        int freqBtnX = guiLeft + 4;
        int freqBtnY = guiTop + 4 + 14;
        if (isInside(mX, mY, freqBtnX, freqBtnY, 76, 12)) {
            // Close this screen and ask server to open the frequency config menu
            minecraft.setScreen(null);
            PacketDistributor.sendToServer(new OpenFreqConfigPayload());
            return true;
        }

        if (targets.isEmpty()) {
            // Still handle aux button clicks even with no targets
            if (handleAuxClick(mX, mY, guiTop + 130 + 4)) return true;
            return super.mouseClicked(mouseX, mouseY, button);
        }

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

            y += 76;
        }

        // ==================== Auxiliary Button Clicks ====================
        if (handleAuxClick(mX, mY, y + 4)) return true;

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

    // ==================== Auxiliary Click Handling ====================

    /**
     * Handle clicks on the 4 auxiliary keybind toggle buttons.
     * Returns true if a button was clicked.
     */
    private boolean handleAuxClick(int mX, int mY, int y) {
        int sectionX = guiLeft + 6;
        int sectionW = GUI_WIDTH - 12;
        int btnW = (sectionW - 20) / AUX_SLOT_COUNT;
        int btnY = y + 13;

        for (int i = 0; i < AUX_SLOT_COUNT; i++) {
            int bx = sectionX + 6 + i * (btnW + 4);
            if (isInside(mX, mY, bx, btnY, btnW, 14)) {
                auxEnabled[i] = !auxEnabled[i];
                sendControl(RemoteControlPayload.SET_AUX_TOGGLE, i + (auxEnabled[i] ? 0.5f : 0f));
                return true;
            }
        }
        return false;
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
