package chatutilities.core.tp;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Teleport request history, built from the server's chat lines (formats verified in logs):
 * <ul>
 *   <li>{@code <Name> quiere teletransportarse a ti. Usa /tpaccept o /tpcancel.} (incoming /tpa)</li>
 *   <li>{@code <Name> quiere que te teletransportes a él. ...} (incoming /tpahere)</li>
 *   <li>{@code La solicitud de teletransporte de <Name> expiró.} / {@code <Name> canceló la solicitud.}</li>
 *   <li>{@code Solicitud de teletransporte aceptada.} / {@code Solicitud aceptada. ...} (you accepted)</li>
 *   <li>{@code Solicitud enviada a <Name>.} (outgoing), {@code <Name> aceptó tu solicitud...},
 *       {@code Tu solicitud de teletransporte expiró.}</li>
 * </ul>
 * Typed commands ({@code /tpa}, {@code /tpaccept}, {@code /tpcancel}...) refine the result.
 */
public final class TeleportTracker {
    private static final String N = "[A-Za-z0-9_]{3,16}";
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern INCOMING_TPA = Pattern.compile(
            "^(?:\\[(" + N + ")\\]|(" + N + ")) quiere teletransportarse a ti\\b.*$", FLAGS);
    private static final Pattern INCOMING_HERE = Pattern.compile(
            "^(?:\\[(" + N + ")\\]|(" + N + ")) quiere que te teletransportes a (?:él|el|ella)\\b.*$", FLAGS);
    private static final Pattern EXPIRED = Pattern.compile(
            "^(?:la )?solicitud de teletransporte de (?:\\[(" + N + ")\\]|(" + N + ")) "
                    + "(?:ha expirado|expiró|ha caducado)\\.?$", FLAGS);
    private static final Pattern CANCELLED = Pattern.compile("^(" + N + ") canceló la solicitud\\.?$", FLAGS);
    private static final Pattern YOU_ACCEPTED = Pattern.compile(
            "^solicitud (?:de teletransporte )?aceptada\\b.*$", FLAGS);
    private static final Pattern SENT = Pattern.compile("^solicitud enviada a (" + N + ")\\.?$", FLAGS);
    private static final Pattern THEY_ACCEPTED = Pattern.compile("^(" + N + ") acept[oó] tu solicitud\\b.*$", FLAGS);
    /** Server reply to /tpaccept when that request no longer exists. */
    private static final Pattern NO_VALID = Pattern.compile("^no hay solicitudes v[aá]lidas\\.?$", FLAGS);
    private static final Pattern OWN_EXPIRED = Pattern.compile(
            "^tu solicitud de teletransporte (?:ha expirado|expiró|ha caducado)\\.?$", FLAGS);

    /** Requests older than this are no longer pending. */
    public static final long TIMEOUT_MS = 60_000L;
    private static final int MAX_RECORDS = 150;
    private static final Gson GSON = new Gson();

    public enum Kind {
        TO_YOU("Quiere ir hacia ti", false),
        YOU_TO_THEM("Te pide ir a su lado", false),
        OUT_TPA("Pediste ir hacia él", true),
        OUT_HERE("Le pediste que venga", true),
        OUT("Le enviaste una solicitud", true);

        public final String label;
        public final boolean outgoing;

        Kind(String label, boolean outgoing) {
            this.label = label;
            this.outgoing = outgoing;
        }
    }

    public enum State {
        PENDING("Pendiente"),
        SENT("Esperando respuesta"),
        ACCEPTED("Aceptada"),
        DENIED("Rechazada"),
        EXPIRED("Caducada"),
        CANCELLED("Cancelada por el jugador"),
        CANCELLED_BY_YOU("Cancelada por ti"),
        REPLACED("Sustituida"),
        DISCONNECTED("Conexión cerrada");

        public final String label;

        State(String label) {
            this.label = label;
        }

        public boolean open() {
            return this == PENDING || this == SENT;
        }
    }

    public record Request(long id, long time, String nick, Kind kind, State state) {
        Request with(State s) {
            return new Request(id, time, nick, kind, s);
        }
    }

