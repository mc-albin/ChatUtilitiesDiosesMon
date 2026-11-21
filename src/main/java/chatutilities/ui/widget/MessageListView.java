package chatutilities.ui.widget;

import chatutilities.core.chat.ChatEntry;
import chatutilities.ui.Theme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Scrollable, bottom-anchored list of wrapped chat entries with a type colour bar. */
public class MessageListView {
    private static final int LINE = 10;

    private final Function<ChatEntry, Text> formatter;
    private String emptyText;
    private int x;
    private int y;
    private int w;
    private int h;
    private int scroll;
    private boolean stick = true;
    private long cacheKey = Long.MIN_VALUE;
    private int cacheWidth = -1;
    private final List<OrderedText> lines = new ArrayList<>();
    private final List<Integer> colors = new ArrayList<>();

    public MessageListView(Function<ChatEntry, Text> formatter, String emptyText) {
        this.formatter = formatter;
        this.emptyText = emptyText;
    }

    public void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    /** Text shown when the list is empty; empty string shows nothing. */
    public void setEmptyText(String text) {
        this.emptyText = text == null ? "" : text;
    }

    public void invalidate() {
        cacheKey = Long.MIN_VALUE;
    }

    public void toBottom() {
        stick = true;
        scroll = 0;
    }

    /**
     * @param key changes whenever the entry list changes (revision plus filter id)
     */
    public void render(DrawContext c, TextRenderer tr, long key, List<ChatEntry> entries) {
        // Borderless: the panel background is the only surface.
        int textWidth = w - 12;
        if (key != cacheKey || textWidth != cacheWidth) {
            lines.clear();
            colors.clear();
            for (ChatEntry e : entries) {
                for (OrderedText line : tr.wrapLines(formatter.apply(e), textWidth)) {
                    lines.add(line);
                    colors.add(Theme.typeColor(e.type()));
                }
            }
            cacheKey = key;
            cacheWidth = textWidth;
        }
        if (lines.isEmpty()) {
            int ey = y + 6;
            for (OrderedText line : tr.wrapLines(Text.literal(emptyText), w - 12)) {
                c.drawText(tr, line, x + 6, ey, Theme.MUTED, false);
                ey += LINE;
            }
            return;
        }
        int visible = Math.max(1, (h - 6) / LINE);
        int max = Math.max(0, lines.size() - visible);
        if (stick) {
            scroll = 0;
        }
        scroll = Math.max(0, Math.min(scroll, max));
        int start = Math.max(0, lines.size() - visible - scroll);
        int end = Math.min(lines.size(), start + visible);
        Theme.scissor(c, x + 1, y + 2, x + w - 1, y + h - 2);
        int ly = y + h - 3 - (end - start) * LINE;
        for (int i = start; i < end; i++) {
            c.fill(x + 3, ly + 1, x + 4, ly + LINE - 1, Theme.withAlpha(colors.get(i), 0xCC));
            c.drawText(tr, lines.get(i), x + 8, ly + 1, Theme.TEXT, true);
            ly += LINE;
        }
        c.disableScissor();
        if (max > 0) {
            int trackH = h - 4;
            int thumb = Math.max(10, trackH * visible / lines.size());
            int pos = (int) ((long) (trackH - thumb) * (max - scroll) / max);
            Theme.round(c, x + w - 4, y + 2 + pos, 2, thumb, 1, Theme.BORDER_STRONG);
        }
        jump = scroll > 0 ? JumpButton.draw(c, tr, x + w - 6, y + h - 4, scroll + (scroll == 1 ? " línea nueva" : " líneas nuevas")) : null;
    }

    private int[] jump;

    /** Click on the "↓ Bajar" button: back to the newest message. */
    public boolean clickJump(double mx, double my) {
        if (jump == null || mx < jump[0] || mx >= jump[0] + jump[2] || my < jump[1] || my >= jump[1] + jump[3]) {
            return false;
        }
        toBottom();
        return true;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public boolean scroll(double mx, double my, double amount) {
        if (!contains(mx, my)) {
            return false;
        }
        scroll += (int) Math.signum(amount) * 3;
        if (scroll <= 0) {
            scroll = 0;
            stick = true;
        } else {
            stick = false;
        }
        return true;
    }
}
