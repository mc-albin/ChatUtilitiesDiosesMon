package chatutilities.core.pay;

import chatutilities.core.ChatText;

/** Builds and validates /pay commands. */
public final class PayCommand {
    private PayCommand() {
    }

    /** Accepts "100000" or "100.000"; separators are only visual. */
    public static long amount(String value) {
        String digits = value == null ? "" : value.replace(".", "");
        if (!digits.matches("[0-9]{1,12}")) {
            throw new IllegalArgumentException("Escribe una cantidad");
        }
        long n = Long.parseLong(digits);
        if (n <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
        return n;
    }

    /** Groups thousands with dots: 100000 -> "100.000". */
    public static String format(String digits) {
        String d = digits.replaceAll("[^0-9]", "").replaceFirst("^0+(?=\\d)", "");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < d.length(); i++) {
            if (i > 0 && (d.length() - i) % 3 == 0) {
                out.append('.');
            }
            out.append(d.charAt(i));
        }
        return out.toString();
    }

    /** @return the command without leading slash, e.g. {@code pay Steve pokedolares 500} */
    public static String build(String player, Currency currency, String amount) {
        if (!ChatText.validName(player)) {
            throw new IllegalArgumentException("Nombre de jugador no válido");
        }
        if (currency == null) {
            throw new IllegalArgumentException("Elige una moneda");
        }
        return "pay " + player + " " + currency.id + " " + amount(amount);
    }
}