    private final List<Request> records = new ArrayList<>();
    private long sequence;
    private long revision;
    private boolean dirty;
    /** Last /tpa or /tpahere typed, to know the kind of the next "Solicitud enviada a X". */
    private String lastOutCommand = "";
    private String lastOutNick = "";
    private long lastOutAt;
    /**
     * Last /tpaccept typed or clicked. The server's "Solicitud de teletransporte aceptada." has no
     * name, so it belongs to that request; without this it closed another pending request.
     */
    private String lastAcceptNick = "";
    private long lastAcceptAt;
    private static final long ACCEPT_REPLY_MS = 5_000L;

    /**
     * Feeds one cleaned chat line.
     *
     * @return the new request when the line is an incoming request, otherwise null
     */
    public synchronized Request observe(String line, long now) {
        expire(now);
        Matcher m = EXPIRED.matcher(line);
        if (m.matches()) {
            update(openFor(first(m), false), State.EXPIRED);
            return null;
        }
        m = CANCELLED.matcher(line);
        if (m.matches()) {
            update(openFor(m.group(1), false), State.CANCELLED);
            return null;
        }
        if (YOU_ACCEPTED.matcher(line).matches()) {
            if (now - lastAcceptAt < ACCEPT_REPLY_MS) {
                // Already marked by the /tpaccept command: the other requests stay pending.
                lastAcceptAt = 0;
            } else {
                update(newestOpen(false), State.ACCEPTED);
            }
            return null;
        }
        if (NO_VALID.matcher(line).matches()) {
            if (now - lastAcceptAt < ACCEPT_REPLY_MS) {
                // The server no longer had that request: show it as expired, not accepted.
                update(latestIncoming(lastAcceptNick), State.EXPIRED);
                lastAcceptAt = 0;
            }
            return null;
        }
        m = SENT.matcher(line);
        if (m.matches()) {
            String nick = m.group(1);
            Kind kind = Kind.OUT;
            if (now - lastOutAt < 10_000L && lastOutNick.equalsIgnoreCase(nick)) {
                kind = lastOutCommand.equals("tpahere") ? Kind.OUT_HERE : Kind.OUT_TPA;
            }
            update(openFor(nick, true), State.REPLACED);
            add(new Request(++sequence, now, nick, kind, State.SENT));
            return null;
        }
        m = THEY_ACCEPTED.matcher(line);
        if (m.matches()) {
            update(openFor(m.group(1), true), State.ACCEPTED);
            return null;
        }
        if (OWN_EXPIRED.matcher(line).matches()) {
            update(newestOpen(true), State.EXPIRED);
            return null;
        }
        Kind kind = null;
        m = INCOMING_TPA.matcher(line);
        if (m.matches()) {
            kind = Kind.TO_YOU;
        } else {
            m = INCOMING_HERE.matcher(line);
            if (m.matches()) {
                kind = Kind.YOU_TO_THEM;
            }
        }
        if (kind == null) {
            return null;
        }
        String nick = first(m);
        Request old = openFor(nick, false);
        if (old != null) {
            if (now - old.time() < 1000L && old.kind() == kind) {
                return null;
            }
            update(old, State.REPLACED);
        }
        Request created = new Request(++sequence, now, nick, kind, State.PENDING);
        add(created);
        return created;
    }

