package chatutilities.ui.section;

import chatutilities.core.ChatUtilitiesCore;
import chatutilities.core.commands.ServerCommands;
import chatutilities.core.players.PlayerDirectory;
import chatutilities.core.tp.TeleportTracker;
import chatutilities.ui.Theme;
import chatutilities.ui.widget.PlayerPicker;
import chatutilities.ui.widget.UiButton;
import chatutilities.ui.widget.UiField;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Incoming /tpa and /tpahere requests with accept/reject, full history and sending requests. */
public class TeleportSection extends Section {
    private static final int CARD = 26;
    private static final int MAX_CARDS = 2;
    private static final int ROW = 13;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());

    private final List<UiButton> accept = new ArrayList<>();
    private final List<UiButton> deny = new ArrayList<>();
    private List<TeleportTracker.Request> shown = List.of();
    private PlayerPicker picker;
    private String draft = "";
    private int historyTop;
    private int historyBottom;
    private int scroll;

    @Override
    public String id() {
        return "tp";
    }

    @Override
    public String title() {
        return "Teletransporte";
    }

    @Override
    public String tabLabel() {
        return "TP";
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.ENDER_PEARL);
    }

    @Override
    public int badge() {
        return ChatUtilitiesCore.TELEPORTS.pending().size();
    }

    @Override
    protected void build() {
        accept.clear();
        deny.clear();
        int listTop = y + 14;
        for (int i = 0; i < MAX_CARDS; i++) {
            int row = i;
            int cy = listTop + i * (CARD + 3);
            accept.add(add(new UiButton(x + w - 128, cy + 5, 60, 16, "Aceptar", UiButton.Style.SUCCESS,
                    () -> respond(row, true))));
            deny.add(add(new UiButton(x + w - 64, cy + 5, 60, 16, "Rechazar", UiButton.Style.DANGER,
                    () -> respond(row, false))));
        }
        int sy = y + h - 20;
        UiField field = add(new UiField(tr, x, sy, w - 196, 20, "Jugador…"));
        field.setText(draft);
        picker = new PlayerPicker(field, s -> draft = s, null);
        add(new UiButton(x + w - 192, sy, 48, 20, "TPA", UiButton.Style.ACCENT, () -> send("tpa")))
                .tooltip("Pedir ir hacia el jugador (/tpa)");
        add(new UiButton(x + w - 140, sy, 64, 20, "TPAHERE", UiButton.Style.ACCENT, () -> send("tpahere")))
                .tooltip("Pedir que el jugador venga (/tpahere)");
        add(new UiButton(x + w - 72, sy, 72, 20, "Cancelar", UiButton.Style.NORMAL, this::cancelOwn))
                .tooltip("Cancela tu solicitud enviada (/tpcancel)");
        refresh();
    }

    private void refresh() {
        shown = ChatUtilitiesCore.TELEPORTS.pending().reversed();
        for (int i = 0; i < MAX_CARDS; i++) {
            boolean visible = i < shown.size();
            accept.get(i).visible = visible;
            deny.get(i).visible = visible;
            accept.get(i).active = ServerCommands.usable("tpaccept");
        }
    }

    private void respond(int row, boolean ok) {
        if (row >= shown.size()) {
            return;
        }
        TeleportTracker.Request r = shown.get(row);
        String command = ok ? "tpaccept " + r.nick() : ServerCommands.denyCommand(r.nick());
        if (ServerCommands.send(command)) {
            ChatUtilitiesCore.TELEPORTS.mark(r.id(), ok ? TeleportTracker.State.ACCEPTED : TeleportTracker.State.DENIED);
            status(ok ? "Aceptada la solicitud de " + r.nick() : "Rechazada la solicitud de " + r.nick(),
                    ok ? Theme.SUCCESS : Theme.DANGER);
        } else {
            status("Sin conexión con el servidor.", Theme.DANGER);
        }
        refresh();
    }

    private void send(String base) {
        String nick = picker.value();
        if (!chatutilities.core.ChatText.validName(nick)) {
            status("Escribe un nombre de jugador válido.", Theme.WARN);
            return;
        }
        if (nick.equalsIgnoreCase(ChatUtilitiesCore.self())) {
            status("No puedes enviarte una solicitud a ti mismo.", Theme.WARN);
            return;
        }
        if (!ServerCommands.usable(base)) {
            status("El servidor no ofrece /" + base + ".", Theme.DANGER);
            return;
        }
        ServerCommands.send(base + " " + nick);
        status("Solicitud enviada a " + nick + ".", Theme.SUCCESS);
    }

    private void cancelOwn() {
        if (ServerCommands.send("tpcancel")) {
            status("Solicitud cancelada.", Theme.MUTED);
        }
    }

    @Override
    public void tick() {
        refresh();
        picker.tick();
    }

    private static int stateColor(TeleportTracker.State s) {
        return switch (s) {
            case ACCEPTED -> Theme.SUCCESS;
            case DENIED, CANCELLED, CANCELLED_BY_YOU -> Theme.DANGER;
            case SENT -> Theme.INFO;
            case EXPIRED -> Theme.WARN;
            default -> Theme.MUTED;
        };
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        heading(c, "Solicitudes recibidas", x, y + 2);
        String count = shown.isEmpty() ? "Ninguna" : shown.size() + " pendiente" + (shown.size() == 1 ? "" : "s");
        c.drawText(tr, count, x + w - tr.getWidth(count), y + 2, Theme.MUTED, false);
        int listTop = y + 14;
        long now = System.currentTimeMillis();
        int cardsBottom;
        if (shown.isEmpty()) {
            // No box: just the text.
            c.drawText(tr, "No hay solicitudes pendientes.", x + 8, listTop + 6, Theme.MUTED, false);
            cardsBottom = listTop + 20;
        } else {
            int n = Math.min(MAX_CARDS, shown.size());
            for (int i = 0; i < n; i++) {
                TeleportTracker.Request r = shown.get(i);
                int cy = listTop + i * (CARD + 3);
                Theme.round(c, x, cy, w, CARD, Theme.R_CARD, Theme.CARD);
                Theme.round(c, x + 3, cy + 5, 2, CARD - 10, 1, r.kind() == TeleportTracker.Kind.TO_YOU ? Theme.INFO : Theme.WARN);
                int tx = x + 10;
                PlayerListEntry entry = PlayerDirectory.entry(r.nick());
                if (entry != null) {
                    PlayerSkinDrawer.draw(c, entry.getSkinTextures(), x + 9, cy + 5, 16);
                    tx = x + 30;
                }
                long age = (now - r.time()) / 1000;
                c.drawText(tr, r.nick(), tx, cy + 4, Theme.TEXT, false);
                c.drawText(tr, tr.trimToWidth(r.kind().label + " · hace " + age + "s", x + w - 134 - tx), tx, cy + 15,
                        TeleportTracker.TIMEOUT_MS / 1000 - age <= 10 ? Theme.WARN : Theme.MUTED, false);
            }
            cardsBottom = listTop + n * (CARD + 3) - 3;
            if (shown.size() > MAX_CARDS) {
                c.drawText(tr, "+" + (shown.size() - MAX_CARDS) + " más", x + w - 40, cardsBottom + 2, Theme.MUTED, false);
            }
        }
        int hy = cardsBottom + 8;
        heading(c, "Historial", x, hy);
        drawStatus(c, x + tr.getWidth("Historial") + 10, hy);
        historyTop = hy + 12;
        historyBottom = y + h - 26;
        renderHistory(c, mouseX, mouseY);
    }

    private void renderHistory(DrawContext c, int mouseX, int mouseY) {
        int boxH = historyBottom - historyTop;
        if (boxH < ROW + 4) {
            return;
        }
        c.fill(x, historyTop - 2, x + w, historyTop - 1, Theme.BORDER);
        List<TeleportTracker.Request> all = ChatUtilitiesCore.TELEPORTS.history();
        if (all.isEmpty()) {
            c.drawText(tr, "Aún no hay teletransportes en el historial.", x + 8, historyTop + 6, Theme.MUTED, false);
            return;
        }
        int visible = (boxH - 4) / ROW;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, all.size() - visible)));
        int ry = historyTop + 3;
        LocalDate today = LocalDate.now();
        for (int i = scroll; i < all.size() && i < scroll + visible; i++) {
            TeleportTracker.Request r = all.get(i);
            Instant at = Instant.ofEpochMilli(r.time());
            boolean isToday = at.atZone(ZoneId.systemDefault()).toLocalDate().equals(today);
            String time = (isToday ? TIME : DAY).format(at);
            int tx = x + 6;
            c.drawText(tr, time, tx, ry + 2, Theme.FAINT, false);
            tx += tr.getWidth("00/00 00:00") + 6;
            c.drawText(tr, r.kind().outgoing ? "↑" : "↓", tx, ry + 2, r.kind().outgoing ? Theme.INFO : Theme.WARN, false);
            tx += 10;
            c.drawText(tr, r.nick(), tx, ry + 2, Theme.TEXT, false);
            tx += tr.getWidth(r.nick()) + 6;
            String state = r.state().label;
            int stateX = x + w - 8 - tr.getWidth(state);
            c.drawText(tr, tr.trimToWidth(r.kind().label, Math.max(0, stateX - tx - 6)), tx, ry + 2, Theme.MUTED, false);
            c.drawText(tr, state, stateX, ry + 2, stateColor(r.state()), false);
            ry += ROW;
        }
        if (all.size() > visible) {
            int trackH = boxH - 4;
            int thumb = Math.max(8, trackH * visible / all.size());
            int pos = (int) ((long) (trackH - thumb) * scroll / Math.max(1, all.size() - visible));
            Theme.round(c, x + w - 4, historyTop + 2 + pos, 2, thumb, 1, Theme.BORDER_STRONG);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (mx >= x && mx < x + w && my >= historyTop && my < historyBottom) {
            scroll -= (int) Math.signum(amount) * 2;
            scroll = Math.max(0, scroll);
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
