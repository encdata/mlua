package dev.mlua.minecraft.animation;

import dev.mlua.common.enums.AnimationPriority;
import dev.mlua.math.MLuaCFrame;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Applies the sampled tracks for one runtime to its entity bone pose. */
final class MLuaAnimationPoseBlender {
    private static final int PRIORITY_COUNT = AnimationPriority.values().length;

    private MLuaAnimationPoseBlender() {}

    static void blend(MLuaEntityScope scope, List<MLuaAnimationTrack> tracks, Object runtimeOwner) {
        MLuaBonePose.beginAnimatedUpdate(scope, runtimeOwner);
        MLuaBonePose.clearCurrentAnimatedPose(scope, runtimeOwner);
        List<MLuaAnimationTrack> sorted = new ArrayList<>(tracks.size());
        for (MLuaAnimationTrack track : tracks) {
            if (track.getWeight() > 0.001f) sorted.add(track);
        }
        sorted.sort(Comparator.comparingInt((MLuaAnimationTrack track) -> track.getPriority().ordinal())
            .thenComparingInt(MLuaAnimationTrack::trackId));

        // Accumulate each bone's priority layers in one pass over tracks. This keeps
        // per-tick work proportional to sampled poses instead of bones * tracks * priorities.
        Map<String, BoneBlend> bones = new HashMap<>();
        for (MLuaAnimationTrack track : sorted) {
            int layer = track.getPriority().ordinal();
            double weight = track.getWeight();
            for (Map.Entry<String, MLuaCFrame> sampled : track.sampledPoseView().entrySet()) {
                BoneBlend blend = bones.computeIfAbsent(sampled.getKey(), ignored -> new BoneBlend());
                blend.add(layer, sampled.getValue(), weight);
            }
        }

        for (Map.Entry<String, BoneBlend> bone : bones.entrySet()) {
            MLuaCFrame pose = MLuaCFrame.IDENTITY;
            BoneBlend layers = bone.getValue();
            // Higher priorities blend over lower ones, so fading a layer reveals the pose below it.
            for (int layer = 0; layer < layers.poses.length; layer++) {
                MLuaCFrame layerPose = layers.poses[layer];
                if (layerPose != null) pose = pose.lerp(layerPose, Math.min(1.0, layers.weights[layer]));
            }
            MLuaBonePose.setAnimatedPose(scope, bone.getKey(), pose, runtimeOwner);
        }
    }

    /** Per-bone weighted pose accumulator, indexed by AnimationPriority ordinal. */
    private static final class BoneBlend {
        private final MLuaCFrame[] poses = new MLuaCFrame[PRIORITY_COUNT];
        private final double[] weights = new double[poses.length];

        private void add(int layer, MLuaCFrame pose, double weight) {
            double nextWeight = weights[layer] + weight;
            poses[layer] = poses[layer] == null ? pose : poses[layer].lerp(pose, weight / nextWeight);
            weights[layer] = nextWeight;
        }
    }
}
