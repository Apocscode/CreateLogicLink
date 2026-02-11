package com.apocscode.logiclink.client;

import com.apocscode.logiclink.block.TrainMonitorBlock;
import com.apocscode.logiclink.block.TrainMonitorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Block Entity Renderer (TESR) for the Train Network Monitor.
 * Renders a live display on the monitor face showing train status information.
 * Only the master block renders; it covers the full multi-block surface.
 *
 * Coordinate system after transform:
 *   Origin = top-left of display (viewer perspective)
 *   +X = viewer's right
 *   +Y = viewer's down
 *   +Z = into the screen (toward the block)
 */
public class TrainMonitorRenderer implements BlockEntityRenderer<TrainMonitorBlockEntity> {

    private static final ResourceLocation WHITE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    private final Font font;

    // Frame deduplication: prevent overdraw when multiple slaves all trigger master render
    private static long lastRenderedTime = 0;
    private static BlockPos lastRenderedMaster = null;

    // Colors — CRN-style industrial dark palette (ARGB)
    private static final int BG_COLOR       = 0xFF303030; // CRN surface gray
    private static final int BG_INNER       = 0xFF393939; // CRN panel bg
    private static final int BORDER_OUTER   = 0xFF575757; // CRN dark border
    private static final int BORDER_HILITE  = 0xFF6F6F6F; // CRN light edge
    private static final int BORDER_SHADOW  = 0xFF373737; // CRN shadow edge
    private static final int GREEN          = 0xFF1AEA5F; // CRN on-time green
    private static final int RED            = 0xFFFF4242; // CRN delayed red
    private static final int YELLOW         = 0xFFFF9900; // CRN notification orange
    private static final int GRAY           = 0xFF8B8B8B; // CRN neutral gray
    private static final int WHITE          = 0xFFEEEEEE;
    private static final int TITLE_COLOR    = 0xFFE8C84A; // brass/gold accent
    private static final int MODE_INDICATOR = 0xFF747474; // CRN neutral
    private static final int HEADER_BG      = 0xFF4F4F4F; // CRN title bar
    private static final int STATION_LABEL  = 0xFFDDDDEE; // near-white station label

