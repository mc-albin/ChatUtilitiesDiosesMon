package chatutilities.ui.section;

import chatutilities.core.ChatText;
import chatutilities.core.ChatUtilitiesCore;
import chatutilities.core.chat.ChatEntry;
import chatutilities.core.chat.ChatLog;
import chatutilities.core.chat.MessageType;
import chatutilities.core.commands.ServerCommands;
import chatutilities.core.players.PlayerHeads;
import chatutilities.core.raids.RaidChat;
import chatutilities.core.raids.RaidParty;
import chatutilities.ui.Theme;
import chatutilities.ui.widget.PlayerPicker;
import chatutilities.ui.widget.UiButton;
import chatutilities.ui.widget.UiField;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * "Raids" view of the chat page: raid posts from public and clan chat (with stars and Pokémon)
 * with one-click TPA / invite, plus your own raid party driven by the {@code /raids} commands.
 */
final class RaidsPane {
    static final int SIDE = 158;
    private static final int ROW = 28;
    private static final int BTN_H = 12;
    private static final long MAX_AGE_MS = 90 * 60_000L;

    private record Post(ChatEntry entry, RaidChat.Info info) {
    }

    private final ChatSection owner;
    private TextRenderer tr;
    private List<Post> posts = List.of();
    private long postsRev = -1;
    private long postsMinute = -1;
    private int scroll;
    private String inviteDraft = "";
    /** Players already invited (lower case) and when; their older posts are hidden. */
    private final Map<String, Long> invited = new HashMap<>();

    private int fx;
    private int fy;
    private int fw;
    private int fh;
    private int sx;
    private int sy;
    private int sh;

    private PlayerPicker picker;
    private UiButton acceptBtn;
    private UiButton startBtn;
    private UiButton leaveBtn;

    RaidsPane(ChatSection owner) {
        this.owner = owner;
    }

    PlayerPicker picker() {
        return picker;
    }

    /**
     * @param x      left edge of the whole view
     * @param top    top of the view (below the chips)
     * @param w      full width
     * @param bottom bottom of the feed (above the chat input)
     * @param end    bottom of the section (the side column goes down to here)
     */
    void build(TextRenderer tr, int x, int top, int w, int bottom, int end) {
        this.tr = tr;
        fx = x;
        fy = top;
        fw = w - SIDE - 6;
        fh = bottom - top;
        sx = x + w - SIDE;
        sy = top;
        sh = end - top;
        int fieldY = end - 20;
        UiField field = owner.add(new UiField(tr, sx, fieldY, SIDE - 52, 20, "Invitar jugador…"));
        field.setText(inviteDraft);
        picker = new PlayerPicker(field, s -> inviteDraft = s, null);
        owner.add(new UiButton(sx + SIDE - 48, fieldY, 48, 20, "Invitar", UiButton.Style.ACCENT,
                () -> invite(picker.value())));
        int half = (SIDE - 4) / 2;
        startBtn = owner.add(new UiButton(sx, fieldY - 20, half, 16, "Iniciar", UiButton.Style.SUCCESS, this::start)
                .tooltip("Empezar la raid"));
        leaveBtn = owner.add(new UiButton(sx + half + 4, fieldY - 20, half, 16, "Salir", UiButton.Style.DANGER, this::leave)
                .tooltip("Salir de la raid"));
        acceptBtn = owner.add(new UiButton(sx + SIDE - 62, sy + 36, 56, 14, "Aceptar", UiButton.Style.SUCCESS, this::accept)
                .tooltip("Unirte a la raid"));
        refresh();
    }

    void tick() {
        picker.tick();
        refresh();
    }

    private void refresh() {
        String self = ChatUtilitiesCore.self();
        RaidParty.Lobby lobby = RaidParty.lobby(self);
        boolean raids = ServerCommands.usable("raids");
        acceptBtn.visible = RaidParty.invite() != null && lobby == null;
        acceptBtn.active = raids;
        leaveBtn.visible = lobby != null;
        leaveBtn.active = raids;
        startBtn.visible = lobby != null && RaidParty.isLeader(self);
        startBtn.active = raids;
    }

