package chatutilities.core.raids;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pokémon species names, read from Cobblemon's own language files
 * ({@code cobblemon.species.<id>.name} in {@code assets/cobblemon/lang/*.json}), so every species
 * the client knows is recognised without a hard-coded list.
 */
public final class PokemonNames {
    private static final Pattern KEY = Pattern.compile("^cobblemon\\.species\\.([a-z0-9_]+)\\.name$");
    /** Normal Spanish words that are also species names. */
    private static final Set<String> COMMON_WORDS = Set.of("abra", "mime", "seel", "onix");

    /** Normalised name (lower case, no accents, letters and digits only) to display name. */
    private static Map<String, String> names;

    private PokemonNames() {
    }

    public static String normalize(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static synchronized Map<String, String> names() {
        if (names != null) {
            return names;
        }
        Map<String, String> out = new HashMap<>();
        ResourceManager rm;
        try {
            rm = MinecraftClient.getInstance().getResourceManager();
        } catch (Throwable notReady) {
            return out;
        }
        for (String lang : new String[]{"en_us", "es_es", "es_mx"}) {
            try {
                Optional<Resource> res = rm.getResource(Identifier.of("cobblemon", "lang/" + lang + ".json"));
                if (res.isEmpty()) {
                    continue;
                }
                try (Reader r = res.get().getReader()) {
                    JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                        Matcher m = KEY.matcher(e.getKey());
                        if (!m.matches() || !e.getValue().isJsonPrimitive()) {
                            continue;
                        }
                        String display = e.getValue().getAsString();
                        String key = normalize(display);
                        if (key.length() >= 3 && !COMMON_WORDS.contains(key)) {
                            out.putIfAbsent(key, display);
                        }
                        out.putIfAbsent(m.group(1).replace("_", ""), display);
                    }
                }
            } catch (Exception ignored) {
                // Missing or broken language file: fewer names recognised.
            }
        }
        if (!out.isEmpty()) {
            names = out;
        }
        return out;
    }

    /** Display name for a species id or name ("blissey" gives "Blissey"); capitalised input if unknown. */
    public static String display(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String known = names().get(normalize(raw));
        if (known != null) {
            return known;
        }
        String s = raw.strip();
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    /** First species named in a chat message (single words and two-word names), or empty. */
    public static String find(String text) {
        Map<String, String> map = names();
        if (map.isEmpty() || text == null) {
            return "";
        }
        String[] words = text.split("[\\s,.;:!?¡¿/()\\[\\]\"']+");
        for (int i = 0; i < words.length; i++) {
            String w = normalize(words[i]);
            if (i + 1 < words.length) {
                String two = map.get(w + normalize(words[i + 1]));
                if (two != null && w.length() >= 2) {
                    return two;
                }
            }
            if (w.length() >= 3 && !COMMON_WORDS.contains(w)) {
                String one = map.get(w);
                if (one != null) {
                    return one;
                }
            }
        }
        return "";
    }
}
