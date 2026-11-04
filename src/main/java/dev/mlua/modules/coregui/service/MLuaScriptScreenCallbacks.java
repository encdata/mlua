package dev.mlua.modules.coregui.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.mlua.core.MLuaLogger;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Owns Lua class lifecycle and input callback dispatch for one scripted screen. */
final class MLuaScriptScreenCallbacks {
    private final MLuaScriptScreen screen;
    private final Set<String> failedInputMethods = ConcurrentHashMap.newKeySet();
    private final Set<String> failedLifecycleMethods = ConcurrentHashMap.newKeySet();

    MLuaScriptScreenCallbacks(MLuaScriptScreen screen) {
        this.screen = screen;
    }

    boolean isPauseScreen() {
        LuaTable instance = screen.luaInstance();
        if (screen.isDisposed() || instance == null || failedInputMethods.contains("IsPauseScreen")) return true;
        LuaValue method = instance.get("IsPauseScreen");
        if (!method.isfunction()) return true;
        try {
            LuaValue result = screen.invokeLua(method, LuaValue.varargsOf(new LuaValue[]{instance})).arg1();
            return optionalBooleanResult(result, "IsPauseScreen", true);
        } catch (RuntimeException failure) {
            failedInputMethods.add("IsPauseScreen");
            MLuaLogger.LOGGER.error("Lua screen method 'IsPauseScreen' failed; using Minecraft's pause-screen default", failure);
            return true;
        }
    }

    Boolean invokeInputResult(String methodName, LuaValue... parameters) {
        LuaTable instance = screen.luaInstance();
        if (screen.isDisposed() || instance == null || failedInputMethods.contains(methodName)) return null;
        LuaValue method = instance.get(methodName);
        if (!method.isfunction()) return null;
        LuaValue[] arguments = new LuaValue[parameters.length + 1];
        arguments[0] = instance;
        System.arraycopy(parameters, 0, arguments, 1, parameters.length);
        try {
            LuaValue result = screen.invokeLua(method, LuaValue.varargsOf(arguments)).arg1();
            return result.isnil() ? null : optionalBooleanResult(result, methodName, false);
        } catch (RuntimeException failure) {
            failedInputMethods.add(methodName);
            MLuaLogger.LOGGER.error("Lua screen input method '{}' failed", methodName, failure);
            return null;
        }
    }

    boolean invokeInputMethod(String methodName, LuaValue... parameters) {
        return Boolean.TRUE.equals(invokeInputResult(methodName, parameters));
    }

    void invokeLifecycle(String methodName, LuaValue... parameters) {
        LuaTable instance = screen.luaInstance();
        if (instance == null || failedLifecycleMethods.contains(methodName)) return;
        LuaValue method = instance.get(methodName);
        if (!method.isfunction()) return;
        LuaValue[] arguments = new LuaValue[parameters.length + 1];
        arguments[0] = instance;
        System.arraycopy(parameters, 0, arguments, 1, parameters.length);
        try {
            screen.invokeLua(method, LuaValue.varargsOf(arguments));
        } catch (RuntimeException failure) {
            failedLifecycleMethods.add(methodName);
            MLuaLogger.LOGGER.error("Lua screen lifecycle method '{}' failed", methodName, failure);
        }
    }

    void clear() {
        failedInputMethods.clear();
        failedLifecycleMethods.clear();
    }

    private static boolean optionalBooleanResult(LuaValue result, String methodName, boolean nilValue) {
        if (result.isnil()) return nilValue;
        if (!result.isboolean()) {
            throw new LuaError("[MLua] Screen:" + methodName + " must return true, false, or nil.");
        }
        return result.toboolean();
    }
}
