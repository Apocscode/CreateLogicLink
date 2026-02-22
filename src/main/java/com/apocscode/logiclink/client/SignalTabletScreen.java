package com.apocscode.logiclink.client;

import com.apocscode.logiclink.block.SignalTabletItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Client-side GUI for the Signal Diagnostic Tablet.
 * Shows signal diagnostics sorted by distance to player.
 * Each row has a clickable highlight button that toggles
 * in-world ghost box rendering at that location via SignalHighlightManager.
 */
public class SignalTabletScreen extends Screen {

    // ==================== Colors ====================
    private static final int BG_COLOR       = 0xDD202028;
    private static final int BORDER_COLOR   = 0xFF4488CC;
    private static final int TITLE_COLOR    = 0xFF88CCFF;
    private static final int TEXT_COLOR     = 0xFFCCCCCC;
    private static final int HEADER_COLOR   = 0xFF88CCFF;
    private static final int GREEN          = 0xFF1AEA5F;
    private static final int RED            = 0xFFFF4242;
    private static final int YELLOW         = 0xFFFF9900;
    private static final int CYAN           = 0xFF00DDEF;
    private static final int GRAY           = 0xFF8B8B8B;
    private static final int WHITE          = 0xFFFFFFFF;
    private static final int PANEL_BG       = 0xFF2A2A35;
    private static final int SEPARATOR      = 0xFF454555;
    private static final int ALERT_BG       = 0xFF4A2020;
    private static final int BTN_ON         = 0xFF22AA44;
    private static final int BTN_OFF        = 0xFF555566;
    private static final int BTN_HOVER      = 0xFF3388CC;

    private final CompoundTag scanData;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int guiLeft, guiTop, guiWidth, guiHeight;

    /** Sorted diagnostic entries with pre-computed distance and suggestion coords. */
    private final List<DiagEntry> sortedDiags = new ArrayList<>();

    /** Pre-processed diagnostic entry for display. */
    private record DiagEntry(CompoundTag diag, double distance, List<SugCoord> sugCoords) {}
    private record SugCoord(int x, int y, int z, int type, String label, float dirX, float dirZ) {}

    // Layout constants for the list — computed in init
    private int entryH = 48;
    private int listTop, listH, visibleEntries;

    public SignalTabletScreen(CompoundTag scanData) {
        super(Component.literal("Signal Diagnostic Tablet"));
        this.scanData = scanData;
    }

    public static void openFromItem(ItemStack stack) {
        CompoundTag data = SignalTabletItem.getScanData(stack);
        if (data == null) data = new CompoundTag();
        Minecraft.getInstance().setScreen(new SignalTabletScreen(data));
    }

    @Override
    protected void init() {
        super.init();
        guiWidth = Math.min(440, (int)(this.width * 0.88f));
        guiHeight = Math.min(340, (int)(this.height * 0.88f));
        guiLeft = (this.width - guiWidth) / 2;
        guiTop = (this.height - guiHeight) / 2;

        // Clear old highlights when opening fresh
        SignalHighlightManager.clearAll();

        // Pre-process diagnostics: compute distance and sort
        sortedDiags.clear();
        if (scanData != null && scanData.contains("Diagnostics")) {
            double px = scanData.getDouble("playerX");
            double py = scanData.getDouble("playerY");
            double pz = scanData.getDouble("playerZ");

            ListTag diagnostics = scanData.getList("Diagnostics", 10);
            for (int i = 0; i < diagnostics.size(); i++) {
                CompoundTag diag = diagnostics.getCompound(i);
                float dx = diag.contains("x") ? diag.getFloat("x") : 0;
                float dy = diag.contains("y") ? diag.getFloat("y") : 0;
                float dz = diag.contains("z") ? diag.getFloat("z") : 0;
                double dist = Math.sqrt((dx - px) * (dx - px) + (dy - py) * (dy - py) + (dz - pz) * (dz - pz));

                // Collect suggestion coordinates for highlight toggles
                List<SugCoord> coords = new ArrayList<>();
                String type = diag.getString("type");
                if (diag.contains("suggestions")) {
                    ListTag sug = diag.getList("suggestions", 10);
                    for (int s = 0; s < sug.size(); s++) {
                        CompoundTag sp = sug.getCompound(s);
                        int sx = sp.getInt("sx"), sy = sp.getInt("sy"), sz = sp.getInt("sz");
                        String sigType = sp.contains("signalType") ? sp.getString("signalType") : "signal";
                        float sdx = sp.contains("sdx") ? sp.getFloat("sdx") : 0;
                        float sdz = sp.contains("sdz") ? sp.getFloat("sdz") : 0;
                        int mType = switch (sigType) {
                            case "chain" -> SignalHighlightManager.TYPE_CHAIN;
                            case "conflict" -> SignalHighlightManager.TYPE_CONFLICT;
                            default -> SignalHighlightManager.TYPE_SIGNAL;
                        };
                        String label = switch (sigType) {
                            case "chain" -> "\u26D3";
                            case "conflict" -> "\u26A0";
                            default -> "\u2691";
                        };
                        coords.add(new SugCoord(sx, sy, sz, mType, label, sdx, sdz));
                    }
                }
                // For SIGNAL_CONFLICT, if no suggestions, use the diagnostic location itself
                if (coords.isEmpty() && type.equals("SIGNAL_CONFLICT") && diag.contains("x")) {
                    coords.add(new SugCoord((int) dx, (int) dy, (int) dz, SignalHighlightManager.TYPE_CONFLICT, "\u26A0", 0, 0));
                }

                sortedDiags.add(new DiagEntry(diag, dist, coords));
            }

            // Sort by distance (closest first)
            sortedDiags.sort(Comparator.comparingDouble(DiagEntry::distance));
        }
    }

    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // No-op: skip blur shader and menu background entirely
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // Dim background
        gfx.fill(0, 0, this.width, this.height, 0x88000000);