    public TrainMonitorRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(TrainMonitorBlockEntity be, float partialTick, PoseStack ps,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {

        // --- Multi-block culling fix ---
        // Minecraft culls BER per chunk-section (16x16x16). For large multi-blocks
        // spanning several sections, the master's section may leave the frustum while
        // slaves are still visible. Solution: every block in the formation can trigger
        // the master's render by offsetting the PoseStack to master's position.
        TrainMonitorBlockEntity master;
        if (be.isMaster()) {
            master = be;
        } else {
            master = be.getMaster();
            if (master == null) return; // orphan slave or unloaded
            // Offset PoseStack from this slave's position to the master's position
            BlockPos myPos = be.getBlockPos();
            BlockPos mPos  = master.getBlockPos();
            ps.translate(mPos.getX() - myPos.getX(),
                         mPos.getY() - myPos.getY(),
                         mPos.getZ() - myPos.getZ());
        }

        // Frame deduplication: only render this master once per frame
        // Multiple slaves may trigger render() but we only need one actual draw
        long now = System.nanoTime();
        BlockPos masterPos = master.getBlockPos();
        if (masterPos.equals(lastRenderedMaster) && (now - lastRenderedTime) < 5_000_000L) {
            return; // already rendered this master within 5ms (same frame)
        }
        lastRenderedTime = now;
        lastRenderedMaster = masterPos;

        int monW = master.getMonitorWidth();
        int monH = master.getMonitorHeight();
        Direction facing = master.getBlockState().getValue(TrainMonitorBlock.FACING);

        ps.pushPose();

        // Transform so that screen (0,0) = top-left, +X = right, +Y = down
        applyFacingTransform(ps, facing);

        float totalW = monW;
        float totalH = monH;

        // === Layer 1: CRN-style background with beveled border ===
        // Outer border
        renderQuad(ps, buffers, 0, 0, totalW, totalH, BORDER_SHADOW);
        // Inner background
        float bdr = 0.03f; // border width in blocks
        renderQuad(ps, buffers, bdr, bdr, totalW - bdr * 2, totalH - bdr * 2, BG_COLOR);
        // Highlight edges (top + left = lighter)
        renderQuad(ps, buffers, bdr, bdr, totalW - bdr * 2, 0.015f, BORDER_HILITE);
        renderQuad(ps, buffers, bdr, bdr, 0.015f, totalH - bdr * 2, BORDER_HILITE);
        // Inner panel area
        float pad = 0.06f;
        renderQuad(ps, buffers, pad, pad, totalW - pad * 2, totalH - pad * 2, BG_INNER);

        // === Layer 2: Content (pushed 0.003 blocks toward viewer) ===
        ps.pushPose();
        ps.translate(0, 0, -0.003);

        int mode = master.getDisplayMode();
        if (mode == TrainMonitorBlockEntity.MODE_MAP) {
            // === Render-to-texture map (single textured quad, no z-fighting) ===
            renderMapTexture(master, ps, buffers, totalW, totalH, pad);

            // Text overlays on top of texture quad
            ps.pushPose();
            ps.translate(0, 0, -0.002);
            float pixelScale = 1.0f / 160.0f;
            ps.scale(pixelScale, pixelScale, pixelScale);
            float screenW = totalW / pixelScale;
            float screenH = totalH / pixelScale;
            renderMapOverlays(master, ps, buffers, screenW, screenH, totalW, totalH, pad);
            // Mode indicator
            String modeLabel = "[MAP]";
            float modeX = screenW - 4 - font.width(modeLabel);
            drawText(ps, buffers, modeLabel, modeX, screenH - 10, MODE_INDICATOR);
            ps.popPose();
        } else {
            // LIST mode with pixel-scale text
            float pixelScale = 1.0f / 160.0f;
            ps.pushPose();
            ps.scale(pixelScale, pixelScale, pixelScale);
            float screenW = totalW / pixelScale;
            float screenH = totalH / pixelScale;
            renderListMode(master, ps, buffers, screenW, screenH);
            String modeLabel = "[LIST]";
            float modeX = screenW - 4 - font.width(modeLabel);
            drawText(ps, buffers, modeLabel, modeX, screenH - 10, MODE_INDICATOR);
            ps.popPose();
        }

        ps.popPose(); // z-offset
        ps.popPose(); // facing transform
    }

    // ==================== MAP Mode (Render-to-Texture) ====================

    /**
     * Render the map as a single textured quad using the DynamicTexture approach.
     * All map geometry (tracks, stations, signals, trains) is pre-rendered to a
     * high-resolution NativeImage pixel buffer, eliminating z-fighting.
     */
    private void renderMapTexture(TrainMonitorBlockEntity be, PoseStack ps,
                                   MultiBufferSource buffers,
                                   float totalW, float totalH, float pad) {
        CompoundTag mapData = be.getMapData();
        TrainMapTexture tex = TrainMapTexture.getOrCreate(
                be.getBlockPos(), be.getMonitorWidth(), be.getMonitorHeight());

        if (tex.needsRedraw(mapData)) {
            tex.redraw(mapData);
        }

        // Render texture as a single quad filling the inner panel area
        ResourceLocation texLoc = tex.getResourceLocation();
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(texLoc));
        Matrix4f mat = ps.last().pose();
        int ol = OverlayTexture.NO_OVERLAY;

        float x = pad, y = pad;
        float w = totalW - pad * 2;
        float h = totalH - pad * 2;
        vc.addVertex(mat, x, y, 0).setColor(1f, 1f, 1f, 1f)
                .setUv(0, 0).setOverlay(ol).setUv2(240, 240).setNormal(0, 0, -1);
        vc.addVertex(mat, x, y + h, 0).setColor(1f, 1f, 1f, 1f)
                .setUv(0, 1).setOverlay(ol).setUv2(240, 240).setNormal(0, 0, -1);
        vc.addVertex(mat, x + w, y + h, 0).setColor(1f, 1f, 1f, 1f)
                .setUv(1, 1).setOverlay(ol).setUv2(240, 240).setNormal(0, 0, -1);
        vc.addVertex(mat, x + w, y, 0).setColor(1f, 1f, 1f, 1f)
                .setUv(1, 0).setOverlay(ol).setUv2(240, 240).setNormal(0, 0, -1);
    }

