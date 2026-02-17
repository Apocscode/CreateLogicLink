package com.apocscode.logiclink.client;

import com.apocscode.logiclink.block.LogicRemoteItem;
import com.apocscode.logiclink.network.HubNetwork;
import com.apocscode.logiclink.network.IHubDevice;

import net.minecraft.ChatFormatting;
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
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Motor/Drive assignment screen for the Logic Remote.
 * <p>
 * Workflow:
 * 1. Player places a Logic Link Hub in the world
 * 2. Hub auto-discovers nearby LogicLink motors/drives
 * 3. This screen lists all hub-connected motors/drives on the left
 * 4. Player assigns up to 4 devices to axis slots (keybind positions)
 * 5. Each slot has: direction (Forward/Reverse), speed (1-256 scrollable), keybind label
 * <p>
 * Use case: Gantry control - Motor X, Motor Y/Z, Piston Motor, Aux Redstone Link
 * <p>
 * Visual style matches Create's dark-panel controller theme.
 */
public class MotorConfigScreen extends Screen {

    // ==================== Colors (Create-style warm palette) ====================
    private static final int BG_OUTER       = 0xFF8B8279;
    private static final int BG_INNER       = 0xFFA49A8E;
    private static final int BORDER_DARK    = 0xFF5A524A;
    private static final int BORDER_LIGHT   = 0xFFCCC4B8;
    private static final int TITLE_COLOR    = 0xFF575F7A;
    private static final int LABEL_COLOR    = 0xFF575F7A;
    private static final int TEXT_COLOR     = 0xFF575F7A;
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
    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 240;
    private static final int DEVICE_PANEL_W = 120;
    private static final int SLOT_PANEL_X = DEVICE_PANEL_W + 8;
    private static final int SLOT_PANEL_W = GUI_WIDTH - SLOT_PANEL_X - 6;
    private static final int SLOT_H = 48;

    // ==================== Device Discovery ====================
    private final List<DeviceInfo> availableDevices = new ArrayList<>();
    private int deviceScrollOffset = 0;
    private static final int DEVICE_ROWS_VISIBLE = 10;

    /** Linked hub label for display. */
    private String linkedHubLabel = "";
    /** Linked hub position for display. */
    private BlockPos linkedHubPos = null;

    // ==================== Axis Slot Configuration ====================
    public static final int MAX_AXIS_SLOTS = 4;
    private final AxisSlot[] axisSlots = new AxisSlot[MAX_AXIS_SLOTS];

    /** Slot currently being assigned (-1 = none). */
    private int assigningSlot = -1;

    /** Slot with active speed field (for keyboard input). */
    private int editingSpeedSlot = -1;
    private String speedEditBuffer = "";

    /** Keybind labels for the 4 axes. */
    private static final String[] AXIS_LABELS = {"Axis 1", "Axis 2", "Axis 3", "Axis 4"};
    public static final String[] AXIS_KEYS = {"W", "S", "A", "D"};
    private static final int[] KEY_COLORS = {GREEN, RED, CYAN, YELLOW};

    /** Parent screen to return to. */
    private final Screen parentScreen;

    public MotorConfigScreen(Screen parentScreen) {
        super(Component.literal("Motor / Drive Assignment"));
        this.parentScreen = parentScreen;

        for (int i = 0; i < MAX_AXIS_SLOTS; i++) {
            axisSlots[i] = new AxisSlot();
        }
    }

    @Override
    protected void init() {
        super.init();
        guiLeft = (this.width - GUI_WIDTH) / 2;
        guiTop = (this.height - GUI_HEIGHT) / 2;

        discoverDevices();
        loadConfigFromItem();
    }

    // ==================== Device Discovery ====================

