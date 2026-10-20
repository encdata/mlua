package dev.mlua.mc.impl.particle;

import dev.mlua.mcapi.core.MCResourceLocation;
import dev.mlua.mcapi.particle.MCParticleType;
import dev.mlua.mc.impl.core.MCResourceLocationImpl;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.registries.ForgeRegistries;

public class MCParticleTypeImpl implements MCParticleType {
    private final ParticleType<?> inner;

    public MCParticleTypeImpl(ParticleType<?> inner) {
        this.inner = inner;
    }

    public ParticleType<?> inner() {
        return inner;
    }

    @Override
    public MCResourceLocation getRegistryName() {
        var key = ForgeRegistries.PARTICLE_TYPES.getKey(inner);
        return key == null ? null : MCResourceLocationImpl.fromReal(key);
    }
}
