package chatutilities.core.players;

import chatutilities.core.ChatText;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Player names for pickers: the tab list plus the names the server itself suggests for
 * {@code /msg <prefix>} (vanilla command-completion request, only sent while typing).
 */
public final class PlayerDirectory {
    private static final long MIN_INTERVAL_MS = 400L;
    private static final Set<String> remote = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private static String queued;
    private static long nextAllowed;
    private static long revision;

    private PlayerDirectory() {
    }

    public static void reset() {
        remote.clear();
        queued = null;
        nextAllowed = 0;
        revision++;
    }

    public static void query(String prefix) {
        queued = prefix == null ? "" : prefix.strip();
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        if (queued == null || now < nextAllowed) {
            return;
        }
        String prefix = queued;
        queued = null;
        nextAllowed = now + MIN_INTERVAL_MS;
        request(prefix);
    }

    private static void request(String prefix) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayNetworkHandler h = mc.getNetworkHandler();
        if (h == null || !prefix.matches("[A-Za-z0-9_]{0,16}")) {
            return;
        }
        CommandDispatcher<CommandSource> d = h.getCommandDispatcher();
        String command = d.getRoot().getChild("msg") != null ? "msg"
                : d.getRoot().getChild("tell") != null ? "tell" : null;
        if (command == null) {
            return;
        }
        try {
            ParseResults<CommandSource> parse = d.parse(command + " " + prefix, h.getCommandSource());
            d.getCompletionSuggestions(parse).thenAccept(s -> mc.execute(() -> {
                for (Suggestion suggestion : s.getList()) {
                    if (ChatText.validName(suggestion.getText())) {
                        remote.add(suggestion.getText());
                    }
                }
                revision++;
            }));
        } catch (RuntimeException ignored) {
            // Suggestions are optional; the tab list still works.
        }
    }

    public static List<String> tabNames() {
        ClientPlayNetworkHandler h = MinecraftClient.getInstance().getNetworkHandler();
        if (h == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (PlayerListEntry e : h.getPlayerList()) {
            String n = e.getProfile().getName();
            if (ChatText.validName(n)) {
                names.add(n);
            }
        }
        return names;
    }

    public static List<String> matches(String prefix, int limit) {
        String p = prefix == null ? "" : prefix.strip().toLowerCase(Locale.ROOT);
        TreeSet<String> all = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        all.addAll(tabNames());
        all.addAll(remote);
        List<String> out = new ArrayList<>();
        for (String n : all) {
            if (n.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(n);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    public static PlayerListEntry entry(String name) {
        ClientPlayNetworkHandler h = MinecraftClient.getInstance().getNetworkHandler();
        return h == null || name == null || name.isEmpty() ? null : h.getPlayerListEntry(name);
    }

    public static long revision() {
        return revision;
    }
}
