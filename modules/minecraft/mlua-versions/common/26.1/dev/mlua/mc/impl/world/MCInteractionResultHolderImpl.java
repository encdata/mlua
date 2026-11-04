package dev.mlua.mc.impl.world;

import dev.mlua.mcapi.world.MCInteractionResult;
import dev.mlua.mcapi.world.MCInteractionResultHolder;

public class MCInteractionResultHolderImpl<T> implements MCInteractionResultHolder<T> {
    private final MCInteractionResult result;
    private final T object;
    public MCInteractionResultHolderImpl(T object, MCInteractionResult result) { this.object = object; this.result = result; }
    public static <T> MCInteractionResultHolderImpl<T> pass(T object) { return new MCInteractionResultHolderImpl<>(object, MCInteractionResult.PASS); }
    @Override public MCInteractionResult getResult() { return result; }
    @Override public T getObject() { return object; }
}
