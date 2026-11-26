package chatutilities.hud;

import chatutilities.core.ChatUtilitiesCore;
import chatutilities.core.raids.RaidParty;
import chatutilities.core.chat.ChatEntry;
import chatutilities.core.chat.ChatLog;
import chatutilities.core.chat.MessageType;
import chatutilities.core.config.ModConfig;
import chatutilities.core.players.PlayerDirectory;
import chatutilities.core.tp.TeleportTracker;
import chatutilities.ui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Floating HUD: TP requests, private messages, quick-action confirmations and clan viewer. */
public final class HudOverlay {
    /** Toast width from the settings ("Tamaño avisos"). */
    public static int toastW() {
        return ModConfig.get().toastWidth;
    }
    public static final int TOAST_H = 32;
    public static final int CLAN_W = 240;
    /** Allied clans' colour in the clan viewer (same as the panel). */
    public static final int ALLY_COLOR = 0xFF7DD3FC;

    private static KeyBinding panelKey;

    private HudOverlay() {
    }

    public static void setPanelKey(KeyBinding key) {
        panelKey = key;
    }

    /** Scale of the private/TP/raid notices. */
    public static float toastScale() {
        return ModConfig.get().toastScale;
    }

    public static float clanScale() {
        return ModConfig.get().clanScale;
    }

    /** On-screen size of a one-line notice, after the scale. */
    public static int toastBoxW() {
        return Math.round(toastW() * toastScale());
    }

    public static int toastBoxH() {
        return Math.round(TOAST_H * toastScale());
    }

    public static int clanBoxW() {
        return Math.round(clanWidth() * clanScale());
    }

    /** TP and raid notices have their own scale, separate from private messages. */
    public static float tpScale() {
        return ModConfig.get().tpScale;
    }

    public static int tpBoxW() {
        return Math.round(toastW() * tpScale());
    }

    public static int tpBoxH() {
        return Math.round(TOAST_H * tpScale());
    }

    public static int[] tpRect(int sw, int sh) {
        ModConfig cfg = ModConfig.get();
        return rect(cfg.tpX, cfg.tpY, tpBoxW(), tpBoxH(), sw, sh);
    }

    public static int[] msgRect(int sw, int sh) {
        ModConfig cfg = ModConfig.get();
        return rect(cfg.msgX, cfg.msgY, toastBoxW(), toastBoxH(), sw, sh);
    }

    // ---- fixed "pending" pill: unread private messages, stays until they are read ----

    public static final int PEND_H = 16;

    public static String pendingText(int count) {
        String key = panelKey == null ? "G" : panelKey.getBoundKeyLocalizedText().getString();
        return count + (count == 1 ? " privado pendiente" : " privados pendientes") + " \u00b7 " + key;
    }

    public static int pendingW(TextRenderer tr, int count) {
        return tr.getWidth(pendingText(count)) + 24;
    }

    /** Player who sent the newest unread private message, or null. */
    public static String pendingSender() {
        ChatEntry last = ChatUtilitiesCore.lastMessageNotice;
        if (last != null && ChatUtilitiesCore.LOG.unreadPrivate(last.peer()) > 0) {
            return last.peer();
        }
        for (String p : ChatUtilitiesCore.LOG.peers()) {
            if (ChatUtilitiesCore.LOG.unreadPrivate(p) > 0) {
                return p;
            }
        }
        return null;
    }

    public static int[] pendingRect(int sw, int sh, int w) {
        ModConfig cfg = ModConfig.get();
        return rect(cfg.pendX, cfg.pendY, w, PEND_H, sw, sh);
    }

