package com.apocscode.logiclink.client;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Render-to-texture engine for the CTC train network map.
 *
 * Instead of drawing hundreds of overlapping vertex quads on the TESR (which causes
 * z-fighting, bleed artifacts, and merged parallel tracks), this renders the full
 * map to a high-resolution NativeImage pixel buffer and uploads it as a DynamicTexture.
 * The TESR then displays a single textured quad — zero z-fighting, higher resolution,
 * and parallel tracks render as distinct pixel rows.
 *
 * This is the same approach used by Xaero's minimap and similar mods.
 *
 * Resolution: 256 pixels per block (vs ~160 for TESR vertex quads), capped at 2048.
 * Redraws only when map data changes (hash-based dirty detection).
 */
public class TrainMapTexture {

    // ==================== Resolution ====================
    private static final int PX_PER_BLOCK = 256;
    private static final int MAX_TEX = 2048;
    private static final int MIN_TEX = 256;

    // ==================== Cache ====================
    private static final Map<String, TrainMapTexture> CACHE = new HashMap<>();
    private static long frameCounter = 0;

    // ==================== Color Palette (ARGB) ====================
    private static final int BG        = 0xFF2A2A2A;
    private static final int GRID      = 0xFF333333;

    private static final int TRK_CLEAR = 0xFFBBBBBB;
    private static final int TRK_OCC   = 0xFFFF4242;
    private static final int TRK_ROUTE = 0xFF33DD77;
    private static final int TRK_INTER = 0xFF8844FF;

    private static final int STA_EMPTY = 0xFF606060;
    private static final int STA_PRES  = 0xFF1AEA5F;
    private static final int STA_IMM   = 0xFFFF9900;
    private static final int STA_BDR   = 0xFF909090;

    private static final int SIG_G     = 0xFF1AEA5F;
    private static final int SIG_R     = 0xFFFF4242;
    private static final int SIG_Y     = 0xFFFF9900;
    private static final int SIG_OFF   = 0xFF555555;

    private static final int TRN_MOV   = 0xFF1AEA5F;
    private static final int TRN_STP   = 0xFF8B8B8B;
    private static final int TRN_DER   = 0xFFFF4242;
    private static final int TRN_NAV   = 0xFFFF9900;

    private static final int OBS_ON    = 0xFFFF9900;
    private static final int OBS_OFF   = 0xFF555555;

    // ==================== Instance State ====================
    private final int texW, texH;
    private NativeImage image;
    private DynamicTexture dynamicTexture;
    private ResourceLocation resLoc;
    private int lastHash = 0;
    private long lastUsedFrame = 0;
    private boolean disposed = false;

    // Coordinate mapping (set during redraw, used by text overlay positioning)
    private float mapMinX, mapMinZ;
    private float mapScale;
    private float mapOffX, mapOffZ; // pixel offset within texture (includes margin + centering)
    private boolean validBounds = false;

    // Debug counters — what was actually drawn last redraw
    private int drawnNodes, drawnEdges, drawnStations, drawnSignals, drawnTrains;

    // ==================== Constructor ====================

    private TrainMapTexture(int w, int h, String key) {
        this.texW = w;
        this.texH = h;
        this.image = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        this.dynamicTexture = new DynamicTexture(image);
        this.resLoc = ResourceLocation.fromNamespaceAndPath("logiclink", "train_map_" + key);
        Minecraft.getInstance().getTextureManager().register(resLoc, dynamicTexture);
    }

    // ==================== Public API ====================