        int x = guiLeft, y = guiTop, w = guiWidth, h = guiHeight;
        int margin = 6;

        // Border + background
        gfx.fill(x - 1, y - 1, x + w + 1, y + h + 1, BORDER_COLOR);
        gfx.fill(x, y, x + w, y + h, BG_COLOR);

        // Title bar
        gfx.fill(x, y, x + w, y + 16, 0xFF1A1A2A);
        gfx.drawCenteredString(font, "\u2691 SIGNAL DIAGNOSTIC TABLET", x + w / 2, y + 4, TITLE_COLOR);

        // ==================== Color Legend ====================
        int legendY = y + 18;
        gfx.fill(x + margin, legendY, x + w - margin, legendY + 12, 0xFF1A1A2A);

        int lx = x + margin + 6;
        // Green box = Place Signal
        gfx.fill(lx, legendY + 2, lx + 8, legendY + 10, 0xFF1AEA5F);
        gfx.drawString(font, "Signal", lx + 10, legendY + 2, GRAY, false);
        lx += 46;
        // Cyan box = Chain Signal
        gfx.fill(lx, legendY + 2, lx + 8, legendY + 10, 0xFF00DDEF);
        gfx.drawString(font, "Chain", lx + 10, legendY + 2, GRAY, false);
        lx += 42;
        // Red box = Remove / Conflict
        gfx.fill(lx, legendY + 2, lx + 8, legendY + 10, 0xFFFF4242);
        gfx.drawString(font, "Conflict", lx + 10, legendY + 2, GRAY, false);
        lx += 56;
        // Yellow box = Warning
        gfx.fill(lx, legendY + 2, lx + 8, legendY + 10, 0xFFFF9900);
        gfx.drawString(font, "Warning", lx + 10, legendY + 2, GRAY, false);

        int cy = y + 32;
        int col1 = x + margin + 4;
        int col2 = x + w / 2;

        if (scanData == null || scanData.isEmpty() || !scanData.contains("Diagnostics")) {
            gfx.drawCenteredString(font, "No scan data. Right-click to scan.", x + w / 2, y + h / 2, GRAY);
            gfx.drawCenteredString(font, "Press ESC to close", x + w / 2, y + h / 2 + 14, GRAY);
            return;
        }

        // ==================== Network Summary ====================
        gfx.fill(x + margin, cy, x + w - margin, cy + 48, PANEL_BG);
        gfx.drawString(font, "NETWORK SUMMARY", col1, cy + 2, HEADER_COLOR, false);
        cy += 12;

        int trains = scanData.getInt("trainCount");
        int stations = scanData.getInt("stationCount");
        int signals = scanData.getInt("signalCount");
        int nodes = scanData.getInt("nodeCount");
        int edges = scanData.getInt("edgeCount");
        int maxCars = scanData.getInt("MaxCarriages");
        float maxLen = scanData.getFloat("MaxTrainLength");

