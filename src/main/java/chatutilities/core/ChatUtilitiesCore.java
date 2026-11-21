package chatutilities.core;

import chatutilities.core.chat.ChatEntry;
import chatutilities.core.chat.ChatHistoryStore;
import chatutilities.core.chat.ChatLog;
import chatutilities.core.chat.MessageClassifier;
import chatutilities.core.chat.MessageType;
import chatutilities.core.config.ModConfig;
import chatutilities.core.pay.PayHistory;
import chatutilities.core.sound.Notifier;
import chatutilities.core.players.PlayerDirectory;
import chatutilities.core.raids.RaidParty;
import chatutilities.core.tp.TeleportTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;

import java.nio.file.Path;

/**
 * Loader-independent hub. A loader module (Fabric now, Forge/NeoForge later) only has to
 * forward chat messages, connection events, ticks and HUD rendering here.
 */
public final class ChatUtilitiesCore {
    public static final String MOD_ID = "chatutilitiesdiosesmon";
    public static final ChatLog LOG = new ChatLog();
    public static final TeleportTracker TELEPORTS = new TeleportTracker();
    public static final PayHistory PAYS = new PayHistory();

    /** Last notification events, read by the HUD. */
    public static volatile long lastTpNotice;
    public static volatile ChatEntry lastMessageNotice;
    public static volatile long lastMessageNoticeAt;

    /** Short floating confirmation for quick actions (AFK / FEED), from the server's reply. */
    public enum QuickKind { AFK_ON, AFK_OFF, FEED }

    public record QuickNotice(QuickKind kind, String text, long at) {
    }

    public static volatile QuickNotice quickNotice;
    /** AFK state taken from the server's own replies, whoever triggered it. */
    public static volatile boolean afk;
    /** When the current AFK period started (epoch millis), 0 when not AFK. */
    public static volatile long afkSince;
    /** When AFK / FEED were last pressed in the panel; replies only show a notice after that. */
    private static volatile long afkAskedAt;
    private static volatile long feedAskedAt;
    private static final long QUICK_WINDOW_MS = 10_000L;
    private static RegistryWrapper.WrapperLookup registries;
    private static int saveTicks;

    private ChatUtilitiesCore() {
    }

    private static Path historyDir() {
        return ModConfig.configDir.resolve("chatutilitiesdiosesmon").resolve("history");
    }

    /** Called once at start-up, after the config is loaded. */
    public static void init() {
        TELEPORTS.load(historyDir().resolve("tp.json"));
        PAYS.load(historyDir().resolve("pagos.json"));
    }

    /** The panel's AFK / FEED button was pressed. */
    public static void quickRequested(String command) {
        long now = System.currentTimeMillis();
        if (command.equalsIgnoreCase("afk")) {
            afkAskedAt = now;
        } else if (command.equalsIgnoreCase("feed")) {
            feedAskedAt = now;
        }
    }

    /** Every command the player sends (typed or from the panel), without the slash. */
    public static void onCommand(String command) {
        long now = System.currentTimeMillis();
        TELEPORTS.onCommand(command, now);
        PAYS.onCommand(command, now);
    }

