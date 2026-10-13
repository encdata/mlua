package dev.mlua.mc.impl.sound;
import dev.mlua.mcapi.sound.MCSoundSource;
import net.minecraft.sounds.SoundSource;
public final class MCSoundSourceImpl {
    private MCSoundSourceImpl() {}
    public static MCSoundSource fromReal(SoundSource source) { if (source == null) return MCSoundSource.MASTER; return switch (source) { case MASTER -> MCSoundSource.MASTER; case MUSIC -> MCSoundSource.MUSIC; case RECORDS -> MCSoundSource.RECORDS; case WEATHER -> MCSoundSource.WEATHER; case BLOCKS -> MCSoundSource.BLOCKS; case HOSTILE -> MCSoundSource.HOSTILE; case NEUTRAL -> MCSoundSource.NEUTRAL; case PLAYERS -> MCSoundSource.PLAYERS; case AMBIENT -> MCSoundSource.AMBIENT; case VOICE -> MCSoundSource.VOICE; default -> MCSoundSource.MASTER; }; }
    public static SoundSource toReal(MCSoundSource source) { return SoundSource.MASTER; }
}
