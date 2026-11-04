package dev.mlua.modules.coregui.service;

import dev.mlua.common.enums.CoreGuiType;
import dev.mlua.core.engine.MLuaLuauTypes;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.modules.enums.MLuaEnumValue;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import net.minecraft.network.chat.Component;

/** Converts and validates values shared by the public GUI and native-screen APIs. */
final class MLuaGuiValueParser {
    private MLuaGuiValueParser() { }

    static Object nativeText(LuaValue value) {
        if (value.type() == LuaValue.TSTRING) return value.checkjstring();
        try {
            Object component = dev.mlua.api.MLuaLuaCoercions.javaValue(value, Component.class);
            if (component instanceof Component) return component;
        } catch (RuntimeException ignored) { }
        throw new LuaError("[MLua] Expected a string or Java Component value.");
    }

    static int readColor(LuaValue value, String method, String parameter) {
        return MLuaLuaArguments.wholeInt32Bits(value, method, parameter);
    }

    static int optionalColor(LuaValue value, int fallback, String method, String parameter) {
        return value.isnil() ? fallback : readColor(value, method, parameter);
    }

    static LuaTable requireScreenUDim2(LuaValue value, String property) {
        if (!MLuaLuauTypes.isUDim2(value)) {
            throw new LuaError("[MLua] Screen " + property + " requires a UDim2 value.");
        }
        return value.checktable();
    }

    static int screenCoordinate(LuaTable dimensions, String axis, int extent) {
        LuaValue dimension = dimensions.get(axis);
        return (int) Math.round(dimension.get("Scale").todouble() * extent + dimension.get("Offset").toint());
    }

    static CoreGuiType requireCoreGuiType(LuaValue value, String method) {
        CoreGuiType type = MLuaEnumValue.toJava(value, CoreGuiType.class);
        if (type == null) throw new LuaError("[MLua] Gui:" + method + " expects an Enum.CoreGuiType value");
        return type;
    }

    static void requireNativeSelector(LuaTable selector) {
        MLuaGuiNativeControlRegistry.requireSelector(selector);
    }
}
