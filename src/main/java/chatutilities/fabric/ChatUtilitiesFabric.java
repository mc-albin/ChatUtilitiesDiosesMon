package chatutilities.fabric;

import chatutilities.core.ChatText;
import chatutilities.core.ChatUtilitiesCore;
import chatutilities.core.chat.MessageClassifier;
import chatutilities.core.config.ModConfig;
import chatutilities.hud.HudOverlay;
import chatutilities.ui.ConfigScreen;
import chatutilities.ui.MainPanelScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric glue: forwards events to the loader-independent core. */
public class ChatUtilitiesFabric implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("ChatUtilitiesDiosesMon");
    private static final String CATEGORY = "category.chatutilitiesdiosesmon";

    private static KeyBinding panelKey;
    private static KeyBinding configKey;

    @Override
    public void onInitializeClient() {
        ModConfig.configDir = FabricLoader.getInstance().getConfigDir();
        ModConfig.load();
        chatutilities.ui.Theme.apply();
        ChatUtilitiesCore.init();

        panelKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.chatutilitiesdiosesmon.panel",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));
        configKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.chatutilitiesdiosesmon.config",
                InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), CATEGORY));
        HudOverlay.setPanelKey(panelKey);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            safe(ChatUtilitiesCore::shutdown);
            safe(chatutilities.core.update.Updater::applyOnExit);
        });
        chatutilities.core.update.Updater.checkAsync();

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay || !ModConfig.get().hideClanInVanilla || message == null) {
                return true;
            }
            return !MessageClassifier.isClan(ChatText.clean(message.getString()));
        });
        // GAME fires when the message is shown, GAME_CANCELED when any mod hid it: log both.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                safe(() -> ChatUtilitiesCore.onGameMessage(message));
            }
        });
        ClientReceiveMessageEvents.GAME_CANCELED.register((message, overlay) -> {
            if (!overlay) {
                safe(() -> ChatUtilitiesCore.onGameMessage(message));
            }
        });
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) ->
                safe(() -> ChatUtilitiesCore.onChatMessage(message, sender == null ? null : sender.getName())));
        ClientReceiveMessageEvents.CHAT_CANCELED.register((message, signed, sender, params, time) ->
                safe(() -> ChatUtilitiesCore.onChatMessage(message, sender == null ? null : sender.getName())));

        // Every command the player sends (typed or from the panel) refines the TP and payment history.
        ClientSendMessageEvents.COMMAND.register(command -> safe(() -> ChatUtilitiesCore.onCommand(command)));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            HudOverlay.resetClanFeed();
            ChatUtilitiesCore.onJoin();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            HudOverlay.resetClanFeed();
            ChatUtilitiesCore.onDisconnect();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ChatUtilitiesCore.tick();
            while (panelKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    client.setScreen(MainPanelScreen.openFromKey(panelKey));
                }
            }
            while (configKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new ConfigScreen(null));
                }
            }
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> safe(() -> HudOverlay.render(context, tickCounter.getTickDelta(false))));
        LOG.info("ChatUtilitiesDiosesMon cargado.");
    }

    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            LOG.warn("Error en ChatUtilitiesDiosesMon: {}", e.toString());
        }
    }
}
