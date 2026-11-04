package dev.mlua.minecraft.binding;

import net.minecraft.network.chat.Component;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Converts supported Lua values to and from native Minecraft widget values. */
final class MLuaNativeControlValueAdapter {
    private MLuaNativeControlValueAdapter() { }

    static Object fromLuaValue(LuaValue value, Class<?> type) {
        if (value.isnil()) {
            if (type.isPrimitive()) throw new IllegalArgumentException();
            return null;
        }
        if (type == String.class) {
            if (value.type() != LuaValue.TSTRING) throw new IllegalArgumentException();
            return value.tojstring();
        }
        if (type == boolean.class || type == Boolean.class) {
            if (!value.isboolean()) throw new IllegalArgumentException();
            return value.toboolean();
        }
        if (type == int.class || type == Integer.class) {
            if (value.type() != LuaValue.TNUMBER) throw new IllegalArgumentException();
            double number = value.todouble();
            if (!Double.isFinite(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE
                || number != Math.rint(number)) throw new IllegalArgumentException();
            return (int) number;
        }
        if (type == long.class || type == Long.class) {
            if (value.type() != LuaValue.TNUMBER) throw new IllegalArgumentException();
            double number = value.todouble();
            // The upper bound is exclusive because Long.MAX_VALUE rounds to 2^63 as a double.
            if (!Double.isFinite(number) || number < -0x1.0p63 || number >= 0x1.0p63
                || number != Math.rint(number)) throw new IllegalArgumentException();
            return (long) number;
        }
        if (type == float.class || type == Float.class) {
            if (value.type() != LuaValue.TNUMBER) throw new IllegalArgumentException();
            double number = value.todouble();
            float converted = (float) number;
            if (!Double.isFinite(number) || !Float.isFinite(converted) || (number != 0.0D && converted == 0.0F)) {
                throw new IllegalArgumentException();
            }
            return converted;
        }
        if (type == double.class || type == Double.class) {
            if (value.type() != LuaValue.TNUMBER) throw new IllegalArgumentException();
            double number = value.todouble();
            if (!Double.isFinite(number)) throw new IllegalArgumentException();
            return number;
        }
        if (Component.class.isAssignableFrom(type)) {
            if (value.type() != LuaValue.TSTRING) throw new IllegalArgumentException();
            Component component = Component.literal(value.tojstring());
            if (!type.isInstance(component)) throw new IllegalArgumentException();
            return component;
        }
        if (type.isEnum()) {
            if (value.type() != LuaValue.TSTRING) throw new IllegalArgumentException();
            for (Object constant : type.getEnumConstants()) {
                if (((Enum<?>) constant).name().equalsIgnoreCase(value.tojstring())) return constant;
            }
        }
        throw new IllegalArgumentException();
    }

    static Conversion convertArgument(LuaValue value, Class<?> type, boolean allowNil) {
        if (value.isnil()) {
            if (!allowNil || type.isPrimitive()) throw new IllegalArgumentException();
            return new Conversion(null, 4);
        }
        Object converted = fromLuaValue(value, type);
        if (type == String.class || type == boolean.class) return new Conversion(converted, 0);
        if (type == Boolean.class) return new Conversion(converted, 1);
        if (Component.class.isAssignableFrom(type)) return new Conversion(converted, 1);
        if (type.isEnum()) return new Conversion(converted, 2);
        if (value.type() == LuaValue.TNUMBER) {
            double number = value.todouble();
            boolean integral = number == Math.rint(number);
            int score;
            if (type == int.class) score = 0;
            else if (type == Integer.class) score = 1;
            else if (type == long.class) score = 2;
            else if (type == Long.class) score = 3;
            else if (type == double.class) score = integral ? 4 : 0;
            else if (type == Double.class) score = integral ? 5 : 1;
            else if (type == float.class) score = integral ? 6 : 2;
            else if (type == Float.class) score = integral ? 7 : 3;
            else throw new IllegalArgumentException();
            return new Conversion(converted, score);
        }
        throw new IllegalArgumentException();
    }

    static LuaValue toLuaValue(Object value) {
        if (value == null) return LuaValue.NIL;
        if (value instanceof String string) return LuaValue.valueOf(string);
        if (value instanceof Boolean bool) return LuaValue.valueOf(bool);
        if (value instanceof Number number) return LuaValue.valueOf(exactLuaNumber(number));
        if (value instanceof Enum<?> enumeration) return LuaValue.valueOf(enumeration.name());
        if (value instanceof Component component) return LuaValue.valueOf(component.getString());
        throw new LuaError("[MLua] Native widget value type is not exposed to Lua: " + value.getClass().getSimpleName());
    }

    private static double exactLuaNumber(Number number) {
        double converted = number.doubleValue();
        if (number instanceof Long || number instanceof java.util.concurrent.atomic.AtomicLong) {
            long integer = number.longValue();
            // Double's upper endpoint is exclusive: 2^63 converts back to Long.MAX_VALUE
            // by saturation, so the round-trip check alone cannot detect that overflow.
            if (converted < -0x1.0p63 || converted >= 0x1.0p63 || (long) converted != integer) {
                throw new LuaError("[MLua] Native widget integer result cannot be represented exactly as a Lua number: "
                    + integer);
            }
            return converted;
        }
        if (number instanceof Float || number instanceof Double) return converted;
        if (!Double.isFinite(converted)) {
            throw new LuaError("[MLua] Native widget numeric result is outside Lua's number range: " + number);
        }
        try {
            java.math.BigDecimal original = new java.math.BigDecimal(number.toString());
            java.math.BigDecimal roundTrip;
            try {
                // Integer values can be exactly represented above 2^53 when they align with binary precision.
                original.toBigIntegerExact();
                roundTrip = new java.math.BigDecimal(converted);
            } catch (ArithmeticException fractionalValue) {
                roundTrip = java.math.BigDecimal.valueOf(converted);
            }
            if (original.compareTo(roundTrip) != 0) {
                throw new LuaError("[MLua] Native widget numeric result cannot be represented exactly as a Lua number: "
                    + number);
            }
        } catch (NumberFormatException invalidNumber) {
            throw new LuaError("[MLua] Native widget returned an unsupported numeric value: " + number);
        }
        return converted;
    }

    record Conversion(Object value, int score) { }
}
