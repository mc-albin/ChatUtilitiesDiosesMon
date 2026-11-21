package chatutilities.ui.section;

import chatutilities.core.ChatUtilitiesCore;
import chatutilities.core.ChatText;
import chatutilities.core.commands.ServerCommands;
import chatutilities.core.pay.Currency;
import chatutilities.core.pay.PayCommand;
import chatutilities.core.pay.PayHistory;
import chatutilities.core.players.PlayerHeads;
import chatutilities.ui.Theme;
import chatutilities.ui.widget.PlayerPicker;
import chatutilities.ui.widget.UiButton;
import chatutilities.ui.widget.UiField;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /pay <player> <currency> <amount>} with a two-step confirmation on the left and the
 * payment history on the right (confirmed, rejected or waiting for the server).
 */
public class PaySection extends Section {
    private static final long CONFIRM_MS = 5000L;
    private static final int FORM_W = 214;
    private static final int HROW = 26;
    private static final long[] QUICK = {1_000L, 10_000L, 100_000L, 1_000_000L};
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());

    private final List<UiButton> currencies = new ArrayList<>();
    private Currency currency = Currency.POKEDOLARES;
    private PlayerPicker picker;
    private UiField amount;
    private UiButton pay;
    private String playerDraft = "";
    private String amountDraft = "";
    private long armedUntil;
    private String armedCommand = "";
    private int historyX;
    private int scroll;

    @Override
    public String id() {
        return "pay";
    }

    @Override
    public String title() {
        return "Pagos";
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.GOLD_INGOT);
    }

    @Override
    protected void build() {
        int fw = Math.min(FORM_W, w / 2);
        UiField player = add(new UiField(tr, x + 20, y + 12, fw - 20, 18, "Nombre del jugador…"));
        player.setText(playerDraft);
        picker = new PlayerPicker(player, s -> {
            playerDraft = s;
            disarm();
        }, null);
        currencies.clear();
        // Each button as wide as its label (plus padding), the spare width shared equally.
        Currency[] all = Currency.values();
        String[] labels = new String[all.length];
        int[] widths = new int[all.length];
        int used = 0;
        for (int i = 0; i < all.length; i++) {
            labels[i] = switch (all[i]) {
                case POKEDOLARES -> "Pokédólares";
                case BATTLE -> "Battle P.";
                case SAFARI -> "Safari P.";
            };
            widths[i] = tr.getWidth(labels[i]) + 12;
            used += widths[i];
        }
        int spare = Math.max(0, fw - used - 3 * (all.length - 1)) / all.length;
        int cx = x;
        for (int i = 0; i < all.length; i++) {
            Currency c = all[i];
            String label = labels[i];
            int cw = widths[i] + spare;
            UiButton b = add(new UiButton(cx, y + 48, cw, 18, label, UiButton.Style.NORMAL, () -> {
                currency = c;
                disarm();
                updateCurrencies();
            }).tooltip(c.label));
            currencies.add(b);
            cx += cw + 3;
        }
        updateCurrencies();
        amount = add(new UiField(tr, x, y + 84, fw, 20, "Cantidad"));
        amount.setMaxLength(16);
        // Only digits can be typed; dots are inserted automatically as thousands separators.
        amount.setTextPredicate(s -> s.matches("[0-9.]*") && s.replace(".", "").length() <= 12);
        amount.setText(amountDraft);
        amount.setChangedListener(s -> {
            String formatted = PayCommand.format(s);
            if (!formatted.equals(s)) {
                amount.setText(formatted);
                amount.setCursorToEnd(false);
                return;
            }
            amountDraft = s;
            disarm();
        });
        int qw = (fw - 3 * 3) / 4;
        for (int i = 0; i < QUICK.length; i++) {
            long add = QUICK[i];
            String label = "+" + (add >= 1_000_000 ? add / 1_000_000 + "M" : add / 1_000 + "K");
            add(new UiButton(x + i * (qw + 3), y + 108, qw, 14, label, UiButton.Style.NORMAL, () -> addAmount(add)));
        }
        pay = add(new UiButton(x, y + h - 22, fw, 22, "Pagar", UiButton.Style.ACCENT, this::press));
        historyX = x + fw + 14;
    }

    private void addAmount(long add) {
        long current = 0;
        try {
            current = Long.parseLong(amount.getText().replace(".", ""));
        } catch (NumberFormatException ignored) {
            // Empty field: start from zero.
        }
        amount.setText(PayCommand.format(Long.toString(Math.min(999_999_999_999L, current + add))));
    }

    private void updateCurrencies() {
        for (int i = 0; i < currencies.size(); i++) {
            Currency c = Currency.values()[i];
            UiButton b = currencies.get(i);
            b.selected(c == currency);
            b.tint(c == currency ? () -> c.color : null);
        }
    }

    private String preview() {
        try {
            return PayCommand.build(picker.value(), currency, amount.getText());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void disarm() {
        armedUntil = 0;
        armedCommand = "";
        if (pay != null) {
            pay.setLabel("Pagar");
            pay.setStyle(UiButton.Style.ACCENT);
        }
    }

    private void press() {
        String command;
        try {
            command = PayCommand.build(picker.value(), currency, amount.getText());
        } catch (IllegalArgumentException e) {
            status(e.getMessage(), Theme.WARN);
            return;
        }
        if (!ServerCommands.usable("pay")) {
            status("El servidor no ofrece /pay.", Theme.DANGER);
            return;
        }
        if (ServerCommands.payCurrency(currency.id) == ServerCommands.Support.NO) {
            status("El servidor no acepta la moneda \"" + currency.id + "\".", Theme.DANGER);
            return;
        }
        long now = System.currentTimeMillis();
        if (now < armedUntil && command.equals(armedCommand)) {
            ServerCommands.send(command);
            status("Pago enviado. Mira su estado en el historial.", Theme.SUCCESS);
            amount.setText("");
            disarm();
            return;
        }
        armedCommand = command;
        armedUntil = now + CONFIRM_MS;
        pay.setLabel("Pulsa otra vez para confirmar");
        pay.setStyle(UiButton.Style.DANGER);
    }

    @Override
    public void tick() {
        picker.tick();
        if (armedUntil != 0 && System.currentTimeMillis() > armedUntil) {
            disarm();
        }
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        int fw = Math.min(FORM_W, w / 2);
        c.drawText(tr, "Destinatario", x, y, Theme.MUTED, false);
        String name = picker.value();
        if (ChatText.validName(name)) {
            PlayerSkinDrawer.draw(c, PlayerHeads.get(name), x, y + 13, 16);
        } else {
            Theme.round(c, x, y + 13, 16, 16, Theme.R_SMALL, Theme.CARD);
        }
        c.drawText(tr, "Moneda", x, y + 37, Theme.MUTED, false);
        c.drawText(tr, "Cantidad", x, y + 73, Theme.MUTED, false);
        String cmd = preview();
        int sy = y + 130;
        if (cmd == null) {
            c.drawText(tr, "Completa jugador y cantidad.", x, sy, Theme.FAINT, false);
        } else {
            c.drawText(tr, "Vas a enviar", x, sy, Theme.MUTED, false);
            c.drawText(tr, tr.trimToWidth(amount.getText() + " " + currency.label, fw), x, sy + 11, currency.color, false);
            c.drawText(tr, tr.trimToWidth("a " + name, fw), x, sy + 22, Theme.TEXT, false);
        }
        drawStatus(c, x, y + h - 36);
        c.fill(historyX - 7, y, historyX - 6, y + h, Theme.BORDER);
        renderHistory(c, mouseX, mouseY);
    }

    private void renderHistory(DrawContext c, int mouseX, int mouseY) {
        int hx = historyX;
        int hw = x + w - hx;
        c.drawText(tr, "Historial de pagos", hx, y, Theme.ACCENT, false);
        int top = y + 13;
        int boxH = h - 13;
        List<PayHistory.Payment> all = ChatUtilitiesCore.PAYS.list();
        if (all.isEmpty()) {
            c.drawText(tr, "Aún no hay pagos.", hx, top + 4, Theme.FAINT, false);
            return;
        }
        int visible = Math.max(1, boxH / HROW);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, all.size() - visible)));
        long now = System.currentTimeMillis();
        int ry = top;
        for (int i = scroll; i < all.size() && i < scroll + visible; i++) {
            PayHistory.Payment p = all.get(i);
            boolean hover = mouseX >= hx && mouseX < hx + hw && mouseY >= ry && mouseY < ry + HROW - 2;
            if (hover) {
                Theme.round(c, hx - 2, ry, hw + 2, HROW - 2, Theme.R_SMALL, Theme.CARD_HOVER);
            }
            PlayerSkinDrawer.draw(c, PlayerHeads.get(p.player), hx, ry + 4, 16);
            int tx = hx + 21;
            Currency cur = currencyOf(p.currency);
            String unit = cur == null ? p.currency : switch (cur) {
                case POKEDOLARES -> "$";
                case BATTLE -> "BP";
                case SAFARI -> "SP";
            };
            String money = (p.outgoing ? "-" : "+") + PayCommand.format(Long.toString(p.amount)) + " " + unit;
            int moneyW = tr.getWidth(money);
            c.drawText(tr, tr.trimToWidth((p.outgoing ? "a " : "de ") + p.player, hw - 21 - moneyW - 8), tx, ry + 3,
                    Theme.TEXT, false);
            c.drawText(tr, money, hx + hw - 4 - moneyW, ry + 3,
                    p.rejected ? Theme.FAINT : p.outgoing ? Theme.DANGER_SOFT : Theme.SUCCESS, false);
            c.drawText(tr, DAY.format(Instant.ofEpochMilli(p.time)), tx, ry + 14, Theme.FAINT, false);
            String state;
            int color;
            if (!p.outgoing) {
                state = "Recibido";
                color = Theme.SUCCESS;
            } else if (p.rejected) {
                state = "Rechazado";
                color = Theme.DANGER;
            } else if (p.confirmed) {
                state = "Confirmado";
                color = Theme.SUCCESS;
            } else if (PayHistory.pending(p, now)) {
                state = "Enviando…";
                color = Theme.WARN;
            } else {
                state = "Sin confirmar";
                color = Theme.WARN;
            }
            int sw = tr.getWidth(state) + 8;
            int sx = hx + hw - 4 - sw;
            Theme.box(c, sx, ry + 13, sw, 11, Theme.R_SMALL, Theme.withAlpha(color, 0x26), Theme.withAlpha(color, 0x90));
            c.drawText(tr, state, sx + 4, ry + 15, color, false);
            ry += HROW;
        }
        if (all.size() > visible) {
            int thumb = Math.max(10, boxH * visible / all.size());
            int pos = (int) ((long) (boxH - thumb) * scroll / Math.max(1, all.size() - visible));
            Theme.round(c, x + w - 1, top + pos, 2, thumb, 1, Theme.BORDER_STRONG);
        }
    }

    private static Currency currencyOf(String id) {
        for (Currency c : Currency.values()) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (mx >= historyX && mx < x + w && my >= y && my < y + h) {
            scroll = Math.max(0, scroll - (int) Math.signum(amount));
            return true;
        }
        return false;
    }

    @Override
    public void renderOverlay(DrawContext c, int mouseX, int mouseY, float delta) {
        picker.renderOverlay(c, tr, mouseX, mouseY);
    }

    @Override
    public boolean overlayClicked(double mx, double my, int button) {
        return picker.mouseClicked(mx, my);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        return picker.keyPressed(key);
    }
}