    /**
     * Render text overlays (title, status bar, station names) on top of the
     * texture map. Called at pixel scale (1/160) with z slightly in front.
     */
    private void renderMapOverlays(TrainMonitorBlockEntity be, PoseStack ps,
                                    MultiBufferSource buffers,
                                    float screenW, float screenH,
                                    float totalW, float totalH, float pad) {
        // Title
        String title = "NETWORK MAP";
        float titleX = (screenW - font.width(title)) / 2;
        drawText(ps, buffers, title, titleX, 2, TITLE_COLOR);

        // Status line
        float y = 12;
        String status = be.getTrainCount() + " trains | " + be.getStationCount() + " sta | " + be.getSignalCount() + " sig";
        drawText(ps, buffers, status, 4, y, GRAY);

        if (be.getTrainsDerailed() > 0) {
            String alert = "! " + be.getTrainsDerailed() + " DERAILED";
            float alertX = screenW - 4 - font.width(alert);
            drawText(ps, buffers, alert, alertX, y, RED);
        } else if (be.getTrainsMoving() > 0) {
            String mvs = "> " + be.getTrainsMoving() + " moving";
            float mvX = screenW - 4 - font.width(mvs);
            drawText(ps, buffers, mvs, mvX, y, GREEN);
        }

        // Station labels positioned to align with texture content
        CompoundTag mapData = be.getMapData();
        TrainMapTexture tex = TrainMapTexture.getOrCreate(
                be.getBlockPos(), be.getMonitorWidth(), be.getMonitorHeight());

        if (tex.hasValidBounds() && mapData != null && mapData.contains("Stations")) {
            float pixelScale = 1.0f / 160.0f;
            float mapPxX = pad / pixelScale;
            float mapPxY = pad / pixelScale;
            float mapPxW = (totalW - 2 * pad) / pixelScale;
            float mapPxH = (totalH - 2 * pad) / pixelScale;

            List<float[]> labels = new ArrayList<>();
            ListTag stations = mapData.getList("Stations", 10);
            for (int i = 0; i < stations.size(); i++) {
                CompoundTag station = stations.getCompound(i);
                float worldX, worldZ;
                if (station.contains("mapX")) {
                    worldX = station.getFloat("mapX");
                    worldZ = station.getFloat("mapZ");
                } else if (station.contains("x")) {
                    worldX = station.getFloat("x");
                    worldZ = station.getFloat("z");
                } else continue;

                float fx = tex.worldToFracX(worldX);
                float fz = tex.worldToFracZ(worldZ);
                float sx = mapPxX + fx * mapPxW;
                float sz = mapPxY + fz * mapPxH;

                String name = station.getString("name");
                if (name.length() > 10) name = name.substring(0, 9) + "..";

                float labelW = font.width(name) * 0.5f;
                float labelH = font.lineHeight * 0.5f;

                // Collision avoidance: try above, below, right, left
                float[][] positions = {
                    {sx - labelW / 2f, sz - 8 - labelH},
                    {sx - labelW / 2f, sz + 7},
                    {sx + 8, sz - labelH / 2f},
                    {sx - 8 - labelW, sz - labelH / 2f},
                };

                boolean placed = false;
                float bestX = positions[0][0], bestY = positions[0][1];
                for (float[] pos : positions) {
                    boolean overlaps = false;
                    for (float[] existing : labels) {
                        if (pos[0] < existing[0] + existing[2] && pos[0] + labelW > existing[0] &&
                            pos[1] < existing[1] + existing[3] && pos[1] + labelH > existing[1]) {
                            overlaps = true;
                            break;
                        }
                    }
                    if (!overlaps) {
                        bestX = pos[0]; bestY = pos[1];
                        placed = true;
                        break;
                    }
                }

                if (placed) {
                    labels.add(new float[]{bestX, bestY, labelW, labelH});
                    ps.pushPose();
                    ps.translate(bestX + labelW / 2f, bestY + labelH / 2f, 0);
                    ps.scale(0.5f, 0.5f, 1.0f);
                    drawText(ps, buffers, name,
                            -font.width(name) / 2f, -font.lineHeight / 2f, STATION_LABEL);
                    ps.popPose();
                }
            }
        }

        // Debug info overlay (bottom-left corner)
        if (tex != null) {
            String dbg = tex.getDebugInfo();
            ps.pushPose();
            ps.scale(0.5f, 0.5f, 1.0f);
            drawText(ps, buffers, dbg, 4, (screenH - 6) * 2, GRAY);
            ps.popPose();
        }
    }

