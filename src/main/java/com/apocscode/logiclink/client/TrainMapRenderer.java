package com.apocscode.logiclink.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * CTC-style (Centralized Traffic Control) network map renderer.
 * Projects the Create train network topology onto a 2D display surface.
 *
 * Visual language (modeled on real railway control panels):
 * - Track lines: color-coded by occupancy state
 *   WHITE = clear track, RED = occupied (train in signal block),
 *   GREEN = reserved route (navigation path ahead of train)
 * - Stations: rectangular berths on the track line, labeled
 *   GREEN fill = train present, AMBER = imminent, GRAY = empty
 * - Signals: small colored dots (RED/GREEN/YELLOW) at boundaries
 * - Trains: colored lozenges moving along track with abbreviated name
 *   GREEN = moving, YELLOW = navigating/slow, RED = derailed, GRAY = stopped
 * - Observers: diamond markers, pulsing when activated
 *
 * Supports both TESR (in-world block face) and GUI (screen) rendering.
 */
public class TrainMapRenderer {

    private static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    // ==================== Color Palette (CRN-inspired CTC Panel) ====================
    // Background
    private static final int BG_DARK       = 0xFF303030;  // CRN surface gray
    private static final int GRID_LINE     = 0xFF3A3A3A;  // subtle grid

    // Tracks
    private static final int TRACK_CLEAR   = 0xFFAAAAAA;  // white-gray (clear)
    private static final int TRACK_OCCUPIED = 0xFFFF4242;  // CRN red (occupied)
    private static final int TRACK_ROUTE   = 0xFF1AEA5F;  // CRN green (reserved route)
    private static final int TRACK_INTERDIM = 0xFF8844FF;  // purple (portal)
    private static final int TRACK_CURVE   = 0xFFBBBBBB;  // slightly dimmer for curves

    // Stations
    private static final int STATION_EMPTY     = 0xFF575757;  // CRN dark border
    private static final int STATION_PRESENT   = 0xFF1AEA5F;  // CRN green (train docked)
    private static final int STATION_IMMINENT  = 0xFFFF9900;  // CRN orange (train approaching)
    private static final int STATION_LABEL     = 0xFFDDDDEE;  // near-white label
    private static final int STATION_BORDER    = 0xFF6F6F6F;  // CRN highlight

    // Signals
    private static final int SIGNAL_GREEN  = 0xFF1AEA5F;  // CRN green
    private static final int SIGNAL_RED    = 0xFFFF4242;  // CRN red
    private static final int SIGNAL_YELLOW = 0xFFFF9900;  // CRN orange
    private static final int SIGNAL_OFF    = 0xFF575757;  // CRN dark

    // Trains
    private static final int TRAIN_MOVING  = 0xFF1AEA5F;  // CRN green
    private static final int TRAIN_STOPPED = 0xFF8B8B8B;  // CRN neutral gray
    private static final int TRAIN_DERAIL  = 0xFFFF4242;  // CRN red
    private static final int TRAIN_NAV     = 0xFFFF9900;  // CRN orange

    // UI
    private static final int TITLE_COLOR   = 0xFFE8C84A;  // brass/gold accent
    private static final int TEXT_DIM      = 0xFF747474;  // CRN neutral
    private static final int HEADER_COLOR  = 0xFF88CCFF;  // light blue
    private static final int ALERT_RED     = 0xFFFF4242;  // CRN red
    private static final int WHITE         = 0xFFEEEEEE;
    private static final int COMPASS_COLOR = 0xFF6F6F6F;  // CRN highlight

    // ==================== Layout Constants ====================
    private static final float MAP_MARGIN = 6.0f;   // pixels around map area
    private static final float HEADER_HEIGHT = 14.0f; // status bar height
    private static final float STATION_HALF_W = 4.0f; // station box half-width along track
    private static final float STATION_HALF_H = 2.5f; // station box half-height perpendicular
    private static final float SIGNAL_RADIUS = 1.5f;
    private static final float TRAIN_HALF_W = 5.0f;   // train lozenge half-width
    private static final float TRAIN_HALF_H = 2.0f;
    private static final float TRACK_THICKNESS = 0.8f;
    private static final float TRACK_THICKNESS_TESR = 1.6f; // thicker for in-world readability

    // ==================== TESR Rendering ====================

    /**
     * Render the CTC map on the TESR (in-world block face).
     * Called from TrainMonitorRenderer when in MAP mode.
     *
     * @param ps          PoseStack already transformed to screen-space
     * @param buffers     MultiBufferSource for rendering
     * @param font        Font for text
     * @param mapData     CompoundTag with full topology from TrainNetworkDataReader
     * @param screenW     Screen width in pixels (after pixelScale)
     * @param screenH     Screen height in pixels
     * @param trainCount  Total train count
     * @param stationCount Total station count
     * @param signalCount Total signal count
     * @param moving      Trains moving
     * @param derailed    Trains derailed
     * @param partialTick Partial tick for animations
     */
    public static void renderTESR(PoseStack ps, MultiBufferSource buffers, Font font,
                                   CompoundTag mapData,
                                   float screenW, float screenH,
                                   int trainCount, int stationCount, int signalCount,
                                   int moving, int derailed,
                                   float partialTick) {
        if (mapData == null || mapData.isEmpty()) {
            // No data — show connecting message
            String msg = "Scanning network topology...";
            float msgX = (screenW - font.width(msg)) / 2;
            drawTextTESR(ps, buffers, font, msg, msgX, screenH / 2, TRAIN_NAV);
            return;
        }

        // === Header bar ===
        float y = 2;
        String title = "NETWORK MAP";
        float titleX = (screenW - font.width(title)) / 2;
        drawTextTESR(ps, buffers, font, title, titleX, y, TITLE_COLOR);
        y += 10;

        // Status line
        String status = trainCount + " trains | " + stationCount + " sta | " + signalCount + " sig";
        drawTextTESR(ps, buffers, font, status, 4, y, TEXT_DIM);

        if (derailed > 0) {
            String alert = "\u26A0 " + derailed + " DERAILED";
            float alertX = screenW - 4 - font.width(alert);
            drawTextTESR(ps, buffers, font, alert, alertX, y, ALERT_RED);
        } else if (moving > 0) {
            String mvs = "\u25B6 " + moving + " moving";
            float mvX = screenW - 4 - font.width(mvs);
            drawTextTESR(ps, buffers, font, mvs, mvX, y, TRAIN_MOVING);
        }

        y += 10;

        // === Map area ===
        float mapX = MAP_MARGIN;
        float mapY = y + 2;
        float mapW = screenW - MAP_MARGIN * 2;
        float mapH = screenH - mapY - MAP_MARGIN;

        if (mapW < 20 || mapH < 20) return;

        renderMapContent(ps, buffers, font, mapData, mapX, mapY, mapW, mapH,
                partialTick, true);
    }

