package dev.mlua.minecraft.binding;

import dev.mlua.modules.coregui.service.MLuaGuiService;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaNativeControlConversionTest {
    @Test
    void convertsOnlyFiniteNumbersWithinTheTargetRange() {
        assertEquals(12, MLuaNativeControl.fromLuaValue(LuaValue.valueOf(12.0D), int.class));
        assertEquals(Long.MIN_VALUE,
            MLuaNativeControl.fromLuaValue(LuaValue.valueOf((double) Long.MIN_VALUE), long.class));
        assertEquals(1.25F, MLuaNativeControl.fromLuaValue(LuaValue.valueOf(1.25D), float.class));
        assertEquals(1.25D, MLuaNativeControl.fromLuaValue(LuaValue.valueOf(1.25D), double.class));
    }

    @Test
    void rejectsFractionalOutOfRangeAndNonFiniteNumbers() {
        assertThrows(IllegalArgumentException.class,
            () -> MLuaNativeControl.fromLuaValue(LuaValue.valueOf("12"), int.class));
        assertThrows(IllegalArgumentException.class,
            () -> MLuaNativeControl.fromLuaValue(LuaValue.valueOf(12), String.class));
        assertThrows(IllegalArgumentException.class,
            () -> MLuaNativeControl.fromLuaValue(LuaValue.valueOf(1.5D), int.class));
        assertThrows(IllegalArgumentException.class,
            () -> MLuaNativeControl.fromLuaValue(LuaValue.valueOf((double) Integer.MAX_VALUE + 1.0D), int.class));
        assertThrows(IllegalArgumentException.class,
            () -> MLuaNativeControl.fromLuaValue(LuaValue.valueOf(0x1.0p63), long.class));
        assertThrows(IllegalArgumentException.class,
            () -> MLuaNativeControl.fromLuaValue(LuaValue.valueOf(Double.NaN), double.class));
        assertThrows(IllegalArgumentException.class,
            () -> MLuaNativeControl.fromLuaValue(LuaValue.valueOf(Double.POSITIVE_INFINITY), float.class));
        assertThrows(IllegalArgumentException.class,
            () -> MLuaNativeControl.fromLuaValue(LuaValue.valueOf(Double.MAX_VALUE), float.class));
        assertThrows(IllegalArgumentException.class,
            () -> MLuaNativeControl.fromLuaValue(LuaValue.valueOf(Double.MIN_VALUE), float.class));
    }

    @Test
    void mapsNilOnlyToJavaReferenceParameters() {
        assertNull(MLuaNativeControl.fromLuaValue(LuaValue.NIL, String.class));
        assertNull(MLuaNativeControl.convertArgument(LuaValue.NIL, String.class, true).value());
        assertThrows(IllegalArgumentException.class,
            () -> MLuaNativeControl.fromLuaValue(LuaValue.NIL, int.class));
        assertThrows(IllegalArgumentException.class,
            () -> MLuaNativeControl.convertArgument(LuaValue.NIL, boolean.class, true));
        assertThrows(IllegalArgumentException.class,
            () -> MLuaNativeControl.convertArgument(LuaValue.NIL, String.class, false));
    }

    @Test
    void ranksNumericOverloadsByExactPrimitiveAndPrecision() {
        LuaValue whole = LuaValue.valueOf(4.0D);
        assertTrue(MLuaNativeControl.convertArgument(whole, int.class, true).score()
            < MLuaNativeControl.convertArgument(whole, Integer.class, true).score());
        assertTrue(MLuaNativeControl.convertArgument(whole, Integer.class, true).score()
            < MLuaNativeControl.convertArgument(whole, long.class, true).score());

        LuaValue fraction = LuaValue.valueOf(4.5D);
        assertTrue(MLuaNativeControl.convertArgument(fraction, double.class, true).score()
            < MLuaNativeControl.convertArgument(fraction, float.class, true).score());
        assertTrue(MLuaNativeControl.convertArgument(fraction, Double.class, true).score()
            > MLuaNativeControl.convertArgument(fraction, double.class, true).score());
    }

    @Test
    void resolvesNilOverloadsByReferenceSpecificityAndKeepsUnrelatedTypesAmbiguous() throws Exception {
        Method objectMethod = NilOverloadWidget.class.getMethod("accept", Object.class);
        Method stringMethod = NilOverloadWidget.class.getMethod("accept", String.class);
        assertEquals(stringMethod, MLuaNativeControl.mostSpecificMethod(List.of(objectMethod, stringMethod)));

        Method firstEnum = AmbiguousEnumWidget.class.getMethod("setMode", FirstMode.class);
        Method secondEnum = AmbiguousEnumWidget.class.getMethod("setMode", SecondMode.class);
        assertNull(MLuaNativeControl.mostSpecificMethod(List.of(firstEnum, secondEnum)));
    }

    @Test
    void selectsTheMostSpecificNumericSetterAndRejectsTies() {
        NumericWidget widget = new NumericWidget();
        MLuaNativeControl.setWidgetProperty(widget, "Amount", LuaValue.valueOf(4.0D));
        assertEquals("int", widget.selected);
        MLuaNativeControl.setWidgetProperty(widget, "Amount", LuaValue.valueOf(4.5D));
        assertEquals("double", widget.selected);

        LuaError ambiguity = assertThrows(LuaError.class,
            () -> MLuaNativeControl.setWidgetProperty(new AmbiguousEnumWidget(), "Mode", LuaValue.valueOf("OPEN")));
        assertTrue(ambiguity.getMessage().contains("ambiguous setters"));
    }

    @Test
    void rejectsInvalidNativeControlValuesBeforeResolvingTheScreen() {
        LuaError visibleError = assertThrows(LuaError.class,
            () -> MLuaGuiService.setNativeControlProperty(null, "Visible", LuaValue.valueOf("true")));
        assertTrue(visibleError.getMessage().contains("Visible expects a boolean"));
        LuaError textError = assertThrows(LuaError.class,
            () -> MLuaGuiService.setNativeControlProperty(null, "Text", LuaValue.valueOf(42)));
        assertTrue(textError.getMessage().contains("Text expects a Lua string"));
        LuaError nilError = assertThrows(LuaError.class,
            () -> MLuaGuiService.setNativeControlProperty(null, "Tooltip", LuaValue.NIL));
        assertTrue(nilError.getMessage().contains("does not accept nil"));
    }

    public static final class NumericWidget {
        private String selected;
        public void setAmount(int value) { selected = "int"; }
        public void setAmount(double value) { selected = "double"; }
    }

    public static final class AmbiguousEnumWidget {
        public void setMode(FirstMode value) { }
        public void setMode(SecondMode value) { }
    }

    public static final class NilOverloadWidget {
        public void accept(Object value) { }
        public void accept(String value) { }
    }

    public enum FirstMode { OPEN }
    public enum SecondMode { OPEN }
}
