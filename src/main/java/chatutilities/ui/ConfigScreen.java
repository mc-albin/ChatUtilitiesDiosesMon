package chatutilities.ui;

import chatutilities.core.update.Updater;
import chatutilities.core.config.ModConfig;
import chatutilities.core.sound.Notifier;
import chatutilities.ui.widget.UiButton;
import chatutilities.ui.widget.UiSlider;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.Function;

/** Settings, independent from the main panel. Every change is saved immediately. */
public class ConfigScreen extends ScaledScreen {
    public enum Tab {
        ALERTS("Avisos"), SOUNDS("Sonidos"), SIZES("Tamaños"), THEME("Tema");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static final int[] DURATIONS = {3, 5, 6, 8, 12, 20};
    private static final int[] CLAN_LINES = {3, 4, 5, 6, 8, 10, 12};
    private static final int[] OPACITIES = {120, 160, 205, 235, 255};
    private static final String[] PANEL_SIZES = {"Pequeño", "Normal", "Grande"};
    private static final int[] TOAST_WIDTHS = {160, 190, 220, 260, 300};
    /** 0 = same width as the vanilla chat. */
    private static final int[] CLAN_WIDTHS = {0, 200, 240, 280, 320, 360};

    private final Screen parent;
    private Tab tab;
    private String note = "";
    private final java.util.List<Object[]> labels = new java.util.ArrayList<>();
    private int noteY;
    private int px;
    private int py;
    private int pw;
    private int ph;
    /** Theme tab: preset tiles, the y of the custom colour labels and the preview box. */
    private final java.util.List<Swatch> swatches = new java.util.ArrayList<>();
    private int themeY;
    private int previewY;
    /** Custom theme: R, G, B sliders of the accent [0..2] and of the background [3..5], and the hex fields. */
    private final chatutilities.ui.widget.UiSlider[] rgb = new chatutilities.ui.widget.UiSlider[6];
    private chatutilities.ui.widget.UiField accentHex;
    private chatutilities.ui.widget.UiField baseHex;
    private static final int HEX_LABEL = 50;

