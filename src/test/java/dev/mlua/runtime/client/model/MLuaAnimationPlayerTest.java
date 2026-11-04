package dev.mlua.runtime.client.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaAnimationPlayerTest {
    @Test
    void stoppingAnAnimationClearsTheSelectedPose() {
        MLuaBBModelAsset asset = asset();
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();

        player.play("idle", asset, false, 1.0F);
        player.stop();

        assertNull(player.active());
        assertFalse(player.playing());
        assertEquals(0.0F, player.time(), 0.0001F);
    }

    @Test
    void fadesBetweenAnimationTracksOverTheRequestedDuration() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [
                {"name": "idle", "loop": "loop", "length": 1,
                 "animators": {"root": {"position": [
                   {"time": 0, "data_points": [{"x": 0, "y": 0, "z": 0}]}
                 ]}}},
                {"name": "walk", "loop": "loop", "length": 1,
                 "animators": {"root": {"position": [
                   {"time": 0, "data_points": [{"x": 10, "y": 0, "z": 0}]}
                 ]}}}
              ]
            }
            """);
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();
        MLuaBBModelAsset.Animation walk = asset.animations().get("walk");
        float[] sample = new float[3];

        player.play("idle", asset, true, 1.0F);
        player.tick(0.25F, asset);
        player.play("walk", asset, true, 1.0F, 1.0F);
        player.sampleInto(walk, "root", "position", sample, 0.0F);
        assertEquals(0.0F, sample[0], 0.0001F);

        player.tick(0.5F, asset);
        player.sampleInto(walk, "root", "position", sample, 1.0F);
        assertEquals(5.0F, sample[0], 0.0001F);

        player.tick(0.5F, asset);
        player.sampleInto(walk, "root", "position", sample, 1.0F);
        assertEquals(10.0F, sample[0], 0.0001F);
    }

    @Test
    void keepsTheFinalFadeTickInterpolatedUntilItsPartialFramesRender() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [
                {"name": "idle", "loop": "loop", "length": 1,
                 "animators": {"root": {"position": [
                   {"time": 0, "data_points": [{"x": 0, "y": 0, "z": 0}]}
                 ]}}},
                {"name": "walk", "loop": "loop", "length": 1,
                 "animators": {"root": {"position": [
                   {"time": 0, "data_points": [{"x": 10, "y": 0, "z": 0}]}
                 ]}}}
              ]
            }
            """);
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();
        MLuaBBModelAsset.Animation walk = asset.animations().get("walk");
        float[] sample = new float[3];

        player.play("idle", asset, true, 1.0F);
        player.play("walk", asset, true, 1.0F, 1.0F);
        player.tick(0.75F, asset);
        player.tick(0.25F, asset);

        player.sampleInto(walk, "root", "position", sample, 0.0F);
        assertEquals(7.5F, sample[0], 0.0001F);
        player.sampleInto(walk, "root", "position", sample, 0.5F);
        assertEquals(8.75F, sample[0], 0.0001F);
        player.sampleInto(walk, "root", "position", sample, 1.0F);
        assertEquals(10.0F, sample[0], 0.0001F);

        player.tick(0.05F, asset);
        player.sampleInto(walk, "root", "position", sample, 0.0F);
        assertEquals(10.0F, sample[0], 0.0001F);
    }

    @Test
    void keepsTickingWhenAShortOneShotEndsBeforeItsFade() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [
                {"name": "idle", "loop": "loop", "length": 1,
                 "animators": {"root": {"position": [
                   {"time": 0, "data_points": [{"x": 0, "y": 0, "z": 0}]}
                 ]}}},
                {"name": "attack", "loop": "once", "length": 0.1,
                 "animators": {"root": {"position": [
                   {"time": 0, "data_points": [{"x": 10, "y": 0, "z": 0}]}
                 ]}}}
              ]
            }
            """);
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();

        player.play("idle", asset, true, 1.0F);
        player.play("attack", asset, false, 1.0F, 1.0F);
        player.tick(0.1F, asset);

        assertFalse(player.playing());
        assertTrue(player.needsTick());

        player.tick(0.9F, asset);
        assertTrue(player.needsTick());
        player.tick(0.05F, asset);
        assertFalse(player.needsTick());
    }

    @Test
    void crossfadesRotationAlongTheShortQuaternionPath() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [
                {"name": "left", "loop": "loop", "length": 1,
                 "animators": {"root": {"rotation": [
                   {"time": 0, "data_points": [{"x": 0, "y": 0, "z": 170}]}
                 ]}}},
                {"name": "right", "loop": "loop", "length": 1,
                 "animators": {"root": {"rotation": [
                   {"time": 0, "data_points": [{"x": 0, "y": 0, "z": -170}]}
                 ]}}}
              ]
            }
            """);
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();
        MLuaBBModelAsset.Animation right = asset.animations().get("right");
        float[] sample = new float[3];

        player.play("left", asset, true, 1.0F);
        player.play("right", asset, true, 1.0F, 1.0F);
        player.tick(0.5F, asset);
        player.sampleInto(right, "root", "rotation", sample, 1.0F);

        assertEquals(180.0F, Math.abs(sample[2]), 0.001F);
    }

    @Test
    void pausingFreezesAnAnimationFadeUntilPlaybackResumes() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [
                {"name": "idle", "loop": "loop", "length": 1,
                 "animators": {"root": {"position": [
                   {"time": 0, "data_points": [{"x": 0, "y": 0, "z": 0}]}
                 ]}}},
                {"name": "walk", "loop": "loop", "length": 1,
                 "animators": {"root": {"position": [
                   {"time": 0, "data_points": [{"x": 10, "y": 0, "z": 0}]}
                 ]}}}
              ]
            }
            """);
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();
        MLuaBBModelAsset.Animation walk = asset.animations().get("walk");
        float[] sample = new float[3];

        player.play("idle", asset, true, 1.0F);
        player.play("walk", asset, true, 1.0F, 1.0F);
        player.tick(0.25F, asset);
        player.pause();
        player.tick(0.5F, asset);
        player.sampleInto(walk, "root", "position", sample, 1.0F);
        assertEquals(2.5F, sample[0], 0.0001F);

        player.resume();
        player.tick(0.75F, asset);
        player.sampleInto(walk, "root", "position", sample, 1.0F);
        assertEquals(10.0F, sample[0], 0.0001F);
    }

    @Test
    void naturallyHeldAnimationKeepsItsFinalPoseUntilStopped() {
        MLuaBBModelAsset asset = asset();
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();

        player.play("idle", asset, false, 1.0F);
        player.tick(2.0F, asset);

        assertEquals("idle", player.active());
        assertFalse(player.playing());
        assertEquals(1.0F, player.time(), 0.0001F);
    }

    @Test
    void onceAnimationReturnsToTheRestPoseAfterItsFinalFrame() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [{"name": "attack", "loop": "once", "length": 1,
                "animators": {"root": {"position": [
                  {"time": 0, "data_points": [{"x": 4, "y": 0, "z": 0}]},
                  {"time": 1, "data_points": [{"x": 12, "y": 0, "z": 0}]}
                ]}}}]
            }
            """);
        MLuaBBModelAsset.Animation attack = asset.animations().get("attack");
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();
        float[] sample = new float[3];

        player.play("attack", asset, null, 1.0F);
        player.tick(1.25F, asset);
        player.sampleInto(attack, "root", "position", sample, 1.0F);
        assertEquals(12.0F, sample[0], 0.0001F);
        assertTrue(player.needsTick());

        player.tick(0.05F, asset);
        player.sampleInto(attack, "root", "position", sample, 1.0F);
        assertEquals(0.0F, sample[0], 0.0001F);
        assertFalse(player.needsTick());
    }

    @Test
    void holdAnimationKeepsItsFinalPoseAfterPlaybackStops() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [{"name": "attack", "loop": "hold", "length": 1,
                "animators": {"root": {"position": [
                  {"time": 0, "data_points": [{"x": 4, "y": 0, "z": 0}]},
                  {"time": 1, "data_points": [{"x": 12, "y": 0, "z": 0}]}
                ]}}}]
            }
            """);
        MLuaBBModelAsset.Animation attack = asset.animations().get("attack");
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();
        float[] sample = new float[3];

        player.play("attack", asset, null, 1.0F);
        player.tick(1.25F, asset);
        player.tick(0.05F, asset);
        player.sampleInto(attack, "root", "position", sample, 1.0F);

        assertEquals(12.0F, sample[0], 0.0001F);
        assertFalse(player.needsTick());
    }

    @Test
    void usesTheAnimationLoopSettingWhenNoOverrideIsProvided() {
        MLuaBBModelAsset asset = loopingAsset();
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();

        player.play("walk", asset, null, 1.0F);
        player.tick(1.25F, asset);

        assertTrue(player.playing());
        assertEquals(0.25F, player.time(), 0.0001F);
    }

    @Test
    void explicitFalseOverridesALoopingAnimation() {
        MLuaBBModelAsset asset = loopingAsset();
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();

        player.play("walk", asset, false, 1.0F);
        player.tick(1.25F, asset);

        assertFalse(player.playing());
        assertEquals(1.0F, player.time(), 0.0001F);
        float[] sample = new float[3];
        player.sampleInto(asset.animations().get("walk"), "root", "position", sample, 1.0F);
        assertEquals(10.0F, sample[0], 0.0001F);
    }

    @Test
    void stopsCleanlyWhenReloadedAssetNoLongerContainsTheActiveAnimation() {
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();
        player.play("idle", asset(), true, 1.0F);
        MLuaBBModelAsset reloaded = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [{"name": "walk", "loop": "loop", "length": 1, "animators": {}}]
            }
            """);

        player.tick(0.05F, reloaded);

        assertNull(player.active());
        assertFalse(player.playing());
        assertEquals(0.0F, player.time(), 0.0001F);
    }

    @Test
    void zeroLengthAnimationsStopWithoutAdvancing() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [{"name": "instant", "loop": "loop", "length": 0, "animators": {}}]
            }
            """);
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();
        player.play("instant", asset, true, 1.0F);

        player.tick(0.05F, asset);

        assertEquals("instant", player.active());
        assertFalse(player.playing());
        assertEquals(0.0F, player.time(), 0.0001F);
    }

    @Test
    void keyframesBeyondDeclaredLengthRemainReachable() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [{"name": "late_pose", "loop": "hold", "length": 1,
                "animators": {"root": {"position": [
                  {"time": 0, "data_points": [{"x": 0, "y": 0, "z": 0}]},
                  {"time": 2, "data_points": [{"x": 8, "y": 0, "z": 0}]}
                ]}}}]
            }
            """);
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();

        player.play("late_pose", asset, false, 1.0F);
        player.tick(1.5F, asset);

        MLuaBBModelAsset.Animation animation = asset.animations().get("late_pose");
        assertEquals(2.0F, animation.length(), 0.0001F);
        assertTrue(player.playing());
        assertEquals(1.5F, player.time(), 0.0001F);
        float[] sample = new float[3];
        player.sampleInto(animation, "root", "position", sample, 1.0F);
        assertEquals(6.0F, sample[0], 0.0001F);
    }

    @Test
    void loopInterpolationKeepsTheEndPoseAtTheWrapBoundary() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [{
                "name": "walk", "loop": "loop", "length": 1,
                "animators": {"root": {"position": [
                  {"time": 0, "data_points": [{"x": 0, "y": 0, "z": 0}]},
                  {"time": 1, "data_points": [{"x": 10, "y": 0, "z": 0}]}
                ]}}
              }]
            }
            """);
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();
        player.play("walk", asset, true, 1.0F);
        player.tick(0.9F, asset);
        player.tick(0.2F, asset);

        float[] sample = new float[3];
        MLuaBBModelAsset.Animation animation = asset.animations().get("walk");
        player.sampleInto(animation, "root", "position", sample, 0.5F);
        assertEquals(10.0F, sample[0], 0.0001F);
        player.sampleInto(animation, "root", "position", sample, 1.0F);
        assertEquals(1.0F, sample[0], 0.0001F);
    }

    @Test
    void stepKeyframesKeepTheirIncomingAndOutgoingValues() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [{"name": "step", "loop": "hold", "length": 2,
                "animators": {"root": {"position": [
                  {"time": 0, "interpolation": "step", "data_points": [
                    {"x": 0, "y": 0, "z": 0}, {"x": 2, "y": 0, "z": 0}]},
                  {"time": 1, "data_points": [
                    {"x": 8, "y": 0, "z": 0}, {"x": 10, "y": 0, "z": 0}]}
                ]}}}]
            }
            """);
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();
        MLuaBBModelAsset.Animation animation = asset.animations().get("step");
        float[] sample = sampleAt(player, asset, animation, 0.5F);
        assertEquals(2.0F, sample[0], 0.0001F);

        sample = sampleAt(player, asset, animation, 1.0F);
        assertEquals(8.0F, sample[0], 0.0001F);
        sample = sampleAt(player, asset, animation, 1.5F);
        assertEquals(10.0F, sample[0], 0.0001F);
    }

    @Test
    void catmullRomUsesTheCurveWhenEitherAdjacentKeyframeSelectsIt() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [{"name": "swing", "loop": "hold", "length": 3,
                "animators": {"root": {"position": [
                  {"time": 0, "data_points": [{"x": 0, "y": 0, "z": 0}]},
                  {"time": 1, "interpolation": "catmullrom", "data_points": [{"x": 1, "y": 0, "z": 0}]},
                  {"time": 2, "data_points": [{"x": 0, "y": 0, "z": 0}]},
                  {"time": 3, "data_points": [{"x": 0, "y": 0, "z": 0}]}
                ]}}}]
            }
            """);
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();
        MLuaBBModelAsset.Animation animation = asset.animations().get("swing");
        float[] sample = sampleAt(player, asset, animation, 0.5F);
        assertEquals(0.5625F, sample[0], 0.0001F,
                "A Catmull-Rom keyframe shapes the neighboring segment as well");

        sample = sampleAt(player, asset, animation, 1.5F);

        assertEquals(0.5625F, sample[0], 0.0001F);
    }

    @Test
    void catmullRomWrapsNeighborsOnlyWhenPlaybackLoops() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [{"name": "swing", "loop": "hold", "length": 3,
                "animators": {"root": {"position": [
                  {"time": 0, "interpolation": "catmullrom", "data_points": [{"x": 10, "y": 0, "z": 0}]},
                  {"time": 1, "data_points": [{"x": 0, "y": 0, "z": 0}]},
                  {"time": 2, "data_points": [{"x": 0, "y": 0, "z": 0}]},
                  {"time": 3, "data_points": [{"x": 20, "y": 0, "z": 0}]}
                ]}}}]
            }
            """);
        MLuaBBModelAsset.Animation animation = asset.animations().get("swing");
        float[] sample = new float[3];
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();

        player.play("swing", asset, false, 1.0F);
        player.tick(0.25F, asset);
        player.sampleInto(animation, "root", "position", sample, 1.0F);
        assertEquals(7.96875F, sample[0], 0.0001F);

        player.play("swing", asset, true, 1.0F);
        player.tick(0.25F, asset);
        player.sampleInto(animation, "root", "position", sample, 1.0F);
        assertEquals(8.671875F, sample[0], 0.0001F);
    }

    @Test
    void catmullRomDoesNotCurveAcrossDiscontinuousKeyframeValues() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [{"name": "swing", "loop": "hold", "length": 3,
                "animators": {"root": {"position": [
                  {"time": 0, "data_points": [{"x": 0, "y": 0, "z": 0}]},
                  {"time": 1, "interpolation": "catmullrom", "data_points": [
                    {"x": 8, "y": 0, "z": 0}, {"x": 10, "y": 0, "z": 0}]},
                  {"time": 2, "data_points": [{"x": 20, "y": 0, "z": 0}]},
                  {"time": 3, "data_points": [{"x": 0, "y": 0, "z": 0}]}
                ]}}}]
            }
            """);
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();
        MLuaBBModelAsset.Animation animation = asset.animations().get("swing");
        player.play("swing", asset, false, 1.0F);
        player.tick(1.5F, asset);

        float[] sample = new float[3];
        player.sampleInto(animation, "root", "position", sample, 1.0F);

        assertEquals(16.25F, sample[0], 0.0001F);

        player.setTimePosition(0.5F, asset);
        player.sampleInto(animation, "root", "position", sample);
        assertEquals(4.0F, sample[0], 0.0001F);
    }

    @Test
    void bezierKeyframesUseTheirTimeAndValueHandles() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [{"name": "curve", "loop": "hold", "length": 1,
                "animators": {"root": {"position": [
                  {"time": 0, "interpolation": "bezier", "data_points": [{"x": 0, "y": 0, "z": 0}],
                    "bezier_right_time": [0.1, 0.1, 0.1], "bezier_right_value": [0, 0, 0]},
                  {"time": 1, "data_points": [{"x": 10, "y": 0, "z": 0}],
                    "bezier_left_time": [-0.1, -0.1, -0.1], "bezier_left_value": [0, 0, 0]}
                ]}}}]
            }
            """);
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();
        float[] sample = sampleAt(player, asset, asset.animations().get("curve"), 0.25F);

        assertTrue(sample[0] > 0.0F && sample[0] < 2.5F,
                "Blockbench's zero-value handles ease this segment below linear at one quarter");
    }

    @Test
    void bezierKeyframeHandlesShapeTheSegmentLeadingIntoIt() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [{"name": "curve", "loop": "hold", "length": 2,
                "animators": {"root": {"position": [
                  {"time": 0, "data_points": [{"x": 0, "y": 0, "z": 0}]},
                  {"time": 1, "interpolation": "bezier", "bezier_left_value": [-8, 0, 0],
                    "data_points": [{"x": 10, "y": 0, "z": 0}]},
                  {"time": 2, "data_points": [{"x": 20, "y": 0, "z": 0}]}
                ]}}}]
            }
            """);
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();
        float[] sample = sampleAt(player, asset, asset.animations().get("curve"), 0.5F);

        assertEquals(2.0F, sample[0], 0.0001F,
                "The arriving keyframe's left Bezier handle shapes the preceding segment");
    }

    @Test
    void ignoresNonBlockbenchEasingFieldsAndKeepsLinearInterpolation() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [{"name": "walk", "loop": "hold", "length": 1,
                "animators": {"root": {"position": [
                  {"time": 0, "interpolation": "linear", "easing": "easeOutQuad",
                    "data_points": [{"x": 0, "y": 0, "z": 0}]},
                  {"time": 1, "data_points": [{"x": 10, "y": 0, "z": 0}]}
                ]}}}]
            }
            """);
        float[] sample = sampleAt(new MLuaAnimationPlayer(), asset, asset.animations().get("walk"), 0.5F);

        assertEquals(5.0F, sample[0], 0.0001F);
    }

    @Test
    void blockbenchScaleUsesMultiplicativeFactorsAndIdentityDefaults() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [{"name": "scale", "loop": "hold", "length": 1,
                "animators": {"root": {"scale": [
                  {"time": 0, "data_points": [{"x": 1, "y": 1, "z": 1}]},
                  {"time": 1, "data_points": [{"x": 3}]}
                ]}}}]
            }
            """);
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();
        float[] sample = sampleAt(player, asset, asset.animations().get("scale"), "scale", 0.5F);
        assertEquals(2.0F, sample[0], 0.0001F);
        assertEquals(1.0F, sample[1], 0.0001F);
        assertEquals(1.0F, sample[2], 0.0001F);

        float[] missingScale = {0.0F, 0.0F, 0.0F};
        player.sampleInto(asset.animations().get("scale"), "other_bone", "scale", missingScale, 1.0F);
        assertEquals(1.0F, missingScale[0], 0.0001F);
        assertEquals(1.0F, missingScale[1], 0.0001F);
        assertEquals(1.0F, missingScale[2], 0.0001F);
    }

    private static float[] sampleAt(MLuaAnimationPlayer player, MLuaBBModelAsset asset,
                                   MLuaBBModelAsset.Animation animation, float time) {
        return sampleAt(player, asset, animation, "position", time);
    }

    private static float[] sampleAt(MLuaAnimationPlayer player, MLuaBBModelAsset asset,
                                   MLuaBBModelAsset.Animation animation, String channel, float time) {
        player.play(animation.name(), asset, false, 1.0F);
        player.tick(time, asset);
        float[] sample = new float[3];
        player.sampleInto(animation, "root", channel, sample, 1.0F);
        return sample;
    }

    private static MLuaBBModelAsset asset() {
        return MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [{"name": "idle", "loop": "hold", "length": 1, "animators": {}}]
            }
            """);
    }

    private static MLuaBBModelAsset loopingAsset() {
        return MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [{"name": "walk", "loop": "loop", "length": 1,
                "animators": {"root": {"position": [
                  {"time": 0, "data_points": [{"x": 10, "y": 0, "z": 0}]}
                ]}}}]
            }
            """);
    }
}
