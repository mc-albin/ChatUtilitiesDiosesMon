package chatutilities.ui;

import chatutilities.core.ChatUtilitiesCore;
import chatutilities.core.commands.ServerCommands;
import chatutilities.core.config.ModConfig;
import chatutilities.hud.HudOverlay;
import chatutilities.ui.section.ChatSection;
import chatutilities.ui.section.PaySection;
import chatutilities.ui.section.Section;
import chatutilities.ui.section.TeleportSection;
import chatutilities.ui.widget.UiButton;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The panel: folder tabs on top, a borderless body with a header (icon, title, search, settings,
 * close) and the active section, and a vertical icon bar on the right with the sections and the
 * quick actions.
 */
public class MainPanelScreen extends ScaledScreen {
    private static final int TAB_H = 18;
    private static final int HEADER = 22;
    private static final int BAR_W = 24;
    private static final int BAR_GAP = 4;
    private static final Identifier GEAR = Identifier.of(ChatUtilitiesCore.MOD_ID, "textures/gui/gear.png");

    /** One-click server commands shown in the icon bar. */
    private record QuickAction(String command, String tooltip, Supplier<ItemStack> icon) {
    }

    private static final String AFK = "afk";

    private static final List<QuickAction> QUICK = List.of(
            new QuickAction("pokeheal", "Curar Pokémon", MainPanelScreen::healIcon),
            new QuickAction("feed", "Comida", () -> new ItemStack(Items.COOKED_BEEF)),
            new QuickAction("repair all", "Reparar todo", () -> new ItemStack(Items.ANVIL)),
            new QuickAction(AFK, "", () -> new ItemStack(Items.RED_BED)));

    /** Cobblemon's healing machine when the game has it, a glistering melon otherwise. */
    private static ItemStack healIcon() {
        Item item = Registries.ITEM.get(Identifier.of("cobblemon", "healing_machine"));
        return new ItemStack(item == Items.AIR ? Items.GLISTERING_MELON_SLICE : item);
    }

    /** Sections survive reopening so their state (typed text, selected player) is kept. */
    private static List<Section> sections;

    private final KeyBinding openKey;
    private final List<UiButton> sectionButtons = new ArrayList<>();
    private final List<UiButton> quickButtons = new ArrayList<>();
    private final List<int[]> tabRects = new ArrayList<>();
    private Section current;
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int bodyY;

    public MainPanelScreen(KeyBinding openKey) {
        super(Text.literal("ChatUtilities DiosesMon"));
        this.openKey = openKey;
        if (sections == null) {
            sections = createSections();
        }
        String last = ModConfig.get().lastSection;
        current = sections.stream().filter(s -> s.id().equals(last)).findFirst().orElse(sections.get(0));
    }

    /**
     * Panel opened with the key: straight to TP while a TP notice is on screen, straight to the
     * conversation while a private-message notice is on screen, otherwise the last section.
     */
    public static MainPanelScreen openFromKey(KeyBinding key) {
        MainPanelScreen s = new MainPanelScreen(key);
        long now = System.currentTimeMillis();
        long window = ModConfig.get().notificationSeconds * 1000L;
        if (!ChatUtilitiesCore.TELEPORTS.pending().isEmpty() && now - ChatUtilitiesCore.lastTpNotice < window + 3000L) {
            s.current = sections.stream().filter(x -> x.id().equals("tp")).findFirst().orElse(s.current);
        } else if (ChatUtilitiesCore.lastMessageNotice != null && now - ChatUtilitiesCore.lastMessageNoticeAt < window + 3000L
                && ChatUtilitiesCore.LOG.unreadPrivate() > 0) {
            String peer = ChatUtilitiesCore.lastMessageNotice.peer();
            for (Section sec : sections) {
                if (sec instanceof ChatSection chat) {
                    chat.openPrivate(peer);
                    s.current = chat;
                }
            }
        }
        return s;
    }

    /** Registry of panel pages. New tools only need to be added here. */
    private static List<Section> createSections() {
        return List.of(new ChatSection(), new TeleportSection(), new PaySection());
    }

    public <T extends Element & Drawable & Selectable> T addChild(T widget) {
        return addDrawableChild(widget);
    }

    /** Rebuilds the widgets (used by sections that switch their own sub-views). */
    public void relayout() {
        clearAndInit();
    }

