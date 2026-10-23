package dev.mlua.minecraft.binding;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.common.enums.AnimationPriority;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaAnimation;
import dev.mlua.minecraft.animation.MLuaAnimationTrack;
import dev.mlua.minecraft.animation.MLuaAnimationController;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.minecraft.animation.MLuaAnimationOptions;
import dev.mlua.minecraft.animation.MLuaBonePose;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaContext;
import dev.mlua.runtime.client.MLuaClientRenderManager;
import dev.mlua.modules.enums.MLuaEnumValue;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

public final class MLuaAnimationBindings {

    private MLuaAnimationBindings() {}

    /** Binds animation and model controls for any entity with a registered model adapter. */
    public static void bindEntityMethods(LuaTable table, Entity entity, MLuaRegistration registration) {
        if (!entity.level().isClientSide()) {
            bindClientOnlyUnavailableMethods(table);
            return;
        }
        MLuaClassMethods.setInstanceMethod(table, "LoadAnimation", varArg(args -> {
            LuaValue animValue = args.arg(2);
            if (isRenderObject(animValue)) {
                attachModel(entity, animValue, registration, "Entity:LoadAnimation");
                return animValue;
            }

            MLuaAnimation animation = resolveAnimation(animValue, registration, "Entity:LoadAnimation");

            AnimationPriority priority = resolvePriority(args.arg(3), "Entity:LoadAnimation", AnimationPriority.Idle);
            float fadeTime = MLuaLuaArguments.optionalNonNegativeFiniteFloat(
                args, 4, 0.0f, "Entity:LoadAnimation", "fadeTime");
            String addonOwner = requireAnimationOwner(registration, "Entity:LoadAnimation").ownerModId();
            MLuaAnimationTrack track = MLuaAnimationController.loadAnimation(
                entity, animation, priority, runtimeSession(registration), addonOwner);
            if (fadeTime > 0.001f) {
                track.setFadeSpeed(1.0f / fadeTime);
                track.fadeTo(1.0f);
            }
            return wrapTrack(track, registration, table);
        }));
        MLuaClassMethods.setInstanceMethod(table, "AttachModel", varArg(args ->
            LuaValue.valueOf(attachModel(entity, args.arg(2), registration, "Entity:AttachModel"))));
        MLuaClassMethods.setInstanceMethod(table, "DetachModel", varArg(args ->
            LuaValue.valueOf(detachModel(entity, args.arg(2), registration, "Entity:DetachModel"))));
        MLuaClassMethods.setInstanceMethod(table, "ResetAnimation", luaFn(() -> {
            stopOwnedAnimations(entity, registration, "Entity:ResetAnimation");
            return LuaValue.NONE;
        }));
        MLuaClassMethods.setInstanceMethod(table, "LoadModel", table.get("LoadAnimation"));
        MLuaClassMethods.setInstanceMethod(table, "ResetModel", varArg(args -> {
            var owner = requireRenderOwner(registration, "Entity:ResetModel");
            MLuaAnimationController.stopAll(MLuaEntityScope.of(entity), owner.runtimeOwner(), owner.ownerModId());
            MLuaClientRenderManager.resetEntityModel(MLuaEntityScope.of(entity), owner);
            return LuaValue.NONE;
        }));
        MLuaClassMethods.setInstanceMethod(table, "PlayAnimation", varArg(args -> {
            String name = args.checkjstring(2);
            MLuaAnimationOptions options = MLuaAnimationOptions.fromLua(args.arg(3), "Entity:PlayAnimation");
            MLuaClientRenderManager.playEntityAnimation(entity, name, options.loop(), options.speed());
            return LuaValue.NONE;
        }));
        MLuaClassMethods.setInstanceMethod(table, "StopAnimation", luaFn(() -> {
            MLuaClientRenderManager.stopEntityAnimation(entity);
            return LuaValue.NONE;
        }));
        MLuaClassMethods.setInstanceMethod(table, "GetPlayingAnimationTracks", luaFn(() -> {
            LuaTable tracks = new LuaTable();
            int index = 1;
            for (MLuaAnimationTrack track : MLuaAnimationController.getTracks(entity, runtimeSession(registration))) {
                if (track.isPlaying()) tracks.set(index++, wrapTrack(track, registration, table));
            }
            return tracks;
        }));
        MLuaClassMethods.setInstanceMethod(table, "GetTracksByPriority", varArg(args -> {
            AnimationPriority priority = resolvePriority(args.arg(2), "Entity:GetTracksByPriority", null);
            LuaTable tracks = new LuaTable();
            int index = 1;
            for (MLuaAnimationTrack track : MLuaAnimationController.getTracks(entity, runtimeSession(registration))) {
                if (track.getPriority() == priority) tracks.set(index++, wrapTrack(track, registration, table));
            }
            return tracks;
        }));
        MLuaClassMethods.setInstanceMethod(table, "GetAnimationTracks", luaFn(() -> {
            LuaTable tracks = new LuaTable();
            int index = 1;
            for (MLuaAnimationTrack track : MLuaAnimationController.getTracks(entity, runtimeSession(registration))) {
                tracks.set(index++, wrapTrack(track, registration, table));
            }
            return tracks;
        }));
        MLuaClassMethods.setInstanceMethod(table, "StopAllAnimations", luaFn(() -> {
            stopOwnedAnimations(entity, registration, "Entity:StopAllAnimations");
            return LuaValue.NONE;
        }));
        MLuaClassMethods.setInstanceMethod(table, "GetAnimator", luaFn(() -> {
            if (registration.classTable("Animator") == null) {
                throw new LuaError("[MLua] Entity:GetAnimator is available in client scripts only.");
            }
            return wrapAnimator(table, registration);
        }));
        MLuaClassMethods.setInstanceMethod(table, "SetBonePose", varArg(args -> {
            MLuaBonePose.setPose(MLuaEntityScope.of(entity), args.checkjstring(2),
                MLuaCFrame.fromLua(args.arg(3)), runtimeSession(registration),
                requireAnimationOwner(registration, "Entity:SetBonePose").ownerModId());
            return LuaValue.NONE;
        }));
        MLuaClassMethods.setInstanceMethod(table, "GetBonePose", varArg(args -> {
            MLuaCFrame pose = MLuaBonePose.getBonePose(entity, args.checkjstring(2), runtimeSession(registration));
            return pose == null ? LuaValue.NIL : pose.toLua();
        }));
        MLuaClassMethods.setInstanceMethod(table, "GetBonePoses", luaFn(() -> {
            LuaTable poses = new LuaTable();
            for (Map.Entry<String, MLuaCFrame> entry : MLuaBonePose.getPose(entity, runtimeSession(registration)).entrySet()) {
                poses.set(entry.getKey(), entry.getValue().toLua());
            }
            return poses;
        }));
        MLuaClassMethods.setInstanceMethod(table, "ClearBonePose", varArg(args -> {
            String addonOwner = requireAnimationOwner(registration, "Entity:ClearBonePose").ownerModId();
            if (args.narg() >= 2 && !args.arg(2).isnil()) {
                MLuaBonePose.clearBonePose(MLuaEntityScope.of(entity), args.checkjstring(2),
                    runtimeSession(registration), addonOwner);
            } else {
                MLuaBonePose.clearPose(MLuaEntityScope.of(entity), runtimeSession(registration), addonOwner);
            }
            return LuaValue.NONE;
        }));
    }

