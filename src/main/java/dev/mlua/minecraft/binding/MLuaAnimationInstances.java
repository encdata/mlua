package dev.mlua.minecraft.binding;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.common.enums.AnimationPriority;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaAnimation;
import dev.mlua.minecraft.animation.MLuaAnimationTrack;
import dev.mlua.minecraft.animation.MLuaAnimationController;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.modules.enums.MLuaEnumValue;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;
import net.minecraft.world.entity.Entity;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Owns cached Lua instances for immutable animations, per-entity animators, and live tracks. */
final class MLuaAnimationInstances {
    private static final Map<MLuaRegistration, Map<MLuaAnimation, WeakReference<LuaTable>>> ANIMATION_WRAPPERS = new WeakHashMap<>();
    private static final Map<MLuaRegistration, Map<Integer, WeakReference<LuaTable>>> TRACK_WRAPPERS = new WeakHashMap<>();

    private MLuaAnimationInstances() { }

    static LuaTable createAnimationTable(MLuaRegistration registration) {
        java.util.Objects.requireNonNull(registration, "registration");
        LuaTable animTable = new LuaTable();
        LuaTable animationClass = registration.classTable("Animation");
        LuaTable trackClass = registration.classTable("AnimationTrack");
        LuaTable animatorClass = registration.classTable("Animator");
        if (animationClass == null || trackClass == null || animatorClass == null) {
            throw new IllegalStateException("Animation classes must be registered before creating the Animation library table");
        }
        animTable.set("Animation", animationClass);
        animTable.set("AnimationTrack", trackClass);
        animTable.set("Animator", animatorClass);
        animTable.set("new", animationClass.get("new"));

        animTable.set("AnimationPriority", MLuaEnumValue.enumConstants(AnimationPriority.class, null));
        animTable.set("EasingStyle", MLuaEnumValue.enumConstants(dev.mlua.common.enums.EasingStyle.class, null));
        animTable.set("EasingDirection", MLuaEnumValue.enumConstants(dev.mlua.common.enums.EasingDirection.class, null));

        return animTable;
    }

    static LuaTable wrapAnimation(MLuaAnimation animation, MLuaRegistration registration) {
        synchronized (ANIMATION_WRAPPERS) {
            Map<MLuaAnimation, WeakReference<LuaTable>> wrappers = ANIMATION_WRAPPERS.computeIfAbsent(
                registration, ignored -> new WeakHashMap<>());
            wrappers.entrySet().removeIf(entry -> entry.getValue().get() == null);
            WeakReference<LuaTable> existingReference = wrappers.get(animation);
            LuaTable existing = existingReference == null ? null : existingReference.get();
            if (existing != null && registration.isInstanceOf(existing, "Animation")) return existing;
            LuaTable created = createAnimationInstance(animation, registration);
            wrappers.put(animation, new WeakReference<>(created));
            return created;
        }
    }

