package dev.mlua.modules.javabridge.service;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/** Immutable ParameterizedType used when Lua APIs provide a raw Java collection class. */
final class MLuaJavaParameterizedType implements ParameterizedType {
    private final Type rawType;
    private final Type[] actualTypeArguments;

    MLuaJavaParameterizedType(Type rawType, Type... actualTypeArguments) {
        this.rawType = rawType;
        this.actualTypeArguments = actualTypeArguments.clone();
    }

    @Override public Type[] getActualTypeArguments() { return this.actualTypeArguments.clone(); }
    @Override public Type getRawType() { return this.rawType; }
    @Override public Type getOwnerType() { return null; }
}
