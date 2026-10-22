package dev.mlua.api;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaLuaErrorsTest {
    @Test
    void operationReturnsLuaResultsAndPreservesLuaErrors() {
        var callback = MLuaLuaErrors.operation("Test operation", args -> LuaValue.valueOf(args.arg1().tojstring()));
        assertEquals("ok", callback.call(LuaValue.valueOf("ok")).tojstring());

        LuaError expected = new LuaError("Lua validation failed");
        LuaError actual = assertThrows(LuaError.class,
            () -> MLuaLuaErrors.operation("Test operation", args -> { throw expected; }).call());
        assertSame(expected, actual);
    }

    @Test
    void operationConvertsJavaFailuresToContextualLuaErrors() {
        LuaError failure = assertThrows(LuaError.class,
            () -> MLuaLuaErrors.operation("Inventory:GetSlots", args -> {
                throw new IllegalStateException("inventory unavailable");
            }).call());

        assertTrue(failure.getMessage().contains("Inventory:GetSlots failed"));
        assertTrue(failure.getMessage().contains("inventory unavailable"));
    }

    @Test
    void operationAllowsServiceSpecificErrorMapping() {
        LuaError expected = new LuaError("specialized failure");
        LuaError actual = assertThrows(LuaError.class,
            () -> MLuaLuaErrors.operation("RenderObject:SetSize", args -> {
                throw new IllegalArgumentException("invalid size");
            }, failure -> expected).call());

        assertSame(expected, actual);
    }

    @Test
    void varargsOperationPreservesMultipleReturnValues() {
        var callback = MLuaLuaErrors.varargsOperation("World service call", args ->
            LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf("world"), LuaValue.valueOf(42) }));

        var result = callback.invoke(LuaValue.NONE);
        assertEquals("world", result.arg1().tojstring());
        assertEquals(42, result.arg(2).toint());
    }
}
