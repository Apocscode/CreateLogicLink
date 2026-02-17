package com.apocscode.logiclink.client;

import com.apocscode.logiclink.block.LogicLinkBlockEntity;
import com.apocscode.logiclink.block.LogicRemoteItem;
import com.apocscode.logiclink.controller.ControlProfile;
import com.apocscode.logiclink.controller.ControlProfile.AuxBinding;
import com.apocscode.logiclink.controller.ControlProfile.MotorBinding;
import com.apocscode.logiclink.network.HubNetwork;
import com.apocscode.logiclink.network.IHubDevice;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen control configuration screen for the Logic Remote.
 * <p>
 * Layout:
 *   Left panel:  Hub-connected device list (motors/drives)
 *   Center panel: 8 Motor binding slots (with speed, direction, sequential)
 *   Right panel:  8 Aux redstone binding slots (with power, momentary/toggle)
 * <p>
 * Opened from the 3rd tab button on LogicRemoteConfigScreen.
 * Uses the same visual style as Create's dark-panel theme.
 */
public class ControlConfigScreen extends Screen {

    // ==================== Colors (Create-style warm palette) ====================
    private static final int BG_OUTER       = 0xFF8B8279;
    private static final int BG_INNER       = 0xFFA49A8E;
    private static final int BORDER_DARK    = 0xFF5A524A;
    private static final int BORDER_LIGHT   = 0xFFCCC4B8;
    private static final int TITLE_COLOR    = 0xFF575F7A;
    private static final int LABEL_COLOR    = 0xFF575F7A;
    private static final int TEXT_DIM       = 0xFF7A7268;
    private static final int GREEN          = 0xFF5CB85C;
    private static final int RED            = 0xFFD9534F;
    private static final int YELLOW         = 0xFFF0AD4E;
    private static final int CYAN           = 0xFF5BC0DE;
    private static final int WHITE          = 0xFFFFFFFF;
    private static final int SLOT_BG        = 0xFF968C80;
    private static final int SLOT_SELECTED  = 0xFF8AA0B8;
    private static final int SLOT_ASSIGNED  = 0xFF7A9A7A;
    private static final int BTN_IDLE       = 0xFF8A8070;
    private static final int BTN_HOVER      = 0xFF6B6358;
    private static final int BTN_ACTIVE     = 0xFF5CB85C;
    private static final int FIELD_BG       = 0xFF7A7268;
    private static final int FIELD_BORDER   = 0xFF5A524A;
    private static final int DEVICE_BG      = 0xFF8B8279;
    private static final int DEVICE_HOVER   = 0xFF7A8AA0;

    // ==================== Layout ====================
    private int guiLeft, guiTop;
    private static final int GUI_WIDTH = 400;
    private static final int GUI_HEIGHT = 260;
    private static final int DEVICE_PANEL_W = 110;
    private static final int MOTOR_PANEL_X = DEVICE_PANEL_W + 6;
    private static final int MOTOR_PANEL_W = 150;
    private static final int AUX_PANEL_X = MOTOR_PANEL_X + MOTOR_PANEL_W + 4;
    private static final int AUX_PANEL_W = GUI_WIDTH - AUX_PANEL_X - 4;
    private static final int SLOT_H = 26;

    // ==================== Device Discovery ====================
    private final List<DeviceInfo> availableDevices = new ArrayList<>();
    private int deviceScrollOffset = 0;
    private static final int DEVICE_ROWS_VISIBLE = 12;

    private String linkedHubLabel = "";
    private BlockPos linkedHubPos = null;

    // ==================== Control Profile ====================
    private ControlProfile profile;

    /** Motor slot currently being assigned (-1 = none). */
    private int assigningMotorSlot = -1;

    /** Motor slot with active speed field for keyboard input (-1 = none). */
    private int editingSpeedSlot = -1;
    private String speedEditBuffer = "";

    /** Aux slot with active power field for keyboard input (-1 = none). */
    private int editingPowerSlot = -1;
    private String powerEditBuffer = "";