    // ==================== LIST Mode ====================

    private void renderListMode(TrainMonitorBlockEntity be,
                                 PoseStack ps, MultiBufferSource buffers,
                                 float screenW, float screenH) {
        float margin = 4;

        // Title
        String title = "TRAIN NETWORK";
        float titleX = (screenW - font.width(title)) / 2;
        drawText(ps, buffers, title, titleX, margin, TITLE_COLOR);

        // Overview line
        float lineY = margin + 12;
        int total = be.getTrainCount();
        int stations = be.getStationCount();
        int signals = be.getSignalCount();
        int moving = be.getTrainsMoving();
        int derailed = be.getTrainsDerailed();

        String overview = total + " trains";
        if (stations > 0) overview += " | " + stations + " stations";
        if (signals > 0) overview += " | " + signals + " signals";
        drawText(ps, buffers, overview, margin, lineY, GRAY);

        lineY += 11;

        // Status indicators
        if (total > 0) {
            float oX = margin;
            if (moving > 0) {
                String mv = "\u25CF " + moving + " moving";
                drawText(ps, buffers, mv, oX, lineY, GREEN);
                oX += font.width(mv) + 8;
            }
            if (derailed > 0) {
                String dr = "\u26A0 " + derailed + " derailed";
                drawText(ps, buffers, dr, oX, lineY, RED);
            }
            lineY += 11;
        }

        // Separator line
        lineY += 2;

        // Train list
        List<CompoundTag> trains = be.getTrainDataList();

        if (trains.isEmpty() && total == 0) {
            String msg = "Connecting to train network...";
            float msgX = (screenW - font.width(msg)) / 2;
            drawText(ps, buffers, msg, msgX, lineY + 10, YELLOW);
        } else {
            int maxLines = (int) ((screenH - lineY - margin) / 10);
            int shown = Math.min(trains.size(), maxLines);

            for (int i = 0; i < shown; i++) {
                CompoundTag train = trains.get(i);
                boolean isDerailed = train.getBoolean("derailed");
                double speed = Math.abs(train.getDouble("speed"));

                int dotColor;
                if (isDerailed) dotColor = RED;
                else if (speed > 0.01) dotColor = GREEN;
                else dotColor = GRAY;

                String dot = isDerailed ? "\u25CF" : (speed > 0.01 ? "\u25B6" : "\u25A0");
                drawText(ps, buffers, dot, margin, lineY, dotColor);

                // Train name (left side)
                String name = train.getString("name");
                int maxNameWidth = (int)(screenW * 0.45f);
                if (font.width(name) > maxNameWidth) {
                    while (font.width(name + "..") > maxNameWidth && name.length() > 1) {
                        name = name.substring(0, name.length() - 1);
                    }
                    name += "..";
                }
                drawText(ps, buffers, name, margin + 10, lineY, WHITE);

                // Destination/station (right side)
                String info = "";
                int infoColor = GRAY;
                if (isDerailed) {
                    info = "DERAILED";
                    infoColor = RED;
                } else if (train.contains("currentStation")) {
                    info = "@ " + train.getString("currentStation");
                    infoColor = GREEN;
                } else if (train.contains("destination")) {
                    info = "\u2192 " + train.getString("destination");
                    infoColor = YELLOW;
                }

                if (!info.isEmpty()) {
                    int maxInfoWidth = (int)(screenW * 0.45f);
                    if (font.width(info) > maxInfoWidth) {
                        while (font.width(info + "..") > maxInfoWidth && info.length() > 1) {
                            info = info.substring(0, info.length() - 1);
                        }
                        info += "..";
                    }
                    float infoX = screenW - margin - font.width(info);
                    drawText(ps, buffers, info, infoX, lineY, infoColor);
                }

                lineY += 10;
            }

            if (trains.size() > shown) {
                String more = "... +" + (trains.size() - shown) + " more";
                drawText(ps, buffers, more, margin, lineY, GRAY);
            }
        }
    }

