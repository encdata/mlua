package dev.mlua.minecraft.binding;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.minecraft.animation.MLuaAnimationTrack;
import dev.mlua.minecraft.animation.MLuaAnimationController;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.engine.MLuaLuaBindings;
import java.util.List;
import net.minecraft.world.entity.Entity;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Lua bindings for an entity's Java-style Animator controller. */
final class MLuaAnimatorBindings {
    private MLuaAnimatorBindings() { }

    /** Creates the Java-style per-entity animation controller exposed by Entity:GetAnimator(). */
    static LuaTable wrapAnimator(LuaTable entity, MLuaRegistration registration) {
        LuaValue cached = entity.rawget("__mlua_animator");
        if (cached.istable() && registration.isInstanceOf(cached, "Animator")) return cached.checktable();
        LuaTable fields = new LuaTable();
        fields.rawset("__entity", entity.rawget("__entity"));
        LuaTable animator = createClassInstance(registration, "Animator", fields);
        bindForwarder(animator, entity, "LoadAnimation");
        bindForwarder(animator, entity, "GetPlayingAnimationTracks");
        bindForwarder(animator, entity, "GetAnimationTracks");
        bindForwarder(animator, entity, "GetTracksByPriority");
        bindForwarder(animator, entity, "SetBonePose");
        bindForwarder(animator, entity, "GetBonePose");
        MLuaClassMethods.setInstanceMethod(animator, "StopAll", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                var owner = MLuaAnimationBindings.requireRenderOwner(registration, "Animator:StopAll");
                LuaValue entityValue = entity.rawget("__entity");
                if (!entityValue.isuserdata(Entity.class)) {
                    throw new LuaError("[MLua] Animator:StopAll requires its live entity instance.");
                }
                List<MLuaAnimationTrack> tracks = MLuaAnimationController.getTracks(
                    MLuaEntityScope.of((Entity) entityValue.checkuserdata(Entity.class)),
                    owner.runtimeOwner(), owner.ownerModId());
                float fadeTime = args.arg(2).isnil() ? -1.0f
                    : MLuaLuaArguments.finiteNonNegativeFloat(args.arg(2), "Animator:StopAll", "fadeTime");
                for (MLuaAnimationTrack track : tracks) {
                    if (fadeTime < 0.0f) track.stop();
                    else track.stop(fadeTime);
                }
                return LuaValue.NONE;
            }
        });
        MLuaClassMethods.setInstanceMethod(animator, "UnloadAnimationTrack", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue trackValue = args.arg(2);
                if (!trackValue.istable() || !registration.isInstanceOf(trackValue, "AnimationTrack")) {
                    throw new LuaError("[MLua] Animator:UnloadAnimationTrack expects an AnimationTrack instance.");
                }
                LuaValue idValue = trackValue.checktable().rawget("__trackId");
                if (!idValue.isint() || idValue.toint() <= 0) {
                    throw new LuaError("[MLua] Animator:UnloadAnimationTrack received an invalid track handle.");
                }
                LuaValue entityValue = entity.rawget("__entity");
                if (!entityValue.isuserdata(Entity.class)) {
                    throw new LuaError("[MLua] Animator:UnloadAnimationTrack requires its live entity instance.");
                }
                MLuaContext ownerContext = requireAnimationOwner(registration, "Animator:UnloadAnimationTrack");
                boolean removed = MLuaAnimationController.unloadTrack(
                    MLuaEntityScope.of((Entity) entityValue.checkuserdata(Entity.class)), idValue.toint(), MLuaAnimationBindings.runtimeSession(registration),
                    ownerContext.ownerModId());
                return LuaValue.valueOf(removed);
            }
        });
        MLuaClassMethods.setInstanceMethod(animator, "GetEntity", new ZeroArgFunction() {
            @Override public LuaValue call() { return entity; }
        });
        MLuaClassMethods.setInstanceMethod(animator, "ClearPose", new ZeroArgFunction() {
            @Override public LuaValue call() {
                LuaValue clear = entity.get("ClearBonePose");
                if (!clear.isfunction()) throw new LuaError("[MLua] Entity pose methods are unavailable.");
                clear.invoke(LuaValue.varargsOf(new LuaValue[]{entity}));
                return LuaValue.NONE;
            }
        });
        entity.rawset("__mlua_animator", animator);
        return animator;
    }

    private static void bindForwarder(LuaTable animator, LuaTable entity, String methodName) {
        MLuaClassMethods.setInstanceMethod(animator, methodName, new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue method = entity.get(methodName);
                if (!method.isfunction()) throw new LuaError("[MLua] Entity:" + methodName + " is unavailable.");
                LuaValue[] forwarded = new LuaValue[args.narg()];
                forwarded[0] = entity;
                for (int index = 1; index < forwarded.length; index++) forwarded[index] = args.arg(index + 1);
                return method.invoke(LuaValue.varargsOf(forwarded));
            }
        });
    }

    private static MLuaContext requireAnimationOwner(MLuaRegistration registration, String method) {
        MLuaContext context = MLuaLuaBindings.activeContext(registration);
        if (context == null) throw new LuaError("[MLua] " + method
            + " must be called while an owning MLua script context is active so its state can be cleaned up correctly.");
        return context;
    }

    private static LuaTable createClassInstance(MLuaRegistration registration, String className, LuaTable fields) {
        if (registration == null) throw new IllegalStateException("Lua registration is required for " + className + " instances");
        return registration.createClassInstance(className, fields);
    }
}