    /**
     * Get or create a texture for the given master block position and monitor dimensions.
     * Handles caching, dimension changes, and stale texture eviction.
     */
    public static TrainMapTexture getOrCreate(BlockPos masterPos, int monW, int monH) {
        frameCounter++;
        String key = masterPos.getX() + "_" + masterPos.getY() + "_" + masterPos.getZ();

        // Evict unused textures periodically (every ~10 seconds at 60fps)
        if (frameCounter % 600 == 0) {
            CACHE.entrySet().removeIf(e -> {
                if (frameCounter - e.getValue().lastUsedFrame > 600) {
                    e.getValue().dispose();
                    return true;
                }
                return false;
            });
        }

        int tw = Math.min(monW * PX_PER_BLOCK, MAX_TEX);
        int th = Math.min(monH * PX_PER_BLOCK, MAX_TEX);
        tw = Math.max(tw, MIN_TEX);
        th = Math.max(th, MIN_TEX);

        TrainMapTexture tex = CACHE.get(key);
        if (tex != null && (tex.texW != tw || tex.texH != th || tex.disposed)) {
            tex.dispose();
            tex = null;
            CACHE.remove(key);
        }
        if (tex == null) {
            tex = new TrainMapTexture(tw, th, key);
            CACHE.put(key, tex);
        }
        tex.lastUsedFrame = frameCounter;
        return tex;
    }

    /** Check if the texture needs to be redrawn (data version changed). */
    public boolean needsRedraw(int version) {
        return version != lastHash;
    }

    /** @deprecated Use needsRedraw(int version) for O(1) check */
    public boolean needsRedraw(CompoundTag mapData) {
        int hash = mapData != null ? mapData.hashCode() : 0;
        return hash != lastHash;
    }

    /** Redraw the full map to the pixel buffer and upload to GPU. */
    public void redraw(CompoundTag mapData, int version) {
        lastHash = version;
        validBounds = false;
        drawnNodes = 0; drawnEdges = 0; drawnStations = 0; drawnSignals = 0; drawnTrains = 0;

        // Clear to background
        fillRect(0, 0, texW, texH, BG);

        // Subtle grid
        int gridSpacing = 32;
        for (int gx = 0; gx < texW; gx += gridSpacing)
            drawVLine(gx, 0, texH - 1, GRID);
        for (int gy = 0; gy < texH; gy += gridSpacing)
            drawHLine(0, texW - 1, gy, GRID);

        if (mapData == null || mapData.isEmpty() || !mapData.contains("Bounds")) {
            dynamicTexture.upload();
            return;
        }

        // Map bounds
        CompoundTag bounds = mapData.getCompound("Bounds");
        float minX = bounds.getFloat("minX");
        float maxX = bounds.getFloat("maxX");
        float minZ = bounds.getFloat("minZ");
        float maxZ = bounds.getFloat("maxZ");

        float worldW = maxX - minX;
        float worldH = maxZ - minZ;
        if (worldW < 1) worldW = 1;
        if (worldH < 1) worldH = 1;

        // Fit to texture with margin
        int margin = 24;
        int availW = texW - margin * 2;
        int availH = texH - margin * 2;
        float scale = Math.min((float) availW / worldW, (float) availH / worldH);

        float offX = margin + (availW - worldW * scale) / 2f;
        float offZ = margin + (availH - worldH * scale) / 2f;

        // Store for coordinate mapping
        this.mapMinX = minX;
        this.mapMinZ = minZ;
        this.mapScale = scale;
        this.mapOffX = offX;
        this.mapOffZ = offZ;
        this.validBounds = true;

        // === Layer 1: Track edges ===
        drawEdges(mapData, minX, minZ, scale, offX, offZ);

        // === Layer 2: Navigation route overlays ===
        drawNavRoutes(mapData, minX, minZ, scale, offX, offZ);

        // === Layer 3: Stations ===
        drawStations(mapData, minX, minZ, scale, offX, offZ);

        // === Layer 4: Signals ===
        drawSignals(mapData, minX, minZ, scale, offX, offZ);

        // === Layer 5: Observers ===
        drawObservers(mapData, minX, minZ, scale, offX, offZ);

        // === Layer 6: Trains ===
        drawTrains(mapData, minX, minZ, scale, offX, offZ);

        // Upload to GPU
        dynamicTexture.upload();
    }

    /** Get the ResourceLocation for this texture (for RenderType.entitySolid). */
    public ResourceLocation getResourceLocation() {
        return resLoc;
    }

    /** Whether coordinate mapping is valid (bounds were computed). */
    public boolean hasValidBounds() {
        return validBounds;
    }

    /**
     * Map a world X coordinate to a fraction (0..1) across the texture width.
     * Used by the TESR text overlay to position labels aligned with the texture content.
     */
    public float worldToFracX(float worldX) {
        if (!validBounds) return 0.5f;
        return (mapOffX + (worldX - mapMinX) * mapScale) / texW;
    }

