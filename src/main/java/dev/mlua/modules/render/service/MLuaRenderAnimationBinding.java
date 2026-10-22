package dev.mlua.modules.render.service;

import dev.mlua.minecraft.animation.MLuaAnimationOptions;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.runtime.client.MLuaClientRenderManager;
import dev.mlua.runtime.client.model.MLuaBBModelAsset;
import java.util.function.Function;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Installs the animation operations exposed by a loaded Blockbench RenderObject. */
final class MLuaRenderAnimationBinding {
    private MLuaRenderAnimationBinding() { }

    static void register(LuaTable operations, MLuaClientRenderManager.RenderObject object,
            MLuaBBModelAsset asset) {
        operations.set("GetAnimations", fn(args -> {
            LuaTable animations = new LuaTable();
            int index = 1;
            for (String name : asset.animations().keySet()) animations.set(index++, name);
            return animations;
        }));
        operations.set("GetAnimationName", fn(args -> {
            synchronized (object.executionLock()) {
                String name = object.animationPlayer().active();
                return name == null ? LuaValue.NIL : LuaValue.valueOf(name);
            }
        }));
        operations.set("IsAnimationPlaying", fn(args -> {
            synchronized (object.executionLock()) {
                return LuaValue.valueOf(object.animationPlayer().playing());
            }
        }));
        operations.set("IsAnimationPaused", fn(args -> {
            synchronized (object.executionLock()) {
                return LuaValue.valueOf(object.animationPlayer().paused());
            }
        }));
        operations.set("GetAnimationSpeed", fn(args -> {
            synchronized (object.executionLock()) {
                return LuaValue.valueOf(object.animationPlayer().speed());
            }
        }));
        operations.set("PlayAnimation", fn(args -> {
            String name = args.checkjstring(2);
            MLuaAnimationOptions options = MLuaAnimationOptions.fromLua(args.arg(3), "RenderObject:PlayAnimation");
            synchronized (object.executionLock()) {
                requireLive(object);
                object.animationPlayer().play(name, asset, options.loop(), options.speed());
            }
            return LuaValue.NONE;
        }));
        operations.set("PauseAnimation", fn(args -> {
            synchronized (object.executionLock()) { object.animationPlayer().pause(); }
            return LuaValue.NONE;
        }));
        operations.set("ResumeAnimation", fn(args -> {
            synchronized (object.executionLock()) { object.animationPlayer().resume(); }
            return LuaValue.NONE;
        }));
        operations.set("SetAnimationTime", fn(args -> {
            float time = MLuaLuaArguments.finiteNonNegativeFloat(
                args.arg(2), "RenderObject:SetAnimationTime", "time");
            synchronized (object.executionLock()) {
                requireLive(object);
                try {
                    object.animationPlayer().setTimePosition(time, asset);
                } catch (IllegalArgumentException | IllegalStateException failure) {
                    throw new LuaError("[MLua] RenderObject:SetAnimationTime " + failure.getMessage());
                }
            }
            return LuaValue.NONE;
        }));
        operations.set("SetAnimationSpeed", fn(args -> {
            float speed = MLuaLuaArguments.finiteNonNegativeFloat(
                args.arg(2), "RenderObject:SetAnimationSpeed", "speed");
            synchronized (object.executionLock()) {
                requireLive(object);
                try {
                    object.animationPlayer().setSpeed(speed);
                } catch (IllegalArgumentException | IllegalStateException failure) {
                    throw new LuaError("[MLua] RenderObject:SetAnimationSpeed " + failure.getMessage());
                }
            }
            return LuaValue.NONE;
        }));
        operations.set("StopAnimation", fn(args -> {
            synchronized (object.executionLock()) { object.animationPlayer().stop(); }
            return LuaValue.NONE;
        }));
        operations.set("GetAnimationTime", fn(args -> {
            synchronized (object.executionLock()) { return LuaValue.valueOf(object.animationPlayer().time()); }
        }));
    }

    private static void requireLive(MLuaClientRenderManager.RenderObject object) {
        if (object.destroyed()) throw new LuaError("[MLua] Render object no longer exists");
    }

    private static VarArgFunction fn(Function<Varargs, LuaValue> body) {
        return MLuaRenderLuaSupport.operation("Render animation operation", body);
    }
}
