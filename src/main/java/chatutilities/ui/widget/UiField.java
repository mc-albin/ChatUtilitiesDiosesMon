package chatutilities.ui.widget;

import chatutilities.ui.Theme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Text field drawn as a dark box. The box is larger than the vanilla text area. */
public class UiField extends TextFieldWidget {
    private final int bx;
    private final int by;
    private final int bw;
    private final int bh;

    public UiField(TextRenderer tr, int x, int y, int w, int h, String placeholder) {
        super(tr, x + 5, y + (h - 8) / 2, w - 10, h - (h - 8) / 2, Text.empty());
        this.bx = x;
        this.by = y;
        this.bw = w;
        this.bh = h;
        setDrawsBackground(false);
        setMaxLength(256);
        setPlaceholder(Text.literal(placeholder).withColor(Theme.FAINT));
    }

    public int boxX() {
        return bx;
    }

    public int boxY() {
        return by;
    }

    public int boxW() {
        return bw;
    }

    public int boxH() {
        return bh;
    }

    @Override
    public void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
        if (!isVisible()) {
            return;
        }
        Theme.box(c, bx, by, bw, bh, Theme.R_CARD, Theme.FIELD, isFocused() ? Theme.withAlpha(Theme.EDGE, 0xCC) : Theme.BORDER);
        super.renderWidget(c, mouseX, mouseY, delta);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return isVisible() && mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
    }
}