    /**
     * Map a world Z coordinate to a fraction (0..1) across the texture height.
     */
    public float worldToFracZ(float worldZ) {
        if (!validBounds) return 0.5f;
        return (mapOffZ + (worldZ - mapMinZ) * mapScale) / texH;
    }

    /** Debug: get summary string of what was drawn. */
    public String getDebugInfo() {
        return drawnNodes + "n " + drawnEdges + "e " + drawnStations + "s " + drawnSignals + "sig " + drawnTrains + "t";
    }

    /** Release GPU resources. */
    public void dispose() {
        if (!disposed) {
            disposed = true;
            try {
                dynamicTexture.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** Clear all cached textures (call on dimension change or level unload). */
    public static void clearAll() {
        for (TrainMapTexture tex : CACHE.values()) {
            tex.dispose();
        }
        CACHE.clear();
    }

    // ==================== Map Layer Drawing ====================

    private void drawEdges(CompoundTag mapData, float minX, float minZ,
                           float scale, float offX, float offZ) {
        if (!mapData.contains("Edges")) return;

        ListTag nodes = mapData.getList("Nodes", 10);
        ListTag edges = mapData.getList("Edges", 10);

        float[] nodeXArr = new float[nodes.size()];
        float[] nodeZArr = new float[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            CompoundTag n = nodes.getCompound(i);
            nodeXArr[i] = offX + (n.getFloat("x") - minX) * scale;
            nodeZArr[i] = offZ + (n.getFloat("z") - minZ) * scale;
        }
        drawnNodes = nodes.size();

        for (int i = 0; i < edges.size(); i++) {
            CompoundTag edge = edges.getCompound(i);
            int a = edge.getInt("a");
            int b = edge.getInt("b");
            if (a >= nodes.size() || b >= nodes.size()) continue;

            boolean occupied = edge.getBoolean("occupied");
            boolean interDim = edge.getBoolean("interDim");
            boolean curved = edge.getBoolean("curved");

            int color;
            if (occupied) color = TRK_OCC;
            else if (interDim) color = TRK_INTER;
            else color = TRK_CLEAR;

            int thickness = occupied ? 4 : 3;

            drawnEdges++;

            if (curved && mapData.contains("Curves")) {
                drawCurvedEdge(mapData, i,
                        nodeXArr[a], nodeZArr[a], nodeXArr[b], nodeZArr[b],
                        color, thickness, offX, offZ, minX, minZ, scale);
            } else {
                drawThickLine((int) nodeXArr[a], (int) nodeZArr[a],
                        (int) nodeXArr[b], (int) nodeZArr[b], color, thickness);
            }
        }
    }

    private void drawCurvedEdge(CompoundTag mapData, int edgeIndex,
                                float sx, float sy, float ex, float ey,
                                int color, int thickness,
                                float offX, float offZ, float minX, float minZ, float scale) {
        ListTag curves = mapData.getList("Curves", 10);
        ListTag points = null;
        for (int i = 0; i < curves.size(); i++) {
            CompoundTag c = curves.getCompound(i);
            if (c.getInt("edge") == edgeIndex) {
                points = c.getList("pts", 10);
                break;
            }
        }

        if (points == null || points.isEmpty()) {
            drawThickLine((int) sx, (int) sy, (int) ex, (int) ey, color, thickness);
            return;
        }

        float prevX = sx, prevY = sy;
        for (int i = 0; i < points.size(); i++) {
            CompoundTag pt = points.getCompound(i);
            float px = offX + (pt.getFloat("x") - minX) * scale;
            float py = offZ + (pt.getFloat("z") - minZ) * scale;
            drawThickLine((int) prevX, (int) prevY, (int) px, (int) py, color, thickness);
            prevX = px;
            prevY = py;
        }
        drawThickLine((int) prevX, (int) prevY, (int) ex, (int) ey, color, thickness);
    }

    private void drawNavRoutes(CompoundTag mapData, float minX, float minZ,
                               float scale, float offX, float offZ) {
        if (!mapData.contains("Trains")) return;
        ListTag trains = mapData.getList("Trains", 10);
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
                drawDashedLine(ax, az, bx, bz, TRK_ROUTE, 2);
            }
        }
    }

    private void drawStations(CompoundTag mapData, float minX, float minZ,
                              float scale, float offX, float offZ) {
        if (!mapData.contains("Stations")) return;
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

            int fill;
            if (station.contains("trainPresent")) fill = STA_PRES;
            else if (station.contains("trainImminent")) fill = STA_IMM;
            else fill = STA_EMPTY;

            // Station berth rectangle (larger than TESR for visibility)
            int hw = 10, hh = 6;
            fillRect((int) sx - hw, (int) sz - hh, hw * 2, hh * 2, fill);
            drawRectOutline((int) sx - hw, (int) sz - hh, hw * 2, hh * 2, STA_BDR, 1);
            drawnStations++;
        }
    }

