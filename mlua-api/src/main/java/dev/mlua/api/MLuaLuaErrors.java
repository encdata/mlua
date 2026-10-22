package dev.mlua.api;

import java.util.Objects;
import java.util.function.Function;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

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

    /** Wraps a Java-backed Lua callback with the shared MLua exception policy. */
    public static VarArgFunction operation(String operation, Function<Varargs, LuaValue> body) {
        return operation(operation, body, failure -> javaFailure(operation, failure));
    }

    /** Wraps a callback while allowing the owning service to retain specialized error reporting. */
    public static VarArgFunction operation(String operation, Function<Varargs, LuaValue> body,
                                            Function<Exception, LuaError> failureMapper) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(failureMapper, "failureMapper");
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    return body.apply(args);
                } catch (LuaError failure) {
                    throw failure;
                } catch (Exception failure) {
                    throw Objects.requireNonNull(failureMapper.apply(failure), "failureMapper result");
                }
            }
        };
    }

    /** Same exception boundary for callbacks that return multiple Lua values. */
    public static VarArgFunction varargsOperation(String operation, Function<Varargs, Varargs> body) {
        Objects.requireNonNull(body, "body");
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    return body.apply(args);
                } catch (LuaError failure) {
                    throw failure;
                } catch (Exception failure) {
                    throw javaFailure(operation, failure);
                }
            }
        };
    }

}
