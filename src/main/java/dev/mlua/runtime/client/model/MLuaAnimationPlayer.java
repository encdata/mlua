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
      Map<String, java.util.List<MLuaBBModelAsset.Keyframe>> channels = animation.tracks().get(bone);
      if (channels == null) return new float[]{0, 0, 0};
      java.util.List<MLuaBBModelAsset.Keyframe> frames = channels.get(channel);
      if (frames == null || frames.isEmpty()) return new float[]{0, 0, 0};
      MLuaBBModelAsset.Keyframe previous = frames.get(0), next = frames.get(frames.size() - 1);
      for (MLuaBBModelAsset.Keyframe frame : frames) {
         if (frame.time() <= time) previous = frame;
         if (frame.time() >= time) { next = frame; break; }
      }
      if (previous == next || next.time() <= previous.time()) return previous.value().clone();
      float alpha = (time - previous.time()) / (next.time() - previous.time());
      if ("step".equalsIgnoreCase(next.interpolation())) alpha = 0.0F;
      alpha = ease(alpha, next.easing());
      float[] a = previous.value(), b = next.value();
      return new float[]{a[0] + (b[0] - a[0]) * alpha, a[1] + (b[1] - a[1]) * alpha, a[2] + (b[2] - a[2]) * alpha};
   }

   private static float ease(float value, String easing) {
      float t = Math.max(0.0F, Math.min(1.0F, value));
      String name = easing == null ? "linear" : easing.toLowerCase().replace('-', '_');
      if (name.contains("step")) return 0.0F;
      if (name.contains("sine")) {
         if (name.contains("in_out")) return (1.0F - (float)Math.cos(Math.PI * t)) * 0.5F;
         if (name.contains("out")) return (float)Math.sin(t * Math.PI * 0.5F);
         return 1.0F - (float)Math.cos(t * Math.PI * 0.5F);
      }
      if (name.contains("quad")) {
         if (name.contains("in_out")) return t < 0.5F ? 2.0F * t * t : 1.0F - (float)Math.pow(-2.0F * t + 2.0F, 2.0F) * 0.5F;
         if (name.contains("out")) return 1.0F - (1.0F - t) * (1.0F - t);
         return t * t;
      }
      if (name.contains("cubic")) {
         if (name.contains("in_out")) return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float)Math.pow(-2.0F * t + 2.0F, 3.0F) * 0.5F;
         if (name.contains("out")) return 1.0F - (float)Math.pow(1.0F - t, 3.0F);
         return t * t * t;
      }
      return t;
   }
}
