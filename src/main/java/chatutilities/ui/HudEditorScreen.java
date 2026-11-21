package chatutilities.ui;

import chatutilities.core.config.ModConfig;
import chatutilities.hud.HudOverlay;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Drag the notice previews to place them, and drag the gold corner of each one to make it wider
 * or narrower. Positions are saved as screen fractions, widths in pixels.
 */
public class HudEditorScreen extends Screen {
    private static final int TP = 0;
    private static final int MSG = 1;
    private static final int CLAN = 2;
    private static final int COUNT = 3;
    private static final int HANDLE = 7;

    private final Screen parent;
    private int dragging = -1;
    private int resizing = -1;
    private double offX;
    private double offY;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("Mover avisos"));
        this.parent = parent;
    }

    private int[] rect(int which) {
        return switch (which) {
            case TP -> HudOverlay.tpRect(width, height);
            case MSG -> HudOverlay.msgRect(width, height);
            default -> HudOverlay.clanRect(width, height);
        };
    }

    /** On-screen size of each preview, after its scale. */
    private static int height(int which) {
        return switch (which) {
            case TP -> HudOverlay.tpBoxH();
            case MSG -> HudOverlay.toastBoxH();
            default -> HudOverlay.clanHeight();
        };
    }

    private static int width(int which) {
        return switch (which) {
            case TP -> HudOverlay.tpBoxW();
            case MSG -> HudOverlay.toastBoxW();
            default -> HudOverlay.clanBoxW();
        };
    }

    /** Each notice has its own scale: TP, private messages and clan never share it. */
    private static float scale(int which) {
        return switch (which) {
            case TP -> HudOverlay.tpScale();
            case MSG -> HudOverlay.toastScale();
            default -> HudOverlay.clanScale();
        };
    }

    private boolean visible(int which) {
        return which != CLAN || ModConfig.get().clanViewer;
    }

    @Override
    public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
        c.fill(0, 0, width, height, 0x30000000);
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        renderBackground(c, mouseX, mouseY, delta);
        String help = "Arrastra cada aviso para moverlo · Arrastra la esquina dorada para agrandarlo o achicarlo · ESC para volver";
        c.drawText(textRenderer, help, (width - textRenderer.getWidth(help)) / 2, height - 14, Theme.TEXT, true);
        ModConfig cfg = ModConfig.get();
        int[] tp = rect(TP);
        int[] msg = rect(MSG);
        HudOverlay.drawToast(c, textRenderer, tp[0], tp[1], HudOverlay.tpScale(), Theme.INFO, "Jugador quiere ir hacia ti",
                "Pulsa G para responder.", "", cfg.tpNotifications ? 1f : 0.45f);
        HudOverlay.drawToast(c, textRenderer, msg[0], msg[1], HudOverlay.toastScale(), Theme.SUCCESS, "PRIVADO · Jugador",
                "Vista previa", "", cfg.messageNotifications ? 1f : 0.45f);
        if (cfg.clanViewer) {
            HudOverlay.drawClanViewer(c, textRenderer, width, height, true);
        }
        for (int i = 0; i < COUNT; i++) {
            if (!visible(i)) {
                continue;
            }
            int[] r = rect(i);
            boolean hover = inside(i, mouseX, mouseY);
            boolean active = dragging == i || resizing == i;
            Theme.outline(c, r[0] - 2, r[1] - 2, width(i) + 4, height(i) + 4, Theme.R_PANEL + 1,
                    active ? Theme.withAlpha(Theme.ACCENT, 0xCC) : hover ? Theme.BORDER_STRONG : Theme.BORDER);
            int hx = r[0] + width(i) - HANDLE + 2;
            int hy = r[1] + height(i) - HANDLE + 2;
            boolean onHandle = onHandle(i, mouseX, mouseY);
            Theme.round(c, hx, hy, HANDLE, HANDLE, 2, onHandle || resizing == i ? Theme.ACCENT : Theme.withAlpha(Theme.ACCENT, 0x99));
            if (resizing == i) {
                String size = Math.round(scale(i) * 100) + "%";
                c.drawText(textRenderer, size, r[0] + width(i) - textRenderer.getWidth(size), r[1] - 12, Theme.ACCENT, true);
            }
        }
    }

    private boolean inside(int which, double mx, double my) {
        int[] r = rect(which);
        return mx >= r[0] && mx < r[0] + width(which) && my >= r[1] && my < r[1] + height(which);
    }

    private boolean onHandle(int which, double mx, double my) {
        int[] r = rect(which);
        int hx = r[0] + width(which) - HANDLE + 2;
        int hy = r[1] + height(which) - HANDLE + 2;
        return mx >= hx - 2 && mx < hx + HANDLE + 2 && my >= hy - 2 && my < hy + HANDLE + 2;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = COUNT - 1; i >= 0; i--) {
            if (visible(i) && onHandle(i, mx, my)) {
                detachClan(i);
                resizing = i;
                return true;
            }
        }
        for (int i = COUNT - 1; i >= 0; i--) {
            if (!visible(i) || !inside(i, mx, my)) {
                continue;
            }
            detachClan(i);
            int[] r = rect(i);
            dragging = i;
            offX = mx - r[0];
            offY = my - r[1];
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    /** Moving or resizing the clan viewer detaches it from the chat; keep it where it was. */
    private void detachClan(int which) {
        ModConfig cfg = ModConfig.get();
        if (which != CLAN || !cfg.clanAttach) {
            return;
        }
        int[] r = rect(CLAN);
        if (cfg.clanWidth == 0) {
            cfg.clanWidth = HudOverlay.clanWidth();
        }
        cfg.clanAttach = false;
        cfg.clanX = Math.max(0f, Math.min(1f, (float) r[0] / Math.max(1, width - HudOverlay.clanBoxW())));
        cfg.clanY = Math.max(0f, Math.min(1f, (float) r[1] / Math.max(1, height - HudOverlay.clanHeight())));
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        ModConfig cfg = ModConfig.get();
        if (resizing >= 0) {
            // The corner scales the whole notice (box, head and text), in 5% steps.
            int[] r = rect(resizing);
            int base = resizing == CLAN ? HudOverlay.clanWidth() : HudOverlay.toastW();
            float s = (float) ((mx - r[0]) / Math.max(1, base));
            s = ModConfig.clampScale(Math.round(s * 20f) / 20f);
            switch (resizing) {
                case TP -> cfg.tpScale = s;
                case MSG -> cfg.toastScale = s;
                default -> cfg.clanScale = s;
            }
            return true;
        }
        if (dragging < 0) {
            return super.mouseDragged(mx, my, button, dx, dy);
        }
        float fx = (float) ((mx - offX) / Math.max(1, width - width(dragging)));
        float fy = (float) ((my - offY) / Math.max(1, height - height(dragging)));
        fx = Math.max(0f, Math.min(1f, fx));
        fy = Math.max(0f, Math.min(1f, fy));
        switch (dragging) {
            case TP -> {
                cfg.tpX = fx;
                cfg.tpY = fy;
            }
            case MSG -> {
                cfg.msgX = fx;
                cfg.msgY = fy;
            }
            default -> {
                cfg.clanX = fx;
                cfg.clanY = fy;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging >= 0 || resizing >= 0) {
            dragging = -1;
            resizing = -1;
            ModConfig.save();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public void close() {
        ModConfig.save();
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