    private record Swatch(Theme.Preset preset, int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public ConfigScreen(Screen parent) {
        this(parent, Tab.ALERTS);
    }

    public ConfigScreen(Screen parent, Tab tab) {
        super(Text.literal("Configuración"));
        this.parent = parent;
        this.tab = tab;
    }

    @Override
    protected void initScaled() {
        pw = Math.min(width - 16, 330);
        ph = Math.min(height - 16, 260);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int bx = px + 12;
        int bw = pw - 24;
        int tx = px + pw - 12;
        for (int i = Tab.values().length - 1; i >= 0; i--) {
            Tab t = Tab.values()[i];
            int tw = textRenderer.getWidth(t.label) + 16;
            tx -= tw;
            addDrawableChild(new UiButton(tx, py + 6, tw, 16, t.label, UiButton.Style.NORMAL, () -> {
                tab = t;
                clearAndInit();
            }).selected(t == tab));
            tx -= 4;
        }
        note = "";
        labels.clear();
        int by = py + 34;
        int half = (bw - 4) / 2;
        ModConfig cfg = ModConfig.get();
        if (tab == Tab.ALERTS) {
            toggle(bx, by, half, "Avisos de TP", c -> c.tpNotifications, c -> c.tpNotifications = !c.tpNotifications);
            toggle(bx + half + 4, by, half, "Invitación raid", c -> c.raidInviteNotice, c -> c.raidInviteNotice = !c.raidInviteNotice);
            by += 22;
            toggle(bx, by, half, "Aviso de privados", c -> c.messageNotifications && c.notifyPrivate, c -> {
                boolean on = !(c.messageNotifications && c.notifyPrivate);
                c.messageNotifications = on;
                c.notifyPrivate = on;
            });
            toggle(bx + half + 4, by, half, "Pendientes fijo", c -> c.pendingPill, c -> c.pendingPill = !c.pendingPill);
            by += 22;
            toggle(bx, by, half, "Visor del clan", c -> c.clanViewer, c -> c.clanViewer = !c.clanViewer);
            slider(bx + half + 4, by, half, "En pantalla", 1, 5, 1, cfg.clanMaxShown,
                    v -> Integer.toString((int) v), v -> cfg.clanMaxShown = (int) v);
            by += 22;
            button(bx, by, bw, "Visor del clan: " + (cfg.clanAttach ? "junto al chat" : "posición libre"),
                    () -> cfg.clanAttach = !cfg.clanAttach);
            by += 22;
            slider(bx, by, half, "Avisos", 1, 20, 1, cfg.notificationSeconds,
                    v -> (int) v + " s", v -> cfg.notificationSeconds = (int) v);
            slider(bx + half + 4, by, half, "Opacidad", 25, 100, 5, Math.round(cfg.panelOpacity / 2.55f),
                    v -> (int) v + "%", v -> cfg.panelOpacity = (int) Math.round(v * 2.55));
            by += 22;
            toggle(bx, by, half, "Ocultar clan del chat", c -> c.hideClanInVanilla, c -> c.hideClanInVanilla = !c.hideClanInVanilla);
            toggle(bx + half + 4, by, half, "Ocultar chat MC", c -> c.hideVanillaChat, c -> c.hideVanillaChat = !c.hideVanillaChat);
        }
        if (tab == Tab.SOUNDS) {
            for (Notifier.Category cat : Notifier.Category.values()) {
                int toneX = bx + 52;
                int playW = 22;
                int volW = 96;
                int toneW = bw - 52 - 4 - volW - 4 - playW;
                Notifier.Tone tone = Notifier.tone(cat);
                button(toneX, by, toneW, tone.label, () -> {
                    Notifier.setTone(cat, Notifier.tone(cat).next());
                    Notifier.play(cat);
                });
                int vx = toneX + toneW + 4;
                addDrawableChild(new UiSlider(vx, by, volW, 18, "Vol", 0, 100, 5, Notifier.volume(cat),
                        v -> (int) v + "%", v -> Notifier.setVolume(cat, (int) v), () -> Notifier.play(cat)));
                addDrawableChild(new UiButton(vx + volW + 4, by, playW, 18, "▶", UiButton.Style.NORMAL,
                        () -> Notifier.play(cat)).tooltip("Probar"));
                labels.add(new Object[]{cat.label, bx, by + 5, Theme.TEXT});
                by += 24;
            }
            note(by + 4, "Pulsa el tono para cambiarlo y oírlo. Arrastra la barra para el volumen. También depende del volumen general del juego.");
        }
        if (tab == Tab.SIZES) {
            slider(bx, by, bw, "Panel", 0, 2, 1, cfg.panelSize,
                    v -> PANEL_SIZES[(int) v], v -> cfg.panelSize = (int) v);
            by += 22;
            slider(bx, by, bw, "Ancho de privados y TP", 160, 320, 10, cfg.toastWidth,
                    v -> (int) v + " px", v -> cfg.toastWidth = (int) v);
            by += 22;
            // The leftmost position means "same width as Minecraft's chat".
            slider(bx, by, bw, "Ancho del clan", 170, 400, 10, cfg.clanWidth == 0 ? 170 : cfg.clanWidth,
                    v -> v <= 170 ? "igual que el chat" : (int) v + " px", v -> cfg.clanWidth = v <= 170 ? 0 : (int) v);
            by += 22;
            slider(bx, by, half, "Privados", 50, 200, 5, Math.round(cfg.toastScale * 100),
                    v -> (int) v + "%", v -> cfg.toastScale = (float) (v / 100.0));
            slider(bx + half + 4, by, half, "TP", 50, 200, 5, Math.round(cfg.tpScale * 100),
                    v -> (int) v + "%", v -> cfg.tpScale = (float) (v / 100.0));
            by += 22;
            slider(bx, by, half, "Clan", 50, 200, 5, Math.round(cfg.clanScale * 100),
                    v -> (int) v + "%", v -> cfg.clanScale = (float) (v / 100.0));
            slider(bx + half + 4, by, half, "Clan en pantalla", 1, 5, 1, cfg.clanMaxShown,
                    v -> Integer.toString((int) v), v -> cfg.clanMaxShown = (int) v);
            by += 22;
            slider(bx, by, bw, "Scoreboard", 40, 100, 5, Math.round(cfg.scoreboardScale * 100),
                    v -> (int) v + "%" + (v >= 100 ? " (normal)" : ""), v -> cfg.scoreboardScale = (float) (v / 100.0));
        }
        swatches.clear();
        if (tab == Tab.THEME) {
            Theme.Preset[] presets = Theme.Preset.values();
            int cols = 4;
            int gap = 4;
            int tw = (bw - gap * (cols - 1)) / cols;
            for (int i = 0; i < presets.length; i++) {
                swatches.add(new Swatch(presets[i], bx + (i % cols) * (tw + gap), by + (i / cols) * 24, tw, 20));
            }
            by += ((presets.length + cols - 1) / cols) * 24 + 2;
            themeY = by;
            accentHex = null;
            baseHex = null;
            if (Theme.Preset.of(cfg.theme) == Theme.Preset.CUSTOM) {
                accentHex = hexField(bx + HEX_LABEL, by, half - HEX_LABEL, cfg.customAccent, true);
                baseHex = hexField(bx + half + 4 + HEX_LABEL, by, half - HEX_LABEL, cfg.customBase, false);
                by += 22;
                String[] names = {"Rojo", "Verde", "Azul"};
                for (int k = 0; k < 3; k++) {
                    int shift = 16 - k * 8;
                    rgb[k] = slider(bx, by, half, names[k], 0, 255, 1, (cfg.customAccent >> shift) & 0xFF,
                            v -> Integer.toString((int) v), v -> {
                                cfg.customAccent = withChannel(cfg.customAccent, shift, (int) v);
                                cfg.customSet = true;
                                Theme.apply();
                                setHexText(accentHex, cfg.customAccent);
                            });
                    rgb[k + 3] = slider(bx + half + 4, by, half, names[k], 0, 255, 1, (cfg.customBase >> shift) & 0xFF,
                            v -> Integer.toString((int) v), v -> {
                                cfg.customBase = withChannel(cfg.customBase, shift, (int) v);
                                cfg.customSet = true;
                                Theme.apply();
                                setHexText(baseHex, cfg.customBase);
                            });
                    by += 22;
                }
            }
            previewY = by + 2;
        } else {
            int ay = py + ph - 52;
            addDrawableChild(new UiButton(bx, ay, half, 20, "Mover avisos", UiButton.Style.NORMAL,
                    () -> client.setScreen(new HudEditorScreen(this))));
            addDrawableChild(new UiButton(bx + half + 4, ay, half, 20, "Restablecer posiciones", UiButton.Style.NORMAL, () -> {
                cfg.resetPositions();
                save();
            }));
        }
        int halfW = (bw - 4) / 2;
        updateButton = addDrawableChild(new UiButton(bx, py + ph - 28, halfW, 20, "", UiButton.Style.NORMAL, () -> {
            if (Updater.state() == Updater.State.AVAILABLE || Updater.state() == Updater.State.ERROR && !Updater.latestVersion().isEmpty()) {
                Updater.downloadAsync();
            } else {
                Updater.checkNow();
            }
        }));
        refreshUpdate();
        addDrawableChild(new UiButton(bx + halfW + 4, py + ph - 28, bw - halfW - 4, 20, "Listo", UiButton.Style.ACCENT, this::close));
    }

    private UiButton updateButton;

    /** Label of the update button, following the check and the download. */
    private void refreshUpdate() {
        if (updateButton == null) {
            return;
        }
        boolean justChecked = Updater.manualAt() > 0 && System.currentTimeMillis() - Updater.manualAt() < 6000L;
        String current = Updater.currentVersion();
        updateButton.active = true;
        updateButton.setStyle(UiButton.Style.NORMAL);
        switch (Updater.state()) {
            case CHECKING -> {
                updateButton.setLabel("\u21bb Buscando\u2026");
                updateButton.active = false;
            }
            case AVAILABLE -> {
                updateButton.setLabel("\u2b07 v" + Updater.latestVersion());
                updateButton.setStyle(UiButton.Style.ACCENT);
            }
            case DOWNLOADING -> {
                updateButton.setLabel("\u2b07 \u2026");
                updateButton.active = false;
            }
            case READY -> {
                updateButton.setLabel("\u2714 Reinicia");
                updateButton.setStyle(UiButton.Style.SUCCESS);
                updateButton.active = false;
            }
            case ERROR -> {
                updateButton.setLabel("\u21bb Reintentar");
                updateButton.setStyle(UiButton.Style.DANGER);
            }
            case DISABLED -> {
                updateButton.setLabel("\u2014");
                updateButton.active = false;
            }
            default -> {
                if (justChecked && Updater.lastCheckFailed()) {
                    updateButton.setLabel("\u2716 Sin red");
                    updateButton.setStyle(UiButton.Style.DANGER);
                } else if (justChecked) {
                    updateButton.setLabel("\u2714 Al d\u00eda");
                    updateButton.setStyle(UiButton.Style.SUCCESS);
                } else {
                    updateButton.setLabel("\u21bb Buscar");
                }
            }
        }
        updateButton.tooltip("Actualizaciones \u00b7 v" + current);
    }

    @Override
    public void tick() {
        super.tick();
        refreshUpdate();
    }

    private static int withChannel(int rgb, int shift, int value) {
        return (rgb & ~(0xFF << shift) & 0xFFFFFF) | ((value & 0xFF) << shift);
    }

    private void selectTheme(Theme.Preset preset) {
        ModConfig cfg = ModConfig.get();
        if (preset == Theme.Preset.CUSTOM && !cfg.customSet) {
            // The first time, the custom colours start from the theme in use.
            int[] cur = Theme.current();
            cfg.customAccent = cur[0];
            cfg.customBase = cur[1];
            cfg.customSet = true;
        }
        cfg.theme = preset.name();
        Theme.apply();
        client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(
                net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1f));
        save();
    }

