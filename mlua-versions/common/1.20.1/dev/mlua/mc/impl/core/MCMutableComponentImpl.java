package dev.mlua.mc.impl.core;

import dev.mlua.mcapi.core.MCMutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class MCMutableComponentImpl extends MCComponentImpl implements MCMutableComponent {
    private MutableComponent inner;

    public MCMutableComponentImpl(MutableComponent inner) {
        super(inner);
        this.inner = inner;
    }

    public static MCMutableComponentImpl fromReal(MutableComponent inner) {
        return inner == null ? null : new MCMutableComponentImpl(inner);
    }

    @Override
    public void setString(String text) {
        this.inner = Component.literal(text);
    }

    @Override
    public MutableComponent inner() {
        return inner;
    }

    @Override
    public String getString() {
        return inner.getString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MCMutableComponentImpl that)) return false;
        return inner.equals(that.inner);
    }

    @Override
    public int hashCode() {
        return inner.hashCode();
    }
}
