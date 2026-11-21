package chatutilities.core.players;

import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Skin lookup for player heads. Uses the tab list when the player is online, otherwise asks
 * Mojang once per name (vanilla skull profile lookup) and caches the result for the session.
 * Until a skin is known, the default skin for the name is returned.
 */
public final class PlayerHeads {
    private static final Map<String, SkinTextures> cache = new HashMap<>();
    private static final Set<String> requested = new HashSet<>();

    private PlayerHeads() {
    }

    public static SkinTextures get(String name) {
        if (name == null || name.isEmpty()) {
            return DefaultSkinHelper.getSkinTextures(UUID.randomUUID());
        }
        String key = name.toLowerCase(Locale.ROOT);
        PlayerListEntry entry = PlayerDirectory.entry(name);
        if (entry != null) {
            SkinTextures skin = entry.getSkinTextures();
            cache.put(key, skin);
            return skin;
        }
        SkinTextures cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        if (requested.add(key)) {
            lookup(name, key);
        }
        return DefaultSkinHelper.getSkinTextures(offlineUuid(name));
    }

    private static void lookup(String name, String key) {
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            SkullBlockEntity.fetchProfileByName(name)
                    .thenCompose(profile -> profile.isPresent()
                            ? mc.getSkinProvider().fetchSkinTextures(profile.get())
                            : CompletableFuture.completedFuture(null))
                    .thenAccept(skin -> {
                        if (skin != null) {
                            mc.execute(() -> cache.put(key, skin));
                        }
                    })
                    .exceptionally(error -> null);
        } catch (RuntimeException ignored) {
            // Profile services not ready; the default head stays.
        }
    }

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
