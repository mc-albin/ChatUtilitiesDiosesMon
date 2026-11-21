package chatutilities.ui.widget;

import chatutilities.ui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Gold "↓ Bajar" pill shown over a scrolled-up message list; clicking it jumps to the newest message. */
final class JumpButton {
    private JumpButton() {
    }

    /**
     * Draws the pill with its bottom-right corner at (right, bottom).
     *
     * @return {x, y, w, h} of the clickable area
     */
    static int[] draw(DrawContext c, TextRenderer tr, int right, int bottom, String detail) {
        String text = "↓ Bajar" + (detail.isEmpty() ? "" : " · " + detail);
        int w = tr.getWidth(text) + 12;
        int h = 14;
        int x = right - w;
        int y = bottom - h;
        var mc = MinecraftClient.getInstance();
        double mx = mc.mouse.getX() * mc.getWindow().getScaledWidth() / Math.max(1, mc.getWindow().getWidth()) / Theme.uiScale;
        double my = mc.mouse.getY() * mc.getWindow().getScaledHeight() / Math.max(1, mc.getWindow().getHeight()) / Theme.uiScale;
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        Theme.round(c, x, y, w, h, 6, hover ? Theme.ACCENT_HOVER : Theme.ACCENT);
        c.drawText(tr, text, x + 6, y + 3, Theme.ON_ACCENT, false);
        return new int[]{x, y, w, h};
    }
}
