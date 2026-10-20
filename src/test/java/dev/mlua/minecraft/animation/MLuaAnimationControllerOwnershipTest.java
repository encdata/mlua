package dev.mlua.minecraft.animation;

import dev.mlua.common.enums.AnimationPriority;
import dev.mlua.common.enums.EasingDirection;
import dev.mlua.common.enums.EasingStyle;
import dev.mlua.common.enums.Side;
import dev.mlua.math.MLuaCFrame;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaAnimationControllerOwnershipTest {
    @AfterEach
    void clearTracks() {
        MLuaAnimationController.clearAll();
    }

    @Test
    void identicalEntityTracksAndCallbacksRemainOwnedByTheirRuntime() {
        MLuaEntityScope entity = new MLuaEntityScope(Side.SERVER, "minecraft:overworld", UUID.randomUUID(), 9);
        Object firstRuntime = new Object();
        Object secondRuntime = new Object();
        var firstGlobals = JsePlatform.standardGlobals();
        var secondGlobals = JsePlatform.standardGlobals();
        firstGlobals.load("count = 0").call();
        secondGlobals.load("count = 0").call();
        MLuaAnimation animation = new MLuaAnimation(List.of(
                new MLuaAnimation.Keyframe(0, Map.of(), EasingStyle.Linear, EasingDirection.In),
                new MLuaAnimation.Keyframe(1, Map.of(), EasingStyle.Linear, EasingDirection.In)));

        MLuaAnimationTrack first = MLuaAnimationController.loadAnimation(entity, animation, AnimationPriority.Idle, firstRuntime);
        MLuaAnimationTrack second = MLuaAnimationController.loadAnimation(entity, animation, AnimationPriority.Idle, secondRuntime);
        first.addEvent(0.05f, firstGlobals.load("count = count + 1"));
        second.addEvent(0.05f, secondGlobals.load("count = count + 1"));
        second.addEvent(0.15f, secondGlobals.load("count = count + 1"));
        first.play();
        second.play();
        assertEquals(1, MLuaAnimationController.getTracks(entity, firstRuntime).size());
        assertEquals(1, MLuaAnimationController.getTracks(entity, secondRuntime).size());

        MLuaAnimationController.tickEntity(entity, 0.1f);
        assertEquals(1, firstGlobals.get("count").toint());
        assertEquals(1, secondGlobals.get("count").toint());

        MLuaAnimationController.clearRuntime(firstRuntime, Side.SERVER);
        MLuaAnimationController.tickEntity(entity, 0.1f);

        assertEquals(1, firstGlobals.get("count").toint());
        assertEquals(2, secondGlobals.get("count").toint());
        assertEquals(0, MLuaAnimationController.getTracks(entity, firstRuntime).size());
        assertEquals(1, MLuaAnimationController.getTracks(entity, secondRuntime).size());
    }

    @Test
    void poseBlendingCombinesTracksWithinPriorityThenAppliesHigherPriorityLayers() {
        MLuaEntityScope entity = new MLuaEntityScope(Side.CLIENT, "minecraft:overworld", UUID.randomUUID(), 41);
        Object runtime = new Object();
        String bone = "testBone";
        MLuaAnimationTrack idleFirst = MLuaAnimationController.loadAnimation(entity,
            staticPose(bone, 0.0), AnimationPriority.Idle, runtime);
        MLuaAnimationTrack idleSecond = MLuaAnimationController.loadAnimation(entity,
            staticPose(bone, 10.0), AnimationPriority.Idle, runtime);
        MLuaAnimationTrack action = MLuaAnimationController.loadAnimation(entity,
            staticPose(bone, 20.0), AnimationPriority.Action, runtime);
        idleFirst.fadeTo(1.0f, 0.0f);
        idleSecond.fadeTo(1.0f, 0.0f);
        action.fadeTo(0.5f, 0.0f);

        MLuaAnimationController.tickEntity(entity, 0.1f);

        // Idle tracks average to x=5; the half-weight Action layer blends that to x=12.5.
        assertEquals(12.5, MLuaBonePose.getBonePose(entity, bone, runtime).x(), 1.0e-6);
    }

    private static MLuaAnimation staticPose(String bone, double x) {
        return new MLuaAnimation(List.of(new MLuaAnimation.Keyframe(0,
            Map.of(bone, MLuaCFrame.at(x, 0, 0)), EasingStyle.Linear, EasingDirection.In)));
    }

    @Test
    void clearingOneAddonReleasesItsTracksAndPreservesAnotherAddonsCallbacks() {
        UUID entityUuid = UUID.randomUUID();
        MLuaEntityScope entity = new MLuaEntityScope(Side.SERVER, "minecraft:overworld", entityUuid, 10);
        MLuaEntityScope clientEntity = new MLuaEntityScope(Side.CLIENT, "minecraft:overworld", entityUuid, 10);
        Object runtime = new Object();
        String firstAddon = "first.addon";
        String secondAddon = "second.addon";
        var firstGlobals = JsePlatform.standardGlobals();
        var secondGlobals = JsePlatform.standardGlobals();
        var clientGlobals = JsePlatform.standardGlobals();
        firstGlobals.load("count = 0").call();
        secondGlobals.load("count = 0").call();
        clientGlobals.load("count = 0").call();
        MLuaAnimation animation = new MLuaAnimation(List.of(
                new MLuaAnimation.Keyframe(0, Map.of(), EasingStyle.Linear, EasingDirection.In),
                new MLuaAnimation.Keyframe(1, Map.of(), EasingStyle.Linear, EasingDirection.In)));

        MLuaAnimationTrack first = MLuaAnimationController.loadAnimation(
                entity, animation, AnimationPriority.Idle, runtime, firstAddon);
        MLuaAnimationTrack second = MLuaAnimationController.loadAnimation(
                entity, animation, AnimationPriority.Idle, runtime, secondAddon);
        MLuaAnimationTrack clientTrack = MLuaAnimationController.loadAnimation(
                clientEntity, animation, AnimationPriority.Idle, runtime, firstAddon);
        first.addEvent(0.05f, firstGlobals.load("count = count + 1"));
        first.addEvent(0.15f, firstGlobals.load("count = count + 1"));
        second.addEvent(0.05f, secondGlobals.load("count = count + 1"));
        second.addEvent(0.15f, secondGlobals.load("count = count + 1"));
        clientTrack.addEvent(0.05f, clientGlobals.load("count = count + 1"));
        clientTrack.addEvent(0.15f, clientGlobals.load("count = count + 1"));
        first.play();
        second.play();
        clientTrack.play();

        MLuaAnimationController.tickEntity(entity, 0.1f);
        MLuaAnimationController.tickEntity(clientEntity, 0.1f);
        assertEquals(1, firstGlobals.get("count").toint());
        assertEquals(1, secondGlobals.get("count").toint());
        assertEquals(1, clientGlobals.get("count").toint());

        MLuaAnimationController.clearOwned(runtime, firstAddon, Side.SERVER);

        assertTrue(first.isReleased());
        assertFalse(second.isReleased());
        assertTrue(MLuaAnimationController.getTracks(entity, runtime, firstAddon).isEmpty());
        assertEquals(List.of(second), MLuaAnimationController.getTracks(entity, runtime, secondAddon));
        assertFalse(clientTrack.isReleased(), "server addon cleanup must not release the client's matching addon track");
        assertEquals(List.of(clientTrack), MLuaAnimationController.getTracks(clientEntity, runtime, firstAddon));
        MLuaAnimationController.tickEntity(entity, 0.1f);
        MLuaAnimationController.tickEntity(clientEntity, 0.1f);

        assertEquals(1, firstGlobals.get("count").toint(), "unloaded addon callback must not be retained or invoked");
        assertEquals(2, secondGlobals.get("count").toint(), "another addon's track must keep ticking");
        assertEquals(2, clientGlobals.get("count").toint(), "server cleanup must preserve the matching client callback");
    }

    @Test
    void clearingOrReleasingTrackReturnsGlobalMarkerCapacity() {
        int baseline = MLuaAnimationTrack.activeEventCallbackCount();
        MLuaAnimation animation = new MLuaAnimation(List.of(
                new MLuaAnimation.Keyframe(0, Map.of(), EasingStyle.Linear, EasingDirection.In)));
        MLuaAnimationTrack track = new MLuaAnimationTrack(12, animation, AnimationPriority.Idle);
        var callback = JsePlatform.standardGlobals().load("return true");
        try {
            track.addEvent(0, callback);
            track.addEvent(0, callback);
            assertEquals(baseline + 2, MLuaAnimationTrack.activeEventCallbackCount());

            track.clearEvents();
            assertEquals(baseline, MLuaAnimationTrack.activeEventCallbackCount());

            track.addEvent(0, callback);
            track.release();
            assertEquals(baseline, MLuaAnimationTrack.activeEventCallbackCount());
        } finally {
            track.release();
        }
    }

    @Test
    void oneAddonCannotConsumeOtherAddonsAnimationMarkerCapacity() {
        int perOwnerLimit = MLuaAnimationTrack.MAX_ACTIVE_EVENT_CALLBACKS_PER_OWNER;
        int callbacksPerTrack = MLuaAnimationTrack.MAX_EVENTS_PER_TRACK;
        assertEquals(0, perOwnerLimit % callbacksPerTrack,
            "The owner limit should be fillable exactly using complete tracks");

        Object runtime = new Object();
        String limitedAddon = "mlua_test:marker_limit";
        String otherAddon = "mlua_test:other_markers";
        int ownerBaseline = MLuaAnimationTrack.activeEventCallbackCount(runtime, limitedAddon);
        int otherBaseline = MLuaAnimationTrack.activeEventCallbackCount(runtime, otherAddon);
        MLuaAnimation animation = new MLuaAnimation(List.of(
            new MLuaAnimation.Keyframe(0, Map.of(), EasingStyle.Linear, EasingDirection.In)));
        var callback = JsePlatform.standardGlobals().load("return true");
        List<MLuaAnimationTrack> limitedTracks = new java.util.ArrayList<>();
        MLuaAnimationTrack rejectedTrack = new MLuaAnimationTrack(31, animation, AnimationPriority.Idle,
            runtime, limitedAddon);
        MLuaAnimationTrack otherTrack = new MLuaAnimationTrack(32, animation, AnimationPriority.Idle,
            runtime, otherAddon);
        try {
            int trackCount = perOwnerLimit / callbacksPerTrack;
            for (int trackIndex = 0; trackIndex < trackCount; trackIndex++) {
                MLuaAnimationTrack track = new MLuaAnimationTrack(33 + trackIndex, animation,
                    AnimationPriority.Idle, runtime, limitedAddon);
                limitedTracks.add(track);
                for (int eventIndex = 0; eventIndex < callbacksPerTrack; eventIndex++) {
                    track.addEvent(0, callback);
                }
            }

            assertEquals(perOwnerLimit, MLuaAnimationTrack.activeEventCallbackCount(runtime, limitedAddon));
            assertThrows(IllegalStateException.class, () -> rejectedTrack.addEvent(0, callback));
            otherTrack.addEvent(0, callback);
            assertEquals(otherBaseline + 1,
                MLuaAnimationTrack.activeEventCallbackCount(runtime, otherAddon));

            limitedTracks.get(0).clearEvents();
            for (int eventIndex = 0; eventIndex < callbacksPerTrack; eventIndex++) {
                rejectedTrack.addEvent(0, callback);
            }
            assertEquals(perOwnerLimit,
                MLuaAnimationTrack.activeEventCallbackCount(runtime, limitedAddon));
        } finally {
            for (MLuaAnimationTrack track : limitedTracks) track.release();
            rejectedTrack.release();
            otherTrack.release();
        }

        assertEquals(ownerBaseline, MLuaAnimationTrack.activeEventCallbackCount(runtime, limitedAddon));
        assertEquals(otherBaseline, MLuaAnimationTrack.activeEventCallbackCount(runtime, otherAddon));
    }

    @Test
    void failingMarkerReleasesItsAddonRetentionCapacityImmediately() {
        Object runtime = new Object();
        String addon = "mlua_test:failing_marker";
        int globalBaseline = MLuaAnimationTrack.activeEventCallbackCount();
        int ownerBaseline = MLuaAnimationTrack.activeEventCallbackCount(runtime, addon);
        MLuaAnimation animation = new MLuaAnimation(List.of(
            new MLuaAnimation.Keyframe(0, Map.of(), EasingStyle.Linear, EasingDirection.In)));
        MLuaAnimationTrack track = new MLuaAnimationTrack(41, animation, AnimationPriority.Idle,
            runtime, addon);
        try {
            track.addEvent(0, JsePlatform.standardGlobals().load("error('marker failure')"));
            assertEquals(ownerBaseline + 1, MLuaAnimationTrack.activeEventCallbackCount(runtime, addon));

            track.play();
            track.tick(0.05f);

            assertEquals(ownerBaseline, MLuaAnimationTrack.activeEventCallbackCount(runtime, addon));
            assertEquals(globalBaseline, MLuaAnimationTrack.activeEventCallbackCount());
        } finally {
            track.release();
        }
    }

    @Test
    void nonFatalJavaErrorInMarkerDoesNotAbortOtherAnimationEvents() {
        Object runtime = new Object();
        String addon = "mlua_test:java_marker_error";
        int globalBaseline = MLuaAnimationTrack.activeEventCallbackCount();
        int ownerBaseline = MLuaAnimationTrack.activeEventCallbackCount(runtime, addon);
        MLuaAnimation animation = new MLuaAnimation(List.of(
            new MLuaAnimation.Keyframe(0, Map.of(), EasingStyle.Linear, EasingDirection.In)));
        MLuaAnimationTrack track = new MLuaAnimationTrack(42, animation, AnimationPriority.Idle,
            runtime, addon);
        var globals = JsePlatform.standardGlobals();
        globals.load("count = 0").call();
        try {
            track.addEvent(0, new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    throw new AssertionError("non-fatal callback failure");
                }
            });
            track.addEvent(0, globals.load("count = count + 1"));

            track.play();
            track.tick(0.05f);

            assertEquals(1, globals.get("count").toint(),
                "a failed marker must not abort later callbacks in the same animation update");
            assertEquals(ownerBaseline + 1,
                MLuaAnimationTrack.activeEventCallbackCount(runtime, addon),
                "the failed callback should release its marker slot immediately");
            track.clearEvents();
            assertEquals(ownerBaseline, MLuaAnimationTrack.activeEventCallbackCount(runtime, addon));
            assertEquals(globalBaseline, MLuaAnimationTrack.activeEventCallbackCount());
        } finally {
            track.release();
        }
    }
}
