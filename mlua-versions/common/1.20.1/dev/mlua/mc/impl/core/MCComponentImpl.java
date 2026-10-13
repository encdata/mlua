package dev.mlua.mc.impl.core;

import dev.mlua.mcapi.core.MCComponent;
import net.minecraft.network.chat.Component;

public class MCComponentImpl implements MCComponent {
    private final Component inner;

    public MCComponentImpl(Component inner) {
        this.inner = inner;
    }

    public static MCComponentImpl fromReal(Component inner) {
        return inner == null ? null : new MCComponentImpl(inner);
    }

    public static MCMutableComponentImpl literal(String text) {
        return new MCMutableComponentImpl(Component.literal(text));
    }

    public Component inner() {
        return inner;
    }

    @Override
    public String getString() {
        return inner.getString();
    }

    @Override
    public String toString() {
        return inner.getString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MCComponentImpl that)) return false;
        return inner.equals(that.inner);
    }

    @Override
    public int hashCode() {
        return inner.hashCode();
    }
}