    private void drawSignals(CompoundTag mapData, float minX, float minZ,
                             float scale, float offX, float offZ) {
        if (!mapData.contains("Signals")) return;
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
                case "green" -> SIG_G;
                case "red" -> SIG_R;
                case "yellow" -> SIG_Y;
                default -> SIG_OFF;
            };

            float dirX = signal.contains("dirX") ? signal.getFloat("dirX") : 0;
            float dirZ = signal.contains("dirZ") ? signal.getFloat("dirZ") : 0;
            int px = (int) (sx - dirZ * 6);
            int pz = (int) (sz + dirX * 6);

            // Small square signal indicator
            int size = 3;
            fillRect(px - size, pz - size, size * 2, size * 2, color);
            drawnSignals++;
        }
    }

    private void drawObservers(CompoundTag mapData, float minX, float minZ,
                               float scale, float offX, float offZ) {
        if (!mapData.contains("Observers")) return;
        ListTag observers = mapData.getList("Observers", 10);
        for (int i = 0; i < observers.size(); i++) {
            CompoundTag obs = observers.getCompound(i);
            if (!obs.contains("x")) continue;
            int ox = (int) (offX + (obs.getFloat("x") - minX) * scale);
            int oz = (int) (offZ + (obs.getFloat("z") - minZ) * scale);
            boolean active = obs.getBoolean("activated");
            int color = active ? OBS_ON : OBS_OFF;
            drawDiamond(ox, oz, 5, color);
        }
    }

    private void drawTrains(CompoundTag mapData, float minX, float minZ,
                            float scale, float offX, float offZ) {
        if (!mapData.contains("Trains")) return;
        ListTag trains = mapData.getList("Trains", 10);
        for (int i = 0; i < trains.size(); i++) {
            CompoundTag train = trains.getCompound(i);
            if (!train.contains("x")) continue;

            int tx = (int) (offX + (train.getFloat("x") - minX) * scale);
            int tz = (int) (offZ + (train.getFloat("z") - minZ) * scale);

            boolean derailed = train.getBoolean("derailed");
            double speed = Math.abs(train.getDouble("speed"));
            boolean navigating = train.getBoolean("navigating");

            int color;
            if (derailed) color = TRN_DER;
            else if (speed > 0.01) color = TRN_MOV;
            else if (navigating) color = TRN_NAV;
            else color = TRN_STP;

            // Train lozenge
            int hw = 12, hh = 5;
            fillRect(tx - hw, tz - hh, hw * 2, hh * 2, color);

            // Bright outline for visibility
            drawRectOutline(tx - hw, tz - hh, hw * 2, hh * 2, 0xFFFFFFFF, 1);
            drawnTrains++;

            // Derailed: extra warning outline
            if (derailed) {
                drawRectOutline(tx - hw - 3, tz - hh - 3, hw * 2 + 6, hh * 2 + 6, TRN_DER, 2);
            }
        }
    }

    // ==================== Pixel Drawing Primitives ====================

    private void setPixel(int x, int y, int argb) {
        if (x < 0 || x >= texW || y < 0 || y >= texH) return;
        // NativeImage.setPixelRGBA takes ABGR format despite the method name
        image.setPixelRGBA(x, y, argbToAbgr(argb));
    }

    private static int argbToAbgr(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private void fillRect(int x, int y, int w, int h, int color) {
        int abgr = argbToAbgr(color);
        int x0 = Math.max(0, x);
        int y0 = Math.max(0, y);
        int x1 = Math.min(texW, x + w);
        int y1 = Math.min(texH, y + h);
        for (int py = y0; py < y1; py++) {
            for (int px = x0; px < x1; px++) {
                image.setPixelRGBA(px, py, abgr);
            }
        }
    }

    private void drawRectOutline(int x, int y, int w, int h, int color, int thickness) {
        for (int t = 0; t < thickness; t++) {
            drawHLine(x, x + w - 1, y + t, color);
            drawHLine(x, x + w - 1, y + h - 1 - t, color);
            drawVLine(x + t, y, y + h - 1, color);
            drawVLine(x + w - 1 - t, y, y + h - 1, color);
        }
    }

    private void drawHLine(int x0, int x1, int y, int color) {
        if (y < 0 || y >= texH) return;
        int start = Math.max(0, Math.min(x0, x1));
        int end = Math.min(texW - 1, Math.max(x0, x1));
        int abgr = argbToAbgr(color);
        for (int x = start; x <= end; x++) {
            image.setPixelRGBA(x, y, abgr);
        }
    }

    private void drawVLine(int x, int y0, int y1, int color) {
        if (x < 0 || x >= texW) return;
        int start = Math.max(0, Math.min(y0, y1));
        int end = Math.min(texH - 1, Math.max(y0, y1));
        int abgr = argbToAbgr(color);
        for (int y = start; y <= end; y++) {
            image.setPixelRGBA(x, y, abgr);
        }
    }

    /**
     * Draw a thick line using Bresenham with a filled square brush.
     * At 256px/block resolution, thickness 3-4 gives clean visible tracks.
     */
    private void drawThickLine(int x0, int y0, int x1, int y1, int color, int thickness) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int half = thickness / 2;

        int steps = Math.max(dx, dy);
        if (steps > 200000) return; // safety limit

        int abgr = argbToAbgr(color);
        int x = x0, y = y0;
        for (int i = 0; i <= steps; i++) {
            // Filled square brush for clean thick line
            int bx0 = Math.max(0, x - half);
            int by0 = Math.max(0, y - half);
            int bx1 = Math.min(texW - 1, x + half);
            int by1 = Math.min(texH - 1, y + half);
            for (int py = by0; py <= by1; py++) {
                for (int px = bx0; px <= bx1; px++) {
                    image.setPixelRGBA(px, py, abgr);
                }
            }
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx) { err += dx; y += sy; }
        }
    }

    private void drawDashedLine(int x0, int y0, int x1, int y1, int color, int thickness) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return;

        float dashLen = 8, gapLen = 5;
        float cycle = dashLen + gapLen;
        float t = 0;
        while (t < len) {
            float tEnd = Math.min(t + dashLen, len);
            int sx = (int) (x0 + dx * (t / len));
            int sy = (int) (y0 + dy * (t / len));
            int ex = (int) (x0 + dx * (tEnd / len));
            int ey = (int) (y0 + dy * (tEnd / len));
            drawThickLine(sx, sy, ex, ey, color, thickness);
            t += cycle;
        }
    }

    private void fillCircle(int cx, int cy, int radius, int color) {
        int abgr = argbToAbgr(color);
        int r2 = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy <= r2) {
                    int px = cx + dx, py = cy + dy;
                    if (px >= 0 && px < texW && py >= 0 && py < texH) {
                        image.setPixelRGBA(px, py, abgr);
                    }
                }
            }
        }
    }

    private void drawDiamond(int cx, int cy, int size, int color) {
        int abgr = argbToAbgr(color);
        for (int dy = -size; dy <= size; dy++) {
            for (int dx = -size; dx <= size; dx++) {
                if (Math.abs(dx) + Math.abs(dy) <= size) {
                    int px = cx + dx, py = cy + dy;
                    if (px >= 0 && px < texW && py >= 0 && py < texH) {
                        image.setPixelRGBA(px, py, abgr);
                    }
                }
            }
        }
    }
}