        gfx.drawString(font, "Trains: " + trains, col1, cy, TEXT_COLOR, false);
        gfx.drawString(font, "Stations: " + stations, col2, cy, TEXT_COLOR, false);
        cy += 11;
        gfx.drawString(font, "Signals: " + signals, col1, cy, TEXT_COLOR, false);
        gfx.drawString(font, "Nodes: " + nodes + "  Edges: " + edges, col2, cy, TEXT_COLOR, false);
        cy += 11;
        if (maxCars > 0) {
            gfx.drawString(font, "Max Train: " + maxCars + " cars (" + (int) maxLen + " blocks)", col1, cy, TEXT_COLOR, false);
        }
        cy += 14;

        // ==================== Issue Summary ====================
        int issueCount = scanData.getInt("issueCount");
        int jCount = scanData.getInt("junctionCount");
        int npCount = scanData.getInt("noPathCount");
        int cCount = scanData.getInt("conflictCount");

        gfx.fill(x + margin, cy, x + w - margin, cy + 1, SEPARATOR);
        cy += 3;

        if (issueCount == 0) {
            gfx.drawString(font, "\u2713 No signal issues detected", col1, cy, GREEN, false);
            cy += 14;
        } else {
            gfx.drawString(font, issueCount + " ISSUE(S) FOUND", col1, cy, RED, false);

            // Highlight count
            int hlCount = SignalHighlightManager.count();
            if (hlCount > 0) {
                String hlStr = hlCount + " highlighted";
                int hlW = font.width(hlStr);
                gfx.drawString(font, hlStr, x + w - margin - hlW - 4, cy, CYAN, false);
            }
            cy += 12;

            if (jCount > 0) { gfx.drawString(font, "  \u2022 " + jCount + " junction(s) missing signals", col1, cy, YELLOW, false); cy += 10; }
            if (npCount > 0) { gfx.drawString(font, "  \u2022 " + npCount + " train(s) stuck (no path)", col1, cy, RED, false); cy += 10; }
            if (cCount > 0) { gfx.drawString(font, "  \u2022 " + cCount + " signal conflict(s)", col1, cy, RED, false); cy += 10; }
            cy += 4;
        }

        gfx.fill(x + margin, cy, x + w - margin, cy + 1, SEPARATOR);
        cy += 3;

        // ==================== Diagnostic List ====================
        gfx.drawString(font, "DIAGNOSTIC DETAILS (closest first)", col1, cy, HEADER_COLOR, false);
        cy += 12;

        if (sortedDiags.isEmpty()) {
            gfx.drawString(font, "All clear!", col1, cy, GREEN, false);
            return;
        }

        listTop = cy;
        listH = y + h - cy - 14;
        entryH = 48;
        visibleEntries = Math.max(1, listH / entryH);
        maxScroll = Math.max(0, sortedDiags.size() - visibleEntries);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        gfx.enableScissor(x + margin, listTop, x + w - margin, listTop + listH);

        for (int i = scrollOffset; i < sortedDiags.size() && i < scrollOffset + visibleEntries; i++) {
            DiagEntry entry = sortedDiags.get(i);
            CompoundTag diag = entry.diag;
            int ey = listTop + (i - scrollOffset) * entryH;

            String severity = diag.getString("severity");
            String type = diag.getString("type");
            String desc = diag.getString("desc");

            int sevColor = switch (severity) { case "CRIT" -> RED; case "WARN" -> YELLOW; default -> HEADER_COLOR; };
            int bgColor = switch (severity) { case "CRIT" -> ALERT_BG; case "WARN" -> 0xFF2A2A11; default -> PANEL_BG; };

            gfx.fill(x + margin, ey, x + w - margin, ey + entryH - 2, bgColor);

            // Type icon + severity
            String icon = switch (type) {
                case "JUNCTION_UNSIGNALED" -> "\u26A0";
                case "NO_PATH" -> "\u2716";
                case "SIGNAL_CONFLICT" -> "\u2B55";
                case "TRAIN_PAUSED" -> "\u23F8";
                case "SCHEDULE_DONE" -> "\u2713";
                default -> "?";
            };
            gfx.drawString(font, icon + " [" + severity + "] " + type, col1, ey + 2, sevColor, false);

            // Distance badge
            String distStr = (int) entry.distance + "m";
            int distW = font.width(distStr);
            gfx.drawString(font, distStr, x + w - margin - distW - 4, ey + 2, GRAY, false);

            // Location
            if (diag.contains("x")) {
                String loc = "@ " + (int) diag.getFloat("x") + ", " + (int) diag.getFloat("y") + ", " + (int) diag.getFloat("z");
                gfx.drawString(font, loc, col1, ey + 13, GRAY, false);
            }

            // Description (truncated)
            int maxDescW = w - margin * 2 - 12;
            String descTrunc = desc;
            if (font.width(descTrunc) > maxDescW) {
                while (font.width(descTrunc + "..") > maxDescW && descTrunc.length() > 1)
                    descTrunc = descTrunc.substring(0, descTrunc.length() - 1);
                descTrunc += "..";
            }
            gfx.drawString(font, descTrunc, col1, ey + 23, TEXT_COLOR, false);

            // ========== Highlight buttons per suggestion ==========
            int btnX = col1;
            int btnY = ey + 34;
            for (int s = 0; s < entry.sugCoords.size(); s++) {
                SugCoord sc = entry.sugCoords.get(s);
                boolean active = SignalHighlightManager.isActive(sc.x, sc.y, sc.z);
                int btnW = font.width(sc.label + " " + sc.x + "," + sc.y + "," + sc.z + " ") + 12;
                int btnH = 11;

                // Button background
                boolean hovered = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
                int btnColor = active ? BTN_ON : (hovered ? BTN_HOVER : BTN_OFF);
                gfx.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnColor);
                gfx.fill(btnX, btnY, btnX + btnW, btnY + 1, 0x44FFFFFF);  // top highlight

                // Eye icon + coords text
                String eyeIcon = active ? "\u25C9" : "\u25CE";
                String btnText = eyeIcon + " " + sc.label + " " + sc.x + "," + sc.y + "," + sc.z;
                gfx.drawString(font, btnText, btnX + 3, btnY + 1, WHITE, false);

                btnX += btnW + 4;
                if (btnX + 60 > x + w - margin) break; // prevent overflow
            }