    // ==================== GUI Rendering ====================

    /**
     * Render the CTC map in the GUI screen.
     * Uses GuiGraphics for 2D drawing.
     */
    public static void renderGUI(GuiGraphics gfx, Font font, CompoundTag mapData,
                                  int x, int y, int width, int height,
                                  int trainCount, int stationCount, int signalCount,
                                  int moving, int derailed, int mouseX, int mouseY,
                                  float zoom, float panX, float panY) {
        if (mapData == null || mapData.isEmpty()) {
            gfx.drawCenteredString(font, "Scanning network topology...", x + width / 2, y + height / 2, TRAIN_NAV);
            return;
        }

        // Scissor clip to map viewport
        gfx.enableScissor(x, y, x + width, y + height);
        renderMapContentGUI(gfx, font, mapData, x, y, width, height, mouseX, mouseY, zoom, panX, panY);
        gfx.disableScissor();
    }

    // ==================== Core Map Rendering (TESR path) ====================

    /**
     * Core map rendering — projects all topology elements into the map viewport.
     * Used by TESR path (PoseStack + MultiBufferSource).
     */
    private static void renderMapContent(PoseStack ps, MultiBufferSource buffers, Font font,
                                          CompoundTag mapData,
                                          float mapX, float mapY, float mapW, float mapH,
                                          float partialTick, boolean isTESR) {
        // Get bounds
        if (!mapData.contains("Bounds")) return;
        CompoundTag bounds = mapData.getCompound("Bounds");
        float minX = bounds.getFloat("minX");
        float maxX = bounds.getFloat("maxX");
        float minZ = bounds.getFloat("minZ");
        float maxZ = bounds.getFloat("maxZ");

        float worldW = maxX - minX;
        float worldH = maxZ - minZ;
        if (worldW < 1) worldW = 1;
        if (worldH < 1) worldH = 1;

        // Scale to fit with padding
        float padding = 10.0f;
        float availW = mapW - padding * 2;
        float availH = mapH - padding * 2;
        float scale = Math.min(availW / worldW, availH / worldH);

        // Center offset
        float offX = mapX + padding + (availW - worldW * scale) / 2;
        float offZ = mapY + padding + (availH - worldH * scale) / 2;

        // Compass indicator
        drawTextTESR(ps, buffers, font, "N", mapX + mapW - 10, mapY + 2, COMPASS_COLOR);
        drawTextTESR(ps, buffers, font, "\u2191", mapX + mapW - 10, mapY + 10, COMPASS_COLOR);

        // === Layer 1: Track edges (push slightly toward viewer for z-separation) ===
        ps.pushPose();
        ps.translate(0, 0, -0.01f);
        if (mapData.contains("Edges")) {
            ListTag nodes = mapData.getList("Nodes", 10);
            ListTag edges = mapData.getList("Edges", 10);

            // Build node position lookup
            float[] nodeX = new float[nodes.size()];
            float[] nodeZ = new float[nodes.size()];
            for (int i = 0; i < nodes.size(); i++) {
                CompoundTag n = nodes.getCompound(i);
                nodeX[i] = offX + (n.getFloat("x") - minX) * scale;
                nodeZ[i] = offZ + (n.getFloat("z") - minZ) * scale;
            }

            // Draw edges
            for (int i = 0; i < edges.size(); i++) {
                CompoundTag edge = edges.getCompound(i);
                int a = edge.getInt("a");
                int b = edge.getInt("b");
                if (a >= nodes.size() || b >= nodes.size()) continue;

                boolean occupied = edge.getBoolean("occupied");
                boolean interDim = edge.getBoolean("interDim");
                boolean curved = edge.getBoolean("curved");

                int color;
                if (occupied) color = TRACK_OCCUPIED;
                else if (interDim) color = TRACK_INTERDIM;
                else color = TRACK_CLEAR;

                float thickness = occupied ? TRACK_THICKNESS_TESR * 1.5f : TRACK_THICKNESS_TESR;

                if (curved && mapData.contains("Curves")) {
                    // Draw bezier curves using sampled points
                    drawCurvedEdgeTESR(ps, buffers, mapData, i, nodeX[a], nodeZ[a],
                            nodeX[b], nodeZ[b], color, thickness,
                            offX, offZ, minX, minZ, scale);
                } else {
                    drawLineTESR(ps, buffers, nodeX[a], nodeZ[a], nodeX[b], nodeZ[b],
                            color, thickness);
                }
            }
        }
        ps.popPose(); // end track layer z-push

        // === Layer 2: Navigation paths (route overlay) ===
        if (mapData.contains("Trains")) {
            ListTag trains = mapData.getList("Trains", 10);
            for (int i = 0; i < trains.size(); i++) {
                CompoundTag train = trains.getCompound(i);
                if (!train.contains("path")) continue;

                ListTag path = train.getList("path", 10);
                for (int p = 0; p < path.size(); p++) {
                    CompoundTag seg = path.getCompound(p);
                    float ax = offX + (seg.getFloat("ax") - minX) * scale;
                    float az = offZ + (seg.getFloat("az") - minZ) * scale;
                    float bx = offX + (seg.getFloat("bx") - minX) * scale;
                    float bz = offZ + (seg.getFloat("bz") - minZ) * scale;

                    // Dashed green route line
                    drawDashedLineTESR(ps, buffers, ax, az, bx, bz, TRACK_ROUTE, 0.6f,
                            partialTick);
                }
            }
        }

        // === Layer 3: Stations ===
        // Label collision avoidance: track bounding boxes of drawn labels
        List<float[]> drawnLabels = new ArrayList<>();
        if (mapData.contains("Stations")) {
            ListTag stations = mapData.getList("Stations", 10);
            for (int i = 0; i < stations.size(); i++) {
                CompoundTag station = stations.getCompound(i);
                float sx, sz;
                if (station.contains("mapX")) {
                    sx = offX + (station.getFloat("mapX") - minX) * scale;
                    sz = offZ + (station.getFloat("mapZ") - minZ) * scale;
                } else if (station.contains("x")) {
                    sx = offX + (station.getFloat("x") - minX) * scale;
                    sz = offZ + (station.getFloat("z") - minZ) * scale;
                } else continue;

                int fillColor;
                if (station.contains("trainPresent")) fillColor = STATION_PRESENT;
                else if (station.contains("trainImminent")) fillColor = STATION_IMMINENT;
                else fillColor = STATION_EMPTY;

                // Station berth rectangle
                drawRectTESR(ps, buffers, sx - STATION_HALF_W, sz - STATION_HALF_H,
                        STATION_HALF_W * 2, STATION_HALF_H * 2, fillColor);
                // Border
                drawRectOutlineTESR(ps, buffers, sx - STATION_HALF_W, sz - STATION_HALF_H,
                        STATION_HALF_W * 2, STATION_HALF_H * 2, STATION_BORDER, 0.3f);

                // Label with collision avoidance
                String name = station.getString("name");
                if (name.length() > 10) name = name.substring(0, 9) + "..";
                float labelW = font.width(name) * 0.5f; // half scale rendering
                float labelH = font.lineHeight * 0.5f;

                // Try positions: above, below, right, left
                float[][] offsets = {
                    {sx - labelW / 2f, sz - STATION_HALF_H - labelH - 2},  // above
                    {sx - labelW / 2f, sz + STATION_HALF_H + 2},           // below
                    {sx + STATION_HALF_W + 2, sz - labelH / 2f},           // right
                    {sx - STATION_HALF_W - labelW - 2, sz - labelH / 2f},  // left
                };

                float bestLX = offsets[0][0], bestLY = offsets[0][1];
                boolean placed = false;
                for (float[] off : offsets) {
                    float lx = off[0], ly = off[1];
                    boolean overlaps = false;
                    for (float[] existing : drawnLabels) {
                        if (lx < existing[0] + existing[2] && lx + labelW > existing[0] &&
                            ly < existing[1] + existing[3] && ly + labelH > existing[1]) {
                            overlaps = true;
                            break;
                        }
                    }
                    if (!overlaps) {
                        bestLX = lx;
                        bestLY = ly;
                        placed = true;
                        break;
                    }
                }

                if (placed) {
                    drawnLabels.add(new float[]{bestLX, bestLY, labelW, labelH});
                    ps.pushPose();
                    ps.translate(bestLX + labelW / 2f, bestLY + labelH / 2f, 0);
                    ps.scale(0.5f, 0.5f, 1.0f);
                    drawTextTESR(ps, buffers, font, name, -font.width(name) / 2f, -font.lineHeight / 2f, STATION_LABEL);
                    ps.popPose();
                }
                // else: skip label if all positions overlap — station dot is still visible
            }
        }

        // === Layer 4: Signals ===
        if (mapData.contains("Signals")) {
            ListTag signals = mapData.getList("Signals", 10);
            for (int i = 0; i < signals.size(); i++) {
                CompoundTag signal = signals.getCompound(i);
                float sx, sz;
                if (signal.contains("mapX")) {
                    sx = offX + (signal.getFloat("mapX") - minX) * scale;
                    sz = offZ + (signal.getFloat("mapZ") - minZ) * scale;
                } else if (signal.contains("x")) {
                    sx = offX + (signal.getFloat("x") - minX) * scale;
                    sz = offZ + (signal.getFloat("z") - minZ) * scale;
                } else continue;

                // Determine signal color from forward state
                String stateF = signal.getString("stateF");
                int dotColor = switch (stateF) {
                    case "green" -> SIGNAL_GREEN;
                    case "red" -> SIGNAL_RED;
                    case "yellow" -> SIGNAL_YELLOW;
                    default -> SIGNAL_OFF;
                };

                // Draw signal dot with direction offset
                float dirX = signal.contains("dirX") ? signal.getFloat("dirX") : 0;
                float dirZ = signal.contains("dirZ") ? signal.getFloat("dirZ") : 0;
                float perpX = -dirZ * 3;
                float perpZ = dirX * 3;

                drawCircleTESR(ps, buffers, sx + perpX, sz + perpZ, SIGNAL_RADIUS, dotColor);
            }
        }

        // === Layer 5: Observers ===
        if (mapData.contains("Observers")) {
            ListTag observers = mapData.getList("Observers", 10);
            for (int i = 0; i < observers.size(); i++) {
                CompoundTag obs = observers.getCompound(i);
                if (!obs.contains("x")) continue;

                float ox = offX + (obs.getFloat("x") - minX) * scale;
                float oz = offZ + (obs.getFloat("z") - minZ) * scale;
                boolean activated = obs.getBoolean("activated");

                int obsColor = activated ? TRAIN_NAV : TEXT_DIM;

                // Diamond shape
                drawDiamondTESR(ps, buffers, ox, oz, 2.0f, obsColor);
            }
        }

        // === Layer 6: Train positions (topmost) ===
        if (mapData.contains("Trains")) {
            ListTag trains = mapData.getList("Trains", 10);
            for (int i = 0; i < trains.size(); i++) {
                CompoundTag train = trains.getCompound(i);
                if (!train.contains("x")) continue;

                float tx = offX + (train.getFloat("x") - minX) * scale;
                float tz = offZ + (train.getFloat("z") - minZ) * scale;

                boolean isDerailed = train.getBoolean("derailed");
                double speed = Math.abs(train.getDouble("speed"));
                boolean navigating = train.getBoolean("navigating");

                int trainColor;
                if (isDerailed) trainColor = TRAIN_DERAIL;
                else if (speed > 0.01) trainColor = TRAIN_MOVING;
                else if (navigating) trainColor = TRAIN_NAV;
                else trainColor = TRAIN_STOPPED;

                // Train lozenge
                drawRectTESR(ps, buffers, tx - TRAIN_HALF_W, tz - TRAIN_HALF_H,
                        TRAIN_HALF_W * 2, TRAIN_HALF_H * 2, trainColor);

                // Name label
                String name = train.getString("name");
                if (name.length() > 6) name = name.substring(0, 5) + "..";

                ps.pushPose();
                ps.translate(tx, tz, 0);
                ps.scale(0.4f, 0.4f, 1.0f);
                drawTextTESR(ps, buffers, font, name,
                        -font.width(name) / 2f, -font.lineHeight / 2f, WHITE);
                ps.popPose();

                // Derailed warning flash
                if (isDerailed) {
                    float flash = (float)(Math.sin(partialTick * 4) * 0.5 + 0.5);
                    if (flash > 0.5f) {
                        drawRectOutlineTESR(ps, buffers, tx - TRAIN_HALF_W - 1, tz - TRAIN_HALF_H - 1,
                                TRAIN_HALF_W * 2 + 2, TRAIN_HALF_H * 2 + 2, ALERT_RED, 0.5f);
                    }
                }
            }
        }
    }

