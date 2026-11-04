package dev.mlua.minecraft.binding;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaAnimationTrack;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.modules.enums.MLuaEnumValue;
import java.util.Map;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Lua bindings and identity cache for live animation tracks. */
final class MLuaAnimationTrackBindings {
    private static final Map<MLuaRegistration, Map<Integer, WeakReference<LuaTable>>> TRACK_WRAPPERS = new WeakHashMap<>();

    private MLuaAnimationTrackBindings() { }

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
            @Override public LuaValue call() { return MLuaAnimatorBindings.wrapAnimator(entity, registration); }
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
                return MLuaAnimationAssetBindings.wrapAnimation(track.animation(), registration);
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

    static MLuaContext requireAnimationOwner(MLuaRegistration registration, String method) {
        MLuaContext context = MLuaLuaBindings.activeContext(registration);
        if (context == null) {
            throw new LuaError("[MLua] " + method
                + " must be called while an owning MLua script context is active so its state can be cleaned up correctly.");
        }
        return context;
    }

    private static MLuaContext requireAnimationEventContext(MLuaRegistration registration, String method) {
        return requireAnimationOwner(registration, method);
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
