package dev.mlua.modules.javabridge.service;

import dev.mlua.api.MLuaLuaCoercions;
import dev.mlua.runtime.client.MLuaRenderFrameScope;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

/** Owns Java value wrapping and render-frame scoped reference handling. */
final class MLuaJavaOutputAdapter {
    private final MLuaJavaService service;

    MLuaJavaOutputAdapter(MLuaJavaService service) {
        this.service = service;
    }

    LuaValue wrap(Object value) {
        return wrap(value, Object.class, null);
    }

    LuaValue wrap(Object value, Type declaredType) {
        return wrap(value, declaredType, null);
    }

    LuaValue wrap(Object value, Type declaredType, MLuaRenderFrameScope.View frameScope) {
        if (frameScope == null) frameScope = MLuaRenderFrameScope.currentView();
        if (value == null) return LuaValue.NIL;
        if (value instanceof Optional<?> optional) {
            return optional.isPresent()
                ? wrap(optional.get(), typeArgument(declaredType, 0), frameScope)
                : LuaValue.NIL;
        }
        if (MLuaLuaCoercions.hasAdapter(value.getClass())) {
            try {
                return MLuaLuaCoercions.toLuaValue(value);
            } catch (RuntimeException failure) {
                throw new LuaError("[MLua] Registered Lua adapter could not convert Java "
                    + value.getClass().getName() + " to Lua: " + failure.getMessage());
            }
        }
        if (value instanceof Class<?> clazz) return service.objectProxies().classProxy(clazz);
        if (value instanceof Character character) return LuaValue.valueOf(character.toString());
        if (value instanceof Long longValue) {
            try {
                return MLuaLuaCoercions.toLuaLong(longValue);
            } catch (IllegalArgumentException imprecise) {
                // Preserve exact values LuaJ cannot represent as a double.
                return service.objectProxies().objectProxy(longValue, declaredType);
            }
        }
        if (value instanceof Number number) {
            LuaValue converted = MLuaLuaCoercions.toLuaValue(number);
            return converted.isuserdata()
                ? service.objectProxies().objectProxy(value, declaredType)
                : converted;
        }
        if (value instanceof String || value instanceof Boolean) return CoerceJavaToLua.coerce(value);
        if (frameScope != null) {
            try {
                return LuaValue.userdataOf(frameScope.scopedReference(value));
            } catch (IllegalStateException expired) {
                throw new LuaError("[MLua] " + expired.getMessage());
            }
        }
        return service.objectProxies().objectProxy(value, declaredType);
    }

    static MLuaRenderFrameScope.View frameScope(LuaValue value) {
        if (value == null || !value.isuserdata(MLuaRenderFrameScope.MLuaScopedRenderObject.class)) return null;
        return ((MLuaRenderFrameScope.MLuaScopedRenderObject)
            value.touserdata(MLuaRenderFrameScope.MLuaScopedRenderObject.class)).scopeView();
    }

    static Type typeArgument(Type type, int index) {
        if (type instanceof ParameterizedType parameterized
                && index < parameterized.getActualTypeArguments().length) {
            Type argument = parameterized.getActualTypeArguments()[index];
            if (argument instanceof java.lang.reflect.WildcardType wildcard
                    && wildcard.getUpperBounds().length > 0) {
                return wildcard.getUpperBounds()[0];
            }
            return argument;
        }
        return Object.class;
    }
}
