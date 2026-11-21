package chatutilities.core.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.command.CommandSource;

import java.util.HashSet;
import java.util.Set;

/**
 * Reads the command tree the server sends on join. The tree only contains commands the
 * player may run, so it tells us whether /afk, /feed, /tpaccept, /clan... really exist.
 */
public final class ServerCommands {
    public enum Support { YES, NO, UNKNOWN }

    private ServerCommands() {
    }

    private static CommandDispatcher<CommandSource> dispatcher() {
        ClientPlayNetworkHandler h = MinecraftClient.getInstance().getNetworkHandler();
        return h == null ? null : h.getCommandDispatcher();
    }

    public static boolean connected() {
        return MinecraftClient.getInstance().getNetworkHandler() != null;
    }

    /** True once the server has sent a non-empty command tree. */
    public static boolean treeKnown() {
        CommandDispatcher<CommandSource> d = dispatcher();
        return d != null && !d.getRoot().getChildren().isEmpty();
    }

    public static Support support(String literal) {
        if (!treeKnown()) {
            return Support.UNKNOWN;
        }
        return node(literal) != null ? Support.YES : Support.NO;
    }

    /** Allows the action unless the tree positively says the command is missing. */
    public static boolean usable(String literal) {
        return connected() && support(literal) != Support.NO;
    }

    public static boolean takesArgument(String literal) {
        CommandNode<CommandSource> n = node(literal);
        if (n == null) {
            return false;
        }
        for (CommandNode<CommandSource> child : n.getChildren()) {
            if (child instanceof ArgumentCommandNode) {
                return true;
            }
        }
        return false;
    }

    /** Sends a command without the leading slash. */
    public static boolean send(String command) {
        ClientPlayNetworkHandler h = MinecraftClient.getInstance().getNetworkHandler();
        if (h == null || command == null || command.isBlank()) {
            return false;
        }
        h.sendChatCommand(command.startsWith("/") ? command.substring(1) : command);
        return true;
    }

    /** The private-message command offered by the server ("msg" preferred, then "tell"). */
    public static String privateCommand() {
        if (support("msg") != Support.NO) {
            return "msg";
        }
        if (support("tell") == Support.YES) {
            return "tell";
        }
        return support("w") == Support.YES ? "w" : "msg";
    }

    /**
     * Command used to reject a teleport request. DiosesMon's own message says
     * "Usa /tpaccept o /tpcancel"; /tpdeny is used only if the server exposes it.
     */
    public static String denyCommand(String nick) {
        if (support("tpdeny") == Support.YES) {
            return takesArgument("tpdeny") ? "tpdeny " + nick : "tpdeny";
        }
        if (support("tpcancel") == Support.YES && takesArgument("tpcancel")) {
            return "tpcancel " + nick;
        }
        return "tpcancel";
    }

    /**
     * {@code /raids accept}, with the inviter's name when the server's command tree shows that
     * {@code accept} takes a player argument (the server replies "No tienes ninguna invitacion de
     * ese jugador" when it is the wrong one).
     */
    public static String raidAccept(String inviter) {
        CommandNode<CommandSource> raids = node("raids");
        CommandNode<CommandSource> accept = raids == null ? null : raids.getChild("accept");
        if (accept != null && inviter != null && !inviter.isEmpty()) {
            for (CommandNode<CommandSource> child : accept.getChildren()) {
                if (child instanceof ArgumentCommandNode) {
                    return "raids accept " + inviter;
                }
            }
        }
        return "raids accept";
    }

    /**
     * Checks a currency id against the literal children of {@code /pay <player>}.
     * Returns UNKNOWN when the server models currencies as a free argument.
     */
    public static Support payCurrency(String currencyId) {
        CommandNode<CommandSource> pay = node("pay");
        if (pay == null) {
            return treeKnown() ? Support.NO : Support.UNKNOWN;
        }
        Set<String> literals = new HashSet<>();
        boolean freeArgument = false;
        for (CommandNode<CommandSource> player : pay.getChildren()) {
            if (!(player instanceof ArgumentCommandNode)) {
                continue;
            }
            for (CommandNode<CommandSource> next : player.getChildren()) {
                if (next instanceof LiteralCommandNode) {
                    literals.add(next.getName());
                } else {
                    freeArgument = true;
                }
            }
        }
        if (literals.isEmpty() || freeArgument && !literals.contains(currencyId)) {
            return Support.UNKNOWN;
        }
        return literals.contains(currencyId) ? Support.YES : Support.NO;
    }

    private static CommandNode<CommandSource> node(String literal) {
        CommandDispatcher<CommandSource> d = dispatcher();
        if (d == null) {
            return null;
        }
        CommandNode<CommandSource> n = d.getRoot().getChild(literal);
        if (n != null && n.getRedirect() != null && n.getChildren().isEmpty()) {
            return n.getRedirect();
        }
        return n;
    }
}
