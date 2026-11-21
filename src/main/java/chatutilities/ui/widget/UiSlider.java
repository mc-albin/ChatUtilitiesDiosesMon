package chatutilities.ui.widget;

import chatutilities.ui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * Themed slider: click or drag anywhere on it to pick a value between {@code min} and {@code max}
 * in {@code step} steps. The arrow keys also move it. {@code onChange} runs on every change,
 * {@code onDone} when the mouse is released (save there).
 */
public class UiSlider extends ClickableWidget {
    private final String label;
    private final double min;
    private final double max;
    private final double step;
    private final DoubleFunction<String> format;
    private final DoubleConsumer onChange;
    private final Runnable onDone;
    private double value;

    public UiSlider(int x, int y, int w, int h, String label, double min, double max, double step, double value,
                    DoubleFunction<String> format, DoubleConsumer onChange, Runnable onDone) {
        super(x, y, w, h, Text.literal(label));
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.format = format;
        this.onChange = onChange;
        this.onDone = onDone;
        this.value = snap(value);
    }

    /** Moves the slider without running {@code onChange} (used to follow a hex code typed elsewhere). */
    public void setValue(double v) {
        value = snap(v);
    }

    private double snap(double v) {
        double s = Math.round((v - min) / step) * step + min;
        return Math.max(min, Math.min(max, s));
    }

    private void set(double v) {
        double s = snap(v);
        if (Math.abs(s - value) > 1e-9) {
            value = s;
            onChange.accept(s);
        }
    }

    private void setFromMouse(double mx) {
        double t = (mx - (getX() + 4)) / Math.max(1, width - 8);
        set(min + Math.max(0, Math.min(1, t)) * (max - min));
    }

    @Override
    public void onClick(double mx, double my) {
        setFromMouse(mx);
    }

    @Override
    protected void onDrag(double mx, double my, double dx, double dy) {
        setFromMouse(mx);
    }

    @Override
    public void onRelease(double mx, double my) {
        if (onDone != null) {
            onDone.run();
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            set(value + (key == GLFW.GLFW_KEY_LEFT ? -step : step));
            if (onDone != null) {
                onDone.run();
            }
            return true;
        }
        return false;
    }

    @Override
    protected void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int x = getX();
        int y = getY();
        boolean hover = isHovered();
        Theme.box(c, x, y, width, height, Theme.R_SMALL, Theme.FIELD, hover ? Theme.BORDER_STRONG : Theme.BORDER);
        double t = max > min ? (value - min) / (max - min) : 0;
        int fillW = (int) Math.round((width - 4) * t);
        if (fillW > 0) {
            Theme.round(c, x + 2, y + 2, fillW, height - 4, 2, Theme.withAlpha(Theme.ACCENT, hover ? 0x66 : 0x48));
        }
        int kx = x + 2 + Math.max(0, Math.min(width - 7, fillW - 2));
        Theme.round(c, kx, y + 1, 3, height - 2, 1, Theme.ACCENT);
        String text = label + ": " + format.apply(value);
        c.drawText(tr, text, x + (width - tr.getWidth(text)) / 2, y + (height - 8) / 2, Theme.TEXT, true);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
