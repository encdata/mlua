package dev.mlua.mc.impl.world;

import dev.mlua.mcapi.world.MCInteractionResult;
import dev.mlua.mcapi.world.MCInteractionResultHolder;
import net.minecraft.world.InteractionResultHolder;

public class MCInteractionResultHolderImpl<T> implements MCInteractionResultHolder<T> {
    private final InteractionResultHolder<T> inner;

    public MCInteractionResultHolderImpl(InteractionResultHolder<T> inner) {
        this.inner = inner;
    }

    public InteractionResultHolder<T> inner() {
        return inner;
    }

    @Override
    public MCInteractionResult getResult() {
        return MCInteractionResultImpl.fromReal(inner.getResult());
    }

    @Override
    public T getObject() {
        return inner.getObject();
    }
}
