package chatutilities.core.raids;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Your own raid party. Uses only the {@code /raids} commands and the server replies seen in the
 * DiosesMon logs. When the DiosesMon client mod is installed, its lobby state (members, leader,
 * stars, species) is read as well, since the server already sends it to you.
 *
 * <p>Verified server lines:
 * <ul>
 *   <li>{@code AxelGamer27 te invita a una raid de 4 estrellas (blissey).} then
 *       {@code [Unirse] Haz clic o usa /raids accept para entrar.}</li>
 *   <li>{@code Raid de 4 estrellas creada. Invita a tu party y pulsa Empezar.}</li>
 *   <li>{@code CheiMoon quiere unirse a tu raid.}, {@code Invitacion enviada a pierx_sg.}</li>
 *   <li>{@code Ya estas en la sala de la raid.}, {@code La raid comienza.}</li>
 *   <li>{@code Has huido de la raid.}, {@code La raid ha fracasado.}, {@code Recompensas de la raid entregadas.}</li>
 *   <li>{@code No tienes ninguna invitacion de ese jugador.}, {@code Esta raid ya ha empezado.}, {@code La raid esta llena.}</li>
 * </ul>
 */
public final class RaidParty {
    public static final long INVITE_MS = 90_000L;
    private static final long REQUEST_MS = 120_000L;
    private static final String NAME = "([A-Za-z0-9_]{3,16})";

    private static final Pattern INVITE = Pattern.compile(
            "^" + NAME + "\\s+te\\s+invita\\s+a\\s+una\\s+raid\\s+de\\s+(\\d+)\\s+estrellas?\\s*(?:\\(([^)]*)\\))?.*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATED = Pattern.compile(
            "^Raid\\s+de\\s+(\\d+)\\s+estrellas?\\s+creada.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern JOIN_REQUEST = Pattern.compile(
            "^" + NAME + "\\s+quiere\\s+unirse\\s+a\\s+tu\\s+raid\\.?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern INVITE_SENT = Pattern.compile(
            "^Invitaci[oó]n\\s+enviada\\s+a\\s+" + NAME + "\\.?$", Pattern.CASE_INSENSITIVE);

    public record Invite(String from, int stars, String pokemon, long time) {
    }

    public record Member(String name, boolean leader, boolean ready) {
    }

    /** What the panel shows about the raid you are in. */
    public record Lobby(int stars, String pokemon, String leader, int maxPlayers, String state,
                        List<Member> members, boolean detailed) {
    }

    private static volatile Invite invite;
    private static volatile long inviteNoticeAt;
    private static boolean inRoom;
    private static boolean leaderByChat;
    private static boolean started;
    private static int roomStars;
    private static String roomPokemon = "";
    private static String roomLeader = "";
    private static final Map<String, Long> requests = new LinkedHashMap<>();
    private static volatile String lastReply = "";
    private static volatile long lastReplyAt;

    private RaidParty() {
    }

    /** Reads one server line; returns true when it was a new raid invitation. */
    public static synchronized boolean onLine(String clean, String self, long now) {
        String text = clean.replaceFirst("^\\(!\\)\\s*", "").strip();
        Matcher m = INVITE.matcher(text);
        if (m.matches()) {
            invite = new Invite(m.group(1), Integer.parseInt(m.group(2)), PokemonNames.display(m.group(3)), now);
            inviteNoticeAt = now;
            return true;
        }
        m = CREATED.matcher(text);
        if (m.matches()) {
            inRoom = true;
            leaderByChat = true;
            started = false;
            roomStars = Integer.parseInt(m.group(1));
            roomPokemon = "";
            roomLeader = self;
            requests.clear();
            reply(text, now);
            return false;
        }
        m = JOIN_REQUEST.matcher(text);
        if (m.matches()) {
            requests.put(m.group(1), now);
            reply(text, now);
            return false;
        }
        m = INVITE_SENT.matcher(text);
        if (m.matches()) {
            String invited = m.group(1);
            requests.keySet().removeIf(n -> n.equalsIgnoreCase(invited));
            reply(text, now);
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("ya estas en la sala de la raid")) {
            Invite inv = invite;
            if (!inRoom && inv != null) {
                roomStars = inv.stars();
                roomPokemon = inv.pokemon();
                roomLeader = inv.from();
                leaderByChat = false;
            }
            inRoom = true;
            invite = null;
        } else if (lower.startsWith("la raid comienza") || lower.startsWith("preparando la arena de la raid")) {
            inRoom = true;
            started = true;
            invite = null;
        } else if (lower.startsWith("has huido de la raid") || lower.startsWith("la raid ha fracasado")
                || lower.startsWith("recompensas de la raid entregadas")) {
            clearRoom();
        } else if (lower.startsWith("no tienes ninguna invitacion") || lower.startsWith("esta raid ya ha empezado")
                || lower.startsWith("la raid esta llena")) {
            invite = null;
            reply(text, now);
        } else if (lower.startsWith("solicitud enviada al lider de la raid") || lower.endsWith("no esta conectado.")) {
            reply(text, now);
        }
        return false;
    }

    private static void reply(String text, long now) {
        lastReply = text;
        lastReplyAt = now;
    }