    public void select(Section section) {
        if (section == current) {
            return;
        }
        current = section;
        ModConfig.get().lastSection = section.id();
        ModConfig.save();
        clearAndInit();
        current.onShow();
    }

    /** Opens the chat section on a private conversation (used by Raids and TP). */
    public void openPrivate(String name) {
        for (Section s : sections) {
            if (s instanceof ChatSection chat) {
                chat.openPrivate(name);
                if (current != chat) {
                    select(chat);
                } else {
                    clearAndInit();
                }
                return;
            }
        }
    }

    @Override
    protected void initScaled() {
        int size = ModConfig.get().panelSize;
        int maxW = size == 0 ? 440 : size == 2 ? 620 : 520;
        int maxH = size == 0 ? 250 : size == 2 ? 360 : 300;
        pw = Math.min(width - 16 - BAR_W - BAR_GAP, maxW);
        ph = Math.min(height - 16, maxH);
        px = (width - pw - BAR_W - BAR_GAP) / 2;
        py = (height - ph) / 2;
        bodyY = py + TAB_H;

        int bx = px + pw - 22;
        addDrawableChild(new UiButton(bx, bodyY + 3, 16, 16, "✕", UiButton.Style.NORMAL, this::close).tooltip("Cerrar"));
        bx -= 20;
        addDrawableChild(new UiButton(bx, bodyY + 3, 16, 16, "", UiButton.Style.NORMAL,
                () -> client.setScreen(new ConfigScreen(this, ConfigScreen.Tab.ALERTS)))
                .texture(GEAR).tooltip("Configuración"));

        // Icon bar: sections, then quick actions.
        int barX = px + pw + BAR_GAP;
        int by = bodyY;
        sectionButtons.clear();
        for (Section s : sections) {
            sectionButtons.add(addDrawableChild(new UiButton(barX, by, BAR_W, BAR_W, "", UiButton.Style.NORMAL,
                    () -> select(s)).icon(s.icon()).tooltip(s.title()).selected(s == current)));
            by += BAR_W + 5;
        }
        // Clear gap (with a line in the middle) so the quick actions are not mistaken for sections.
        by += 22;
        quickButtons.clear();
        for (QuickAction q : QUICK) {
            UiButton b = new UiButton(barX, by, BAR_W, BAR_W, "", UiButton.Style.NORMAL, () -> {
                ChatUtilitiesCore.quickRequested(q.command());
                ServerCommands.send(q.command());
            }).icon(q.icon().get()).tooltip(q.tooltip());
            if (q.command().equals(AFK)) {
                // Red while AFK, green while active; the tooltip shows the AFK timer.
                b.tint(() -> ChatUtilitiesCore.afk ? Theme.DANGER : Theme.SUCCESS);
            }
            quickButtons.add(addDrawableChild(b));
            by += BAR_W + 3;
        }
        // Minecraft's own chat on the HUD: green = visible, red = hidden (T still opens it).
        by += 20;
        chatToggle = addDrawableChild(new UiButton(barX, by, BAR_W, BAR_W, "", UiButton.Style.NORMAL, () -> {
            ModConfig cfg = ModConfig.get();
            cfg.hideVanillaChat = !cfg.hideVanillaChat;
            ModConfig.save();
        }).icon(new ItemStack(Items.OAK_SIGN)).tint(() -> ModConfig.get().hideVanillaChat ? Theme.DANGER : Theme.SUCCESS));
        refreshQuick();

        // Update available: gold button in the header, left of the search box.
        updateButton = null;
        var us = chatutilities.core.update.Updater.state();
        if (us == chatutilities.core.update.Updater.State.AVAILABLE || us == chatutilities.core.update.Updater.State.DOWNLOADING
                || us == chatutilities.core.update.Updater.State.READY || us == chatutilities.core.update.Updater.State.ERROR) {
            int uw = 124;
            int ux = bx - 6 - 140 - 22 - uw;
            updateButton = addDrawableChild(new UiButton(ux, bodyY + 3, uw, 16, "", UiButton.Style.ACCENT,
                    chatutilities.core.update.Updater::downloadAsync));
            refreshUpdate();
        }
        current.setHeader(bodyY + 3, bx - 6);
        current.init(this, px + 8, bodyY + HEADER + 6, pw - 16, ph - TAB_H - HEADER - 12);
    }

