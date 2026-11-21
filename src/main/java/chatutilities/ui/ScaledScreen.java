package chatutilities.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Screen drawn at a steady size whatever Minecraft's GUI scale is: with GUI scale 3 or 4 it is
 * drawn smaller, so it looks the same as with GUI scale 2 instead of filling the whole screen.
 * Subclasses lay out and draw in the virtual coordinates; mouse input is converted for them.
 */
public abstract class ScaledScreen extends Screen {
    /** GUI scale the panel is designed for. */
    private static final double TARGET_GUI_SCALE = 2.0;

    /** Factor from virtual to real GUI coordinates (1 at GUI scale 1 and 2). */
    protected float ui = 1f;

    protected ScaledScreen(Text title) {
        super(title);
    }

    private float computeScale() {
        double gs = client.getWindow().getScaleFactor();
        return (float) Math.min(1.0, TARGET_GUI_SCALE / Math.max(1.0, gs));
    }

    @Override
    protected final void init() {
        ui = computeScale();
        width = Math.round(client.getWindow().getScaledWidth() / ui);
        height = Math.round(client.getWindow().getScaledHeight() / ui);
        initScaled();
    }

    protected abstract void initScaled();

    @Override
    public final void render(DrawContext c, int mouseX, int mouseY, float delta) {
        Theme.uiScale = ui;
        var m = c.getMatrices();
        m.push();
        m.scale(ui, ui, 1f);
        try {
            renderScaled(c, Math.round(mouseX / ui), Math.round(mouseY / ui), delta);
        } finally {
            m.pop();
            Theme.uiScale = 1f;
        }
        renderUnscaled(c, mouseX, mouseY, delta);
    }

    protected abstract void renderScaled(DrawContext c, int mouseX, int mouseY, float delta);

    /** Drawn after the scaled part, in real GUI coordinates (floating notices). */
    protected void renderUnscaled(DrawContext c, int mouseX, int mouseY, float delta) {
    }

    @Override
    public final boolean mouseClicked(double mx, double my, int button) {
        return mouseClickedScaled(mx / ui, my / ui, button);
    }

    protected boolean mouseClickedScaled(double mx, double my, int button) {
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public final boolean mouseReleased(double mx, double my, int button) {
        return super.mouseReleased(mx / ui, my / ui, button);
    }

    @Override
    public final boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return super.mouseDragged(mx / ui, my / ui, button, dx / ui, dy / ui);
    }

    @Override
    public final boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        return mouseScrolledScaled(mx / ui, my / ui, horizontal, vertical);
    }

    protected boolean mouseScrolledScaled(double mx, double my, double horizontal, double vertical) {
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public final void mouseMoved(double mx, double my) {
        super.mouseMoved(mx / ui, my / ui);
    }
}
