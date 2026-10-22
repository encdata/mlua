package dev.mlua.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

final class MLuaServiceBaseTest {
    @Test
    void normalizesNestedServiceFunctionsForColonAndDotCallsOnce() {
        LuaTable service = new LuaTable();
        LuaTable nested = new LuaTable();
        nested.set("Echo", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return args.arg(2);
            }
        });
        service.set("Nested", nested);

        LuaTable normalized = MLuaServiceBase.normalizeServiceCalls(service, "Example");
        LuaValue dotResult = nested.get("Echo").invoke(LuaValue.valueOf("dot")).arg1();
        LuaValue colonResult = nested.get("Echo").invoke(LuaValue.varargsOf(new LuaValue[]{
            nested, LuaValue.valueOf("colon")
        })).arg1();

        assertSame(service, normalized);
        assertEquals("dot", dotResult.checkjstring());
        assertEquals("colon", colonResult.checkjstring());
        assertSame(normalized, MLuaServiceBase.normalizeServiceCalls(normalized, "Example"));
    }
}
