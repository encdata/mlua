package dev.mlua.mc.impl.sound;

import dev.mlua.mcapi.sound.MCSoundSource;
import net.minecraft.sounds.SoundSource;

public final class MCSoundSourceImpl {
    private MCSoundSourceImpl() {}

    public static MCSoundSource fromReal(SoundSource source) {
        if (source == null) return MCSoundSource.MASTER;
        return switch (source) {
            case MASTER -> MCSoundSource.MASTER;
            case MUSIC -> MCSoundSource.MUSIC;
            case RECORDS -> MCSoundSource.RECORDS;
            case WEATHER -> MCSoundSource.WEATHER;
            case BLOCKS -> MCSoundSource.BLOCKS;
            case HOSTILE -> MCSoundSource.HOSTILE;
            case NEUTRAL -> MCSoundSource.NEUTRAL;
            case PLAYERS -> MCSoundSource.PLAYERS;
            case AMBIENT -> MCSoundSource.AMBIENT;
            case VOICE -> MCSoundSource.VOICE;
        };
    }

    public static SoundSource toReal(MCSoundSource source) {
        if (source == null) return SoundSource.MASTER;
        return switch (source) {
            case MASTER -> SoundSource.MASTER;
            case MUSIC -> SoundSource.MUSIC;
            case RECORDS -> SoundSource.RECORDS;
            case WEATHER -> SoundSource.WEATHER;
            case BLOCKS -> SoundSource.BLOCKS;
            case HOSTILE -> SoundSource.HOSTILE;
            case NEUTRAL -> SoundSource.NEUTRAL;
            case PLAYERS -> SoundSource.PLAYERS;
            case AMBIENT -> SoundSource.AMBIENT;
            case VOICE -> SoundSource.VOICE;
        };
    }
}
