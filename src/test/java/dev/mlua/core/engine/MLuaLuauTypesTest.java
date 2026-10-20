package dev.mlua.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

final class MLuaLuauTypesTest {
    @Test
    void sharedCoreLibraryInstallsTypedColorVectorAndUdimValues() {
        Globals globals = JsePlatform.standardGlobals();
        MLuaLuauTypes.install(globals);

        LuaValue vector4 = globals.get("vectors").get("vec4")
            .invoke(LuaValue.varargsOf(new LuaValue[]{LuaValue.valueOf(1), LuaValue.valueOf(2),
                LuaValue.valueOf(3), LuaValue.valueOf(4)})).arg1();
        assertEquals("Vec4", MLuaLuauTypes.typeName(vector4).tojstring());

        LuaValue color = globals.get("Color3").get("fromHex").call(LuaValue.valueOf("#336699"));
        assertEquals("Color3", MLuaLuauTypes.typeName(color).tojstring());
        assertEquals(0xFF336699, MLuaLuauTypes.color3Argb(color));

        LuaValue offset = globals.get("UDim").get("new")
            .call(LuaValue.valueOf(0.5), LuaValue.valueOf(6));
        assertEquals(0.5, offset.get("Scale").todouble());
        assertEquals(6, offset.get("Offset").toint());
    }

    @Test
    void udimOffsetsRejectNonIntegerValues() {
        Globals globals = JsePlatform.standardGlobals();
        MLuaLuauTypes.install(globals);

        assertThrows(LuaError.class, () -> globals.get("UDim").get("new")
            .call(LuaValue.valueOf(0.5), LuaValue.valueOf(1.25)));
    }
}
