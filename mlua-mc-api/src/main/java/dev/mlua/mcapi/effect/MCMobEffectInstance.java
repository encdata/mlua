package dev.mlua.mcapi.effect;

public interface MCMobEffectInstance {
    MCMobEffect getEffect();
    int getAmplifier();
    int getDuration();
    boolean isAmbient();
    boolean showParticles();
    boolean showIcon();

    static MCMobEffectInstance of(MCMobEffect effect, int amplifier, int duration) {
        return of(effect, amplifier, duration, false, true, true);
    }

    static MCMobEffectInstance of(MCMobEffect effect, int amplifier, int duration,
                                  boolean ambient, boolean showParticles, boolean showIcon) {
        return new MCMobEffectInstance() {
            @Override
            public MCMobEffect getEffect() {
                return effect;
            }

            @Override
            public int getAmplifier() {
                return amplifier;
            }

            @Override
            public int getDuration() {
                return duration;
            }

            @Override
            public boolean isAmbient() {
                return ambient;
            }

            @Override
            public boolean showParticles() {
                return showParticles;
            }

            @Override
            public boolean showIcon() {
                return showIcon;
            }
        };
    }
}