    @Override
    protected boolean mouseClickedScaled(double mx, double my, int button) {
        if (button == 0) {
            for (Swatch s : swatches) {
                if (s.contains(mx, my)) {
                    selectTheme(s.preset());
                    return true;
                }
            }
        }
        return super.mouseClickedScaled(mx, my, button);
    }

    /** Preset tiles, custom colour values and a small preview of the active palette. */
    private void renderTheme(DrawContext c, int mouseX, int mouseY) {
        ModConfig cfg = ModConfig.get();
        Theme.Preset active = Theme.Preset.of(cfg.theme);
        for (Swatch s : swatches) {
            boolean custom = s.preset() == Theme.Preset.CUSTOM;
            int accent = custom ? cfg.customAccent : s.preset().accent;
            int base = custom ? cfg.customBase : s.preset().base;
            boolean sel = s.preset() == active;
            boolean hover = s.contains(mouseX, mouseY);
            Theme.round(c, s.x(), s.y(), s.w(), s.h(), Theme.R_CARD, 0xFF000000 | base);
            int edge = sel ? 0xFF000000 | accent : Theme.withAlpha(accent, hover ? 0xAA : 0x55);
            Theme.outline(c, s.x(), s.y(), s.w(), s.h(), Theme.R_CARD, edge);
            if (sel) {
                Theme.outline(c, s.x() + 1, s.y() + 1, s.w() - 2, s.h() - 2, Theme.R_CARD - 1, edge);
            }
            Theme.round(c, s.x() + 5, s.y() + 6, 8, 8, 4, 0xFF000000 | accent);
            int ink = Theme.luminance(base) < 0.35 ? 0xFFFFFF : 0x000000;
            String label = textRenderer.trimToWidth(s.preset().label, s.w() - 20);
            c.drawText(textRenderer, label, s.x() + 16, s.y() + 6, 0xFF000000 | Theme.mix(accent, ink, 0.57), false);
        }
        int bx = px + 12;
        int bw = pw - 24;
        int half = (bw - 4) / 2;
        if (active == Theme.Preset.CUSTOM) {
            c.drawText(textRenderer, "Acento", bx, themeY + 5, Theme.TEXT, false);
            c.drawText(textRenderer, "Fondo", bx + half + 4, themeY + 5, Theme.TEXT, false);
        }
        int bottom = py + ph - 34;
        int h = bottom - previewY;
        if (h < 30) {
            return;
        }
        // Preview: a little card drawn with the live palette.
        Theme.round(c, bx, previewY, bw, h, Theme.R_CARD, Theme.withAlpha(Theme.BASE, 0xF0));
        Theme.outline(c, bx, previewY, bw, h, Theme.R_CARD, Theme.BORDER_STRONG);
        int ty = previewY + 6;
        c.drawText(textRenderer, "Vista previa", bx + 8, ty, Theme.ACCENT, false);
        String pill = "Enviar";
        int pillW = textRenderer.getWidth(pill) + 12;
        Theme.round(c, bx + bw - 8 - pillW, ty - 2, pillW, 12, 4, Theme.ACCENT);
        c.drawText(textRenderer, pill, bx + bw - 8 - pillW + 6, ty, Theme.ON_ACCENT, false);
        Theme.badge(c, textRenderer, bx + bw - 14 - pillW, ty - 1, 3, Theme.ACCENT);
        ty += 13;
        if (ty + 9 <= bottom) {
            c.drawText(textRenderer, "Steve » ¡Hola a todos!", bx + 8, ty, Theme.PLAYER_TEXT, false);
            ty += 11;
        }
        if (ty + 9 <= bottom) {
            String hint = active == Theme.Preset.CUSTOM ? "Escribe un código hex o mueve las barras."
                    : "Elige Personalizado para usar tus colores RGB.";
            c.drawText(textRenderer, textRenderer.trimToWidth(hint, bw - 16), bx + 8, ty, Theme.MUTED, false);
        }
    }

