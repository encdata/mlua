package dev.mlua.minecraft.binding;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.common.enums.AnimationPriority;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaAnimation;
import dev.mlua.minecraft.animation.MLuaAnimationTrack;
import dev.mlua.minecraft.animation.MLuaAnimationController;
import dev.mlua.minecraft.animation.MLuaAnimationOptions;
import dev.mlua.minecraft.animation.MLuaBonePose;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.runtime.client.MLuaClientRenderManager;
import dev.mlua.modules.enums.MLuaEnumValue;
import java.util.Map;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.world.entity.LivingEntity;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

public final class MLuaAnimationBindings {

    private static final Map<MLuaRegistration, Map<MLuaAnimation, WeakReference<LuaTable>>> ANIMATION_WRAPPERS = new WeakHashMap<>();
    private static final Map<MLuaRegistration, Map<Integer, WeakReference<LuaTable>>> TRACK_WRAPPERS = new WeakHashMap<>();

    private MLuaAnimationBindings() {}

    /** Binds the entity-facing animation API alongside the entity's other living methods. */
    public static void bindEntityMethods(LuaTable table, LivingEntity entity, MLuaRegistration registration) {
        MLuaClassMethods.setInstanceMethod(table, "LoadAnimation", varArg(args -> {
            LuaValue animValue = args.arg(2);
            if (animValue.istable() && animValue.checktable().get("__bbmodel").toboolean()) {
                LuaTable model = animValue.checktable();
                int renderId = model.get("Id").checkint();
                Object runtimeOwner = registration instanceof MLuaRuntime runtime ? runtime.session() : null;
                var owner = new dev.mlua.runtime.client.MLuaRenderOwnershipRegistry.Owner(
                    runtimeOwner, model.get("__mluaOwnerModId").optjstring(""));
                if (!MLuaClientRenderManager.attachToEntity(renderId, entity, owner)) {
                    throw new LuaError("[MLua] BBModel render object belongs to another runtime");
                }
                return animValue;
            }

            MLuaAnimation animation = resolveAnimation(animValue, registration, "Entity:LoadAnimation");

            AnimationPriority priority = resolvePriority(args.arg(3), "Entity:LoadAnimation", AnimationPriority.Idle);
            float fadeTime = MLuaLuaArguments.optionalNonNegativeFiniteFloat(
                args, 4, 0.0f, "Entity:LoadAnimation", "fadeTime");
            MLuaContext caller = MLuaLuaBindings.activeContext(registration);
            String addonOwner = caller == null ? "" : caller.ownerModId();
            MLuaAnimationTrack track = MLuaAnimationController.loadAnimation(
                entity, animation, priority, registration, addonOwner);
            if (fadeTime > 0.001f) {
                track.setFadeSpeed(1.0f / fadeTime);
                track.fadeTo(1.0f);
            }
            return wrapTrack(track, registration, table);
        }));
        MLuaClassMethods.setInstanceMethod(table, "ResetAnimation", luaFn(() -> {
            MLuaClientRenderManager.resetEntityModel(entity);
            MLuaAnimationController.stopAll(entity, registration);
            return LuaValue.NONE;
        }));
        MLuaClassMethods.setInstanceMethod(table, "LoadModel", table.get("LoadAnimation"));
        MLuaClassMethods.setInstanceMethod(table, "ResetModel", table.get("ResetAnimation"));
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
            for (MLuaAnimationTrack track : MLuaAnimationController.getTracks(entity, registration)) {
                if (track.isPlaying()) tracks.set(index++, wrapTrack(track, registration, table));
            }
            return tracks;
        }));
        MLuaClassMethods.setInstanceMethod(table, "GetTracksByPriority", varArg(args -> {
            AnimationPriority priority = resolvePriority(args.arg(2), "Entity:GetTracksByPriority", null);
            LuaTable tracks = new LuaTable();
            int index = 1;
            for (MLuaAnimationTrack track : MLuaAnimationController.getTracks(entity, registration)) {
                if (track.getPriority() == priority) tracks.set(index++, wrapTrack(track, registration, table));
            }
            return tracks;
        }));
        MLuaClassMethods.setInstanceMethod(table, "GetAnimationTracks", luaFn(() -> {
            LuaTable tracks = new LuaTable();
            int index = 1;
            for (MLuaAnimationTrack track : MLuaAnimationController.getTracks(entity, registration)) {
                tracks.set(index++, wrapTrack(track, registration, table));
            }
            return tracks;
        }));
        MLuaClassMethods.setInstanceMethod(table, "StopAllAnimations", luaFn(() -> {
            MLuaAnimationController.stopAll(entity, registration);
            return LuaValue.NONE;
        }));
        MLuaClassMethods.setInstanceMethod(table, "GetAnimator", luaFn(() -> {
            if (registration.classTable("Animator") == null) {
                throw new LuaError("[MLua] Entity:GetAnimator is available in client scripts only.");
            }
            return wrapAnimator(table, registration);
        }));
        MLuaClassMethods.setInstanceMethod(table, "SetBonePose", varArg(args -> {
            MLuaBonePose.setPose(entity, args.checkjstring(2), MLuaCFrame.fromLua(args.arg(3)), registration);
            return LuaValue.NONE;
        }));
        MLuaClassMethods.setInstanceMethod(table, "GetBonePose", varArg(args -> {
            MLuaCFrame pose = MLuaBonePose.getBonePose(entity, args.checkjstring(2), registration);
            return pose == null ? LuaValue.NIL : pose.toLua();
        }));
        MLuaClassMethods.setInstanceMethod(table, "GetBonePoses", luaFn(() -> {
            LuaTable poses = new LuaTable();
            for (Map.Entry<String, MLuaCFrame> entry : MLuaBonePose.getPose(entity, registration).entrySet()) {
                poses.set(entry.getKey(), entry.getValue().toLua());
            }
            return poses;
        }));
        MLuaClassMethods.setInstanceMethod(table, "ClearBonePose", varArg(args -> {
            if (args.narg() >= 2 && !args.arg(2).isnil()) {
                MLuaBonePose.clearBonePose(entity, args.checkjstring(2), registration);
            } else {
                MLuaBonePose.clearPose(entity, registration);
            }
            return LuaValue.NONE;
        }));
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
        java.util.Objects.requireNonNull(registration, "registration");
        boolean missingAnimation = registration.classTable("Animation") == null;
        boolean missingTrack = registration.classTable("AnimationTrack") == null;
        boolean missingAnimator = registration.classTable("Animator") == null;
        if (missingAnimation) registerAnimationClass(registration);
        if (missingTrack) registerAnimationTrackClass(registration);
        if (missingAnimator) registerAnimatorClass(registration);
        if (missingAnimation || missingTrack || missingAnimator) registerInstanceMethods(registration);
    }

    public static void registerClasses(MLuaRegistration registration) {
        registerAnimationClass(registration);
        registerAnimationTrackClass(registration);
        registerAnimatorClass(registration);
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

    private static void registerAnimatorClass(MLuaRegistration registration) {
        registration.registerClass("Animator", registration.requireInstanceBaseClassName(),
            "Animation controller for a living entity, with independently managed playback tracks.");
        registration.registerClassFunction("Animator", "new", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                throw new LuaError("[MLua] Animator instances are created by Entity:GetAnimator().");
            }
        }, "Rejects direct construction because each Animator belongs to one live entity.");
    }

    private static void registerInstanceMethods(MLuaRegistration registration) {
        MLuaClassMethods.registerInstanceMethod(registration, "Animator", "LoadAnimation",
            "Loads an animation for this entity and returns a controllable AnimationTrack.");
        MLuaClassMethods.registerInstanceMethod(registration, "Animator", "GetPlayingAnimationTracks",
            "Returns tracks whose playback is currently active.");
        MLuaClassMethods.registerInstanceMethod(registration, "Animator", "GetAnimationTracks",
            "Returns every live track, including tracks fading out after Stop.");
        MLuaClassMethods.registerInstanceMethod(registration, "Animator", "GetTracksByPriority",
            "Returns the live tracks running at one animation priority.");
        MLuaClassMethods.registerInstanceMethod(registration, "Animator", "StopAll",
            "Stops every animation track with its default fade duration, or the supplied fadeTime.");
        MLuaClassMethods.registerInstanceMethod(registration, "Animator", "SetBonePose",
            "Sets a CFrame pose override for one registered model bone.");
        MLuaClassMethods.registerInstanceMethod(registration, "Animator", "GetBonePose",
            "Returns the current CFrame pose for one bone, or nil when no pose is set.");
        MLuaClassMethods.registerInstanceMethod(registration, "Animator", "ClearPose",
            "Clears custom pose overrides owned by this animator.");
        MLuaClassMethods.registerInstanceMethod(registration, "Animator", "GetEntity",
            "Returns the living entity controlled by this animator.");
        MLuaClassMethods.registerInstanceProperty(registration, "Animator", "Entity", "GetEntity", null,
            "LivingEntity", "client:Client", "The living entity controlled by this animator.");
        MLuaClassMethods.registerInstanceMethod(registration, "Animation", "GetLength",
            "Returns the total duration of this animation in seconds.");
        MLuaClassMethods.registerInstanceMethod(registration, "Animation", "GetKeyframes",
            "Returns this animation's ordered keyframes.");
        MLuaClassMethods.registerInstanceMethod(registration, "Animation", "GetKeyframeCount",
            "Returns the number of keyframes in this animation.");
        MLuaClassMethods.registerInstanceMethod(registration, "Animation", "GetBoneNames",
            "Returns the sorted bone names used by this animation.");
        MLuaClassMethods.registerInstanceMethod(registration, "Animation", "HasBone",
            "Returns whether this animation contains a pose for the given bone in any keyframe.");
        MLuaClassMethods.registerInstanceMethod(registration, "Animation", "Sample",
            "Returns the interpolated bone pose at a time in seconds.");
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
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "IsAlive",
            "Returns whether this track is playing or still contributing while fading out.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "IsFading",
            "Returns whether the current blend weight is moving toward its target weight.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "GetLooped",
            "Returns whether this animation track repeats.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "SetLooped",
            "Sets whether this animation track repeats; the value must be a boolean.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "GetWeight",
            "Returns this animation track's current weight.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "GetTargetWeight",
            "Returns the blend weight this track is currently moving toward.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "SetWeight",
            "Fades this animation track toward a blend weight from zero to one.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "AdjustWeight",
            "Fades this animation track toward a blend weight over an optional duration in seconds.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "AddEvent",
            "Runs a callback when playback crosses a time in seconds. Time zero runs when playback starts.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "AddMarker",
            "Registers a named callback marker at a time in seconds; the callback receives the track, marker name, and time.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "ClearEvents",
            "Removes all marker callbacks from this track.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "GetAnimation",
            "Returns the Animation instance played by this track.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "GetPose",
            "Returns the current sampled pose as bone names mapped to CFrame values.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "GetBonePose",
            "Returns the current sampled CFrame for one bone, or nil when the animation does not pose it.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "GetEntity",
            "Returns the live entity that owns this animation track.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "GetAnimator",
            "Returns the animation controller that owns this track.");

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
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "Alive", "IsAlive", null,
            "boolean", "client:Client", "Whether this track is playing or still contributing while fading out.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "Looped", "GetLooped", "SetLooped",
            "boolean", "client:Client", "Whether playback repeats. Readable and writable.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "Weight", "GetWeight", "SetWeight",
            "number", "client:Client", "The current blend weight from zero to one. Assigning it fades toward the new weight.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "TargetWeight", "GetTargetWeight", null,
            "number", "client:Client", "The target blend weight from zero to one during a fade.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "Fading", "IsFading", null,
            "boolean", "client:Client", "Whether the current blend weight is moving toward its target weight.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "Entity", "GetEntity", null,
            "LivingEntity", "client:Client", "The live entity that owns this animation track.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "Animator", "GetAnimator", null,
            "Animator", "client:Client", "The animation controller that owns this track.");
    }

    public static LuaTable createAnimationTable(MLuaRegistration registration) {
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

    public static LuaTable wrapAnimation(MLuaAnimation animation, MLuaRegistration registration) {
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
    public static LuaTable wrapAnimator(LuaTable entity, MLuaRegistration registration) {
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
                LuaValue tracksMethod = entity.get("GetAnimationTracks");
                if (!tracksMethod.isfunction()) throw new LuaError("[MLua] Entity animation methods are unavailable.");
                LuaValue tracksValue = tracksMethod.invoke(LuaValue.varargsOf(new LuaValue[]{entity})).arg1();
                if (!tracksValue.istable()) throw new LuaError("[MLua] Entity:GetAnimationTracks() did not return a track table.");
                float fadeTime = args.arg(2).isnil() ? -1.0f
                    : MLuaLuaArguments.finiteNonNegativeFloat(args.arg(2), "Animator:StopAll", "fadeTime");
                LuaTable tracks = tracksValue.checktable();
                for (int index = 1; ; index++) {
                    LuaValue track = tracks.get(index);
                    if (track.isnil()) break;
                    LuaValue stop = track.get("Stop");
                    if (!stop.isfunction()) continue;
                    if (fadeTime < 0.0f) {
                        stop.invoke(LuaValue.varargsOf(new LuaValue[]{track}));
                    } else {
                        stop.invoke(LuaValue.varargsOf(new LuaValue[]{track, LuaValue.valueOf(fadeTime)}));
                    }
                }
                return LuaValue.NONE;
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

    public static LuaTable wrapTrack(MLuaAnimationTrack track, MLuaRegistration registration) {
        return wrapTrack(track, registration, null);
    }

    public static LuaTable wrapTrack(MLuaAnimationTrack track, MLuaRegistration registration, LuaTable entity) {
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
        MLuaClassMethods.setInstanceMethod(table, "IsAlive", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(track.isAlive());
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
        MLuaClassMethods.setInstanceMethod(table, "GetTargetWeight", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(track.getTargetWeight());
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
        MLuaContext context = MLuaLuaBindings.activeContext(registration);
        if (context == null) {
            throw new LuaError("[MLua] " + method + " must be called while an MLua script context is active.");
        }
        return context;
    }

    private static Varargs invokeAnimationEvent(MLuaContext context, LuaValue callback, Varargs args) {
        return MLuaLuaBindings.withContext(context, () -> callback.invoke(args));
    }

    private static LuaTable createClassInstance(MLuaRegistration registration, String className, LuaTable fields) {
        if (registration == null) throw new IllegalStateException("Lua registration is required for " + className + " instances");
        return registration.createClassInstance(className, fields);
    }
}
