package dev.mlua.runtime.client.model;

import java.util.Map;
import java.util.Arrays;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

public final class MLuaAnimationPlayer {
   private String active;
   private float time;
   private float previousTime;
   private double tickAdvance;
   private float speed = 1.0F;
   private boolean playing;
   private boolean paused;
   private boolean loopOverride;

   public void play(String name, MLuaBBModelAsset asset, boolean loop, float speed) {
      if (!asset.animations().containsKey(name)) throw new IllegalArgumentException("Unknown BBModel animation: " + name);
      if (!Float.isFinite(speed) || speed < 0.0F) {
         throw new IllegalArgumentException("BBModel animation speed must be finite and non-negative");
      }
      this.active = name;
      this.time = 0.0F;
      this.previousTime = 0.0F;
      this.tickAdvance = 0.0F;
      this.speed = speed;
      this.loopOverride = loop;
      this.playing = true;
      this.paused = false;
   }

   public void stop() {
      active = null;
      playing = false;
      paused = false;
      time = previousTime = 0.0F;
      tickAdvance = 0.0D;
   }

   public void pause() {
      if (active != null && playing) paused = true;
   }

   public void resume() {
      if (active != null && paused) paused = false;
   }

   public void setTimePosition(float position, MLuaBBModelAsset asset) {
      if (!Float.isFinite(position) || position < 0.0F) {
         throw new IllegalArgumentException("BBModel animation time must be finite and non-negative");
      }
      if (active == null) throw new IllegalStateException("No BBModel animation is selected");
      MLuaBBModelAsset.Animation animation = asset.animations().get(active);
      if (animation == null) throw new IllegalStateException("The selected BBModel animation is no longer available");
      float length = animation.length();
      if (length <= 0.0F) {
         position = 0.0F;
         playing = false;
      } else if (loopOverride || "loop".equalsIgnoreCase(animation.loop())) {
         position %= length;
      } else {
         position = Math.min(position, length);
      }
      time = previousTime = position;
      tickAdvance = 0.0D;
      if (length <= 0.0F) {
         playing = false;
      } else if (position >= length
            && !(loopOverride || "loop".equalsIgnoreCase(animation.loop()))) {
         playing = false;
      } else if (!paused) {
         playing = true;
      }
   }

   public void setSpeed(float speed) {
      if (!Float.isFinite(speed) || speed < 0.0F) {
         throw new IllegalArgumentException("BBModel animation speed must be finite and non-negative");
      }
      if (active == null) throw new IllegalStateException("No BBModel animation is selected");
      this.speed = speed;
   }

   public void tick(float seconds, MLuaBBModelAsset asset) {
      if (!playing || paused || active == null) {
         previousTime = time;
         tickAdvance = 0.0D;
         return;
      }
      MLuaBBModelAsset.Animation animation = asset.animations().get(active);
      previousTime = time;
      double elapsed = Float.isFinite(seconds) && seconds > 0.0F ? seconds : 0.0D;
      tickAdvance = elapsed * speed;
      double nextTime = (double) time + tickAdvance;
      if (animation.length() > 0.0F && nextTime >= animation.length()) {
         if (loopOverride || "loop".equalsIgnoreCase(animation.loop())) {
            time = (float) (nextTime % animation.length());
         } else {
            time = animation.length();
            tickAdvance = Math.max(0.0D, (double) time - previousTime);
            playing = false;
         }
      } else {
         time = (float) Math.min(nextTime, Float.MAX_VALUE);
      }
   }

   public String active() { return active; }
   public float time() { return time; }
   public float speed() { return speed; }
   public boolean playing() { return playing && !paused; }
   public boolean paused() { return paused; }

