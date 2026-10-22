package dev.mlua.modules.coregui.service;

import dev.mlua.minecraft.binding.MLuaLuaArguments;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Owns screen text placement and the Lua canvas drawing bridge. */
final class MLuaScriptScreenRenderer {
    interface Drawing {
        void fill(int left, int top, int right, int bottom, int color);
        void text(Object text, int x, int y, int color, boolean shadow);
    }

    private final List<ScreenText> textLines = new CopyOnWriteArrayList<>();

    void addText(Object text, LuaTable position, int color, int width, int height) {
        textLines.add(new ScreenText(text,
            MLuaGuiService.screenCoordinate(position, "X", width),
            MLuaGuiService.screenCoordinate(position, "Y", height), color));
    }

    void drawText(Drawing drawing) {
        for (ScreenText line : textLines) {
            drawing.text(line.text(), line.x(), line.y(), line.color(), true);
        }
    }

    LuaTable createCanvas(Drawing drawing, int width, int height) {
        LuaTable canvas = new LuaTable();
        canvas.set("Width", LuaValue.valueOf(width));
        canvas.set("Height", LuaValue.valueOf(height));
        canvas.set("Fill", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                drawing.fill(MLuaLuaArguments.wholeInt(args, 2, "ScreenCanvas:Fill", "left"),
                    MLuaLuaArguments.wholeInt(args, 3, "ScreenCanvas:Fill", "top"),
                    MLuaLuaArguments.wholeInt(args, 4, "ScreenCanvas:Fill", "right"),
                    MLuaLuaArguments.wholeInt(args, 5, "ScreenCanvas:Fill", "bottom"),
                    MLuaGuiService.readColor(args.arg(6), "ScreenCanvas:Fill", "color"));
                return LuaValue.NONE;
            }
        });
        canvas.set("DrawText", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                drawing.text(MLuaGuiService.nativeText(args.arg(2)),
                    MLuaLuaArguments.wholeInt(args, 3, "ScreenCanvas:DrawText", "x"),
                    MLuaLuaArguments.wholeInt(args, 4, "ScreenCanvas:DrawText", "y"),
                    MLuaGuiService.optionalColor(args.arg(5), 0xFFFFFF, "ScreenCanvas:DrawText", "color"),
                    args.arg(6).optboolean(true));
                return LuaValue.NONE;
            }
        });
        return canvas;
    }

    void clear() {
        this.textLines.clear();
    }

    private record ScreenText(Object text, int x, int y, int color) { }
}
