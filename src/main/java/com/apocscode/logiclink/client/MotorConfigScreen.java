package com.apocscode.logiclink.client;

import com.apocscode.logiclink.LogicLink;
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
 * Motor/Drive configuration screen for the Logic Remote.
 * <p>
 * Lists all motors/drives connected to nearby Logic Hub,
 * allows assigning 4 axis slots with keybinds (W/S/A/D),
 * sequential movement toggle, and distance setting (1-100m).
 * </p>
 */
public class MotorConfigScreen extends Screen {

    // ==================== Colors ====================
    private static final int BG_COLOR     = 0xDD1A1A24;
    private static final int BORDER_COLOR = 0xFF4488CC;
    private static final int PANEL_BG     = 0xFF2A2A35;
    private static final int TITLE_COLOR  = 0xFF88CCFF;
    private static final int TEXT_COLOR   = 0xFFCCCCCC;
    private static final int GREEN        = 0xFF22CC55;
    private static final int RED          = 0xFFEE3333;
    private static final int YELLOW       = 0xFFFFAA00;
    private static final int CYAN         = 0xFF00CCEE;
    private static final int GRAY         = 0xFF666677;
    private static final int WHITE        = 0xFFFFFFFF;
    private static final int DARK_GRAY    = 0xFF333344;
    private static final int BTN_HOVER    = 0xFF5599CC;
    private static final int BTN_ON       = 0xFF22AA44;
    private static final int BTN_OFF      = 0xFF664444;
    private static final int SECTION_BG   = 0xFF252535;
    private static final int SELECTED     = 0xFF4488CC;

    // ==================== Layout ====================
    private int guiLeft, guiTop;
    private static final int GUI_WIDTH = 300;
    private static final int GUI_HEIGHT = 260;

    // ==================== Device Discovery ====================
    /** All discovered motors/drives from hub network. */
    private final List<DeviceInfo> availableDevices = new ArrayList<>();

    // ==================== Axis Slot Configuration ====================
    /** 4 axis slots: 0=W(forward), 1=S(backward), 2=A(left/right+), 3=D(left/right-) */
    public static final int MAX_AXIS_SLOTS = 4;
    private final AxisSlot[] axisSlots = new AxisSlot[MAX_AXIS_SLOTS];

    /** Currently selected axis slot for device assignment. -1 = none */
    private int selectedSlot = -1;

    /** Scroll offset for device list. */
    private int deviceScrollOffset = 0;

    /** Scroll offset for settings. */
    private int settingsScrollOffset = 0;

    /** Keybind labels for the 4 axes. */
    private static final String[] AXIS_LABELS = {"W (Forward)", "S (Reverse)", "A (Right)", "D (Left)"};
    public static final String[] AXIS_KEYS = {"W", "S", "A", "D"};

    /** Parent screen to return to. */
    private final Screen parentScreen;