    private void discoverDevices() {
        availableDevices.clear();
        linkedHubLabel = "";
        linkedHubPos = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Find the remote item in player's hands
        ItemStack remoteStack = mc.player.getMainHandItem();
        if (!(remoteStack.getItem() instanceof LogicRemoteItem)) {
            remoteStack = mc.player.getOffhandItem();
        }
        if (remoteStack.isEmpty() || !(remoteStack.getItem() instanceof LogicRemoteItem)) return;

        // Use linked hub position as search center (not player position)
        BlockPos hubPos = LogicRemoteItem.getLinkedHub(remoteStack);
        if (hubPos == null) return; // Not linked to a hub

        linkedHubPos = hubPos;
        linkedHubLabel = LogicRemoteItem.getLinkedHubLabel(remoteStack);

        // Try to read hub range from the actual hub block entity if loaded
        int range = HubNetwork.DEFAULT_RANGE;
        BlockEntity hubBe = mc.level.getBlockEntity(hubPos);
        if (hubBe instanceof com.apocscode.logiclink.block.LogicLinkBlockEntity hub) {
            range = hub.getHubRange();
        }

        List<BlockEntity> devices = HubNetwork.getDevicesInRange(mc.level, hubPos, range);

        for (BlockEntity be : devices) {
            if (be instanceof IHubDevice device) {
                String type = device.getDeviceType();
                if ("drive".equals(type) || "creative_motor".equals(type)) {
                    String label = device.getHubLabel().isEmpty() ? type : device.getHubLabel();
                    availableDevices.add(new DeviceInfo(
                            device.getDevicePos(), type, label));
                }
            }
        }
    }

    // ==================== Config Persistence ====================

