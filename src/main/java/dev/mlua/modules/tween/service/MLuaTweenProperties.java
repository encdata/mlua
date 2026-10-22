package dev.mlua.modules.tween.service;

import dev.mlua.core.engine.MLuaLuauTypes;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.math.MLuaVector;
import dev.mlua.module.MLuaRegistration;
import java.util.ArrayList;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Validates tween targets and captures their writable property endpoints once at creation. */
final class MLuaTweenProperties {
    static final int MAX_PROPERTIES_PER_TWEEN = 32;

    private MLuaTweenProperties() { }

    static List<MLuaTween.Property> resolve(LuaValue targetValue, LuaTable target, LuaTable goals,
                                            MLuaRegistration registration) {
        boolean classInstance = registration.classNameOf(targetValue) != null;
        LuaValue metatable = target.getmetatable();
        boolean serviceInstance = metatable != null && metatable.istable()
            && metatable.checktable().rawget("__mlua_service_name").isstring();
        if (!classInstance && !serviceInstance) {
            throw new LuaError("[MLua] TweenService:Create only accepts registered MLua instances and services.");
        }

        List<MLuaTween.Property> properties = new ArrayList<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs entry = goals.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            if (!key.isstring() || key.tojstring().isBlank()) {
                throw new LuaError("[MLua] Tween goal keys must be property names.");
            }
            if (properties.size() >= MAX_PROPERTIES_PER_TWEEN) {
                throw new LuaError("[MLua] A tween can change at most " + MAX_PROPERTIES_PER_TWEEN + " properties.");
            }
            String property = key.tojstring();
            LuaValue getter = target.get("Get" + property);
            LuaValue setter = target.get("Set" + property);
            if (!getter.isfunction() || !setter.isfunction()) {
                throw new LuaError("[MLua] Tween target has no Get" + property + "/Set" + property + " property pair.");
            }
            LuaValue goal = entry.arg(2);
            validateGoal(goal);
            properties.add(new MLuaTween.Property(property, getter, setter, goal));
        }
        if (properties.isEmpty()) throw new LuaError("[MLua] TweenService:Create needs at least one goal property.");
        return properties;
    }

    private static void validateGoal(LuaValue goal) {
        if (goal.isnumber() && !Double.isFinite(goal.todouble())) {
            throw new LuaError("[MLua] Tween numeric goals must be finite.");
        }
        if (!goal.isnumber() && !MLuaVector.isVector2(goal) && !MLuaVector.isVector3(goal)
                && !MLuaCFrame.isCFrame(goal) && !MLuaLuauTypes.isColor3(goal)) {
            throw new LuaError("[MLua] Tween goals support numbers and native Vector2, Vector3, Color3, or CFrame values.");
        }
    }
}