    public MotorConfigScreen(Screen parentScreen) {
        super(Component.literal("Motor / Drive Configuration"));
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        BlockPos playerPos = mc.player.blockPosition();
        List<BlockEntity> devices = HubNetwork.getDevicesInRange(mc.level, playerPos, HubNetwork.DEFAULT_RANGE);

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
                axisSlots[i].sequential = slot.getBoolean("Sequential");
                axisSlots[i].distance = slot.getInt("Distance");
                if (axisSlots[i].distance < 1) axisSlots[i].distance = 1;
                if (axisSlots[i].distance > 100) axisSlots[i].distance = 100;
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
                slot.putBoolean("Sequential", axisSlots[i].sequential);
                slot.putInt("Distance", axisSlots[i].distance);
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
                slots[i].sequential = slot.getBoolean("Sequential");
                slots[i].distance = slot.getInt("Distance");
                if (slots[i].distance < 1) slots[i].distance = 1;
                if (slots[i].distance > 100) slots[i].distance = 100;
            }
        }
        return slots;
    }

    // ==================== Rendering ====================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        // Background
        g.fill(guiLeft - 2, guiTop - 2, guiLeft + GUI_WIDTH + 2, guiTop + GUI_HEIGHT + 2, BORDER_COLOR);
        g.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, BG_COLOR);

        // Title
        g.drawCenteredString(font, this.title, guiLeft + GUI_WIDTH / 2, guiTop + 6, TITLE_COLOR);

        // Back button
        int backX = guiLeft + 4;
        int backY = guiTop + 4;
        boolean backHover = isInside(mouseX, mouseY, backX, backY, 30, 12);
        g.fill(backX, backY, backX + 30, backY + 12, backHover ? BTN_HOVER : DARK_GRAY);
        g.drawCenteredString(font, "< Back", backX + 15, backY + 2, WHITE);

        // Save button
        int saveX = guiLeft + GUI_WIDTH - 44;
        int saveY = guiTop + 4;
        boolean saveHover = isInside(mouseX, mouseY, saveX, saveY, 40, 12);
        g.fill(saveX, saveY, saveX + 40, saveY + 12, saveHover ? GREEN : BTN_ON);
        g.drawCenteredString(font, "Save", saveX + 20, saveY + 2, WHITE);

        int y = guiTop + 22;

        // ==================== Axis Slots ====================
        g.drawString(font, "Axis Layout — Assign Motor/Drive to Keys", guiLeft + 8, y, YELLOW, false);
        y += 14;

        for (int i = 0; i < MAX_AXIS_SLOTS; i++) {
            renderAxisSlot(g, mouseX, mouseY, y, i);
            y += 52;
        }
    }

    private void renderAxisSlot(GuiGraphics g, int mouseX, int mouseY, int y, int index) {
        int sx = guiLeft + 6;
        int sw = GUI_WIDTH - 12;
        AxisSlot slot = axisSlots[index];

        // Slot background (highlight if selected)
        int bg = (selectedSlot == index) ? SELECTED : SECTION_BG;
        g.fill(sx, y, sx + sw, y + 48, bg);

        // Key label
        g.drawString(font, "[" + AXIS_KEYS[index] + "] " + AXIS_LABELS[index],
                sx + 4, y + 2, CYAN, false);

        // Target assignment
        if (slot.targetPos != null) {
            String icon = "drive".equals(slot.targetType) ? "[D]" : "[M]";
            g.drawString(font, icon + " " + slot.targetType + " @ " + slot.targetPos.toShortString(),
                    sx + 4, y + 14, GREEN, false);

            // Clear button
            int clearX = sx + sw - 42;
            int clearY = y + 12;
            boolean clearHover = isInside(mouseX, mouseY, clearX, clearY, 38, 12);
            g.fill(clearX, clearY, clearX + 38, clearY + 12, clearHover ? RED : BTN_OFF);
            g.drawCenteredString(font, "Clear", clearX + 19, clearY + 2, WHITE);

            // Sequential toggle
            int seqX = sx + 4;
            int seqY = y + 28;
            boolean seqHover = isInside(mouseX, mouseY, seqX, seqY, 70, 12);
            g.fill(seqX, seqY, seqX + 70, seqY + 12,
                    seqHover ? BTN_HOVER : (slot.sequential ? BTN_ON : DARK_GRAY));
            g.drawCenteredString(font, slot.sequential ? "Sequential" : "Continuous",
                    seqX + 35, seqY + 2, WHITE);

            // Distance scroller (only visible in sequential mode)
            if (slot.sequential) {
                int distX = sx + 80;
                int distY = y + 28;
                g.drawString(font, "Dist: " + slot.distance + "m", distX, distY + 2, TEXT_COLOR, false);

                // - button
                int minX = distX + 60;
                boolean minHover = isInside(mouseX, mouseY, minX, distY, 12, 12);
                g.fill(minX, distY, minX + 12, distY + 12, minHover ? BTN_HOVER : DARK_GRAY);
                g.drawCenteredString(font, "-", minX + 6, distY + 2, WHITE);

                // + button
                int plusX = minX + 16;
                boolean plusHover = isInside(mouseX, mouseY, plusX, distY, 12, 12);
                g.fill(plusX, distY, plusX + 12, distY + 12, plusHover ? BTN_HOVER : DARK_GRAY);
                g.drawCenteredString(font, "+", plusX + 6, distY + 2, WHITE);
            }
        } else {
            // Assign button
            int assignX = sx + 4;
            int assignY = y + 14;
            boolean assignHover = isInside(mouseX, mouseY, assignX, assignY, 80, 14);
            g.fill(assignX, assignY, assignX + 80, assignY + 14,
                    assignHover ? BTN_HOVER : DARK_GRAY);
            g.drawCenteredString(font, "Assign Device", assignX + 40, assignY + 3, WHITE);
        }

        // If this slot is selected for assignment, show device list
        if (selectedSlot == index) {
            renderDeviceList(g, mouseX, mouseY, y + 14, sx + 90, sw - 94);
        }
    }

    private void renderDeviceList(GuiGraphics g, int mouseX, int mouseY, int y, int x, int w) {
        if (availableDevices.isEmpty()) {
            g.drawString(font, "No devices found", x + 2, y + 2, GRAY, false);
            g.drawString(font, "(Place drives/motors", x + 2, y + 12, GRAY, false);
            g.drawString(font, " near a Logic Hub)", x + 2, y + 22, GRAY, false);
            return;
        }

        int maxShow = Math.min(availableDevices.size() - deviceScrollOffset, 3);
        for (int i = 0; i < maxShow; i++) {
            int idx = i + deviceScrollOffset;
            if (idx >= availableDevices.size()) break;

            DeviceInfo dev = availableDevices.get(idx);
            int dy = y + i * 14;
            boolean hover = isInside(mouseX, mouseY, x, dy, w, 12);
            int color = "drive".equals(dev.type) ? YELLOW : CYAN;

            g.fill(x, dy, x + w, dy + 12, hover ? BTN_HOVER : DARK_GRAY);
            String icon = "drive".equals(dev.type) ? "[D]" : "[M]";
            g.drawString(font, icon + " " + dev.label + " " + dev.pos.toShortString(),
                    x + 2, dy + 2, hover ? WHITE : color, false);
        }

        // Scroll indicators
        if (deviceScrollOffset > 0) {
            g.drawString(font, "^ more ^", x + 2, y - 10, GRAY, false);
        }
        if (deviceScrollOffset + maxShow < availableDevices.size()) {
            g.drawString(font, "v more v", x + 2, y + maxShow * 14, GRAY, false);
        }
    }

    // ==================== Input Handling ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int mX = (int) mouseX, mY = (int) mouseY;

        // Back button
        if (isInside(mX, mY, guiLeft + 4, guiTop + 4, 30, 12)) {
            saveConfigToItem();
            minecraft.setScreen(parentScreen);
            return true;
        }

        // Save button
        if (isInside(mX, mY, guiLeft + GUI_WIDTH - 44, guiTop + 4, 40, 12)) {
            saveConfigToItem();
            minecraft.player.displayClientMessage(
                    Component.literal("Configuration saved!").withStyle(ChatFormatting.GREEN), true);
            return true;
        }

        int y = guiTop + 36;
        for (int i = 0; i < MAX_AXIS_SLOTS; i++) {
            int sx = guiLeft + 6;
            int sw = GUI_WIDTH - 12;
            AxisSlot slot = axisSlots[i];

            if (slot.targetPos != null) {
                // Clear button
                int clearX = sx + sw - 42;
                int clearY = y + 12;
                if (isInside(mX, mY, clearX, clearY, 38, 12)) {
                    slot.targetPos = null;
                    slot.targetType = "";
                    selectedSlot = -1;
                    return true;
                }

                // Sequential toggle
                int seqX = sx + 4;
                int seqY = y + 28;
                if (isInside(mX, mY, seqX, seqY, 70, 12)) {
                    slot.sequential = !slot.sequential;
                    return true;
                }

                // Distance - button
                if (slot.sequential) {
                    int distX = sx + 80 + 60;
                    int distY = y + 28;
                    if (isInside(mX, mY, distX, distY, 12, 12)) {
                        slot.distance = Math.max(1, slot.distance - 1);
                        return true;
                    }
                    // Distance + button
                    if (isInside(mX, mY, distX + 16, distY, 12, 12)) {
                        slot.distance = Math.min(100, slot.distance + 1);
                        return true;
                    }
                }
            } else {
                // Assign button
                int assignX = sx + 4;
                int assignY = y + 14;
                if (isInside(mX, mY, assignX, assignY, 80, 14)) {
                    selectedSlot = (selectedSlot == i) ? -1 : i;
                    deviceScrollOffset = 0;
                    return true;
                }
            }

            // Device list clicks (when slot is selected)
            if (selectedSlot == i && !availableDevices.isEmpty()) {
                int listX = sx + 90;
                int listW = sw - 94;
                int listY = y + 14;
                int maxShow = Math.min(availableDevices.size() - deviceScrollOffset, 3);
                for (int j = 0; j < maxShow; j++) {
                    int idx = j + deviceScrollOffset;
                    if (idx >= availableDevices.size()) break;
                    int dy = listY + j * 14;
                    if (isInside(mX, mY, listX, dy, listW, 12)) {
                        DeviceInfo dev = availableDevices.get(idx);
                        slot.targetPos = dev.pos;
                        slot.targetType = dev.type;
                        selectedSlot = -1;
                        return true;
                    }
                }
            }

            y += 52;
        }

        // Click outside any slot = deselect
        selectedSlot = -1;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (selectedSlot >= 0 && !availableDevices.isEmpty()) {
            if (scrollY > 0 && deviceScrollOffset > 0) {
                deviceScrollOffset--;
                return true;
            }
            if (scrollY < 0 && deviceScrollOffset < availableDevices.size() - 3) {
                deviceScrollOffset++;
                return true;
            }
        }

        // Scroll distance values for axis slots
        int y = guiTop + 36;
        for (int i = 0; i < MAX_AXIS_SLOTS; i++) {
            if (axisSlots[i].targetPos != null && axisSlots[i].sequential) {
                int sx = guiLeft + 86;
                int sy = y + 28;
                if (isInside((int) mouseX, (int) mouseY, sx, sy, 100, 12)) {
                    int delta = scrollY > 0 ? 1 : -1;
                    axisSlots[i].distance = Math.max(1, Math.min(100, axisSlots[i].distance + delta));
                    return true;
                }
            }
            y += 52;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
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
        public boolean sequential = false;
        public int distance = 10; // 1-100 meters

        public boolean hasTarget() {
            return targetPos != null;
        }
    }

    private record DeviceInfo(BlockPos pos, String type, String label) {}
}