    /** Keep server entity wrappers usable; fail only when a client render method is actually called. */
    private static void bindClientOnlyUnavailableMethods(LuaTable table) {
        String[] methods = {
            "LoadAnimation", "AttachModel", "DetachModel", "ResetAnimation", "LoadModel", "ResetModel",
            "PlayAnimation", "StopAnimation", "GetPlayingAnimationTracks", "GetTracksByPriority",
            "GetAnimationTracks", "StopAllAnimations", "GetAnimator", "SetBonePose", "GetBonePose",
            "GetBonePoses", "ClearBonePose"
        };
        for (String method : methods) {
            MLuaClassMethods.setInstanceMethod(table, method, varArg(args -> {
                throw new LuaError("[MLua] Entity:" + method
                    + " is client-side only and requires an entity from the client world.");
            }));
        }
    }

    /** Resolves an Animation instance without round-tripping its immutable asset through Lua tables. */
    public static MLuaAnimation resolveAnimation(LuaValue value, MLuaRegistration registration, String caller) {
        if (!value.istable()) {
            throw new LuaError("[MLua] " + caller + " expects an Animation or keyframe table");
        }
        if (registration.isInstanceOf(value, "Animation")) {
            LuaValue stored = value.rawget("__mlua_animation");
            if (stored.isuserdata(MLuaAnimation.class)) {
                return (MLuaAnimation) stored.checkuserdata(MLuaAnimation.class);
            }
            LuaValue getKeyframes = value.get("GetKeyframes");
            if (!getKeyframes.isfunction()) {
                throw new LuaError("[MLua] Invalid Animation object - missing GetKeyframes");
            }
            LuaValue keyframes = getKeyframes.call();
            if (!keyframes.istable()) {
                throw new LuaError("[MLua] Animation:GetKeyframes() must return a keyframe table");
            }
            return MLuaAnimation.fromLuaTable(keyframes.checktable());
        }
        if (registration.classNameOf(value) != null) {
            throw new LuaError("[MLua] " + caller + " expects an Animation or keyframe table");
        }
        return MLuaAnimation.fromLuaTable(value.checktable());
    }