    private boolean check() {
        if (!ServerCommands.usable("raids")) {
            owner.status("El servidor no ofrece /raids.", Theme.DANGER);
            return false;
        }
        return true;
    }

    void invite(String name) {
        if (!ChatText.validName(name)) {
            owner.status("Escribe un nombre de jugador válido.", Theme.WARN);
            return;
        }
        if (name.equalsIgnoreCase(ChatUtilitiesCore.self())) {
            owner.status("No puedes invitarte a ti mismo.", Theme.WARN);
            return;
        }
        if (!check()) {
            return;
        }
        ServerCommands.send("raids invite " + name);
        RaidParty.dropRequest(name);
        invited.put(name.toLowerCase(Locale.ROOT), System.currentTimeMillis());
        postsRev = -1;
        if (picker != null && picker.value().equalsIgnoreCase(name)) {
            picker.field().setText("");
        }
        owner.status("Invitación de raid enviada a " + name + ".", Theme.SUCCESS);
    }

    private void tpa(String name) {
        if (!ServerCommands.usable("tpa")) {
            owner.status("El servidor no ofrece /tpa.", Theme.DANGER);
            return;
        }
        ServerCommands.send("tpa " + name);
        owner.status("TPA enviado a " + name + ".", Theme.SUCCESS);
    }

    private void accept() {
        RaidParty.Invite inv = RaidParty.invite();
        if (inv == null || !check()) {
            return;
        }
        ServerCommands.send(ServerCommands.raidAccept(inv.from()));
        RaidParty.accepted();
        owner.status("Uniéndote a la raid de " + inv.from() + "…", Theme.SUCCESS);
    }

    private void start() {
        if (check()) {
            ServerCommands.send("raids start");
            owner.status("Iniciando la raid…", Theme.SUCCESS);
        }
    }

    private void leave() {
        if (check()) {
            ServerCommands.send("raids leave");
            RaidParty.left();
            owner.status("Saliste de la raid.", Theme.MUTED);
        }
    }

    // ---- feed ----

    private List<Post> posts() {
        long rev = ChatUtilitiesCore.LOG.revision();
        long minute = System.currentTimeMillis() / 60_000L;
        if (rev == postsRev && minute == postsMinute) {
            return posts;
        }
        postsRev = rev;
        postsMinute = minute;
        long since = System.currentTimeMillis() - MAX_AGE_MS;
        List<ChatEntry> all = new ArrayList<>(ChatUtilitiesCore.LOG.snapshot(ChatLog.Channel.GENERAL,
                e -> e.type() == MessageType.PLAYER && e.time() >= since));
        all.addAll(ChatUtilitiesCore.LOG.snapshot(ChatLog.Channel.CLAN, e -> e.time() >= since));
        all.sort(Comparator.comparingLong(ChatEntry::time).reversed());
        String self = ChatUtilitiesCore.self();
        Set<String> seen = new HashSet<>();
        List<Post> out = new ArrayList<>();
        for (ChatEntry e : all) {
            if (e.sender().isEmpty() || e.sender().equalsIgnoreCase(self)) {
                continue;
            }
            RaidChat.Info info = RaidChat.raid(e.body());
            String key = e.sender().toLowerCase(Locale.ROOT);
            Long invitedAt = invited.get(key);
            if (invitedAt != null && e.time() <= invitedAt) {
                seen.add(key);
                continue;
            }
            if (info != null && seen.add(key)) {
                out.add(new Post(e, info));
            }
        }
        posts = out;
        return out;
    }

    private static int kindColor(RaidChat.Kind k) {
        return switch (k) {
            case OFFER -> Theme.SUCCESS;
            case SEEK -> Theme.INFO;
            case TRADE -> Theme.WARN;
            case BUY -> Theme.DANGER_SOFT;
        };
    }

    private int visibleRows() {
        return Math.max(1, (fh - 4) / ROW);
    }

