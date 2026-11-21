package chatutilities.ui.section;

import chatutilities.ui.MainPanelScreen;
import chatutilities.ui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.item.ItemStack;

/**
 * One page of the panel. To add a new tool, extend this class and register it in
 * {@link MainPanelScreen#createSections()}.
 */
public abstract class Section {
    protected MainPanelScreen screen;
    protected MinecraftClient mc;
    protected TextRenderer tr;
    protected int x;
    protected int y;
    protected int w;
    protected int h;
    private String status = "";
    private int statusColor = Theme.MUTED;
    private long statusUntil;

    public abstract String id();

    public abstract String title();

    public abstract ItemStack icon();

    /** Short label for the sidebar; defaults to {@link #title()}. */
    public String tabLabel() {
        return title();
    }

    /** Folder tabs shown above the panel while this section is open. */
    public java.util.List<String> tabs() {
        return java.util.List.of(title());
    }

    public int selectedTab() {
        return 0;
    }

    public void selectTab(int index) {
    }

    /** Unread count drawn on a folder tab. */
    public int tabBadge(int index) {
        return 0;
    }

    /** Text next to the icon in the panel header. */
    public String header() {
        return title();
    }

    /** Header area the section may use for its own widgets (search box): top and right limit. */
    protected int headerY;
    protected int headerRight;

    public final void setHeader(int top, int right) {
        this.headerY = top;
        this.headerRight = right;
    }

    /** Called on every (re)layout; create widgets here with {@link #add}. */
    protected abstract void build();

    public final void init(MainPanelScreen screen, int x, int y, int w, int h) {
        this.screen = screen;
        this.mc = MinecraftClient.getInstance();
        this.tr = mc.textRenderer;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        build();
    }

    public void tick() {
    }

    public void onShow() {
    }

    /** Drawn before widgets. */
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
    }

    /** Drawn after widgets (dropdowns, tooltips). */
    public void renderOverlay(DrawContext c, int mouseX, int mouseY, float delta) {
    }

    /** Clicks checked before widgets (used by dropdowns). */
    public boolean overlayClicked(double mx, double my, int button) {
        return false;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        return false;
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        return false;
    }

    /** Unread or pending count shown on the sidebar. */
    public int badge() {
        return 0;
    }

    protected <T extends Element & Drawable & Selectable> T add(T widget) {
        return screen.addChild(widget);
    }

    protected void status(String text, int color) {
        status = text;
        statusColor = color;
        statusUntil = System.currentTimeMillis() + 6000L;
    }

    protected void drawStatus(DrawContext c, int sx, int sy) {
        if (!status.isEmpty() && System.currentTimeMillis() < statusUntil) {
            c.drawText(tr, tr.trimToWidth(status, w - (sx - x)), sx, sy, statusColor, false);
        }
    }

    /** Status line on a small dark box, so it stays readable on top of a message list. */
    protected void drawStatusBox(DrawContext c, int sx, int sy) {
        if (status.isEmpty() || System.currentTimeMillis() >= statusUntil) {
            return;
        }
        String text = tr.trimToWidth(status, w - (sx - x) - 12);
        Theme.box(c, sx - 4, sy - 3, tr.getWidth(text) + 8, 14, Theme.R_SMALL,
                Theme.withAlpha(Theme.BASE, 0xEE), Theme.withAlpha(statusColor, 0x99));
        c.drawText(tr, text, sx, sy, statusColor, false);
    }

    protected void heading(DrawContext c, String text, int hx, int hy) {
        c.drawText(tr, text, hx, hy, Theme.ACCENT, false);
    }
}
