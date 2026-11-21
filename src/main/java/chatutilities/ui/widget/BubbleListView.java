package chatutilities.ui.widget;

import chatutilities.core.chat.ChatEntry;
import chatutilities.core.chat.MessageType;
import chatutilities.core.players.PlayerHeads;
import chatutilities.ui.Theme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Private conversation drawn as chat bubbles: received messages on the left with the player's
 * head, your messages on the right in gold. Bottom-anchored, scrolls by pixels.
 */
public class BubbleListView {
    private static final int LINE = 10;
    private static final int PAD = 4;
    private static final int GAP = 4;
    private static final int HEAD = 8;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());

    private record Bubble(boolean mine, String peer, List<OrderedText> lines, int width, String time, int height) {
    }

    private int x;
    private int y;
    private int w;
    private int h;
    private int scroll;
    private long cacheKey = Long.MIN_VALUE;
    private int cacheWidth = -1;
    private List<Bubble> bubbles = List.of();
    private int total;
    private String emptyText = "";

    public void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void setEmptyText(String text) {
        this.emptyText = text == null ? "" : text;
    }

    public void toBottom() {
        scroll = 0;
    }

    public void invalidate() {
        cacheKey = Long.MIN_VALUE;
    }

    public void render(DrawContext c, TextRenderer tr, long key, List<ChatEntry> entries) {
        int maxText = Math.max(40, (int) (w * 0.68f) - PAD * 2);
        if (key != cacheKey || w != cacheWidth) {
            List<Bubble> out = new ArrayList<>();
            int sum = 0;
            LocalDate today = LocalDate.now();
            for (ChatEntry e : entries) {
                boolean mine = e.type() == MessageType.PRIVATE_OUT;
                List<OrderedText> lines = tr.wrapLines(Text.literal(e.body()), maxText);
                int lw = 0;
                for (OrderedText l : lines) {
                    lw = Math.max(lw, tr.getWidth(l));
                }
                Instant at = Instant.ofEpochMilli(e.time());
                boolean isToday = at.atZone(ZoneId.systemDefault()).toLocalDate().equals(today);
                String time = (isToday ? TIME : DAY).format(at);
                int bh = lines.size() * LINE + PAD * 2 - 1;
                out.add(new Bubble(mine, e.peer(), lines, lw + PAD * 2, time, bh));
                sum += bh + GAP;
            }
            bubbles = out;
            total = sum;
            cacheKey = key;
            cacheWidth = w;
        }
        if (bubbles.isEmpty()) {
            if (!emptyText.isEmpty()) {
                c.drawText(tr, tr.trimToWidth(emptyText, w - 8), x + 4, y + 4, Theme.MUTED, false);
            }
            return;
        }
        int max = Math.max(0, total - h + 4);
        scroll = Math.max(0, Math.min(scroll, max));
        Theme.scissor(c, x, y, x + w, y + h);
        int by = y + h - 2 + scroll;
        for (int i = bubbles.size() - 1; i >= 0; i--) {
            Bubble b = bubbles.get(i);
            by -= b.height();
            if (by + b.height() < y) {
                break;
            }
            if (by <= y + h) {
                drawBubble(c, tr, b, by);
            }
            by -= GAP;
        }
        c.disableScissor();
        if (max > 0) {
            int thumb = Math.max(10, h * h / Math.max(h, total));
            int pos = (int) ((long) (h - thumb) * (max - scroll) / max);
            Theme.round(c, x + w - 2, y + pos, 2, thumb, 1, Theme.BORDER_STRONG);
        }
        jump = scroll > 0 ? JumpButton.draw(c, tr, x + w - 6, y + h - 4, "") : null;
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

    private void drawBubble(DrawContext c, TextRenderer tr, Bubble b, int by) {
        int timeW = tr.getWidth(b.time());
        if (b.mine()) {
            int bx = x + w - 8 - b.width();
            Theme.box(c, bx, by, b.width(), b.height(), Theme.R_CARD, Theme.withAlpha(Theme.ACCENT, 0x40),
                    Theme.withAlpha(Theme.ACCENT, 0x99));
            drawLines(c, tr, b, bx + PAD, by + PAD, Theme.BRIGHT);
            c.drawText(tr, b.time(), bx - 4 - timeW, by + b.height() - 9, Theme.FAINT, false);
        } else {
            PlayerSkinDrawer.draw(c, PlayerHeads.get(b.peer()), x + 2, by + 2, HEAD);
            int bx = x + 4 + HEAD + 3;
            Theme.box(c, bx, by, b.width(), b.height(), Theme.R_CARD, Theme.withAlpha(Theme.BASE, 0xD0),
                    Theme.BORDER_STRONG);
            drawLines(c, tr, b, bx + PAD, by + PAD, Theme.TEXT);
            c.drawText(tr, b.time(), bx + b.width() + 4, by + b.height() - 9, Theme.FAINT, false);
        }
    }

    private static void drawLines(DrawContext c, TextRenderer tr, Bubble b, int tx, int ty, int color) {
        for (OrderedText line : b.lines()) {
            c.drawText(tr, line, tx, ty, color, false);
            ty += LINE;
        }
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public boolean scroll(double mx, double my, double amount) {
        if (!contains(mx, my)) {
            return false;
        }
        scroll = Math.max(0, scroll + (int) Math.signum(amount) * 20);
        return true;
    }
}
