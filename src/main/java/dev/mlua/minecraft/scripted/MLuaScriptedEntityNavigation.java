package dev.mlua.minecraft.scripted;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.Varargs;
import dev.mlua.minecraft.binding.MLuaLuaArguments;

/** Shared argument rules for scripted entity navigation across Minecraft versions. */
final class MLuaScriptedEntityNavigation {
    private MLuaScriptedEntityNavigation() { }

    static Request readRequest(Varargs args, String method) {
        double x;
        double y;
        double z;
        int speedIndex;
        if (args.arg(2).isnumber()) {
            x = coordinate(args, 2, method, "x");
            y = coordinate(args, 3, method, "y");
            z = coordinate(args, 4, method, "z");
            speedIndex = 5;
        } else {
            double[] position = MLuaLuaArguments.finiteVector3(
                args.arg(2), "ScriptedEntity:" + method + " position");
            x = position[0];
            y = position[1];
            z = position[2];
            speedIndex = 3;
        }

        double speed = MLuaLuaArguments.optionalFiniteNumber(
            args, speedIndex, 1.0, "ScriptedEntity:" + method, "speed");
        if (speed <= 0.0) {
            throw new LuaError("[MLua] ScriptedEntity:" + method + " speed must be greater than zero.");
        }
        return new Request(x, y, z, speed);
    }

    private static double coordinate(Varargs args, int index, String method, String name) {
        var value = args.arg(index);
        if (!value.isnumber() || !Double.isFinite(value.todouble())) {
            throw new LuaError("[MLua] ScriptedEntity:" + method + " " + name + " must be finite.");
        }
        return value.todouble();
    }

    record Request(double x, double y, double z, double speed) { }
}
