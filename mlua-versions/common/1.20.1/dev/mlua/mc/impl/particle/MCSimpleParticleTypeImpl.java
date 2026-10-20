package dev.mlua.mc.impl.particle;

import dev.mlua.mcapi.core.MCResourceLocation;
import dev.mlua.mcapi.particle.MCSimpleParticleType;
import net.minecraft.core.particles.SimpleParticleType;

public class MCSimpleParticleTypeImpl extends MCParticleTypeImpl implements MCSimpleParticleType {
    private final SimpleParticleType inner;

    public MCSimpleParticleTypeImpl(SimpleParticleType inner) {
        super(inner);
        this.inner = inner;
    }

    public static MCSimpleParticleTypeImpl fromReal(SimpleParticleType inner) {
        return inner == null ? null : new MCSimpleParticleTypeImpl(inner);
    }

    @Override
    public boolean alwaysUpdate() {
        return inner.getOverrideLimiter();
    }
}
