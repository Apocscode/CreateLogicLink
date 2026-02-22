package com.apocscode.logiclink.client;

import com.apocscode.logiclink.block.TrainMonitorBlockEntity;
import com.apocscode.logiclink.block.TrainMonitorMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI screen for the Train Network Monitor.
 * Features a tabbed interface: MAP | TRAINS | STATIONS | ALERTS.
 * MAP tab shows the CTC-style topological network map.
 * TRAINS tab shows scrollable train list with details panel.
 * STATIONS tab shows station list with arrival/departure info.
 * ALERTS tab shows derailments and network issues.
 */
public class TrainMonitorScreen extends AbstractContainerScreen<TrainMonitorMenu> {

    // ==================== Tab Constants ====================
    private static final int TAB_MAP      = 0;
    private static final int TAB_TRAINS   = 1;
    private static final int TAB_STATIONS = 2;
    private static final int TAB_SIGNALS  = 3;
    private static final int TAB_ALERTS   = 4;
    private static final String[] TAB_LABELS = {"MAP", "TRAINS", "STATIONS", "SIGNALS", "ALERTS"};

    // ==================== Colors — CRN-style industrial palette ====================
    private static final int BG_COLOR       = 0xDD303030;
    private static final int BORDER_COLOR   = 0xFFD4A843; // brass/gold border
    private static final int TITLE_COLOR    = 0xFFE8C84A; // gold title
    private static final int TEXT_COLOR     = 0xFFCCCCCC;
    private static final int HEADER_COLOR   = 0xFF88CCFF;
    private static final int GREEN          = 0xFF1AEA5F; // CRN on-time
    private static final int RED            = 0xFFFF4242; // CRN delayed
    private static final int YELLOW         = 0xFFFF9900; // CRN notification orange
    private static final int GRAY           = 0xFF8B8B8B; // CRN neutral
    private static final int WHITE          = 0xFFFFFFFF;
    private static final int DARK_BG        = 0xFF303030; // CRN surface
    private static final int PANEL_BG       = 0xFF393939; // CRN panel
    private static final int SEPARATOR      = 0xFF575757; // CRN dark border
    private static final int TAB_ACTIVE_BG  = 0xFF393939;
    private static final int TAB_INACTIVE_BG = 0xFF303030;
    private static final int TAB_HOVER_BG   = 0xFF474747;
    private static final int ALERT_BG       = 0xFF4A2020;

    // ==================== Layout State ====================
    private int activeTab = TAB_MAP;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int selectedTrain = -1;
    private int stationScrollOffset = 0;
    private int stationMaxScroll = 0;
    private int signalsScrollOffset = 0;
    private int signalsMaxScroll = 0;

    // ==================== Map Zoom & Pan ====================
    private float mapZoom = 1.0f;
    private static final float MIN_ZOOM = 0.1f;
    private static final float MAX_ZOOM = 10.0f;
    private float mapPanX = 0; // pixel offset
    private float mapPanY = 0;
    private boolean mapDragging = false;
    private double dragStartX, dragStartY;
    private float dragStartPanX, dragStartPanY;

    // Cached data
    private TrainMonitorBlockEntity monitorBE = null;

    public TrainMonitorScreen(TrainMonitorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        // Size set dynamically in init() to fill most of the screen
        this.imageWidth = 400;
        this.imageHeight = 280;
    }

    @Override
    protected void init() {
        // Scale GUI to fill ~90% of the game window
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        this.imageWidth = Math.max(400, (int)(screenW * 0.9f));
        this.imageHeight = Math.max(280, (int)(screenH * 0.88f));
        super.init();
        if (mc.level != null) {
            BlockEntity be = mc.level.getBlockEntity(menu.getMasterPos());
            if (be instanceof TrainMonitorBlockEntity m) {
                monitorBE = m;
            }
        }
    }