    private static AnimationPriority resolvePriority(LuaValue value, String caller, AnimationPriority defaultValue) {
        if (value == null || value.isnil()) {
            if (defaultValue != null) return defaultValue;
            throw new LuaError("[MLua] " + caller + " requires an AnimationPriority value or name.");
        }
        if (value.istable()) {
            AnimationPriority priority = MLuaEnumValue.toJava(value, AnimationPriority.class);
            if (priority == null) throw new LuaError("[MLua] " + caller + " priority must be an AnimationPriority value.");
            return priority;
        }
        if (!value.isstring()) throw new LuaError("[MLua] " + caller + " priority must be an AnimationPriority value or name.");
        try {
            return AnimationPriority.valueOf(value.tojstring());
        } catch (IllegalArgumentException error) {
            throw new LuaError("[MLua] Unknown AnimationPriority: " + value.tojstring());
        }
    }

    private static boolean isRenderObject(LuaValue value) {
        if (!value.istable()) return false;
        LuaTable table = value.checktable();
        return table.rawget(dev.mlua.modules.render.service.MLuaRenderService.OBJECT_ID).isnumber()
            && table.rawget("__mluaRenderOwner").isuserdata(
                dev.mlua.runtime.client.MLuaRenderOwnershipRegistry.Owner.class);
    }

    private static boolean attachModel(Entity entity, LuaValue value, MLuaRegistration registration, String caller) {
        RenderObjectHandle model = renderObjectHandle(value, registration, caller);
        if (!MLuaClientRenderManager.attachToEntity(model.id(), entity, model.owner())) {
            throw new LuaError("[MLua] " + caller + " requires a live Mesh or BBModel RenderObject owned by this runtime.");
        }
        return true;
    }

    private static boolean detachModel(Entity entity, LuaValue value, MLuaRegistration registration, String caller) {
        RenderObjectHandle model = renderObjectHandle(value, registration, caller);
        return MLuaClientRenderManager.detachFromEntity(model.id(), entity, model.owner());
    }