    void render(DrawContext c, int mouseX, int mouseY) {
        renderFeed(c, mouseX, mouseY);
        renderSide(c, mouseX, mouseY);
    }

    /** Row positions of the last frame: {post index, top, height}, used for clicks. */
    private final List<int[]> rowRects = new ArrayList<>();
    private static final int BTN_W = 30;

    /** Wrapped message of a post; the whole text is shown, never cut. */
    private List<net.minecraft.text.OrderedText> bodyLines(Post p) {
        int textW = Math.max(40, fx + fw - 6 - BTN_W - 6 - (fx + 31));
        return tr.wrapLines(net.minecraft.text.Text.literal(p.entry().body()), textW);
    }

    /** TPA and MSG are stacked and centred vertically in the row. */
    private static int buttonsTop(int rowY, int rowH) {
        return rowY + (rowH - (BTN_H * 2 + 3)) / 2;
    }

    private void renderFeed(DrawContext c, int mouseX, int mouseY) {
        rowRects.clear();
        List<Post> list = posts();
        if (list.isEmpty()) {
            return;
        }
        scroll = Math.max(0, Math.min(scroll, list.size() - 1));
        long now = System.currentTimeMillis();
        Theme.scissor(c, fx, fy, fx + fw, fy + fh);
        int ry = fy;
        for (int i = scroll; i < list.size() && ry < fy + fh; i++) {
            Post p = list.get(i);
            List<net.minecraft.text.OrderedText> lines = bodyLines(p);
            // Tall enough for the text and for both buttons with a margin.
            int rh = Math.max(BTN_H * 2 + 3 + 10, 15 + lines.size() * 10 + 4);
            boolean hover = mouseX >= fx && mouseX < fx + fw && mouseY >= ry && mouseY < ry + rh;
            if (hover) {
                Theme.round(c, fx, ry, fw, rh - 1, Theme.R_SMALL, Theme.CARD_HOVER);
            }
            Theme.round(c, fx + 2, ry + 4, 2, rh - 9, 1, kindColor(p.info().kind()));
            String name = p.entry().sender();
            PlayerSkinDrawer.draw(c, PlayerHeads.get(name), fx + 8, ry + 5, 16);
            int tx = fx + 31;
            int btnLeft = fx + fw - 6 - BTN_W;
            c.drawText(tr, name, tx, ry + 4, ChatLog.isAlly(p.entry()) ? ChatSection.ALLY_COLOR
                    : p.entry().type() == MessageType.CLAN ? MessageType.CLAN.color : Theme.TEXT, false);
            int after = tx + tr.getWidth(name) + 6;
            String summary = p.info().summary();
            // What the player wants first ("Compra cupo", "Busca raid"...), then stars and Pokémon.
            String label = summary.isEmpty() ? p.info().kind().label : p.info().kind().label + " · " + summary;
            int labelColor = summary.isEmpty() ? Theme.MUTED : Theme.GOLD;
            String ago = ago(now - p.entry().time());
            int agoW = tr.getWidth(ago);
            c.drawText(tr, tr.trimToWidth(label, Math.max(0, btnLeft - after - agoW - 10)), after, ry + 4, labelColor, false);
            c.drawText(tr, ago, btnLeft - 6 - agoW, ry + 4, Theme.FAINT, false);
            int ly = ry + 15;
            for (var line : lines) {
                c.drawText(tr, line, tx, ly, Theme.MUTED, false);
                ly += 10;
            }
            int by = buttonsTop(ry, rh);
            drawMini(c, btnLeft, by, BTN_W, "TPA", Theme.SUCCESS, mouseX, mouseY, true);
            drawMini(c, btnLeft, by + BTN_H + 3, BTN_W, "MSG", Theme.INFO, mouseX, mouseY, true);
            rowRects.add(new int[]{i, ry, rh});
            ry += rh;
        }
        c.disableScissor();
        if (list.size() > 1) {
            int thumb = Math.max(10, fh / Math.max(1, list.size()));
            int pos = (int) ((long) (fh - thumb) * scroll / Math.max(1, list.size() - 1));
            Theme.round(c, fx + fw - 2, fy + pos, 2, thumb, 1, Theme.BORDER_STRONG);
        }
    }

