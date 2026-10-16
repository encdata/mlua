package dev.mlua.runtime.client.model;

import java.util.Map;

public final class MLuaAnimationPlayer {
   private String active;
   private float time;
   private float speed = 1.0F;
   private boolean playing;
   private boolean loopOverride;

   public void play(String name, MLuaBBModelAsset asset, boolean loop, float speed) {
      if (!asset.animations().containsKey(name)) throw new IllegalArgumentException("Unknown BBModel animation: " + name);
      if (!Float.isFinite(speed) || speed < 0.0F) {
         throw new IllegalArgumentException("BBModel animation speed must be finite and non-negative");
      }
      this.active = name;
      this.time = 0.0F;
      this.speed = speed;
      this.loopOverride = loop;
      this.playing = true;
   }

   public void stop() { playing = false; time = 0.0F; }
   public void tick(float seconds, MLuaBBModelAsset asset) {
      if (!playing || active == null) return;
      MLuaBBModelAsset.Animation animation = asset.animations().get(active);
      time += seconds * speed;
      if (animation.length() > 0 && time > animation.length()) {
         if (loopOverride || "loop".equalsIgnoreCase(animation.loop()) || "hold".equalsIgnoreCase(animation.loop())) time %= animation.length();
         else { time = animation.length(); playing = false; }
      }
   }

   public String active() { return active; }
   public float time() { return time; }
   public boolean playing() { return playing; }

   public void copyStateFrom(MLuaAnimationPlayer other) {
      this.active = other.active;
      this.time = other.time;
      this.speed = other.speed;
      this.playing = other.playing;
      this.loopOverride = other.loopOverride;
   }

   public float[] sample(MLuaBBModelAsset.Animation animation, String bone, String channel) {
      float[] result = new float[3];
      sampleInto(animation, bone, channel, result);
      return result;
   }

   /** Samples a track into caller-owned storage so render loops can reuse their scratch vectors. */
   public void sampleInto(MLuaBBModelAsset.Animation animation, String bone, String channel, float[] result) {
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
         if (frames.get(middle).time() < time) low = middle + 1;
         else high = middle;
      }
      int nextIndex = Math.min(low, frames.size() - 1);
      MLuaBBModelAsset.Keyframe next = frames.get(nextIndex);
      MLuaBBModelAsset.Keyframe previous;
      if (next.time() == time) {
         int lastEqual = nextIndex;
         while (lastEqual + 1 < frames.size() && frames.get(lastEqual + 1).time() == time) lastEqual++;
         previous = frames.get(lastEqual);
      } else {
         previous = frames.get(Math.max(0, nextIndex - 1));
      }
      if (previous == next || next.time() <= previous.time()) {
         float[] value = previous.value();
         result[0] = value[0]; result[1] = value[1]; result[2] = value[2];
         return;
      }
      float alpha = (time - previous.time()) / (next.time() - previous.time());
      if ("step".equalsIgnoreCase(next.interpolation())) alpha = 0.0F;
      alpha = ease(alpha, next.easing());
      float[] a = previous.value(), b = next.value();
      result[0] = a[0] + (b[0] - a[0]) * alpha;
      result[1] = a[1] + (b[1] - a[1]) * alpha;
      result[2] = a[2] + (b[2] - a[2]) * alpha;
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
      if (value == null || value.length() < fragment.length()) return false;
      for (int start = 0; start <= value.length() - fragment.length(); start++) {
         int index = 0;
         while (index < fragment.length()) {
            char actual = value.charAt(start + index);
            if (actual == '-') actual = '_';
            if (Character.toLowerCase(actual) != fragment.charAt(index)) break;
            index++;
         }
         if (index == fragment.length()) return true;
      }
      return false;
   }
}
