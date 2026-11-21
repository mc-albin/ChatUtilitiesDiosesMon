package chatutilities.core;

import java.util.regex.Pattern;

/** Text helpers shared by every parser. No loader-specific code. */
public final class ChatText {
    public static final String NAME = "[A-Za-z0-9_]{3,16}";
    private static final Pattern NAME_PATTERN = Pattern.compile(NAME);
    private static final Pattern FORMAT = Pattern.compile("(?i)§x(§[0-9a-f]){6}|§[0-9a-fk-orx]");
    private static final Pattern INVISIBLE = Pattern.compile("[\\u200B-\\u200D\\uFEFF]");
    private static final Pattern PRIVATE_USE = Pattern.compile("\\p{Co}+");
    private static final Pattern SPACES = Pattern.compile("[\\p{Zs}\\s]+");

    private ChatText() {
    }

    /** Removes colour codes and invisible characters and collapses whitespace. */
    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String s = FORMAT.matcher(raw).replaceAll("");
        s = INVISIBLE.matcher(s).replaceAll("");
        s = PRIVATE_USE.matcher(s).replaceAll(" ");
        return SPACES.matcher(s).replaceAll(" ").strip();
    }

    public static boolean validName(String value) {
        return value != null && NAME_PATTERN.matcher(value).matches();
    }
}
