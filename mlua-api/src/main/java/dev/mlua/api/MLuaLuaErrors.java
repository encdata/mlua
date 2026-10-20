package dev.mlua.api;

import java.util.Objects;
import org.luaj.vm2.LuaError;

/** Builds Lua-facing errors for Java failures without discarding their causes. */
public final class MLuaLuaErrors {
    private MLuaLuaErrors() { }

    public static LuaError javaFailure(String operation, Throwable cause) {
        Objects.requireNonNull(cause, "cause");
        String name = operation == null || operation.isBlank() ? "Java operation" : operation;
        String detail = cause.getMessage();
        String message = "[MLua] " + name + " failed"
            + (detail == null || detail.isBlank() ? " (" + cause.getClass().getSimpleName() + ")" : ": " + detail);
        return new LuaError(new RuntimeException(message, cause));
    }
}
