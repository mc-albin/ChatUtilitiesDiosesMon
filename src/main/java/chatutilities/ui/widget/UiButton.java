package chatutilities.ui.widget;

import chatutilities.ui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** Flat dark button with styles, optional item icon and selected state. */
public class UiButton extends ClickableWidget {
    public enum Style { NORMAL, ACCENT, SUCCESS, DANGER, TAB, NAV, QUICK }

    private final Runnable action;
    private Style style;
    private boolean selected;
    private ItemStack icon = ItemStack.EMPTY;
    /** Optional live colour (ARGB) that tints the fill and border, e.g. the AFK state. */
    private java.util.function.IntSupplier tint;

    public UiButton(int x, int y, int w, int h, String label, Style style, Runnable action) {
        super(x, y, w, h, Text.literal(label));
        this.style = style;
        this.action = action;
    }

    public UiButton icon(ItemStack stack) {
        this.icon = stack == null ? ItemStack.EMPTY : stack;
        return this;
    }

    public UiButton tooltip(String text) {
        setTooltip(text == null || text.isEmpty() ? null : Tooltip.of(Text.literal(text)));
        return this;
    }

    /** 16x16 texture drawn centred instead of a label or item. */
    private net.minecraft.util.Identifier texture;

    public UiButton texture(net.minecraft.util.Identifier id) {
        this.texture = id;
        return this;
    }

    public UiButton tint(java.util.function.IntSupplier color) {
        this.tint = color;
        return this;
    }

    public UiButton selected(boolean value) {
        this.selected = value;
        return this;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setStyle(Style style) {
        this.style = style;
    }

    public void setLabel(String label) {
        setMessage(Text.literal(label));
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (action != null) {
            action.run();
        }
    }

    @Override
    protected void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        boolean hover = isHovered() && active;
        int fill;
        int border;
        int color;
        switch (style) {
            case ACCENT -> {
                fill = !active ? Theme.withAlpha(Theme.ACCENT, 0x33) : hover || selected ? Theme.ACCENT_HOVER : Theme.ACCENT;
                border = 0;
                color = active ? Theme.ON_ACCENT : Theme.FAINT;
            }
            case TAB, NAV -> {
                fill = selected ? Theme.SELECT : hover ? Theme.CARD_HOVER : 0;
                border = 0;
                color = selected ? Theme.TEXT : hover ? Theme.TEXT : Theme.DIM;
            }
            case SUCCESS, DANGER, QUICK -> {
                int accent = style == Style.SUCCESS ? Theme.SUCCESS : style == Style.DANGER ? Theme.DANGER : Theme.GOLD;
                fill = !active ? 0x40000000 : Theme.withAlpha(accent, hover ? 0x55 : selected ? 0x48 : 0x26);
                border = !active ? Theme.BORDER : Theme.withAlpha(accent, hover ? 0xCC : 0x80);
                color = active ? Theme.TEXT : Theme.FAINT;
            }
            default -> {
                fill = !active ? 0x40000000 : selected ? Theme.SELECT : hover ? Theme.withAlpha(Theme.ACCENT_HOVER, 0x33) : Theme.withAlpha(Theme.BASE, 0x99);
                border = selected ? Theme.BORDER_STRONG : hover ? Theme.BORDER_STRONG : Theme.BORDER;
                color = active ? Theme.TEXT : Theme.FAINT;
            }
        }
        if (tint != null && active) {
            int t = tint.getAsInt();
            fill = Theme.withAlpha(t, hover ? 0x70 : 0x45);
            border = Theme.withAlpha(t, hover ? 0xFF : 0xCC);
        }
        int x = getX();
        int y = getY();
        Theme.box(c, x, y, width, height, Math.min(Theme.R_CARD, height / 3), fill, border);
        if (style == Style.TAB && selected) {
            Theme.round(c, x + 2, y + 4, 2, height - 8, 1, Theme.ACCENT);
        }
        if (style == Style.NAV) {
            String text = getMessage().getString();
            int contentW = (icon.isEmpty() ? 0 : 20) + tr.getWidth(text);
            int cx = x + (width - contentW) / 2;
            if (!icon.isEmpty()) {
                c.drawItem(icon, cx, y + (height - 16) / 2);
                cx += 20;
            }
            c.drawText(tr, text, cx, y + (height - 8) / 2, color, false);
            if (selected) {
                Theme.round(c, x + width / 2 - 8, y + height - 3, 16, 2, 1, Theme.ACCENT);
            }
            return;
        }
        int textX = x + 4;
        int available = width - 8;
        boolean hasIcon = !icon.isEmpty();
        if (texture != null) {
            if (!active) {
                c.setShaderColor(0.45f, 0.45f, 0.45f, 1f);
            }
            c.drawTexture(texture, x + (width - 16) / 2, y + (height - 16) / 2, 0, 0, 16, 16, 16, 16);
            c.setShaderColor(1f, 1f, 1f, 1f);
            return;
        }
        if (hasIcon && getMessage().getString().isEmpty()) {
            if (!active) {
                c.setShaderColor(0.45f, 0.45f, 0.45f, 1f);
            }
            c.drawItem(icon, x + (width - 16) / 2, y + (height - 16) / 2);
            c.setShaderColor(1f, 1f, 1f, 1f);
            return;
        }
        if (hasIcon) {
            if (style == Style.QUICK) {
                c.drawItem(icon, x + (width - 16) / 2, y + 5);
            } else {
                c.drawItem(icon, x + (style == Style.TAB ? 8 : 4), y + (height - 16) / 2);
                textX = x + (style == Style.TAB ? 28 : 23);
                available = width - (textX - x) - 4;
            }
        }
        String label = tr.trimToWidth(getMessage().getString(), Math.max(0, available));
        int ty = style == Style.QUICK && hasIcon ? y + height - 13 : y + (height - 8) / 2;
        if (style == Style.TAB || hasIcon && style != Style.QUICK) {
            c.drawText(tr, label, textX, ty, color, false);
        } else {
            c.drawText(tr, label, x + (width - tr.getWidth(label)) / 2, ty, color, false);
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
