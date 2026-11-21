package chatutilities.core.chat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Chat history split into three channels (general, clan, private), each bounded on its own so
 * busy public chat never pushes out clan or private messages. Also tracks unread counters.
 */
public final class ChatLog {
    public enum Channel {
        GENERAL("general"), CLAN("clan"), PRIVATE("privados");

        public final String file;

        Channel(String file) {
            this.file = file;
        }

        public static Channel of(MessageType type) {
            return type == MessageType.CLAN ? CLAN : type.isPrivate() ? PRIVATE : GENERAL;
        }
    }

    public static final int MAX = 2000;

    private final Map<Channel, ArrayDeque<ChatEntry>> entries = new EnumMap<>(Channel.class);
    /** Lower-case peer name to display name, most recent last. */
    private final LinkedHashMap<String, String> peers = new LinkedHashMap<>();
    private final Map<String, Integer> unreadPrivate = new LinkedHashMap<>();
    private int unreadClan;
    private int unreadAlly;
    private long revision;
    private boolean dirty;

    public ChatLog() {
        for (Channel c : Channel.values()) {
            entries.put(c, new ArrayDeque<>());
        }
    }

    public synchronized void add(ChatEntry entry) {
        append(entry);
        if (entry.type() == MessageType.PRIVATE_IN) {
            unreadPrivate.merge(entry.peer().toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
        if (entry.type() == MessageType.CLAN && !entry.fromMe()) {
            if (isAlly(entry)) {
                unreadAlly++;
            } else {
                unreadClan++;
            }
        }
        revision++;
        dirty = true;
    }

    private void append(ChatEntry entry) {
        ArrayDeque<ChatEntry> q = entries.get(Channel.of(entry.type()));
        q.addLast(entry);
        while (q.size() > MAX) {
            q.removeFirst();
        }
        if (entry.type().isPrivate() && !entry.peer().isEmpty()) {
            String key = entry.peer().toLowerCase(Locale.ROOT);
            peers.remove(key);
            peers.put(key, entry.peer());
        }
    }

    /** Puts entries loaded from disk before the ones of this session; no unread counters. */
    public synchronized void restore(Channel channel, Collection<ChatEntry> loaded, List<String> savedPeers) {
        ArrayDeque<ChatEntry> current = new ArrayDeque<>(entries.get(channel));
        entries.get(channel).clear();
        LinkedHashMap<String, String> sessionPeers = new LinkedHashMap<>(peers);
        for (ChatEntry e : loaded) {
            append(e);
        }
        if (savedPeers != null) {
            peers.clear();
            for (String p : savedPeers) {
                peers.put(p.toLowerCase(Locale.ROOT), p);
            }
        }
        for (Map.Entry<String, String> p : sessionPeers.entrySet()) {
            peers.remove(p.getKey());
            peers.put(p.getKey(), p.getValue());
        }
        for (ChatEntry e : current) {
            append(e);
        }
        revision++;
    }

    public synchronized List<ChatEntry> snapshot(Channel channel, Predicate<ChatEntry> filter) {
        List<ChatEntry> out = new ArrayList<>();
        for (ChatEntry e : entries.get(channel)) {
            if (filter.test(e)) {
                out.add(e);
            }
        }
        return out;
    }

    public synchronized List<ChatEntry> all(Channel channel) {
        return new ArrayList<>(entries.get(channel));
    }

    /** Last {@code n} entries of a channel, oldest first. */
    public synchronized List<ChatEntry> tail(Channel channel, int n) {
        List<ChatEntry> all = new ArrayList<>(entries.get(channel));
        return all.subList(Math.max(0, all.size() - n), all.size());
    }

    /** Conversation partners, most recent first. */
    public synchronized List<String> peers() {
        List<String> out = new ArrayList<>(peers.values());
        Collections.reverse(out);
        return out;
    }

    /** Conversation partners, oldest first (storage order). */
    public synchronized List<String> peersInOrder() {
        return new ArrayList<>(peers.values());
    }

    public synchronized void touchPeer(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!peers.containsKey(key)) {
            peers.put(key, name);
            revision++;
            dirty = true;
        }
    }

    /** Removes a conversation from the list; it comes back if that player writes again. */
    public synchronized void closePeer(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        peers.remove(key);
        unreadPrivate.remove(key);
        revision++;
        dirty = true;
    }

    public synchronized int unreadPrivate() {
        return unreadPrivate.values().stream().mapToInt(Integer::intValue).sum();
    }

    public synchronized int unreadPrivate(String peer) {
        return unreadPrivate.getOrDefault(peer.toLowerCase(Locale.ROOT), 0);
    }

    public synchronized void readPrivate(String peer) {
        if (unreadPrivate.remove(peer.toLowerCase(Locale.ROOT)) != null) {
            revision++;
        }
    }

    /**
     * Clan chat lines from an allied clan carry that clan's tag
     * ({@code Clan Chat [LaLogia] → ...}); lines from your own clan have no tag.
     */
    public static boolean isAlly(ChatEntry e) {
        return e.type() == MessageType.CLAN && !e.tag().isEmpty();
    }

    public synchronized int unreadClan() {
        return unreadClan;
    }

    public synchronized void readClan() {
        unreadClan = 0;
    }

    public synchronized int unreadAlly() {
        return unreadAlly;
    }

    public synchronized void readAlly() {
        unreadAlly = 0;
    }

    public synchronized long revision() {
        return revision;
    }

    /** True once since the last call if anything changed that should be saved. */
    public synchronized boolean takeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }
}