   /** Applies one Blockbench bone's base transform and the matching interpolated animation channels. */
   public void applyBoneTransform(PoseStack pose, MLuaBBModelAsset.Animation animation,
                                  MLuaBBModelAsset.Bone bone, float partialTick,
                                  float[] position, float[] rotation, float[] scale) {
      requireScratch(position, rotation, scale);
      if (animation == null) {
         Arrays.fill(position, 0.0F);
         Arrays.fill(rotation, 0.0F);
         Arrays.fill(scale, 0.0F);
      } else {
         sampleInto(animation, bone.uuid(), "position", position, partialTick);
         sampleInto(animation, bone.uuid(), "rotation", rotation, partialTick);
         sampleInto(animation, bone.uuid(), "scale", scale, partialTick);
      }
      pose.translate(position[0] / 16.0F, position[1] / 16.0F, position[2] / 16.0F);
      pose.translate(bone.pivot(0) / 16.0F, bone.pivot(1) / 16.0F, bone.pivot(2) / 16.0F);
      pose.mulPose(Axis.XP.rotationDegrees(bone.rotation(0) + rotation[0]));
      pose.mulPose(Axis.YP.rotationDegrees(bone.rotation(1) + rotation[1]));
      pose.mulPose(Axis.ZP.rotationDegrees(bone.rotation(2) + rotation[2]));
      pose.scale(Math.max(0.001F, 1.0F + scale[0]),
         Math.max(0.001F, 1.0F + scale[1]), Math.max(0.001F, 1.0F + scale[2]));
      pose.translate(-bone.pivot(0) / 16.0F, -bone.pivot(1) / 16.0F, -bone.pivot(2) / 16.0F);
   }

   /** Applies a bone transform to a captured matrix for deferred geometry submission. */
   public void applyBoneTransform(Matrix4f matrix, MLuaBBModelAsset.Animation animation,
                                  MLuaBBModelAsset.Bone bone, float partialTick,
                                  float[] position, float[] rotation, float[] scale) {
      requireScratch(position, rotation, scale);
      if (animation == null) {
         Arrays.fill(position, 0.0F);
         Arrays.fill(rotation, 0.0F);
         Arrays.fill(scale, 0.0F);
      } else {
         sampleInto(animation, bone.uuid(), "position", position, partialTick);
         sampleInto(animation, bone.uuid(), "rotation", rotation, partialTick);
         sampleInto(animation, bone.uuid(), "scale", scale, partialTick);
      }
      matrix.translate(position[0] / 16.0F, position[1] / 16.0F, position[2] / 16.0F);
      matrix.translate(bone.pivot(0) / 16.0F, bone.pivot(1) / 16.0F, bone.pivot(2) / 16.0F);
      matrix.rotate(Axis.XP.rotationDegrees(bone.rotation(0) + rotation[0]));
      matrix.rotate(Axis.YP.rotationDegrees(bone.rotation(1) + rotation[1]));
      matrix.rotate(Axis.ZP.rotationDegrees(bone.rotation(2) + rotation[2]));
      matrix.scale(Math.max(0.001F, 1.0F + scale[0]),
         Math.max(0.001F, 1.0F + scale[1]), Math.max(0.001F, 1.0F + scale[2]));
      matrix.translate(-bone.pivot(0) / 16.0F, -bone.pivot(1) / 16.0F, -bone.pivot(2) / 16.0F);
   }

   /** Applies cube keyed tracks around the cube's center, matching the Blockbench local origin. */
   public void applyCubeTransform(PoseStack pose, MLuaBBModelAsset.Animation animation,
                                  MLuaBBModelAsset.Cube cube, float partialTick,
                                  float[] position, float[] rotation, float[] scale) {
      requireScratch(position, rotation, scale);
      if (animation == null) return;
      sampleInto(animation, cube.uuid(), "position", position, partialTick);
      sampleInto(animation, cube.uuid(), "rotation", rotation, partialTick);
      sampleInto(animation, cube.uuid(), "scale", scale, partialTick);
      float centerX = (cube.from(0) + cube.to(0)) / 32.0F;
      float centerY = (cube.from(1) + cube.to(1)) / 32.0F;
      float centerZ = (cube.from(2) + cube.to(2)) / 32.0F;
      pose.translate(centerX, centerY, centerZ);
      pose.translate(position[0] / 16.0F, position[1] / 16.0F, position[2] / 16.0F);
      pose.mulPose(Axis.XP.rotationDegrees(rotation[0]));
      pose.mulPose(Axis.YP.rotationDegrees(rotation[1]));
      pose.mulPose(Axis.ZP.rotationDegrees(rotation[2]));
      pose.scale(Math.max(0.001F, 1.0F + scale[0]),
         Math.max(0.001F, 1.0F + scale[1]), Math.max(0.001F, 1.0F + scale[2]));
      pose.translate(-centerX, -centerY, -centerZ);
   }