    // ==================== Core Map Rendering (GUI path) ====================

    /**
     * Core map rendering for the GUI screen using GuiGraphics.
     */
    private static void renderMapContentGUI(GuiGraphics gfx, Font font, CompoundTag mapData,
                                             int mapX, int mapY, int mapW, int mapH,
                                             int mouseX, int mouseY,
                                             float zoom, float panX, float panY) {
        if (!mapData.contains("Bounds")) {
            gfx.drawCenteredString(font, "No track data", mapX + mapW / 2, mapY + mapH / 2, TEXT_DIM);
            return;
        }

        CompoundTag bounds = mapData.getCompound("Bounds");
        float minX = bounds.getFloat("minX");
        float maxX = bounds.getFloat("maxX");
        float minZ = bounds.getFloat("minZ");
        float maxZ = bounds.getFloat("maxZ");

        float worldW = maxX - minX;
        float worldH = maxZ - minZ;
        if (worldW < 1) worldW = 1;
        if (worldH < 1) worldH = 1;

        float padding = 15.0f;
        float availW = mapW - padding * 2;
        float availH = mapH - padding * 2;
        // Apply zoom to the base scale
        float baseScale = Math.min(availW / worldW, availH / worldH);
        float scale = baseScale * zoom;

        // Center + pan offset
        float centerX = mapX + mapW / 2f + panX;
        float centerY = mapY + mapH / 2f + panY;
        float offX = centerX - (worldW * scale) / 2f;
        float offZ = centerY - (worldH * scale) / 2f;

        // Compass
        gfx.drawString(font, "N", mapX + mapW - 12, mapY + 2, COMPASS_COLOR, false);
        gfx.drawString(font, "^", mapX + mapW - 10, mapY + 10, COMPASS_COLOR, false);

        // === Track edges ===
        if (mapData.contains("Edges")) {
            ListTag nodes = mapData.getList("Nodes", 10);
            ListTag edges = mapData.getList("Edges", 10);

            float[] nodeXArr = new float[nodes.size()];
            float[] nodeZArr = new float[nodes.size()];
            for (int i = 0; i < nodes.size(); i++) {
                CompoundTag n = nodes.getCompound(i);
                nodeXArr[i] = offX + (n.getFloat("x") - minX) * scale;
                nodeZArr[i] = offZ + (n.getFloat("z") - minZ) * scale;
            }

            for (int i = 0; i < edges.size(); i++) {
                CompoundTag edge = edges.getCompound(i);
                int a = edge.getInt("a");
                int b = edge.getInt("b");
                if (a >= nodes.size() || b >= nodes.size()) continue;

                boolean occupied = edge.getBoolean("occupied");
                boolean interDim = edge.getBoolean("interDim");

                int color;
                if (occupied) color = TRACK_OCCUPIED;
                else if (interDim) color = TRACK_INTERDIM;
                else color = TRACK_CLEAR;

                drawLineGUI(gfx, (int) nodeXArr[a], (int) nodeZArr[a],
                        (int) nodeXArr[b], (int) nodeZArr[b], color);
            }
        }

        // === Stations with label collision avoidance ===
        List<int[]> guiDrawnLabels = new ArrayList<>(); // {x, y, w, h}
        if (mapData.contains("Stations")) {
            ListTag stations = mapData.getList("Stations", 10);
            for (int i = 0; i < stations.size(); i++) {
                CompoundTag station = stations.getCompound(i);
                float sx, sz;
                if (station.contains("mapX")) {
                    sx = offX + (station.getFloat("mapX") - minX) * scale;
                    sz = offZ + (station.getFloat("mapZ") - minZ) * scale;
                } else if (station.contains("x")) {
                    sx = offX + (station.getFloat("x") - minX) * scale;
                    sz = offZ + (station.getFloat("z") - minZ) * scale;
                } else continue;

                int fillColor;
                String tooltip = station.getString("name");
                if (station.contains("trainPresent")) {
                    fillColor = STATION_PRESENT;
                    tooltip += " - " + station.getString("trainPresent");
                } else if (station.contains("trainImminent")) {
                    fillColor = STATION_IMMINENT;
                    tooltip += " -> " + station.getString("trainImminent");
                } else {
                    fillColor = STATION_EMPTY;
                }

                int si = (int) sx, szi = (int) sz;
                int hw = 5, hh = 3;
                gfx.fill(si - hw, szi - hh, si + hw, szi + hh, fillColor);
                gfx.fill(si - hw, szi - hh, si + hw, szi - hh + 1, STATION_BORDER);
                gfx.fill(si - hw, szi + hh - 1, si + hw, szi + hh, STATION_BORDER);

                // Label with collision avoidance
                if (zoom >= 0.5f) {
                    String name = station.getString("name");
                    if (name.length() > 14) name = name.substring(0, 12) + "..";
                    int labelW = font.width(name);
                    int labelH = font.lineHeight;

                    // Try positions: above, below, right, left
                    int[][] positions = {
                        {si - labelW / 2, szi - hh - labelH - 1},  // above
                        {si - labelW / 2, szi + hh + 2},           // below
                        {si + hw + 3, szi - labelH / 2},           // right
                        {si - hw - labelW - 3, szi - labelH / 2},  // left
                    };

                    int bestX = positions[0][0], bestY = positions[0][1];
                    boolean placed = false;
                    for (int[] pos : positions) {
                        int lx = pos[0], ly = pos[1];
                        boolean overlaps = false;
                        for (int[] existing : guiDrawnLabels) {
                            if (lx < existing[0] + existing[2] && lx + labelW > existing[0] &&
                                ly < existing[1] + existing[3] && ly + labelH > existing[1]) {
                                overlaps = true;
                                break;
                            }
                        }
                        if (!overlaps) {
                            bestX = lx;
                            bestY = ly;
                            placed = true;
                            break;
                        }
                    }

                    if (placed) {
                        guiDrawnLabels.add(new int[]{bestX, bestY, labelW, labelH});
                        gfx.drawString(font, name, bestX, bestY, STATION_LABEL, false);
                    }
                    // else: skip label, station dot still visible; hover shows name
                }

                // Hover tooltip (always works regardless of label)
                if (mouseX >= si - hw && mouseX <= si + hw &&
                        mouseY >= szi - hh && mouseY <= szi + hh) {
                    gfx.fill(mouseX + 8, mouseY - 2, mouseX + 12 + font.width(tooltip),
                            mouseY + 11, 0xEE111122);
                    gfx.drawString(font, tooltip, mouseX + 10, mouseY, WHITE, false);
                }
            }
        }

        // === Signals ===
        if (mapData.contains("Signals")) {
            ListTag signals = mapData.getList("Signals", 10);
            for (int i = 0; i < signals.size(); i++) {
                CompoundTag signal = signals.getCompound(i);
                float sx, sz;
                if (signal.contains("mapX")) {
                    sx = offX + (signal.getFloat("mapX") - minX) * scale;
                    sz = offZ + (signal.getFloat("mapZ") - minZ) * scale;
                } else if (signal.contains("x")) {
                    sx = offX + (signal.getFloat("x") - minX) * scale;
                    sz = offZ + (signal.getFloat("z") - minZ) * scale;
                } else continue;

                String stateF = signal.getString("stateF");
                int color = switch (stateF) {
                    case "green" -> SIGNAL_GREEN;
                    case "red" -> SIGNAL_RED;
                    case "yellow" -> SIGNAL_YELLOW;
                    default -> SIGNAL_OFF;
                };

                float dirX = signal.contains("dirX") ? signal.getFloat("dirX") : 0;
                float dirZ = signal.contains("dirZ") ? signal.getFloat("dirZ") : 0;
                int px = (int) (sx - dirZ * 4);
                int pz = (int) (sz + dirX * 4);

                gfx.fill(px - 2, pz - 2, px + 2, pz + 2, color);
            }
        }

        // === Observers ===
        if (mapData.contains("Observers")) {
            ListTag observers = mapData.getList("Observers", 10);
            for (int i = 0; i < observers.size(); i++) {
                CompoundTag obs = observers.getCompound(i);
                if (!obs.contains("x")) continue;

                int ox = (int) (offX + (obs.getFloat("x") - minX) * scale);
                int oz = (int) (offZ + (obs.getFloat("z") - minZ) * scale);
                boolean activated = obs.getBoolean("activated");
                int color = activated ? TRAIN_NAV : TEXT_DIM;

                // Diamond
                gfx.fill(ox - 1, oz - 3, ox + 1, oz + 3, color);
                gfx.fill(ox - 3, oz - 1, ox + 3, oz + 1, color);
            }
        }

        // === Trains ===
        if (mapData.contains("Trains")) {
            ListTag trains = mapData.getList("Trains", 10);
            for (int i = 0; i < trains.size(); i++) {
                CompoundTag train = trains.getCompound(i);
                if (!train.contains("x")) continue;

                int tx = (int) (offX + (train.getFloat("x") - minX) * scale);
                int tz = (int) (offZ + (train.getFloat("z") - minZ) * scale);

                boolean isDerailed = train.getBoolean("derailed");
                double speed = Math.abs(train.getDouble("speed"));
                boolean navigating = train.getBoolean("navigating");

                int trainColor;
                if (isDerailed) trainColor = TRAIN_DERAIL;
                else if (speed > 0.01) trainColor = TRAIN_MOVING;
                else if (navigating) trainColor = TRAIN_NAV;
                else trainColor = TRAIN_STOPPED;

                // Lozenge
                int hw = 6, hh = 3;
                gfx.fill(tx - hw, tz - hh, tx + hw, tz + hh, trainColor);

                // Name
                String name = train.getString("name");
                if (name.length() > 8) name = name.substring(0, 7) + "..";
                if (zoom >= 0.7f) {
                    gfx.drawString(font, name, tx - font.width(name) / 2, tz + hh + 1, WHITE, false);
                }

                // Hover — detailed info
                if (mouseX >= tx - hw && mouseX <= tx + hw &&
                        mouseY >= tz - hh && mouseY <= tz + hh) {
                    String fullName = train.getString("name");
                    String info = fullName;
                    if (isDerailed) info += " - DERAILED";
                    else if (speed > 0.01) info += String.format(" - %.1f m/t", speed);
                    else info += " - stopped";
                    if (train.contains("destination"))
                        info += " \u2192 " + train.getString("destination");

                    gfx.fill(mouseX + 8, mouseY - 2, mouseX + 12 + font.width(info),
                            mouseY + 11, 0xEE111122);
                    gfx.drawString(font, info, mouseX + 10, mouseY, WHITE, false);
                }
            }

            // Navigation path overlay
            for (int i = 0; i < trains.size(); i++) {
                CompoundTag train = trains.getCompound(i);
                if (!train.contains("path")) continue;

                ListTag path = train.getList("path", 10);
                for (int p = 0; p < path.size(); p++) {
                    CompoundTag seg = path.getCompound(p);
                    int ax = (int) (offX + (seg.getFloat("ax") - minX) * scale);
                    int az = (int) (offZ + (seg.getFloat("az") - minZ) * scale);
                    int bx = (int) (offX + (seg.getFloat("bx") - minX) * scale);
                    int bz = (int) (offZ + (seg.getFloat("bz") - minZ) * scale);

                    drawLineGUI(gfx, ax, az, bx, bz, TRACK_ROUTE & 0x88FFFFFF);
                }
            }
        }

        // === Diagnostic markers (highlight problem areas) ===
        if (mapData.contains("Diagnostics")) {
            ListTag diagnostics = mapData.getList("Diagnostics", 10);
            long time = System.currentTimeMillis();
            float pulse = (float) (Math.sin(time * 0.005) * 0.3 + 0.7); // 0.4-1.0

            for (int i = 0; i < diagnostics.size(); i++) {
                CompoundTag diag = diagnostics.getCompound(i);
                if (!diag.contains("x")) continue;

                float dx = offX + (diag.getFloat("x") - minX) * scale;
                float dz = offZ + (diag.getFloat("z") - minZ) * scale;
                int cx = (int) dx, cz = (int) dz;

                String severity = diag.getString("severity");
                boolean isCrit = severity.equals("CRIT");
                int alpha = (int) (0xFF * pulse);
                int baseColor = isCrit ? 0xFF4242 : 0xFF9900;
                int markerColor = (alpha << 24) | baseColor;
                int solidColor = 0xFF000000 | baseColor;

                // Pulsing ring
                int ri = Math.max(8, Math.min(20, (int) (12 * zoom)));
                gfx.fill(cx - ri, cz - ri, cx + ri, cz - ri + 1, markerColor);
                gfx.fill(cx - ri, cz + ri - 1, cx + ri, cz + ri, markerColor);
                gfx.fill(cx - ri, cz - ri, cx - ri + 1, cz + ri, markerColor);
                gfx.fill(cx + ri - 1, cz - ri, cx + ri, cz + ri, markerColor);

                // Inner crosshair
                gfx.fill(cx - 3, cz, cx + 3, cz + 1, solidColor);
                gfx.fill(cx, cz - 3, cx + 1, cz + 3, solidColor);

                // Warning icon
                if (zoom >= 0.4f) {
                    String icon = isCrit ? "\u26A0" : "?";
                    gfx.drawString(font, icon, cx + ri + 2, cz - 4, solidColor, false);
                }

                // Hover tooltip with details
                if (mouseX >= cx - ri && mouseX <= cx + ri &&
                        mouseY >= cz - ri && mouseY <= cz + ri) {
                    String desc = diag.getString("desc");
                    int tipW = font.width(desc) + 8;
                    gfx.fill(mouseX + 8, mouseY - 4, mouseX + tipW + 8, mouseY + 22, 0xEE111122);
                    gfx.drawString(font, desc, mouseX + 12, mouseY - 2, WHITE, false);

                    // Show suggestions in tooltip
                    if (diag.contains("suggestions")) {
                        ListTag sug = diag.getList("suggestions", 10);
                        StringBuilder sb = new StringBuilder();
                        for (int s = 0; s < sug.size(); s++) {
                            CompoundTag sp = sug.getCompound(s);
                            if (s > 0) sb.append("  ");
                            String sigType = sp.contains("signalType") ? sp.getString("signalType") : "signal";
                            String prefix = switch (sigType) {
                                case "chain" -> "\u26D3 Chain";     // Chain link emoji
                                case "conflict" -> "\u2716 Conflict"; // X mark
                                default -> "\u2691 Signal";          // Flag
                            };
                            sb.append(prefix).append(" [").append(sp.getInt("sx")).append(" ")
                              .append(sp.getInt("sy")).append(" ")
                              .append(sp.getInt("sz")).append("]");
                            if (sp.contains("dir")) sb.append(" ").append(sp.getString("dir"));
                        }
                        int tipColor = diag.getString("type").equals("SIGNAL_CONFLICT") ? 0xFFFF4444 : SIGNAL_GREEN;
                        gfx.drawString(font, sb.toString(), mouseX + 12, mouseY + 9, tipColor, false);
                    }
                }

                // Draw suggestion markers (colored diamonds at placement coords)
                if (diag.contains("suggestions") && zoom >= 0.3f) {
                    ListTag sug = diag.getList("suggestions", 10);
                    for (int s = 0; s < sug.size(); s++) {
                        CompoundTag sp = sug.getCompound(s);
                        float sgx = offX + (sp.getInt("sx") - minX) * scale;
                        float sgz = offZ + (sp.getInt("sz") - minZ) * scale;
                        int sx = (int) sgx, sz = (int) sgz;

                        // Color by signal type: green=signal, cyan=chain, red=conflict
                        String sigType = sp.contains("signalType") ? sp.getString("signalType") : "signal";
                        int sugColor;
                        switch (sigType) {
                            case "chain" -> sugColor = 0xFF00DDEF;    // Cyan
                            case "conflict" -> sugColor = 0xFFFF2222; // Red
                            default -> sugColor = SIGNAL_GREEN;       // Green
                        };

                        // Diamond marker
                        gfx.fill(sx - 1, sz - 3, sx + 2, sz + 4, sugColor);
                        gfx.fill(sx - 3, sz - 1, sx + 4, sz + 2, sugColor);

                        // For conflicts, draw an X through the diamond
                        if (sigType.equals("conflict")) {
                            gfx.fill(sx - 2, sz - 2, sx + 3, sz - 1, sugColor);
                            gfx.fill(sx - 2, sz + 1, sx + 3, sz + 2, sugColor);
                        }
                    }
                }
            }
        }
    }

