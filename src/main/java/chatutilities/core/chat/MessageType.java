package chatutilities.core.chat;

/** Category assigned to every chat line. Colours are ARGB and used by the UI. */
public enum MessageType {
    PLAYER("Jugador", 0xFFE8D7AE),
    PRIVATE_IN("Privado", 0xFF7DD3FC),
    PRIVATE_OUT("Privado", 0xFFFFEAB8),
    CLAN("Clan", 0xFF4ADE80),
    LEGENDARY("Legendario", 0xFFFFC94D),
    TELEPORT("Teletransporte", 0xFF60A5FA),
    PAYMENT("Pago", 0xFFFBBF24),
    SYSTEM("Servidor", 0xFFB89A5E);

    public final String label;
    public final int color;

    MessageType(String label, int color) {
        this.label = label;
        this.color = color;
    }

    /** Types kept in the chat history; other server/system lines are not stored. */
    public boolean isChat() {
        return this == PLAYER || this == CLAN || this == LEGENDARY || isPrivate();
    }

    public boolean isPrivate() {
        return this == PRIVATE_IN || this == PRIVATE_OUT;
    }
}