   /** Applies a cube transform to a captured matrix for deferred geometry submission. */
   public void applyCubeTransform(Matrix4f matrix, MLuaBBModelAsset.Animation animation,
                                  MLuaBBModelAsset.Cube cube, float partialTick,
                                  float[] position, float[] rotation, float[] scale) {
      requireScratch(position, rotation, scale);
      if (animation == null) return;
      sampleInto(animation, cube.uuid(), "position", position, partialTick);
      sampleInto(animation, cube.uuid(), "rotation", rotation, partialTick);
      sampleInto(animation, cube.uuid(), "scale", scale, partialTick);
      float centerX = (cube.from(0) + cube.to(0)) / 32.0F;
      float centerY = (cube.from(1) + cube.to(1)) / 32.0F;
      float centerZ = (cube.from(2) + cube.to(2)) / 32.0F;
      matrix.translate(centerX, centerY, centerZ);
      matrix.translate(position[0] / 16.0F, position[1] / 16.0F, position[2] / 16.0F);
      matrix.rotate(Axis.XP.rotationDegrees(rotation[0]));
      matrix.rotate(Axis.YP.rotationDegrees(rotation[1]));
      matrix.rotate(Axis.ZP.rotationDegrees(rotation[2]));
      matrix.scale(Math.max(0.001F, 1.0F + scale[0]),
         Math.max(0.001F, 1.0F + scale[1]), Math.max(0.001F, 1.0F + scale[2]));
      matrix.translate(-centerX, -centerY, -centerZ);
   }

   private static void requireScratch(float[] position, float[] rotation, float[] scale) {
      if (position == null || position.length < 3 || rotation == null || rotation.length < 3
            || scale == null || scale.length < 3) {
         throw new IllegalArgumentException("BBModel animation scratch vectors must each contain at least three values");
      }
   }

   public void copyStateFrom(MLuaAnimationPlayer other) {
      this.active = other.active;
      this.time = other.time;
      this.previousTime = other.previousTime;
      this.tickAdvance = other.tickAdvance;
      this.speed = other.speed;
      this.playing = other.playing;
      this.paused = other.paused;
      this.loopOverride = other.loopOverride;
   }

   public float[] sample(MLuaBBModelAsset.Animation animation, String bone, String channel) {
      float[] result = new float[3];
      sampleInto(animation, bone, channel, result);
      return result;
   }

   /** Samples a track into caller-owned storage so render loops can reuse their scratch vectors. */
   public void sampleInto(MLuaBBModelAsset.Animation animation, String bone, String channel, float[] result) {
      sampleIntoAt(animation, bone, channel, result, time);
   }

   /** Samples between tick states using Minecraft's render partial tick. */
   public void sampleInto(MLuaBBModelAsset.Animation animation, String bone, String channel,
                          float[] result, float partialTick) {
      float alpha = Float.isFinite(partialTick) ? Math.max(0.0F, Math.min(1.0F, partialTick)) : 1.0F;
      double sampleTime = previousTime + tickAdvance * alpha;
      if (tickAdvance == 0.0D) sampleTime = time;
      if (animation.length() > 0.0F) {
         if (loopOverride || "loop".equalsIgnoreCase(animation.loop())) {
            sampleTime %= animation.length();
         } else {
            sampleTime = Math.min(animation.length(), sampleTime);
         }
      }
      sampleIntoAt(animation, bone, channel, result, sampleTime);
   }