    // ==================== TESR Drawing Primitives ====================

    private static void drawTextTESR(PoseStack ps, MultiBufferSource buffers, Font font,
                                      String text, float x, float y, int color) {
        font.drawInBatch(text, x, y, color, false,
                ps.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, 0xF000F0);
    }

    private static void drawLineTESR(PoseStack ps, MultiBufferSource buffers,
                                      float x1, float y1, float x2, float y2,
                                      int color, float thickness) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(WHITE_TEXTURE));
        Matrix4f mat = ps.last().pose();

        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        // Calculate perpendicular for thickness
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;

        float nx = -dy / len * thickness * 0.5f;
        float ny = dx / len * thickness * 0.5f;

        int ol = OverlayTexture.NO_OVERLAY;
        vc.addVertex(mat, x1 + nx, y1 + ny, 0).setColor(r, g, b, a).setUv(0, 0).setOverlay(ol).setUv2(240, 240).setNormal(0, 0, -1);
        vc.addVertex(mat, x1 - nx, y1 - ny, 0).setColor(r, g, b, a).setUv(0, 1).setOverlay(ol).setUv2(240, 240).setNormal(0, 0, -1);
        vc.addVertex(mat, x2 - nx, y2 - ny, 0).setColor(r, g, b, a).setUv(1, 1).setOverlay(ol).setUv2(240, 240).setNormal(0, 0, -1);
        vc.addVertex(mat, x2 + nx, y2 + ny, 0).setColor(r, g, b, a).setUv(1, 0).setOverlay(ol).setUv2(240, 240).setNormal(0, 0, -1);
    }

    private static void drawDashedLineTESR(PoseStack ps, MultiBufferSource buffers,
                                            float x1, float y1, float x2, float y2,
                                            int color, float thickness, float partialTick) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;

        float dashLen = 3.0f;
        float gapLen = 2.0f;
        float cycle = dashLen + gapLen;

        float t = 0;
        while (t < len) {
            float tEnd = Math.min(t + dashLen, len);
            float sx = x1 + dx * (t / len);
            float sy = y1 + dy * (t / len);
            float ex = x1 + dx * (tEnd / len);
            float ey = y1 + dy * (tEnd / len);
            drawLineTESR(ps, buffers, sx, sy, ex, ey, color, thickness);
            t += cycle;
        }
    }

    private static void drawCurvedEdgeTESR(PoseStack ps, MultiBufferSource buffers,
                                            CompoundTag mapData, int edgeIndex,
                                            float startX, float startY,
                                            float endX, float endY,
                                            int color, float thickness,
                                            float offX, float offZ,
                                            float minWorldX, float minWorldZ, float scale) {
        // Find curve samples for this edge
        ListTag curves = mapData.getList("Curves", 10);
        ListTag curvePoints = null;

        for (int i = 0; i < curves.size(); i++) {
            CompoundTag c = curves.getCompound(i);
            if (c.getInt("edge") == edgeIndex) {
                curvePoints = c.getList("pts", 10);
                break;
            }
        }

        if (curvePoints == null || curvePoints.isEmpty()) {
            // Fallback to straight line
            drawLineTESR(ps, buffers, startX, startY, endX, endY, color, thickness);
            return;
        }

        // Draw segments: start → point0 → point1 → ... → end
        float prevX = startX, prevY = startY;
        for (int i = 0; i < curvePoints.size(); i++) {
            CompoundTag pt = curvePoints.getCompound(i);
            float px = offX + (pt.getFloat("x") - minWorldX) * scale;
            float py = offZ + (pt.getFloat("z") - minWorldZ) * scale;
            drawLineTESR(ps, buffers, prevX, prevY, px, py, color, thickness);
            prevX = px;
            prevY = py;
        }
        drawLineTESR(ps, buffers, prevX, prevY, endX, endY, color, thickness);
    }

    private static void drawRectTESR(PoseStack ps, MultiBufferSource buffers,
                                      float x, float y, float w, float h, int color) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(WHITE_TEXTURE));
        Matrix4f mat = ps.last().pose();

        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        int ol = OverlayTexture.NO_OVERLAY;
        vc.addVertex(mat, x, y, 0).setColor(r, g, b, a).setUv(0, 0).setOverlay(ol).setUv2(240, 240).setNormal(0, 0, -1);
        vc.addVertex(mat, x, y + h, 0).setColor(r, g, b, a).setUv(0, 1).setOverlay(ol).setUv2(240, 240).setNormal(0, 0, -1);
        vc.addVertex(mat, x + w, y + h, 0).setColor(r, g, b, a).setUv(1, 1).setOverlay(ol).setUv2(240, 240).setNormal(0, 0, -1);
        vc.addVertex(mat, x + w, y, 0).setColor(r, g, b, a).setUv(1, 0).setOverlay(ol).setUv2(240, 240).setNormal(0, 0, -1);
    }

    private static void drawRectOutlineTESR(PoseStack ps, MultiBufferSource buffers,
                                             float x, float y, float w, float h,
                                             int color, float thickness) {
        drawLineTESR(ps, buffers, x, y, x + w, y, color, thickness);
        drawLineTESR(ps, buffers, x + w, y, x + w, y + h, color, thickness);
        drawLineTESR(ps, buffers, x + w, y + h, x, y + h, color, thickness);
        drawLineTESR(ps, buffers, x, y + h, x, y, color, thickness);
    }

    private static void drawCircleTESR(PoseStack ps, MultiBufferSource buffers,
                                        float cx, float cy, float radius, int color) {
        // Approximate circle with a small square (sufficient at pixel scale)
        drawRectTESR(ps, buffers, cx - radius, cy - radius, radius * 2, radius * 2, color);
    }

    private static void drawDiamondTESR(PoseStack ps, MultiBufferSource buffers,
                                         float cx, float cy, float size, int color) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(WHITE_TEXTURE));
        Matrix4f mat = ps.last().pose();

        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        int ol = OverlayTexture.NO_OVERLAY;
        // Diamond = 4 triangles as quads with center
        vc.addVertex(mat, cx, cy - size, 0).setColor(r, g, b, a).setUv(0, 0).setOverlay(ol).setUv2(240, 240).setNormal(0, 0, -1);
        vc.addVertex(mat, cx - size, cy, 0).setColor(r, g, b, a).setUv(0, 1).setOverlay(ol).setUv2(240, 240).setNormal(0, 0, -1);
        vc.addVertex(mat, cx, cy + size, 0).setColor(r, g, b, a).setUv(1, 1).setOverlay(ol).setUv2(240, 240).setNormal(0, 0, -1);
        vc.addVertex(mat, cx + size, cy, 0).setColor(r, g, b, a).setUv(1, 0).setOverlay(ol).setUv2(240, 240).setNormal(0, 0, -1);
    }

    // ==================== GUI Drawing Primitives ====================

    /**
     * Draw a 2px thick line using GuiGraphics (Bresenham with thickness).
     * For long edges exceeding the step limit, draws a straight fill rect instead.
     */
    private static void drawLineGUI(GuiGraphics gfx, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);

        int steps = Math.max(dx, dy);
        if (steps < 1) {
            gfx.fill(x1, y1, x1 + 2, y1 + 2, color);
            return;
        }
        if (steps > 50000) return; // hard safety limit

        // For very long lines, use a simple filled rect approximation
        if (steps > 15000) {
            // Thick line via rect — works for mostly horizontal/vertical
            if (dx >= dy) {
                int sx = Math.min(x1, x2);
                int ex = Math.max(x1, x2);
                int sy = Math.min(y1, y2);
                int ey = Math.max(y1, y2);
                gfx.fill(sx, sy, ex + 1, ey + 2, color);
            } else {
                int sx = Math.min(x1, x2);
                int ex = Math.max(x1, x2);
                int sy = Math.min(y1, y2);
                int ey = Math.max(y1, y2);
                gfx.fill(sx, sy, ex + 2, ey + 1, color);
            }
            return;
        }

        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        int x = x1, y = y1;
        for (int i = 0; i <= steps; i++) {
            // Draw 2px wide line
            gfx.fill(x, y, x + 2, y + 2, color);
            if (x == x2 && y == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx) { err += dx; y += sy; }
        }
    }
}