    /**
     * Draw text using POLYGON_OFFSET mode to prevent z-fighting with the background quad.
     */
    private void drawText(PoseStack ps, MultiBufferSource buffers,
                          String text, float x, float y, int color) {
        font.drawInBatch(text, x, y, color, false,
                ps.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, 0xF000F0);
    }

    /**
     * Transform PoseStack so that rendering in the XY plane (z=0) appears on
     * the monitor's front face. After this transform:
     *   (0, 0) = top-left of screen from viewer
     *   +X     = viewer's right
     *   +Y     = viewer's down (font.drawInBatch convention)
     *   -Z     = toward the viewer (for correct face culling/normals)
     *
     * Master is always at the top-left of the display from the viewer's perspective.
     * Width extends in the viewer's right direction, height extends downward.
     *
     * Transforms derived mathematically:
     *   SOUTH: screen +X = world +X (east),  screen +Y = world -Y (down)
     *   NORTH: screen +X = world -X (west),  screen +Y = world -Y (down)
     *   EAST:  screen +X = world -Z (north), screen +Y = world -Y (down)
     *   WEST:  screen +X = world +Z (south), screen +Y = world -Y (down)
     */
    private void applyFacingTransform(PoseStack ps, Direction facing) {
        float offset = 0.02f; // Offset from block face to prevent z-fighting

        switch (facing) {
            case SOUTH:
                // Screen at z=1, player looks from south toward -Z
                // Origin top-left = (0, 1, 1+offset)
                ps.translate(0, 1, 1.0 + offset);
                ps.mulPose(Axis.XP.rotationDegrees(180));
                break;

            case NORTH:
                // Screen at z=0, player looks from north toward +Z
                // Origin top-left = (1, 1, -offset)
                ps.translate(1, 1, -offset);
                ps.mulPose(Axis.YP.rotationDegrees(180));
                ps.mulPose(Axis.XP.rotationDegrees(180));
                break;

            case EAST:
                // Screen at x=1, player looks from east toward -X
                // Origin top-left = (1+offset, 1, 1)
                ps.translate(1.0 + offset, 1, 1);
                ps.mulPose(Axis.YP.rotationDegrees(90));
                ps.mulPose(Axis.XP.rotationDegrees(180));
                break;

            case WEST:
                // Screen at x=0, player looks from west toward +X
                // Origin top-left = (-offset, 1, 0)
                ps.translate(-offset, 1, 0);
                ps.mulPose(Axis.YP.rotationDegrees(-90));
                ps.mulPose(Axis.XP.rotationDegrees(180));
                break;

            default:
                // Fallback (UP/DOWN no longer used)
                ps.translate(0, 1, 1.0 + offset);
                ps.mulPose(Axis.XP.rotationDegrees(180));
                break;
        }
    }

    /**
     * Render a colored quad on the display surface.
     */
    private void renderQuad(PoseStack ps, MultiBufferSource buffers,
                            float x, float y, float w, float h, int color) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(WHITE_TEXTURE));
        Matrix4f mat = ps.last().pose();

        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        int overlay = OverlayTexture.NO_OVERLAY;
        // Quad with normal facing outward (toward viewer = -Z in transformed space)
        vc.addVertex(mat, x,     y,     0).setColor(r, g, b, a).setUv(0, 0).setOverlay(overlay).setUv2(240, 240).setNormal(0, 0, -1);
        vc.addVertex(mat, x,     y + h, 0).setColor(r, g, b, a).setUv(0, 1).setOverlay(overlay).setUv2(240, 240).setNormal(0, 0, -1);
        vc.addVertex(mat, x + w, y + h, 0).setColor(r, g, b, a).setUv(1, 1).setOverlay(overlay).setUv2(240, 240).setNormal(0, 0, -1);
        vc.addVertex(mat, x + w, y,     0).setColor(r, g, b, a).setUv(1, 0).setOverlay(overlay).setUv2(240, 240).setNormal(0, 0, -1);
    }

    /**
     * Must return true for EVERY block in the multi-block formation so that
     * any visible slave can trigger the master's display render.
     */
    @Override
    public boolean shouldRenderOffScreen(TrainMonitorBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