    private static final float[] SCALES = {0.75f, 0.9f, 1f, 1.15f, 1.3f, 1.5f, 1.75f, 2f};

    /** Scoreboard sizes, from normal down to small (never hidden). */
    private static final float[] BOARD = {1f, 0.9f, 0.8f, 0.7f, 0.6f, 0.5f, 0.4f};

    private static float nextBoard(float current) {
        for (float s : BOARD) {
            if (s < current - 0.01f) {
                return s;
            }
        }
        return BOARD[0];
    }

    private static float nextScale(float current) {
        for (float s : SCALES) {
            if (s > current + 0.01f) {
                return s;
            }
        }
        return SCALES[0];
    }

    /** Slider that updates the setting while dragging and saves when released. */
    private UiSlider slider(int x, int y, int w, String label, double min, double max, double step, double value,
                            java.util.function.DoubleFunction<String> format, java.util.function.DoubleConsumer set) {
        return addDrawableChild(new UiSlider(x, y, w, 18, label, min, max, step, value, format, set, this::persist));
    }

    /**
     * Field for a hex colour code. A complete code (6 digits, or 3) is applied while typing:
     * the panel recolours and the R, G, B sliders follow. An incomplete code is shown in red.
     */
    private chatutilities.ui.widget.UiField hexField(int x, int y, int w, int value, boolean accent) {
        var f = new chatutilities.ui.widget.UiField(textRenderer, x, y, w, 18, "#RRGGBB");
        f.setMaxLength(7);
        f.setTextPredicate(s -> s.matches("#?[0-9A-Fa-f]{0,6}"));
        f.setText(Theme.hex(value));
        f.setEditableColor(Theme.TEXT);
        f.setChangedListener(s -> {
            int v = Theme.parseHex(s);
            f.setEditableColor(v < 0 ? Theme.DANGER : Theme.TEXT);
            if (v < 0) {
                return;
            }
            ModConfig cfg = ModConfig.get();
            if ((accent ? cfg.customAccent : cfg.customBase) == v) {
                return;
            }
            if (accent) {
                cfg.customAccent = v;
            } else {
                cfg.customBase = v;
            }
            cfg.customSet = true;
            Theme.apply();
            for (int k = 0; k < 3; k++) {
                UiSlider sl = rgb[(accent ? 0 : 3) + k];
                if (sl != null) {
                    sl.setValue((v >> (16 - k * 8)) & 0xFF);
                }
            }
            persist();
        });
        return addDrawableChild(f);
    }

