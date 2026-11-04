package dev.mlua.mc.impl.block;

import dev.mlua.mcapi.block.MCBlock;
import dev.mlua.mcapi.block.MCBlockState;
import dev.mlua.mcapi.block.MCProperty;
import dev.mlua.mcapi.block.MCSoundType;
import net.minecraft.world.level.block.state.BlockState;

public class MCBlockStateImpl implements MCBlockState {
    private final BlockState inner;

    public MCBlockStateImpl(BlockState inner) {
        this.inner = inner;
    }

    public static MCBlockStateImpl fromReal(BlockState inner) {
        return inner == null ? null : new MCBlockStateImpl(inner);
    }

    public BlockState inner() {
        return inner;
    }

    @Override
    public MCBlock getBlock() {
        return MCBlockImpl.fromReal(inner.getBlock());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Comparable<T>> T getValue(MCProperty<T> property) {
        var prop = ((MCPropertyImpl<T>) property).inner();
        return (T) inner.getValue(prop);
    }

    @Override
    public <T extends Comparable<T>> MCBlockState setValue(MCProperty<T> property, T value) {
        var prop = ((MCPropertyImpl<T>) property).inner();
        return MCBlockStateImpl.fromReal(inner.setValue(prop, value));
    }

    @Override
    public boolean isAir() {
        return inner.isAir();
    }

    @Override
    public boolean isSolid() {
        return inner.isSolid();
    }

    @Override
    public MCSoundType getSoundType() {
        return MCSoundTypeImpl.fromReal(inner.getSoundType());
    }

    @Override
    public boolean hasProperty(MCProperty<?> property) {
        return inner.hasProperty(((MCPropertyImpl<?>) property).inner());
    }
}
