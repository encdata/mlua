package dev.mlua.runtime.client.model;

import java.util.List;
import java.util.Map;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaBBModelAssetAnimationFormatTest {
    @Test
    void keepsTheOriginalJavaAnimationValueConstructors() {
        MLuaBBModelAsset.Cube cube = new MLuaBBModelAsset.Cube("cube", "cube",
            new float[]{0, 0, 0}, new float[]{2, 4, 6}, Map.of());
        MLuaBBModelAsset.Animation animation = new MLuaBBModelAsset.Animation(
            "idle", "loop", 1.0F, Map.of());
        MLuaBBModelAsset.Keyframe keyframe = new MLuaBBModelAsset.Keyframe(
            0.0F, new float[]{1, 2, 3}, "linear", "linear");

        assertEquals(1.0F, cube.origin(0), 0.0001F);
        assertFalse(animation.usesQuaternionRotation("root"));
        assertEquals(2.0F, keyframe.value(1), 0.0001F);
        assertEquals(2.0F, keyframe.outgoingValue(1), 0.0001F);
    }

    @Test
    void readsKeyframesFromTheFormatBlockbenchExports() {
        String json = """
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [{"name": "swing", "length": 1, "animators": {
                "root-id": {"name": "root", "type": "bone", "keyframes": [
                  {"channel": "rotation", "time": 1, "interpolation": "linear",
                   "data_points": [{"x": 45, "y": 0, "z": 0}]},
                  {"channel": "rotation", "time": 0, "interpolation": "linear",
                   "data_points": [{"x": 0, "y": 0, "z": 0}]}
                ]},
                "effects": {"name": "effects", "type": "effect", "keyframes": [
                  {"channel": "particle", "time": 2, "data_points": [{"effect": "smoke"}]}
                ]}
              }}]
            }
            """;
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse(json);

        MLuaBBModelAsset.Animation animation = asset.animations().get("swing");
        MLuaBBModelAsset.Animation separateAnimation = MLuaBBModelAsset.parseAnimations(json).get("swing");
        List<MLuaBBModelAsset.Keyframe> keyframes = animation
            .tracks().get("root-id").get("rotation");

        assertEquals(2, keyframes.size());
        assertEquals(2.0F, animation.length(), 0.0001F);
        assertEquals(2, separateAnimation.tracks().get("root-id").get("rotation").size());
        assertEquals(0.0F, keyframes.get(0).time(), 0.0001F);
        assertEquals(45.0F, keyframes.get(1).value(0), 0.0001F);
        assertEquals(0, animation.tracks().get("effects").size());
        float[] rotation = new float[3];
        MLuaBBModelAnimationSampler.sampleInto(animation, "root-id", "rotation", rotation, 0.5D, false);
        assertEquals(22.5F, rotation[0], 0.0001F);
    }

    @Test
    void quaternionEnabledBoneRotationsUseShortestArcSlerp() {
        String json = """
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [{"name": "turn", "length": 1, "animators": {
                "root-id": {"name": "root", "type": "bone", "quaternion_interpolation": true,
                  "keyframes": [
                    {"channel": "rotation", "time": 0, "interpolation": "linear",
                     "data_points": [{"x": 90, "y": 0, "z": 0}]},
                    {"channel": "rotation", "time": 1, "interpolation": "linear",
                     "data_points": [{"x": 0, "y": 90, "z": 0}]}
                  ]}
              }}]
            }
            """;
        MLuaBBModelAsset.Animation animation = MLuaBBModelAsset.parse(json)
            .animations().get("turn");
        float[] rotation = new float[3];

        assertTrue(animation.usesQuaternionRotation("root-id"));
        MLuaBBModelAnimationSampler.sampleInto(animation, "root-id", "rotation",
            rotation, 0.5D, false);

        Quaternionf before = rotation(90, 0, 0);
        Quaternionf after = rotation(0, 90, 0);
        Quaternionf expected = before.slerp(after, 0.5F);
        Quaternionf actual = rotation(rotation[0], rotation[1], rotation[2]);
        assertEquals(1.0F, Math.abs(expected.dot(actual)), 0.0001F);
    }

    @Test
    void quaternionInterpolationKeepsTheBoneBaseRotation() {
        String json = """
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [{"name": "turn", "length": 1, "animators": {
                "root-id": {"quaternion_interpolation": true, "keyframes": [
                  {"channel": "rotation", "time": 0,
                   "data_points": [{"x": 80, "y": 10, "z": -20}]},
                  {"channel": "rotation", "time": 1,
                   "data_points": [{"x": -15, "y": 70, "z": 35}]}
                ]}
              }}]
            }
            """;
        MLuaBBModelAsset.Animation animation = MLuaBBModelAsset.parse(json)
            .animations().get("turn");
        MLuaBBModelAsset.Bone bone = new MLuaBBModelAsset.Bone("root-id", "root", null,
            new float[]{0, 0, 0}, new float[]{15, -25, 40});
        float[] rotation = new float[3];
        MLuaBBModelAnimationSampler.sampleInto(animation, bone, "rotation", rotation,
            0.5D, false, new MLuaBBModelAnimationSampler.QuaternionScratch());

        Quaternionf base = rotation(bone.rotation(0), bone.rotation(1), bone.rotation(2));
        Quaternionf before = new Quaternionf(base).mul(rotation(80, 10, -20));
        Quaternionf after = new Quaternionf(base).mul(rotation(-15, 70, 35));
        Quaternionf expected = before.slerp(after, 0.5F);
        Quaternionf actual = rotation(bone.rotation(0) + rotation[0],
            bone.rotation(1) + rotation[1], bone.rotation(2) + rotation[2]);

        assertEquals(1.0F, Math.abs(expected.dot(actual)), 0.0001F);
    }

    private static Quaternionf rotation(float x, float y, float z) {
        return new Quaternionf().rotationZYX((float) Math.toRadians(z),
            (float) Math.toRadians(y), (float) Math.toRadians(x));
    }

    @Test
    void legacyRotationKeyframesInvertXAndYButKeepZ() {
        MLuaBBModelAsset.Keyframe keyframe = rotationKeyframe("4.10");

        assertArrayEquals(new float[]{-2.0F, -3.0F, 4.0F}, keyframe.value(), 0.0001F);
        assertArrayEquals(new float[]{-5.0F, -6.0F, 7.0F}, keyframe.outgoingValue(), 0.0001F);
        assertArrayEquals(new float[]{-8.0F, -9.0F, 10.0F}, keyframe.bezierLeftValue(), 0.0001F);
        assertArrayEquals(new float[]{-11.0F, -12.0F, 13.0F}, keyframe.bezierRightValue(), 0.0001F);
    }

    @Test
    void legacyPositionKeyframesInvertOnlyXIncludingBezierHandles() {
        MLuaBBModelAsset.Keyframe keyframe = keyframe("4.10", "position");

        assertArrayEquals(new float[]{-2.0F, 3.0F, 4.0F}, keyframe.value(), 0.0001F);
        assertArrayEquals(new float[]{-5.0F, 6.0F, 7.0F}, keyframe.outgoingValue(), 0.0001F);
        assertArrayEquals(new float[]{-8.0F, 9.0F, 10.0F}, keyframe.bezierLeftValue(), 0.0001F);
        assertArrayEquals(new float[]{-11.0F, 12.0F, 13.0F}, keyframe.bezierRightValue(), 0.0001F);
    }

    @Test
    void blockbenchFiveRotationValuesAreAlreadyInTheCurrentDirection() {
        MLuaBBModelAsset.Keyframe keyframe = rotationKeyframe("5.0");

        assertArrayEquals(new float[]{2.0F, 3.0F, 4.0F}, keyframe.value(), 0.0001F);
        assertArrayEquals(new float[]{5.0F, 6.0F, 7.0F}, keyframe.outgoingValue(), 0.0001F);
    }

    @Test
    void blockbenchFivePositionValuesAreNotInverted() {
        MLuaBBModelAsset.Keyframe keyframe = keyframe("5.0", "position");

        assertArrayEquals(new float[]{2.0F, 3.0F, 4.0F}, keyframe.value(), 0.0001F);
        assertArrayEquals(new float[]{5.0F, 6.0F, 7.0F}, keyframe.outgoingValue(), 0.0001F);
        assertArrayEquals(new float[]{8.0F, 9.0F, 10.0F}, keyframe.bezierLeftValue(), 0.0001F);
        assertArrayEquals(new float[]{11.0F, 12.0F, 13.0F}, keyframe.bezierRightValue(), 0.0001F);
    }

    private static MLuaBBModelAsset.Keyframe rotationKeyframe(String formatVersion) {
        return keyframe(formatVersion, "rotation");
    }

    private static MLuaBBModelAsset.Keyframe keyframe(String formatVersion, String channel) {
        String json = """
            {
              "meta": {"format_version": "%s", "model_format": "free"},
              "animations": [{"name": "turn", "length": 1,
                "animators": {"root": {"%s": [
                  {"time": 0, "interpolation": "bezier",
                   "bezier_left_value": [8, 9, 10],
                   "bezier_right_value": [11, 12, 13],
                   "data_points": [
                    {"x": 2, "y": 3, "z": 4},
                    {"x": 5, "y": 6, "z": 7}
                  ]}
                ]}}
              }]
            }
            """.formatted(formatVersion, channel);
        return MLuaBBModelAsset.parse(json).animations().get("turn")
            .tracks().get("root").get(channel).get(0);
    }
}
