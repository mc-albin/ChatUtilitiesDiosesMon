package chatutilities.ui;

import chatutilities.core.config.ModConfig;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Active colour palette, rounded shapes and drawing helpers. ARGB colours.
 * <p>
 * The palette fields are not constants: {@link #apply()} rebuilds them from the theme chosen in
 * the configuration (a preset or the user's own RGB accent and background), so every screen and
 * HUD element follows the theme as soon as it changes.
 */
public final class Theme {
    /** Built-in themes. Each one is an accent colour on a dark background (RGB, no alpha). */
    public enum Preset {
        OCEANO("Océano", 0x5CC8FF, 0x06121F),
        ESMERALDA("Esmeralda", 0x5CE6A0, 0x061A10),
        AMATISTA("Amatista", 0xC79BFF, 0x140A22),
        CARMESI("Carmesí", 0xFF6B7F, 0x1E070B),
        SAKURA("Sakura", 0xFF9ED2, 0x1F0A16),
        NOCHE("Noche", 0xD8DEE9, 0x111418),
        /** The user's own accent and background (ModConfig.customAccent / customBase). */
        CUSTOM("Personalizado", 0x5CC8FF, 0x06121F);

        public final String label;
        public final int accent;
        public final int base;

        Preset(String label, int accent, int base) {
            this.label = label;
            this.accent = accent;
            this.base = base;
        }

        public static Preset of(String name) {
            for (Preset p : values()) {
                if (p.name().equalsIgnoreCase(name)) {
                    return p;
                }
            }
            return DEFAULT;
        }

        /** Theme used by a new install or when the saved one no longer exists. */
        public static final Preset DEFAULT = OCEANO;
    }

    /** Primary highlight. */
    public static int ACCENT = 0xFFFFCE5C;
    public static int ACCENT_SOFT = 0x40FFCE5C;
    /** Lighter accent used for hovered accent buttons. */
    public static int ACCENT_HOVER = 0xFFFFE2A0;
    /** Text drawn on top of an ACCENT fill. */
    public static int ON_ACCENT = 0xFF1A1404;
    public static int TEXT = 0xFFFFEAB8;
    public static int BRIGHT = 0xFFFFFAEE;
    public static int MUTED = 0xFFDBC184;
    /** Secondary text one step dimmer than MUTED. */
    public static int DIM = 0xFFB89A5E;
    public static int FAINT = 0xFF7B5F1F;
    /** Text of public player messages, a little dimmer than TEXT. */
    public static int PLAYER_TEXT = 0xFFE8D7AE;
    public static final int SUCCESS = 0xFF4ADE80;
    public static final int DANGER = 0xFFFF3D63;
    public static final int DANGER_SOFT = 0xFFFF8FB0;
    public static final int WARN = 0xFFFBBF24;
    public static final int INFO = 0xFF7DD3FC;
    public static int GOLD = 0xFFFFC94C;
    /** Dark base used by panels, toasts and the HUD (RGB, no alpha). */
    public static int BASE = 0x1A1404;
    /** Border colour (RGB, no alpha); BORDER and BORDER_STRONG add the alpha. */
    public static int EDGE = 0xE0AF3D;
    public static int BORDER = 0x40E0AF3D;
    public static int BORDER_STRONG = 0x99E0AF3D;
    public static int CARD = 0x66261E0A;
    public static int CARD_HOVER = 0x1FFFE2A0;
    public static int SELECT = 0x33FFCE5C;
    public static int FIELD = 0xB3181204;

    public static final int R_PANEL = 6;
    public static final int R_CARD = 4;
    public static final int R_SMALL = 3;

    static {
        derive(Preset.DEFAULT.accent, Preset.DEFAULT.base);
    }

    private Theme() {
    }

    /**
     * Parses "#RRGGBB", "RRGGBB" or the short "#RGB" form into 0xRRGGBB.
     * Returns -1 when the text is not a complete colour.
     */
    public static int parseHex(String text) {
        if (text == null) {
            return -1;
        }
        String h = text.strip();
        if (h.startsWith("#")) {
            h = h.substring(1);
        }
        if (h.length() == 3) {
            h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
        }
        if (!h.matches("[0-9A-Fa-f]{6}")) {
            return -1;
        }
        return Integer.parseInt(h, 16);
    }

    /** "#RRGGBB" for an RGB colour. */
    public static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    /** Rebuilds the palette from the configuration. Call after loading or changing the theme. */
    public static void apply() {
        ModConfig cfg = ModConfig.get();
        Preset p = Preset.of(cfg.theme);
        if (p == Preset.CUSTOM) {
            derive(cfg.customAccent, cfg.customBase);
        } else {
            derive(p.accent, p.base);
        }
    }

    /** Accent and background of the active theme (RGB), used to seed the custom colours. */
    public static int[] current() {
        return new int[]{ACCENT & 0xFFFFFF, BASE & 0xFFFFFF};
    }

    /** Builds a whole palette from an accent and a background colour (both RGB). */
    public static void derive(int accent, int base) {
        int a = accent & 0xFFFFFF;
        int b = base & 0xFFFFFF;
        boolean darkBase = luminance(b) < 0.35;
        // On a light background the text must be dark, so the "towards white" mixes go towards black.
        int ink = darkBase ? 0xFFFFFF : 0x000000;
        ACCENT = 0xFF000000 | a;
        ACCENT_SOFT = 0x40000000 | a;
        ACCENT_HOVER = 0xFF000000 | mix(a, 0xFFFFFF, 0.45);
        ON_ACCENT = 0xFF000000 | (luminance(a) > 0.5 ? (darkBase ? b : 0x101010) : 0xFFFFFF);
        TEXT = 0xFF000000 | mix(a, ink, 0.57);
        BRIGHT = 0xFF000000 | mix(a, ink, 0.9);
        MUTED = 0xFF000000 | (darkBase ? scale(mix(a, ink, 0.35), 0.86) : mix(a, ink, 0.45));
        DIM = 0xFF000000 | (darkBase ? scale(mix(a, ink, 0.15), 0.72) : mix(a, b, 0.25));
        FAINT = 0xFF000000 | mix(a, b, 0.55);
        PLAYER_TEXT = 0xFF000000 | mix(TEXT & 0xFFFFFF, b, 0.09);
        GOLD = ACCENT;
        BASE = b;
        EDGE = scale(a, 0.88);
        BORDER = 0x40000000 | EDGE;
        BORDER_STRONG = 0x99000000 | EDGE;
        CARD = 0x66000000 | mix(b, a, 0.05);
        CARD_HOVER = 0x1F000000 | mix(a, 0xFFFFFF, 0.45);
        SELECT = 0x33000000 | a;
        FIELD = 0xB3000000 | (darkBase ? scale(b, 0.92) : mix(b, 0x000000, 0.06));
    }

    /** Relative luminance 0..1 of an RGB colour. */
    public static double luminance(int rgb) {
        return (0.2126 * ((rgb >> 16) & 0xFF) + 0.7152 * ((rgb >> 8) & 0xFF) + 0.0722 * (rgb & 0xFF)) / 255.0;
    }

    /** Linear mix of two RGB colours; {@code t} = 0 gives {@code from}, 1 gives {@code to}. */
    public static int mix(int from, int to, double t) {
        int r = (int) Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int bl = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(bl);
    }

    private static int scale(int rgb, double f) {
        return mix(0, rgb, f);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** Text colour of a chat line, following the theme for the theme-tinted types. */
    public static int typeColor(chatutilities.core.chat.MessageType type) {
        return switch (type) {
            case PLAYER -> PLAYER_TEXT;
            case PRIVATE_OUT -> TEXT;
            case SYSTEM -> DIM;
            default -> type.color;
        };
    }

    /** Scale of the screen being drawn (see {@link ScaledScreen}); scissor rectangles need it. */
    public static float uiScale = 1f;

    /** Like DrawContext.enableScissor, but for coordinates of a {@link ScaledScreen}. */
    public static void scissor(DrawContext c, int x1, int y1, int x2, int y2) {
        float s = uiScale;
        c.enableScissor((int) Math.floor(x1 * s), (int) Math.floor(y1 * s),
                (int) Math.ceil(x2 * s), (int) Math.ceil(y2 * s));
    }

    public static int panel() {
        return ModConfig.get().panelOpacity << 24 | BASE;
    }

    public static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }

    /** Rounded filled box with a 1px rounded border. */
    public static void box(DrawContext c, int x, int y, int w, int h, int fill, int border) {
        box(c, x, y, w, h, R_CARD, fill, border);
    }

    public static void box(DrawContext c, int x, int y, int w, int h, int r, int fill, int border) {
        if ((fill >>> 24) != 0) {
            round(c, x, y, w, h, r, fill);
        }
        if ((border >>> 24) != 0) {
            outline(c, x, y, w, h, r, border);
        }
    }

    private static int inset(int r, int row) {
        double d = r - row - 0.5;
        return r - (int) Math.round(Math.sqrt(Math.max(0, (double) r * r - d * d)));
    }

    /** Filled rounded rectangle. */
    public static void round(DrawContext c, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            c.fill(x + in, y + i, x + w - in, y + i + 1, color);
            c.fill(x + in, y + h - 1 - i, x + w - in, y + h - i, color);
        }
        c.fill(x, y + r, x + w, y + h - r, color);
    }

    /** 1px rounded outline. */
    public static void outline(DrawContext c, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            c.drawBorder(x, y, w, h, color);
            return;
        }
        int top = inset(r, 0);
        c.fill(x + top, y, x + w - top, y + 1, color);
        c.fill(x + top, y + h - 1, x + w - top, y + h, color);
        for (int i = 1; i < r; i++) {
            int in = inset(r, i);
            int end = Math.max(in + 1, inset(r, i - 1));
            c.fill(x + in, y + i, x + end, y + i + 1, color);
            c.fill(x + w - end, y + i, x + w - in, y + i + 1, color);
            c.fill(x + in, y + h - 1 - i, x + end, y + h - i, color);
            c.fill(x + w - end, y + h - 1 - i, x + w - in, y + h - i, color);
        }
        c.fill(x, y + r, x + 1, y + h - r, color);
        c.fill(x + w - 1, y + r, x + w, y + h - r, color);
    }

    /** Pill-shaped count badge ending at {@code right}. */
    public static void badge(DrawContext c, TextRenderer tr, int right, int y, int count, int color) {
        if (count <= 0) {
            return;
        }
        String s = count > 99 ? "99+" : Integer.toString(count);
        int w = Math.max(10, tr.getWidth(s) + 6);
        int x = right - w;
        round(c, x, y, w, 10, 4, color);
        c.drawText(tr, s, x + (w - tr.getWidth(s)) / 2 + 1, y + 1, ON_ACCENT, false);
    }

    /**
     * Arrow pointing "up" rotated by {@code degrees} clockwise, centred on (cx, cy).
     * 0 means straight ahead of the player.
     */
    public static void arrow(DrawContext c, float cx, float cy, float degrees, int color) {
        var m = c.getMatrices();
        m.push();
        m.translate(cx, cy, 0);
        m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(degrees));
        for (int j = 0; j < 7; j++) {
            c.fill(-j, -9 + j, j + 1, -8 + j, color);
        }
        c.fill(-1, -2, 2, 8, color);
        m.pop();
    }
}