    private void loadConfigFromItem() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof LogicRemoteItem)) {
            stack = mc.player.getOffhandItem();
        }
        if (stack.isEmpty()) return;

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains("AxisConfig")) return;

        ListTag axisList = tag.getList("AxisConfig", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(axisList.size(), MAX_AXIS_SLOTS); i++) {
            CompoundTag slot = axisList.getCompound(i);
            if (slot.contains("X")) {
                axisSlots[i].targetPos = new BlockPos(slot.getInt("X"), slot.getInt("Y"), slot.getInt("Z"));
                axisSlots[i].targetType = slot.getString("Type");
                axisSlots[i].reversed = slot.getBoolean("Reversed");
                axisSlots[i].speed = slot.getInt("Speed");
                if (axisSlots[i].speed < 1) axisSlots[i].speed = 1;
                if (axisSlots[i].speed > 256) axisSlots[i].speed = 256;
                // Legacy compat: migrate old Sequential/Distance format
                if (slot.contains("Distance") && !slot.contains("Speed")) {
                    axisSlots[i].speed = Math.max(1, Math.min(256, slot.getInt("Distance") * 4));
                }
                if (slot.contains("Sequential")) {
                    axisSlots[i].sequential = slot.getBoolean("Sequential");
                }
            }
        }
    }

    private void saveConfigToItem() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof LogicRemoteItem)) {
            stack = mc.player.getOffhandItem();
        }
        if (stack.isEmpty()) return;

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        ListTag axisList = new ListTag();
        for (int i = 0; i < MAX_AXIS_SLOTS; i++) {
            CompoundTag slot = new CompoundTag();
            if (axisSlots[i].targetPos != null) {
                slot.putInt("X", axisSlots[i].targetPos.getX());
                slot.putInt("Y", axisSlots[i].targetPos.getY());
                slot.putInt("Z", axisSlots[i].targetPos.getZ());
                slot.putString("Type", axisSlots[i].targetType);
                slot.putBoolean("Reversed", axisSlots[i].reversed);
                slot.putInt("Speed", axisSlots[i].speed);
                slot.putBoolean("Sequential", axisSlots[i].sequential);
            }
            axisList.add(slot);
        }
        tag.put("AxisConfig", axisList);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** Get axis config for the remote overlay to use. */
    public static AxisSlot[] getAxisConfigFromItem(ItemStack stack) {
        AxisSlot[] slots = new AxisSlot[MAX_AXIS_SLOTS];
        for (int i = 0; i < MAX_AXIS_SLOTS; i++) {
            slots[i] = new AxisSlot();
        }

        if (stack.isEmpty()) return slots;

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains("AxisConfig")) return slots;

        ListTag axisList = tag.getList("AxisConfig", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(axisList.size(), MAX_AXIS_SLOTS); i++) {
            CompoundTag slot = axisList.getCompound(i);
            if (slot.contains("X")) {
                slots[i].targetPos = new BlockPos(slot.getInt("X"), slot.getInt("Y"), slot.getInt("Z"));
                slots[i].targetType = slot.getString("Type");
                slots[i].reversed = slot.getBoolean("Reversed");
                slots[i].speed = slot.getInt("Speed");
                if (slots[i].speed < 1) slots[i].speed = 1;
                if (slots[i].speed > 256) slots[i].speed = 256;
                // Legacy compat
                if (slot.contains("Distance") && !slot.contains("Speed")) {
                    slots[i].speed = Math.max(1, Math.min(256, slot.getInt("Distance") * 4));
                }
                if (slot.contains("Sequential")) {
                    slots[i].sequential = slot.getBoolean("Sequential");
                }
            }
        }
        return slots;
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
        g.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + 18, BORDER_DARK);
        g.drawCenteredString(font, this.title, guiLeft + GUI_WIDTH / 2, guiTop + 5, TITLE_COLOR);

        // Back button (top-left)
        renderButton(g, mouseX, mouseY, guiLeft + 3, guiTop + 2, 32, 13, "\u2190 Back", BTN_IDLE, BTN_HOVER, WHITE);

        // Save button (top-right)
        renderButton(g, mouseX, mouseY, guiLeft + GUI_WIDTH - 35, guiTop + 2, 32, 13, "Save", BTN_ACTIVE, GREEN, WHITE);

        // ==================== Left Panel: Hub Devices ====================
        int panelX = guiLeft + 4;
        int panelY = guiTop + 22;
        int panelW = DEVICE_PANEL_W;
        int panelH = GUI_HEIGHT - 26;

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, BG_INNER);
        g.fill(panelX, panelY, panelX + panelW, panelY + 12, BORDER_DARK);

        // Hub header: show label or position if linked
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
            g.drawString(font, "Logic Link Hub", panelX + 4, panelY + 30, TEXT_DIM, false);
            g.drawString(font, "to link remote", panelX + 4, panelY + 40, TEXT_DIM, false);
        } else if (availableDevices.isEmpty()) {
            g.drawString(font, "No devices", panelX + 4, panelY + 18, TEXT_DIM, false);
            g.drawString(font, "Place motors/", panelX + 4, panelY + 30, TEXT_DIM, false);
            g.drawString(font, "drives near", panelX + 4, panelY + 40, TEXT_DIM, false);
            g.drawString(font, "the linked hub", panelX + 4, panelY + 50, TEXT_DIM, false);
        } else {
            int listY = panelY + 14;
            int maxShow = Math.min(availableDevices.size() - deviceScrollOffset, DEVICE_ROWS_VISIBLE);
            for (int i = 0; i < maxShow; i++) {
                int idx = i + deviceScrollOffset;
                if (idx >= availableDevices.size()) break;

                DeviceInfo dev = availableDevices.get(idx);
                int dy = listY + i * 18;
                boolean hover = isInside(mouseX, mouseY, panelX + 2, dy, panelW - 4, 16);
                boolean alreadyAssigned = isDeviceAssigned(dev.pos);

                int bg = hover ? DEVICE_HOVER : DEVICE_BG;
                if (alreadyAssigned) bg = SLOT_ASSIGNED;
                g.fill(panelX + 2, dy, panelX + panelW - 2, dy + 16, bg);

                String icon = "drive".equals(dev.type) ? "\u25B6" : "\u2699";
                int col = alreadyAssigned ? TEXT_DIM : ("drive".equals(dev.type) ? YELLOW : CYAN);
                g.drawString(font, icon + " " + dev.label, panelX + 5, dy + 1, col, false);

                // Position text below label
                String posStr = dev.pos.getX() + "," + dev.pos.getY() + "," + dev.pos.getZ();
                g.drawString(font, posStr, panelX + 5, dy + 8, TEXT_DIM, false);
            }

            // Scroll indicators
            if (deviceScrollOffset > 0) {
                g.drawCenteredString(font, "\u25B2", panelX + panelW / 2, panelY + 12, TEXT_DIM);
            }
            if (deviceScrollOffset + maxShow < availableDevices.size()) {
                g.drawCenteredString(font, "\u25BC", panelX + panelW / 2,
                        panelY + panelH - 10, TEXT_DIM);
            }
        }

        // Refresh button at bottom of device panel
        int refX = panelX + 2;
        int refY = panelY + panelH - 14;
        renderButton(g, mouseX, mouseY, refX, refY, panelW - 4, 12, "\u21BB Refresh", BTN_IDLE, BTN_HOVER, LABEL_COLOR);

        // ==================== Right Panel: 4 Axis Slots ====================
        int slotPanelX = guiLeft + SLOT_PANEL_X;
        int slotPanelY = guiTop + 22;

        for (int i = 0; i < MAX_AXIS_SLOTS; i++) {
            int sy = slotPanelY + i * (SLOT_H + 2);
            renderAxisSlot(g, mouseX, mouseY, slotPanelX, sy, i);
        }

        // Help text at bottom
        if (assigningSlot >= 0) {
            g.drawCenteredString(font, "Click a device from the list to assign to [" + AXIS_KEYS[assigningSlot] + "]",
                    guiLeft + GUI_WIDTH / 2, guiTop + GUI_HEIGHT - 12, YELLOW);
        }
    }

    private void renderAxisSlot(GuiGraphics g, int mouseX, int mouseY, int x, int y, int index) {
        AxisSlot slot = axisSlots[index];

        // Slot background
        int bg = (assigningSlot == index) ? SLOT_SELECTED : (slot.hasTarget() ? SLOT_ASSIGNED : SLOT_BG);
        g.fill(x, y, x + SLOT_PANEL_W, y + SLOT_H, bg);
        // Left accent bar (keybind color)
        g.fill(x, y, x + 3, y + SLOT_H, KEY_COLORS[index]);

        // Header: keybind + label
        String header = "[" + AXIS_KEYS[index] + "] " + AXIS_LABELS[index];
        g.drawString(font, header, x + 6, y + 2, KEY_COLORS[index], false);

        if (slot.hasTarget()) {
            // Assigned device info
            String icon = "drive".equals(slot.targetType) ? "\u25B6" : "\u2699";
            String typeName = "drive".equals(slot.targetType) ? "Drive" : "Motor";
            g.drawString(font, icon + " " + typeName + " @ " + slot.targetPos.toShortString(),
                    x + 6, y + 13, GREEN, false);

            // Direction toggle: Forward / Reverse
            int dirX = x + 6;
            int dirY = y + 25;
            int dirW = 52;
            boolean dirHover = isInside(mouseX, mouseY, dirX, dirY, dirW, 12);
            int dirBg = dirHover ? BTN_HOVER : (slot.reversed ? RED : BTN_ACTIVE);
            g.fill(dirX, dirY, dirX + dirW, dirY + 12, dirBg);
            g.drawCenteredString(font, slot.reversed ? "Reverse" : "Forward", dirX + dirW / 2, dirY + 2, WHITE);

            // Speed field (Create-style scroll input)
            int speedX = dirX + dirW + 4;
            int speedY = y + 25;
            int speedW = 64;
            boolean speedHover = isInside(mouseX, mouseY, speedX, speedY, speedW, 12);
            int speedBg = (editingSpeedSlot == index) ? SLOT_SELECTED : (speedHover ? FIELD_BORDER : FIELD_BG);
            g.fill(speedX - 1, speedY - 1, speedX + speedW + 1, speedY + 13, FIELD_BORDER);
            g.fill(speedX, speedY, speedX + speedW, speedY + 12, speedBg);

            String speedStr;
            if (editingSpeedSlot == index) {
                speedStr = speedEditBuffer + "_";
            } else {
                speedStr = slot.speed + " RPM";
            }
            g.drawString(font, speedStr, speedX + 3, speedY + 2, WHITE, false);

            // Scroll hint
            if (speedHover && editingSpeedSlot != index) {
                g.drawString(font, "\u2191\u2193", speedX + speedW + 2, speedY + 2, TEXT_DIM, false);
            }

            // Clear (X) button
            int clrX = x + SLOT_PANEL_W - 14;
            int clrY = y + 2;
            boolean clrHover = isInside(mouseX, mouseY, clrX, clrY, 12, 12);
            g.fill(clrX, clrY, clrX + 12, clrY + 12, clrHover ? RED : BTN_IDLE);
            g.drawCenteredString(font, "X", clrX + 6, clrY + 2, WHITE);

            // Reassign button
            int reassX = x + SLOT_PANEL_W - 50;
            int reassY = y + 35;
            renderButton(g, mouseX, mouseY, reassX, reassY, 46, 11, "Reassign", BTN_IDLE, BTN_HOVER, LABEL_COLOR);

        } else {
            // Empty slot
            if (assigningSlot == index) {
                g.drawString(font, "Select a device from", x + 6, y + 15, YELLOW, false);
                g.drawString(font, "the list on the left", x + 6, y + 26, YELLOW, false);

                // Cancel button
                int canX = x + SLOT_PANEL_W - 48;
                int canY = y + 35;
                renderButton(g, mouseX, mouseY, canX, canY, 44, 11, "Cancel", BTN_IDLE, RED, WHITE);
            } else {
                g.drawString(font, "Empty \u2014 click to assign", x + 6, y + 18, TEXT_DIM, false);

                // Assign button
                int assX = x + SLOT_PANEL_W - 48;
                int assY = y + 35;
                renderButton(g, mouseX, mouseY, assX, assY, 44, 11, "Assign", BTN_IDLE, BTN_HOVER, WHITE);
            }
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

        // Clicking anywhere clears speed edit mode
        if (editingSpeedSlot >= 0) {
            commitSpeedEdit();
        }

        // Back button
        if (isInside(mX, mY, guiLeft + 3, guiTop + 2, 32, 13)) {
            saveConfigToItem();
            minecraft.setScreen(parentScreen);
            return true;
        }

        // Save button
        if (isInside(mX, mY, guiLeft + GUI_WIDTH - 35, guiTop + 2, 32, 13)) {
            saveConfigToItem();
            minecraft.player.displayClientMessage(
                    Component.literal("Configuration saved!").withStyle(ChatFormatting.GREEN), true);
            return true;
        }

        // Refresh button
        int panelX = guiLeft + 4;
        int panelY = guiTop + 22;
        int panelH = GUI_HEIGHT - 26;
        int panelW = DEVICE_PANEL_W;
        if (isInside(mX, mY, panelX + 2, panelY + panelH - 14, panelW - 4, 12)) {
            discoverDevices();
            return true;
        }

        // Device list clicks (when assigning)
        if (assigningSlot >= 0 && !availableDevices.isEmpty()) {
            int listY = panelY + 14;
            int maxShow = Math.min(availableDevices.size() - deviceScrollOffset, DEVICE_ROWS_VISIBLE);
            for (int i = 0; i < maxShow; i++) {
                int idx = i + deviceScrollOffset;
                if (idx >= availableDevices.size()) break;
                int dy = listY + i * 18;
                if (isInside(mX, mY, panelX + 2, dy, panelW - 4, 16)) {
                    DeviceInfo dev = availableDevices.get(idx);
                    AxisSlot slot = axisSlots[assigningSlot];
                    slot.targetPos = dev.pos;
                    slot.targetType = dev.type;
                    slot.speed = "drive".equals(dev.type) ? 8 : 64; // sensible defaults
                    slot.reversed = false;
                    assigningSlot = -1;
                    return true;
                }
            }
        }

        // Axis slot interactions
        int slotPanelX = guiLeft + SLOT_PANEL_X;
        int slotPanelY = guiTop + 22;
        for (int i = 0; i < MAX_AXIS_SLOTS; i++) {
            int sy = slotPanelY + i * (SLOT_H + 2);
            AxisSlot slot = axisSlots[i];

            if (slot.hasTarget()) {
                // Direction toggle
                int dirX = slotPanelX + 6;
                int dirY = sy + 25;
                if (isInside(mX, mY, dirX, dirY, 52, 12)) {
                    slot.reversed = !slot.reversed;
                    return true;
                }

                // Speed field click (enter edit mode)
                int speedX = dirX + 56;
                int speedY = sy + 25;
                if (isInside(mX, mY, speedX, speedY, 64, 12)) {
                    editingSpeedSlot = i;
                    speedEditBuffer = String.valueOf(slot.speed);
                    return true;
                }

                // Clear button
                int clrX = slotPanelX + SLOT_PANEL_W - 14;
                int clrY = sy + 2;
                if (isInside(mX, mY, clrX, clrY, 12, 12)) {
                    slot.targetPos = null;
                    slot.targetType = "";
                    if (assigningSlot == i) assigningSlot = -1;
                    return true;
                }

                // Reassign button
                int reassX = slotPanelX + SLOT_PANEL_W - 50;
                int reassY = sy + 35;
                if (isInside(mX, mY, reassX, reassY, 46, 11)) {
                    assigningSlot = i;
                    deviceScrollOffset = 0;
                    return true;
                }
            } else {
                if (assigningSlot == i) {
                    // Cancel button
                    int canX = slotPanelX + SLOT_PANEL_W - 48;
                    int canY = sy + 35;
                    if (isInside(mX, mY, canX, canY, 44, 11)) {
                        assigningSlot = -1;
                        return true;
                    }
                } else {
                    // Assign button
                    int assX = slotPanelX + SLOT_PANEL_W - 48;
                    int assY = sy + 35;
                    if (isInside(mX, mY, assX, assY, 44, 11)) {
                        assigningSlot = i;
                        deviceScrollOffset = 0;
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mX = (int) mouseX, mY = (int) mouseY;

        // Scroll device list
        int panelX = guiLeft + 4;
        int panelY = guiTop + 22;
        int panelH = GUI_HEIGHT - 26;
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

        // Scroll speed values on axis slots
        int slotPanelX = guiLeft + SLOT_PANEL_X;
        int slotPanelY = guiTop + 22;
        for (int i = 0; i < MAX_AXIS_SLOTS; i++) {
            int sy = slotPanelY + i * (SLOT_H + 2);
            AxisSlot slot = axisSlots[i];
            if (!slot.hasTarget()) continue;

            int speedX = slotPanelX + 62;
            int speedY = sy + 25;
            if (isInside(mX, mY, speedX, speedY, 64, 12)) {
                int delta = (int) Math.signum(scrollY);
                // Shift = x10 for faster adjustment
                if (hasShiftDown()) delta *= 10;
                slot.speed = Math.max(1, Math.min(256, slot.speed + delta));
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Speed edit mode: accept digit keys
        if (editingSpeedSlot >= 0) {
            if (keyCode == 259) { // Backspace
                if (!speedEditBuffer.isEmpty()) {
                    speedEditBuffer = speedEditBuffer.substring(0, speedEditBuffer.length() - 1);
                }
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // Enter / Numpad Enter
                commitSpeedEdit();
                return true;
            }
            if (keyCode == 256) { // Escape
                editingSpeedSlot = -1;
                speedEditBuffer = "";
                return true;
            }
            // Digit keys (0-9)
            char c = 0;
            if (keyCode >= 48 && keyCode <= 57) c = (char) keyCode;
            if (keyCode >= 320 && keyCode <= 329) c = (char) (keyCode - 272); // numpad
            if (c >= '0' && c <= '9') {
                if (speedEditBuffer.length() < 3) {
                    speedEditBuffer += c;
                }
                return true;
            }
            return true; // consume all keys while editing
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void commitSpeedEdit() {
        if (editingSpeedSlot >= 0 && editingSpeedSlot < MAX_AXIS_SLOTS) {
            try {
                int val = Integer.parseInt(speedEditBuffer);
                axisSlots[editingSpeedSlot].speed = Math.max(1, Math.min(256, val));
            } catch (NumberFormatException ignored) {
                // Keep old value
            }
        }
        editingSpeedSlot = -1;
        speedEditBuffer = "";
    }

    private boolean isDeviceAssigned(BlockPos pos) {
        for (AxisSlot slot : axisSlots) {
            if (slot.targetPos != null && slot.targetPos.equals(pos)) return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        if (editingSpeedSlot >= 0) commitSpeedEdit();
        saveConfigToItem();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean isInside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ==================== Data Classes ====================

    public static class AxisSlot {
        public BlockPos targetPos = null;
        public String targetType = "";
        public boolean reversed = false;
        public int speed = 64; // 1-256 RPM
        /** Legacy compat fields. */
        public boolean sequential = false;
        public int distance = 10;

        public boolean hasTarget() {
            return targetPos != null;
        }
    }

    private record DeviceInfo(BlockPos pos, String type, String label) {}
}
