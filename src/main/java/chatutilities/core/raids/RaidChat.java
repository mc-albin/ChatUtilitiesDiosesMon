package chatutilities.core.raids;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sorts public chat messages into raid posts and trade posts. Patterns come from the saved
 * DiosesMon chat history, e.g. "raid 4 estrellas tipo dragon", "tengo una raid lvl 4",
 * "Tengo una raid lvl 4 de Cloyster", "vendo raid 4 Mamoswine", "inviten a raids",
 * "vendo kit groudon completo, msg con ofertas".
 */
public final class RaidChat {
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final Pattern RAID_WORD = Pattern.compile("\\braids?\\b", FLAGS);
    private static final Pattern STARS = Pattern.compile(
            "(?:\\b([1-7])\\s*(?:estrellas?|estrella|stars?|\\*|★|⭐)"
                    + "|(?:lvl|lv|nivel|nv|level)\\.?\\s*([1-7])\\b"
                    + "|\\braids?\\s*(?:de\\s*)?([1-7])\\b)", FLAGS);
    /** The post is about an actual raid someone has or wants, not a question about raids. */
    private static final Pattern RAID_ACTION = Pattern.compile(
            "\\b(?:tengo|tenemos|vendo|compro|inviten|invitenme|invitame|invitan|invita|invitacion|tpa?|"
                    + "alguien|quien|quiere|quieren|necesit\\w*|falta\\w*|busco|buscamos|espacio|camp\\w*|ayudo|"
                    + "carreo|nick|venir|vengan|manden|hay)\\b", FLAGS);
    /** Promotions (streams, tournaments, giveaways) that merely mention raids are not raid posts. */
    private static final Pattern PROMO = Pattern.compile(
            "\\b(?:stream\\w*|twitch|youtube|yt|kick|tiktok|directo|live|torneo\\w*|sorteo\\w*|discord|canal|"
                    + "suscri\\w*|premios?|comunidad)\\b", FLAGS);
    /** Characters around the word "raid" where the action word must appear when no stars are given. */
    private static final int ACTION_WINDOW = 30;
    private static final Pattern SEEK = Pattern.compile(
            "\\b(?:inviten\\w*|invitame|invitan|invita\\s+a|me\\s+invitan|manden\\s+invitaci\\w*|ayudo|carreo|"
                    + "busco\\s+raid|yo\\s+voy|yo\\s+estoy|me\\s+uno)\\b", FLAGS);
    /** Wants to buy a raid or a place in one ("pago por cupo", "compro raid"). */
    private static final Pattern BUY = Pattern.compile("\\b(?:compr\\w*|pag[oa]\\w*|cupos?|pagaria)\\b", FLAGS);
    private static final Pattern SELL = Pattern.compile("\\b(?:vend\\w*)\\b", FLAGS);
    /** Asks whether someone has a raid ("alguien con raid lvl 5?"): they want to join, not offer. */
    private static final Pattern ASKS = Pattern.compile(
            "\\b(?:alguien\\s+(?:con|tiene|tienes|que\\s+tenga)|quien\\s+(?:tiene|tenga)|hay\\s+(?:alguna\\s+)?raids?)\\b", FLAGS);
    private static final Pattern TYPE = Pattern.compile(
            "\\b(normal|fuego|agua|planta|electrico|hielo|lucha|veneno|tierra|volador|psiquico|bicho|roca|"
                    + "fantasma|dragon|siniestro|acero|hada)\\b", FLAGS);

    private static final Pattern TRADE = Pattern.compile(
            "\\b(?:vend[oaei]\\w*|compr[oa]\\w*|cambi[oa]|cambiaria|intercambi\\w*|rento|alquil\\w*|subast\\w*|"
                    + "gts|ofertas?|precio\\w*|tradeo)\\b|^doy\\b", FLAGS);
    private static final Pattern TRADE_QUESTION = Pattern.compile(
            "^(?:se\\s+puede\\w*|como|donde|cuando|que|q)\\b.*\\?$", FLAGS);

    public enum Kind {
        /** Has a raid / room for players: send a TPA. */
        OFFER("Tiene raid"),
        /** Wants to be invited: invite them. */
        SEEK("Busca raid"),
        /** Wants to buy a raid or pay for a place. */
        BUY("Compra cupo"),
        /** Selling a raid. */
        TRADE("Vende raid");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    /**
     * @param stars   1-7 or 0 when not said
     * @param pokemon species display name or empty
     * @param types   Spanish type names said in the post (when no species was named)
     */
    public record Info(Kind kind, int stars, String pokemon, List<String> types) {
        public String summary() {
            StringBuilder s = new StringBuilder();
            if (stars > 0) {
                s.append(stars).append("★");
            }
            if (!pokemon.isEmpty()) {
                s.append(s.isEmpty() ? "" : " ").append(pokemon);
            } else if (!types.isEmpty()) {
                s.append(s.isEmpty() ? "" : " ").append("tipo ").append(String.join("/", types));
            }
            return s.toString();
        }
    }

    private RaidChat() {
    }

    private static String plain(String body) {
        return Normalizer.normalize(body, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    /** Raid details for a public post, or {@code null} when the message is not a raid post. */
    public static Info raid(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String text = plain(body);
        Matcher raidWord = RAID_WORD.matcher(text);
        if (!raidWord.find() || PROMO.matcher(text).find()) {
            return null;
        }
        Matcher stars = STARS.matcher(text);
        int n = 0;
        if (stars.find()) {
            String g = stars.group(1) != null ? stars.group(1) : stars.group(2) != null ? stars.group(2) : stars.group(3);
            n = Integer.parseInt(g);
        }
        if (n == 0) {
            // Without a star count, the action ("tengo", "inviten"...) must be right next to "raid",
            // so a long message that only mentions raids in passing is not taken as a raid post.
            boolean near = false;
            raidWord.reset();
            while (raidWord.find() && !near) {
                int from = Math.max(0, raidWord.start() - ACTION_WINDOW);
                int to = Math.min(text.length(), raidWord.end() + ACTION_WINDOW);
                near = RAID_ACTION.matcher(text.substring(from, to)).find();
            }
            if (!near) {
                return null;
            }
        }
        Kind kind = BUY.matcher(text).find() ? Kind.BUY
                : SELL.matcher(text).find() ? Kind.TRADE
                : SEEK.matcher(text).find() || ASKS.matcher(text).find() ? Kind.SEEK
                : Kind.OFFER;
        // Prefer the species named after "raid" ("vendo palkia y compro raid de fennekin").
        Matcher word = RAID_WORD.matcher(body);
        String pokemon = word.find() ? PokemonNames.find(body.substring(word.end())) : "";
        if (pokemon.isEmpty()) {
            pokemon = PokemonNames.find(body);
        }
        Set<String> types = new LinkedHashSet<>();
        if (pokemon.isEmpty()) {
            Matcher t = TYPE.matcher(text);
            while (t.find()) {
                types.add(t.group(1).toLowerCase(Locale.ROOT));
            }
        }
        return new Info(kind, n, pokemon, new ArrayList<>(types));
    }

    public static boolean isTrade(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String text = plain(body).strip();
        return TRADE.matcher(text).find() && !TRADE_QUESTION.matcher(text).matches();
    }
}
