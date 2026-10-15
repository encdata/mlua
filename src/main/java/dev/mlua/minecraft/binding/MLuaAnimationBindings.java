package dev.mlua.minecraft.binding;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.common.enums.AnimationPriority;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaAnimation;
import dev.mlua.minecraft.animation.MLuaAnimationTrack;
import dev.mlua.minecraft.animation.MLuaAnimationController;
import dev.mlua.modules.enums.MLuaEnumValue;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

public final class MLuaAnimationBindings {

    private MLuaAnimationBindings() {}

    public static void ensureRegisteredClasses(MLuaRegistration registration) {
        java.util.Objects.requireNonNull(registration, "registration");
        boolean missingAnimation = registration.classTable("Animation") == null;
        boolean missingTrack = registration.classTable("AnimationTrack") == null;
        if (missingAnimation) registerAnimationClass(registration);
        if (missingTrack) registerAnimationTrackClass(registration);
        if (missingAnimation || missingTrack) registerInstanceMethods(registration);
    }

    public static void registerClasses(MLuaRegistration registration) {
        registerAnimationClass(registration);
        registerAnimationTrackClass(registration);
        registerInstanceMethods(registration);
    }

    private static void registerAnimationClass(MLuaRegistration registration) {
        registration.registerClass("Animation", registration.requireInstanceBaseClassName(),
            "A reusable sequence of keyframes that can be played on a living entity.");
        registration.registerClassFunction("Animation", "new", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                LuaTable keyframes = args.arg(1).checktable();
                MLuaAnimation anim = MLuaAnimation.fromLuaTable(keyframes);
                return wrapAnimation(anim, registration);
            }
        }, "Creates an animation from an ordered keyframe table.");
    }

    private static void registerAnimationTrackClass(MLuaRegistration registration) {
        registration.registerClass("AnimationTrack", registration.requireInstanceBaseClassName(),
            "A live animation playback instance returned by an entity animation controller.");
        registration.registerClassFunction("AnimationTrack", "new", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                throw new LuaError("[MLua] AnimationTrack cannot be created directly. Use entity:LoadAnimation(animation)");
            }
        }, "Rejects direct construction because tracks are created by entity:LoadAnimation.");
    }

    private static void registerInstanceMethods(MLuaRegistration registration) {
        MLuaClassMethods.registerInstanceMethod(registration, "Animation", "GetLength",
            "Returns the total duration of this animation in seconds.");
        MLuaClassMethods.registerInstanceMethod(registration, "Animation", "GetKeyframes",
            "Returns this animation's ordered keyframes.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "Play",
            "Starts playback. An optional fadeTime in seconds fades the track in.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "Stop",
            "Stops playback. An optional fadeTime in seconds fades the track out.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "Pause",
            "Pauses this animation track at its current position.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "Resume",
            "Resumes this animation track from its current position.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "AdjustSpeed",
            "Sets this animation track's finite, non-negative playback speed.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "GetTimePosition",
            "Returns this animation track's current playback position.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "SetTimePosition",
            "Moves playback to a finite, non-negative position, clamped to the animation length.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "GetTrackId",
            "Returns this animation track's runtime identifier.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "GetPriority",
            "Returns this animation track's playback priority.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "GetLength",
            "Returns this animation track's total duration.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "GetSpeed",
            "Returns this animation track's playback speed.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "IsPlaying",
            "Returns whether this animation track is playing.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "IsPaused",
            "Returns whether this animation track is paused.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "GetLooped",
            "Returns whether this animation track repeats.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "SetLooped",
            "Sets whether this animation track repeats; the value must be a boolean.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "GetWeight",
            "Returns this animation track's current weight.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "SetWeight",
            "Fades this animation track toward a blend weight from zero to one.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "AdjustWeight",
            "Fades this animation track toward a blend weight over an optional duration in seconds.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "AddEvent",
            "Runs a callback when playback crosses a time in seconds. Time zero runs when playback starts.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "ClearEvents",
            "Removes all marker callbacks from this track.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "GetAnimation",
            "Returns the Animation instance played by this track.");

        MLuaClassMethods.registerInstanceProperty(registration, "Animation", "Length", "GetLength", null,
            "number", "client:Client", "The animation duration in seconds.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "Animation", "GetAnimation", null,
            "Animation", "client:Client", "The animation played by this track.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "TrackId", "GetTrackId", null,
            "number", "client:Client", "The track's runtime identifier.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "Priority", "GetPriority", null,
            "AnimationPriority", "client:Client", "The track's animation blend priority.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "Length", "GetLength", null,
            "number", "client:Client", "The animation duration in seconds.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "TimePosition", "GetTimePosition", "SetTimePosition",
            "number", "client:Client", "The current playback position in seconds. Readable and writable.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "Speed", "GetSpeed", "AdjustSpeed",
            "number", "client:Client", "Playback speed; zero stops time advancement while retaining the current pose. Readable and writable.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "Playing", "IsPlaying", null,
            "boolean", "client:Client", "Whether this track is currently playing.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "Paused", "IsPaused", null,
            "boolean", "client:Client", "Whether this track is paused.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "Looped", "GetLooped", "SetLooped",
            "boolean", "client:Client", "Whether playback repeats. Readable and writable.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "Weight", "GetWeight", "SetWeight",
            "number", "client:Client", "The current blend weight from zero to one. Assigning it fades toward the new weight.");
    }

    public static LuaTable createAnimationTable(MLuaRegistration registration) {
        java.util.Objects.requireNonNull(registration, "registration");
        LuaTable animTable = new LuaTable();
        LuaTable animationClass = registration.classTable("Animation");
        LuaTable trackClass = registration.classTable("AnimationTrack");
        if (animationClass == null || trackClass == null) {
            throw new IllegalStateException("Animation classes must be registered before creating the Animation library table");
        }
        animTable.set("Animation", animationClass);
        animTable.set("AnimationTrack", trackClass);

        animTable.set("AnimationPriority", MLuaEnumValue.enumConstants(AnimationPriority.class, null));
        animTable.set("EasingStyle", MLuaEnumValue.enumConstants(dev.mlua.common.enums.EasingStyle.class, null));
        animTable.set("EasingDirection", MLuaEnumValue.enumConstants(dev.mlua.common.enums.EasingDirection.class, null));

        return animTable;
    }

    public static LuaTable wrapAnimation(MLuaAnimation animation, MLuaRegistration registration) {
        LuaTable fields = new LuaTable();
        LuaTable table = createClassInstance(registration, "Animation", fields);
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
        return table;
    }

    public static LuaTable wrapTrack(MLuaAnimationTrack track, MLuaRegistration registration) {
        LuaTable fields = new LuaTable();
        fields.set("__trackId", track.trackId());
        fields.set("__entityId", track.entityId());
        LuaTable table = createClassInstance(registration, "AnimationTrack", fields);

        MLuaClassMethods.setInstanceMethod(table, "Play", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
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
                track.pause();
                return LuaValue.NONE;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "Resume", new ZeroArgFunction() {
            public LuaValue call() {
                track.resume();
                return LuaValue.NONE;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "AdjustSpeed", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
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

        MLuaClassMethods.setInstanceMethod(table, "GetLooped", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(track.isLooped());
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "SetLooped", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                track.setLooped(MLuaLuaArguments.optionalBoolean(
                    args, 2, false, "AnimationTrack:SetLooped", "looped"));
                return LuaValue.NONE;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "GetWeight", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(track.getWeight());
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "SetWeight", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
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
                float triggerTime = MLuaLuaArguments.finiteNonNegativeFloat(
                    args.arg(2), "AnimationTrack:AddEvent", "time");
                LuaValue callback = args.checkfunction(3);
                try {
                    track.addEvent(triggerTime, new VarArgFunction() {
                        @Override
                        public Varargs invoke(Varargs eventArgs) {
                            return callback.invoke(LuaValue.varargsOf(new LuaValue[]{table, eventArgs.arg(2)}));
                        }
                    });
                } catch (IllegalArgumentException error) {
                    throw new LuaError("[MLua] AnimationTrack:AddEvent: " + error.getMessage());
                }
                return LuaValue.NONE;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "ClearEvents", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                track.clearEvents();
                return LuaValue.NONE;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "GetAnimation", new ZeroArgFunction() {
            public LuaValue call() {
                return wrapAnimation(track.animation(), registration);
            }
        });

        return table;
    }

    private static LuaTable createClassInstance(MLuaRegistration registration, String className, LuaTable fields) {
        if (registration == null) throw new IllegalStateException("Lua registration is required for " + className + " instances");
        return registration.createClassInstance(className, fields);
    }
}
