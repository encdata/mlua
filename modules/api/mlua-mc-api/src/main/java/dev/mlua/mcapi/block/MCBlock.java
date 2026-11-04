package dev.mlua.mcapi.block;

import dev.mlua.mcapi.core.MCResourceLocation;

public interface MCBlock {
    MCBlockState defaultBlockState();
    String getDescriptionId();
    MCResourceLocation getRegistryName();
    MCSoundType getSoundType();
    boolean isEnabledForEnchantment();
    float getExplosionResistance();
}
