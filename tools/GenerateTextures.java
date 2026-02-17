import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Generates 16x16 pixel-art textures for CreateLogicLink blocks.
 * Each top texture gets the ∀ (turned A / universal quantifier) symbol
 * as the mod's theme branding.
 * 
 * Run: java tools/GenerateTextures.java
 */
public class GenerateTextures {

    static final String OUT = "src/main/resources/assets/logiclink/textures/block/";

    public static void main(String[] args) throws Exception {
        new File(OUT).mkdirs();

        generateLogicLinkTop();
        generateRedstoneControllerTop();
        generateForallOverlay();
        generateBookCover();
        generateContraptionRemoteTop();

        System.out.println("All textures generated!");
    }

    // ─── Logic Link Top ───────────────────────────────────────────────
    // Andesite-toned base with centered ∀ in brass/gold color
    static void generateLogicLinkTop() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

        // Base: andesite-inspired grey with subtle texture
        int[][] base = {
            {0xFF8B8B8B, 0xFF8A8A8A, 0xFF8E8E8E, 0xFF8C8C8C, 0xFF898989, 0xFF8D8D8D, 0xFF8A8A8A, 0xFF8E8E8E, 0xFF8C8C8C, 0xFF8B8B8B, 0xFF8E8E8E, 0xFF898989, 0xFF8D8D8D, 0xFF8A8A8A, 0xFF8C8C8C, 0xFF8B8B8B},
            {0xFF8A8A8A, 0xFF878787, 0xFF8B8B8B, 0xFF898989, 0xFF8C8C8C, 0xFF888888, 0xFF8B8B8B, 0xFF8A8A8A, 0xFF878787, 0xFF8C8C8C, 0xFF898989, 0xFF8B8B8B, 0xFF888888, 0xFF8C8C8C, 0xFF898989, 0xFF8A8A8A},
            {0xFF8E8E8E, 0xFF8B8B8B, 0xFF888888, 0xFF8C8C8C, 0xFF8A8A8A, 0xFF8D8D8D, 0xFF898989, 0xFF8B8B8B, 0xFF8E8E8E, 0xFF888888, 0xFF8C8C8C, 0xFF8A8A8A, 0xFF8D8D8D, 0xFF898989, 0xFF8B8B8B, 0xFF8E8E8E},
        };