    private static RenderObjectHandle renderObjectHandle(LuaValue value, MLuaRegistration registration, String caller) {
        if (!isRenderObject(value)) {
            throw new LuaError("[MLua] " + caller + " expects a live Mesh or BBModel RenderObject.");
        }
        LuaTable table = value.checktable();
        int id;
        try {
            id = table.rawget(dev.mlua.modules.render.service.MLuaRenderService.OBJECT_ID).checkint();
        } catch (RuntimeException invalid) {
            throw new LuaError("[MLua] " + caller + " received an invalid RenderObject handle.");
        }
        var owner = (dev.mlua.runtime.client.MLuaRenderOwnershipRegistry.Owner) table.rawget("__mluaRenderOwner").checkuserdata(
            dev.mlua.runtime.client.MLuaRenderOwnershipRegistry.Owner.class);
        Object runtimeOwner = runtimeSession(registration);
        if (runtimeOwner == null || owner.runtimeOwner() != runtimeOwner) {
            throw new LuaError("[MLua] " + caller + " cannot use a RenderObject from another runtime.");
        }
        return new RenderObjectHandle(id, owner);
    }

    private static void stopOwnedAnimations(Entity entity, MLuaRegistration registration, String caller) {
        var owner = requireRenderOwner(registration, caller);
        MLuaAnimationController.stopAll(MLuaEntityScope.of(entity), owner.runtimeOwner(), owner.ownerModId());
    }

    static dev.mlua.runtime.client.MLuaRenderOwnershipRegistry.Owner requireRenderOwner(
            MLuaRegistration registration, String caller) {
        MLuaContext context = requireAnimationOwner(registration, caller);
        Object runtimeOwner = runtimeSession(registration);
        if (runtimeOwner == null) {
            throw new LuaError("[MLua] " + caller + " requires an active MLua runtime session.");
        }
        return new dev.mlua.runtime.client.MLuaRenderOwnershipRegistry.Owner(runtimeOwner, context.ownerModId());
    }

    static MLuaContext requireAnimationOwner(MLuaRegistration registration, String caller) {
        return MLuaAnimationInstances.requireAnimationOwner(registration, caller);
    }

    static Object runtimeSession(MLuaRegistration registration) {
        return registration instanceof MLuaRuntime runtime ? runtime.session() : null;
    }

    private record RenderObjectHandle(int id, dev.mlua.runtime.client.MLuaRenderOwnershipRegistry.Owner owner) { }

    private static LuaValue luaFn(Supplier<LuaValue> function) {
        return new ZeroArgFunction() {
            @Override public LuaValue call() { return function.get(); }
        };
    }

    private static LuaValue varArg(Function<Varargs, LuaValue> function) {
        return new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return function.apply(args); }
        };
    }

    public static void ensureRegisteredClasses(MLuaRegistration registration) {
        MLuaAnimationClassRegistration.ensureRegisteredClasses(registration);
    }

    public static void registerClasses(MLuaRegistration registration) {
        MLuaAnimationClassRegistration.registerClasses(registration);
    }

    public static LuaTable createAnimationTable(MLuaRegistration registration) {
        return MLuaAnimationInstances.createAnimationTable(registration);
    }

    public static LuaTable wrapAnimation(MLuaAnimation animation, MLuaRegistration registration) {
        return MLuaAnimationInstances.wrapAnimation(animation, registration);
    }

    public static LuaTable wrapAnimator(LuaTable entity, MLuaRegistration registration) {
        return MLuaAnimationInstances.wrapAnimator(entity, registration);
    }

    public static LuaTable wrapTrack(MLuaAnimationTrack track, MLuaRegistration registration) {
        return MLuaAnimationInstances.wrapTrack(track, registration);
    }

    public static LuaTable wrapTrack(MLuaAnimationTrack track, MLuaRegistration registration, LuaTable entity) {
        return MLuaAnimationInstances.wrapTrack(track, registration, entity);
    }

}
