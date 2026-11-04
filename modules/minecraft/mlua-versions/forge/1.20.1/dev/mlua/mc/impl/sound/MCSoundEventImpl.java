package dev.mlua.mc.impl.sound;

import dev.mlua.mcapi.core.MCResourceLocation;
import dev.mlua.mcapi.sound.MCSoundEvent;
import dev.mlua.mc.impl.core.MCResourceLocationImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.ForgeRegistries;

public class MCSoundEventImpl implements MCSoundEvent {
    private final SoundEvent inner;

    public MCSoundEventImpl(SoundEvent inner) {
        this.inner = inner;
    }

    public SoundEvent inner() {
        return inner;
    }

    @Override
    public MCResourceLocation getLocation() {
        return MCResourceLocationImpl.fromReal(inner.getLocation());
    }

    public static MCSoundEventImpl fromReal(SoundEvent inner) {
        return inner == null ? null : new MCSoundEventImpl(inner);
    }
}