    private UiButton chatToggle;
    private UiButton updateButton;

    /** Label and state of the update button, following the download. */
    private void refreshUpdate() {
        if (updateButton == null) {
            return;
        }
        String v = chatutilities.core.update.Updater.latestVersion();
        switch (chatutilities.core.update.Updater.state()) {
            case AVAILABLE -> {
                updateButton.setLabel("⬇ Actualizar a " + v);
                updateButton.setStyle(UiButton.Style.ACCENT);
                updateButton.active = true;
                String n = chatutilities.core.update.Updater.notes();
                updateButton.tooltip("Versión " + v + " disponible (tienes " + chatutilities.core.update.Updater.currentVersion()
                        + ")" + (n.isEmpty() ? "" : "\n" + n) + "\nSe descarga ahora y se instala al cerrar el juego.");
            }
            case DOWNLOADING -> {
                updateButton.setLabel("Descargando…");
                updateButton.active = false;
            }
            case READY -> {
                updateButton.setLabel("✔ Lista: cierra el juego");
                updateButton.setStyle(UiButton.Style.SUCCESS);
                updateButton.active = true;
                updateButton.tooltip("La versión " + v + " se instala al cerrar Minecraft.");
            }
            case ERROR -> {
                updateButton.setLabel("Reintentar actualización");
                updateButton.setStyle(UiButton.Style.DANGER);
                updateButton.active = true;
                updateButton.tooltip(chatutilities.core.update.Updater.error());
            }
            default -> updateButton.visible = false;
        }
    }

    private void refreshQuick() {
        refreshUpdate();
        if (chatToggle != null) {
            chatToggle.tooltip(ModConfig.get().hideVanillaChat
                    ? "Chat de Minecraft: oculto\nClic para mostrarlo"
                    : "Chat de Minecraft: visible\nClic para ocultarlo");
        }
        for (int i = 0; i < quickButtons.size(); i++) {
            String command = QUICK.get(i).command();
            UiButton b = quickButtons.get(i);
            b.active = ServerCommands.usable(command.split(" ", 2)[0]);
            if (command.equals(AFK)) {
                b.tooltip(ChatUtilitiesCore.afk ? "Estás AFK ahora · " + ChatUtilitiesCore.afkElapsed() : "No estás AFK");
            }
        }
    }

