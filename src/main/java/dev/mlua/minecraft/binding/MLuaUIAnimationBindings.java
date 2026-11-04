package dev.mlua.minecraft.binding;

import dev.mlua.common.enums.EasingDirection;
import dev.mlua.common.enums.EasingStyle;
import dev.mlua.core.engine.MLuaLuauTypes;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.enums.MLuaEnumValue;
import dev.mlua.minecraft.ui.UDim;
import dev.mlua.minecraft.ui.UDim2;
import dev.mlua.minecraft.ui.element.GuiBase;
import dev.mlua.minecraft.ui.element.GuiObject;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Owns Lua argument conversion and methods for animated GuiObject properties. */
final class MLuaUIAnimationBindings {
    private MLuaUIAnimationBindings() { }

    static void register(MLuaRegistration registration) {
        registration.registerClassFunction("GuiObject", "MoveTo", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                GuiObject object = requireGuiObject(args.arg1(), "MoveTo");
                return LuaValue.valueOf(object.tweenPosition(parseUDim2(args.arg(2)), easingDirection(args.arg(5)),
                    easingStyle(args.arg(4)), positiveDuration(args.arg(3)), args.arg(6).optboolean(true)));
            }
        }, "Animates Position to a UDim2 target and returns whether the animation started.");
        registration.registerClassFunction("GuiObject", "ResizeTo", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                GuiObject object = requireGuiObject(args.arg1(), "ResizeTo");
                return LuaValue.valueOf(object.tweenSize(parseUDim2(args.arg(2)), easingDirection(args.arg(5)),
                    easingStyle(args.arg(4)), positiveDuration(args.arg(3)), args.arg(6).optboolean(true)));
            }
        }, "Animates Size to a UDim2 target and returns whether the animation started.");
        registration.registerClassFunction("GuiObject", "MoveAndResizeTo", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                GuiObject object = requireGuiObject(args.arg1(), "MoveAndResizeTo");
                return LuaValue.valueOf(object.tweenSizeAndPosition(parseUDim2(args.arg(3)), parseUDim2(args.arg(2)),
                    easingDirection(args.arg(6)), easingStyle(args.arg(5)), positiveDuration(args.arg(4)),
                    args.arg(7).optboolean(true)));
            }
        }, "Animates both Position and Size to UDim2 targets and returns whether the animation started.");
        registration.registerClassFunction("GuiObject", "AnimateTo", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                GuiObject object = requireGuiObject(args.arg1(), "AnimateTo");
                LuaValue goalsValue = args.arg(2);
                if (!goalsValue.istable()) throw new LuaError("[MLua] AnimateTo expects a table of target properties.");
                LuaTable goals = goalsValue.checktable();
                UDim2 position = null;
                UDim2 size = null;
                Float rotation = null;
                Float transparency = null;
                Integer backgroundColor = null;
                Integer borderColor = null;
                Integer borderSizePixel = null;
                Integer textColor = null;
                Float textTransparency = null;
                Integer imageColor = null;
                Float imageTransparency = null;
                LuaValue key = LuaValue.NIL;
                while (true) {
                    Varargs entry = goals.next(key);
                    key = entry.arg1();
                    if (key.isnil()) break;
                    if (!key.isstring()) throw new LuaError("[MLua] AnimateTo target property names must be strings.");
                    String name = key.tojstring();
                    LuaValue value = entry.arg(2);
                    switch (name) {
                        case "Position" -> position = parseUDim2(value);
                        case "Size" -> size = parseUDim2(value);
                        case "Rotation" -> rotation = finiteFloat(value, "Rotation");
                        case "BackgroundTransparency" -> {
                            transparency = finiteFloat(value, "BackgroundTransparency");
                            if (transparency < 0.0f || transparency > 1.0f) {
                                throw new LuaError("[MLua] BackgroundTransparency animation target must be between zero and one.");
                            }
                        }
                        case "BackgroundColor3" -> backgroundColor = parseColor3(value);
                        case "BorderColor3" -> borderColor = parseColor3(value);
                        case "BorderSizePixel" -> borderSizePixel = nonNegativePixelSize(value);
                        case "TextColor3" -> textColor = parseColor3(value);
                        case "TextTransparency" -> {
                            textTransparency = finiteFloat(value, "TextTransparency");
                            if (textTransparency < 0.0f || textTransparency > 1.0f) {
                                throw new LuaError("[MLua] TextTransparency animation target must be between zero and one.");
                            }
                        }
                        case "ImageColor3" -> imageColor = parseColor3(value);
                        case "ImageTransparency" -> {
                            imageTransparency = finiteFloat(value, "ImageTransparency");
                            if (imageTransparency < 0.0f || imageTransparency > 1.0f) {
                                throw new LuaError("[MLua] ImageTransparency animation target must be between zero and one.");
                            }
                        }
                        default -> throw new LuaError("[MLua] AnimateTo does not support the UI property '" + name + "'.");
                    }
                }
                if (position == null && size == null && rotation == null && transparency == null && backgroundColor == null
                    && borderColor == null && borderSizePixel == null
                    && textColor == null && textTransparency == null && imageColor == null && imageTransparency == null) {
                    throw new LuaError("[MLua] AnimateTo requires at least one supported target property.");
                }
                return LuaValue.valueOf(object.tweenTo(position, size, rotation, transparency, backgroundColor,
                    borderColor, borderSizePixel, textColor, textTransparency, imageColor, imageTransparency,
                    easingDirection(args.arg(5)), easingStyle(args.arg(4)),
                    positiveDuration(args.arg(3)), args.arg(6).optboolean(true)));
            }
        }, "Animates supported UI properties toward a target table: Position, Size, Rotation, BackgroundTransparency, BackgroundColor3, BorderColor3, BorderSizePixel, TextColor3, TextTransparency, ImageColor3, or ImageTransparency.");
        registration.registerClassFunction("GuiObject", "CancelAnimation", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(requireGuiObject(args.arg1(), "CancelAnimation").cancelTween());
            }
        }, "Stops the active UI animation at its current property values and returns whether an animation was stopped.");
        registration.registerClassFunction("GuiObject", "IsAnimating", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(requireGuiObject(args.arg1(), "IsAnimating").isTweening());
            }
        }, "Returns whether this UI object currently has a running property animation.");
    }

    private static GuiObject requireGuiObject(LuaValue value, String method) {
        GuiBase element = MLuaUIInstanceBindings.requireGuiElement(value, method);
        if (!(element instanceof GuiObject object)) throw new LuaError("[MLua] " + method + " requires a GuiObject instance.");
        return object;
    }

    private static double positiveDuration(LuaValue value) {
        double duration = value.checkdouble();
        if (!Double.isFinite(duration) || duration <= 0.0) {
            throw new LuaError("[MLua] UI animation duration must be a finite number greater than zero.");
        }
        return duration;
    }

    private static UDim2 parseUDim2(LuaValue value) {
        if (!MLuaLuauTypes.isUDim2(value)) {
            throw new LuaError("[MLua] Animation Position and Size targets require native UDim2 values.");
        }
        LuaTable table = value.checktable();
        LuaValue x = table.get("X").isnil() ? table.get("x") : table.get("X");
        LuaValue y = table.get("Y").isnil() ? table.get("y") : table.get("Y");
        return new UDim2(parseUDim(x), parseUDim(y));
    }

    private static UDim parseUDim(LuaValue value) {
        if (!MLuaLuauTypes.isUDim(value)) {
            throw new LuaError("[MLua] Animation Position and Size axes require native UDim values.");
        }
        LuaTable table = value.checktable();
        return new UDim(table.get("Scale").checkdouble(),
            MLuaLuaArguments.wholeInt(table.get("Offset"), "UI animation", "UDim offset"));
    }

    private static int parseColor3(LuaValue value) {
        if (MLuaLuauTypes.isColor3(value)) return MLuaLuauTypes.color3Argb(value);
        if (value.isnumber()) return MLuaLuaArguments.wholeInt32Bits(value, "UI animation", "packed color");
        throw new LuaError("[MLua] UI color animation targets require a native Color3 value or packed color integer.");
    }

    private static int nonNegativePixelSize(LuaValue value) {
        double size = value.checkdouble();
        if (!Double.isFinite(size) || size < 0.0 || size > Integer.MAX_VALUE) {
            throw new LuaError("[MLua] BorderSizePixel animation target must be a finite non-negative value within the supported range.");
        }
        return (int)Math.round(size);
    }

    private static float finiteFloat(LuaValue value, String property) {
        double number = value.checkdouble();
        if (!Double.isFinite(number) || number < -Float.MAX_VALUE || number > Float.MAX_VALUE) {
            throw new LuaError("[MLua] " + property + " animation target must be a finite number.");
        }
        return (float) number;
    }

    private static String easingStyle(LuaValue value) {
        if (value.isnil()) return EasingStyle.Linear.name();
        EasingStyle style = MLuaEnumValue.toJava(value, EasingStyle.class);
        if (style == null) throw new LuaError("[MLua] UI animation easing style must be an Enum.EasingStyle value.");
        return style.name();
    }

    private static String easingDirection(LuaValue value) {
        if (value.isnil()) return EasingDirection.Out.name();
        EasingDirection direction = MLuaEnumValue.toJava(value, EasingDirection.class);
        if (direction == null) throw new LuaError("[MLua] UI animation easing direction must be an Enum.EasingDirection value.");
        return direction.name();
    }

}