    /**
     * Box with slightly rounded corners, the head of the newest sender and "3 privados pendientes \u00b7 G"; a thin
     * pulsing bar on the left draws the eye.
     */
    public static void drawPending(DrawContext c, TextRenderer tr, int x, int y, int count, String sender, float alpha) {
        int a = Math.round(alpha * 255);
        int w = pendingW(tr, count);
        int color = MessageType.PRIVATE_IN.color;
        Theme.round(c, x + 1, y + 2, w, PEND_H, Theme.R_SMALL, Theme.withAlpha(0, (int) (a * 0.3f)));
        Theme.box(c, x, y, w, PEND_H, Theme.R_SMALL, Theme.withAlpha(Theme.BASE, (int) (a * 0.9f)),
                Theme.withAlpha(color, (int) (a * 0.75f)));
        double pulse = 0.45 + 0.55 * Math.abs(Math.sin(System.currentTimeMillis() / 400.0));
        c.fill(x + 2, y + 3, x + 4, y + PEND_H - 3, Theme.withAlpha(color, (int) (a * pulse)));
        if (alpha > 0.4f && sender != null) {
            PlayerSkinDrawer.draw(c, chatutilities.core.players.PlayerHeads.get(sender), x + 6, y + 4, 8);
        }
        if (a >= 30) {
            c.drawText(tr, pendingText(count), x + 18, y + 4, Theme.withAlpha(Theme.TEXT, a), false);
        }
    }

    /** Height of the clan anchor slot (the newest message sits here), after the scale. */
    public static int clanHeight() {
        return Math.round(TOAST_H * clanScale());
    }

    /** Clan viewer box: right above the vanilla chat when attached, otherwise the saved position. */
    public static int[] clanRect(int sw, int sh) {
        ModConfig cfg = ModConfig.get();
        if (cfg.clanAttach) {
            MinecraftClient mc = MinecraftClient.getInstance();
            var chat = mc.inGameHud.getChatHud();
            double scale = chat.getChatScale();
            int chatTop = sh - 40 - (int) Math.ceil(chat.getHeight() * scale);
            return new int[]{2, Math.max(0, chatTop - 6 - clanHeight())};
        }
        return rect(cfg.clanX, cfg.clanY, clanBoxW(), clanHeight(), sw, sh);
    }

    /** Width chosen in the settings; 0 means "same as the vanilla chat" (or CLAN_W when free). */
    public static int clanWidth() {
        int chosen = ModConfig.get().clanWidth;
        if (chosen > 0) {
            return chosen;
        }
        if (ModConfig.get().clanAttach) {
            var chat = MinecraftClient.getInstance().inGameHud.getChatHud();
            return Math.max(160, (int) Math.ceil((chat.getWidth() + 8) * chat.getChatScale()));
        }
        return CLAN_W;
    }

    private static int[] rect(float fx, float fy, int w, int h, int sw, int sh) {
        int x = Math.round(fx * Math.max(0, sw - w));
        int y = Math.round(fy * Math.max(0, sh - h));
        return new int[]{x, y};
    }

    public static void render(DrawContext c, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) {
            return;
        }
        ModConfig cfg = ModConfig.get();
        int sw = c.getScaledWindowWidth();
        int sh = c.getScaledWindowHeight();
        if (mc.currentScreen != null) {
            return;
        }
        if (cfg.clanViewer) {
            drawClanViewer(c, mc.textRenderer, sw, sh, false);
        }
        renderQuick(c);
        renderWelcome(c, mc.textRenderer);
        renderUpdateNotice(c, mc.textRenderer);
        long now = System.currentTimeMillis();
        long window = cfg.notificationSeconds * 1000L;
        String hint = panelKey == null ? "" : "Pulsa " + panelKey.getBoundKeyLocalizedText().getString() + " para responder";

