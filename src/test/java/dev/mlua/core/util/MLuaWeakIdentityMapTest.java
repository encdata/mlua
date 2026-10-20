package dev.mlua.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class MLuaWeakIdentityMapTest {
    @Test
    void distinguishesKeysByIdentityEvenWhenTheyCompareEqual() {
        MLuaWeakIdentityMap<EqualKey, String> values = new MLuaWeakIdentityMap<>();
        EqualKey first = new EqualKey("same");
        EqualKey second = new EqualKey("same");

        values.put(first, "first");
        values.put(second, "second");

        assertEquals("first", values.get(first));
        assertEquals("second", values.get(second));
        assertEquals(2, values.size());
        assertEquals("first", values.remove(first));
        assertNull(values.get(first));
        assertEquals("second", values.get(second));
    }

    private record EqualKey(String value) { }
}