        // Fill 16x16 with repeating andesite pattern
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                img.setRGB(x, y, base[y % 3][x]);
            }
        }

        // Add border detail - darker edges like Create's casing style
        int border = 0xFF7A7A7A;
        int highlight = 0xFF969696;
        for (int i = 0; i < 16; i++) {
            img.setRGB(i, 0, highlight);  // top highlight
            img.setRGB(0, i, highlight);  // left highlight
            img.setRGB(i, 15, border);    // bottom shadow
            img.setRGB(15, i, border);    // right shadow
        }

        // Draw ∀ symbol in brass/gold - centered, 8x10 pixels
        // The "turned A" = upside down A
        int gold  = 0xFFD4A833; // brass gold
        int goldH = 0xFFE0BC50; // highlight
        int goldD = 0xFFC09020; // shadow

        // ∀ pixel map (rows 3-12, cols 4-11) - an upside-down A
        // Row 3: wide top bar of the upside-down A
        setP(img, 5, 3, gold); setP(img, 6, 3, gold); setP(img, 7, 3, gold);
        setP(img, 8, 3, gold); setP(img, 9, 3, gold); setP(img, 10, 3, gold);

        // Row 4: legs start spreading
        setP(img, 5, 4, goldH); setP(img, 10, 4, goldH);

        // Row 5
        setP(img, 5, 5, gold); setP(img, 10, 5, gold);

        // Row 6: crossbar
        setP(img, 5, 6, gold); setP(img, 6, 6, goldD); setP(img, 7, 6, goldD);
        setP(img, 8, 6, goldD); setP(img, 9, 6, goldD); setP(img, 10, 6, gold);

        // Row 7
        setP(img, 5, 7, goldH); setP(img, 10, 7, goldH);

        // Row 8
        setP(img, 6, 8, gold); setP(img, 9, 8, gold);

        // Row 9
        setP(img, 6, 9, gold); setP(img, 9, 9, gold);

        // Row 10
        setP(img, 7, 10, goldH); setP(img, 8, 10, goldH);

        // Row 11: bottom point
        setP(img, 7, 11, gold); setP(img, 8, 11, gold);

        // Row 12: very tip
        setP(img, 7, 12, goldD);

        ImageIO.write(img, "png", new File(OUT + "logic_link_top.png"));
        System.out.println("  Created logic_link_top.png");
    }

    // ─── Redstone Controller Top ──────────────────────────────────────
    // Blackstone base with ∀ in red, and a small redstone link icon
    static void generateRedstoneControllerTop() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

        // Base: dark blackstone palette
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int v = 0x2D + ((x * 3 + y * 7) % 5);
                img.setRGB(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
            }
        }

        // Border
        int borderD = 0xFF1E1E1E;
        int borderH = 0xFF3A3A3A;
        for (int i = 0; i < 16; i++) {
            img.setRGB(i, 0, borderH);
            img.setRGB(0, i, borderH);
            img.setRGB(i, 15, borderD);
            img.setRGB(15, i, borderD);
        }

        // Draw ∀ in red tones (left-center area, cols 1-7, rows 2-11)
        int red  = 0xFFCC2222;
        int redH = 0xFFEE3333;
        int redD = 0xFFAA1818;

        // ∀ symbol - upside-down A, positioned left of center
        // Row 2: top bar
        setP(img, 2, 2, red); setP(img, 3, 2, red); setP(img, 4, 2, red);
        setP(img, 5, 2, red); setP(img, 6, 2, red);
        // Row 3: legs
        setP(img, 2, 3, redH); setP(img, 6, 3, redH);
        // Row 4
        setP(img, 2, 4, red); setP(img, 6, 4, red);
        // Row 5: crossbar
        setP(img, 2, 5, red); setP(img, 3, 5, redD); setP(img, 4, 5, redD);
        setP(img, 5, 5, redD); setP(img, 6, 5, red);
        // Row 6
        setP(img, 2, 6, redH); setP(img, 6, 6, redH);
        // Row 7
        setP(img, 3, 7, red); setP(img, 5, 7, red);
        // Row 8
        setP(img, 3, 8, red); setP(img, 5, 8, red);
        // Row 9: converge
        setP(img, 4, 9, redH);
        // Row 10: tip
        setP(img, 4, 10, red);

        // ─── Small Redstone Link icon (right side, ~5x5, rows 4-10, cols 10-14)
        // Create's Redstone Link looks like a small antenna/receiver on a base
        // Uses red and dark iron tones
        int iron   = 0xFF6B6B6B; // iron/andesite
        int ironD  = 0xFF555555;
        int ironH  = 0xFF808080;
        int rstone  = 0xFFDD0000; // redstone red
        int rstoneH = 0xFFFF2200;

        // Base platform (rows 9-10)
        setP(img, 10, 10, ironD); setP(img, 11, 10, iron); setP(img, 12, 10, iron); setP(img, 13, 10, ironD);
        setP(img, 10, 9, ironH); setP(img, 11, 9, iron); setP(img, 12, 9, iron); setP(img, 13, 9, ironH);

        // Vertical antenna post (rows 5-8, col 11-12)
        setP(img, 11, 8, iron); setP(img, 12, 8, iron);
        setP(img, 11, 7, ironH); setP(img, 12, 7, ironH);
        setP(img, 11, 6, iron); setP(img, 12, 6, iron);

        // Redstone receiver tip (rows 4-5)
        setP(img, 10, 5, rstone); setP(img, 11, 5, rstoneH); setP(img, 12, 5, rstoneH); setP(img, 13, 5, rstone);
        setP(img, 11, 4, rstone); setP(img, 12, 4, rstone);

        // Tiny redstone glow dots 
        setP(img, 10, 4, 0x66FF0000); // subtle glow
        setP(img, 13, 4, 0x66FF0000);

        ImageIO.write(img, "png", new File(OUT + "redstone_controller_top.png"));
        System.out.println("  Created redstone_controller_top.png");
    }

    // ─── ∀ Overlay (transparent) ──────────────────────────────────────
    // Can be layered on top of any texture
    static void generateForallOverlay() throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        // Fully transparent base
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++)
                img.setRGB(x, y, 0x00000000);

        int white = 0xCCFFFFFF; // semi-transparent white

        // ∀ symbol centered, same shape as logic_link_top
        setP(img, 5, 3, white); setP(img, 6, 3, white); setP(img, 7, 3, white);
        setP(img, 8, 3, white); setP(img, 9, 3, white); setP(img, 10, 3, white);
        setP(img, 5, 4, white); setP(img, 10, 4, white);
        setP(img, 5, 5, white); setP(img, 10, 5, white);
        setP(img, 5, 6, white); setP(img, 6, 6, white); setP(img, 7, 6, white);
        setP(img, 8, 6, white); setP(img, 9, 6, white); setP(img, 10, 6, white);
        setP(img, 5, 7, white); setP(img, 10, 7, white);
        setP(img, 6, 8, white); setP(img, 9, 8, white);
        setP(img, 6, 9, white); setP(img, 9, 9, white);
        setP(img, 7, 10, white); setP(img, 8, 10, white);
        setP(img, 7, 11, white); setP(img, 8, 11, white);
        setP(img, 7, 12, white);

        ImageIO.write(img, "png", new File(OUT + "forall_overlay.png"));
        System.out.println("  Created forall_overlay.png");
    }

    // ─── Book Cover Texture (for Patchouli image page) ────────────────
    // 256x256 image suitable for patchouli:image pages
    static void generateBookCover() throws Exception {
        String imgOut = "src/main/resources/assets/logiclink/textures/patchouli/";
        new File(imgOut).mkdirs();

        BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Dark background
        g.setColor(new Color(0x1A, 0x1A, 0x2E));
        g.fillRect(0, 0, 256, 256);

        // Decorative border
        g.setColor(new Color(0xD4, 0xA8, 0x33)); // brass gold
        g.setStroke(new BasicStroke(3));
        g.drawRect(8, 8, 239, 239);
        g.setStroke(new BasicStroke(1));
        g.drawRect(14, 14, 227, 227);

        // Large ∀ symbol in center
        g.setFont(new Font("Serif", Font.BOLD, 120));
        g.setColor(new Color(0xD4, 0xA8, 0x33));
        String symbol = "\u2200";
        FontMetrics fm = g.getFontMetrics();
        int sw = fm.stringWidth(symbol);
        int sx = (256 - sw) / 2;
        int sy = 128 + fm.getAscent() / 2 - 15;
        
        // Shadow
        g.setColor(new Color(0x80, 0x60, 0x10));
        g.drawString(symbol, sx + 2, sy + 2);
        // Main symbol
        g.setColor(new Color(0xD4, 0xA8, 0x33));
        g.drawString(symbol, sx, sy);
        // Highlight
        g.setColor(new Color(0xE8, 0xC8, 0x60, 100));
        g.drawString(symbol, sx - 1, sy - 1);

        // "CreateLogicLink" text at top
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        fm = g.getFontMetrics();
        String title = "CreateLogicLink";
        int tw = fm.stringWidth(title);
        g.setColor(new Color(0xD4, 0xA8, 0x33));
        g.drawString(title, (256 - tw) / 2, 42);

        // Subtitle at bottom
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        fm = g.getFontMetrics();
        String sub = "Factory Automation with Lua";
        int subw = fm.stringWidth(sub);
        g.setColor(new Color(0xA0, 0x80, 0x30));
        g.drawString(sub, (256 - subw) / 2, 230);

        g.dispose();
        ImageIO.write(img, "png", new File(imgOut + "book_cover.png"));
        System.out.println("  Created patchouli/book_cover.png");
    }

    // ─── Contraption Remote Top (32x32) ──────────────────────────────
    // Top-down view of a gamepad/controller sitting in the block tray.
    // The block model UV samples pixels x:1-25, y:0-16 (UV [0.5,0,12.5,8]).
    // Layout is designed so the full controller fits within that viewport.
    // 3D button/joystick elements overlay this — it serves as the body base.
    //
    // Visible viewport (24×16 px):
    //   y 0-1:   tray background above controller
    //   y 2:     body edge top
    //   y 3:     body highlight
    //   y 4-5:   ∀ branding + left stick area + XYAB area
    //   y 6-7:   left joystick + XYAB buttons center
    //   y 8:     start/select buttons
    //   y 9-10:  d-pad area + right joystick area
    //   y 11-12: d-pad center + right joystick
    //   y 13-14: body lower
    //   y 15:    body edge bottom
    //   y 16:    tray below controller
    static void generateContraptionRemoteTop() throws Exception {
        int W = 32;
        BufferedImage img = new BufferedImage(W, W, BufferedImage.TYPE_INT_ARGB);

        // --- Color palette (brighter for visibility on the block tray) ---
        int bodyBase   = 0xFF505050; // medium grey controller body
        int bodyLight  = 0xFF626262; // body highlight
        int bodyDark   = 0xFF3E3E3E; // body shadow/edge
        int bodyEdge   = 0xFF2A2A2A; // outline
        int trayBg     = 0xFF787878; // tray/casing background (lighter contrast)

        int stickBase  = 0xFF6A6A6A; // joystick base ring
        int stickTop   = 0xFF828282; // joystick cap
        int stickDot   = 0xFF969696; // joystick highlight dot
        int stickRim   = 0xFF585858; // joystick rim shadow

        int dpadCol    = 0xFF6E6E6E; // d-pad color
        int dpadLight  = 0xFF808080; // d-pad highlight
        int dpadDark   = 0xFF5A5A5A; // d-pad shadow
        int dpadCenter = 0xFF606060; // d-pad center indent

        int btnY       = 0xFFE8D848; // Yellow - Y (brighter)
        int btnX       = 0xFF4878E0; // Blue   - X (brighter)
        int btnB       = 0xFFE04848; // Red    - B (brighter)
        int btnA       = 0xFF48CC48; // Green  - A (brighter)
        int btnYH      = 0xFFF8E860; // Y highlight
        int btnXH      = 0xFF6090F0; // X highlight
        int btnBH      = 0xFFF06060; // B highlight
        int btnAH      = 0xFF60E060; // A highlight

        int startSel   = 0xFF707070; // start/select button
        int startSelH  = 0xFF808080; // highlight

        int brass      = 0xFFD4A833; // brass accent
        int brassD     = 0xFFC09020; // brass shadow

        // Fill entire image with tray background
        for (int y = 0; y < W; y++)
            for (int x = 0; x < W; x++)
                img.setRGB(x, y, trayBg);

        // === Controller body (compact, fits within viewport y:2-15, x:2-28) ===
        // Main body fill
        for (int y = 3; y <= 14; y++)
            for (int x = 3; x <= 28; x++)
                set32(img, x, y, bodyBase);
        // Rounded corners: top row
        for (int x = 4; x <= 27; x++) set32(img, x, 2, bodyBase);
        // Rounded corners: bottom row
        for (int x = 4; x <= 27; x++) set32(img, x, 15, bodyBase);

        // Left grip bulge (y:5-12, x:1-2)
        for (int y = 5; y <= 12; y++) set32(img, 2, y, bodyBase);
        for (int y = 6; y <= 11; y++) set32(img, 1, y, bodyBase);
        // Right grip bulge
        for (int y = 5; y <= 12; y++) set32(img, 29, y, bodyBase);
        for (int y = 6; y <= 11; y++) set32(img, 30, y, bodyBase);

        // --- Body shading ---
        for (int x = 4; x <= 27; x++) set32(img, x, 2, bodyLight);
        for (int x = 3; x <= 28; x++) set32(img, x, 3, bodyLight);
        for (int x = 4; x <= 27; x++) set32(img, x, 15, bodyDark);
        for (int x = 3; x <= 28; x++) set32(img, x, 14, bodyDark);

        // --- Body outline ---
        // Top/bottom edges
        for (int x = 4; x <= 27; x++) { set32(img, x, 1, bodyEdge); set32(img, x, 16, bodyEdge); }
        // Left/right edges
        for (int y = 3; y <= 14; y++) { set32(img, 2, y, bodyEdge); set32(img, 29, y, bodyEdge); }
        // Corner pixels
        set32(img, 3, 2, bodyEdge); set32(img, 28, 2, bodyEdge);
        set32(img, 3, 15, bodyEdge); set32(img, 28, 15, bodyEdge);
        // Grip outlines
        for (int y = 5; y <= 12; y++) set32(img, 1, y, bodyEdge);
        for (int y = 6; y <= 11; y++) set32(img, 0, y, bodyEdge);
        for (int y = 5; y <= 12; y++) set32(img, 30, y, bodyEdge);
        for (int y = 6; y <= 11; y++) set32(img, 31, y, bodyEdge);

        // === Left Joystick (center ~7, 6, radius 2) ===
        drawCircleFilled(img, 7, 6, 3, stickBase);
        drawCircleFilled(img, 7, 6, 2, stickTop);
        set32(img, 7, 5, stickDot); // highlight
        set32(img, 9, 8, stickRim); // shadow

        // === D-pad (center ~7, 11) ===
        // Vertical bar (y:9-13, x:7-8)
        for (int y = 9; y <= 13; y++) { set32(img, 7, y, dpadCol); set32(img, 8, y, dpadCol); }
        // Horizontal bar (x:5-10, y:10-11)
        for (int x = 5; x <= 10; x++) { set32(img, x, 10, dpadCol); set32(img, x, 11, dpadCol); }
        // Highlights (top & left)
        set32(img, 7, 9, dpadLight); set32(img, 8, 9, dpadLight);
        set32(img, 5, 10, dpadLight); set32(img, 5, 11, dpadLight);
        // Shadows (bottom & right)
        set32(img, 7, 13, dpadDark); set32(img, 8, 13, dpadDark);
        set32(img, 10, 10, dpadDark); set32(img, 10, 11, dpadDark);
        // Center indent
        set32(img, 7, 10, dpadCenter); set32(img, 8, 10, dpadCenter);
        set32(img, 7, 11, dpadCenter); set32(img, 8, 11, dpadCenter);

        // === XYAB Buttons (diamond, center ~22, 6) ===
        // Y top (yellow) 
        set32(img, 22, 4, btnYH); set32(img, 23, 4, btnY);
        // X left (blue)
        set32(img, 20, 6, btnXH); set32(img, 21, 6, btnX);
        // B right (red)
        set32(img, 24, 6, btnBH); set32(img, 25, 6, btnB);
        // A bottom (green)
        set32(img, 22, 8, btnAH); set32(img, 23, 8, btnA);

        // === Right Joystick (center ~23, 11, radius 2) ===
        drawCircleFilled(img, 23, 11, 3, stickBase);
        drawCircleFilled(img, 23, 11, 2, stickTop);
        set32(img, 23, 10, stickDot); // highlight
        set32(img, 25, 13, stickRim); // shadow

        // === Start / Select (center of body, y:8) ===
        // Start (left of center)
        set32(img, 13, 8, startSel); set32(img, 14, 8, startSelH);
        // Select (right of center)
        set32(img, 17, 8, startSel); set32(img, 18, 8, startSelH);

        // === Small ∀ branding logo (center-top, y:3-5) ===
        // Compact 4-wide ∀: top bar, legs, crossbar, converge, tip
        set32(img, 14, 3, brass); set32(img, 15, 3, brass); set32(img, 16, 3, brass); set32(img, 17, 3, brass);
        set32(img, 14, 4, brassD); set32(img, 17, 4, brassD);
        set32(img, 14, 5, brass); set32(img, 15, 5, brassD); set32(img, 16, 5, brassD); set32(img, 17, 5, brass);
        set32(img, 15, 6, brassD); set32(img, 16, 6, brassD);

        // === Decorative brass screw dots ===
        set32(img, 5, 3, brass); set32(img, 26, 3, brass);
        set32(img, 5, 14, brassD); set32(img, 26, 14, brassD);

        ImageIO.write(img, "png", new File(OUT + "contraption_remote_top.png"));
        System.out.println("  Created contraption_remote_top.png (32x32)");
    }

    /** Fill a circle at center (cx,cy) with given radius in a 32x32 image */
    static void drawCircleFilled(BufferedImage img, int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy <= r * r) {
                    set32(img, cx + dx, cy + dy, argb);
                }
            }
        }
    }

    static void setP(BufferedImage img, int x, int y, int argb) {
        if (x >= 0 && x < 16 && y >= 0 && y < 16) {
            img.setRGB(x, y, argb);
        }
    }

    static void set32(BufferedImage img, int x, int y, int argb) {
        if (x >= 0 && x < 32 && y >= 0 && y < 32) {
            img.setRGB(x, y, argb);
        }
    }
}