    /** A command the player sent (from the panel or typed), without the leading slash. */
    public synchronized void onCommand(String command, long now) {
        String[] parts = command.strip().split("\\s+");
        if (parts.length == 0) {
            return;
        }
        String base = parts[0].toLowerCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1] : "";
        switch (base) {
            case "tpa", "tpahere" -> {
                lastOutCommand = base;
                lastOutNick = arg;
                lastOutAt = now;
            }
            case "tpaccept" -> {
                Request r = arg.isEmpty() ? newestOpen(false) : openFor(arg, false);
                lastAcceptNick = r != null ? r.nick() : arg;
                lastAcceptAt = now;
                update(r, State.ACCEPTED);
            }
            case "tpdeny" -> update(arg.isEmpty() ? newestOpen(false) : openFor(arg, false), State.DENIED);
            case "tpcancel" -> {
                Request incoming = arg.isEmpty() ? newestOpen(false) : openFor(arg, false);
                if (incoming != null) {
                    update(incoming, State.DENIED);
                } else {
                    update(newestOpen(true), State.CANCELLED_BY_YOU);
                }
            }
            default -> {
            }
        }
    }

    private void add(Request r) {
        records.add(r);
        while (records.size() > MAX_RECORDS) {
            records.remove(0);
        }
        revision++;
        dirty = true;
    }

    public synchronized void expire(long now) {
        for (int i = 0; i < records.size(); i++) {
            Request r = records.get(i);
            if (r.state().open() && now - r.time() > TIMEOUT_MS) {
                records.set(i, r.with(State.EXPIRED));
                revision++;
                dirty = true;
            }
        }
    }

    /** Incoming requests waiting for an answer. */
    public synchronized List<Request> pending() {
        return records.stream().filter(r -> r.state() == State.PENDING).toList();
    }

    /** Full history (incoming and outgoing), newest first, without incoming pending ones. */
    public synchronized List<Request> history() {
        List<Request> out = new ArrayList<>();
        for (int i = records.size() - 1; i >= 0; i--) {
            if (records.get(i).state() != State.PENDING) {
                out.add(records.get(i));
            }
        }
        return out;
    }

    public synchronized void mark(long id, State state) {
        for (Request r : records) {
            if (r.id() == id) {
                update(r, state);
                return;
            }
        }
    }

    public synchronized void disconnect() {
        for (Request r : new ArrayList<>(records)) {
            if (r.state().open()) {
                update(r, State.DISCONNECTED);
            }
        }
    }

    public synchronized long revision() {
        return revision;
    }

    private Request openFor(String nick, boolean outgoing) {
        if (nick == null || nick.isEmpty()) {
            return null;
        }
        for (int i = records.size() - 1; i >= 0; i--) {
            Request r = records.get(i);
            if (r.state().open() && r.kind().outgoing == outgoing && r.nick().equalsIgnoreCase(nick)) {
                return r;
            }
        }
        return null;
    }

    /** Newest incoming request from this player, whatever its state. */
    private Request latestIncoming(String nick) {
        if (nick == null || nick.isEmpty()) {
            return null;
        }
        for (int i = records.size() - 1; i >= 0; i--) {
            Request r = records.get(i);
            if (!r.kind().outgoing && r.nick().equalsIgnoreCase(nick)) {
                return r;
            }
        }
        return null;
    }

    private Request newestOpen(boolean outgoing) {
        for (int i = records.size() - 1; i >= 0; i--) {
            Request r = records.get(i);
            if (r.state().open() && r.kind().outgoing == outgoing) {
                return r;
            }
        }
        return null;
    }

    private void update(Request r, State state) {
        if (r == null || r.state() == state) {
            return;
        }
        int index = records.indexOf(r);
        if (index >= 0) {
            records.set(index, r.with(state));
            revision++;
            dirty = true;
        }
    }

    private static String first(Matcher m) {
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    // ---- persistence (config/chatutilities/history/tp.json) ----

    private static final class Stored {
        long time;
        String nick;
        String kind;
        String state;
    }

    public synchronized void load(Path file) {
        if (!Files.exists(file)) {
            return;
        }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<Stored> list = GSON.fromJson(r, new TypeToken<List<Stored>>() { }.getType());
            if (list == null) {
                return;
            }
            List<Request> loaded = new ArrayList<>();
            for (Stored s : list) {
                try {
                    State st = State.valueOf(s.state);
                    loaded.add(new Request(++sequence, s.time, s.nick, Kind.valueOf(s.kind),
                            st.open() ? State.DISCONNECTED : st));
                } catch (RuntimeException ignored) {
                    // unknown entry from another version
                }
            }
            records.addAll(0, loaded);
            while (records.size() > MAX_RECORDS) {
                records.remove(0);
            }
            revision++;
        } catch (IOException | RuntimeException e) {
            System.err.println("[ChatUtilities] No se pudo leer " + file + ": " + e.getMessage());
        }
    }

    /** @return JSON to write, or null when nothing changed */
    public synchronized String takeJson(boolean force) {
        if (!dirty && !force) {
            return null;
        }
        dirty = false;
        List<Stored> out = new ArrayList<>();
        for (Request r : records) {
            Stored s = new Stored();
            s.time = r.time();
            s.nick = r.nick();
            s.kind = r.kind().name();
            s.state = r.state().name();
            out.add(s);
        }
        return GSON.toJson(out);
    }

    public static void write(Path file, String json) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                w.write(json);
            }
        } catch (IOException e) {
            System.err.println("[ChatUtilities] No se pudo guardar " + file + ": " + e.getMessage());
        }
    }
}