        List<TeleportTracker.Request> pending = ChatUtilitiesCore.TELEPORTS.pending();
        boolean tpShown = cfg.tpNotifications && !pending.isEmpty() && now - ChatUtilitiesCore.lastTpNotice < window;
        RaidParty.Invite invite = RaidParty.invite();
        // Stays until the invitation is accepted or expires (90 s), with a countdown.
        long raidWindow = RaidParty.INVITE_MS;
        if (cfg.raidInviteNotice && invite != null && now - invite.time() < raidWindow) {
            int[] r = tpRect(sw, sh);
            String what = invite.stars() + "★" + (invite.pokemon().isEmpty() ? "" : " " + invite.pokemon());
            long left = Math.max(0, (raidWindow - (now - invite.time())) / 1000);
            String key = panelKey == null ? "" : panelKey.getBoundKeyLocalizedText().getString();
            drawSlideToast(c, mc.textRenderer, r[0], r[1] + (tpShown ? tpBoxH() + 6 : 0), toastW(), tpScale(), Theme.GOLD,
                    "INVITACIÓN A RAID · " + invite.from(),
                    what + (key.isEmpty() ? "" : " · Pulsa " + key + " para aceptar") + " · " + left + "s",
                    invite.from(), now - RaidParty.inviteNoticeAt(), raidWindow - (RaidParty.inviteNoticeAt() - invite.time()));
        }
        if (tpShown) {
            TeleportTracker.Request last = pending.get(pending.size() - 1);
            int[] r = tpRect(sw, sh);
            // "Nombre quiere ir hacia ti" / "Pulsa G para responder".
            String label = last.kind().label;
            String title = last.nick() + " " + Character.toLowerCase(label.charAt(0)) + label.substring(1);
            String line = (hint.isEmpty() ? "" : hint + ".") + (pending.size() > 1 ? " +" + (pending.size() - 1) + " más" : "");
            drawSlideToast(c, mc.textRenderer, r[0], r[1], toastW(), tpScale(),
                    last.kind() == TeleportTracker.Kind.TO_YOU ? Theme.INFO : Theme.WARN,
                    title, line.strip(), last.nick(), now - ChatUtilitiesCore.lastTpNotice, window);
        }

        ChatEntry msg = ChatUtilitiesCore.lastMessageNotice;
        if (cfg.messageNotifications && msg != null && msg.type() == MessageType.PRIVATE_IN
                && now - ChatUtilitiesCore.lastMessageNoticeAt < window + 7_500L) {
            int unread = ChatUtilitiesCore.LOG.unreadPrivate();
            if (unread > 0) {
                int[] r = msgRect(sw, sh);
                String title = "PRIVADO · " + msg.sender() + (unread > 1 ? "  +" + (unread - 1) : "");
                drawSlideToast(c, mc.textRenderer, r[0], r[1], msg.type().color, title, msg.body(), msg.sender(),
                        now - ChatUtilitiesCore.lastMessageNoticeAt, window);
            }
        }

