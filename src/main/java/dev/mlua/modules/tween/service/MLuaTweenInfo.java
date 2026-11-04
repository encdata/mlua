package dev.mlua.modules.tween.service;

import dev.mlua.common.enums.EasingDirection;
import dev.mlua.common.enums.EasingStyle;
import dev.mlua.modules.enums.MLuaEnumValue;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Validated value object shared by TweenInfo.new and TweenService:Create. */
public record MLuaTweenInfo(double duration, EasingStyle easingStyle, EasingDirection easingDirection) {
    private static final double MAX_DURATION_SECONDS = 86_400.0;

    public static MLuaTweenInfo fromArguments(Varargs args, int offset) {
        double duration = args.arg(offset + 1).checkdouble();
        validateDuration(duration, "TweenInfo.new");
        EasingStyle style = parseEnum(args.arg(offset + 2), EasingStyle.class, EasingStyle.Linear, "EasingStyle");
        EasingDirection direction = parseEnum(args.arg(offset + 3), EasingDirection.class,
            EasingDirection.Out, "EasingDirection");
        return new MLuaTweenInfo(duration, style, direction);
    }

    public static MLuaTweenInfo fromTable(LuaTable info) {
        if (info == null || !"TweenInfo".equals(info.get("__type").optjstring(""))) {
            throw new LuaError("[MLua] TweenService:Create expects a TweenInfo.new value.");
        }
        double duration = info.get("Duration").checkdouble();
        validateDuration(duration, "TweenInfo.Duration");
        EasingStyle style = parseString(info.get("EasingStyle"), EasingStyle.class,
            EasingStyle.Linear, "TweenInfo.EasingStyle");
        EasingDirection direction = parseString(info.get("EasingDirection"), EasingDirection.class,
            EasingDirection.Out, "TweenInfo.EasingDirection");
        return new MLuaTweenInfo(duration, style, direction);
    }

    public LuaTable toLuaTable() {
        LuaTable info = new LuaTable();
        info.set("Duration", duration);
        info.set("EasingStyle", easingStyle.name());
        info.set("EasingDirection", easingDirection.name());
        info.set("__type", "TweenInfo");
        return info;
    }

    private static void validateDuration(double duration, String source) {
        if (!Double.isFinite(duration) || duration < 0.0 || duration > MAX_DURATION_SECONDS) {
            throw new LuaError("[MLua] " + source + " must be finite and between 0 and 86400 seconds.");
        }
    }

    private static <E extends Enum<E>> E parseEnum(LuaValue value, Class<E> type, E fallback, String name) {
        if (value.isnil()) return fallback;
        E enumValue = MLuaEnumValue.toJava(value, type);
        return enumValue != null ? enumValue : parseString(value, type, fallback, name);
    }

    private static <E extends Enum<E>> E parseString(LuaValue value, Class<E> type, E fallback, String name) {
        if (value.isnil()) return fallback;
        if (!value.isstring()) throw new LuaError("[MLua] " + name + " must be a string or matching Enum value.");
        String nameValue = value.tojstring();
        try {
            return Enum.valueOf(type, nameValue);
        } catch (IllegalArgumentException invalid) {
            throw new LuaError("[MLua] Unknown " + name + ": " + nameValue);
        }
    }
}
