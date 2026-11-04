package dev.mlua.modules.coregui.service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Owns the Lua-facing builder passed to a screen's initialization callback. */
final class MLuaScriptScreenBuilder {
    private MLuaScriptScreenBuilder() { }

    static LuaTable create(Host host) {
        LuaTable builder = new LuaTable();
        builder.set("SetOnClose", function(args -> {
            LuaValue callback = args.arg(2);
            requireCallback(callback, "Screen:SetOnClose");
            host.setOnClose(callback);
            return LuaValue.NONE;
        }));
        builder.set("SetOnTick", function(args -> {
            LuaValue callback = args.arg(2);
            requireCallback(callback, "Screen:SetOnTick");
            host.setOnTick(callback);
            return LuaValue.NONE;
        }));
        builder.set("SetOnRender", function(args -> {
            LuaValue callback = args.arg(2);
            requireCallback(callback, "Screen:SetOnRender");
            host.setOnRender(callback);
            return LuaValue.NONE;
        }));
        builder.set("AddText", function(args -> {
            Object text = MLuaGuiValueParser.nativeText(args.arg(2));
            LuaTable position = MLuaGuiValueParser.requireScreenUDim2(args.arg(3), "Position");
            int color = MLuaGuiValueParser.optionalColor(args.arg(4), 0xFFFFFF, "NativeScreen:AddText", "color");
            host.addText(text, position, color);
            return LuaValue.NONE;
        }));
        builder.set("AddButton", function(args -> {
            Object label = MLuaGuiValueParser.nativeText(args.arg(2));
            LuaTable position = MLuaGuiValueParser.requireScreenUDim2(args.arg(3), "Position");
            LuaTable size = MLuaGuiValueParser.requireScreenUDim2(args.arg(4), "Size");
            LuaValue callback = args.arg(5);
            requireCallback(callback, "Screen:AddButton");
            return host.addButton(label, position, size, callback);
        }));
        builder.set("AddEditBox", function(args -> {
            LuaTable position = MLuaGuiValueParser.requireScreenUDim2(args.arg(2), "Position");
            LuaTable size = MLuaGuiValueParser.requireScreenUDim2(args.arg(3), "Size");
            String initialText = args.arg(4).optjstring("");
            LuaValue changed = args.arg(5);
            if (!changed.isnil() && !changed.isfunction()) {
                throw new LuaError("[MLua] Screen:AddEditBox onChanged must be a callback function.");
            }
            return host.addEditBox(position, size, initialText, changed);
        }));
        builder.set("AddRenderableWidget", function(args -> host.addRenderableWidget(args.arg(2))));
        return builder;
    }

    private static VarArgFunction function(java.util.function.Function<Varargs, Varargs> callback) {
        return new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return callback.apply(args); }
        };
    }

    private static void requireCallback(LuaValue callback, String operation) {
        if (!callback.isfunction()) {
            throw new LuaError("[MLua] " + operation + " expects a callback function.");
        }
    }

    interface Host {
        void setOnClose(LuaValue callback);
        void setOnTick(LuaValue callback);
        void setOnRender(LuaValue callback);
        void addText(Object text, LuaTable position, int color);
        LuaTable addButton(Object label, LuaTable position, LuaTable size, LuaValue callback);
        LuaTable addEditBox(LuaTable position, LuaTable size, String initialText, LuaValue changed);
        LuaTable addRenderableWidget(LuaValue widget);
    }
}
