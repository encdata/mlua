package dev.mlua.runtime.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaConfigInputFilterTest {
    @Test
    void preservesPartialIntegerAndDecimalEditingForms() {
        for (String text : new String[]{"", "-", "0", "-001"}) {
            assertTrue(MLuaConfigInputFilter.accepts("int", text));
            assertTrue(MLuaConfigInputFilter.accepts("long", text));
        }
        for (String text : new String[]{"", "-", ".", "-.", ".5", "5.", "-5.2"}) {
            assertTrue(MLuaConfigInputFilter.accepts("double", text));
        }
    }

    @Test
    void rejectsCharactersAndFormatsThatNumericEditorsCannotAccept() {
        for (String text : new String[]{"+1", "1.0", " 2", "１２"}) {
            assertFalse(MLuaConfigInputFilter.accepts("int", text));
        }
        for (String text : new String[]{"+1", "1..", "1e4", " 1"}) {
            assertFalse(MLuaConfigInputFilter.accepts("double", text));
        }
        assertFalse(MLuaConfigInputFilter.accepts(null, "1"));
        assertFalse(MLuaConfigInputFilter.accepts("unknown", "1"));
        assertTrue(MLuaConfigInputFilter.accepts("enum", null));
    }
}
