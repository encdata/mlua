package dev.mlua.modules.javabridge.service;

import java.util.Objects;
import java.util.function.Supplier;
import org.luaj.vm2.LuaValue;

/** Defers generic conversion of a Lua table until overload resolution selects a Java target. */
final class MLuaJavaTableArgument {
    private final LuaValue value;
    private final Supplier<Object> defaultConversion;
    private boolean defaultResolved;
    private Object defaultValue;

    MLuaJavaTableArgument(LuaValue value, Supplier<Object> defaultConversion) {
        this.value = Objects.requireNonNull(value, "value");
        this.defaultConversion = Objects.requireNonNull(defaultConversion, "defaultConversion");
    }

    LuaValue value() { return this.value; }

    Object defaultValue() {
        if (!this.defaultResolved) {
            this.defaultValue = this.defaultConversion.get();
            this.defaultResolved = true;
        }
        return this.defaultValue;
    }
}
