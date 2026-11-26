package chatutilities.core.sound;

import chatutilities.core.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.function.Supplier;

/** Notification sounds: one tone and volume per category, chosen in the settings. */
public final class Notifier {
    public enum Tone {
        NONE("Sin sonido", null),
        BELL("Campanilla", () -> SoundEvents.BLOCK_NOTE_BLOCK_BELL.value()),
        PLING("Pling", () -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value()),
        CHIME("Carillón", () -> SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value()),
        XYLOPHONE("Xilófono", () -> SoundEvents.BLOCK_NOTE_BLOCK_XYLOPHONE.value()),
        HARP("Arpa", () -> SoundEvents.BLOCK_NOTE_BLOCK_HARP.value()),
        AMETHYST("Amatista", () -> SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME),
        ORB("Experiencia", () -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP),
        LEVEL("Subir nivel", () -> SoundEvents.ENTITY_PLAYER_LEVELUP),
        BIG_BELL("Campana", () -> SoundEvents.BLOCK_BELL_USE);

        public final String label;
        private final Supplier<SoundEvent> sound;

        Tone(String label, Supplier<SoundEvent> sound) {
            this.label = label;
            this.sound = sound;
        }

        public Tone next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Tone parse(String name, Tone fallback) {
            try {
                return name == null ? fallback : valueOf(name);
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }
    }

    public enum Category {
        TP("TP"), PRIVATE("Privados"), CLAN("Clan"), RAID_INVITE("Inv. raid"), RAID_POST("Raids");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    private Notifier() {
    }

    public static Tone tone(Category c) {
        ModConfig cfg = ModConfig.get();
        return switch (c) {
            case TP -> Tone.parse(cfg.soundTp, Tone.BELL);
            case PRIVATE -> Tone.parse(cfg.soundPrivate, Tone.PLING);
            case CLAN -> Tone.parse(cfg.soundClan, Tone.NONE);
            case RAID_INVITE -> Tone.parse(cfg.soundRaidInvite, Tone.BELL);
            case RAID_POST -> Tone.parse(cfg.soundRaidPost, Tone.NONE);
        };
    }

    public static void setTone(Category c, Tone t) {
        ModConfig cfg = ModConfig.get();
        switch (c) {
            case TP -> cfg.soundTp = t.name();
            case PRIVATE -> cfg.soundPrivate = t.name();
            case CLAN -> cfg.soundClan = t.name();
            case RAID_INVITE -> cfg.soundRaidInvite = t.name();
            case RAID_POST -> cfg.soundRaidPost = t.name();
        }
    }

    /** Volume 0..100. */
    public static int volume(Category c) {
        ModConfig cfg = ModConfig.get();
        return switch (c) {
            case TP -> cfg.volumeTp;
            case PRIVATE -> cfg.volumePrivate;
            case CLAN -> cfg.volumeClan;
            case RAID_INVITE -> cfg.volumeRaidInvite;
            case RAID_POST -> cfg.volumeRaidPost;
        };
    }

    public static void setVolume(Category c, int v) {
        int value = Math.max(0, Math.min(100, v));
        ModConfig cfg = ModConfig.get();
        switch (c) {
            case TP -> cfg.volumeTp = value;
            case PRIVATE -> cfg.volumePrivate = value;
            case CLAN -> cfg.volumeClan = value;
            case RAID_INVITE -> cfg.volumeRaidInvite = value;
            case RAID_POST -> cfg.volumeRaidPost = value;
        }
    }

    public static void play(Category c) {
        play(tone(c), volume(c));
    }

    public static void play(Tone tone, int volume) {
        if (tone == Tone.NONE || tone.sound == null || volume <= 0) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> mc.getSoundManager().play(
                PositionedSoundInstance.master(tone.sound.get(), 1f, volume / 100f)));
    }
}