    private static void clearRoom() {
        inRoom = false;
        leaderByChat = false;
        started = false;
        roomStars = 0;
        roomPokemon = "";
        roomLeader = "";
        requests.clear();
    }

    public static synchronized void tick(long now) {
        Invite inv = invite;
        if (inv != null && now - inv.time() > INVITE_MS) {
            invite = null;
        }
        requests.values().removeIf(t -> now - t > REQUEST_MS);
    }

    public static synchronized void reset() {
        invite = null;
        clearRoom();
    }

    /** Called after /raids leave was sent. */
    public static synchronized void left() {
        clearRoom();
    }

    /** Called after /raids accept was sent. */
    public static synchronized void accepted() {
        Invite inv = invite;
        if (inv != null) {
            roomStars = inv.stars();
            roomPokemon = inv.pokemon();
            roomLeader = inv.from();
        }
    }

    public static Invite invite() {
        return invite;
    }

    public static long inviteNoticeAt() {
        return inviteNoticeAt;
    }

    public static String lastReply(long now) {
        return now - lastReplyAt < 8_000L ? lastReply : "";
    }

    /** Players who asked to join your raid (chat replies plus the client mod's list). */
    public static synchronized List<String> requests() {
        List<String> out = new ArrayList<>(requests.keySet());
        for (String n : ClientMod.requests()) {
            if (out.stream().noneMatch(n::equalsIgnoreCase)) {
                out.add(n);
            }
        }
        return out;
    }

    public static synchronized void dropRequest(String name) {
        requests.keySet().removeIf(n -> n.equalsIgnoreCase(name));
    }

    /** Your raid, or {@code null} when you are not in one. */
    public static synchronized Lobby lobby(String self) {
        Lobby fromMod = ClientMod.lobby();
        if (fromMod != null) {
            return fromMod;
        }
        if (!inRoom) {
            return null;
        }
        List<Member> members = new ArrayList<>();
        if (!roomLeader.isEmpty()) {
            members.add(new Member(roomLeader, true, false));
        }
        if (!self.isEmpty() && !self.equalsIgnoreCase(roomLeader)) {
            members.add(new Member(self, false, false));
        }
        return new Lobby(roomStars, roomPokemon, roomLeader, 0, started ? "En combate" : "En la sala", members, false);
    }

    public static synchronized boolean isLeader(String self) {
        Lobby l = lobby(self);
        return l != null && (l.leader().equalsIgnoreCase(self) || leaderByChat && !l.detailed());
    }

    /** Reflection bridge to {@code net.diosesmon.client.raids.RaidsClientState} (read-only). */
    private static final class ClientMod {
        private static boolean resolved;
        private static Class<?> state;

        private static Class<?> state() {
            if (!resolved) {
                resolved = true;
                try {
                    state = Class.forName("net.diosesmon.client.raids.RaidsClientState");
                } catch (Throwable ignored) {
                    state = null;
                }
            }
            return state;
        }

        private static Object call(Object target, String method) throws Exception {
            Class<?> c = target instanceof Class<?> k ? k : target.getClass();
            Method m = c.getMethod(method);
            m.setAccessible(true);
            return m.invoke(target instanceof Class<?> ? null : target);
        }

        private static String str(Object o) {
            return o == null ? "" : o.toString();
        }

        static Lobby lobby() {
            Class<?> c = state();
            if (c == null) {
                return null;
            }
            try {
                if (!Boolean.TRUE.equals(call(c, "hasLobby"))) {
                    return null;
                }
                List<Member> members = new ArrayList<>();
                Object list = call(c, "members");
                if (list instanceof List<?> l) {
                    for (Object m : l) {
                        members.add(new Member(str(call(m, "name")), Boolean.TRUE.equals(call(m, "leader")),
                                Boolean.TRUE.equals(call(m, "ready"))));
                    }
                }
                Object stars = call(c, "stars");
                Object max = call(c, "maxPlayers");
                return new Lobby(stars instanceof Integer i ? i : 0, PokemonNames.display(str(call(c, "species"))),
                        str(call(c, "leader")), max instanceof Integer i ? i : 0, stateLabel(str(call(c, "state"))),
                        members, true);
            } catch (Throwable error) {
                return null;
            }
        }

        static List<String> requests() {
            Class<?> c = state();
            List<String> out = new ArrayList<>();
            if (c == null) {
                return out;
            }
            try {
                if (!Boolean.TRUE.equals(call(c, "hasLobby"))) {
                    return out;
                }
                Object list = call(c, "requests");
                if (list instanceof List<?> l) {
                    for (Object r : l) {
                        String n = str(call(r, "name"));
                        if (!n.isEmpty()) {
                            out.add(n);
                        }
                    }
                }
            } catch (Throwable ignored) {
                // Older client mod: chat replies still work.
            }
            return out;
        }

        private static String stateLabel(String s) {
            String l = s.toLowerCase(Locale.ROOT);
            if (l.contains("battle") || l.contains("fight") || l.contains("combat")) {
                return "En combate";
            }
            if (l.contains("start") || l.contains("prepar")) {
                return "Empezando";
            }
            return "En la sala";
        }
    }
}