    public static String self() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.getSession() == null ? "" : mc.getSession().getUsername();
    }

    /** System/plugin messages (most DiosesMon chat arrives this way). */
    public static void onGameMessage(Text message) {
        if (message == null) {
            return;
        }
        String clean = ChatText.clean(message.getString());
        if (clean.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        checkQuickReply(clean, now);
        PAYS.onLine(clean, now);
        if (RaidParty.onLine(clean, self(), now)) {
            Notifier.play(Notifier.Category.TP);
        }
        TeleportTracker.Request request = TELEPORTS.observe(clean, now);
        if (request != null) {
            Notifier.play(Notifier.Category.TP);
        }
        MessageClassifier.Result r = MessageClassifier.classify(clean, self());
        MessageType type = request != null ? MessageType.TELEPORT : r.type();
        record(new ChatEntry(now, type, r.sender(), r.peer(), r.body(), r.tag(), r.rank(),
                isSelf(r.sender()) && type != MessageType.PRIVATE_IN, message));
        if (request != null && ModConfig.get().tpNotifications) {
            lastTpNotice = now;
        }
    }

    /** Signed vanilla player chat. */
    public static void onChatMessage(Text message, String senderName) {
        if (message == null) {
            return;
        }
        String clean = ChatText.clean(message.getString());
        MessageClassifier.Result r = MessageClassifier.classify(clean, self());
        if (r.type() == MessageType.SYSTEM) {
            String sender = senderName == null ? "" : senderName;
            r = new MessageClassifier.Result(MessageType.PLAYER, sender, "", clean, "", "");
        }
        record(new ChatEntry(System.currentTimeMillis(), r.type(), r.sender(), r.peer(), r.body(),
                r.tag(), r.rank(), isSelf(r.sender()) && r.type() != MessageType.PRIVATE_IN, message));
    }

    private static void record(ChatEntry entry) {
        if (!entry.type().isChat()) {
            return;
        }
        LOG.add(entry);
        if (!entry.fromMe()) {
            if (entry.type() == MessageType.PRIVATE_IN) {
                Notifier.play(Notifier.Category.PRIVATE);
            } else if (entry.type() == MessageType.CLAN) {
                Notifier.play(Notifier.Category.CLAN);
            }
        }
        ModConfig cfg = ModConfig.get();
        // Clan messages have their own HUD viewer; the toast is only for private messages.
        if (cfg.messageNotifications && cfg.notifyPrivate && entry.type() == MessageType.PRIVATE_IN) {
            lastMessageNotice = entry;
            lastMessageNoticeAt = entry.time();
        }
    }

    /**
     * Server replies verified in logs: "{@code <Name> ahora está AFK.}", "{@code <Name> ya no está AFK.}"
     * (sometimes prefixed with "(!)") and "{@code Hambre restaurada.}".
     */
    private static void checkQuickReply(String clean, long now) {
        String text = clean.replaceFirst("^\\(!\\)\\s*", "").strip();
        String me = self();
        if (!me.isEmpty() && text.equalsIgnoreCase(me + " ahora está AFK.")) {
            if (!afk) {
                afkSince = now;
            }
            afk = true;
        } else if (!me.isEmpty() && text.equalsIgnoreCase(me + " ya no está AFK.")) {
            afk = false;
            afkSince = 0;
        }
        boolean feedAsked = now - feedAskedAt < QUICK_WINDOW_MS;
        boolean afkAsked = now - afkAskedAt < QUICK_WINDOW_MS;
        if (feedAsked && (text.equalsIgnoreCase("Hambre restaurada.") || text.equalsIgnoreCase("Hambre restaurada"))) {
            feedAskedAt = 0;
            quickNotice = new QuickNotice(QuickKind.FEED, "Hambre restaurada", now);
        } else if (afkAsked && !me.isEmpty() && text.equalsIgnoreCase(me + " ahora está AFK.")) {
            afkAskedAt = 0;
            quickNotice = new QuickNotice(QuickKind.AFK_ON, "Te pusiste AFK", now);
        } else if (afkAsked && !me.isEmpty() && text.equalsIgnoreCase(me + " ya no está AFK.")) {
            afkAskedAt = 0;
            quickNotice = new QuickNotice(QuickKind.AFK_OFF, "Ya no estás AFK", now);
        }
    }

    private static boolean isSelf(String name) {
        return name != null && !name.isEmpty() && name.equalsIgnoreCase(self());
    }

    public static void onJoin() {
        ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler != null) {
            registries = handler.getRegistryManager();
            ChatHistoryStore.load(LOG, registries);
        }
        PlayerDirectory.reset();
        // Joining a server (also when the proxy moves you to the lobby or another server, which is
        // not a disconnect) starts outside AFK: the old AFK period belonged to the previous server.
        afk = false;
        afkSince = 0;
    }

    public static void onDisconnect() {
        saveHistory();
        TELEPORTS.disconnect();
        RaidParty.reset();
        PlayerDirectory.reset();
        lastTpNotice = 0;
        lastMessageNotice = null;
        afk = false;
        afkSince = 0;
    }

    /** "mm:ss" or "h:mm:ss" since the start of the AFK period. */
    public static String afkElapsed() {
        long since = afkSince;
        if (!afk || since <= 0) {
            return "";
        }
        long s = Math.max(0, (System.currentTimeMillis() - since) / 1000);
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format("%02d:%02d", s / 60, s % 60);
    }

    /** Game closing: save and wait for the files to be written. */
    public static void shutdown() {
        saveHistory();
        ChatHistoryStore.flush();
    }

    public static void saveHistory() {
        saveSmall(true);
        if (registries != null) {
            LOG.takeDirty();
            ChatHistoryStore.save(LOG, registries);
        }
    }

    /** TP and payment histories (small files, written directly). */
    private static void saveSmall(boolean force) {
        String tp = TELEPORTS.takeJson(force);
        if (tp != null) {
            TeleportTracker.write(historyDir().resolve("tp.json"), tp);
        }
        String pays = PAYS.takeJson(force);
        if (pays != null) {
            TeleportTracker.write(historyDir().resolve("pagos.json"), pays);
        }
    }

    public static void tick() {
        if (++saveTicks >= 1200) {
            saveTicks = 0;
            if (LOG.takeDirty() && registries != null) {
                ChatHistoryStore.save(LOG, registries);
            }
            saveSmall(false);
        }
        TELEPORTS.expire(System.currentTimeMillis());
        RaidParty.tick(System.currentTimeMillis());
        PlayerDirectory.tick();
        PAYS.expire(System.currentTimeMillis());
    }
}