    private static LuaTable createAnimationInstance(MLuaAnimation animation, MLuaRegistration registration) {
        LuaTable table = createClassInstance(registration, "Animation", new LuaTable());
        table.rawset("__mlua_animation", LuaValue.userdataOf(animation));
        MLuaClassMethods.setInstanceMethod(table, "GetLength", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(animation.getLength());
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "GetKeyframes", new ZeroArgFunction() {
            public LuaValue call() {
                LuaTable kfTable = new LuaTable();
                int i = 1;
                for (MLuaAnimation.Keyframe kf : animation.getKeyframes()) {
                    LuaTable kfEntry = new LuaTable();
                    kfEntry.set("Time", LuaValue.valueOf(kf.time));
                    kfEntry.set("EasingStyle", MLuaEnumValue.forJava(kf.easingStyle));
                    kfEntry.set("EasingDirection", MLuaEnumValue.forJava(kf.easingDirection));
                    LuaTable dataTable = new LuaTable();
                    for (Map.Entry<String, MLuaCFrame> entry : kf.data.entrySet()) {
                        dataTable.set(entry.getKey(), entry.getValue().toLua());
                    }
                    kfEntry.set("Data", dataTable);
                    kfTable.set(i++, kfEntry);
                }
                return kfTable;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "GetKeyframeCount", new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.valueOf(animation.getKeyframeCount()); }
        });
        MLuaClassMethods.setInstanceMethod(table, "GetBoneNames", new ZeroArgFunction() {
            @Override public LuaValue call() {
                LuaTable names = new LuaTable();
                int index = 1;
                for (String name : animation.getBoneNames()) names.set(index++, LuaValue.valueOf(name));
                return names;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "HasBone", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(animation.hasBone(args.checkjstring(2)));
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "Sample", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                float time = MLuaLuaArguments.finiteNonNegativeFloat(args.arg(2), "Animation:Sample", "time");
                LuaTable pose = new LuaTable();
                animation.sample(time).forEach((bone, transform) -> pose.set(bone, transform.toLua()));
                return pose;
            }
        });
        return table;
    }

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

    static LuaTable wrapTrack(MLuaAnimationTrack track, MLuaRegistration registration) {
        return wrapTrack(track, registration, null);
    }

    static LuaTable wrapTrack(MLuaAnimationTrack track, MLuaRegistration registration, LuaTable entity) {
        synchronized (TRACK_WRAPPERS) {
            Map<Integer, WeakReference<LuaTable>> wrappers = TRACK_WRAPPERS.computeIfAbsent(
                registration, ignored -> new HashMap<>());
            wrappers.entrySet().removeIf(entry -> entry.getValue().get() == null);
            WeakReference<LuaTable> existingReference = wrappers.get(track.trackId());
            LuaTable existing = existingReference == null ? null : existingReference.get();
            if (existing != null && registration.isInstanceOf(existing, "AnimationTrack")) {
                bindTrackOwner(existing, entity, registration);
                return existing;
            }
            LuaTable created = createTrackInstance(track, registration);
            bindTrackOwner(created, entity, registration);
            wrappers.put(track.trackId(), new WeakReference<>(created));
            return created;
        }
    }

    private static void bindTrackOwner(LuaTable track, LuaTable entity, MLuaRegistration registration) {
        if (entity == null || track.rawget("__mlua_entity_bound").toboolean()) return;
        track.rawset("__mlua_entity_bound", LuaValue.TRUE);
        MLuaClassMethods.setInstanceMethod(track, "GetEntity", new ZeroArgFunction() {
            @Override public LuaValue call() { return entity; }
        });
        MLuaClassMethods.setInstanceMethod(track, "GetAnimator", new ZeroArgFunction() {
            @Override public LuaValue call() { return wrapAnimator(entity, registration); }
        });
    }

