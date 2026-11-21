package chatutilities.core.chat;

import chatutilities.core.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Saves the chat history between sessions, one file per section:
 * {@code config/chatutilities/history/general.json}, {@code clan.json} and
 * {@code privados.json}. The original formatted text is kept as JSON when possible.
 */
public final class ChatHistoryStore {
    /** General chat is busy; keep fewer lines on disk. */
    private static final int GENERAL_SAVED = 2000;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ChatUtilitiesDiosesMon-history");
        t.setDaemon(true);
        return t;
    });

    private static boolean loaded;

    private ChatHistoryStore() {
    }

    static final class Stored {
        long time;
        String type;
        String sender;
        String peer;
        String body;
        String tag;
        String rank;
        boolean fromMe;
        String plain;
        String json;
    }

    static final class FileData {
        List<String> peers;
        List<Stored> entries = new ArrayList<>();
    }

    private static Path dir() {
        return ModConfig.configDir.resolve("chatutilitiesdiosesmon").resolve("history");
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /** Loads the three files once per game start. */
    public static void load(ChatLog log, RegistryWrapper.WrapperLookup registries) {
        if (loaded) {
            return;
        }
        loaded = true;
        for (ChatLog.Channel channel : ChatLog.Channel.values()) {
            Path p = dir().resolve(channel.file + ".json");
            if (!Files.exists(p)) {
                continue;
            }
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                FileData data = GSON.fromJson(r, new TypeToken<FileData>() { }.getType());
                if (data == null || data.entries == null) {
                    continue;
                }
                List<ChatEntry> out = new ArrayList<>();
                for (Stored s : data.entries) {
                    ChatEntry e = decode(s, registries);
                    if (e != null) {
                        out.add(e);
                    }
                }
                log.restore(channel, out, channel == ChatLog.Channel.PRIVATE ? data.peers : null);
            } catch (IOException | RuntimeException e) {
                System.err.println("[ChatUtilities] No se pudo leer " + p + ": " + e.getMessage());
            }
        }
    }

    /** Serialises on the calling thread and writes the files in the background. */
    public static void save(ChatLog log, RegistryWrapper.WrapperLookup registries) {
        if (!loaded) {
            // Never overwrite saved history with an empty session before it was read.
            return;
        }
        for (ChatLog.Channel channel : ChatLog.Channel.values()) {
            FileData data = new FileData();
            List<ChatEntry> all = log.all(channel);
            int from = channel == ChatLog.Channel.GENERAL ? Math.max(0, all.size() - GENERAL_SAVED) : 0;
            for (ChatEntry e : all.subList(from, all.size())) {
                data.entries.add(encode(e, registries));
            }
            if (channel == ChatLog.Channel.PRIVATE) {
                data.peers = log.peersInOrder();
            }
            String json = GSON.toJson(data);
            Path target = dir().resolve(channel.file + ".json");
            if (IO.isShutdown()) {
                write(target, json);
            } else {
                IO.execute(() -> write(target, json));
            }
        }
    }

    /** Waits (up to 5 s) for pending writes; used when the game closes. */
    public static void flush() {
        IO.shutdown();
        try {
            IO.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void write(Path target, String json) {
        try {
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                w.write(json);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("[ChatUtilities] No se pudo guardar " + target + ": " + e.getMessage());
        }
    }

    private static Stored encode(ChatEntry e, RegistryWrapper.WrapperLookup registries) {
        Stored s = new Stored();
        s.time = e.time();
        s.type = e.type().name();
        s.sender = e.sender();
        s.peer = e.peer();
        s.body = e.body();
        s.tag = e.tag();
        s.rank = e.rank();
        s.fromMe = e.fromMe();
        s.plain = e.original().getString();
        try {
            s.json = Text.Serialization.toJsonString(e.original(), registries);
        } catch (RuntimeException ex) {
            s.json = null;
        }
        return s;
    }

    private static ChatEntry decode(Stored s, RegistryWrapper.WrapperLookup registries) {
        MessageType type;
        try {
            type = MessageType.valueOf(s.type);
        } catch (RuntimeException e) {
            return null;
        }
        if (!type.isChat()) {
            return null;
        }
        Text original = null;
        if (s.json != null) {
            try {
                original = Text.Serialization.fromJson(s.json, registries);
            } catch (RuntimeException e) {
                original = null;
            }
        }
        if (original == null) {
            original = Text.literal(s.plain == null ? nz(s.body) : s.plain);
        }
        return new ChatEntry(s.time, type, nz(s.sender), nz(s.peer), nz(s.body), nz(s.tag), nz(s.rank), s.fromMe, original);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
