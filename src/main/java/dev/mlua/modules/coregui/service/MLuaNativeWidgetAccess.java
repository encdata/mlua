package dev.mlua.modules.coregui.service;

import dev.mlua.core.engine.MLuaLuauTypes;
import dev.mlua.minecraft.binding.MLuaNativeControl;
import dev.mlua.minecraft.ui.UDim;
import dev.mlua.minecraft.ui.UDim2;
import dev.mlua.minecraft.ui.element.GuiObject;
import net.minecraft.network.chat.Component;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

/** Cross-version reflective access to Minecraft's native screen widgets. */
final class MLuaNativeWidgetAccess {
    private MLuaNativeWidgetAccess() { }

    static int uiDimension(LuaValue value, int parentSize) {
        if (!MLuaLuauTypes.isUDim(value)) throw new LuaError("[MLua] UDim2 axes must be UDim values");
        return (int) Math.round(parentSize * value.get("Scale").todouble() + value.get("Offset").toint());
    }

    static int readDimension(Object target, String methodName, String fieldName, int fallback) {
        int value = readInt(target, methodName, Integer.MIN_VALUE);
        if (value != Integer.MIN_VALUE) return value;
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.getInt(target);
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    static Object readWidgetText(Object widget) {
        try {
            return widget.getClass().getMethod("getMessage").invoke(widget);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    static boolean setWidgetText(Object widget, String text) {
        try {
            for (Method setter : widget.getClass().getMethods()) {
                if (!setter.getName().equals("setMessage") || setter.getParameterCount() != 1) continue;
                Class<?> componentType = setter.getParameterTypes()[0];
                Method literal = componentType.getMethod("literal", String.class);
                setter.invoke(widget, literal.invoke(null, text));
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return false;
    }

    static int readInt(Object target, String methodName, int fallback) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value instanceof Number number ? number.intValue() : fallback;
        } catch (ReflectiveOperationException ignored) {
            return fallback;
        }
    }

    static boolean invokeIfPresent(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            target.getClass().getMethod(methodName, parameterTypes).invoke(target, args);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new LuaError("[MLua] Failed to update native widget using " + methodName + ": " + failure.getMessage());
        }
    }

    static boolean setWidgetField(Object widget, String name, boolean value) {
        for (Class<?> type = widget.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.setBoolean(widget, value);
                return true;
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }
        return false;
    }

    static boolean setIntField(Object widget, String name, int value) {
        for (Class<?> type = widget.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.setInt(widget, value);
                return true;
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }
        return false;
    }

    static boolean readWidgetField(Object widget, String name, boolean fallback) {
        for (Class<?> type = widget.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.getBoolean(widget);
            } catch (NoSuchFieldException ignored) {
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    static void positionAdjacent(GuiObject element, Object widget, Object screen, boolean before) {
        if (element == null) return;
        int x = readInt(widget, "getX", 0);
        int y = readInt(widget, "getY", 0);
        int width = readInt(widget, "getWidth", 0);
        int height = readInt(widget, "getHeight", 0);
        int elementWidth = element.computeAbsoluteWidth(readDimension(screen, "getWidth", "width", 0));
        int elementHeight = element.computeAbsoluteHeight(readDimension(screen, "getHeight", "height", 0));
        int elementX = before ? x - elementWidth : x + width;
        int elementY = y + (height - elementHeight) / 2;
        element.setPosition(new UDim2(new UDim(0, elementX), new UDim(0, elementY)));
    }

    static boolean isNativeControl(Object widget) {
        try {
            widget.getClass().getMethod("getX");
            widget.getClass().getMethod("getY");
            widget.getClass().getMethod("getWidth");
            widget.getClass().getMethod("getHeight");
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    static boolean matchesWidget(Object widget, int index, LuaTable selector) {
        LuaValue targetIndex = selector.get("Index");
        if (targetIndex.isint() && targetIndex.checkint() != index) return false;
        LuaValue className = selector.get("Class");
        if (className.type() == LuaValue.TSTRING && !matchesWidgetType(widget.getClass(), className.checkjstring(), new java.util.HashSet<>())) return false;
        LuaValue text = selector.get("Text");
        if (text.type() == LuaValue.TSTRING) {
            try {
                Object message = widget.getClass().getMethod("getMessage").invoke(widget);
                Object value = message.getClass().getMethod("getString").invoke(message);
                if (!text.checkjstring().equals(value)) return false;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
        for (LuaValue key : selector.keys()) {
            if (key.type() != LuaValue.TSTRING) continue;
            String property = key.tojstring();
            if (Set.of("Index", "Class", "Text").contains(property)) continue;
            Object actual;
            try {
                actual = MLuaNativeControl.readWidgetProperty(widget, property);
            } catch (LuaError missingProperty) {
                return false;
            }
            if (!matchesWidgetProperty(actual, selector.get(key))) return false;
        }
        return targetIndex.isint() || className.type() == LuaValue.TSTRING
            || text.type() == LuaValue.TSTRING || selector.keys().length > 0;
    }

    static boolean matchesWidgetProperty(Object actual, LuaValue expected) {
        if (actual == null || expected.isnil()) return false;
        if (actual instanceof Component component) actual = component.getString();
        else if (actual instanceof Enum<?> value) actual = value.name();
        if (actual instanceof Boolean value) return expected.isboolean() && value == expected.toboolean();
        if (actual instanceof Number value) return expected.type() == LuaValue.TNUMBER
            && Double.compare(value.doubleValue(), expected.todouble()) == 0;
        if (actual instanceof String value) return expected.type() == LuaValue.TSTRING && value.equals(expected.tojstring());
        return false;
    }

    static boolean matchesWidgetType(Class<?> type, String selector, Set<Class<?>> visited) {
        if (type == null || !visited.add(type)) return false;
        if (selector.equals(type.getSimpleName()) || selector.equals(type.getName())) return true;
        for (Class<?> contract : type.getInterfaces()) {
            if (matchesWidgetType(contract, selector, visited)) return true;
        }
        return matchesWidgetType(type.getSuperclass(), selector, visited);
    }

    static void applyWidgetProperties(Object widget, LuaTable properties, Object screen) {
        LuaValue position = properties.get("Position");
        if (!position.isnil()) {
            if (!MLuaLuauTypes.isUDim2(position)) {
                throw new LuaError("[MLua] NativeControl.Position requires a UDim2 value created by UDim2.new, UDim2.fromScale, or UDim2.fromOffset");
            }
            LuaTable dimensions = position.checktable();
            LuaValue x = dimensions.get("X");
            LuaValue y = dimensions.get("Y");
            int screenWidth = readDimension(screen, "getWidth", "width", 0);
            int screenHeight = readDimension(screen, "getHeight", "height", 0);
            int nextX = uiDimension(x, screenWidth);
            int nextY = uiDimension(y, screenHeight);
            if (!invokeIfPresent(widget, "setPosition", new Class<?>[]{int.class, int.class}, nextX, nextY)
                && (!setIntField(widget, "x", nextX) || !setIntField(widget, "y", nextY))) {
                throw new LuaError("[MLua] NativeControl position is not writable for " + widget.getClass().getSimpleName());
            }
        }
        LuaValue size = properties.get("Size");
        if (!size.isnil()) {
            if (!MLuaLuauTypes.isUDim2(size)) {
                throw new LuaError("[MLua] NativeControl.Size requires a UDim2 value created by UDim2.new, UDim2.fromScale, or UDim2.fromOffset");
            }
            LuaTable dimensions = size.checktable();
            int nextWidth = uiDimension(dimensions.get("X"),
                readDimension(screen, "getWidth", "width", 0));
            if (!invokeIfPresent(widget, "setWidth", new Class<?>[]{int.class}, nextWidth) && !setIntField(widget, "width", nextWidth)) {
                throw new LuaError("[MLua] NativeControl size is not writable for " + widget.getClass().getSimpleName());
            }
            int nextHeight = uiDimension(dimensions.get("Y"),
                readDimension(screen, "getHeight", "height", 0));
            if (!invokeIfPresent(widget, "setHeight", new Class<?>[]{int.class}, nextHeight) && !setIntField(widget, "height", nextHeight)) {
                throw new LuaError("[MLua] NativeControl size is not writable for " + widget.getClass().getSimpleName());
            }
        }
        LuaValue visible = properties.get("Visible");
        if (!visible.isnil()) {
            if (!visible.isboolean()) throw new LuaError("[MLua] NativeControl.Visible expects a boolean");
            if (!setWidgetField(widget, "visible", visible.checkboolean())) {
                throw new LuaError("[MLua] NativeControl.Visible is not writable for " + widget.getClass().getSimpleName());
            }
        }
        LuaValue text = properties.get("Text");
        if (!text.isnil()) {
            if (text.type() != LuaValue.TSTRING) throw new LuaError("[MLua] NativeControl.Text expects a Lua string");
            if (!setWidgetText(widget, text.checkjstring())) {
                throw new LuaError("[MLua] NativeControl.Text is not writable for " + widget.getClass().getSimpleName());
            }
        }
        for (LuaValue key : properties.keys()) {
            if (key.type() != LuaValue.TSTRING) continue;
            String name = key.tojstring();
            if (Set.of("Position", "Size", "Visible", "Text", "Replacement", "Before", "After")
                .contains(name)) continue;
            MLuaNativeControl.setWidgetProperty(widget, name, properties.get(key));
        }
    }

}