    private static LuaTable createTrackInstance(MLuaAnimationTrack track, MLuaRegistration registration) {
        LuaTable fields = new LuaTable();
        fields.set("__trackId", track.trackId());
        fields.set("__entityId", track.entityId());
        LuaTable table = createClassInstance(registration, "AnimationTrack", fields);

        MLuaClassMethods.setInstanceMethod(table, "Play", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                requireTrackOwner(track, registration, "AnimationTrack:Play");
                if (args.arg(2).isnil()) {
                    track.play();
                } else {
                    track.play(MLuaLuaArguments.finiteNonNegativeFloat(
                        args.arg(2), "AnimationTrack:Play", "fadeTime"));
                }
                return LuaValue.NONE;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "Stop", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                requireTrackOwner(track, registration, "AnimationTrack:Stop");
                if (args.arg(2).isnil()) {
                    track.stop();
                } else {
                    track.stop(MLuaLuaArguments.finiteNonNegativeFloat(
                        args.arg(2), "AnimationTrack:Stop", "fadeTime"));
                }
                return LuaValue.NONE;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "Pause", new ZeroArgFunction() {
            public LuaValue call() {
                requireTrackOwner(track, registration, "AnimationTrack:Pause");
                track.pause();
                return LuaValue.NONE;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "Resume", new ZeroArgFunction() {
            public LuaValue call() {
                requireTrackOwner(track, registration, "AnimationTrack:Resume");
                track.resume();
                return LuaValue.NONE;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "AdjustSpeed", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                requireTrackOwner(track, registration, "AnimationTrack:AdjustSpeed");
                track.adjustSpeed(MLuaLuaArguments.finiteNonNegativeFloat(
                    args.arg(2), "AnimationTrack:AdjustSpeed", "speed"));
                return LuaValue.NONE;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "GetTimePosition", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(track.getTime());
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "SetTimePosition", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                requireTrackOwner(track, registration, "AnimationTrack:SetTimePosition");
                track.setTimePosition(MLuaLuaArguments.finiteNonNegativeFloat(
                    args.arg(2), "AnimationTrack:SetTimePosition", "timePosition"));
                return LuaValue.NONE;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "GetTrackId", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(track.trackId());
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "GetPriority", new ZeroArgFunction() {
            public LuaValue call() {
                String name = track.getPriority().name();
                LuaValue priorityEnum = MLuaEnumValue.forJava(track.getPriority());
                return priorityEnum != null ? priorityEnum : LuaValue.valueOf(name);
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "GetLength", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(track.getLength());
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "GetSpeed", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(track.getSpeed());
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "IsPlaying", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(track.isPlaying());
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "IsPaused", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(track.isPaused());
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "IsAlive", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(track.isAlive());
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "IsReleased", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(track.isReleased());
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "IsFading", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(track.isFading());
            }
        });

        MLuaClassMethods.setInstanceMethod(table, "GetLooped", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(track.isLooped());
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "SetLooped", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                requireTrackOwner(track, registration, "AnimationTrack:SetLooped");
                track.setLooped(MLuaLuaArguments.optionalBoolean(
                    args, 2, false, "AnimationTrack:SetLooped", "looped"));
                return LuaValue.NONE;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "GetHoldOnEnd", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(track.holdOnEnd());
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "SetHoldOnEnd", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                requireTrackOwner(track, registration, "AnimationTrack:SetHoldOnEnd");
                track.setHoldOnEnd(MLuaLuaArguments.optionalBoolean(
                    args, 2, true, "AnimationTrack:SetHoldOnEnd", "holdOnEnd"));
                return LuaValue.NONE;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "GetWeight", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(track.getWeight());
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "GetTargetWeight", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(track.getTargetWeight());
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "SetWeight", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                requireTrackOwner(track, registration, "AnimationTrack:SetWeight");
                float weight = MLuaLuaArguments.unitIntervalFloat(
                    args, 2, "AnimationTrack:SetWeight", "weight");
                if (args.arg(3).isnil()) {
                    track.fadeTo(weight);
                } else {
                    track.fadeTo(weight, MLuaLuaArguments.finiteNonNegativeFloat(
                        args.arg(3), "AnimationTrack:SetWeight", "fadeTime"));
                }
                return LuaValue.NONE;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "AdjustWeight", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                requireTrackOwner(track, registration, "AnimationTrack:AdjustWeight");
                float weight = MLuaLuaArguments.unitIntervalFloat(
                    args, 2, "AnimationTrack:AdjustWeight", "weight");
                float fadeTime = MLuaLuaArguments.finiteNonNegativeFloat(
                    args.arg(3), "AnimationTrack:AdjustWeight", "fadeTime");
                track.fadeTo(weight, fadeTime);
                return LuaValue.NONE;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "AddEvent", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                requireTrackOwner(track, registration, "AnimationTrack:AddEvent");
                float triggerTime = MLuaLuaArguments.finiteNonNegativeFloat(
                    args.arg(2), "AnimationTrack:AddEvent", "time");
                LuaValue callback = args.checkfunction(3);
                MLuaContext eventContext = requireAnimationEventContext(registration, "AnimationTrack:AddEvent");
                try {
                    track.addEvent(triggerTime, new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs eventArgs) {
                            return invokeAnimationEvent(eventContext, callback,
                                LuaValue.varargsOf(new LuaValue[]{table, eventArgs.arg(2)}));
                        }
                    });
                } catch (IllegalArgumentException | IllegalStateException error) {
                    throw new LuaError("[MLua] AnimationTrack:AddEvent: " + error.getMessage());
                }
                return LuaValue.NONE;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "AddMarker", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                requireTrackOwner(track, registration, "AnimationTrack:AddMarker");
                String marker = args.checkjstring(2).trim();
                if (marker.isEmpty()) throw new LuaError("[MLua] AnimationTrack:AddMarker name cannot be empty.");
                float triggerTime = MLuaLuaArguments.finiteNonNegativeFloat(
                    args.arg(3), "AnimationTrack:AddMarker", "time");
                LuaValue callback = args.checkfunction(4);
                MLuaContext eventContext = requireAnimationEventContext(registration, "AnimationTrack:AddMarker");
                try {
                    track.addEvent(triggerTime, new VarArgFunction() {
                        @Override public Varargs invoke(Varargs eventArgs) {
                            return invokeAnimationEvent(eventContext, callback,
                                LuaValue.varargsOf(new LuaValue[]{table, LuaValue.valueOf(marker), eventArgs.arg(2)}));
                        }
                    });
                } catch (IllegalArgumentException | IllegalStateException error) {
                    throw new LuaError("[MLua] AnimationTrack:AddMarker: " + error.getMessage());
                }
                return LuaValue.NONE;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "ClearEvents", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                requireTrackOwner(track, registration, "AnimationTrack:ClearEvents");
                track.clearEvents();
                return LuaValue.NONE;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "GetAnimation", new ZeroArgFunction() {
            public LuaValue call() {
                return wrapAnimation(track.animation(), registration);
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "GetPose", new ZeroArgFunction() {
            @Override public LuaValue call() {
                LuaTable pose = new LuaTable();
                track.getComputedPose().forEach((bone, transform) -> pose.set(bone, transform.toLua()));
                return pose;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "GetBonePose", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                MLuaCFrame pose = track.getBonePose(args.checkjstring(2));
                return pose == null ? LuaValue.NIL : pose.toLua();
            }
        });

        return table;
    }

    private static MLuaContext requireAnimationEventContext(MLuaRegistration registration, String method) {
        return requireAnimationOwner(registration, method);
    }

    private static MLuaContext requireAnimationOwner(MLuaRegistration registration, String method) {
        MLuaContext context = MLuaLuaBindings.activeContext(registration);
        if (context == null) {
            throw new LuaError("[MLua] " + method
                + " must be called while an owning MLua script context is active so its state can be cleaned up correctly.");
        }
        return context;
    }

    private static void requireTrackOwner(MLuaAnimationTrack track, MLuaRegistration registration, String method) {
        MLuaContext context = requireAnimationOwner(registration, method);
        Object runtimeOwner = MLuaAnimationBindings.runtimeSession(registration);
        if (runtimeOwner == null || !track.isOwnedBy(runtimeOwner, context.ownerModId())) {
            throw new LuaError("[MLua] " + method + " can only change a track owned by the calling addon.");
        }
    }

    private static Varargs invokeAnimationEvent(MLuaContext context, LuaValue callback, Varargs args) {
        return MLuaLuaBindings.withContext(context,
            () -> MLuaCallbackBudget.run(context, () -> callback.invoke(args)));
    }

    private static LuaTable createClassInstance(MLuaRegistration registration, String className, LuaTable fields) {
        if (registration == null) throw new IllegalStateException("Lua registration is required for " + className + " instances");
        return registration.createClassInstance(className, fields);
    }
}
