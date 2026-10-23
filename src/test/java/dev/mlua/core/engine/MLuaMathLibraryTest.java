package dev.mlua.core.engine;

import dev.mlua.math.MLuaVector;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MLuaMathLibraryTest {
    @Test
    void vectorLerpPreservesComponentsAndExtrapolation() {
        LuaTable math = new LuaTable();
        MLuaMathLibrary.install(math);

        LuaValue vector2 = math.get("lerp").invoke(LuaValue.varargsOf(new LuaValue[]{
            MLuaVector.vector2(2.0, -4.0), MLuaVector.vector2(6.0, 8.0), LuaValue.valueOf(1.5)
        })).arg1();
        LuaValue vector3 = math.get("lerp").invoke(LuaValue.varargsOf(new LuaValue[]{
            MLuaVector.vector3(-2.0, 0.0, 5.0), MLuaVector.vector3(4.0, 6.0, -1.0), LuaValue.valueOf(0.25)
        })).arg1();

        assertEquals(8.0, vector2.get("X").todouble(), 0.0);
        assertEquals(14.0, vector2.get("Y").todouble(), 0.0);
        assertEquals(-0.5, vector3.get("X").todouble(), 0.0);
        assertEquals(1.5, vector3.get("Y").todouble(), 0.0);
        assertEquals(3.5, vector3.get("Z").todouble(), 0.0);
    }
}
