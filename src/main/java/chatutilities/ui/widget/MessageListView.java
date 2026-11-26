package chatutilities.ui.widget;

import chatutilities.core.chat.ChatEntry;
import chatutilities.ui.Theme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
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
    /** Entry of each wrapped line, and the author's name span {from, to} in pixels (null if none). */
    private final List<ChatEntry> lineEntries = new ArrayList<>();
    private final List<int[]> nameSpans = new ArrayList<>();
    /** Visible lines of the last frame: first index, top y of the first one. */
    private int shownStart;
    private int shownEnd;
    private int shownTop;

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
        render(c, tr, key, entries, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    /** Same, underlining the author's name under the mouse (right click opens its menu). */
    public void render(DrawContext c, TextRenderer tr, long key, List<ChatEntry> entries, int mouseX, int mouseY) {
        // Borderless: the panel background is the only surface.
        int textWidth = w - 12;
        if (key != cacheKey || textWidth != cacheWidth) {
            lines.clear();
            colors.clear();
            lineEntries.clear();
            nameSpans.clear();
            for (ChatEntry e : entries) {
                boolean first = true;
                for (OrderedText line : tr.wrapLines(formatter.apply(e), textWidth)) {
                    lines.add(line);
                    colors.add(Theme.typeColor(e.type()));
                    lineEntries.add(e);
                    nameSpans.add(first && !e.fromMe() ? nameSpan(tr, line, e.sender()) : null);
                    first = false;
                }
            }
            cacheKey = key;
            cacheWidth = textWidth;
        }
        if (lines.isEmpty()) {
            shownStart = 0;
            shownEnd = 0;
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
        shownStart = start;
        shownEnd = end;
        shownTop = ly;
        int hovered = nameLineAt(mouseX, mouseY);
        for (int i = start; i < end; i++) {
            c.fill(x + 3, ly + 1, x + 4, ly + LINE - 1, Theme.withAlpha(colors.get(i), 0xCC));
            if (i == hovered) {
                int[] span = nameSpans.get(i);
                Theme.round(c, x + 8 + span[0] - 2, ly, span[1] - span[0] + 4, LINE, 2, Theme.withAlpha(Theme.ACCENT, 0x30));
                c.fill(x + 8 + span[0], ly + LINE - 1, x + 8 + span[1], ly + LINE, Theme.ACCENT);
            }
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

    /** Pixel span of the author's name inside the first line of a message, or null. */
    private static int[] nameSpan(TextRenderer tr, OrderedText line, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        List<Integer> cps = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        line.accept((index, style, cp) -> {
            cps.add(cp);
            styles.add(style);
            sb.appendCodePoint(cp);
            return true;
        });
        String s = sb.toString();
        int from = -1;
        for (int at = s.indexOf(name); at >= 0; at = s.indexOf(name, at + 1)) {
            boolean left = at == 0 || !nameChar(s.charAt(at - 1));
            int endAt = at + name.length();
            boolean right = endAt >= s.length() || !nameChar(s.charAt(endAt));
            if (left && right) {
                from = at;
                break;
            }
        }
        if (from < 0) {
            return null;
        }
        // Code point positions (names are ASCII, but earlier symbols may not be).
        int cpFrom = s.codePointCount(0, from);
        int cpTo = cpFrom + name.codePointCount(0, name.length());
        return new int[]{prefixWidth(tr, cps, styles, cpFrom), prefixWidth(tr, cps, styles, cpTo)};
    }

    private static boolean nameChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }

    private static int prefixWidth(TextRenderer tr, List<Integer> cps, List<Style> styles, int count) {
        OrderedText prefix = visitor -> {
            for (int i = 0; i < count; i++) {
                if (!visitor.accept(i, styles.get(i), cps.get(i))) {
                    return false;
                }
            }
            return true;
        };
        return tr.getWidth(prefix);
    }

    /** Index of the visible line whose author name is under the mouse, or -1. */
    private int nameLineAt(double mx, double my) {
        if (!contains(mx, my) || my < shownTop) {
            return -1;
        }
        int i = shownStart + (int) ((my - shownTop) / LINE);
        if (i < shownStart || i >= shownEnd || i >= nameSpans.size()) {
            return -1;
        }
        int[] span = nameSpans.get(i);
        if (span == null || mx < x + 8 + span[0] - 2 || mx >= x + 8 + span[1] + 2) {
            return -1;
        }
        return i;
    }

    /** The message whose author name is under the mouse, or null. */
    public ChatEntry nameAt(double mx, double my) {
        int i = nameLineAt(mx, my);
        return i < 0 ? null : lineEntries.get(i);
    }

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
