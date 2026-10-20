package dev.mlua.minecraft.animation;

import dev.mlua.common.enums.EasingDirection;
import dev.mlua.common.enums.EasingStyle;

public final class MLuaEasingHelper {

    private MLuaEasingHelper() {}

    public static double apply(double t, EasingStyle style, EasingDirection dir) {
        return switch (dir) {
            case In -> easeIn(t, style);
            case Out -> easeOut(t, style);
            case InOut -> easeInOut(t, style);
        };
    }

    private static double easeIn(double t, EasingStyle style) {
        return switch (style) {
            case Linear -> t;
            case Sine -> 1.0 - Math.cos(t * Math.PI / 2.0);
            case Quad -> t * t;
            case Cubic -> t * t * t;
            case Quart -> t * t * t * t;
            case Quint -> t * t * t * t * t;
            case Expo -> t == 0.0 ? 0.0 : Math.pow(2.0, 10.0 * (t - 1.0));
            case Circ -> 1.0 - Math.sqrt(1.0 - t * t);
            case Back -> {
                double c1 = 1.70158;
                yield c1 * t * t * t - c1 * t * t;
            }
            case Elastic -> {
                if (t == 0.0 || t == 1.0) yield t;
                double c4 = (2.0 * Math.PI) / 3.0;
                yield -Math.pow(2.0, 10.0 * t - 10.0) * Math.sin((t * 10.0 - 10.75) * c4);
            }
            case Bounce -> 1.0 - easeOut(1.0 - t, EasingStyle.Bounce);
        };
    }

    private static double easeOut(double t, EasingStyle style) {
        return switch (style) {
            case Linear -> t;
            case Sine -> Math.sin(t * Math.PI / 2.0);
            case Quad -> t * (2.0 - t);
            case Cubic -> {
                double f = 1.0 - t;
                yield 1.0 - f * f * f;
            }
            case Quart -> {
                double f = 1.0 - t;
                yield 1.0 - f * f * f * f;
            }
            case Quint -> {
                double f = 1.0 - t;
                yield 1.0 - f * f * f * f * f;
            }
            case Expo -> t == 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * t);
            case Circ -> Math.sqrt(1.0 - (t - 1.0) * (t - 1.0));
            case Back -> {
                double c1 = 1.70158;
                double f = t - 1.0;
                yield 1.0 + c1 * f * f * f + c1 * f * f;
            }
            case Elastic -> {
                if (t == 0.0 || t == 1.0) yield t;
                double c4 = (2.0 * Math.PI) / 3.0;
                yield Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * c4) + 1.0;
            }
            case Bounce -> {
                double n1 = 7.5625;
                double d1 = 2.75;
                if (t < 1.0 / d1) {
                    yield n1 * t * t;
                } else if (t < 2.0 / d1) {
                    yield n1 * (t -= 1.5 / d1) * t + 0.75;
                } else if (t < 2.5 / d1) {
                    yield n1 * (t -= 2.25 / d1) * t + 0.9375;
                } else {
                    yield n1 * (t -= 2.625 / d1) * t + 0.984375;
                }
            }
        };
    }

    private static double easeInOut(double t, EasingStyle style) {
        if (style == EasingStyle.Linear) return t;
        return t < 0.5
            ? easeIn(t * 2.0, style) * 0.5
            : easeOut(t * 2.0 - 1.0, style) * 0.5 + 0.5;
    }
}
