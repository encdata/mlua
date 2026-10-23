package dev.mlua.minecraft.binding;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.minecraft.animation.MLuaAnimation;
import dev.mlua.module.MLuaRegistration;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Owns Lua class declarations and documented signatures for animation types. */
public final class MLuaAnimationClassRegistration {
    private MLuaAnimationClassRegistration() { }

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
                return MLuaAnimationBindings.wrapAnimation(anim, registration);
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
            "Returns every live track, including tracks holding a final pose or fading out after Stop.");
        MLuaClassMethods.registerInstanceMethod(registration, "Animator", "GetTracksByPriority",
            "Returns the live tracks running at one animation priority.");
        MLuaClassMethods.registerInstanceMethod(registration, "Animator", "StopAll",
            "Stops this addon's animation tracks for the entity with their default fade duration, or the supplied fadeTime.");
        MLuaClassMethods.registerInstanceMethod(registration, "Animator", "UnloadAnimationTrack",
            "Immediately releases one track owned by the calling addon, including its marker callbacks.");
        MLuaClassMethods.registerInstanceMethod(registration, "Animator", "SetBonePose",
            "Sets a CFrame manual pose override for one registered model bone, owned by the calling addon.");
        MLuaClassMethods.registerInstanceMethod(registration, "Animator", "GetBonePose",
            "Returns the current CFrame pose for one bone, or nil when no pose is set.");
        MLuaClassMethods.registerInstanceMethod(registration, "Animator", "ClearPose",
            "Clears the calling addon's manual pose overrides for this entity without stopping animation tracks.");
        MLuaClassMethods.registerInstanceMethod(registration, "Animator", "GetEntity",
            "Returns the entity controlled by this animator.");
        MLuaClassMethods.registerInstanceProperty(registration, "Animator", "Entity", "GetEntity", null,
            "Entity", "client:Client", "The entity controlled by this animator.");
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
            "Returns whether this track is playing, holding its final pose, or fading out.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "IsReleased",
            "Returns whether this track was released after fading out or its entity/runtime was unloaded.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "IsFading",
            "Returns whether the current blend weight is moving toward its target weight.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "GetLooped",
            "Returns whether this animation track repeats.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "SetLooped",
            "Sets whether this animation track repeats; the value must be a boolean.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "GetHoldOnEnd",
            "Returns whether this track retains its final pose after non-looped playback ends.");
        MLuaClassMethods.registerInstanceMethod(registration, "AnimationTrack", "SetHoldOnEnd",
            "Sets whether this track retains its final pose after non-looped playback ends.");
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
            "boolean", "client:Client", "Whether this track is playing, holding its final pose, or fading out.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "Looped", "GetLooped", "SetLooped",
            "boolean", "client:Client", "Whether playback repeats. Readable and writable.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "HoldOnEnd", "GetHoldOnEnd", "SetHoldOnEnd",
            "boolean", "client:Client", "Whether non-looped playback keeps its final pose. Defaults to true; false fades out and releases the track at the end.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "Weight", "GetWeight", "SetWeight",
            "number", "client:Client", "The current blend weight from zero to one. Assigning it fades toward the new weight.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "TargetWeight", "GetTargetWeight", null,
            "number", "client:Client", "The target blend weight from zero to one during a fade.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "Fading", "IsFading", null,
            "boolean", "client:Client", "Whether the current blend weight is moving toward its target weight.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "Entity", "GetEntity", null,
            "Entity", "client:Client", "The live entity that owns this animation track.");
        MLuaClassMethods.registerInstanceProperty(registration, "AnimationTrack", "Animator", "GetAnimator", null,
            "Animator", "client:Client", "The animation controller that owns this track.");
    }
}
