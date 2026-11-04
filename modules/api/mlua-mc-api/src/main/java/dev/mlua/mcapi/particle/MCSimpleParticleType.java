package dev.mlua.mcapi.particle;

import dev.mlua.mcapi.core.MCResourceLocation;

public interface MCSimpleParticleType extends MCParticleType {

    static MCSimpleParticleType create(boolean overrideLimiter) {
        return new MCSimpleParticleType() {
            private final boolean override = overrideLimiter;

            @Override
            public MCResourceLocation getRegistryName() {
                return null;
            }

            @Override
            public boolean alwaysUpdate() {
                return override;
            }
        };
    }

    boolean alwaysUpdate();

}