    // ==================== Tab State ====================
    /** 0 = Motor bindings, 1 = Aux bindings (for right panel) */
    private int activeTab = 0;

    public ControlConfigScreen() {
        super(Component.literal("Control Configuration"));
    }

    @Override
    protected void init() {
        super.init();
        guiLeft = (this.width - GUI_WIDTH) / 2;
        guiTop = (this.height - GUI_HEIGHT) / 2;

        // Load profile from held item
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ItemStack stack = getRemoteItem(mc);
            profile = ControlProfile.fromItem(stack);
        } else {
            profile = new ControlProfile();
        }

        discoverDevices();
    }

    private ItemStack getRemoteItem(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof LogicRemoteItem)) {
            stack = mc.player.getOffhandItem();
        }
        return stack;
    }

    // ==================== Device Discovery ====================

    private void discoverDevices() {
        availableDevices.clear();
        linkedHubLabel = "";
        linkedHubPos = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ItemStack remoteStack = getRemoteItem(mc);
        if (remoteStack.isEmpty() || !(remoteStack.getItem() instanceof LogicRemoteItem)) return;

        BlockPos hubPos = LogicRemoteItem.getLinkedHub(remoteStack);
        if (hubPos == null) return;

        linkedHubPos = hubPos;
        linkedHubLabel = LogicRemoteItem.getLinkedHubLabel(remoteStack);

        int range = HubNetwork.DEFAULT_RANGE;
        BlockEntity hubBe = mc.level.getBlockEntity(hubPos);
        if (hubBe instanceof LogicLinkBlockEntity hub) {
            range = hub.getHubRange();
        }

        List<BlockEntity> devices = HubNetwork.getDevicesInRange(mc.level, hubPos, range);
        for (BlockEntity be : devices) {
            if (be instanceof IHubDevice device) {
                String type = device.getDeviceType();
                if ("drive".equals(type) || "creative_motor".equals(type)) {
                    String label = device.getHubLabel().isEmpty() ? type : device.getHubLabel();
                    availableDevices.add(new DeviceInfo(device.getDevicePos(), type, label));
                }
            }
        }
    }

    // ==================== Rendering ====================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        // Outer frame
        g.fill(guiLeft - 2, guiTop - 2, guiLeft + GUI_WIDTH + 2, guiTop + GUI_HEIGHT + 2, BORDER_DARK);
        g.fill(guiLeft - 1, guiTop - 1, guiLeft + GUI_WIDTH + 1, guiTop + GUI_HEIGHT + 1, BORDER_LIGHT);
        g.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, BG_OUTER);

        // Title bar
        g.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + 16, BORDER_DARK);
        g.drawCenteredString(font, this.title, guiLeft + GUI_WIDTH / 2, guiTop + 4, TITLE_COLOR);

        // Back button (top-left)
        renderButton(g, mouseX, mouseY, guiLeft + 3, guiTop + 2, 32, 12, "\u2190 Back", BTN_IDLE, BTN_HOVER, WHITE);

        // Save button (top-right)
        renderButton(g, mouseX, mouseY, guiLeft + GUI_WIDTH - 35, guiTop + 2, 32, 12, "Save", BTN_ACTIVE, GREEN, WHITE);

        renderDevicePanel(g, mouseX, mouseY);
        renderMotorPanel(g, mouseX, mouseY);
        renderAuxPanel(g, mouseX, mouseY);

        // Help text at bottom
        if (assigningMotorSlot >= 0) {
            String axisLabel = assigningMotorSlot < ControlProfile.MOTOR_AXIS_LABELS.length
                    ? ControlProfile.MOTOR_AXIS_LABELS[assigningMotorSlot] : "?";
            g.drawCenteredString(font, "Click a device to assign to [" + axisLabel + "]",
                    guiLeft + GUI_WIDTH / 2, guiTop + GUI_HEIGHT - 10, YELLOW);
        }
    }

    private void renderDevicePanel(GuiGraphics g, int mouseX, int mouseY) {
        int panelX = guiLeft + 4;
        int panelY = guiTop + 20;
        int panelH = GUI_HEIGHT - 24;

        g.fill(panelX, panelY, panelX + DEVICE_PANEL_W, panelY + panelH, BG_INNER);
        g.fill(panelX, panelY, panelX + DEVICE_PANEL_W, panelY + 12, BORDER_DARK);

        // Hub header
        String hubHeader;
        if (linkedHubPos != null) {
            hubHeader = linkedHubLabel.isEmpty()
                    ? "Hub " + linkedHubPos.toShortString()
                    : linkedHubLabel;
        } else {
            hubHeader = "No Hub Linked";
        }
        g.drawString(font, hubHeader, panelX + 3, panelY + 2, LABEL_COLOR, false);

        if (linkedHubPos == null) {
            g.drawString(font, "Shift+click a", panelX + 4, panelY + 18, TEXT_DIM, false);
            g.drawString(font, "Logic Hub to", panelX + 4, panelY + 28, TEXT_DIM, false);
            g.drawString(font, "link remote", panelX + 4, panelY + 38, TEXT_DIM, false);
        } else if (availableDevices.isEmpty()) {
            g.drawString(font, "No devices", panelX + 4, panelY + 18, TEXT_DIM, false);
            g.drawString(font, "Place motors/", panelX + 4, panelY + 28, TEXT_DIM, false);
            g.drawString(font, "drives near hub", panelX + 4, panelY + 38, TEXT_DIM, false);
        } else {
            int listY = panelY + 14;
            int maxShow = Math.min(availableDevices.size() - deviceScrollOffset, DEVICE_ROWS_VISIBLE);
            for (int i = 0; i < maxShow; i++) {
                int idx = i + deviceScrollOffset;
                if (idx >= availableDevices.size()) break;

                DeviceInfo dev = availableDevices.get(idx);
                int dy = listY + i * 18;
                boolean hover = isInside(mouseX, mouseY, panelX + 2, dy, DEVICE_PANEL_W - 4, 16);
                boolean alreadyAssigned = isDeviceAssignedToMotor(dev.pos);

                int bg = hover ? DEVICE_HOVER : DEVICE_BG;
                if (alreadyAssigned) bg = SLOT_ASSIGNED;
                g.fill(panelX + 2, dy, panelX + DEVICE_PANEL_W - 2, dy + 16, bg);

                String icon = "drive".equals(dev.type) ? "\u25B6" : "\u2699";
                int col = alreadyAssigned ? TEXT_DIM : ("drive".equals(dev.type) ? YELLOW : CYAN);
                g.drawString(font, icon + " " + dev.label, panelX + 5, dy + 1, col, false);

                String posStr = dev.pos.getX() + "," + dev.pos.getY() + "," + dev.pos.getZ();
                g.drawString(font, posStr, panelX + 5, dy + 8, TEXT_DIM, false);
            }

            // Scroll indicators
            if (deviceScrollOffset > 0) {
                g.drawCenteredString(font, "\u25B2", panelX + DEVICE_PANEL_W / 2, panelY + 12, TEXT_DIM);
            }
            if (deviceScrollOffset + maxShow < availableDevices.size()) {
                g.drawCenteredString(font, "\u25BC", panelX + DEVICE_PANEL_W / 2,
                        panelY + panelH - 10, TEXT_DIM);
            }
        }

        // Refresh button
        int refY = panelY + panelH - 14;
        renderButton(g, mouseX, mouseY, panelX + 2, refY, DEVICE_PANEL_W - 4, 12,
                "\u21BB Refresh", BTN_IDLE, BTN_HOVER, LABEL_COLOR);
    }

    private void renderMotorPanel(GuiGraphics g, int mouseX, int mouseY) {
        int panelX = guiLeft + MOTOR_PANEL_X;
        int panelY = guiTop + 20;
        int panelH = GUI_HEIGHT - 24;

        g.fill(panelX, panelY, panelX + MOTOR_PANEL_W, panelY + panelH, BG_INNER);
        g.fill(panelX, panelY, panelX + MOTOR_PANEL_W, panelY + 12, BORDER_DARK);
        g.drawString(font, "Motor / Drive Axes", panelX + 3, panelY + 2, LABEL_COLOR, false);

        for (int i = 0; i < ControlProfile.MAX_MOTOR_BINDINGS; i++) {
            int sy = panelY + 14 + i * (SLOT_H + 2);
            renderMotorSlot(g, mouseX, mouseY, panelX + 2, sy, MOTOR_PANEL_W - 4, i);
        }
    }

    private void renderMotorSlot(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int index) {
        MotorBinding slot = profile.getMotorBinding(index);

        // Determine colors based on state
        int[] keyColors = {CYAN, GREEN, CYAN, GREEN, YELLOW, YELLOW, RED, RED};
        int keyColor = index < keyColors.length ? keyColors[index] : WHITE;
        int bg = (assigningMotorSlot == index) ? SLOT_SELECTED : (slot.hasTarget() ? SLOT_ASSIGNED : SLOT_BG);
        g.fill(x, y, x + w, y + SLOT_H, bg);

        // Left accent bar
        g.fill(x, y, x + 2, y + SLOT_H, keyColor);

        // Header: axis label + keyboard key
        String header = "[" + ControlProfile.MOTOR_AXIS_KEYS[index] + "] "
                + ControlProfile.MOTOR_AXIS_LABELS[index];
        g.drawString(font, header, x + 4, y + 2, keyColor, false);

        if (slot.hasTarget()) {
            // Assigned device info
            String icon = "drive".equals(slot.targetType) ? "\u25B6" : "\u2699";
            String typeName = "drive".equals(slot.targetType) ? "Drive" : "Motor";
            String label = slot.label.isEmpty() ? typeName : slot.label;
            g.drawString(font, icon + " " + label, x + 4, y + 12, GREEN, false);

            // Direction indicator
            int dirX = x + w - 80;
            int dirY = y + 2;
            int dirW = 28;
            boolean dirHover = isInside(mouseX, mouseY, dirX, dirY, dirW, 10);
            int dirBg = dirHover ? BTN_HOVER : (slot.reversed ? RED : BTN_ACTIVE);
            g.fill(dirX, dirY, dirX + dirW, dirY + 10, dirBg);
            g.drawCenteredString(font, slot.reversed ? "REV" : "FWD", dirX + dirW / 2, dirY + 1, WHITE);

            // Speed field
            int speedX = dirX + dirW + 2;
            int speedW = 48;
            boolean speedHover = isInside(mouseX, mouseY, speedX, dirY, speedW, 10);
            int speedBg = (editingSpeedSlot == index) ? SLOT_SELECTED : (speedHover ? FIELD_BORDER : FIELD_BG);
            g.fill(speedX, dirY, speedX + speedW, dirY + 10, speedBg);

            String speedStr = (editingSpeedSlot == index) ? speedEditBuffer + "_" : slot.speed + " RPM";
            g.drawString(font, speedStr, speedX + 2, dirY + 1, WHITE, false);

            // Sequential indicator
            if (slot.sequential) {
                g.drawString(font, "SEQ " + slot.distance + "m", x + 4, y + SLOT_H - 8, YELLOW, false);
            }

            // Clear (X) button
            int clrX = x + w - 10;
            int clrY = y + SLOT_H - 10;
            boolean clrHover = isInside(mouseX, mouseY, clrX, clrY, 9, 9);
            g.fill(clrX, clrY, clrX + 9, clrY + 9, clrHover ? RED : BTN_IDLE);
            g.drawCenteredString(font, "x", clrX + 5, clrY + 1, WHITE);
        } else {
            // Empty slot
            if (assigningMotorSlot == index) {
                g.drawString(font, "Select device...", x + 4, y + 12, YELLOW, false);
                // Cancel button
                int canX = x + w - 36;
                int canY = y + 14;
                renderButton(g, mouseX, mouseY, canX, canY, 34, 10, "Cancel", BTN_IDLE, RED, WHITE);
            } else {
                g.drawString(font, "Empty", x + 4, y + 12, TEXT_DIM, false);
                // Assign button
                int assX = x + w - 36;
                int assY = y + 14;
                renderButton(g, mouseX, mouseY, assX, assY, 34, 10, "Assign", BTN_IDLE, BTN_HOVER, WHITE);
            }
        }
    }

    private void renderAuxPanel(GuiGraphics g, int mouseX, int mouseY) {
        int panelX = guiLeft + AUX_PANEL_X;
        int panelY = guiTop + 20;
        int panelH = GUI_HEIGHT - 24;

        g.fill(panelX, panelY, panelX + AUX_PANEL_W, panelY + panelH, BG_INNER);
        g.fill(panelX, panelY, panelX + AUX_PANEL_W, panelY + 12, BORDER_DARK);
        g.drawString(font, "Aux Redstone", panelX + 3, panelY + 2, LABEL_COLOR, false);

        for (int i = 0; i < ControlProfile.MAX_AUX_BINDINGS; i++) {
            int sy = panelY + 14 + i * (SLOT_H + 2);
            renderAuxSlot(g, mouseX, mouseY, panelX + 2, sy, AUX_PANEL_W - 4, i);
        }
    }

    private void renderAuxSlot(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int index) {
        AuxBinding slot = profile.getAuxBinding(index);

        int[] auxColors = {GREEN, RED, CYAN, YELLOW, GREEN, RED, CYAN, YELLOW};
        int auxColor = index < auxColors.length ? auxColors[index] : WHITE;
        int bg = slot.hasFrequency() ? SLOT_ASSIGNED : SLOT_BG;
        g.fill(x, y, x + w, y + SLOT_H, bg);

        // Left accent bar
        g.fill(x, y, x + 2, y + SLOT_H, auxColor);

        // Header: key label + gamepad label
        String header = "[" + ControlProfile.AUX_KEYS[index] + "] "
                + ControlProfile.AUX_LABELS[index];
        g.drawString(font, header, x + 4, y + 2, auxColor, false);

        // Power level display
        int pwrX = x + w - 40;
        int pwrY = y + 2;
        boolean pwrHover = isInside(mouseX, mouseY, pwrX, pwrY, 18, 10);
        int pwrBg = (editingPowerSlot == index) ? SLOT_SELECTED : (pwrHover ? FIELD_BORDER : FIELD_BG);
        g.fill(pwrX, pwrY, pwrX + 18, pwrY + 10, pwrBg);
        String pwrStr = (editingPowerSlot == index) ? powerEditBuffer + "_" : "" + slot.power;
        g.drawCenteredString(font, pwrStr, pwrX + 9, pwrY + 1, WHITE);

        // Momentary/Toggle button
        int modeX = x + w - 20;
        boolean modeHover = isInside(mouseX, mouseY, modeX, pwrY, 18, 10);
        int modeBg = modeHover ? BTN_HOVER : (slot.momentary ? CYAN : YELLOW);
        g.fill(modeX, pwrY, modeX + 18, pwrY + 10, modeBg);
        g.drawCenteredString(font, slot.momentary ? "MOM" : "TOG", modeX + 9, pwrY + 1, WHITE);

        // Frequency status
        if (slot.hasFrequency()) {
            g.drawString(font, "Freq set", x + 4, y + 13, GREEN, false);
        } else {
            g.drawString(font, "No freq (bind link)", x + 4, y + 13, TEXT_DIM, false);
        }
    }

    private void renderButton(GuiGraphics g, int mouseX, int mouseY,
                               int x, int y, int w, int h,
                               String text, int idleColor, int hoverColor, int textColor) {
        boolean hover = isInside(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, hover ? hoverColor : idleColor);
        g.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, textColor);
    }

    // ==================== Input Handling ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int mX = (int) mouseX, mY = (int) mouseY;

        // Commit any active edit
        if (editingSpeedSlot >= 0) commitSpeedEdit();
        if (editingPowerSlot >= 0) commitPowerEdit();

        // Back button
        if (isInside(mX, mY, guiLeft + 3, guiTop + 2, 32, 12)) {
            saveProfile();
            onClose();
            return true;
        }

        // Save button
        if (isInside(mX, mY, guiLeft + GUI_WIDTH - 35, guiTop + 2, 32, 12)) {
            saveProfile();
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.literal("Configuration saved!").withStyle(ChatFormatting.GREEN), true);
            }
            return true;
        }

        // Refresh button (device panel)
        int panelX = guiLeft + 4;
        int panelY = guiTop + 20;
        int panelH = GUI_HEIGHT - 24;
        if (isInside(mX, mY, panelX + 2, panelY + panelH - 14, DEVICE_PANEL_W - 4, 12)) {
            discoverDevices();
            return true;
        }

        // Device list clicks
        if (assigningMotorSlot >= 0 && !availableDevices.isEmpty()) {
            int listY = panelY + 14;
            int maxShow = Math.min(availableDevices.size() - deviceScrollOffset, DEVICE_ROWS_VISIBLE);
            for (int i = 0; i < maxShow; i++) {
                int idx = i + deviceScrollOffset;
                if (idx >= availableDevices.size()) break;
                int dy = listY + i * 18;
                if (isInside(mX, mY, panelX + 2, dy, DEVICE_PANEL_W - 4, 16)) {
                    DeviceInfo dev = availableDevices.get(idx);
                    MotorBinding mb = profile.getMotorBinding(assigningMotorSlot);
                    mb.targetPos = dev.pos;
                    mb.targetType = dev.type;
                    mb.label = dev.label;
                    mb.speed = "drive".equals(dev.type) ? 8 : 64;
                    mb.reversed = false;
                    assigningMotorSlot = -1;
                    return true;
                }
            }
        }

        // Motor slot interactions
        int motorPanelX = guiLeft + MOTOR_PANEL_X;
        int motorSlotY = guiTop + 20 + 14;
        for (int i = 0; i < ControlProfile.MAX_MOTOR_BINDINGS; i++) {
            int sy = motorSlotY + i * (SLOT_H + 2);
            int slotX = motorPanelX + 2;
            int slotW = MOTOR_PANEL_W - 4;
            MotorBinding mb = profile.getMotorBinding(i);

            if (mb.hasTarget()) {
                // Direction toggle
                int dirX = slotX + slotW - 80;
                int dirY = sy + 2;
                if (isInside(mX, mY, dirX, dirY, 28, 10)) {
                    mb.reversed = !mb.reversed;
                    return true;
                }

                // Speed field click
                int speedX = dirX + 30;
                if (isInside(mX, mY, speedX, dirY, 48, 10)) {
                    editingSpeedSlot = i;
                    speedEditBuffer = String.valueOf(mb.speed);
                    return true;
                }

                // Clear button
                int clrX = slotX + slotW - 10;
                int clrY = sy + SLOT_H - 10;
                if (isInside(mX, mY, clrX, clrY, 9, 9)) {
                    mb.targetPos = null;
                    mb.targetType = "";
                    mb.label = "";
                    if (assigningMotorSlot == i) assigningMotorSlot = -1;
                    return true;
                }
            } else {
                if (assigningMotorSlot == i) {
                    // Cancel button
                    int canX = slotX + slotW - 36;
                    int canY = sy + 14;
                    if (isInside(mX, mY, canX, canY, 34, 10)) {
                        assigningMotorSlot = -1;
                        return true;
                    }
                } else {
                    // Assign button
                    int assX = slotX + slotW - 36;
                    int assY = sy + 14;
                    if (isInside(mX, mY, assX, assY, 34, 10)) {
                        assigningMotorSlot = i;
                        deviceScrollOffset = 0;
                        return true;
                    }
                }
            }
        }

        // Aux slot interactions
        int auxPanelX = guiLeft + AUX_PANEL_X;
        int auxSlotY = guiTop + 20 + 14;
        for (int i = 0; i < ControlProfile.MAX_AUX_BINDINGS; i++) {
            int sy = auxSlotY + i * (SLOT_H + 2);
            int slotX = auxPanelX + 2;
            int slotW = AUX_PANEL_W - 4;
            AuxBinding ab = profile.getAuxBinding(i);

            // Power level click
            int pwrX = slotX + slotW - 40;
            int pwrY = sy + 2;
            if (isInside(mX, mY, pwrX, pwrY, 18, 10)) {
                editingPowerSlot = i;
                powerEditBuffer = String.valueOf(ab.power);
                return true;
            }

            // Momentary/Toggle button
            int modeX = slotX + slotW - 20;
            if (isInside(mX, mY, modeX, pwrY, 18, 10)) {
                ab.momentary = !ab.momentary;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mX = (int) mouseX, mY = (int) mouseY;

        // Scroll device list
        int panelX = guiLeft + 4;
        int panelY = guiTop + 20;
        int panelH = GUI_HEIGHT - 24;
        if (isInside(mX, mY, panelX, panelY, DEVICE_PANEL_W, panelH)) {
            int max = Math.max(0, availableDevices.size() - DEVICE_ROWS_VISIBLE);
            if (scrollY > 0 && deviceScrollOffset > 0) {
                deviceScrollOffset--;
                return true;
            }
            if (scrollY < 0 && deviceScrollOffset < max) {
                deviceScrollOffset++;
                return true;
            }
            return true;
        }

        // Scroll speed on motor slots
        int motorPanelX = guiLeft + MOTOR_PANEL_X;
        int motorSlotY = guiTop + 20 + 14;
        for (int i = 0; i < ControlProfile.MAX_MOTOR_BINDINGS; i++) {
            int sy = motorSlotY + i * (SLOT_H + 2);
            MotorBinding mb = profile.getMotorBinding(i);
            if (!mb.hasTarget()) continue;

            int slotW = MOTOR_PANEL_W - 4;
            int speedX = motorPanelX + 2 + slotW - 50;
            int speedY = sy + 2;
            if (isInside(mX, mY, speedX, speedY, 48, 10)) {
                int delta = (int) Math.signum(scrollY);
                if (hasShiftDown()) delta *= 10;
                mb.speed = Math.max(1, Math.min(256, mb.speed + delta));
                return true;
            }
        }

        // Scroll power on aux slots
        int auxPanelX = guiLeft + AUX_PANEL_X;
        int auxSlotY = guiTop + 20 + 14;
        for (int i = 0; i < ControlProfile.MAX_AUX_BINDINGS; i++) {
            int sy = auxSlotY + i * (SLOT_H + 2);
            int slotW = AUX_PANEL_W - 4;
            int pwrX = auxPanelX + 2 + slotW - 40;
            int pwrY = sy + 2;
            if (isInside(mX, mY, pwrX, pwrY, 18, 10)) {
                AuxBinding ab = profile.getAuxBinding(i);
                int delta = (int) Math.signum(scrollY);
                ab.power = Math.max(1, Math.min(15, ab.power + delta));
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Speed edit mode
        if (editingSpeedSlot >= 0) {
            if (keyCode == 259) { // Backspace
                if (!speedEditBuffer.isEmpty())
                    speedEditBuffer = speedEditBuffer.substring(0, speedEditBuffer.length() - 1);
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // Enter
                commitSpeedEdit();
                return true;
            }
            if (keyCode == 256) { // Escape
                editingSpeedSlot = -1;
                speedEditBuffer = "";
                return true;
            }
            char c = keyToChar(keyCode);
            if (c >= '0' && c <= '9' && speedEditBuffer.length() < 3) {
                speedEditBuffer += c;
                return true;
            }
            return true;
        }

        // Power edit mode
        if (editingPowerSlot >= 0) {
            if (keyCode == 259) {
                if (!powerEditBuffer.isEmpty())
                    powerEditBuffer = powerEditBuffer.substring(0, powerEditBuffer.length() - 1);
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                commitPowerEdit();
                return true;
            }
            if (keyCode == 256) {
                editingPowerSlot = -1;
                powerEditBuffer = "";
                return true;
            }
            char c = keyToChar(keyCode);
            if (c >= '0' && c <= '9' && powerEditBuffer.length() < 2) {
                powerEditBuffer += c;
                return true;
            }
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private char keyToChar(int keyCode) {
        if (keyCode >= 48 && keyCode <= 57) return (char) keyCode;
        if (keyCode >= 320 && keyCode <= 329) return (char) (keyCode - 272);
        return 0;
    }

    private void commitSpeedEdit() {
        if (editingSpeedSlot >= 0 && editingSpeedSlot < ControlProfile.MAX_MOTOR_BINDINGS) {
            try {
                int val = Integer.parseInt(speedEditBuffer);
                profile.getMotorBinding(editingSpeedSlot).speed = Math.max(1, Math.min(256, val));
            } catch (NumberFormatException ignored) {}
        }
        editingSpeedSlot = -1;
        speedEditBuffer = "";
    }

    private void commitPowerEdit() {
        if (editingPowerSlot >= 0 && editingPowerSlot < ControlProfile.MAX_AUX_BINDINGS) {
            try {
                int val = Integer.parseInt(powerEditBuffer);
                profile.getAuxBinding(editingPowerSlot).power = Math.max(1, Math.min(15, val));
            } catch (NumberFormatException ignored) {}
        }
        editingPowerSlot = -1;
        powerEditBuffer = "";
    }

    private void saveProfile() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack stack = getRemoteItem(mc);
        if (!stack.isEmpty()) {
            ControlProfile.saveToItem(stack, profile);

            // Also save in legacy AxisConfig format for backward compat with RemoteClientHandler
            saveLegacyAxisConfig(stack);
        }
    }

    /**
     * Save first 4 motor bindings in old AxisConfig format for RemoteClientHandler compatibility.
     */
    private void saveLegacyAxisConfig(ItemStack stack) {
        net.minecraft.nbt.CompoundTag tag = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();

        net.minecraft.nbt.ListTag axisList = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < Math.min(ControlProfile.MAX_MOTOR_BINDINGS, 8); i++) {
            net.minecraft.nbt.CompoundTag slot = new net.minecraft.nbt.CompoundTag();
            MotorBinding mb = profile.getMotorBinding(i);
            if (mb.hasTarget()) {
                slot.putInt("X", mb.targetPos.getX());
                slot.putInt("Y", mb.targetPos.getY());
                slot.putInt("Z", mb.targetPos.getZ());
                slot.putString("Type", mb.targetType);
                slot.putBoolean("Reversed", mb.reversed);
                slot.putInt("Speed", mb.speed);
                slot.putBoolean("Sequential", mb.sequential);
                slot.putInt("Distance", mb.distance);
            }
            axisList.add(slot);
        }
        tag.put("AxisConfig", axisList);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
    }

    private boolean isDeviceAssignedToMotor(BlockPos pos) {
        for (int i = 0; i < ControlProfile.MAX_MOTOR_BINDINGS; i++) {
            MotorBinding mb = profile.getMotorBinding(i);
            if (mb.targetPos != null && mb.targetPos.equals(pos)) return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        if (editingSpeedSlot >= 0) commitSpeedEdit();
        if (editingPowerSlot >= 0) commitPowerEdit();
        saveProfile();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean isInside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ==================== Data ====================

    private record DeviceInfo(BlockPos pos, String type, String label) {}
}
