package chatutilities.hud;

import net.minecraft.client.gui.DrawContext;

/** Pushes a matrix that scales around the right edge, vertically centred (where scoreboards sit). */
public final class ScaleHelper {
    private ScaleHelper() {
    }

    public static void push(DrawContext context, float scale) {
        var m = context.getMatrices();
        m.push();
        if (scale < 0.999f) {
            float ax = context.getScaledWindowWidth();
            float ay = context.getScaledWindowHeight() / 2f;
            m.translate(ax, ay, 0);
            m.scale(scale, scale, 1f);
            m.translate(-ax, -ay, 0);
        }
    }
}
