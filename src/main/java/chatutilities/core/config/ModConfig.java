package chatutilities.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persistent settings stored in config/chatutilitiesdiosesmon.json. */
public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig instance = new ModConfig();
    /** Set by the loader module before {@link #load()}. */
    public static Path configDir = Path.of("config");

    public boolean tpNotifications = true;
    public boolean messageNotifications = true;
    public boolean notifyPrivate = true;
    public boolean notifyClan = true;
    /** Notification anchors as screen fractions (0..1). */
    public float tpX = 0.5f;
    public float tpY = 0.02f;
    public float msgX = 0.99f;
    public float msgY = 0.35f;
    /** Fixed "unread private messages" pill: stays on screen until they are read. */
    public boolean pendingPill = true;
    public float pendX = 0.99f;
    public float pendY = 0.22f;
    public int notificationSeconds = 6;
    /** Panel background alpha (60..255). */
    public int panelOpacity = 205;
    public boolean hideClanInVanilla = false;
    /** Floating clan chat viewer. */
    public boolean clanViewer = true;
    public int clanViewerLines = 5;
    /** Sits right above the vanilla chat instead of a free position. */
    public boolean clanAttach = true;
    public float clanX = 0f;
    public float clanY = 0.5f;
    /** Notification tones (Notifier.Tone names) and volumes 0..100. */
    public String soundTp = "BELL";
    public String soundPrivate = "PLING";
    public String soundClan = "NONE";
    public int volumeTp = 70;
    public int volumePrivate = 70;
    public int volumeClan = 50;
    /** Raid invitation sound, and the sound for new posts in the Raids tab (off until chosen). */
    public String soundRaidInvite = "BELL";
    public String soundRaidPost = "NONE";
    public int volumeRaidInvite = 70;
    public int volumeRaidPost = 60;
    /** Raid invitation notice: stays on screen until accepted or expired, with the key to accept. */
    public boolean raidInviteNotice = true;
    public String lastSection = "chat";
    /** True once the "press H to open the panel" welcome notice has been shown after installing. */
    public boolean welcomeShown = false;
    /** Panel size: 0 small, 1 normal, 2 large. */
    public int panelSize = 1;
    /** Width of the floating notices (private messages, TP, raids). */
    public int toastWidth = 190;
    /** Width of the floating clan viewer; 0 = same as the vanilla chat. */
    public int clanWidth = 0;
    /** Whole-notice scale (box, head and text) of the private-message notice, 0.5..2. */
    public float toastScale = 1f;
    /** Whole-notice scale of the TP and raid notices, independent from the private one. */
    public float tpScale = 1f;
    /** Clan messages on screen at the same time (the rest wait their turn). */
    public int clanMaxShown = 2;
    /** Hide Minecraft's chat messages (opening the chat with T shows only the typing box). */
    public boolean hideVanillaChat = true;
    /** Size of the scoreboard sidebar, 0.4..1 (never hidden). */
    public float scoreboardScale = 1f;
    /** Whole-notice scale of the clan messages, 0.5..2. */
    public float clanScale = 1f;
    /** Colour theme: a Theme.Preset name (OCEANO, ESMERALDA, ..., CUSTOM). */
    public String theme = "OCEANO";
    /** Custom theme accent and background (RGB 0xRRGGBB), used when theme is CUSTOM. */
    public int customAccent = 0x5CC8FF;
    public int customBase = 0x06121F;
    /** True once the user has picked custom colours (otherwise they start from the active theme). */
    public boolean customSet = false;

    public static ModConfig get() {
        return instance;
    }

    private static Path path() {
        return configDir.resolve("chatutilitiesdiosesmon.json");
    }

    public static void load() {
        Path p = path();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                ModConfig loaded = GSON.fromJson(r, ModConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (IOException | RuntimeException e) {
                System.err.println("[ChatUtilities] Config corrupta, usando valores por defecto: " + e.getMessage());
            }
        }
        instance.sanitize();
        save();
    }

    public static void save() {
        Path p = path();
        try {
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, w);
            }
        } catch (IOException e) {
            System.err.println("[ChatUtilities] No se pudo guardar la config: " + e.getMessage());
        }
    }

    public void resetPositions() {
        ModConfig d = new ModConfig();
        tpX = d.tpX;
        tpY = d.tpY;
        msgX = d.msgX;
        msgY = d.msgY;
        pendX = d.pendX;
        pendY = d.pendY;
        clanX = d.clanX;
        clanY = d.clanY;
    }

    public void sanitize() {
        tpX = clamp01(tpX);
        tpY = clamp01(tpY);
        msgX = clamp01(msgX);
        msgY = clamp01(msgY);
        pendX = clamp01(pendX);
        pendY = clamp01(pendY);
        clanX = clamp01(clanX);
        clanY = clamp01(clanY);
        clanViewerLines = Math.max(2, Math.min(12, clanViewerLines));
        volumeTp = Math.max(0, Math.min(100, volumeTp));
        volumePrivate = Math.max(0, Math.min(100, volumePrivate));
        volumeClan = Math.max(0, Math.min(100, volumeClan));
        volumeRaidInvite = Math.max(0, Math.min(100, volumeRaidInvite));
        volumeRaidPost = Math.max(0, Math.min(100, volumeRaidPost));
        notificationSeconds = Math.max(1, Math.min(20, notificationSeconds));
        panelOpacity = Math.max(60, Math.min(255, panelOpacity));
        if (lastSection == null) {
            lastSection = "chat";
        }
        panelSize = Math.max(0, Math.min(2, panelSize));
        toastWidth = Math.max(160, Math.min(320, toastWidth));
        clanWidth = clanWidth <= 0 ? 0 : Math.max(180, Math.min(400, clanWidth));
        toastScale = clampScale(toastScale);
        tpScale = clampScale(tpScale);
        scoreboardScale = Float.isNaN(scoreboardScale) ? 1f : Math.max(0.4f, Math.min(1f, scoreboardScale));
        clanMaxShown = Math.max(1, Math.min(5, clanMaxShown));
        clanScale = clampScale(clanScale);
        // The gold theme was removed: old configs that still name it get the default theme.
        if (theme == null || theme.isBlank() || theme.equalsIgnoreCase("DORADO")) {
            theme = "OCEANO";
        }
        customAccent &= 0xFFFFFF;
        customBase &= 0xFFFFFF;
    }

    public static float clampScale(float v) {
        return Float.isNaN(v) || v <= 0 ? 1f : Math.max(0.5f, Math.min(2f, v));
    }

    private static float clamp01(float v) {
        return Float.isNaN(v) ? 0.5f : Math.max(0f, Math.min(1f, v));
    }
}