    private static String ago(long ms) {
        long min = ms / 60_000L;
        return min < 1 ? "ahora" : "hace " + min + "m";
    }

    private void drawMini(DrawContext c, int bx, int by, int bw, String text, int accent, int mouseX, int mouseY,
                          boolean strong) {
        boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + BTN_H;
        int fill = Theme.withAlpha(accent, hover ? 0x60 : strong ? 0x38 : 0x18);
        int border = Theme.withAlpha(accent, hover ? 0xCC : strong ? 0x90 : 0x50);
        Theme.box(c, bx, by, bw, BTN_H, Theme.R_SMALL, fill, border);
        c.drawText(tr, text, bx + (bw - tr.getWidth(text)) / 2, by + 2, strong || hover ? Theme.TEXT : Theme.MUTED, false);
    }

    // ---- side column: your raid ----

    private void renderSide(DrawContext c, int mouseX, int mouseY) {
        int boxH = sh - 46;
        c.fill(sx - 3, sy, sx - 2, sy + sh, Theme.BORDER);
        c.drawText(tr, "Tu raid", sx + 7, sy + 6, Theme.ACCENT, false);
        String self = ChatUtilitiesCore.self();
        RaidParty.Lobby lobby = RaidParty.lobby(self);
        long now = System.currentTimeMillis();
        int cy = sy + 18;
        RaidParty.Invite inv = RaidParty.invite();
        if (inv != null && lobby == null) {
            Theme.box(c, sx + 4, cy, SIDE - 8, 36, Theme.R_SMALL, Theme.withAlpha(Theme.GOLD, 0x1C),
                    Theme.withAlpha(Theme.GOLD, 0x70));
            PlayerSkinDrawer.draw(c, PlayerHeads.get(inv.from()), sx + 8, cy + 4, 12);
            long left = Math.max(0, (RaidParty.INVITE_MS - (now - inv.time())) / 1000);
            String secs = left + "s";
            int secsX = sx + SIDE - 10 - tr.getWidth(secs);
            c.drawText(tr, secs, secsX, cy + 6, Theme.FAINT, false);
            c.drawText(tr, tr.trimToWidth(inv.from() + " te invita", secsX - 6 - (sx + 24)), sx + 24, cy + 6, Theme.TEXT, false);
            String what = inv.stars() + "★" + (inv.pokemon().isEmpty() ? "" : " " + inv.pokemon());
            c.drawText(tr, tr.trimToWidth(what, SIDE - 76), sx + 8, cy + 22, Theme.GOLD, false);
            cy += 40;
        }
        if (lobby == null) {
            if (inv == null) {
                c.drawText(tr, "No estás en una raid.", sx + 7, cy + 2, Theme.MUTED, false);
                cy += 14;
            }
        } else {
            String head = (lobby.stars() > 0 ? lobby.stars() + "★ " : "") + (lobby.pokemon().isEmpty() ? "Raid" : lobby.pokemon());
            c.drawText(tr, tr.trimToWidth(head, SIDE - 14), sx + 7, cy, Theme.GOLD, false);
            cy += 11;
            String count = lobby.maxPlayers() > 0 ? lobby.members().size() + "/" + lobby.maxPlayers() : "";
            String state = lobby.state() + (count.isEmpty() ? "" : " · " + count);
            c.drawText(tr, tr.trimToWidth(state, SIDE - 14), sx + 7, cy, Theme.MUTED, false);
            cy += 13;
            for (RaidParty.Member m : lobby.members()) {
                if (cy + 11 > sy + boxH - 2) {
                    break;
                }
                PlayerSkinDrawer.draw(c, PlayerHeads.get(m.name()), sx + 8, cy, 8);
                boolean me = m.name().equalsIgnoreCase(self);
                c.drawText(tr, tr.trimToWidth(m.name(), SIDE - 44), sx + 20, cy, me ? Theme.ACCENT : Theme.TEXT, false);
                String mark = m.leader() ? "Líder" : m.ready() ? "✔" : "";
                if (!mark.isEmpty()) {
                    c.drawText(tr, mark, sx + SIDE - 7 - tr.getWidth(mark), cy, m.leader() ? Theme.GOLD : Theme.SUCCESS, false);
                }
                cy += 11;
            }
        }
        List<String> requests = RaidParty.requests();
        if (!requests.isEmpty() && cy + 22 < sy + boxH) {
            cy += 3;
            c.drawText(tr, "Quieren unirse", sx + 7, cy, Theme.INFO, false);
            cy += 11;
            for (String r : requests) {
                if (cy + 13 > sy + boxH - 2) {
                    break;
                }
                PlayerSkinDrawer.draw(c, PlayerHeads.get(r), sx + 8, cy + 2, 8);
                c.drawText(tr, tr.trimToWidth(r, SIDE - 64), sx + 20, cy + 2, Theme.TEXT, false);
                drawMini(c, sx + SIDE - 6 - 40, cy, 40, "Invitar", Theme.INFO, mouseX, mouseY, true);
                cy += 13;
            }
        }
        String reply = RaidParty.lastReply(now);
        if (!reply.isEmpty() && cy + 4 < sy + boxH - 11) {
            c.drawText(tr, tr.trimToWidth(reply, SIDE - 12), sx + 6, sy + boxH - 11, Theme.FAINT, false);
        }
    }

