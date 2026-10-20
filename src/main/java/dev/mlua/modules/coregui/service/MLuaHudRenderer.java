package dev.mlua.modules.coregui.service;

import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Executes registered HUD callbacks and owns the short-lived drawing context they receive. */
final class MLuaHudRenderer {
    private MLuaHudRenderer() { }

    static void render(List<MLuaHudElement> elements, Object graphics) {
        if (graphics == null || elements.isEmpty()) return;
        for (MLuaHudElement element : elements) {
            if (!element.failed) renderElement(element, graphics);
        }
    }

    private static void renderElement(MLuaHudElement element, Object graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        Object window = minecraft == null ? null : minecraft.getWindow();
        synchronized (element.executionLock) {
            HudCanvasFrame frame = new HudCanvasFrame(graphics);
            LuaTable canvas = createCanvas(element, frame);
            canvas.set("Width", LuaValue.valueOf(readNumber(window, "getGuiScaledWidth", "guiScaledWidth")));
            canvas.set("Height", LuaValue.valueOf(readNumber(window, "getGuiScaledHeight", "guiScaledHeight")));
            canvas.set("Anchor", LuaValue.valueOf(element.anchor));
            canvas.set("Phase", LuaValue.valueOf(element.before ? "Before" : "After"));
            try {
                MLuaLuaBindings.withContext(element.context, () -> MLuaCallbackBudget.run(element.context, () -> {
                    element.callback.invoke(canvas);
                    return null;
                }));
            } catch (RuntimeException failure) {
                element.failed = true;
                MLuaLogger.LOGGER.error("Lua HUD element '{}' failed; rendering is paused until it is registered again",
                    element.id, failure);
            } finally {
                frame.close();
            }
        }
    }

    private static LuaTable createCanvas(MLuaHudElement element, HudCanvasFrame frame) {
        LuaTable canvas = new LuaTable();
        canvas.set("Width", LuaValue.ZERO);
        canvas.set("Height", LuaValue.ZERO);
        canvas.set("Anchor", LuaValue.valueOf(element.anchor));
        canvas.set("Phase", LuaValue.valueOf(element.before ? "Before" : "After"));
        canvas.set("Fill", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                drawFill(frame.requireGraphics(), MLuaLuaArguments.wholeInt(args, 2, "HudCanvas:Fill", "left"),
                    MLuaLuaArguments.wholeInt(args, 3, "HudCanvas:Fill", "top"),
                    MLuaLuaArguments.wholeInt(args, 4, "HudCanvas:Fill", "right"),
                    MLuaLuaArguments.wholeInt(args, 5, "HudCanvas:Fill", "bottom"),
                    MLuaLuaArguments.wholeInt32Bits(args.arg(6), "HudCanvas:Fill", "color"));
                return LuaValue.NONE;
            }
        });
        canvas.set("DrawText", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                drawText(frame.requireGraphics(), MLuaGuiService.nativeText(args.arg(2)),
                    MLuaLuaArguments.wholeInt(args, 3, "HudCanvas:DrawText", "x"),
                    MLuaLuaArguments.wholeInt(args, 4, "HudCanvas:DrawText", "y"),
                    args.arg(5).isnil() ? 0xFFFFFF
                        : MLuaLuaArguments.wholeInt32Bits(args.arg(5), "HudCanvas:DrawText", "color"),
                    args.arg(6).optboolean(true));
                return LuaValue.NONE;
            }
        });
        return canvas;
    }

    private static int readNumber(Object target, String... methodNames) {
        if (target == null) return 0;
        for (String name : methodNames) {
            try {
                Object value = target.getClass().getMethod(name).invoke(target);
                if (value instanceof Number number) return number.intValue();
            } catch (ReflectiveOperationException ignored) { }
        }
        return 0;
    }

    private static void drawFill(Object graphics, int left, int top, int right, int bottom, int color) {
        try {
            graphics.getClass().getMethod("fill", int.class, int.class, int.class, int.class, int.class)
                .invoke(graphics, left, top, right, bottom, color);
        } catch (ReflectiveOperationException failure) {
            throw new LuaError("[MLua] This Minecraft HUD graphics context does not support Fill.");
        }
    }

    static void drawText(Object graphics, Object text, int x, int y, int color, boolean shadow) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) throw new LuaError("[MLua] Minecraft client is not available.");
        Class<?> textType = text instanceof Component ? Component.class : String.class;
        for (String name : List.of("text", "drawString")) {
            try {
                graphics.getClass().getMethod(name, Font.class, textType, int.class, int.class, int.class, boolean.class)
                    .invoke(graphics, minecraft.font, text, x, y, color, shadow);
                return;
            } catch (ReflectiveOperationException ignored) { }
        }
        throw new LuaError("[MLua] This Minecraft HUD graphics context does not support DrawText.");
    }

    private static final class HudCanvasFrame {
        private final Thread renderThread = Thread.currentThread();
        private volatile Object graphics;

        private HudCanvasFrame(Object graphics) {
            this.graphics = java.util.Objects.requireNonNull(graphics, "HUD graphics");
        }

        private Object requireGraphics() {
            if (Thread.currentThread() != this.renderThread) {
                throw new LuaError("[MLua] HUD canvas drawing methods must run on the render thread that created the canvas.");
            }
            Object current = this.graphics;
            if (current == null) {
                throw new LuaError("[MLua] HUD canvas drawing methods are only available during their render callback.");
            }
            return current;
        }

        private void close() {
            this.graphics = null;
        }
    }
}
