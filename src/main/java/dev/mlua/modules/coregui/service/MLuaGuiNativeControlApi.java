package dev.mlua.modules.coregui.service;

import dev.mlua.core.engine.MLuaLuauTypes;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import java.util.Set;

/** Registers native control queries and screen layout operations on Gui. */
final class MLuaGuiNativeControlApi {
    private MLuaGuiNativeControlApi() { }

    static void install(MLuaGuiService service, LuaTable table) {
        table.set("GetNativeControls", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue selector = args.arg(2);
                if (!selector.isnil() && !selector.istable()) throw new LuaError("[MLua] Gui:GetNativeControls selector must be a table");
                if (selector.istable()) MLuaGuiValueParser.requireNativeSelector(selector.checktable());
                return service.nativeControls(selector, false);
            }
        });

        table.set("GetNativeControl", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable selector = args.checktable(2);
                MLuaGuiValueParser.requireNativeSelector(selector);
                return service.nativeControls(selector, true).get(1);
            }
        });

        table.set("SetNativeLayout", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                service.requireNativeScreenClientThread("Gui:SetNativeLayout");
                String target = args.checkjstring(2);
                if (target.isBlank()) throw new LuaError("[MLua] Gui:SetNativeLayout requires a screen class name or 'Any'");
                LuaTable selector = args.checktable(3);
                LuaTable properties = args.checktable(4);
                MLuaGuiValueParser.requireNativeSelector(selector);
                if (properties.keys().length == 0) throw new LuaError("[MLua] Native layout needs at least one property");
                for (LuaValue key : properties.keys()) {
                    if (key.type() != LuaValue.TSTRING || key.tojstring().isBlank() || !Character.isLetter(key.tojstring().charAt(0))
                        || key.tojstring().startsWith("__")) {
                        throw new LuaError("[MLua] Native layout property names must start with a letter and cannot be runtime metadata");
                    }
                }
                if (!properties.get("X").isnil() || !properties.get("Y").isnil()
                    || !properties.get("Width").isnil() || !properties.get("Height").isnil()) {
                    throw new LuaError("[MLua] Native layout uses Position and Size UDim2 values, not X/Y/Width/Height fields");
                }
                if (!properties.get("Position").isnil() && !MLuaLuauTypes.isUDim2(properties.get("Position"))
                    || !properties.get("Size").isnil() && !MLuaLuauTypes.isUDim2(properties.get("Size"))
                    || !properties.get("Visible").isnil() && !properties.get("Visible").isboolean()
                    || !properties.get("Text").isnil() && properties.get("Text").type() != LuaValue.TSTRING) {
                    throw new LuaError("[MLua] Native layout Position and Size require UDim2 values; Visible must be boolean and Text must be a string");
                }
                for (LuaValue key : properties.keys()) {
                    if (key.type() != LuaValue.TSTRING || Set.of("Position", "Size", "Visible", "Text", "Replacement", "Before", "After").contains(key.tojstring())) continue;
                    LuaValue value = properties.get(key);
                    if (value.type() != LuaValue.TSTRING && value.type() != LuaValue.TNUMBER
                        && value.type() != LuaValue.TBOOLEAN) {
                        throw new LuaError("[MLua] Native layout property '" + key
                            + "' must use a string, number, boolean, enum name, or text value");
                    }
                }
                if (!properties.get("Replacement").isnil()
                    && ((!selector.get("Index").isint() && selector.get("Text").type() != LuaValue.TSTRING)
                        || properties.get("Visible").isboolean() && properties.get("Visible").checkboolean())) {
                    throw new LuaError("[MLua] A replacement requires a specific Index or Text selector and cannot set Visible=true");
                }
                if ((!properties.get("Before").isnil() || !properties.get("After").isnil()) && !selector.get("Index").isint()) {
                    throw new LuaError("[MLua] NativeControl.Before and After require a specific Index selector");
                }
                MLuaNativeLayout layout = service.addNativeLayout(target, selector, properties);
                try {
                    service.recomposeLayouts(MLuaNativeScreenManager.currentScreen(), layout);
                } catch (RuntimeException | Error failure) {
                    service.removeNativeLayout(layout.id());
                    throw failure;
                }
                return LuaValue.valueOf(layout.id());
            }
        });

        table.set("RemoveNativeLayout", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                service.requireNativeScreenClientThread("Gui:RemoveNativeLayout");
                return LuaValue.valueOf(service.removeNativeLayout(args.checklong(2)));
            }
        });

    }

}