        int pendingCount = ChatUtilitiesCore.LOG.unreadPrivate();
        if (cfg.pendingPill && pendingCount > 0) {
            int[] r = pendingRect(sw, sh, pendingW(mc.textRenderer, pendingCount));
            drawPending(c, mc.textRenderer, r[0], r[1], pendingCount, pendingSender(), 1f);
        }

    }

    public static String stars(int n) {
        return "★".repeat(Math.max(0, Math.min(5, n)));
    }

    private static float fade(long now, long start, long window) {
        long age = now - start;
        float in = Math.min(1f, age / 150f);
        float out = Math.min(1f, (window - age) / 400f);
        return Math.max(0f, Math.min(in, out));
    }

    private static final long WELCOME_MS = 12_000L;
    private static long welcomeAt;

    /**
     * Shown once after installing: top-left card that tells which key opens the panel. It slides
     * in from the left, stays 12 seconds and is not shown again (config "welcomeShown").
     */
    private static long updateNoticeAt;

    /** "Actualización disponible" card, top left, once per session for 12 seconds. */
    static void renderUpdateNotice(DrawContext c, TextRenderer tr) {
        if (chatutilities.core.update.Updater.state() != chatutilities.core.update.Updater.State.AVAILABLE
                || !ModConfig.get().welcomeShown) {
            return;
        }
        long now = System.currentTimeMillis();
        if (updateNoticeAt == 0) {
            updateNoticeAt = now;
        }
        long age = now - updateNoticeAt;
        if (age > WELCOME_MS) {
            return;
        }
        String key = panelKey == null ? "H" : panelKey.getBoundKeyLocalizedText().getString();
        topLeftCard(c, tr, "⬇ v" + chatutilities.core.update.Updater.latestVersion(),
                "Pulsa " + key, age, new ItemStack(Items.EMERALD));
    }

    /** Card that slides in from the left at the top-left corner and fades out (welcome, update). */
    private static void topLeftCard(DrawContext c, TextRenderer tr, String title, String line, long age, ItemStack icon) {
        float in = Math.min(1f, age / 300f);
        float out = Math.min(1f, (WELCOME_MS - age) / 500f);
        float alpha = Math.max(0f, Math.min(in, out));
        if (alpha < 0.03f) {
            return;
        }
        int w = Math.max(tr.getWidth(title), tr.getWidth(line)) + 38;
        int h = 30;
        int x = 6 - Math.round((1f - in) * (w + 6));
        int y = 6;
        int a = (int) (alpha * 255);
        Theme.round(c, x + 1, y + 2, w, h, Theme.R_PANEL, Theme.withAlpha(0, (int) (a * 0.3f)));
        Theme.box(c, x, y, w, h, Theme.R_PANEL, Theme.withAlpha(Theme.BASE, (int) (a * 0.92f)),
                Theme.withAlpha(Theme.ACCENT, (int) (a * 0.7f)));
        Theme.round(c, x + 3, y + 5, 2, h - 10, 1, Theme.withAlpha(Theme.ACCENT, a));
        if (alpha > 0.4f) {
            c.drawItem(icon, x + 9, y + 7);
        }
        if (a >= 30) {
            c.drawText(tr, title, x + 30, y + 6, Theme.withAlpha(Theme.ACCENT, a), false);
            c.drawText(tr, line, x + 30, y + 17, Theme.withAlpha(Theme.TEXT, a), false);
        }
    }

    static void renderWelcome(DrawContext c, TextRenderer tr) {
        ModConfig cfg = ModConfig.get();
        if (cfg.welcomeShown) {
            return;
        }
        long now = System.currentTimeMillis();
        if (welcomeAt == 0) {
            welcomeAt = now;
        }
        long age = now - welcomeAt;
        if (age > WELCOME_MS) {
            cfg.welcomeShown = true;
            ModConfig.save();
            return;
        }
        float in = Math.min(1f, age / 300f);
        float out = Math.min(1f, (WELCOME_MS - age) / 500f);
        float alpha = Math.max(0f, Math.min(in, out));
        if (alpha < 0.03f) {
            return;
        }
        String key = panelKey == null ? "H" : panelKey.getBoundKeyLocalizedText().getString();
        String title = "☀ ChatUtilities DiosesMon instalado";
        String line = "Pulsa " + key + " para abrir el panel";
        int w = Math.max(tr.getWidth(title), tr.getWidth(line)) + 38;
        int h = 30;
        int x = 6 - Math.round((1f - in) * (w + 6));
        int y = 6;
        int a = (int) (alpha * 255);
        Theme.round(c, x + 1, y + 2, w, h, Theme.R_PANEL, Theme.withAlpha(0, (int) (a * 0.3f)));
        Theme.box(c, x, y, w, h, Theme.R_PANEL, Theme.withAlpha(Theme.BASE, (int) (a * 0.92f)),
                Theme.withAlpha(Theme.ACCENT, (int) (a * 0.7f)));
        Theme.round(c, x + 3, y + 5, 2, h - 10, 1, Theme.withAlpha(Theme.ACCENT, a));
        if (alpha > 0.4f) {
            c.drawItem(new ItemStack(Items.WRITABLE_BOOK), x + 9, y + 7);
        }
        if (a >= 30) {
            c.drawText(tr, title, x + 30, y + 6, Theme.withAlpha(Theme.ACCENT, a), false);
            c.drawText(tr, line, x + 30, y + 17, Theme.withAlpha(Theme.TEXT, a), false);
        }
    }

    /** "Te pusiste AFK" / "Hambre restaurada": small pill at the top that floats in and fades out. */
    public static void renderQuick(DrawContext c) {
        ChatUtilitiesCore.QuickNotice q = ChatUtilitiesCore.quickNotice;
        if (q == null) {
            return;
        }
        long age = System.currentTimeMillis() - q.at();
        long life = 3000L;
        if (age < 0 || age > life) {
            return;
        }
        float in = Math.min(1f, age / 220f);
        float out = Math.min(1f, (life - age) / 500f);
        float alpha = Math.max(0f, Math.min(in, out));
        if (alpha < 0.03f) {
            return;
        }
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int a = (int) (alpha * 255);
        int w = tr.getWidth(q.text()) + 36;
        int h = 22;
        int x = (c.getScaledWindowWidth() - w) / 2;
        int y = 22 - Math.round((1f - in) * 10f) - Math.round((1f - out) * 6f);
        int accent = q.kind() == ChatUtilitiesCore.QuickKind.AFK_ON ? Theme.WARN : Theme.SUCCESS;
        Theme.round(c, x + 1, y + 2, w, h, h / 2, Theme.withAlpha(0, (int) (a * 0.3f)));
        Theme.box(c, x, y, w, h, h / 2, Theme.withAlpha(Theme.BASE, (int) (a * 0.9f)), Theme.withAlpha(accent, (int) (a * 0.6f)));
        if (alpha > 0.4f) {
            c.drawItem(new ItemStack(q.kind() == ChatUtilitiesCore.QuickKind.FEED ? Items.COOKED_BEEF : Items.RED_BED), x + 7, y + 3);
        }
        if (a >= 30) {
            c.drawText(tr, q.text(), x + 27, y + 7, Theme.withAlpha(Theme.TEXT, a), false);
        }
    }

    /** Milliseconds each clan message stays on screen. */
    public static final long CLAN_LINE_MS = 10_000L;
    private static final int STACK_GAP = 4;

    static net.minecraft.client.util.SkinTextures skinOf(String name) {
        PlayerListEntry entry = name == null || name.isEmpty() ? null : PlayerDirectory.entry(name);
        return entry != null ? entry.getSkinTextures()
                : net.minecraft.client.util.DefaultSkinHelper.getSkinTextures(java.util.UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ---- clan feed: at most N messages on screen, the rest wait their turn ----

    /** A visible clan notice: when it appeared and, if a newer one pushed it out, its shortened life. */
    private static final class Shown {
        final ChatEntry entry;
        final long shownAt;
        long forcedLife = -1;

        Shown(ChatEntry entry, long shownAt) {
            this.entry = entry;
            this.shownAt = shownAt;
        }
    }

    /** A visible message is replaced only after it has been on screen at least this long. */
    public static final long CLAN_MIN_SHOW_MS = 2_000L;
    private static final List<Shown> clanShown = new ArrayList<>();
    private static final java.util.ArrayDeque<ChatEntry> clanWaiting = new java.util.ArrayDeque<>();
    private static final java.util.Set<ChatEntry> clanSeen = new java.util.HashSet<>();
    /** Messages older than this (history loaded from disk, before joining) are never shown. */
    private static long clanSince = System.currentTimeMillis();

    private static long clanLife(TextRenderer tr, Shown s, int width) {
        if (s.forcedLife > 0) {
            return s.forcedLife;
        }
        return life(Layout.of(tr, "X", s.entry.body(), width), CLAN_LINE_MS);
    }

    private static void updateClanFeed(TextRenderer tr, long now, int width) {
        int max = ModConfig.get().clanMaxShown;
        for (ChatEntry e : ChatUtilitiesCore.LOG.tail(ChatLog.Channel.CLAN, 20)) {
            // Only fresh messages, each once. Anything older than 30 s is never queued, so
            // forgetting old entries below can never bring a message back (that caused a loop).
            // Your own clan/alliance messages are never shown on screen (only in the panel).
            boolean mine = e.fromMe() || e.sender().equalsIgnoreCase(ChatUtilitiesCore.self());
            if (!mine && e.time() >= clanSince && now - e.time() < 30_000L && clanSeen.add(e)) {
                clanWaiting.add(e);
            }
        }
        clanSeen.removeIf(e -> now - e.time() > 120_000L);
        clanWaiting.removeIf(e -> now - e.time() > 60_000L);
        clanShown.removeIf(s -> now - s.shownAt > clanLife(tr, s, width));
        while (!clanWaiting.isEmpty() && clanShown.size() < max) {
            clanShown.add(new Shown(clanWaiting.poll(), now));
        }
        if (!clanWaiting.isEmpty() && !clanShown.isEmpty()) {
            // Full: the oldest visible message closes once it has been seen for 2 s, then the next one comes in.
            Shown oldest = clanShown.get(0);
            long age = now - oldest.shownAt;
            long closing = Arrival.HIDE + Arrival.STAY + Arrival.FADE;
            if (age >= CLAN_MIN_SHOW_MS && clanLife(tr, oldest, width) > age + closing) {
                oldest.forcedLife = age + closing;
            }
        }
    }

    /**
     * Clan chat, one notice per message with the same arrival animation as private messages.
     * The newest is at the bottom (the anchor slot) and older ones stack upwards, up to the
     * configured number; allied clans are shown in blue with their tag.
     *
     * @param preview draw numbered sample slots, without expiring (position editor)
     */
    public static void drawClanViewer(DrawContext c, TextRenderer tr, int sw, int sh, boolean preview) {
        int width = clanWidth();
        float scale = clanScale();
        int[] r = clanRect(sw, sh);
        int bottom = r[1] + clanHeight();
        int gap = Math.round(STACK_GAP * scale);
        if (preview) {
            // One sample per slot so you can see where every message will appear.
            int max = ModConfig.get().clanMaxShown;
            int y = bottom;
            for (int i = 0; i < max; i++) {
                y -= clanHeight();
                String title = i == 0 ? "CLAN · Mensaje más nuevo" : "CLAN · Mensaje " + (i + 1);
                drawNotice(c, tr, r[0], y, width, scale, MessageType.CLAN.color, title, "Vista previa", "",
                        2_000L, Long.MAX_VALUE / 4);
                y -= gap;
            }
            return;
        }
        long now = System.currentTimeMillis();
        updateClanFeed(tr, now, width);
        int y = bottom;
        for (int i = clanShown.size() - 1; i >= 0; i--) {
            Shown s = clanShown.get(i);
            ChatEntry e = s.entry;
            long age = now - s.shownAt;
            long lifeMs = clanLife(tr, s, width);
            boolean ally = ChatLog.isAlly(e);
            String title = (ally ? "[" + e.tag() + "] " : "") + e.sender();
            int th = noticeHeight(tr, e.body(), width, scale, age, lifeMs);
            if (th <= 0) {
                continue;
            }
            y -= th;
            if (y < 0) {
                break;
            }
            drawNotice(c, tr, r[0], y, width, scale, ally ? ALLY_COLOR : MessageType.CLAN.color, title, e.body(),
                    e.sender(), age, lifeMs);
            y -= gap;
        }
    }

    /** Forget queued clan messages (disconnect). */
    public static void resetClanFeed() {
        clanShown.clear();
        clanWaiting.clear();
        clanSeen.clear();
        clanSince = System.currentTimeMillis();
    }

    /**
     * Arrival animation shared by the clan viewer and the message toasts:
     * the head pops in, the box opens to the right and shows the message, then the message hides
     * from right to left back into the head, the head stays a moment and fades out.
     *
     * @param open      0 = only the head, 1 = fully open
     * @param headScale pop-in size of the head (0..1)
     * @param headAlpha opacity of the head and box
     * @param textAlpha opacity of the text
     */
    record Arrival(float open, float headScale, float headAlpha, float textAlpha) {
        static final long POP = 220;
        static final long GROW = 380;
        static final long HIDE = 450;
        static final long STAY = 350;
        static final long FADE = 300;

        static Arrival of(long age, long life) {
            if (age < 0 || age > life) {
                return new Arrival(0, 0, 0, 0);
            }
            float scale = easeOut(age / (float) POP);
            float grow = age < POP ? 0f : easeOut((age - POP) / (float) GROW);
            long hideStart = life - FADE - STAY - HIDE;
            float hide = age < hideStart ? 0f : easeOut((age - hideStart) / (float) HIDE);
            float open = Math.min(grow, 1f - hide);
            float fade = Math.max(0f, Math.min(1f, (life - age) / (float) FADE));
            float text = Math.max(0f, Math.min(1f, open * 1.6f - 0.4f));
            return new Arrival(open, scale, Math.min(fade, Math.min(1f, scale * 1.5f)), text);
        }
    }

    /** Player head centred on (cx, cy), scaled for the pop-in. */
    private static void drawHead(DrawContext c, String name, int cx, int cy, int size, float scale, float alpha) {
        int s = Math.max(1, Math.round(size * Math.min(1f, scale)));
        if (alpha < 1f) {
            c.setShaderColor(1f, 1f, 1f, alpha);
        }
        PlayerSkinDrawer.draw(c, skinOf(name), cx - s / 2, cy - s / 2, s);
        c.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static float easeOut(float t) {
        float u = 1f - Math.max(0f, Math.min(1f, t));
        return 1f - u * u * u;
    }


    // ---- notices: full message, whole-notice scale ----

    private static final int TEXT_X = 31;
    private static final int HEAD_BOX = 32;

    /** Wrapped title and body of a notice; nothing is cut, the box grows to fit the whole message. */
    private record Layout(List<OrderedText> title, List<OrderedText> body, int height) {
        static Layout of(TextRenderer tr, String title, String line, int width) {
            int textW = Math.max(40, width - TEXT_X - 8);
            List<OrderedText> t = tr.wrapLines(Text.literal(title), textW);
            List<OrderedText> b = line == null || line.isEmpty() ? List.of() : tr.wrapLines(Text.literal(line), textW);
            int h = Math.max(TOAST_H, 7 + t.size() * 10 + 1 + b.size() * 10 + 5);
            return new Layout(t, b, h);
        }

        int lines() {
            return title.size() + body.size();
        }
    }

    /** Longer messages stay longer on screen so they can be read. */
    private static long life(Layout l, long window) {
        return window + Math.max(0, l.lines() - 2) * 1500L;
    }

    public static void drawSlideToast(DrawContext c, TextRenderer tr, int x, int y, int accent, String title,
                                      String line, String head, long age, long window) {
        drawSlideToast(c, tr, x, y, toastW(), toastScale(), accent, title, line, head, age, window);
    }

    /** Current on-screen height of a notice with an exact life (0 when not visible), used to stack. */
    static int noticeHeight(TextRenderer tr, String line, int width, float scale, long age, long lifeMs) {
        Layout l = Layout.of(tr, "X", line, width);
        Arrival an = Arrival.of(age, lifeMs);
        if (an.headAlpha() * 255 < 6) {
            return 0;
        }
        return Math.round((TOAST_H + (l.height() - TOAST_H) * an.open()) * scale);
    }

    /** Scissor in screen pixels for a rectangle given in the notice's own (unscaled) coordinates. */
    private static void clip(DrawContext c, int x, int y, float s, int l, int t, int r, int b) {
        c.enableScissor(x + (int) Math.floor(l * s), y + (int) Math.floor(t * s),
                x + (int) Math.ceil(r * s), y + (int) Math.ceil(b * s));
    }

    /**
     * Notice with the sender's head and the arrival animation. Everything (box, head and text) is
     * drawn at {@code scale}; title and message wrap to as many lines as they need.
     *
     * @param fullW width before scaling
     */
    public static void drawSlideToast(DrawContext c, TextRenderer tr, int x, int y, int fullW, float scale, int accent,
                                      String title, String line, String head, long age, long window) {
        long lifeMs = life(Layout.of(tr, title, line, fullW), window);
        drawNotice(c, tr, x, y, fullW, scale, accent, title, line, head, age, lifeMs);
    }

    /** Same as {@link #drawSlideToast} with an exact life (the clan feed shortens it when replaced). */
    static void drawNotice(DrawContext c, TextRenderer tr, int x, int y, int fullW, float scale, int accent,
                           String title, String line, String head, long age, long lifeMs) {
        Layout l = Layout.of(tr, title, line, fullW);
        if (age < 0 || age > lifeMs) {
            return;
        }
        Arrival an = Arrival.of(age, lifeMs);
        int a = (int) (255 * an.headAlpha());
        if (a < 6) {
            return;
        }
        // Head first (a small square box), then the box opens to the right; at the end it closes
        // from right to left back to the head, which fades out.
        int w = Math.round(HEAD_BOX + (fullW - HEAD_BOX) * an.open());
        int h = Math.round(TOAST_H + (l.height() - TOAST_H) * an.open());
        var m = c.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1f);
        Theme.round(c, 1, 2, w, h, Theme.R_PANEL, Theme.withAlpha(0, (int) (a * 0.3f)));
        Theme.box(c, 0, 0, w, h, Theme.R_PANEL, Theme.withAlpha(Theme.BASE, (int) (a * 0.9f)),
                Theme.withAlpha(Theme.EDGE, (int) (a * 0.34f)));
        if (an.open() > 0.05f) {
            Theme.round(c, 3, 7, 2, h - 14, 1, Theme.withAlpha(accent, (int) (a * an.open())));
        }
        drawHead(c, head, 16, 16, 16, an.headScale(), an.headAlpha());
        int textAlpha = (int) (255 * an.textAlpha());
        if (textAlpha >= 20 && w - 8 - TEXT_X > 4) {
            clip(c, x, y, scale, 0, 0, w - 4, h);
            int ly = 7;
            for (OrderedText o : l.title()) {
                c.drawText(tr, o, TEXT_X, ly, Theme.withAlpha(accent, textAlpha), false);
                ly += 10;
            }
            ly += 1;
            for (OrderedText o : l.body()) {
                c.drawText(tr, o, TEXT_X, ly, Theme.withAlpha(Theme.TEXT, textAlpha), false);
                ly += 10;
            }
            c.disableScissor();
        }
        m.pop();
    }

    /** Static preview used by the position editor, at the notices' scale. */
    public static void drawToast(DrawContext c, TextRenderer tr, int x, int y, float s, int accent, String title,
                                 String line, String head, float alpha) {
        if (alpha <= 0.02f) {
            return;
        }
        int w = toastW();
        Layout l = Layout.of(tr, title, line, w);
        int a = (int) (alpha * 255);
        var m = c.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(s, s, 1f);
        Theme.round(c, 1, 2, w, l.height(), Theme.R_PANEL, Theme.withAlpha(0, (int) (a * 0.3f)));
        Theme.box(c, 0, 0, w, l.height(), Theme.R_PANEL, Theme.withAlpha(Theme.BASE, (int) (a * 0.88f)),
                Theme.withAlpha(Theme.EDGE, (int) (a * 0.34f)));
        Theme.round(c, 3, 7, 2, l.height() - 14, 1, Theme.withAlpha(accent, a));
        drawHead(c, head, 16, 16, 16, 1f, alpha);
        if (a >= 30) {
            int ly = 7;
            for (OrderedText o : l.title()) {
                c.drawText(tr, o, TEXT_X, ly, Theme.withAlpha(accent, a), false);
                ly += 10;
            }
            ly += 1;
            for (OrderedText o : l.body()) {
                c.drawText(tr, o, TEXT_X, ly, Theme.withAlpha(Theme.TEXT, a), false);
                ly += 10;
            }
        }
        m.pop();
    }
}