   private void sampleIntoAt(MLuaBBModelAsset.Animation animation, String bone, String channel,
                             float[] result, double sampleTime) {
      if (result == null || result.length < 3) {
         throw new IllegalArgumentException("Animation sample output must contain at least three values");
      }
      Map<String, java.util.List<MLuaBBModelAsset.Keyframe>> channels = animation.tracks().get(bone);
      if (channels == null) {
         result[0] = result[1] = result[2] = 0.0F;
         return;
      }
      java.util.List<MLuaBBModelAsset.Keyframe> frames = channels.get(channel);
      if (frames == null || frames.isEmpty()) {
         result[0] = result[1] = result[2] = 0.0F;
         return;
      }
      int low = 0;
      int high = frames.size();
      while (low < high) {
         int middle = (low + high) >>> 1;
         if (frames.get(middle).time() < sampleTime) low = middle + 1;
         else high = middle;
      }
      int nextIndex = Math.min(low, frames.size() - 1);
      MLuaBBModelAsset.Keyframe next = frames.get(nextIndex);
      MLuaBBModelAsset.Keyframe previous;
      if (next.time() == sampleTime) {
         int lastEqual = nextIndex;
         while (lastEqual + 1 < frames.size() && frames.get(lastEqual + 1).time() == sampleTime) lastEqual++;
         previous = frames.get(lastEqual);
      } else {
         previous = frames.get(Math.max(0, nextIndex - 1));
      }
      if (previous == next || next.time() <= previous.time()) {
         result[0] = previous.value(0); result[1] = previous.value(1); result[2] = previous.value(2);
         return;
      }
      float alpha = (float)((sampleTime - previous.time()) / (next.time() - previous.time()));
      if ("step".equalsIgnoreCase(next.interpolation())) alpha = 0.0F;
      alpha = ease(alpha, next.easing());
      for (int axis = 0; axis < 3; axis++) {
         float start = previous.value(axis), end = next.value(axis);
         result[axis] = start + (end - start) * alpha;
      }
   }

   private static float ease(float value, String easing) {
      float t = Math.max(0.0F, Math.min(1.0F, value));
      if (containsNormalized(easing, "step")) return 0.0F;
      if (containsNormalized(easing, "sine")) {
         if (containsNormalized(easing, "in_out")) return (1.0F - (float)Math.cos(Math.PI * t)) * 0.5F;
         if (containsNormalized(easing, "out")) return (float)Math.sin(t * Math.PI * 0.5F);
         return 1.0F - (float)Math.cos(t * Math.PI * 0.5F);
      }
      if (containsNormalized(easing, "quad")) {
         if (containsNormalized(easing, "in_out")) {
            if (t < 0.5F) return 2.0F * t * t;
            float inverse = -2.0F * t + 2.0F;
            return 1.0F - inverse * inverse * 0.5F;
         }
         if (containsNormalized(easing, "out")) return 1.0F - (1.0F - t) * (1.0F - t);
         return t * t;
      }
      if (containsNormalized(easing, "cubic")) {
         if (containsNormalized(easing, "in_out")) {
            if (t < 0.5F) return 4.0F * t * t * t;
            float inverse = -2.0F * t + 2.0F;
            return 1.0F - inverse * inverse * inverse * 0.5F;
         }
         if (containsNormalized(easing, "out")) {
            float inverse = 1.0F - t;
            return 1.0F - inverse * inverse * inverse;
         }
         return t * t * t;
      }
      return t;
   }

   private static boolean containsNormalized(String value, String fragment) {
      if (value == null || fragment == null || value.isEmpty() || fragment.isEmpty()) return false;
      for (int start = 0; start < value.length(); start++) {
         char first = value.charAt(start);
         if (!Character.isLetterOrDigit(first)
               || Character.toLowerCase(first) != firstAlphaNumeric(fragment, 0)) continue;
         int valueIndex = start;
         int fragmentIndex = 0;
         while (true) {
            while (valueIndex < value.length() && !Character.isLetterOrDigit(value.charAt(valueIndex))) valueIndex++;
            while (fragmentIndex < fragment.length() && !Character.isLetterOrDigit(fragment.charAt(fragmentIndex))) fragmentIndex++;
            if (fragmentIndex >= fragment.length()) return true;
            if (valueIndex >= value.length()
                  || Character.toLowerCase(value.charAt(valueIndex))
                     != Character.toLowerCase(fragment.charAt(fragmentIndex))) break;
            valueIndex++;
            fragmentIndex++;
         }
      }
      return false;
   }

   private static char firstAlphaNumeric(String value, int start) {
      for (int index = start; index < value.length(); index++) {
         char character = value.charAt(index);
         if (Character.isLetterOrDigit(character)) return Character.toLowerCase(character);
      }
      return '\0';
   }
}
