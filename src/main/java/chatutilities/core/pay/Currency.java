package chatutilities.core.pay;

/**
 * Currencies accepted by DiosesMon's /pay. Syntax verified in two client mods built for
 * the server: {@code /pay <player> <currency> <amount>}.
 */
public enum Currency {
    POKEDOLARES("pokedolares", "Pokédólares", 0xFFFBBF24),
    BATTLE("battle_points", "Battle Points", 0xFFF87171),
    SAFARI("safari_points", "Safari Points", 0xFF4ADE80);

    public final String id;
    public final String label;
    public final int color;

    Currency(String id, String label, int color) {
        this.id = id;
        this.label = label;
        this.color = color;
    }
}
