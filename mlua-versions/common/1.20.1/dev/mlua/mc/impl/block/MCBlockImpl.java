package dev.mlua.mc.impl.block;

import dev.mlua.mcapi.block.MCBlock;
import dev.mlua.mcapi.block.MCBlockState;
import dev.mlua.mcapi.block.MCSoundType;
import dev.mlua.mcapi.core.MCResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

public class MCBlockImpl implements MCBlock {
    private final Block inner;

    public MCBlockImpl(Block inner) {
        this.inner = inner;
    }

    public static MCBlockImpl fromReal(Block inner) {
        return inner == null ? null : new MCBlockImpl(inner);
    }

    public Block inner() {
        return inner;
    }

    @Override
    public MCBlockState defaultBlockState() {
        return MCBlockStateImpl.fromReal(inner.defaultBlockState());
    }

    @Override
    public String getDescriptionId() {
        return inner.getDescriptionId();
    }

    @Override
    public MCResourceLocation getRegistryName() {
        var key = BuiltInRegistries.BLOCK.getKey(inner);
        return key == null ? null : dev.mlua.mc.impl.core.MCResourceLocationImpl.fromReal(key);
    }

    @Override
    public MCSoundType getSoundType() {
        return MCSoundTypeImpl.fromReal(inner.getSoundType(inner.defaultBlockState()));
    }

    @Override
    public boolean isEnabledForEnchantment() {
        return true;
    }

    @Override
    public float getExplosionResistance() {
        return inner.getExplosionResistance();
    }
}
