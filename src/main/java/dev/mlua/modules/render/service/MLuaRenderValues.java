package dev.mlua.modules.render.service;

import dev.mlua.math.MLuaCFrame;
import dev.mlua.math.MLuaVector;
import dev.mlua.runtime.client.MLuaClientRenderManager;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Shared validation and conversion rules for Lua render values. */
final class MLuaRenderValues {
    private MLuaRenderValues() { }

    static double opacity(LuaValue value, String propertyName) {
        double opacity = finiteNumber(value, propertyName);
        if (!Double.isFinite(opacity) || opacity < 0.0 || opacity > 1.0) {
            throw new LuaError("[MLua] RenderObject " + propertyName + " must be between 0 and 1.");
        }
        return opacity;
    }

    static boolean supportsResource(MLuaClientRenderManager.RenderType type) {
        return type == MLuaClientRenderManager.RenderType.IMAGE
            || type == MLuaClientRenderManager.RenderType.ITEM
            || type == MLuaClientRenderManager.RenderType.BLOCK;
    }

    static void requireResourceType(MLuaClientRenderManager.RenderType type, String operation) {
        if (type == MLuaClientRenderManager.RenderType.TEXT) {
            throw new LuaError("[MLua] RenderObject:" + operation + " is not available on text objects; use SetText.");
        }
        if (!supportsResource(type)) {
            throw new LuaError("[MLua] RenderObject:" + operation + " is only available on image, item, or block objects.");
        }
    }

    static double[] worldVector(LuaValue value) {
        double[] vector = MLuaCFrame.vector(value);
        for (double component : vector) finiteNumber(component, "WorldPosition");
        return vector;
    }

    static double[] screenVector(LuaValue value) {
        if (!MLuaVector.isVector2(value)) throw new LuaError("[MLua] Render screen coordinates require Vector2 values");
        LuaTable vector = value.checktable();
        return new double[]{finiteNumber(vector.get("X"), "Position"), finiteNumber(vector.get("Y"), "Position")};
    }

    static double[] screenSize(LuaValue value) {
        double[] size = screenVector(value);
        if (size[0] < 0.0 || size[1] < 0.0) {
            throw new LuaError("[MLua] RenderObject Size components must be non-negative.");
        }
        return size;
    }

    static double nonNegative(LuaValue value, String name) {
        return atLeast(value, name, 0.0);
    }

    static float modelScale(LuaValue value) {
        double scale = atLeast(value, "ModelScale", 0.001);
        if (scale > Float.MAX_VALUE) {
            throw new LuaError("[MLua] RenderObject ModelScale must fit in a finite float.");
        }
        return (float)scale;
    }

    static double atLeast(LuaValue value, String name, double minimum) {
        double number = finiteNumber(value, name);
        if (number < minimum) {
            throw new LuaError("[MLua] RenderObject " + name + " must be at least " + minimum + ".");
        }
        return number;
    }

    static double finiteNumber(LuaValue value, String name) {
        return finiteNumber(value.checkdouble(), name);
    }

    static double finiteNumber(double number, String name) {
        if (!Double.isFinite(number)) throw new LuaError("[MLua] RenderObject " + name + " must be finite.");
        return number;
    }

    static int wholeInt(LuaValue value, String name) {
        double number = finiteNumber(value, name);
        if (number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new LuaError("[MLua] RenderObject " + name + " must be a whole number within the 32-bit integer range.");
        }
        return (int)number;
    }

    static String renderTargetName(MLuaClientRenderManager.RenderTarget target) {
        return switch (target) {
            case MANUAL -> "Manual";
            case HUD -> "Hud";
            case WORLD_OVERLAY -> "WorldOverlay";
            case WORLD_MODEL -> "WorldModel";
            case ENTITY_MODEL -> "EntityModel";
            case FIRST_PERSON_ITEM -> "FirstPersonItem";
            case THIRD_PERSON_ITEM -> "ThirdPersonItem";
        };
    }

    static int packedColor(LuaValue value) {
        double number = finiteNumber(value, "Color");
        if (number != Math.rint(number) || number < Integer.MIN_VALUE || number > 0xffffffffL) {
            throw new LuaError("[MLua] RenderObject Color must be a whole 32-bit packed color value.");
        }
        return (int)(long)number;
    }

}
