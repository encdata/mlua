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
            "Starts or resumes this animation track.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "Stop",
            "Stops this animation track.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "Pause",
            "Pauses this animation track at its current position.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "Resume",
            "Resumes this animation track from its current position.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "AdjustSpeed",
            "Sets this animation track's finite, non-negative playback speed.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "GetTimePosition",
            "Returns this animation track's current playback position.");
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
            "Sets this animation track's blend weight from zero to one.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "GetAnimation",
            "Returns the Animation instance played by this track.");
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
        fields.set("Length", LuaValue.valueOf(animation.getLength()));
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
                    kfEntry.set("EasingStyle", kf.easingStyle.name());
                    kfEntry.set("EasingDirection", kf.easingDirection.name());
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

        MLuaClassMethods.setInstanceMethod(table, "Play", new ZeroArgFunction() {
            public LuaValue call() {
                track.play();
                return LuaValue.NONE;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "Stop", new ZeroArgFunction() {
            public LuaValue call() {
                track.stop();
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
                track.fadeTo(MLuaLuaArguments.unitIntervalFloat(
                    args, 2, "AnimationTrack:SetWeight", "weight"));
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