            // Train name for train-related diagnostics
            if ((type.equals("NO_PATH") || type.equals("TRAIN_PAUSED") || type.equals("SCHEDULE_DONE"))
                    && diag.contains("trainName") && entry.sugCoords.isEmpty()) {
                gfx.drawString(font, "Train: " + diag.getString("trainName"), col1, ey + 34, YELLOW, false);
            }

            // Detail/suggestion text
            if (diag.contains("detail")) {
                String detailText = "\u2192 " + diag.getString("detail");
                int maxDetailW = w - margin * 2 - 12;
                if (font.width(detailText) > maxDetailW) {
                    while (font.width(detailText + "..") > maxDetailW && detailText.length() > 4)
                        detailText = detailText.substring(0, detailText.length() - 1);
                    detailText += "..";
                }
                int detailY = entry.sugCoords.isEmpty() ? ey + 34 : ey + 46;
                if (diag.contains("trainName") && entry.sugCoords.isEmpty()) detailY = ey + 45;
                gfx.drawString(font, detailText, col1, detailY, 0xFF8888AA, false);
            }

            gfx.fill(x + margin, ey + entryH - 2, x + w - margin, ey + entryH - 1, SEPARATOR);
        }

        gfx.disableScissor();

        // Scroll bar
        if (maxScroll > 0) {
            int barH = Math.max(10, listH * visibleEntries / sortedDiags.size());
            int barY = listTop + (int) ((listH - barH) * (float) scrollOffset / maxScroll);
            gfx.fill(x + w - margin - 3, barY, x + w - margin, barY + barH, 0xFF555577);
        }

        // Footer
        int footerY = y + h - 12;
        gfx.drawString(font, "Click buttons to highlight in-world  |  Rescan: right-click tablet  |  ESC to close", x + margin, footerY, GRAY, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !sortedDiags.isEmpty()) {
            int x = guiLeft, w = guiWidth;
            int margin = 6;
            int col1 = x + margin + 4;

            for (int i = scrollOffset; i < sortedDiags.size() && i < scrollOffset + visibleEntries; i++) {
                DiagEntry entry = sortedDiags.get(i);
                int ey = listTop + (i - scrollOffset) * entryH;
                int btnX = col1;
                int btnY = ey + 34;

                for (SugCoord sc : entry.sugCoords) {
                    int btnW = font.width(sc.label + " " + sc.x + "," + sc.y + "," + sc.z + " ") + 12;
                    int btnH = 11;

                    if (mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                        boolean nowActive = SignalHighlightManager.toggle(sc.x, sc.y, sc.z, sc.type, sc.dirX, sc.dirZ);
                        // Play click sound
                        Minecraft.getInstance().player.playSound(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, nowActive ? 1.2f : 0.8f);
                        return true;
                    }

                    btnX += btnW + 4;
                    if (btnX + 60 > x + w - margin) break;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0 && scrollOffset > 0) { scrollOffset--; return true; }
        else if (scrollY < 0 && scrollOffset < maxScroll) { scrollOffset++; return true; }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