    // ==================== Main Render ====================

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx, mouseX, mouseY, partialTick);

        int x = leftPos;
        int y = topPos;
        int w = imageWidth;
        int h = imageHeight;

        // Border + background
        gfx.fill(x - 2, y - 2, x + w + 2, y + h + 2, BORDER_COLOR);
        gfx.fill(x, y, x + w, y + h, DARK_BG);

        if (monitorBE == null) {
            gfx.drawCenteredString(font, "No monitor data available", x + w / 2, y + h / 2, RED);
            return;
        }

        // Title bar
        gfx.fill(x, y, x + w, y + 14, PANEL_BG);
        gfx.fill(x, y + 14, x + w, y + 15, SEPARATOR);

        String titleText = "TRAIN NETWORK MONITOR";
        int monW = monitorBE.getMonitorWidth();
        int monH = monitorBE.getMonitorHeight();
        if (monW > 1 || monH > 1) {
            titleText += " [" + monW + "x" + monH + "]";
        }
        gfx.drawCenteredString(font, titleText, x + w / 2, y + 3, TITLE_COLOR);

        // Tab bar
        int tabY = y + 15;
        int tabH = 12;
        renderTabBar(gfx, x, tabY, w, tabH, mouseX, mouseY);

        // Content area
        int contentY = tabY + tabH + 1;
        int contentH = h - (contentY - y);

        gfx.fill(x, contentY, x + w, y + h, TAB_ACTIVE_BG);

        // Dispatch to active tab
        switch (activeTab) {
            case TAB_MAP -> renderMapTab(gfx, x, contentY, w, contentH, mouseX, mouseY, partialTick);
            case TAB_TRAINS -> renderTrainsTab(gfx, x, contentY, w, contentH, mouseX, mouseY);
            case TAB_STATIONS -> renderStationsTab(gfx, x, contentY, w, contentH, mouseX, mouseY);
            case TAB_SIGNALS -> renderSignalsTab(gfx, x, contentY, w, contentH, mouseX, mouseY);
            case TAB_ALERTS -> renderAlertsTab(gfx, x, contentY, w, contentH);
        }
    }

    // ==================== Tab Bar ====================

    private void renderTabBar(GuiGraphics gfx, int x, int y, int totalW, int tabH,
                               int mouseX, int mouseY) {
        int tabW = totalW / TAB_LABELS.length;

        for (int i = 0; i < TAB_LABELS.length; i++) {
            int tx = x + i * tabW;
            int tw = (i == TAB_LABELS.length - 1) ? (totalW - i * tabW) : tabW;

            boolean active = (i == activeTab);
            boolean hovered = mouseX >= tx && mouseX < tx + tw &&
                              mouseY >= y && mouseY < y + tabH;

            int bg = active ? TAB_ACTIVE_BG : (hovered ? TAB_HOVER_BG : TAB_INACTIVE_BG);
            gfx.fill(tx, y, tx + tw, y + tabH, bg);

            // Bottom highlight on active tab
            if (active) {
                gfx.fill(tx, y + tabH - 1, tx + tw, y + tabH, TITLE_COLOR);
            }

            // Separator between tabs
            if (i > 0) {
                gfx.fill(tx, y + 1, tx + 1, y + tabH - 1, SEPARATOR);
            }

            // Label — add alert/diagnostic count badges
            String label = TAB_LABELS[i];
            if (i == TAB_ALERTS) {
                int alertCount = getAlertCount();
                if (alertCount > 0) {
                    label += " (" + alertCount + ")";
                }
            }
            if (i == TAB_SIGNALS) {
                int diagCount = getDiagnosticCount();
                if (diagCount > 0) {
                    label += " (" + diagCount + ")";
                }
            }

            int labelColor = active ? TITLE_COLOR : (hovered ? WHITE : GRAY);
            int labelX = tx + (tw - font.width(label)) / 2;
            gfx.drawString(font, label, labelX, y + 2, labelColor, false);
        }

        // Bottom border under tab bar
        gfx.fill(x, y + tabH, x + totalW, y + tabH + 1, SEPARATOR);
    }

    // ==================== MAP Tab ====================

    private void renderMapTab(GuiGraphics gfx, int x, int y, int w, int h,
                               int mouseX, int mouseY, float partialTick) {
        CompoundTag mapData = monitorBE.getMapData();

        if (mapData == null || mapData.isEmpty()) {
            gfx.drawCenteredString(font, "Scanning network topology...", x + w / 2, y + h / 2, YELLOW);
            return;
        }

        // Zoom/pan HUD
        String zoomStr = String.format("Zoom: %.0f%%", mapZoom * 100);
        gfx.drawString(font, zoomStr, x + 4, y + h - 10, GRAY, false);
        gfx.drawString(font, "Scroll=Zoom  Drag=Pan  R=Reset", x + w - font.width("Scroll=Zoom  Drag=Pan  R=Reset") - 4, y + h - 10, 0xFF555555, false);

        // Render the CTC map in the content area with zoom/pan
        TrainMapRenderer.renderGUI(gfx, font, mapData,
                x + 2, y + 2, w - 4, h - 16,
                monitorBE.getTrainCount(), monitorBE.getStationCount(),
                monitorBE.getSignalCount(),
                monitorBE.getTrainsMoving(), monitorBE.getTrainsDerailed(),
                mouseX, mouseY,
                mapZoom, mapPanX, mapPanY);
    }

    // ==================== TRAINS Tab ====================

    private void renderTrainsTab(GuiGraphics gfx, int x, int y, int w, int h,
                                  int mouseX, int mouseY) {
        // Two-panel layout: left details + right list
        int leftW = 100;
        int rightX = x + leftW + 1;
        int rightW = w - leftW - 1;

        // Left panel
        gfx.fill(x, y, x + leftW, y + h, PANEL_BG);
        gfx.fill(x + leftW, y, x + leftW + 1, y + h, SEPARATOR);

        renderOverviewPanel(gfx, x + 4, y + 4, leftW - 8, h - 8);

        // Right panel — train list
        renderTrainList(gfx, rightX + 2, y + 4, rightW - 4, h - 8, mouseX, mouseY);
    }

    private void renderOverviewPanel(GuiGraphics gfx, int x, int y, int width, int maxHeight) {
        gfx.drawString(font, "OVERVIEW", x, y, HEADER_COLOR, false);
        y += 12;

        int total = monitorBE.getTrainCount();
        int moving = monitorBE.getTrainsMoving();
        int stopped = monitorBE.getTrainsStopped();
        int derailed = monitorBE.getTrainsDerailed();
        int stations = monitorBE.getStationCount();
        int signals = monitorBE.getSignalCount();

        gfx.drawString(font, "Trains: " + total, x, y, TEXT_COLOR, false);
        y += 11;

        gfx.fill(x, y + 1, x + 5, y + 6, GREEN);
        gfx.drawString(font, " Moving: " + moving, x + 6, y, GREEN, false);
        y += 10;

        gfx.fill(x, y + 1, x + 5, y + 6, GRAY);
        gfx.drawString(font, " Stopped: " + stopped, x + 6, y, GRAY, false);
        y += 10;

        if (derailed > 0) {
            gfx.fill(x, y + 1, x + 5, y + 6, RED);
            gfx.drawString(font, " Derailed: " + derailed, x + 6, y, RED, false);
        } else {
            gfx.fill(x, y + 1, x + 5, y + 6, 0xFF336633);
            gfx.drawString(font, " Derailed: 0", x + 6, y, 0xFF558855, false);
        }
        y += 14;

        gfx.fill(x, y, x + width, y + 1, SEPARATOR);
        y += 4;

        gfx.drawString(font, "INFRASTRUCTURE", x, y, HEADER_COLOR, false);
        y += 12;

        gfx.drawString(font, "Stations: " + stations, x, y, TEXT_COLOR, false);
        y += 10;
        gfx.drawString(font, "Signals: " + signals, x, y, TEXT_COLOR, false);
        y += 14;

        gfx.fill(x, y, x + width, y + 1, SEPARATOR);
        y += 4;

        // Selected train details
        List<CompoundTag> trains = monitorBE.getTrainDataList();
        if (selectedTrain >= 0 && selectedTrain < trains.size()) {
            gfx.drawString(font, "SELECTED", x, y, HEADER_COLOR, false);
            y += 12;

            CompoundTag train = trains.get(selectedTrain);
            String name = train.getString("name");
            if (name.length() > 14) name = name.substring(0, 12) + "..";
            gfx.drawString(font, name, x, y, WHITE, false);
            y += 10;

            double speed = train.getDouble("speed");
            String speedStr = String.format("%.1f m/t", Math.abs(speed));
            gfx.drawString(font, "Spd: " + speedStr, x, y, TEXT_COLOR, false);
            y += 10;

            int cars = train.getInt("carriages");
            gfx.drawString(font, "Cars: " + cars, x, y, TEXT_COLOR, false);
            y += 10;

            if (train.contains("destination")) {
                String dest = train.getString("destination");
                if (dest.length() > 14) dest = dest.substring(0, 12) + "..";
                gfx.drawString(font, "\u2192 " + dest, x, y, YELLOW, false);
                y += 10;
            }

            if (train.contains("currentStation")) {
                String st = train.getString("currentStation");
                if (st.length() > 14) st = st.substring(0, 12) + "..";
                gfx.drawString(font, "\u25A0 " + st, x, y, GREEN, false);
            }
        }
    }

    private void renderTrainList(GuiGraphics gfx, int x, int y, int width, int height,
                                  int mouseX, int mouseY) {
        gfx.drawString(font, "TRAINS", x, y, HEADER_COLOR, false);
        y += 12;

        List<CompoundTag> trains = monitorBE.getTrainDataList();
        if (trains.isEmpty()) {
            gfx.drawString(font, "No trains detected", x, y, GRAY, false);
            return;
        }

        int entryHeight = 14;
        int visibleEntries = (height - 14) / entryHeight;
        maxScroll = Math.max(0, trains.size() - visibleEntries);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        for (int i = scrollOffset; i < trains.size() && i < scrollOffset + visibleEntries; i++) {
            CompoundTag train = trains.get(i);
            int entryY = y + (i - scrollOffset) * entryHeight;

            boolean hovered = mouseX >= x && mouseX <= x + width &&
                              mouseY >= entryY && mouseY <= entryY + entryHeight;
            if (i == selectedTrain) {
                gfx.fill(x - 1, entryY - 1, x + width + 1, entryY + entryHeight - 2, 0xFF222244);
            } else if (hovered) {
                gfx.fill(x - 1, entryY - 1, x + width + 1, entryY + entryHeight - 2, 0xFF1A1A33);
            }

            boolean isDerailed = train.getBoolean("derailed");
            boolean isNavigating = train.getBoolean("navigating");
            double speed = Math.abs(train.getDouble("speed"));

            int dotColor;
            if (isDerailed) dotColor = RED;
            else if (speed > 0.01) dotColor = GREEN;
            else if (isNavigating) dotColor = YELLOW;
            else dotColor = GRAY;

            gfx.fill(x, entryY + 2, x + 4, entryY + 6, dotColor);

            String name = train.getString("name");
            if (name.length() > 16) name = name.substring(0, 14) + "..";
            gfx.drawString(font, name, x + 7, entryY, WHITE, false);

            String status;
            int statusColor;
            if (isDerailed) {
                status = "DERAILED";
                statusColor = RED;
            } else if (train.contains("currentStation")) {
                String st = train.getString("currentStation");
                status = "\u25A0 " + (st.length() > 10 ? st.substring(0, 8) + ".." : st);
                statusColor = GREEN;
            } else if (train.contains("destination")) {
                String dest = train.getString("destination");
                status = "\u2192 " + (dest.length() > 10 ? dest.substring(0, 8) + ".." : dest);
                statusColor = YELLOW;
            } else {
                status = "idle";
                statusColor = GRAY;
            }

            int statusWidth = font.width(status);
            gfx.drawString(font, status, x + width - statusWidth, entryY, statusColor, false);

            gfx.fill(x, entryY + entryHeight - 2, x + width, entryY + entryHeight - 1, 0xFF1A1A33);
        }

        // Scroll bar
        if (maxScroll > 0) {
            int barHeight = Math.max(10, height * visibleEntries / trains.size());
            int barY = y + (int) ((height - barHeight) * (float) scrollOffset / maxScroll);
            gfx.fill(x + width - 2, barY, x + width, barY + barHeight, 0xFF555577);
        }
    }

    // ==================== STATIONS Tab ====================

    private void renderStationsTab(GuiGraphics gfx, int x, int y, int w, int h,
                                    int mouseX, int mouseY) {
        CompoundTag mapData = monitorBE.getMapData();
        if (mapData == null || !mapData.contains("Stations")) {
            gfx.drawCenteredString(font, "Scanning stations...", x + w / 2, y + h / 2, YELLOW);
            return;
        }

        int margin = 4;
        gfx.drawString(font, "STATIONS", x + margin, y + margin, HEADER_COLOR, false);

        int colNameX = x + margin;
        int colStatusX = x + w / 2;
        int colTrainX = x + w - 80;
        int headerY = y + margin + 12;

        gfx.drawString(font, "Name", colNameX, headerY, GRAY, false);
        gfx.drawString(font, "Status", colStatusX, headerY, GRAY, false);
        gfx.drawString(font, "Train", colTrainX, headerY, GRAY, false);

        gfx.fill(x + margin, headerY + 10, x + w - margin, headerY + 11, SEPARATOR);

        ListTag stations = mapData.getList("Stations", Tag.TAG_COMPOUND);
        int entryH = 12;
        int listY = headerY + 14;
        int visibleEntries = (h - (listY - y) - margin) / entryH;
        stationMaxScroll = Math.max(0, stations.size() - visibleEntries);
        stationScrollOffset = Math.min(stationScrollOffset, stationMaxScroll);

        for (int i = stationScrollOffset; i < stations.size() && i < stationScrollOffset + visibleEntries; i++) {
            CompoundTag station = stations.getCompound(i);
            int ey = listY + (i - stationScrollOffset) * entryH;

            String name = station.getString("name");
            // trainPresent/trainImminent are stored as Strings (train name), not booleans
            boolean hasTrainPresent = station.contains("trainPresent");
            boolean hasTrainImminent = station.contains("trainImminent");

            // Station name (truncated)
            if (font.width(name) > (colStatusX - colNameX - 4)) {
                while (font.width(name + "..") > (colStatusX - colNameX - 4) && name.length() > 1)
                    name = name.substring(0, name.length() - 1);
                name += "..";
            }
            gfx.drawString(font, name, colNameX, ey, WHITE, false);

            // Status
            if (hasTrainPresent) {
                gfx.drawString(font, "\u25CF Occupied", colStatusX, ey, GREEN, false);
            } else if (hasTrainImminent) {
                gfx.drawString(font, "\u25CF Arriving", colStatusX, ey, YELLOW, false);
            } else {
                gfx.drawString(font, "\u25CB Empty", colStatusX, ey, GRAY, false);
            }

            // Train name if present or imminent
            String tName = null;
            if (hasTrainPresent) {
                tName = station.getString("trainPresent");
            } else if (hasTrainImminent) {
                tName = station.getString("trainImminent");
            }
            if (tName != null && !tName.isEmpty()) {
                if (tName.length() > 10) tName = tName.substring(0, 8) + "..";
                gfx.drawString(font, tName, colTrainX, ey, TEXT_COLOR, false);
            }

            // Row separator
            gfx.fill(x + margin, ey + entryH - 1, x + w - margin, ey + entryH, 0xFF1A1A33);
        }

        // Station scroll bar
        if (stationMaxScroll > 0) {
            int listH = visibleEntries * entryH;
            int barH = Math.max(10, listH * visibleEntries / stations.size());
            int barY = listY + (int) ((listH - barH) * (float) stationScrollOffset / stationMaxScroll);
            gfx.fill(x + w - margin - 2, barY, x + w - margin, barY + barH, 0xFF555577);
        }
    }

    // ==================== SIGNALS Tab ====================

    private void renderSignalsTab(GuiGraphics gfx, int x, int y, int w, int h,
                                   int mouseX, int mouseY) {
        int margin = 4;
        gfx.drawString(font, "SIGNAL DIAGNOSTICS", x + margin, y + margin, HEADER_COLOR, false);

        CompoundTag mapData = monitorBE.getMapData();
        if (mapData == null || !mapData.contains("Diagnostics")) {
            gfx.drawCenteredString(font, "Scanning network topology...", x + w / 2, y + h / 2, YELLOW);
            return;
        }

        ListTag diagnostics = mapData.getList("Diagnostics", 10);
        if (diagnostics.isEmpty()) {
            gfx.drawCenteredString(font, "\u2713 No signal issues detected", x + w / 2, y + h / 2, GREEN);
            return;
        }

        // Summary counts
        int junctionCount = 0, noPathCount = 0, conflictCount = 0;
        for (int i = 0; i < diagnostics.size(); i++) {
            String type = diagnostics.getCompound(i).getString("type");
            if (type.equals("JUNCTION_UNSIGNALED")) junctionCount++;
            else if (type.equals("NO_PATH")) noPathCount++;
            else if (type.equals("SIGNAL_CONFLICT")) conflictCount++;
        }

        int lineY = y + margin + 12;
        String summary = diagnostics.size() + " issue(s): " + junctionCount + " junction, "
                + noPathCount + " no-path, " + conflictCount + " conflict";
        gfx.drawString(font, summary, x + margin, lineY, TEXT_COLOR, false);
        lineY += 14;
        gfx.fill(x + margin, lineY - 2, x + w - margin, lineY - 1, SEPARATOR);

        // Scrollable diagnostic list
        int entryH = 42;
        int listH = h - (lineY - y) - margin;
        int visibleEntries = Math.max(1, listH / entryH);
        signalsMaxScroll = Math.max(0, diagnostics.size() - visibleEntries);
        signalsScrollOffset = Math.min(signalsScrollOffset, signalsMaxScroll);

        for (int i = signalsScrollOffset; i < diagnostics.size() && i < signalsScrollOffset + visibleEntries; i++) {
            CompoundTag diag = diagnostics.getCompound(i);
            int ey = lineY + (i - signalsScrollOffset) * entryH;

            String severity = diag.getString("severity");
            String type = diag.getString("type");
            String desc = diag.getString("desc");

            int sevColor = switch (severity) {
                case "CRIT" -> RED;
                case "WARN" -> YELLOW;
                default -> HEADER_COLOR;
            };
            int bgColor = switch (severity) {
                case "CRIT" -> ALERT_BG;
                case "WARN" -> 0xFF2A2A11;
                default -> PANEL_BG;
            };

            gfx.fill(x + margin, ey, x + w - margin, ey + entryH - 2, bgColor);

            // Severity + type header
            gfx.drawString(font, "[" + severity + "] " + type, x + margin + 2, ey + 1, sevColor, false);

            // Location coordinates
            if (diag.contains("x")) {
                String loc = "@ " + (int) diag.getFloat("x") + ", " + (int) diag.getFloat("y") + ", " + (int) diag.getFloat("z");
                gfx.drawString(font, loc, x + w - margin - font.width(loc) - 2, ey + 1, GRAY, false);
            }

            // Description line
            String descTrunc = desc;
            int maxDescW = w - margin * 2 - 8;
            if (font.width(descTrunc) > maxDescW) {
                while (font.width(descTrunc + "..") > maxDescW && descTrunc.length() > 1)
                    descTrunc = descTrunc.substring(0, descTrunc.length() - 1);
                descTrunc += "..";
            }
            gfx.drawString(font, descTrunc, x + margin + 2, ey + 12, TEXT_COLOR, false);

            // Signal placement suggestions
            if (diag.contains("suggestions")) {
                ListTag sug = diag.getList("suggestions", 10);
                boolean isConflict = type.equals("SIGNAL_CONFLICT");
                StringBuilder sb = new StringBuilder(isConflict ? "\u2716 Fix: " : "\u2192 Place: ");
                for (int s = 0; s < sug.size(); s++) {
                    CompoundTag sp = sug.getCompound(s);
                    if (s > 0) sb.append("  ");
                    String sigType = sp.contains("signalType") ? sp.getString("signalType") : "signal";
                    String label = switch (sigType) {
                        case "chain" -> "\u26D3chain";
                        case "conflict" -> "\u26A0conflict";
                        default -> "\u2691signal";
                    };
                    sb.append(label).append("[").append(sp.getInt("sx")).append(", ")
                      .append(sp.getInt("sy")).append(", ")
                      .append(sp.getInt("sz")).append("]");
                    if (sp.contains("dir")) sb.append(" ").append(sp.getString("dir"));
                }
                String sugStr = sb.toString();
                if (font.width(sugStr) > maxDescW) {
                    sugStr = sugStr.substring(0, Math.min(sugStr.length(), 80)) + "..";
                }
                int sugColor = isConflict ? RED : GREEN;
                gfx.drawString(font, sugStr, x + margin + 2, ey + 23, sugColor, false);
            }

            // Entry separator
            gfx.fill(x + margin, ey + entryH - 2, x + w - margin, ey + entryH - 1, SEPARATOR);
        }

        // Scroll bar
        if (signalsMaxScroll > 0) {
            int barH = Math.max(10, listH * visibleEntries / diagnostics.size());
            int barY = lineY + (int) ((listH - barH) * (float) signalsScrollOffset / signalsMaxScroll);
            gfx.fill(x + w - margin - 2, barY, x + w - margin, barY + barH, 0xFF555577);
        }
    }

    // ==================== ALERTS Tab ====================

    private void renderAlertsTab(GuiGraphics gfx, int x, int y, int w, int h) {
        int margin = 4;
        gfx.drawString(font, "ALERTS", x + margin, y + margin, HEADER_COLOR, false);

        int lineY = y + margin + 14;

        List<String[]> alerts = gatherAlerts();

        if (alerts.isEmpty()) {
            gfx.drawCenteredString(font, "\u2713 All systems nominal", x + w / 2, y + h / 2, GREEN);
            return;
        }

        for (String[] alert : alerts) {
            if (lineY + 12 > y + h - margin) break;

            String severity = alert[0]; // "CRIT", "WARN", "INFO"
            String message = alert[1];

            int sevColor;
            int bgColor;
            switch (severity) {
                case "CRIT" -> { sevColor = RED; bgColor = ALERT_BG; }
                case "WARN" -> { sevColor = YELLOW; bgColor = 0xFF2A2A11; }
                default     -> { sevColor = HEADER_COLOR; bgColor = PANEL_BG; }
            }

            gfx.fill(x + margin, lineY - 1, x + w - margin, lineY + 10, bgColor);
            gfx.drawString(font, "[" + severity + "]", x + margin + 2, lineY, sevColor, false);
            gfx.drawString(font, message, x + margin + 36, lineY, TEXT_COLOR, false);

            lineY += 13;
        }
    }

    private List<String[]> gatherAlerts() {
        List<String[]> alerts = new ArrayList<>();

        // Derailed trains
        List<CompoundTag> trains = monitorBE.getTrainDataList();
        for (CompoundTag train : trains) {
            if (train.getBoolean("derailed")) {
                String name = train.getString("name");
                alerts.add(new String[]{"CRIT", "DERAILED: " + name});
            }
        }

        // No-path and signal diagnostics from map analysis
        CompoundTag mapData = monitorBE.getMapData();
        if (mapData != null && mapData.contains("Diagnostics")) {
            ListTag diags = mapData.getList("Diagnostics", 10);
            for (int i = 0; i < diags.size(); i++) {
                CompoundTag d = diags.getCompound(i);
                String type = d.getString("type");
                if (type.equals("NO_PATH")) {
                    String loc = d.contains("x")
                            ? " @ " + (int) d.getFloat("x") + "," + (int) d.getFloat("z")
                            : "";
                    alerts.add(new String[]{"CRIT", "NO PATH: " + d.getString("trainName") + loc});
                } else if (type.equals("SIGNAL_CONFLICT")) {
                    String loc = d.contains("x")
                            ? " @ " + (int) d.getFloat("x") + "," + (int) d.getFloat("z")
                            : "";
                    alerts.add(new String[]{d.getString("severity"), "CONFLICT: Wrong signal type" + loc});
                } else if (d.getString("severity").equals("CRIT")) {
                    alerts.add(new String[]{"WARN", "SIGNAL: " + d.getString("desc")});
                }
            }
        }

        // No trains
        if (monitorBE.getTrainCount() == 0 && trains.isEmpty()) {
            alerts.add(new String[]{"WARN", "No trains detected on network"});
        }

        // No stations
        if (monitorBE.getStationCount() == 0) {
            alerts.add(new String[]{"WARN", "No stations found"});
        }

        // No map data
        if (mapData == null || mapData.isEmpty()) {
            alerts.add(new String[]{"INFO", "Network topology scan pending"});
        }

        return alerts;
    }

    private int getAlertCount() {
        if (monitorBE == null) return 0;
        int count = monitorBE.getTrainsDerailed();
        if (monitorBE.getTrainCount() == 0) count++;
        CompoundTag mapData = monitorBE.getMapData();
        if (mapData != null && mapData.contains("Diagnostics")) {
            ListTag diags = mapData.getList("Diagnostics", 10);
            for (int i = 0; i < diags.size(); i++) {
                String type = diags.getCompound(i).getString("type");
                if (type.equals("NO_PATH") || type.equals("SIGNAL_CONFLICT")) count++;
            }
        }
        return count;
    }

    private int getDiagnosticCount() {
        if (monitorBE == null) return 0;
        CompoundTag mapData = monitorBE.getMapData();
        if (mapData != null && mapData.contains("Diagnostics")) {
            return mapData.getList("Diagnostics", 10).size();
        }
        return 0;
    }

    // ==================== Input Handling ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Tab click detection
            int tabY = topPos + 15;
            int tabH = 12;
            int tabW = imageWidth / TAB_LABELS.length;

            if (mouseY >= tabY && mouseY < tabY + tabH) {
                for (int i = 0; i < TAB_LABELS.length; i++) {
                    int tx = leftPos + i * tabW;
                    int tw = (i == TAB_LABELS.length - 1) ? (imageWidth - i * tabW) : tabW;
                    if (mouseX >= tx && mouseX < tx + tw) {
                        activeTab = i;
                        return true;
                    }
                }
            }

            // Map drag start (MAP tab, inside content area)
            if (activeTab == TAB_MAP) {
                int contentY = tabY + tabH + 1;
                if (mouseX >= leftPos && mouseX <= leftPos + imageWidth &&
                    mouseY >= contentY && mouseY <= topPos + imageHeight) {
                    mapDragging = true;
                    dragStartX = mouseX;
                    dragStartY = mouseY;
                    dragStartPanX = mapPanX;
                    dragStartPanY = mapPanY;
                    return true;
                }
            }

            // Train list click (only on TRAINS tab)
            if (activeTab == TAB_TRAINS && monitorBE != null) {
                int contentY = tabY + tabH + 1;
                int leftW = 100;
                int listX = leftPos + leftW + 3;
                int listY = contentY + 4 + 12; // after "TRAINS" header
                int listW = imageWidth - leftW - 5;
                int entryHeight = 14;

                List<CompoundTag> trains = monitorBE.getTrainDataList();
                int listH = imageHeight - (listY - topPos) - 8;
                int visibleEntries = listH / entryHeight;

                for (int i = scrollOffset; i < trains.size() && i < scrollOffset + visibleEntries; i++) {
                    int entryY = listY + (i - scrollOffset) * entryHeight;
                    if (mouseX >= listX && mouseX <= listX + listW &&
                        mouseY >= entryY && mouseY <= entryY + entryHeight) {
                        selectedTrain = (selectedTrain == i) ? -1 : i;
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (activeTab == TAB_MAP) {
            // Zoom centered on mouse position
            float oldZoom = mapZoom;
            float zoomFactor = (deltaY > 0) ? 1.15f : 1.0f / 1.15f;
            mapZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, mapZoom * zoomFactor));
            // Adjust pan so the point under the mouse stays fixed
            float mx = (float) mouseX - leftPos - imageWidth / 2f;
            float my = (float) mouseY - topPos - imageHeight / 2f;
            mapPanX = mx - (mx - mapPanX) * (mapZoom / oldZoom);
            mapPanY = my - (my - mapPanY) * (mapZoom / oldZoom);
            return true;
        } else if (activeTab == TAB_TRAINS) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) deltaY));
        } else if (activeTab == TAB_STATIONS) {
            stationScrollOffset = Math.max(0, Math.min(stationMaxScroll, stationScrollOffset - (int) deltaY));
        } else if (activeTab == TAB_SIGNALS) {
            signalsScrollOffset = Math.max(0, Math.min(signalsMaxScroll, signalsScrollOffset - (int) deltaY));
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (activeTab == TAB_MAP && mapDragging && button == 0) {
            mapPanX = dragStartPanX + (float)(mouseX - dragStartX);
            mapPanY = dragStartPanY + (float)(mouseY - dragStartY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && mapDragging) {
            mapDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Tab switching with number keys 1-4
        if (keyCode >= 49 && keyCode <= 53) { // '1' thru '5'
            activeTab = keyCode - 49;
            return true;
        }
        // 'R' key = reset zoom/pan
        if (keyCode == 82 && activeTab == TAB_MAP) { // 'R'
            mapZoom = 1.0f;
            mapPanX = 0;
            mapPanY = 0;
            return true;
        }
        // '+' / '-' keys for zoom
        if (activeTab == TAB_MAP) {
            if (keyCode == 61 || keyCode == 334) { // '=' / numpad '+'
                mapZoom = Math.min(MAX_ZOOM, mapZoom * 1.25f);
                return true;
            }
            if (keyCode == 45 || keyCode == 333) { // '-' / numpad '-'
                mapZoom = Math.max(MIN_ZOOM, mapZoom / 1.25f);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        // Handled in render()
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        // Don't render default inventory labels
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}