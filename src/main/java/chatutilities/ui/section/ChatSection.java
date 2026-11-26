package chatutilities.ui.section;

import chatutilities.core.ChatUtilitiesCore;
import chatutilities.core.ChatText;
import chatutilities.core.chat.ChatEntry;
import chatutilities.core.chat.ChatLog;
import chatutilities.core.chat.MessageType;
import chatutilities.core.commands.ServerCommands;
import chatutilities.core.players.PlayerHeads;
import chatutilities.core.raids.RaidChat;
import chatutilities.core.raids.RaidParty;
import chatutilities.ui.Theme;
import chatutilities.ui.widget.BubbleListView;
import chatutilities.ui.widget.MessageListView;
import chatutilities.ui.widget.PlayerPicker;
import chatutilities.ui.widget.UiButton;
import chatutilities.ui.widget.UiField;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Chat page with one folder tab per channel: General (players and legendary broadcasts only),
 * Clan and Alianza ({@code /clan chat}), Privados ({@code /msg}, as bubbles), Raids and Comercio.
 * A word search in the header filters the open tab.
 */
public class ChatSection extends Section {
    /** Colour used for allied clans (tags and names), to tell them apart from your own clan. */
    public static final int ALLY_COLOR = 0xFF7DD3FC;
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());
    private static final int PEERS = 122;
    private static final int ROW = 14;
    private static final int INPUT_H = 20;
    private static final String NO_RESULTS = "Sin resultados de búsqueda";

    private enum View {
        GENERAL("General"), CLAN("Clan"), ALLY("Alianza"), PRIVATE("Privados"), RAIDS("Raids"), TRADE("Comercio");

        final String label;

        View(String label) {
            this.label = label;
        }
    }

    /** Filtered list of one tab, recomputed only when the log, the search or the conversation changes. */
    private static final class Filtered {
        long key = Long.MIN_VALUE;
        List<ChatEntry> entries = List.of();
    }

    private final MessageListView generalList = new MessageListView(ChatSection::formatPlayer, "");
    private final MessageListView clanList = new MessageListView(ChatSection::formatClan, "");
    private final MessageListView allyList = new MessageListView(ChatSection::formatClan, "");
    private final MessageListView tradeList = new MessageListView(ChatSection::formatPlayer, "");
    private final BubbleListView bubbles = new BubbleListView();
    private final RaidsPane raids = new RaidsPane(this);
    private final Map<View, Filtered> filtered = new EnumMap<>(View.class);
    private final Map<View, String> drafts = new EnumMap<>(View.class);
    /** Words typed in the search box; shared by every tab. */
    private String query = "";
    private View view = View.GENERAL;
    private UiField input;
    private PlayerPicker picker;
    private String target = "";
    private String pickerDraft = "";
    private boolean focusPending;
    /** First conversation shown in the Privados list (mouse wheel over the list scrolls it). */
    private int peerScroll;

    /**
     * Messages you sent from the panel, newest last, for the ↑/↓ keys (like Minecraft's chat).
     * Starts with Minecraft's own chat history; kept separate afterwards so a private or clan
     * message never ends up in the public chat history.
     */
    private static final List<String> SENT = new java.util.ArrayList<>();
    private static boolean sentSeeded;
    private int historyIndex = -1;
    private String historyDraft = "";

    private void remember(String text) {
        if (SENT.isEmpty() || !SENT.get(SENT.size() - 1).equals(text)) {
            SENT.add(text);
            while (SENT.size() > 100) {
                SENT.remove(0);
            }
        }
        historyIndex = -1;
    }

    /** ↑ (older) or ↓ (newer) through the sent messages; past the newest it restores what you were typing. */
    private boolean browseHistory(boolean older) {
        if (!sentSeeded) {
            sentSeeded = true;
            List<String> vanilla = new java.util.ArrayList<>(mc.inGameHud.getChatHud().getMessageHistory());
            SENT.addAll(0, vanilla.subList(Math.max(0, vanilla.size() - 50), vanilla.size()));
        }
        if (SENT.isEmpty()) {
            return false;
        }
        if (historyIndex == -1) {
            historyDraft = input.getText();
        }
        historyIndex = older ? Math.min(historyIndex + 1, SENT.size() - 1) : historyIndex - 1;
        if (historyIndex < 0) {
            historyIndex = -1;
            input.setText(historyDraft);
        } else {
            input.setText(SENT.get(SENT.size() - 1 - historyIndex));
        }
        input.setCursorToEnd(false);
        return true;
    }

    // ---- formatting ----

    /** Time of day for today's messages, day and time for older (saved) ones. */
    static Text time(ChatEntry e) {
        Instant at = Instant.ofEpochMilli(e.time());
        boolean today = at.atZone(ZoneId.systemDefault()).toLocalDate().equals(java.time.LocalDate.now());
        return Text.literal((today ? TIME : DAY).format(at) + " ").withColor(Theme.FAINT);
    }

    static Text formatPlayer(ChatEntry e) {
        return Text.empty().append(time(e)).append(e.original());
    }

    static Text formatClan(ChatEntry e) {
        MutableText t = Text.empty().append(time(e));
        if (!e.tag().isEmpty()) {
            t.append(Text.literal("[" + e.tag() + "] ").withColor(ALLY_COLOR));
        }
        int rankColor = rankColor(e.rank());
        if (!e.rank().isEmpty()) {
            t.append(Text.literal(e.rank() + " ").withColor(rankColor != 0 ? rankColor : Theme.FAINT));
        }
        int nameColor = rankColor != 0 ? rankColor
                : e.fromMe() ? Theme.ACCENT : ChatLog.isAlly(e) ? ALLY_COLOR : MessageType.CLAN.color;
        t.append(Text.literal(e.sender()).withColor(nameColor));
        return t.append(Text.literal(": " + e.body()).withColor(Theme.TEXT));
    }

    /** Leaders in gold, co-leaders in pink; 0 for every other rank. */
    static final int LEADER_COLOR = 0xFFFFC94C;
    static final int COLEADER_COLOR = 0xFFFF8FB0;

    static int rankColor(String rank) {
        String r = fold(rank).replaceAll("[^a-z]", "");
        if (r.equals("lider") || r.equals("leader")) {
            return LEADER_COLOR;
        }
        if (r.equals("colider") || r.equals("coleader") || r.equals("sublider") || r.equals("vicelider")) {
            return COLEADER_COLOR;
        }
        return 0;
    }

    /** Lower case without accents, so "cancion" finds "canción". */
    static String fold(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** True when every word of the search appears in the line (text, author, other player or tag). */
    static boolean matches(ChatEntry e, String[] words) {
        if (words.length == 0) {
            return true;
        }
        String hay = fold(e.body() + " " + e.sender() + " " + e.peer() + " " + e.tag());
        for (String word : words) {
            if (!hay.contains(word)) {
                return false;
            }
        }
        return true;
    }

    private String[] words() {
        String q = fold(query).strip();
        return q.isEmpty() ? new String[0] : q.split("\\s+");
    }

    // ---- section metadata ----

    @Override
    public String id() {
        return "chat";
    }

    @Override
    public String title() {
        return "Chat";
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.WRITABLE_BOOK);
    }

    @Override
    public String header() {
        return view == View.PRIVATE && !target.isEmpty() ? "Privados · " + target : view.label;
    }

    @Override
    public List<String> tabs() {
        return Arrays.stream(View.values()).map(v -> v.label).toList();
    }

    @Override
    public int selectedTab() {
        return view.ordinal();
    }

    @Override
    public void selectTab(int index) {
        show(View.values()[index]);
    }

    @Override
    public int tabBadge(int index) {
        return unread(View.values()[index]);
    }

    @Override
    public int badge() {
        return ChatUtilitiesCore.LOG.unreadPrivate() + ChatUtilitiesCore.LOG.unreadClan() + ChatUtilitiesCore.LOG.unreadAlly()
                + unread(View.RAIDS);
    }

    private int unread(View v) {
        return switch (v) {
            case CLAN -> ChatUtilitiesCore.LOG.unreadClan();
            case ALLY -> ChatUtilitiesCore.LOG.unreadAlly();
            case PRIVATE -> ChatUtilitiesCore.LOG.unreadPrivate();
            case RAIDS -> RaidParty.invite() != null ? 1 : 0;
            default -> 0;
        };
    }

    // ---- layout ----

    @Override
    protected void build() {
        if (view != View.RAIDS) {
            // Word search in the header, on the right; the text is kept between tabs.
            int sw = 140;
            int sx = headerRight - sw;
            UiField search = add(new UiField(tr, sx, headerY, sw, 16, "Buscar palabras…"));
            search.setText(query);
            search.setChangedListener(s -> {
                query = s;
                list().toBottom();
                bubbles.toBottom();
            });
            if (!query.isEmpty()) {
                add(new UiButton(sx - 18, headerY, 16, 16, "✕", UiButton.Style.NORMAL, () -> {
                    query = "";
                    screen.relayout();
                }).tooltip("Borrar búsqueda"));
            }
        }
        int ix = x;
        int iw = w;
        picker = null;
        if (view == View.PRIVATE) {
            UiField name = add(new UiField(tr, x, y, PEERS, 16, "Nuevo chat…"));
            name.setText(pickerDraft);
            picker = new PlayerPicker(name, s -> pickerDraft = s, this::choose);
            ix = x + PEERS + 8;
            iw = w - PEERS - 8;
            bubbles.setBounds(ix, y, iw, h - INPUT_H - 6);
        } else if (view == View.RAIDS) {
            raids.build(tr, x, y, w, y + h - INPUT_H - 6, y + h);
            iw = w - RaidsPane.SIDE - 6;
        } else {
            list().setBounds(x, y, w, h - INPUT_H - 6);
        }
        String hint = switch (view) {
            case GENERAL, RAIDS, TRADE -> "Escribe en el chat o un /comando…";
            case CLAN -> "Mensaje para el clan…";
            case ALLY -> "Mensaje para la alianza…";
            case PRIVATE -> target.isEmpty() ? "Elige una conversación…" : "Mensaje para " + target + "…";
        };
        input = add(new UiField(tr, ix, y + h - INPUT_H, iw - 62, INPUT_H, hint));
        input.setText(drafts.getOrDefault(view, ""));
        View v = view;
        input.setChangedListener(s -> drafts.put(v, s));
        add(new UiButton(ix + iw - 58, y + h - INPUT_H, 58, INPUT_H, "Enviar", UiButton.Style.ACCENT, this::send));
        focusPending = true;
    }

    /** Puts the cursor in the message box so typing goes straight to the chat. */
    private void focusInput() {
        if (input != null) {
            screen.setFocused(input);
        }
    }

    private MessageListView list() {
        return switch (view) {
            case CLAN -> clanList;
            case ALLY -> allyList;
            case TRADE -> tradeList;
            default -> generalList;
        };
    }

    private void show(View v) {
        if (v == view) {
            return;
        }
        menuName = null;
        view = v;
        list().toBottom();
        bubbles.toBottom();
        screen.relayout();
    }

    /** Conversation rows that fit in the Privados list. */
    private int peerRows() {
        return Math.max(1, (h - 20) / ROW);
    }

    /** Opens the Raids tab (key pressed while a raid invitation is open, to accept it). */
    public void openRaids() {
        menuName = null;
        view = View.RAIDS;
    }

    /** Opens a conversation (from Raids, TP or the conversation list). */
    public void openPrivate(String name) {
        if (!ChatText.validName(name)) {
            return;
        }
        menuName = null;
        view = View.PRIVATE;
        target = name;
        pickerDraft = "";
        ChatUtilitiesCore.LOG.touchPeer(name);
        ChatUtilitiesCore.LOG.readPrivate(name);
        bubbles.toBottom();
        bubbles.invalidate();
    }

    private void choose(String name) {
        if (!ChatText.validName(name)) {
            status("Nombre de jugador no válido.", Theme.WARN);
            return;
        }
        openPrivate(name);
        screen.relayout();
    }

    private void tpa(String name) {
        if (!ServerCommands.usable("tpa")) {
            status("El servidor no ofrece /tpa.", Theme.DANGER);
            return;
        }
        ServerCommands.send("tpa " + name);
        status("Solicitud de TP enviada a " + name + ".", Theme.SUCCESS);
    }

    private void closePeer(String name) {
        ChatUtilitiesCore.LOG.closePeer(name);
        if (name.equalsIgnoreCase(target)) {
            target = "";
            screen.relayout();
        }
    }

    private void send() {
        String text = input.getText().strip();
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (handler == null) {
            status("Sin conexión con el servidor.", Theme.DANGER);
            return;
        }
        if (text.isEmpty()) {
            return;
        }
        switch (view) {
            case GENERAL, RAIDS, TRADE -> {
                if (text.startsWith("/")) {
                    handler.sendChatCommand(text.substring(1));
                } else {
                    handler.sendChatMessage(text);
                }
                mc.inGameHud.getChatHud().addToMessageHistory(text);
            }
            case CLAN, ALLY -> {
                if (!ServerCommands.usable("clan")) {
                    status("El servidor no te ofrece /clan (¿sin clan?).", Theme.DANGER);
                    return;
                }
                ServerCommands.send("clan chat " + text);
            }
            case PRIVATE -> {
                if (target.isEmpty()) {
                    status("Primero elige una conversación.", Theme.WARN);
                    return;
                }
                ServerCommands.send(ServerCommands.privateCommand() + " " + target + " " + text);
                bubbles.toBottom();
            }
        }
        remember(text);
        input.setText("");
        list().toBottom();
    }

    @Override
    public void tick() {
        if (focusPending) {
            focusPending = false;
            focusInput();
        }
        switch (view) {
            case RAIDS -> raids.tick();
            case ALLY -> ChatUtilitiesCore.LOG.readAlly();
            case CLAN -> ChatUtilitiesCore.LOG.readClan();
            case PRIVATE -> {
                picker.tick();
                if (!target.isEmpty()) {
                    ChatUtilitiesCore.LOG.readPrivate(target);
                }
            }
            default -> {
            }
        }
    }

    // ---- rendering ----

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        long rev = ChatUtilitiesCore.LOG.revision();
        switch (view) {
            case GENERAL -> showFiltered(c, generalList, rev, ChatLog.Channel.GENERAL,
                    e -> e.type() == MessageType.PLAYER || e.type() == MessageType.LEGENDARY, mouseX, mouseY);
            case CLAN -> showFiltered(c, clanList, rev, ChatLog.Channel.CLAN, e -> !ChatLog.isAlly(e), mouseX, mouseY);
            case ALLY -> showFiltered(c, allyList, rev, ChatLog.Channel.CLAN, e -> ChatLog.isAlly(e) || e.fromMe(), mouseX, mouseY);
            case TRADE -> showFiltered(c, tradeList, rev, ChatLog.Channel.GENERAL,
                    e -> e.type() == MessageType.PLAYER && RaidChat.isTrade(e.body()), mouseX, mouseY);
            case RAIDS -> raids.render(c, mouseX, mouseY);
            case PRIVATE -> renderPrivate(c, mouseX, mouseY, rev);
        }
    }

    private List<ChatEntry> filter(long rev, ChatLog.Channel channel, Predicate<ChatEntry> rule) {
        String[] words = words();
        long key = (rev * 31 + String.join(" ", words).hashCode()) * 31 + target.hashCode();
        Filtered f = filtered.computeIfAbsent(view, v -> new Filtered());
        if (f.key != key) {
            f.key = key;
            f.entries = ChatUtilitiesCore.LOG.snapshot(channel, e -> rule.test(e) && matches(e, words));
        }
        return f.entries;
    }

    private void showFiltered(DrawContext c, MessageListView list, long rev, ChatLog.Channel channel,
                              Predicate<ChatEntry> rule, int mouseX, int mouseY) {
        List<ChatEntry> entries = filter(rev, channel, rule);
        list.setEmptyText(words().length > 0 ? NO_RESULTS : "");
        boolean free = menuName == null;
        list.render(c, tr, filtered.get(view).key, entries, free ? mouseX : Integer.MIN_VALUE, free ? mouseY : Integer.MIN_VALUE);
    }

    private void renderPrivate(DrawContext c, int mouseX, int mouseY, long rev) {
        // Conversation list on the left, a thin gold line, bubbles on the right.
        c.fill(x + PEERS + 3, y, x + PEERS + 4, y + h, Theme.BORDER);
        List<String> peers = ChatUtilitiesCore.LOG.peers();
        int ry = y + 20;
        int visibleRows = peerRows();
        peerScroll = Math.max(0, Math.min(peerScroll, peers.size() - visibleRows));
        if (peers.size() > visibleRows) {
            // Scroll bar on the right edge of the list.
            int trackY = y + 20;
            int trackH = visibleRows * ROW;
            int thumb = Math.max(10, trackH * visibleRows / peers.size());
            int pos = (int) ((long) (trackH - thumb) * peerScroll / Math.max(1, peers.size() - visibleRows));
            Theme.round(c, x + PEERS + 1, trackY, 2, trackH, 1, Theme.withAlpha(Theme.BORDER, 0x80));
            Theme.round(c, x + PEERS + 1, trackY + pos, 2, thumb, 1, Theme.BORDER_STRONG);
        }
        for (int pi = peerScroll; pi < peers.size(); pi++) {
            String p = peers.get(pi);
            if (ry + ROW > y + h) {
                break;
            }
            boolean sel = p.equalsIgnoreCase(target);
            boolean hover = mouseX >= x && mouseX < x + PEERS && mouseY >= ry && mouseY < ry + ROW;
            if (sel || hover) {
                Theme.round(c, x, ry, PEERS, ROW, Theme.R_SMALL, sel ? Theme.SELECT : Theme.CARD_HOVER);
            }
            if (sel) {
                c.fill(x, ry + 2, x + 1, ry + ROW - 2, Theme.ACCENT);
            }
            int unread = ChatUtilitiesCore.LOG.unreadPrivate(p);
            PlayerSkinDrawer.draw(c, PlayerHeads.get(p), x + 4, ry + 3, 8);
            c.drawText(tr, tr.trimToWidth(p, PEERS - 52), x + 16, ry + 3,
                    sel ? Theme.ACCENT : unread > 0 ? Theme.TEXT : Theme.DIM, false);
            if (unread > 0) {
                Theme.badge(c, tr, x + PEERS - 34, ry + 2, unread, MessageType.PRIVATE_IN.color);
            }
            boolean onTp = hover && mouseX >= x + PEERS - 30 && mouseX < x + PEERS - 14;
            boolean onX = hover && mouseX >= x + PEERS - 13;
            Theme.box(c, x + PEERS - 30, ry + 2, 16, 10, 2, Theme.withAlpha(Theme.SUCCESS, onTp ? 0x60 : 0x22),
                    Theme.withAlpha(Theme.SUCCESS, onTp ? 0xCC : 0x70));
            c.drawText(tr, "TP", x + PEERS - 27, ry + 3, onTp ? Theme.BRIGHT : Theme.MUTED, false);
            c.drawText(tr, "✕", x + PEERS - 10, ry + 3, onX ? Theme.DANGER : Theme.FAINT, false);
            ry += ROW;
        }
        if (target.isEmpty()) {
            bubbles.setEmptyText("");
            bubbles.render(c, tr, rev * 31, List.of());
            return;
        }
        String key = target;
        List<ChatEntry> entries = filter(rev, ChatLog.Channel.PRIVATE, e -> e.peer().equalsIgnoreCase(key));
        bubbles.setEmptyText(words().length > 0 ? NO_RESULTS : "");
        bubbles.render(c, tr, filtered.get(view).key, entries);
    }

    // ---- player name menu (right click on a name) ----

    private static final int MENU_W = 136;
    private static final int MENU_H = 66;
    private static final int MENU_BTN_H = 16;
    private static final String[] MENU_LABELS = {"TP", "Pagar", "Privado", "Invitar raid"};
    /** Player whose menu is open, or null. */
    private String menuName;
    private int menuX;
    private int menuY;

    private void openMenu(String name, double mx, double my) {
        menuName = name;
        menuX = (int) Math.max(x, Math.min(mx + 2, x + w - MENU_W));
        menuY = (int) Math.max(y, Math.min(my + 2, y + h - MENU_H));
    }

    private int menuButtonAt(double mx, double my) {
        int bw = (MENU_W - 16) / 2;
        for (int i = 0; i < MENU_LABELS.length; i++) {
            int bx = menuX + 6 + (i % 2) * (bw + 4);
            int by = menuY + 24 + (i / 2) * (MENU_BTN_H + 4);
            if (mx >= bx && mx < bx + bw && my >= by && my < by + MENU_BTN_H) {
                return i;
            }
        }
        return -1;
    }

    private static int menuColor(int i) {
        return switch (i) {
            case 0 -> Theme.SUCCESS;
            case 1 -> Theme.GOLD;
            case 2 -> MessageType.PRIVATE_IN.color;
            default -> Theme.WARN;
        };
    }

    private void renderMenu(DrawContext c, int mouseX, int mouseY) {
        c.getMatrices().push();
        c.getMatrices().translate(0, 0, 400);
        Theme.round(c, menuX + 1, menuY + 2, MENU_W, MENU_H, Theme.R_PANEL, Theme.withAlpha(0, 0x50));
        Theme.box(c, menuX, menuY, MENU_W, MENU_H, Theme.R_PANEL, Theme.withAlpha(Theme.BASE, 0xF4),
                Theme.withAlpha(Theme.ACCENT, 0xA0));
        PlayerSkinDrawer.draw(c, PlayerHeads.get(menuName), menuX + 7, menuY + 6, 12);
        c.drawText(tr, tr.trimToWidth(menuName, MENU_W - 40), menuX + 24, menuY + 8, Theme.TEXT, false);
        boolean onClose = mouseX >= menuX + MENU_W - 16 && mouseX < menuX + MENU_W && mouseY >= menuY && mouseY < menuY + 20;
        c.drawText(tr, "✕", menuX + MENU_W - 12, menuY + 7, onClose ? Theme.DANGER : Theme.FAINT, false);
        c.fill(menuX + 6, menuY + 21, menuX + MENU_W - 6, menuY + 22, Theme.BORDER);
        int hover = menuButtonAt(mouseX, mouseY);
        int bw = (MENU_W - 16) / 2;
        for (int i = 0; i < MENU_LABELS.length; i++) {
            int bx = menuX + 6 + (i % 2) * (bw + 4);
            int by = menuY + 24 + (i / 2) * (MENU_BTN_H + 4);
            int accent = menuColor(i);
            boolean on = hover == i;
            Theme.box(c, bx, by, bw, MENU_BTN_H, Theme.R_SMALL, Theme.withAlpha(accent, on ? 0x60 : 0x1C),
                    Theme.withAlpha(accent, on ? 0xCC : 0x60));
            String label = MENU_LABELS[i];
            c.drawText(tr, label, bx + (bw - tr.getWidth(label)) / 2, by + 4, on ? Theme.BRIGHT : Theme.MUTED, false);
        }
        c.getMatrices().pop();
    }

    /** Runs the menu option: TP request, Pagos with the name filled in, private chat or raid invite. */
    private void menuAction(int i, String name) {
        menuName = null;
        switch (i) {
            case 0 -> tpa(name);
            case 1 -> screen.openPay(name);
            case 2 -> {
                openPrivate(name);
                screen.relayout();
            }
            default -> raids.invite(name);
        }
    }

    @Override
    public void renderOverlay(DrawContext c, int mouseX, int mouseY, float delta) {
        if (menuName != null) {
            renderMenu(c, mouseX, mouseY);
            return;
        }
        if (view == View.RAIDS) {
            raids.picker().renderOverlay(c, tr, mouseX, mouseY);
        } else if (picker != null) {
            picker.renderOverlay(c, tr, mouseX, mouseY);
        }
    }

    // ---- input ----

    @Override
    public boolean overlayClicked(double mx, double my, int button) {
        if (menuName != null) {
            String name = menuName;
            boolean inside = mx >= menuX && mx < menuX + MENU_W && my >= menuY && my < menuY + MENU_H;
            if (!inside) {
                // A click outside closes the menu and still goes through (right click on another name).
                menuName = null;
                return false;
            }
            int i = button == 0 ? menuButtonAt(mx, my) : -1;
            if (i >= 0) {
                menuAction(i, name);
            } else if (button == 0 && mx >= menuX + MENU_W - 16 && my < menuY + 20) {
                menuName = null;
            }
            return true;
        }
        if (view == View.RAIDS) {
            return raids.picker().mouseClicked(mx, my);
        }
        return picker != null && picker.mouseClicked(mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (view == View.RAIDS) {
            return raids.mouseClicked(mx, my);
        }
        if (view == View.PRIVATE ? bubbles.clickJump(mx, my) : list().clickJump(mx, my)) {
            return true;
        }
        if (button == 1 && view != View.PRIVATE) {
            ChatEntry e = list().nameAt(mx, my);
            if (e != null && ChatText.validName(e.sender()) && !e.sender().equalsIgnoreCase(ChatUtilitiesCore.self())) {
                openMenu(e.sender(), mx, my);
                return true;
            }
            return false;
        }
        int listTop = y + 20;
        if (view != View.PRIVATE || mx < x || mx >= x + PEERS || my < listTop) {
            return false;
        }
        int row = (int) ((my - listTop) / ROW);
        if (row >= peerRows()) {
            return false;
        }
        int index = row + peerScroll;
        List<String> peers = ChatUtilitiesCore.LOG.peers();
        if (index < 0 || index >= peers.size()) {
            return false;
        }
        String name = peers.get(index);
        if (mx >= x + PEERS - 13) {
            closePeer(name);
        } else if (mx >= x + PEERS - 30 && mx < x + PEERS - 14) {
            tpa(name);
        } else {
            choose(name);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (view == View.RAIDS) {
            return raids.mouseScrolled(mx, my, amount);
        }
        if (view == View.PRIVATE) {
            if (mx >= x && mx < x + PEERS + 4 && my >= y + 20 && my < y + h) {
                int max = Math.max(0, ChatUtilitiesCore.LOG.peers().size() - peerRows());
                peerScroll = Math.max(0, Math.min(max, peerScroll - (int) Math.signum(amount) * 2));
                return true;
            }
            return bubbles.scroll(mx, my, amount);
        }
        return list().scroll(mx, my, amount);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (menuName != null && key == GLFW.GLFW_KEY_ESCAPE) {
            menuName = null;
            return true;
        }
        if (picker != null && picker.keyPressed(key)) {
            return true;
        }
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (view == View.RAIDS) {
            if (raids.picker().keyPressed(key)) {
                return true;
            }
            if (enter && raids.picker().field().isFocused()) {
                raids.invite(raids.picker().value());
                return true;
            }
        }
        if (enter && input.isFocused()) {
            send();
            return true;
        }
        if (input.isFocused() && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            return browseHistory(key == GLFW.GLFW_KEY_UP);
        }
        if (enter && picker != null && picker.field().isFocused()) {
            choose(picker.value());
            return true;
        }
        return false;
    }
}
