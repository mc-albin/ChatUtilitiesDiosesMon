package chatutilities.core.chat;

import net.minecraft.text.Text;

/**
 * One received chat line.
 *
 * @param time     reception time in epoch millis
 * @param type     classification
 * @param sender   author of the line (player name) or empty
 * @param peer     other player in a private conversation, empty otherwise
 * @param body     message content without prefixes
 * @param tag      clan tag shown by the server, empty when absent
 * @param rank     clan rank shown by the server (clan chat only), empty when absent
 * @param fromMe   true when the local player wrote it
 * @param original styled text exactly as the server sent it
 */
public record ChatEntry(long time, MessageType type, String sender, String peer, String body,
                        String tag, String rank, boolean fromMe, Text original) {
}
