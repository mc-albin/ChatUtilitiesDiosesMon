package chatutilities.core.pay;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Payment history. Pokédollar transfers are confirmed by the server lines found in logs:
 * {@code ¡Has enviado 10000$ a Name!} and {@code Has recibido 10000$ de Name.}
 * Battle/Safari points have no known confirmation line, so payments sent with {@code /pay}
 * are recorded as sent without server confirmation.
 */
public final class PayHistory {
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern OUT = Pattern.compile(
            "^¡?\\s*Has enviado ([0-9][0-9.,]*)\\s*\\$ a ([A-Za-z0-9_]{3,16})\\s*!?$", FLAGS);
    private static final Pattern IN = Pattern.compile(
            "^¡?\\s*Has recibido ([0-9][0-9.,]*)\\s*\\$ de ([A-Za-z0-9_]{3,16})\\s*[.!]?$", FLAGS);
    private static final int MAX = 200;
    private static final long MATCH_MS = 15_000L;
    private static final Gson GSON = new Gson();

    public static final class Payment {
        public long time;
        public boolean outgoing;
        public String player;
        /** Currency id (pokedolares, battle_points, safari_points). */
        public String currency;
        public long amount;
        public boolean confirmed;
        /** The server refused it (error reply, or no confirmation in time for Pokédólares). */
        public boolean rejected;
    }

    /** Server replies that mean the last /pay failed (not enough money, unknown player...). */
    private static final Pattern ERROR = Pattern.compile(
            "no\\s+tienes\\s+suficiente|insuficiente|no\\s+se\\s+(?:ha\\s+)?encontr|no\\s+encontrad|no\\s+existe"
                    + "|no\\s+est[aá]\\s+(?:conectad|en\\s+l[ií]nea)|no\\s+puedes|cantidad\\s+(?:no\\s+)?(?:v[aá]lida|inv[aá]lida)"
                    + "|uso\\s+correcto|usage:|error", FLAGS);
    /** Pokédólar payments without the server's "Has enviado" after this time count as rejected. */
    private static final long CONFIRM_TIMEOUT_MS = 12_000L;
    private static final long ERROR_WINDOW_MS = 6_000L;

    /** Marks pending Pokédólar payments as rejected when the server never confirmed them. */
    public synchronized void expire(long now) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            Payment p = entries.get(i);
            if (now - p.time > CONFIRM_TIMEOUT_MS * 4) {
                break;
            }
            if (p.outgoing && !p.confirmed && !p.rejected && "pokedolares".equals(p.currency)
                    && now - p.time > CONFIRM_TIMEOUT_MS) {
                p.rejected = true;
                changed();
            }
        }
    }

    /** Waiting for the server's answer (only right after sending). */
    public static boolean pending(Payment p, long now) {
        return p.outgoing && !p.confirmed && !p.rejected && now - p.time <= CONFIRM_TIMEOUT_MS;
    }

    private final List<Payment> entries = new ArrayList<>();
    private long revision;
    private boolean dirty;

    public synchronized void onLine(String clean, long now) {
        Matcher m = OUT.matcher(clean);
        if (m.matches()) {
            long amount = parse(m.group(1));
            String player = m.group(2);
            for (int i = entries.size() - 1; i >= 0; i--) {
                Payment p = entries.get(i);
                if (now - p.time > MATCH_MS) {
                    break;
                }
                if (p.outgoing && !p.confirmed && p.amount == amount && "pokedolares".equals(p.currency)
                        && p.player.equalsIgnoreCase(player)) {
                    p.confirmed = true;
                    changed();
                    return;
                }
            }
            add(now, true, player, "pokedolares", amount, true);
            return;
        }
        m = IN.matcher(clean);
        if (m.matches()) {
            add(now, false, m.group(2), "pokedolares", parse(m.group(1)), true);
            return;
        }
        if (ERROR.matcher(clean).find()) {
            // An error right after a /pay: the newest unanswered payment was refused.
            for (int i = entries.size() - 1; i >= 0; i--) {
                Payment p = entries.get(i);
                if (now - p.time > ERROR_WINDOW_MS) {
                    break;
                }
                if (p.outgoing && !p.confirmed && !p.rejected) {
                    p.rejected = true;
                    changed();
                    return;
                }
            }
        }
    }

    /** {@code pay <player> <currency> <amount>} sent by the player (panel or typed). */
    public synchronized void onCommand(String command, long now) {
        String[] parts = command.strip().split("\\s+");
        if (parts.length != 4 || !parts[0].equalsIgnoreCase("pay")) {
            return;
        }
        long amount = parse(parts[3]);
        if (amount <= 0) {
            return;
        }
        add(now, true, parts[1], parts[2].toLowerCase(Locale.ROOT), amount, false);
    }

    private void add(long time, boolean outgoing, String player, String currency, long amount, boolean confirmed) {
        Payment p = new Payment();
        p.time = time;
        p.outgoing = outgoing;
        p.player = player;
        p.currency = currency;
        p.amount = amount;
        p.confirmed = confirmed;
        entries.add(p);
        while (entries.size() > MAX) {
            entries.remove(0);
        }
        changed();
    }

    private void changed() {
        revision++;
        dirty = true;
    }

    private static long parse(String s) {
        try {
            return Long.parseLong(s.replace(".", "").replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Newest first. */
    public synchronized List<Payment> list() {
        List<Payment> out = new ArrayList<>(entries);
        java.util.Collections.reverse(out);
        return out;
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized void load(Path file) {
        if (!Files.exists(file)) {
            return;
        }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<Payment> list = GSON.fromJson(r, new TypeToken<List<Payment>>() { }.getType());
            if (list != null) {
                entries.addAll(0, list);
                while (entries.size() > MAX) {
                    entries.remove(0);
                }
                revision++;
            }
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
        return GSON.toJson(entries);
    }
}