    // ---- input ----

    boolean mouseClicked(double mx, double my) {
        List<Post> list = posts();
        if (mx >= fx && mx < fx + fw && my >= fy && my < fy + fh) {
            for (int[] r : rowRects) {
                if (my < r[1] || my >= r[1] + r[2] || r[0] >= list.size()) {
                    continue;
                }
                String name = list.get(r[0]).entry().sender();
                int btnLeft = fx + fw - 6 - BTN_W;
                if (mx >= btnLeft && mx < btnLeft + BTN_W) {
                    int by = buttonsTop(r[1], r[2]);
                    if (my >= by && my < by + BTN_H) {
                        tpa(name);
                        return true;
                    }
                    if (my >= by + BTN_H + 3 && my < by + 2 * BTN_H + 3) {
                        // Straight to the private conversation with this player.
                        owner.openPrivate(name);
                        owner.screen.relayout();
                        return true;
                    }
                }
                if (mx < fx + 31 + tr.getWidth(name)) {
                    picker.field().setText(name);
                    return true;
                }
                return false;
            }
            return false;
        }
        return requestClicked(mx, my);
    }

    /** Re-walks the side layout to find a click on a join request's "Invitar". */
    private boolean requestClicked(double mx, double my) {
        if (mx < sx + SIDE - 46 || mx >= sx + SIDE - 6) {
            return false;
        }
        String self = ChatUtilitiesCore.self();
        RaidParty.Lobby lobby = RaidParty.lobby(self);
        int boxH = sh - 46;
        int cy = sy + 18;
        if (RaidParty.invite() != null && lobby == null) {
            cy += 40;
        }
        if (lobby == null) {
            if (RaidParty.invite() == null) {
                cy += 14;
            }
        } else {
            cy += 24;
            for (RaidParty.Member ignored : lobby.members()) {
                if (cy + 11 > sy + boxH - 2) {
                    break;
                }
                cy += 11;
            }
        }
        List<String> requests = RaidParty.requests();
        if (requests.isEmpty() || cy + 22 >= sy + boxH) {
            return false;
        }
        cy += 14;
        for (String r : requests) {
            if (cy + 13 > sy + boxH - 2) {
                break;
            }
            if (my >= cy && my < cy + BTN_H) {
                invite(r);
                return true;
            }
            cy += 13;
        }
        return false;
    }

    boolean mouseScrolled(double mx, double my, double amount) {
        if (mx >= fx && mx < fx + fw && my >= fy && my < fy + fh) {
            scroll = Math.max(0, scroll - (int) Math.signum(amount));
            return true;
        }
        return false;
    }
}