    /** Shows a colour in a hex field unless the user is typing in it. */
    private static void setHexText(chatutilities.ui.widget.UiField f, int rgb) {
        if (f != null && !f.isFocused()) {
            f.setText(Theme.hex(rgb));
        }
    }

    private void persist() {
        ModConfig.get().sanitize();
        ModConfig.save();
    }

    private void button(int x, int y, int w, String label, Runnable change) {
        addDrawableChild(new UiButton(x, y, w, 18, label, UiButton.Style.NORMAL, () -> {
            change.run();
            save();
        }));
    }

    private void toggle(int x, int y, int w, String label, Function<ModConfig, Boolean> get, Consumer<ModConfig> flip) {
        boolean on = get.apply(ModConfig.get());
        addDrawableChild(new UiButton(x, y, w, 18, label + ": " + (on ? "Sí" : "No"),
                on ? UiButton.Style.SUCCESS : UiButton.Style.NORMAL, () -> {
            flip.accept(ModConfig.get());
            save();
        }));
    }

    private void save() {
        ModConfig.save();
        clearAndInit();
    }

    private void note(int y, String text) {
        note = text;
        noteY = y;
    }

    private static int cycle(int[] values, int current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    private static int next(int[] values, int current) {
        for (int v : values) {
            if (v > current) {
                return v;
            }
        }
        return values[0];
    }

    @Override
    public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
        c.fill(0, 0, width, height, 0x50000000);
    }

    @Override
    protected void renderScaled(DrawContext c, int mouseX, int mouseY, float delta) {
        renderBackground(c, mouseX, mouseY, delta);
        Theme.round(c, px, py, pw, ph, Theme.R_PANEL, Theme.panel());
        c.fill(px + 8, py + 27, px + pw - 8, py + 28, Theme.BORDER);
        c.drawText(textRenderer, title, px + 12, py + 10, Theme.TEXT, false);
        for (Object[] l : labels) {
            c.drawText(textRenderer, (String) l[0], (int) l[1], (int) l[2], (int) l[3], false);
        }
        if (!note.isEmpty()) {
            int ly = noteY;
            for (var line : textRenderer.wrapLines(Text.literal(note), pw - 24)) {
                c.drawText(textRenderer, line, px + 12, ly, Theme.MUTED, false);
                ly += 10;
            }
        }
        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.Drawable d) {
                d.render(c, mouseX, mouseY, delta);
            }
        }
        if (tab == Tab.THEME) {
            renderTheme(c, mouseX, mouseY);
        }
    }

    @Override
    public void close() {
        persist();
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
