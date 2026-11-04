package dev.mlua.mc.impl.effect;

import dev.mlua.mcapi.effect.MCMobEffectCategory;
import net.minecraft.world.effect.MobEffectCategory;

public final class MCMobEffectCategoryImpl {
    private MCMobEffectCategoryImpl() {}

    public static MCMobEffectCategory fromReal(MobEffectCategory category) {
        if (category == null) return MCMobEffectCategory.NEUTRAL;
        return switch (category) {
            case BENEFICIAL -> MCMobEffectCategory.BENEFICIAL;
            case HARMFUL -> MCMobEffectCategory.HARMFUL;
            case NEUTRAL -> MCMobEffectCategory.NEUTRAL;
        };
    }

    public static MobEffectCategory toReal(MCMobEffectCategory category) {
        if (category == null) return MobEffectCategory.NEUTRAL;
        return switch (category) {
            case BENEFICIAL -> MobEffectCategory.BENEFICIAL;
            case HARMFUL -> MobEffectCategory.HARMFUL;
            case NEUTRAL -> MobEffectCategory.NEUTRAL;
        };
    }
}
