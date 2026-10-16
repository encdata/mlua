package dev.mlua.modules.animation;

import dev.mlua.minecraft.animation.MLuaAnimationController;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.minecraft.binding.MLuaAnimationBindings;
import dev.mlua.loaderapi.EventBridge;
import dev.mlua.common.enums.Side;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.MLuaContextView;
import dev.mlua.modules.animation.Service.MLuaAnimationService;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.luaj.vm2.Globals;

@dev.mlua.module.MLuaModuleSide(Side.CLIENT)
public class AnimationModule implements MLuaModule {
    @Override
    public String id() { return "animation"; }

    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        MLuaAnimationController.clearRuntime(runtime, side);
    }

    @Override
    public int priority() { return 15; }

    @Override
    public List<String> dependencies() { return List.of("registry"); }

    @Override
    public Side side() { return Side.CLIENT; }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder animation = ServiceEntry.builder("Animation", MLuaAnimationService::new)
            .className("AnimationService")
            .reflective(MLuaAnimationService.class);
        return List.of(animation.build());
    }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        MLuaAnimationBindings.registerClasses(registration);
    }

    @Override
    public void contributeGlobals(Globals globals, MLuaContextView context) {
        MLuaAnimationBindings.ensureRegisteredClasses(context.registration());
        globals.set("Animation", MLuaAnimationBindings.createAnimationTable(context.registration()));
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.signature("AnimationService", "AnimationService:Create(keyframes: KeyframeDefinition[])", "Animation"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationService", "AnimationService:RegisterAnimation(id: string, animation: Animation|KeyframeDefinition[])", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationService", "AnimationService:GetAnimation(id: string)", "Animation|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationService", "AnimationService:SetPose(entity: Entity|number, bone: string, cframe: CFrame)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationService", "AnimationService:GetPose(entity: Entity|number)", "table<string, CFrame>"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationService", "AnimationService:ClearPose(entity: Entity|number)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationService", "AnimationService:GetRegisteredAnimations()", "table<string, Animation>"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationService", "AnimationService:GetAnimationPriority()", "AnimationPriority"));
        docs.register(MLuaDocBuilder.Registration.signature("Animation", "Animation:GetLength()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Animation", "Animation:GetKeyframes()", "KeyframeDefinition[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Animation", "Animation:GetKeyframeCount()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("Animation", "Animation:GetBoneNames()", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Animation", "Animation:HasBone(name: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("Animation", "Animation:Sample(time: number)", "table<string, CFrame>"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:Pause()", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:Resume()", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:AdjustSpeed(speed: number)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:GetTimePosition()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:SetTimePosition(timePosition: number)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:GetTrackId()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:GetPriority()", "AnimationPriority"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:GetLength()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:GetSpeed()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:IsPlaying()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:IsPaused()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:GetLooped()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:SetLooped(looped: boolean)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:GetWeight()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:SetWeight(weight: number, fadeTime?: number)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:AdjustWeight(weight: number, fadeTime: number)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:AddEvent(time: number, callback: function)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:AddMarker(name: string, time: number, callback: function)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:GetAnimation()", "Animation"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:GetPose()", "table<string, CFrame>"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:GetBonePose(bone: string)", "CFrame|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("LivingEntity", "LivingEntity:GetAnimator()", "Animator"));
        docs.register(MLuaDocBuilder.Registration.signature("Animator", "Animator:LoadAnimation(animation: Animation|KeyframeDefinition[], priority?: AnimationPriority, fadeTime?: number)", "AnimationTrack"));
        docs.register(MLuaDocBuilder.Registration.signature("Animator", "Animator:GetPlayingAnimationTracks()", "AnimationTrack[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Animator", "Animator:StopAll()", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Animator", "Animator:SetBonePose(bone: string, transform: CFrame)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Animator", "Animator:GetBonePose(bone: string)", "CFrame|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("Animator", "Animator:ClearPose()", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Animator", "Animator:GetEntity()", "Entity"));
        docs.register(MLuaDocBuilder.Registration.method("AnimationTrack", "AnimationTrack:Play(fadeTime?: number)", "Starts playback and optionally fades from zero to full weight over fadeTime seconds. Calling Play() uses the track's default fade speed.", "fadeTime", "void", "client:Client", ""));
        docs.register(MLuaDocBuilder.Registration.method("AnimationTrack", "AnimationTrack:Stop(fadeTime?: number)", "Stops playback and optionally fades the pose out over fadeTime seconds. Calling Stop() uses the track's default fade speed.", "fadeTime", "void", "client:Client", ""));
        docs.register(MLuaDocBuilder.Registration.method("AnimationTrack", "AnimationTrack:SetWeight(weight: number, fadeTime?: number)", "Fades the track toward weight (0 to 1), optionally over fadeTime seconds.", "weight, fadeTime", "void", "client:Client", ""));
        docs.register(MLuaDocBuilder.Registration.method("AnimationTrack", "AnimationTrack:AdjustWeight(weight: number, fadeTime: number)", "Fades the track toward weight (0 to 1) over the specified duration in seconds. A duration of zero changes the weight immediately.", "weight, fadeTime", "void", "client:Client", ""));
        docs.register(MLuaDocBuilder.Registration.method("AnimationTrack", "AnimationTrack:AddEvent(time: number, callback: function)", "Runs callback(track, time) when playback crosses the marker time in seconds. A marker at zero runs as soon as playback starts.", "", "void", "client:Client", ""));
        docs.register(MLuaDocBuilder.Registration.method("AnimationTrack", "AnimationTrack:AddMarker(name: string, time: number, callback: function)", "Runs callback(track, name, time) when playback crosses the named marker time. Marker names must not be empty; time is measured in seconds.", "marker name, time in seconds, callback", "void", "client:Client", ""));
        docs.register(MLuaDocBuilder.Registration.method("AnimationTrack", "AnimationTrack:ClearEvents()", "Removes all marker callbacks from this track.", "", "void", "client:Client", ""));

        docs.register(MLuaDocBuilder.Registration.service("Animation", "MLuaAnimationService",
                "Service for creating and managing animations.",
                "services/animation.html"));
        docs.register(MLuaDocBuilder.Registration.clazz("Animation", "MLuaAnimationBindings",
                "A reusable sequence of keyframes that can be played on a living entity.",
                "classes/animation.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.clazz("AnimationTrack", "MLuaAnimationBindings",
                "A live animation playback instance returned by an entity animation controller.",
                "classes/animation-track.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.clazz("Animator", "MLuaAnimationBindings",
                "The animation controller owned by one living entity. It loads immutable Animation definitions into playback tracks.",
                "classes/animator.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.property("Animation", "Animation.Length", "number",
            "The animation duration in seconds.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Animation", "Check animation bones before playback",
            "Animation definitions are immutable after creation. GetBoneNames returns a sorted list of every mapped bone; HasBone and Sample help validate a definition against a model adapter before playback.",
            "local animator = entity:GetAnimator()\nlocal animation = Animation.new({\n    { Time = 0, Data = { head = CFrame.identity } },\n    { Time = 0.5, Data = { head = CFrame.Angles(0, math.rad(20), 0) } },\n})\n\nprint(animation.Length, animation:GetKeyframeCount())\nfor _, bone in ipairs(animation:GetBoneNames()) do\n    print(\"Animated bone:\", bone)\nend\nif animation:HasBone(\"head\") then\n    animator:LoadAnimation(animation):Play()\nend"));
        docs.register(MLuaDocBuilder.Registration.property("AnimationTrack", "AnimationTrack.Animation", "Animation",
            "The animation played by this track.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("AnimationTrack", "AnimationTrack.TrackId", "number",
            "The track's runtime identifier.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("AnimationTrack", "AnimationTrack.Priority", "AnimationPriority",
            "The track's animation blend priority.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("AnimationTrack", "AnimationTrack.Length", "number",
            "The animation duration in seconds.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("AnimationTrack", "AnimationTrack.TimePosition", "number",
            "The current playback position in seconds. Readable and writable.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("AnimationTrack", "AnimationTrack.Speed", "number",
            "Playback speed; zero stops time advancement while retaining the current pose. Readable and writable.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("AnimationTrack", "AnimationTrack.Playing", "boolean",
            "Whether this track is currently playing.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("AnimationTrack", "AnimationTrack.Paused", "boolean",
            "Whether this track is paused.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("AnimationTrack", "AnimationTrack.Looped", "boolean",
            "Whether playback repeats. Readable and writable.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("AnimationTrack", "AnimationTrack.Weight", "number",
            "The current blend weight from zero to one. Assigning it fades toward the new weight.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("Animator", "Animator.Entity", "LivingEntity",
            "The living entity controlled by this animator.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.type("KeyframeDefinition", null, "A pose at a point in an animation. Time and Data are required; the easing values are optional."));
        docs.register(MLuaDocBuilder.Registration.field("KeyframeDefinition", "Time", "number", "Position in seconds. Keyframes are sorted by this value and duplicate times are rejected."));
        docs.register(MLuaDocBuilder.Registration.field("KeyframeDefinition", "Data", "table<string, CFrame>", "Maps each registered bone name to its pose at this keyframe."));
        docs.register(MLuaDocBuilder.Registration.field("KeyframeDefinition", "EasingStyle?", "EasingStyle", "Optional Enum.EasingStyle value, or its exact name, used to interpolate from the previous keyframe into this one. Defaults to Linear."));
        docs.register(MLuaDocBuilder.Registration.field("KeyframeDefinition", "EasingDirection?", "EasingDirection", "Optional Enum.EasingDirection value, or its exact name, used to interpolate from the previous keyframe into this one. Defaults to In."));
        docs.register(MLuaDocBuilder.Registration.guideStep("AnimationTrack", "React to animation markers",
            "AddMarker invokes callback(track, name, time) when playback crosses its named time in seconds. AddEvent remains useful for unnamed timing callbacks. A marker at zero runs as soon as playback starts. ClearEvents removes callbacks that have not fired yet.",
            "local Animations = Minecraft:GetService(\"Animation\")\nlocal animator = entity:GetAnimator()\nlocal animation = Animations:GetAnimation(\"myaddon:reload\")\nlocal track = animator:LoadAnimation(animation, Animation.AnimationPriority.Action)\n\ntrack:AddMarker(\"reload_start\", 0, function(activeTrack, name, time)\n    print(name, activeTrack.TrackId, time)\nend)\ntrack:AddMarker(\"magazine_inserted\", 0.65, function(activeTrack, name, time)\n    print(name, time)\nend)\ntrack:Play()\n\n-- Remove marker callbacks when they are no longer needed.\ntrack:ClearEvents()"));
    }

    @Override
    public void registerEvents(EventBridge events) {
        events.onClientTickEnd(() -> {
            Minecraft mc = Minecraft.getInstance();
            Level level = mc.level;
            if (level == null) return;
            tickTrackedEntities(level, true);
        });

        events.onServerLevelTickEnd(levelObj -> {
            Level level = (Level) levelObj;
            tickTrackedEntities(level, false);
        });

        events.onClientDisconnect(MLuaAnimationController::clearAll);
    }

    private static void tickTrackedEntities(Level level, boolean clientSide) {
        String dimension = level.dimension().location().toString();
        for (MLuaEntityScope scope : MLuaAnimationController.getTrackedEntities()) {
            if (scope.side() != (clientSide ? dev.mlua.common.enums.Side.CLIENT : dev.mlua.common.enums.Side.SERVER)
                || !scope.dimension().equals(dimension)) continue;
            net.minecraft.world.entity.Entity entity = level.getEntity(scope.entityId());
            if (entity != null && entity.getUUID().equals(scope.uuid())) {
                MLuaAnimationController.tickEntity(entity, 0.05f);
            }
        }
    }
}
