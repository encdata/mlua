package dev.mlua.api;

import dev.mlua.runtime.client.MLuaRenderFrameScope;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaLuaCoercionsTest {
    @Test
    void preservesNestedJavaNullAndArrayPositions() {
        Object[] source = {"before", null, "after"};
        LuaValue lua = MLuaLuaCoercions.toLuaValue(source);

        assertEquals("before", lua.get(1).tojstring());
        assertTrue(MLuaLuaCoercions.isJavaNull(lua.get(2)));
        assertEquals("after", lua.get(3).tojstring());
        assertArrayEquals(source, (Object[]) MLuaLuaCoercions.javaValue(lua, Object[].class));
        assertNull(MLuaLuaCoercions.javaValue(LuaValue.NIL, String.class));
        assertEquals(Optional.empty(), MLuaLuaCoercions.javaValue(LuaValue.NIL, Optional.class));
    }

    @Test
    void convertsMapsAndRejectsSparseOrInexactValues() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("present", "value");
        source.put("explicitNull", null);
        LuaValue lua = MLuaLuaCoercions.toLuaValue(source);

        assertEquals("value", lua.get("present").tojstring());
        assertTrue(MLuaLuaCoercions.isJavaNull(lua.get("explicitNull")));
        Map<?, ?> converted = (Map<?, ?>) MLuaLuaCoercions.javaValue(lua, Object.class);
        assertEquals("value", converted.get("present"));
        assertTrue(converted.containsKey("explicitNull"));
        assertNull(converted.get("explicitNull"));

        LuaTable sparse = new LuaTable();
        sparse.set(1, "one");
        sparse.set(3, "three");
        assertThrows(IllegalArgumentException.class,
            () -> MLuaLuaCoercions.javaValue(sparse, String[].class));
        assertThrows(IllegalArgumentException.class,
            () -> MLuaLuaCoercions.toLuaLong(9_007_199_254_740_992L));
        assertEquals(9_007_199_254_740_991L,
            MLuaLuaCoercions.toLuaLong(9_007_199_254_740_991L).tolong());
    }

    @Test
    void infersOnlyDenseNumericTablesAsJavaSequences() {
        Object empty = MLuaLuaCoercions.javaValue(new LuaTable(), Object.class);
        assertTrue(empty instanceof java.util.List<?>);
        assertTrue(((java.util.List<?>) empty).isEmpty());

        LuaTable mixed = new LuaTable();
        mixed.set(1, "first");
        mixed.set("name", "mixed");
        Object converted = MLuaLuaCoercions.javaValue(mixed, Object.class);
        assertTrue(converted instanceof Map<?, ?>);
        assertEquals("first", ((Map<?, ?>) converted).get(1.0));
        assertEquals("mixed", ((Map<?, ?>) converted).get("name"));
    }

    @Test
    void rejectsCyclesAndAppliesLimitsBeforeConverting() {
        LuaTable cyclic = new LuaTable();
        cyclic.set("self", cyclic);
        assertThrows(IllegalArgumentException.class, () -> MLuaLuaCoercions.validateLuaValue(cyclic));

        LuaTable tooDeep = new LuaTable();
        LuaTable cursor = tooDeep;
        for (int i = 0; i <= MLuaLuaCoercions.MAX_CONVERSION_DEPTH; i++) {
            LuaTable child = new LuaTable();
            cursor.set("child", child);
            cursor = child;
        }
        assertThrows(IllegalArgumentException.class, () -> MLuaLuaCoercions.validateLuaValue(tooDeep));
    }

    @Test
    void addonAdaptersRoundTripOnlyTheirDeclaredJavaType() throws Exception {
        var registration = MLuaLuaCoercions.registerAdapter(new MLuaLuaCoercions.Adapter<AdapterValue>() {
            @Override public Class<AdapterValue> type() { return AdapterValue.class; }
            @Override public AdapterValue fromLua(LuaValue value) {
                return new AdapterValue(value.checkjstring());
            }
            @Override public LuaValue toLua(AdapterValue value) {
                return LuaValue.valueOf(value.value());
            }
        });
        try (registration) {
            AdapterValue value = (AdapterValue) MLuaLuaCoercions.javaValue(
                LuaValue.valueOf("adapter"), AdapterValue.class);
            assertEquals("adapter", value.value());
            assertEquals("adapter", MLuaLuaCoercions.toLuaValue(value).tojstring());
            assertThrows(IllegalArgumentException.class,
                () -> MLuaLuaCoercions.javaValue(LuaValue.valueOf("adapter"), StringBuilder.class));
        }
    }

    @Test
    void unwrapsScopedRenderReferencesForEveryJavaConversionEntryPoint() {
        MLuaRenderFrameScope scope = new MLuaRenderFrameScope();
        NativeRenderObject nativeObject = new NativeRenderObject();
        LuaValue renderObject = LuaValue.userdataOf(scope.view().scopedReference(nativeObject));

        assertSame(nativeObject, MLuaLuaCoercions.javaValue(renderObject));
        assertSame(nativeObject, MLuaLuaCoercions.javaValue(renderObject, Object.class));
        assertSame(nativeObject, MLuaLuaCoercions.javaValue(renderObject, NativeRenderObject.class));

        LuaTable nestedList = new LuaTable();
        nestedList.set(1, renderObject);
        assertSame(nativeObject, ((java.util.List<?>) MLuaLuaCoercions.javaValue(nestedList)).get(0));
        LuaTable nestedMap = new LuaTable();
        nestedMap.set("renderObject", renderObject);
        assertSame(nativeObject,
            ((Map<?, ?>) MLuaLuaCoercions.javaValue(nestedMap, Object.class)).get("renderObject"));

        LuaValue javaResult = MLuaLuaCoercions.toLuaValue(scope.view().scopedReference(nativeObject));
        assertSame(nativeObject, MLuaLuaCoercions.javaValue(javaResult, NativeRenderObject.class));
        Map<String, Object> resultMap = Map.of("renderObject", scope.view().scopedReference(nativeObject));
        LuaValue nestedJavaResult = MLuaLuaCoercions.toLuaValue(resultMap);
        assertSame(nativeObject, ((Map<?, ?>) MLuaLuaCoercions.javaValue(nestedJavaResult, Object.class))
            .get("renderObject"));

        scope.close();
        assertThrows(IllegalStateException.class, () -> MLuaLuaCoercions.javaValue(renderObject));
        assertThrows(IllegalStateException.class,
            () -> MLuaLuaCoercions.javaValue(renderObject, NativeRenderObject.class));
        assertThrows(IllegalStateException.class, () -> MLuaLuaCoercions.javaValue(nestedList));
        assertThrows(IllegalStateException.class,
            () -> MLuaLuaCoercions.javaValue(nestedMap, Object.class));
        assertThrows(IllegalStateException.class,
            () -> MLuaLuaCoercions.javaValue(javaResult, NativeRenderObject.class));
        assertThrows(IllegalStateException.class,
            () -> MLuaLuaCoercions.javaValue(nestedJavaResult, Object.class));
    }

    private record AdapterValue(String value) { }
    private static final class NativeRenderObject { }
}
