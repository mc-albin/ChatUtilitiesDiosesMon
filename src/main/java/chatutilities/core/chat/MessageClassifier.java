package chatutilities.core.chat;

import chatutilities.core.ChatText;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies DiosesMon chat lines. Formats were taken from real server logs:
 * <ul>
 *   <li>Public chat: {@code [Clan] <rank glyphs> Name » message}</li>
 *   <li>Private: {@code Recibido de Name » message} / {@code Enviado a Name » message}</li>
 *   <li>Clan: {@code Clan Chat [AllyTag] → [Rango] Name ⏩ message}</li>
 * </ul>
 */
public final class MessageClassifier {
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final Pattern CLAN = Pattern.compile(
            "^[^\\p{L}\\p{N}]*Clan\\s*Chat\\s*(?:\\[([^\\]]{1,32})\\]\\s*)?[^\\p{L}\\p{N}\\[]*"
                    + "(?:\\[([^\\]]{1,24})\\]\\s*)?(?:[^\\sA-Za-z0-9_\\[]+\\s*)*"
                    + "([A-Za-z0-9_]{2,16})\\s*[^\\p{L}\\p{N}\\s]+\\s*(.*)$", FLAGS);

    /**
     * Alliance channel ({@code /alianza chat}): "Alianza Chat" or "Chat de Alianza" as a word at the
     * start of the line, never a bracketed clan name such as {@code [AlianzaEsmeralda] Name ► ...}.
     * Group 1 is the optional clan tag, 2 the rank, 3 the player and 4 the message.
     */
    private static final Pattern ALLY = Pattern.compile(
            "^[^\\p{L}\\p{N}\\[]*(?:Alianza\\s*Chat|Chat\\s*(?:de\\s*(?:la\\s*)?)?Alianza)\\b\\s*"
                    + "(?:\\[([^\\]]{1,32})\\]\\s*)?[^\\p{L}\\p{N}\\[]*"
                    + "(?:\\[([^\\]]{1,24})\\]\\s*)?(?:[^\\sA-Za-z0-9_\\[]+\\s*)*"
                    + "([A-Za-z0-9_]{2,16})\\s*[^\\p{L}\\p{N}\\s]+\\s*(.*)$", FLAGS);

    private static final Pattern PRIVATE = Pattern.compile(
            "^[^\\p{L}\\p{N}\\[]*(?:\\[[^\\]]+\\]\\s*)?(Recibido\\s+de|Enviado\\s+a)\\s+"
                    + "(?:\\[[^\\]]+\\]\\s*)?(?:[^\\sA-Za-z0-9_]+\\s*)*([A-Za-z0-9_]{3,16})\\s*"
                    + "(?:»|>>|:)\\s*(.*)$", FLAGS);

    private static final Pattern PAYMENT = Pattern.compile(
            "(?:has\\s+(?:recibido|enviado)|pago\\s+(?:recibido|enviado|realizado))\\s+[0-9][0-9.,]*", FLAGS);

    private static final Pattern TELEPORT = Pattern.compile(
            "teletransport|/tpaccept|solicitud\\s+(?:enviada|cancelada)|acept[oó]\\s+tu\\s+solicitud", FLAGS);

    /**
     * Legendary spawn broadcast, verified in logs:
     * {@code POKÉMON LEGENDARIO ► Apareció un nuevo Pokémon Legendario Suicune en el bioma Llanura.}
     * followed by {@code ¡Captúralo antes de que pasen 10 minutos!}. The despawn line
     * ("El Pokémon Legendario X ha desaparecido") is intentionally not matched.
     */
    private static final Pattern LEGENDARY = Pattern.compile(
            "^[^\\p{L}]*Pok[eé]mon\\s+Legendario\\s*[^\\p{L}\\p{N}\\s]+\\s*Apareci[oó]\\s+un\\s+nuevo\\b.*", FLAGS);
    private static final Pattern LEGENDARY_FOLLOW = Pattern.compile(
            "^[¡!]?\\s*Capt[uú]ralo\\s+antes\\s+de\\s+que\\s+pasen\\s+\\d+\\s+minutos.*", FLAGS);
    /**
     * Legendary captured broadcast: any server line (no "»" player separator) that speaks of a
     * legendary and a capture, e.g. "POKÉMON LEGENDARIO ► X ha capturado a Suicune".
     */
    private static final Pattern LEGENDARY_CAUGHT = Pattern.compile(
            "^[^»]*(?:legendari[oa][^»]*captur|captur[^»]*legendari[oa])[^»]*$", FLAGS);

    private static final Pattern LEADING_TAG = Pattern.compile("^\\[([^\\]]{1,32})\\]");

    private MessageClassifier() {
    }

    public record Result(MessageType type, String sender, String peer, String body, String tag, String rank) {
        static Result system(String body) {
            return new Result(MessageType.SYSTEM, "", "", body, "", "");
        }
    }

    public static boolean isClan(String clean) {
        return CLAN.matcher(clean).matches() || ALLY.matcher(clean).matches();
    }

    /** @param clean text already passed through {@link ChatText#clean(String)} */
    public static Result classify(String clean, String self) {
        Matcher m = CLAN.matcher(clean);
        if (m.matches() && !m.group(4).isBlank()) {
            return new Result(MessageType.CLAN, m.group(3), "", m.group(4).strip(),
                    nz(m.group(1)), nz(m.group(2)));
        }
        m = ALLY.matcher(clean);
        if (m.matches() && !m.group(4).isBlank()) {
            // Alliance lines are stored as clan lines with a tag, which marks them as allied.
            String tag = nz(m.group(1));
            return new Result(MessageType.CLAN, m.group(3), "", m.group(4).strip(),
                    tag.isEmpty() ? "Alianza" : tag, nz(m.group(2)));
        }
        m = PRIVATE.matcher(clean);
        if (m.matches() && !m.group(3).isBlank()) {
            boolean incoming = m.group(1).toLowerCase(Locale.ROOT).startsWith("recibido");
            String peer = m.group(2);
            return new Result(incoming ? MessageType.PRIVATE_IN : MessageType.PRIVATE_OUT,
                    incoming ? peer : nz(self), peer, m.group(3).strip(), "", "");
        }
        if (LEGENDARY.matcher(clean).matches() || LEGENDARY_FOLLOW.matcher(clean).matches()
                || LEGENDARY_CAUGHT.matcher(clean).matches()) {
            return new Result(MessageType.LEGENDARY, "", "", clean, "", "");
        }
        if (PAYMENT.matcher(clean).find()) {
            return new Result(MessageType.PAYMENT, "", "", clean, "", "");
        }
        if (TELEPORT.matcher(clean).find()) {
            return new Result(MessageType.TELEPORT, "", "", clean, "", "");
        }
        int sep = clean.indexOf('»');
        if (sep > 0) {
            String left = clean.substring(0, sep).strip();
            String body = clean.substring(sep + 1).strip();
            String sender = lastName(left);
            if (!sender.isEmpty() && !body.isEmpty()) {
                Matcher tag = LEADING_TAG.matcher(left);
                return new Result(MessageType.PLAYER, sender, "", body, tag.find() ? tag.group(1) : "", "");
            }
        }
        return Result.system(clean);
    }

    private static String lastName(String left) {
        String[] parts = left.split("\\s+");
        for (int i = parts.length - 1; i >= 0; i--) {
            String token = parts[i].replaceAll("^[^A-Za-z0-9_]+|[^A-Za-z0-9_]+$", "");
            if (ChatText.validName(token)) {
                return token;
            }
        }
        return "";
    }

    private static String nz(String s) {
        return s == null ? "" : s.strip();
    }
}
