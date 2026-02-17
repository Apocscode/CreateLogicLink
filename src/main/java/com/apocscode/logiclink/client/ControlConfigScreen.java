package com.apocscode.logiclink.client;

import com.apocscode.logiclink.block.ContraptionRemoteBlockEntity;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.network.SaveBlockProfilePayload;
import com.apocscode.logiclink.network.SaveControlProfilePayload;
import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.Util;
import java.net.URI;
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

    // ==================== Motor Panel Scroll ====================
    private int motorScrollOffset = 0;
    private static final int MOTOR_ROWS_VISIBLE = 8;

    private String linkedHubLabel = "";
    private BlockPos linkedHubPos = null;

    // ==================== Control Profile ====================
    private ControlProfile profile;

    /** Motor slot currently being assigned (-1 = none). */
    private int assigningMotorSlot = -1;

    /** Motor slot with active speed field for keyboard input (-1 = none). */
    private int editingSpeedSlot = -1;
    private String speedEditBuffer = "";

    /** Motor slot with active distance field for keyboard input (-1 = none). */
    private int editingDistanceSlot = -1;
    private String distanceEditBuffer = "";

    /** Aux slot with active power field for keyboard input (-1 = none). */
    private int editingPowerSlot = -1;
    private String powerEditBuffer = "";

    // ==================== Frequency Picker ====================
    /** Aux slot index for frequency picker (-1 = closed). */
    private int freqPickerAuxSlot = -1;
    /** Which frequency slot: 1 or 2. */
    private int freqPickerSlotNum = 1;
    /** Current search text in frequency picker. */
    private String freqPickerSearch = "";
    /** Filtered item results for the picker. */
    private final List<Item> freqPickerResults = new ArrayList<>();
    /** Scroll offset in the picker result list. */
    private int freqPickerScroll = 0;
    private static final int FREQ_PICKER_W = 170;
    private static final int FREQ_PICKER_H = 190;
    private static final int FREQ_PICKER_ROWS = 8;

    // ==================== Tab State ====================
    /** 0 = Motor bindings, 1 = Aux bindings (for right panel) */
    private int activeTab = 0;

    /** Ticks remaining for save flash feedback. */
    private int saveFlashTicks = 0;

    /** Block position for block mode (null = item mode). */
    private final BlockPos configBlockPos;

    /** Item mode constructor. */
    public ControlConfigScreen() {
        super(Component.literal("Control Configuration"));
        this.configBlockPos = null;
    }

    /** Block mode constructor — loads/saves profile from block entity at pos. */
    public ControlConfigScreen(BlockPos blockPos) {
        super(Component.literal("Control Configuration"));
        this.configBlockPos = blockPos;
    }

    @Override
    protected void init() {
        super.init();
        guiLeft = (this.width - GUI_WIDTH) / 2;
        guiTop = (this.height - GUI_HEIGHT) / 2;

        // Load profile
        Minecraft mc = Minecraft.getInstance();
        if (configBlockPos != null && mc.level != null) {
            // Block mode: load from block entity
            BlockEntity be = mc.level.getBlockEntity(configBlockPos);
            if (be instanceof ContraptionRemoteBlockEntity remote) {
                profile = remote.getControlProfile();
                // Make a deep copy so edits don't modify the BE directly
                profile = ControlProfile.load(profile.save());
            } else {
                profile = new ControlProfile();
            }
        } else if (mc.player != null) {
            // Item mode: load from held item
            ItemStack stack = getRemoteItem(mc);
            profile = ControlProfile.fromItem(stack);
            int motorCount = 0;
            for (int i = 0; i < ControlProfile.MAX_MOTOR_BINDINGS; i++) {
                if (profile.getMotorBinding(i).hasTarget()) motorCount++;
            }
            LogicLink.LOGGER.info("[ControlConfigScreen] init() loaded profile from {}: {} motors bound",
                    stack.getItem().getClass().getSimpleName(), motorCount);
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

        int range = HubNetwork.DEFAULT_RANGE;

        if (configBlockPos != null) {
            // Block mode: use the block's position as center to discover nearby devices
            linkedHubLabel = "Block Mode";
            linkedHubPos = configBlockPos;
            List<BlockEntity> devices = HubNetwork.getDevicesInRange(mc.level, configBlockPos, range);
            for (BlockEntity be : devices) {
                if (be instanceof IHubDevice device) {
                    String type = device.getDeviceType();
                    if ("drive".equals(type) || "creative_motor".equals(type)) {
                        String label = device.getHubLabel().isEmpty() ? type : device.getHubLabel();
                        availableDevices.add(new DeviceInfo(device.getDevicePos(), type, label));
                    }
                }
            }
            return;
        }

        // Item mode: get hub from held remote
        ItemStack remoteStack = getRemoteItem(mc);
        if (remoteStack.isEmpty() || !(remoteStack.getItem() instanceof LogicRemoteItem)) return;

        BlockPos hubPos = LogicRemoteItem.getLinkedHub(remoteStack);
        if (hubPos == null) return;

        linkedHubPos = hubPos;
        linkedHubLabel = LogicRemoteItem.getLinkedHubLabel(remoteStack);

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

        // Bug report button (top, next to save)
        renderButton(g, mouseX, mouseY, guiLeft + GUI_WIDTH - 70, guiTop + 2, 12, 12, "\u26A0", 0xFF994400, 0xFFCC6600, 0xFFFFDD00);

        // Save button (top-right) — flashes bright green after save
        int saveBg = saveFlashTicks > 0 ? GREEN : BTN_ACTIVE;
        int saveHover = saveFlashTicks > 0 ? 0xFF7FD97F : GREEN;
        String saveLabel = saveFlashTicks > 0 ? "✔ Saved" : "Save";
        renderButton(g, mouseX, mouseY, guiLeft + GUI_WIDTH - 35, guiTop + 2, 32, 12, saveLabel, saveBg, saveHover, WHITE);

        // Tick down save flash
        if (saveFlashTicks > 0) saveFlashTicks--;

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

        // Frequency picker popup overlay
        if (freqPickerAuxSlot >= 0) {
            renderFreqPicker(g, mouseX, mouseY);
        }
    }

    private void renderFreqPicker(GuiGraphics g, int mouseX, int mouseY) {
        int px = (this.width - FREQ_PICKER_W) / 2;
        int py = (this.height - FREQ_PICKER_H) / 2;

        // Dim background
        g.fill(0, 0, this.width, this.height, 0x88000000);

        // Popup border + background
        g.fill(px - 2, py - 2, px + FREQ_PICKER_W + 2, py + FREQ_PICKER_H + 2, BORDER_DARK);
        g.fill(px - 1, py - 1, px + FREQ_PICKER_W + 1, py + FREQ_PICKER_H + 1, BORDER_LIGHT);
        g.fill(px, py, px + FREQ_PICKER_W, py + FREQ_PICKER_H, BG_OUTER);

        // Title
        String title = "Freq Slot " + freqPickerSlotNum + " — Aux [" +
                ControlProfile.AUX_KEYS[freqPickerAuxSlot] + "]";
        g.fill(px, py, px + FREQ_PICKER_W, py + 14, BORDER_DARK);
        g.drawCenteredString(font, title, px + FREQ_PICKER_W / 2, py + 3, TITLE_COLOR);

        // Search field
        int sfX = px + 4;
        int sfY = py + 18;
        int sfW = FREQ_PICKER_W - 8;
        g.fill(sfX, sfY, sfX + sfW, sfY + 12, FIELD_BG);
        g.fill(sfX, sfY, sfX + sfW, sfY + 1, FIELD_BORDER);
        g.fill(sfX, sfY + 11, sfX + sfW, sfY + 12, FIELD_BORDER);
        g.fill(sfX, sfY, sfX + 1, sfY + 12, FIELD_BORDER);
        g.fill(sfX + sfW - 1, sfY, sfX + sfW, sfY + 12, FIELD_BORDER);
        String searchDisplay = freqPickerSearch.isEmpty() ? "\u00A77Search items..." : freqPickerSearch + "_";
        g.drawString(font, searchDisplay, sfX + 3, sfY + 2, WHITE, false);

        // Results list
        int listY = sfY + 16;
        int rowH = 18;
        int maxShow = Math.min(freqPickerResults.size() - freqPickerScroll, FREQ_PICKER_ROWS);
        for (int i = 0; i < maxShow; i++) {
            int idx = i + freqPickerScroll;
            if (idx >= freqPickerResults.size()) break;
            Item item = freqPickerResults.get(idx);
            int ry = listY + i * rowH;
            boolean hover = isInside(mouseX, mouseY, px + 2, ry, FREQ_PICKER_W - 4, rowH);
            g.fill(px + 2, ry, px + FREQ_PICKER_W - 2, ry + rowH - 1, hover ? DEVICE_HOVER : DEVICE_BG);

            // Item icon (16x16)
            g.renderItem(new ItemStack(item), px + 4, ry + 1);

            // Item name (truncated to fit)
            String itemName = item.getDescription().getString();
            if (font.width(itemName) > FREQ_PICKER_W - 28) {
                while (font.width(itemName + "..") > FREQ_PICKER_W - 28 && itemName.length() > 1) {
                    itemName = itemName.substring(0, itemName.length() - 1);
                }
                itemName += "..";
            }
            g.drawString(font, itemName, px + 22, ry + 5, WHITE, false);
        }

        // Scroll indicators
        if (freqPickerScroll > 0) {
            g.drawCenteredString(font, "\u25B2", px + FREQ_PICKER_W / 2, listY - 6, TEXT_DIM);
        }
        if (freqPickerScroll + FREQ_PICKER_ROWS < freqPickerResults.size()) {
            g.drawCenteredString(font, "\u25BC", px + FREQ_PICKER_W / 2, listY + FREQ_PICKER_ROWS * rowH, TEXT_DIM);
        }

        // Result count
        g.drawString(font, freqPickerResults.size() + " items", px + 4, py + FREQ_PICKER_H - 12, TEXT_DIM, false);

        // Close hint
        g.drawString(font, "ESC to close", px + FREQ_PICKER_W - 60, py + FREQ_PICKER_H - 12, TEXT_DIM, false);
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
                g.drawString(font, posStr, panelX + 5, dy + 8, YELLOW, false);
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

        int totalSlots = ControlProfile.MAX_MOTOR_BINDINGS;
        int maxShow = Math.min(totalSlots - motorScrollOffset, MOTOR_ROWS_VISIBLE);
        for (int i = 0; i < maxShow; i++) {
            int slotIndex = i + motorScrollOffset;
            int sy = panelY + 14 + i * (SLOT_H + 2);
            renderMotorSlot(g, mouseX, mouseY, panelX + 2, sy, MOTOR_PANEL_W - 4, slotIndex);
        }

        // Scroll indicators
        if (motorScrollOffset > 0) {
            g.drawCenteredString(font, "\u25B2", panelX + MOTOR_PANEL_W / 2, panelY + 12, YELLOW);
        }
        if (motorScrollOffset + maxShow < totalSlots) {
            g.drawCenteredString(font, "\u25BC", panelX + MOTOR_PANEL_W / 2, panelY + panelH - 10, YELLOW);
        }
    }

    private void renderMotorSlot(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int index) {
        MotorBinding slot = profile.getMotorBinding(index);

        // Determine colors based on state
        int[] keyColors = {CYAN, GREEN, CYAN, GREEN, YELLOW, YELLOW, YELLOW, YELLOW, RED, RED, RED, RED};
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

            // Sequential/Continuous toggle (bottom row, under direction)
            int seqBtnX = dirX;
            int seqBtnY = y + SLOT_H - 11;
            int seqBtnW = 14;
            boolean seqHover = isInside(mouseX, mouseY, seqBtnX, seqBtnY, seqBtnW, 10);
            int seqBg = slot.sequential ? (seqHover ? YELLOW : 0xFFB89A4E) : (seqHover ? BTN_HOVER : BTN_IDLE);
            g.fill(seqBtnX, seqBtnY, seqBtnX + seqBtnW, seqBtnY + 10, seqBg);
            g.drawCenteredString(font, slot.sequential ? "S" : "C", seqBtnX + seqBtnW / 2, seqBtnY + 1, WHITE);

            // Distance field (only shown when sequential)
            if (slot.sequential) {
                int distX = seqBtnX + seqBtnW + 2;
                int distW = 32;
                boolean distHover = isInside(mouseX, mouseY, distX, seqBtnY, distW, 10);
                int distBg = (editingDistanceSlot == index) ? SLOT_SELECTED : (distHover ? FIELD_BORDER : FIELD_BG);
                g.fill(distX, seqBtnY, distX + distW, seqBtnY + 10, distBg);
                String distStr = (editingDistanceSlot == index) ? distanceEditBuffer + "_" : slot.distance + "m";
                g.drawString(font, distStr, distX + 2, seqBtnY + 1, YELLOW, false);
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

        // Frequency item slots (bottom row)
        int freqY = y + 14;
        int freqBoxSize = 12;
        // Slot 1
        int f1x = x + 4;
        boolean f1hover = isInside(mouseX, mouseY, f1x, freqY, freqBoxSize, freqBoxSize);
        g.fill(f1x, freqY, f1x + freqBoxSize, freqY + freqBoxSize, f1hover ? FIELD_BORDER : FIELD_BG);
        if (!slot.freqId1.isEmpty()) {
            Item item1 = BuiltInRegistries.ITEM.get(ResourceLocation.parse(slot.freqId1));
            if (item1 != Items.AIR) {
                g.pose().pushPose();
                g.pose().translate(f1x - 2, freqY - 2, 0);
                g.pose().scale(0.75f, 0.75f, 1.0f);
                g.renderItem(new ItemStack(item1), 0, 0);
                g.pose().popPose();
            }
        }
        // Slot 2
        int f2x = f1x + freqBoxSize + 2;
        boolean f2hover = isInside(mouseX, mouseY, f2x, freqY, freqBoxSize, freqBoxSize);
        g.fill(f2x, freqY, f2x + freqBoxSize, freqY + freqBoxSize, f2hover ? FIELD_BORDER : FIELD_BG);
        if (!slot.freqId2.isEmpty()) {
            Item item2 = BuiltInRegistries.ITEM.get(ResourceLocation.parse(slot.freqId2));
            if (item2 != Items.AIR) {
                g.pose().pushPose();
                g.pose().translate(f2x - 2, freqY - 2, 0);
                g.pose().scale(0.75f, 0.75f, 1.0f);
                g.renderItem(new ItemStack(item2), 0, 0);
                g.pose().popPose();
            }
        }
        // Clear freq button (only when at least one is set)
        if (slot.hasFrequency()) {
            int clrX = f2x + freqBoxSize + 2;
            boolean clrHover = isInside(mouseX, mouseY, clrX, freqY + 1, 9, 9);
            g.fill(clrX, freqY + 1, clrX + 9, freqY + 10, clrHover ? RED : BTN_IDLE);
            g.drawCenteredString(font, "x", clrX + 5, freqY + 2, WHITE);
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

        // Frequency picker popup takes priority
        if (freqPickerAuxSlot >= 0) {
            int px = (this.width - FREQ_PICKER_W) / 2;
            int py = (this.height - FREQ_PICKER_H) / 2;

            // Click inside popup?
            if (isInside(mX, mY, px, py, FREQ_PICKER_W, FREQ_PICKER_H)) {
                // Click on result item
                int sfY = py + 18;
                int listY = sfY + 16;
                int rowH = 18;
                int maxShow = Math.min(freqPickerResults.size() - freqPickerScroll, FREQ_PICKER_ROWS);
                for (int i = 0; i < maxShow; i++) {
                    int idx = i + freqPickerScroll;
                    int ry = listY + i * rowH;
                    if (isInside(mX, mY, px + 2, ry, FREQ_PICKER_W - 4, rowH)) {
                        Item selected = freqPickerResults.get(idx);
                        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(selected);
                        AuxBinding ab = profile.getAuxBinding(freqPickerAuxSlot);
                        if (freqPickerSlotNum == 1) {
                            ab.freqId1 = itemId.toString();
                        } else {
                            ab.freqId2 = itemId.toString();
                        }
                        freqPickerAuxSlot = -1;
                        autoSave();
                        return true;
                    }
                }
                return true; // consumed click inside popup
            } else {
                // Click outside popup = close
                freqPickerAuxSlot = -1;
                return true;
            }
        }

        // Commit any active edit
        if (editingSpeedSlot >= 0) commitSpeedEdit();
        if (editingDistanceSlot >= 0) commitDistanceEdit();
        if (editingPowerSlot >= 0) commitPowerEdit();

        // Back button
        if (isInside(mX, mY, guiLeft + 3, guiTop + 2, 32, 12)) {
            saveProfile();
            onClose();
            return true;
        }

        // Bug report button
        if (isInside(mX, mY, guiLeft + GUI_WIDTH - 70, guiTop + 2, 12, 12)) {
            Util.getPlatform().openUri(URI.create("https://github.com/Apocscode/CreateLogicLink/issues"));
            return true;
        }

        // Save button
        if (isInside(mX, mY, guiLeft + GUI_WIDTH - 35, guiTop + 2, 32, 12)) {
            saveProfile();
            saveFlashTicks = 30; // 1.5 seconds visual feedback
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.literal("✔ Profile Saved").withStyle(ChatFormatting.GREEN), true);
                minecraft.player.playSound(
                        net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
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
                    autoSave();
                    return true;
                }
            }
        }

        // Motor slot interactions (scroll-aware)
        int motorPanelX = guiLeft + MOTOR_PANEL_X;
        int motorSlotY = guiTop + 20 + 14;
        int motorMaxShow = Math.min(ControlProfile.MAX_MOTOR_BINDINGS - motorScrollOffset, MOTOR_ROWS_VISIBLE);
        for (int vi = 0; vi < motorMaxShow; vi++) {
            int i = vi + motorScrollOffset;
            int sy = motorSlotY + vi * (SLOT_H + 2);
            int slotX = motorPanelX + 2;
            int slotW = MOTOR_PANEL_W - 4;
            MotorBinding mb = profile.getMotorBinding(i);

            if (mb.hasTarget()) {
                // Direction toggle
                int dirX = slotX + slotW - 80;
                int dirY = sy + 2;
                if (isInside(mX, mY, dirX, dirY, 28, 10)) {
                    mb.reversed = !mb.reversed;
                    autoSave();
                    return true;
                }

                // Speed field click
                int speedX = dirX + 30;
                if (isInside(mX, mY, speedX, dirY, 48, 10)) {
                    editingSpeedSlot = i;
                    speedEditBuffer = String.valueOf(mb.speed);
                    return true;
                }

                // Sequential/Continuous toggle
                int seqBtnX = slotX + slotW - 80;
                int seqBtnY = sy + SLOT_H - 11;
                if (isInside(mX, mY, seqBtnX, seqBtnY, 14, 10)) {
                    mb.sequential = !mb.sequential;
                    autoSave();
                    return true;
                }

                // Distance field click (only when sequential)
                if (mb.sequential) {
                    int distX = seqBtnX + 16;
                    if (isInside(mX, mY, distX, seqBtnY, 32, 10)) {
                        editingDistanceSlot = i;
                        distanceEditBuffer = String.valueOf(mb.distance);
                        return true;
                    }
                }

                // Clear button
                int clrX = slotX + slotW - 10;
                int clrY = sy + SLOT_H - 10;
                if (isInside(mX, mY, clrX, clrY, 9, 9)) {
                    mb.targetPos = null;
                    mb.targetType = "";
                    mb.label = "";
                    if (assigningMotorSlot == i) assigningMotorSlot = -1;
                    autoSave();
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
                autoSave();
                return true;
            }

            // Frequency slot 1 click
            int freqY = sy + 14;
            int f1x = slotX + 4;
            if (isInside(mX, mY, f1x, freqY, 12, 12)) {
                openFreqPicker(i, 1);
                return true;
            }

            // Frequency slot 2 click
            int f2x = f1x + 14;
            if (isInside(mX, mY, f2x, freqY, 12, 12)) {
                openFreqPicker(i, 2);
                return true;
            }

            // Frequency clear button
            if (ab.hasFrequency()) {
                int clrX = f2x + 14;
                if (isInside(mX, mY, clrX, freqY + 1, 9, 9)) {
                    ab.freqId1 = "";
                    ab.freqId2 = "";
                    autoSave();
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mX = (int) mouseX, mY = (int) mouseY;

        // Frequency picker scroll
        if (freqPickerAuxSlot >= 0) {
            int maxScroll = Math.max(0, freqPickerResults.size() - FREQ_PICKER_ROWS);
            if (scrollY > 0 && freqPickerScroll > 0) {
                freqPickerScroll--;
            } else if (scrollY < 0 && freqPickerScroll < maxScroll) {
                freqPickerScroll++;
            }
            return true;
        }

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

        // Scroll motor panel list
        int motorPanelX = guiLeft + MOTOR_PANEL_X;
        int motorPanelY = guiTop + 20;
        int motorPanelH = GUI_HEIGHT - 24;
        if (isInside(mX, mY, motorPanelX, motorPanelY, MOTOR_PANEL_W, motorPanelH)) {
            // Check if hovering over a speed field first (speed scroll takes priority)
            int motorSlotY = motorPanelY + 14;
            boolean speedScrolled = false;
            int motorMaxShow = Math.min(ControlProfile.MAX_MOTOR_BINDINGS - motorScrollOffset, MOTOR_ROWS_VISIBLE);
            for (int vi = 0; vi < motorMaxShow; vi++) {
                int idx = vi + motorScrollOffset;
                int sy = motorSlotY + vi * (SLOT_H + 2);
                MotorBinding mb = profile.getMotorBinding(idx);
                if (!mb.hasTarget()) continue;

                int slotW = MOTOR_PANEL_W - 4;
                int speedX = motorPanelX + 2 + slotW - 50;
                int speedY = sy + 2;
                if (isInside(mX, mY, speedX, speedY, 48, 10)) {
                    int delta = (int) Math.signum(scrollY);
                    if (hasShiftDown()) delta *= 10;
                    mb.speed = Math.max(1, Math.min(256, mb.speed + delta));
                    autoSave();
                    speedScrolled = true;
                    break;
                }

                // Distance scroll (when sequential)
                if (mb.sequential) {
                    int distX = motorPanelX + 2 + slotW - 80 + 16;
                    int distY = sy + SLOT_H - 11;
                    if (isInside(mX, mY, distX, distY, 32, 10)) {
                        int delta = (int) Math.signum(scrollY);
                        if (hasShiftDown()) delta *= 10;
                        mb.distance = Math.max(1, Math.min(64, mb.distance + delta));
                        autoSave();
                        speedScrolled = true;
                        break;
                    }
                }
            }
            if (speedScrolled) return true;

            // Otherwise scroll the motor list
            int maxOff = Math.max(0, ControlProfile.MAX_MOTOR_BINDINGS - MOTOR_ROWS_VISIBLE);
            if (scrollY > 0 && motorScrollOffset > 0) {
                motorScrollOffset--;
                return true;
            }
            if (scrollY < 0 && motorScrollOffset < maxOff) {
                motorScrollOffset++;
                return true;
            }
            return true;
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
                autoSave();
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Frequency picker search input
        if (freqPickerAuxSlot >= 0) {
            if (keyCode == 256) { // Escape
                freqPickerAuxSlot = -1;
                return true;
            }
            if (keyCode == 259) { // Backspace
                if (!freqPickerSearch.isEmpty()) {
                    freqPickerSearch = freqPickerSearch.substring(0, freqPickerSearch.length() - 1);
                    updateFreqPickerResults();
                }
                return true;
            }
            return true; // consume all other keys while picker is open
        }

        // Distance edit mode
        if (editingDistanceSlot >= 0) {
            if (keyCode == 259) { // Backspace
                if (!distanceEditBuffer.isEmpty())
                    distanceEditBuffer = distanceEditBuffer.substring(0, distanceEditBuffer.length() - 1);
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // Enter
                commitDistanceEdit();
                return true;
            }
            if (keyCode == 256) { // Escape
                editingDistanceSlot = -1;
                distanceEditBuffer = "";
                return true;
            }
            char c = keyToChar(keyCode);
            if (c >= '0' && c <= '9' && distanceEditBuffer.length() < 2) {
                distanceEditBuffer += c;
                return true;
            }
            return true;
        }

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
        autoSave();
    }

    private void commitDistanceEdit() {
        if (editingDistanceSlot >= 0 && editingDistanceSlot < ControlProfile.MAX_MOTOR_BINDINGS) {
            try {
                int val = Integer.parseInt(distanceEditBuffer);
                profile.getMotorBinding(editingDistanceSlot).distance = Math.max(1, Math.min(64, val));
            } catch (NumberFormatException ignored) {}
        }
        editingDistanceSlot = -1;
        distanceEditBuffer = "";
        autoSave();
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
        autoSave();
    }

    private void saveProfile() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (configBlockPos != null) {
            // Block mode: save to block entity via SaveBlockProfilePayload
            PacketDistributor.sendToServer(new SaveBlockProfilePayload(configBlockPos, profile.save()));
            // Also update client-side block entity for immediate feedback
            if (mc.level != null) {
                BlockEntity be = mc.level.getBlockEntity(configBlockPos);
                if (be instanceof ContraptionRemoteBlockEntity remote) {
                    remote.setControlProfile(profile);
                }
            }
            return;
        }

        ItemStack stack = getRemoteItem(mc);
        if (!stack.isEmpty()) {
            // Update client-side for immediate feedback
            ControlProfile.saveToItem(stack, profile);
            saveLegacyAxisConfig(stack);

            // Send to server so the data actually persists
            PacketDistributor.sendToServer(new SaveControlProfilePayload(profile.save()));
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
        for (int i = 0; i < ControlProfile.MAX_MOTOR_BINDINGS; i++) {
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

    /**
     * Auto-save: sends the profile to the server immediately on every change.
     * This ensures persistence even if the screen is closed unexpectedly.
     */
    private void autoSave() {
        net.minecraft.nbt.CompoundTag savedTag = profile.save();
        int motorCount = 0;
        for (int i = 0; i < ControlProfile.MAX_MOTOR_BINDINGS; i++) {
            if (profile.getMotorBinding(i).hasTarget()) motorCount++;
        }
        LogicLink.LOGGER.info("[ControlConfigScreen] autoSave() sending {} motors to server, tag keys={}",
                motorCount, savedTag.getAllKeys());

        if (configBlockPos != null) {
            // Block mode: save to block entity
            PacketDistributor.sendToServer(new SaveBlockProfilePayload(configBlockPos, savedTag));
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                BlockEntity be = mc.level.getBlockEntity(configBlockPos);
                if (be instanceof ContraptionRemoteBlockEntity remote) {
                    remote.setControlProfile(profile);
                    LogicLink.LOGGER.info("[ControlConfigScreen] autoSave() updated client-side block entity");
                }
            }
            return;
        }

        PacketDistributor.sendToServer(new SaveControlProfilePayload(savedTag));
        // Also update client-side item for immediate feedback
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            ItemStack stack = getRemoteItem(mc);
            if (!stack.isEmpty()) {
                ControlProfile.saveToItem(stack, profile);
                LogicLink.LOGGER.info("[ControlConfigScreen] autoSave() updated client-side item");
            } else {
                LogicLink.LOGGER.warn("[ControlConfigScreen] autoSave() could not find held item!");
            }
        }
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
        if (editingDistanceSlot >= 0) commitDistanceEdit();
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

    // ==================== Frequency Picker Helpers ====================

    private void openFreqPicker(int auxSlot, int slotNum) {
        freqPickerAuxSlot = auxSlot;
        freqPickerSlotNum = slotNum;
        freqPickerSearch = "";
        freqPickerScroll = 0;
        updateFreqPickerResults();
    }

    private void updateFreqPickerResults() {
        freqPickerResults.clear();
        freqPickerScroll = 0;
        String query = freqPickerSearch.toLowerCase();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            String name = item.getDescription().getString().toLowerCase();
            String id = BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase();
            if (query.isEmpty() || name.contains(query) || id.contains(query)) {
                freqPickerResults.add(item);
            }
            if (freqPickerResults.size() >= 500) break; // cap results
        }
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (freqPickerAuxSlot >= 0) {
            if (ch >= ' ' && freqPickerSearch.length() < 30) {
                freqPickerSearch += ch;
                updateFreqPickerResults();
            }
            return true;
        }
        return super.charTyped(ch, modifiers);
    }

    // ==================== Data ====================

    private record DeviceInfo(BlockPos pos, String type, String label) {}
}