    @Override
    public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
        c.fill(0, 0, width, height, 0x30000000);
    }

    @Override
    protected void renderScaled(DrawContext c, int mouseX, int mouseY, float delta) {
        renderBackground(c, mouseX, mouseY, delta);
        int body = Theme.panel();
        Theme.round(c, px, bodyY, pw, ph - TAB_H, Theme.R_PANEL, body);
        renderTabs(c, mouseX, mouseY, body);
        // Header: icon, title, thin gold line below.
        c.drawItem(current.icon(), px + 6, bodyY + 3);
        int titleMax = updateButton != null && updateButton.visible ? updateButton.getX() - (px + 26) - 6 : pw / 2;
        c.drawText(textRenderer, textRenderer.trimToWidth(current.header(), Math.max(20, titleMax)), px + 26, bodyY + 7,
                Theme.TEXT, false);
        c.fill(px + 6, bodyY + HEADER, px + pw - 6, bodyY + HEADER + 1, Theme.BORDER);
        // Icon bar: gold mark on the open section, separator before the quick actions.
        for (int i = 0; i < sectionButtons.size(); i++) {
            UiButton b = sectionButtons.get(i);
            if (sections.get(i) == current) {
                Theme.round(c, b.getX() - 3, b.getY() + 4, 2, b.getHeight() - 8, 1, Theme.ACCENT);
            }
        }
        if (!quickButtons.isEmpty()) {
            UiButton first = quickButtons.get(0);
            int lineY = first.getY() - 14;
            c.fill(first.getX() + 2, lineY, first.getX() + BAR_W - 2, lineY + 1, Theme.BORDER_STRONG);
        }
        current.render(c, mouseX, mouseY, delta);
        for (Element e : children()) {
            if (e instanceof Drawable d) {
                d.render(c, mouseX, mouseY, delta);
            }
        }
        for (int i = 0; i < sectionButtons.size(); i++) {
            UiButton b = sectionButtons.get(i);
            Theme.badge(c, textRenderer, b.getX() + b.getWidth() + 2, b.getY() - 2, sections.get(i).badge(), Theme.ACCENT);
        }
        if (ChatUtilitiesCore.afk && !quickButtons.isEmpty()) {
            UiButton afk = quickButtons.get(quickButtons.size() - 1);
            String t = ChatUtilitiesCore.afkElapsed();
            c.drawText(textRenderer, t, afk.getX() + (BAR_W - textRenderer.getWidth(t)) / 2, afk.getY() + BAR_W + 3,
                    Theme.DANGER, true);
        }
        if (chatToggle != null) {
            int lineY = chatToggle.getY() - 8;
            c.fill(chatToggle.getX() + 2, lineY, chatToggle.getX() + BAR_W - 2, lineY + 1, Theme.BORDER_STRONG);
        }
        current.renderOverlay(c, mouseX, mouseY, delta);
    }

    @Override
    protected void renderUnscaled(DrawContext c, int mouseX, int mouseY, float delta) {
        HudOverlay.renderQuick(c);
    }

    /** Folder tabs of the current section, joined to the body when selected. */
    private void renderTabs(DrawContext c, int mouseX, int mouseY, int body) {
        tabRects.clear();
        List<String> tabs = current.tabs();
        int tx = px + 4;
        for (int i = 0; i < tabs.size(); i++) {
            String label = tabs.get(i);
            int badge = current.tabBadge(i);
            String count = badge > 99 ? "99+" : Integer.toString(badge);
            int pillW = badge > 0 ? Math.max(11, textRenderer.getWidth(count) + 6) : 0;
            String text = label;
            int tw = textRenderer.getWidth(text) + 18 + (badge > 0 ? pillW + 4 : 0);
            boolean sel = i == current.selectedTab();
            boolean hover = mouseX >= tx && mouseX < tx + tw && mouseY >= py && mouseY < bodyY;
            int fill = sel ? body : Theme.withAlpha(Theme.BASE, hover ? 0xC8 : 0x96);
            // Clip the bottom so the tab has rounded top corners and a flat base on the body.
            Theme.scissor(c, tx, py, tx + tw, bodyY);
            Theme.round(c, tx, py, tw, TAB_H + 8, Theme.R_CARD, fill);
            c.disableScissor();
            if (sel) {
                c.fill(tx + 3, py, tx + tw - 3, py + 1, Theme.ACCENT);
            }
            c.drawText(textRenderer, text, tx + 9, py + 6, sel ? Theme.TEXT : hover ? Theme.MUTED : Theme.DIM, false);
            if (badge > 0) {
                // Gold pill after the label with the unread count.
                int bx = tx + 9 + textRenderer.getWidth(text) + 4;
                Theme.round(c, bx, py + 4, pillW, 11, 5, Theme.ACCENT);
                c.drawText(textRenderer, count, bx + (pillW - textRenderer.getWidth(count)) / 2 + 1, py + 6,
                        Theme.ON_ACCENT, false);
            }
            tabRects.add(new int[]{tx, tw, i});
            tx += tw + 2;
        }
    }

    @Override
    public void tick() {
        refreshQuick();
        current.tick();
    }

    @Override
    protected boolean mouseClickedScaled(double mx, double my, int button) {
        if (current.overlayClicked(mx, my, button)) {
            return true;
        }
        if (my >= py && my < bodyY) {
            for (int[] r : tabRects) {
                if (mx >= r[0] && mx < r[0] + r[1]) {
                    current.selectTab(r[2]);
                    return true;
                }
            }
        }
        if (super.mouseClickedScaled(mx, my, button)) {
            return true;
        }
        setFocused(null);
        return current.mouseClicked(mx, my, button);
    }

    @Override
    protected boolean mouseScrolledScaled(double mx, double my, double horizontal, double vertical) {
        return current.mouseScrolled(mx, my, vertical) || super.mouseScrolledScaled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (current.keyPressed(key, scan, modifiers)) {
            return true;
        }
        boolean typing = getFocused() instanceof TextFieldWidget;
        if (!typing && openKey != null && openKey.matchesKey(key, scan)) {
            close();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
