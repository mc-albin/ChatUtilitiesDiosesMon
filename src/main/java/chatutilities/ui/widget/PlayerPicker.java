package chatutilities.ui.widget;

import chatutilities.core.players.PlayerDirectory;
import chatutilities.ui.Theme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

/** Adds a player-name dropdown under a {@link UiField}. */
public class PlayerPicker {
    private static final int ROW = 12;
    private static final int LIMIT = 6;

    private final UiField field;
    private final Consumer<String> onPick;
    private List<String> shown = List.of();
    private boolean dismissed;

    /**
     * @param onType called with the field text on every change (use it to keep a draft)
     * @param onPick called when a name is chosen from the dropdown
     */
    public PlayerPicker(UiField field, Consumer<String> onType, Consumer<String> onPick) {
        this.field = field;
        this.onPick = onPick;
        field.setMaxLength(16);
        field.setTextPredicate(s -> s.matches("[A-Za-z0-9_]{0,16}"));
        field.setChangedListener(text -> {
            dismissed = false;
            PlayerDirectory.query(text);
            if (onType != null) {
                onType.accept(text);
            }
        });
    }

    public UiField field() {
        return field;
    }

    public String value() {
        return field.getText().strip();
    }

    private boolean open() {
        // Only while typing: an empty, just-focused field never opens the list.
        return field.isFocused() && !dismissed && !value().isEmpty() && !shown.isEmpty()
                && !(shown.size() == 1 && shown.get(0).equalsIgnoreCase(value()));
    }

    public void tick() {
        shown = field.isFocused() ? PlayerDirectory.matches(value(), LIMIT) : List.of();
    }

    public void renderOverlay(DrawContext c, TextRenderer tr, int mouseX, int mouseY) {
        if (!open()) {
            return;
        }
        int x = field.boxX();
        int y = field.boxY() + field.boxH() + 1;
        int w = field.boxW();
        c.getMatrices().push();
        c.getMatrices().translate(0, 0, 400);
        Theme.box(c, x, y, w, shown.size() * ROW + 2, Theme.withAlpha(Theme.BASE, 0xF2), Theme.BORDER_STRONG);
        for (int i = 0; i < shown.size(); i++) {
            int ry = y + 1 + i * ROW;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + ROW;
            if (hover) {
                c.fill(x + 1, ry, x + w - 1, ry + ROW, Theme.SELECT);
            }
            c.drawText(tr, shown.get(i), x + 5, ry + 2, hover ? Theme.TEXT : Theme.MUTED, false);
        }
        c.getMatrices().pop();
    }

    public boolean mouseClicked(double mx, double my) {
        if (!open()) {
            return false;
        }
        int x = field.boxX();
        int y = field.boxY() + field.boxH() + 2;
        if (mx < x || mx >= x + field.boxW() || my < y || my >= y + shown.size() * ROW) {
            return false;
        }
        pick(shown.get((int) ((my - y) / ROW)));
        return true;
    }

    public boolean keyPressed(int key) {
        if (!open()) {
            return false;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            pick(shown.get(0));
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            dismissed = true;
            return true;
        }
        return false;
    }

    private void pick(String name) {
        field.setText(name);
        field.setCursorToEnd(false);
        dismissed = true;
        if (onPick != null) {
            onPick.accept(name);
        }
    }
}
