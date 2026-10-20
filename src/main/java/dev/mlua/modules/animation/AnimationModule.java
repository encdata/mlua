package dev.mlua.modules.animation;

import dev.mlua.minecraft.animation.MLuaAnimationController;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.minecraft.binding.MLuaAnimationBindings;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.loaderapi.EventBridge;
import dev.mlua.common.enums.Side;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.MLuaContextView;
import dev.mlua.modules.animation.service.MLuaAnimationService;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import org.luaj.vm2.Globals;

@dev.mlua.module.MLuaModuleSide(Side.CLIENT)
public class AnimationModule implements MLuaModule {
    @Override
    public String id() { return "animation"; }

    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        MLuaAnimationController.clearRuntime(runtimeSession, side);
        MLuaAnimationService.clearRuntimeState(runtimeSession, side);
    }

    @Override
    public void clearOwnedState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession,
                                Side side, String ownerModId) {
        MLuaAnimationService.clearOwnedState(runtimeSession, ownerModId, side);
        MLuaAnimationController.clearOwned(runtimeSession, ownerModId, side);
        dev.mlua.minecraft.animation.MLuaBonePose.clearOwned(runtimeSession, ownerModId, side);
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
        docs.register(MLuaDocBuilder.Registration.signature("AnimationService", "AnimationService:HasAnimation(id: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationService", "AnimationService:RemoveAnimation(id: string)", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationService", "AnimationService:ClearAnimations()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationService", "AnimationService:GetAnimationIds()", "string[]"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationService", "AnimationService:GetAnimationCount()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationService", "AnimationService:SetPose(entity: Entity|number, bone: string, cframe: CFrame)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationService", "AnimationService:GetPose(entity: Entity|number)", "table<string, CFrame>"));
        docs.register(MLuaDocBuilder.Registration.method("AnimationService", "AnimationService:SetPose(entity: Entity|number, bone: string, cframe: CFrame)",
            "Sets this addon's manual pose override on a live entity in the client world. A numeric ID must currently resolve in that world; use an Entity instance when possible because IDs can be reused after unload.",
            "live client Entity or loaded entity ID, bone name, CFrame", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("AnimationService", "AnimationService:GetPose(entity: Entity|number)",
            "Returns the combined custom pose for a live entity in the client world. A numeric ID must currently resolve in that world.",
            "live client Entity or loaded entity ID", "table<string, CFrame>", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("AnimationService", "AnimationService:ClearPose(entity: Entity|number)",
            "Clears this addon's manual pose overrides for a live entity in the client world. It does not stop active animation tracks or clear another addon's poses. A numeric ID must currently resolve in that world.",
            "live client Entity or loaded entity ID", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("AnimationService", "AnimationService:ClearPoses()",
            "Clears this addon's manual pose overrides on the current side. Animation tracks continue playing, and other addons' poses remain.",
            "No arguments", "void", "client:Client", null));
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
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:IsReleased()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:GetLooped()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:SetLooped(looped: boolean)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:GetHoldOnEnd()", "boolean"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:SetHoldOnEnd(holdOnEnd: boolean)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:GetWeight()", "number"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:SetWeight(weight: number, fadeTime?: number)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:AdjustWeight(weight: number, fadeTime: number)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:GetAnimation()", "Animation"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:GetPose()", "table<string, CFrame>"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:GetBonePose(bone: string)", "CFrame|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:GetEntity()", "Entity"));
        docs.register(MLuaDocBuilder.Registration.signature("AnimationTrack", "AnimationTrack:GetAnimator()", "Animator"));
        docs.register(MLuaDocBuilder.Registration.signature("Entity", "Entity:GetAnimator()", "Animator"));
        docs.register(MLuaDocBuilder.Registration.method("Animator", "Animator:LoadAnimation(animation: Animation|KeyframeDefinition[], priority?: AnimationPriority, fadeTime?: number)", "Creates an entity animation track owned by the calling addon. The owner can control or release the track; other addons can inspect tracks returned by the entity's track queries but cannot change them. Each addon may hold 128 tracks per entity and the client runtime allows 8192 total. Addon-scoped cleanup removes that addon's tracks. By default, a non-looped track holds its final pose after playback ends; set track.HoldOnEnd = false to fade out and release it automatically. A single keyframe at time zero is a static pose that stays active until the track is stopped.", "animation, priority, fadeTime", "AnimationTrack", "client:Client", ""));
        docs.register(MLuaDocBuilder.Registration.signature("Animator", "Animator:GetPlayingAnimationTracks()", "AnimationTrack[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Animator", "Animator:GetAnimationTracks()", "AnimationTrack[]"));
        docs.register(MLuaDocBuilder.Registration.signature("Animator", "Animator:GetTracksByPriority(priority: AnimationPriority)", "AnimationTrack[]"));
        docs.register(MLuaDocBuilder.Registration.method("Animator", "Animator:StopAll(fadeTime?: number)", "Stops only this addon's tracks for the entity. With no fadeTime, each track uses its default fade; supply a duration in seconds to override it.", "fadeTime", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("Animator", "Animator:UnloadAnimationTrack(track: AnimationTrack): boolean",
            "Immediately releases this addon's track and its marker callbacks. Returns false if the track is not owned by this addon or is no longer loaded.",
            "AnimationTrack instance", "boolean", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.signature("Animator", "Animator:SetBonePose(bone: string, transform: CFrame)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Animator", "Animator:GetBonePose(bone: string)", "CFrame|nil"));
        docs.register(MLuaDocBuilder.Registration.method("Animator", "Animator:ClearPose()",
            "Clears the calling addon's manual pose overrides for this entity without stopping animation tracks.",
            "No arguments", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.signature("Animator", "Animator:GetEntity()", "Entity"));
        docs.register(MLuaDocBuilder.Registration.method("AnimationTrack", "AnimationTrack:Play(fadeTime?: number)", "Starts playback and optionally fades from zero to full weight over fadeTime seconds. Calling Play() uses the track's default fade speed. Only the addon that loaded this track can change its playback.", "fadeTime", "void", "client:Client", ""));
        docs.register(MLuaDocBuilder.Registration.method("AnimationTrack", "AnimationTrack:Stop(fadeTime?: number)", "Stops playback and optionally fades the pose out over fadeTime seconds. Calling Stop() uses the track's default fade speed. Only the addon that loaded this track can change its playback.", "fadeTime", "void", "client:Client", ""));
        docs.register(MLuaDocBuilder.Registration.method("AnimationTrack", "AnimationTrack:SetHoldOnEnd(holdOnEnd: boolean)", "Controls what happens when non-looped playback reaches its last keyframe. Defaults to true and keeps the final pose until Stop or cleanup; false fades the pose out and releases the track. This setting affects the next natural playback end.", "holdOnEnd", "void", "client:Client", ""));
        docs.register(MLuaDocBuilder.Registration.method("AnimationTrack", "AnimationTrack:SetWeight(weight: number, fadeTime?: number)", "Fades the track toward weight (0 to 1), optionally over fadeTime seconds.", "weight, fadeTime", "void", "client:Client", ""));
        docs.register(MLuaDocBuilder.Registration.method("AnimationTrack", "AnimationTrack:AdjustWeight(weight: number, fadeTime: number)", "Fades the track toward weight (0 to 1) over the specified duration in seconds. A duration of zero changes the weight immediately.", "weight, fadeTime", "void", "client:Client", ""));
        docs.register(MLuaDocBuilder.Registration.method("AnimationTrack", "AnimationTrack:AddEvent(time: number, callback: function)", "Runs callback(track, time) when playback crosses the marker time in seconds. A marker at zero runs as soon as playback starts. The callback runs with the registering script context and execution lock. Each track accepts up to " + dev.mlua.minecraft.animation.MLuaAnimationTrack.MAX_EVENTS_PER_TRACK + " event callbacks; one addon runtime can retain at most " + dev.mlua.minecraft.animation.MLuaAnimationTrack.MAX_ACTIVE_EVENT_CALLBACKS_PER_OWNER + " callbacks, and MLua retains at most " + dev.mlua.minecraft.animation.MLuaAnimationTrack.MAX_ACTIVE_EVENT_CALLBACKS + " callbacks across all live tracks. ClearEvents or releasing a track frees that capacity. During a batched animation update, one addon can invoke at most " + dev.mlua.minecraft.animation.MLuaAnimationTrack.MAX_EVENT_CALLBACKS_PER_ADDON_BATCH_TICK + " callbacks across the supplied entities and the batch is capped at " + dev.mlua.minecraft.animation.MLuaAnimationTrack.MAX_EVENT_CALLBACKS_PER_BATCH_TICK + " total callbacks; direct single-entity updates use the per-entity limits of " + dev.mlua.minecraft.animation.MLuaAnimationTrack.MAX_EVENT_CALLBACKS_PER_ADDON_TICK + " per addon and " + dev.mlua.minecraft.animation.MLuaAnimationTrack.MAX_EVENT_CALLBACKS_PER_ENTITY_TICK + " total. Markers beyond an exhausted tick budget are skipped for that update. A callback is removed after it throws.", "time in seconds, callback", "void", "client:Client", ""));
        docs.register(MLuaDocBuilder.Registration.method("AnimationTrack", "AnimationTrack:AddMarker(name: string, time: number, callback: function)", "Runs callback(track, name, time) when playback crosses the named marker time. Marker names must not be empty; time is measured in seconds. The callback runs with the registering script context and execution lock. Events and markers share the per-track, per-addon-runtime, and global callback retention limits, batched-update limits, and direct per-entity limits documented for AddEvent. ClearEvents or releasing a track frees retained callback capacity. Markers beyond an exhausted tick budget are skipped for that update. A failing callback is removed.", "marker name, time in seconds, callback", "void", "client:Client", ""));
        docs.register(MLuaDocBuilder.Registration.method("AnimationTrack", "AnimationTrack:ClearEvents()", "Removes all marker callbacks from this track.", "", "void", "client:Client", ""));
        docs.register(MLuaDocBuilder.Registration.method("AnimationTrack", "AnimationTrack:IsReleased()",
            "Returns whether the track was released after fading out or its entity/runtime was unloaded. Released tracks no longer retain marker callbacks and cannot be changed; load a new track to play the animation again.",
            "No arguments", "boolean", "client:Client", null));

        docs.register(MLuaDocBuilder.Registration.service("Animation", "MLuaAnimationService",
                "Service for creating and managing animations.",
                "services/animation.html"));
        docs.register(MLuaDocBuilder.Registration.clazz("Animation", "MLuaAnimationBindings",
                "A reusable sequence of keyframes that can be played on a living entity.",
                "classes/animation.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.clazz("AnimationTrack", "MLuaAnimationBindings",
                "A live animation playback instance returned by an entity animation controller. The addon that loaded the track owns its playback controls and marker callbacks; other addons can inspect track state but cannot mutate or release it.",
                "classes/animation-track.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.clazz("Animator", "MLuaAnimationBindings",
                "The animation controller owned by one entity. It loads immutable Animation definitions into playback tracks and applies their named bones through registered model adapters.",
                "classes/animator.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.property("Animation", "Animation.Length", "number",
            "The animation duration in seconds.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Animation", "Check animation bones before playback",
            "Animation definitions are immutable after creation. GetBoneNames returns a sorted list of every mapped bone; HasBone and Sample help validate a definition against a model adapter before playback. Non-looped tracks keep their final pose by default; set HoldOnEnd to false when the track should fade out and release itself.",
            "local animator = entity:GetAnimator()\nlocal animation = Animation.new({\n    { Time = 0, Data = { head = CFrame.identity } },\n    { Time = 0.5, Data = { head = CFrame.Angles(0, math.rad(20), 0) } },\n})\n\nprint(animation.Length, animation:GetKeyframeCount())\nfor _, bone in ipairs(animation:GetBoneNames()) do\n    print(\"Animated bone:\", bone)\nend\nif animation:HasBone(\"head\") then\n    local track = animator:LoadAnimation(animation)\n    -- Set before Play to fade out and release after the last keyframe.\n    track.HoldOnEnd = false\n    track:Play()\nend"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Animation", "Animate a non-living addon model",
            "Register both the model's named ModelPart paths and the renderer field that owns that model. MLua applies the entity's sampled bone transforms only while those native parts render, then restores the shared model instance. This requires a renderer that draws its model through Minecraft ModelPart rendering.",
            "local Models = Minecraft:GetService(\"ModelService\")\nModels:RegisterAdapter(\"com.example.client.model.CrateModel\", {\n    lid = \"root.lid\",\n})\nModels:RegisterRendererAdapter(\"com.example.client.render.CrateRenderer\", \"model\")\n\nlocal animation = Animation.new({\n    { Time = 0, Data = { lid = CFrame.identity } },\n    { Time = 0.35, Data = { lid = CFrame.Angles(math.rad(-80), 0, 0) } },\n})\nentity:GetAnimator():LoadAnimation(animation):Play()"));
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
        docs.register(MLuaDocBuilder.Registration.property("AnimationTrack", "AnimationTrack.HoldOnEnd", "boolean",
            "Whether non-looped playback keeps its final pose. Defaults to true; false fades out and releases the track when playback ends.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("AnimationTrack", "AnimationTrack.Weight", "number",
            "The current blend weight from zero to one. Assigning it fades toward the new weight.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("Animator", "Animator.Entity", "Entity",
            "The entity controlled by this animator.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.type("KeyframeDefinition", null, "A pose at a point in an animation. Time and Data are required; the easing values are optional."));
        docs.register(MLuaDocBuilder.Registration.field("KeyframeDefinition", "Time", "number", "Position in seconds. Keyframes are sorted by this value and duplicate times are rejected."));
        docs.register(MLuaDocBuilder.Registration.field("KeyframeDefinition", "Data", "table<string, CFrame>", "Maps each registered bone name to its pose at this keyframe."));
        docs.register(MLuaDocBuilder.Registration.field("KeyframeDefinition", "EasingStyle?", "EasingStyle", "Optional Enum.EasingStyle value, or its exact name, used to interpolate from the previous keyframe into this one. Defaults to Linear."));
        docs.register(MLuaDocBuilder.Registration.field("KeyframeDefinition", "EasingDirection?", "EasingDirection", "Optional Enum.EasingDirection value, or its exact name, used to interpolate from the previous keyframe into this one. Defaults to In."));
        docs.register(MLuaDocBuilder.Registration.guideStep("AnimationTrack", "React to animation markers",
            "AddMarker invokes callback(track, name, time) when playback crosses its named time in seconds. AddEvent remains useful for unnamed timing callbacks. A marker at zero runs as soon as playback starts. ClearEvents removes callbacks that have not fired yet.",
            "local Animations = Minecraft:GetService(\"Animation\")\nlocal animator = entity:GetAnimator()\nlocal animation = Animations:GetAnimation(\"myaddon:reload\")\nlocal track = animator:LoadAnimation(animation, Animation.AnimationPriority.Action)\n\ntrack:AddMarker(\"reload_start\", 0, function(activeTrack, name, time)\n    print(name, activeTrack.TrackId, time)\nend)\ntrack:AddMarker(\"magazine_inserted\", 0.65, function(activeTrack, name, time)\n    print(name, time)\nend)\ntrack:Play()\n\n-- Remove marker callbacks when they are no longer needed.\ntrack:ClearEvents()"));

        docs.register(MLuaDocBuilder.Registration.contribution(MLuaDocBuilder.Contribution.builder(id(), "animation:authoring-walkthrough")
            .section("Animation")
            .title("Create and play an entity animation")
            .description("Author keyframes in Lua, wait for a client entity to exist, and control the resulting AnimationTrack. The animation changes the rendered model; it does not change server-side entity state.")
            .order(10)
            .step("Client script", "Define keyframes for model bones",
                "Animation time is measured in seconds. Each Data table maps a bone name to a CFrame pose at that time. The names must match the bone paths exposed by the entity model's ModelService adapter; start with a known mapped bone and inspect the model adapter if no pose appears. Keep the definition at the top of the same client script used in the next steps.",
                "local look = Animation.new({\n    { Time = 0, Data = { head = CFrame.identity } },\n    { Time = 0.25, Data = { head = CFrame.Angles(0, math.rad(18), 0) } },\n    { Time = 0.5, Data = { head = CFrame.identity } },\n})\n\nprint(look.Length, table.concat(look:GetBoneNames(), \", \"))")
            .step("Client script", "Start it when the local player is ready",
                "Client scripts can start before a world has loaded. ClientTick fires after each client tick, so wait until GetLocalPlayer returns an entity. Track both its entity id and dimension: the id changes after respawn, while the dimension can change without replacing the local player.",
                "local Players = Minecraft:GetService(\"Players\")\nlocal Events = Minecraft:GetService(\"Events\")\nlocal activeEntityKey\nlocal activeTrack\n\nEvents.ClientTick:Connect(function()\n    local player = Players:GetLocalPlayer()\n    if not player then\n        activeEntityKey = nil\n        activeTrack = nil\n        return\n    end\n\n    local entityKey = tostring(player.Id) .. \"@\" .. player:GetDimension()\n    if entityKey == activeEntityKey then return end\n    activeEntityKey = entityKey\n\n    activeTrack = player:GetAnimator():LoadAnimation(look)\n    activeTrack.HoldOnEnd = false\n    activeTrack:Play(0.15)\nend)")
            .step("AnimationTrack", "Control playback and release it cleanly",
                "LoadAnimation returns a track for one entity. Tracks blend by priority and weight; use a fade when stopping or changing weight. A non-looped track holds its final pose unless HoldOnEnd is false. MLua also releases addon-owned tracks when their runtime unloads, but stop tracks yourself when ending a temporary effect early. This continues the same client script from the previous step.",
                "if activeTrack and activeTrack:IsPlaying() then\n    activeTrack:Stop(0.2)\nend")
            .build()));
    }

    @Override
    public void registerEvents(EventBridge events) {
        events.onClientWorldUnload(world -> discardDimensionState(world,
            dev.mlua.common.enums.Side.CLIENT));
        events.onClientEntityUnload(entity -> {
            if (entity instanceof Entity mcEntity) {
                MLuaAnimationController.removeEntity(MLuaEntityScope.of(mcEntity));
            }
        });
        events.onClientTickEnd(() -> {
            Minecraft mc = Minecraft.getInstance();
            Level level = mc.level;
            if (level == null) return;
            tickTrackedEntities(level);
        });

        events.onClientDisconnect(() -> MLuaAnimationController.clearSide(Side.CLIENT));
    }

    private static void tickTrackedEntities(Level level) {
        String dimension = level.dimension().location().toString();
        List<MLuaEntityScope> liveScopes = new java.util.ArrayList<>();
        for (MLuaEntityScope scope : MLuaAnimationController.getTrackedEntities()) {
            if (scope.side() != dev.mlua.common.enums.Side.CLIENT) continue;
            if (!scope.dimension().equals(dimension)) {
                // The client only owns one active level. When it changes dimensions,
                // discard client-side state from the level that was just left.
                discardStaleEntityState(scope);
                continue;
            }
            Entity entity = level.getEntity(scope.entityId());
            if (entity != null && entity.getUUID().equals(scope.uuid())) {
                liveScopes.add(scope);
            } else {
                // Tracks and animated poses belong to the live entity instance. Entity ids
                // can be reused after unload, so retaining the scope would leak state and
                // risk applying an old pose to a later entity in this dimension.
                discardStaleEntityState(scope);
            }
        }
        MLuaAnimationController.tickEntities(liveScopes, 0.05f);
    }

    private static void discardStaleEntityState(MLuaEntityScope scope) {
        MLuaAnimationController.removeEntity(scope);
        MLuaEntityWrapper.removeFromCache(scope);
    }

    private static void discardDimensionState(Object world, dev.mlua.common.enums.Side side) {
        if (!(world instanceof Level level)) return;
        String dimension = level.dimension().location().toString();
        for (MLuaEntityScope scope : MLuaAnimationController.getTrackedEntities()) {
            if (scope.side() == side && scope.dimension().equals(dimension)) {
                discardStaleEntityState(scope);
            }
        }
        // Manual poses do not create animation tracks, so they are absent from
        // getTrackedEntities() and need an independent dimension cleanup pass.
        dev.mlua.minecraft.animation.MLuaBonePose.clearDimension(dimension, side);
    }
}
