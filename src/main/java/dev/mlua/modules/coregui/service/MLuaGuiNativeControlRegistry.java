package dev.mlua.modules.coregui.service;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.binding.MLuaNativeControl;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Resolves native Minecraft widgets into scoped Lua control instances. */
final class MLuaGuiNativeControlRegistry {
    private final MLuaGuiService service;

    MLuaGuiNativeControlRegistry(MLuaGuiService service) {
        this.service = service;
    }

    LuaTable find(LuaValue selector, boolean firstOnly, Object screen) {
        LuaTable result = new LuaTable();
        if (screen == null) return result;
        try {
            Object childrenValue = screen.getClass().getMethod("children").invoke(screen);
            if (!(childrenValue instanceof Iterable<?> children)) return result;
            MLuaRegistration registration = this.service.context == null
                ? dev.mlua.core.MLuaRuntime.get()
                : this.service.context.mlua().registration();
            String screenName = screen.getClass().getName();
            int index = 0;
            int output = 1;
            for (Object widget : children) {
                if (widget == null || !MLuaNativeWidgetAccess.isNativeControl(widget)) continue;
                index++;
                if (selector.istable()
                        && !MLuaNativeWidgetAccess.matchesWidget(widget, index, selector.checktable())) continue;
                String owner = this.service.ownerId();
                MLuaNativeControl.Backing backing = new MLuaNativeControl.Backing(
                    widget, screen, screenName, index, owner, this.service.runtimeOwner());
                MLuaNativeLayout layout = this.service.findNativeLayout(screenName, index, owner);
                if (layout != null) {
                    backing.setReplacement(layout.properties().get("Replacement"));
                    backing.setBefore(layout.properties().get("Before"));
                    backing.setAfter(layout.properties().get("After"));
                }
                result.set(output++, MLuaNativeControl.instance(registration, backing));
                if (firstOnly) break;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return result;
    }

    static void requireSelector(LuaTable selector) {
        for (LuaValue key : selector.keys()) {
            if (key.type() != LuaValue.TSTRING) throw new LuaError("[MLua] Native widget selector fields must be strings");
            String name = key.tojstring();
            if (name.isBlank() || name.startsWith("__") || !Character.isLetter(name.charAt(0))) {
                throw new LuaError("[MLua] Invalid native widget selector field '" + name + "'");
            }
        }
        LuaValue index = selector.get("Index");
        LuaValue className = selector.get("Class");
        LuaValue text = selector.get("Text");
        if (!index.isnil() && (!index.isint() || index.checkint() < 1)) {
            throw new LuaError("[MLua] Native widget Index must be a positive one-based integer");
        }
        if (!className.isnil() && (className.type() != LuaValue.TSTRING || className.checkjstring().isBlank())) {
            throw new LuaError("[MLua] Native widget Class must be a non-empty Java class or interface name");
        }
        if (!text.isnil() && text.type() != LuaValue.TSTRING) {
            throw new LuaError("[MLua] Native widget Text must be a string");
        }
        for (LuaValue key : selector.keys()) {
            String name = key.tojstring();
            if (java.util.Set.of("Index", "Class", "Text").contains(name)) continue;
            LuaValue value = selector.get(key);
            if (value.type() != LuaValue.TSTRING && value.type() != LuaValue.TNUMBER
                    && value.type() != LuaValue.TBOOLEAN) {
                throw new LuaError("[MLua] Native widget property selector '" + name
                    + "' must use a string, number, or boolean");
            }
        }
        if (index.isnil() && className.isnil() && text.isnil() && selector.keys().length == 0) {
            throw new LuaError("[MLua] Native widget selector requires Index, Class, Text, or a readable property");
        }
    }
}
