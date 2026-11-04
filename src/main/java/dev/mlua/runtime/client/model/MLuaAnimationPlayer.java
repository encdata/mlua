package dev.mlua.runtime.client.model;

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
   private volatile boolean playing;
   private volatile boolean paused;
   private boolean restPosePending;
   private boolean restPoseApplied;
   private MLuaBBModelAsset.Animation restPoseAnimation;
   private Boolean loopOverride;
   private MLuaBBModelAsset.Animation outgoingAnimation;
   private float outgoingTime;
   private float outgoingPreviousTime;
   private double outgoingTickAdvance;
   private float outgoingSpeed;
   private Boolean outgoingLoopOverride;
   private boolean outgoingAdvances;
   private float fadeDuration;
   private float fadeTime;
   private float previousFadeTime;
   private double fadeTickAdvance;
   private float[] transitionSample;
   private MLuaBBModelAnimationSampler.QuaternionScratch transitionQuaternionScratch;
   private final Runnable stateChanged;
   private MLuaBBModelAnimationSampler.QuaternionScratch quaternionScratch;

   public MLuaAnimationPlayer() {
      this(null);
   }

   /** Allows an owning render object to maintain its active-tick membership. */
   public MLuaAnimationPlayer(Runnable stateChanged) {
      this.stateChanged = stateChanged;
   }

   public void play(String name, MLuaBBModelAsset asset, Boolean loopOverride, float speed) {
      play(name, asset, loopOverride, speed, 0.0F);
   }

   public void play(String name, MLuaBBModelAsset asset, Boolean loopOverride,
                    float speed, float fadeDuration) {
      if (!asset.animations().containsKey(name)) throw new IllegalArgumentException("Unknown BBModel animation: " + name);
      if (!Float.isFinite(speed) || speed < 0.0F) {
         throw new IllegalArgumentException("BBModel animation speed must be finite and non-negative");
      }
      if (!Float.isFinite(fadeDuration) || fadeDuration < 0.0F) {
         throw new IllegalArgumentException("BBModel animation fade duration must be finite and non-negative");
      }
      if (fadeDuration > 0.0F && active != null && !restPoseApplied) {
         MLuaBBModelAsset.Animation current = asset.animations().get(active);
         if (current != null) {
            outgoingAnimation = current;
            outgoingTime = time;
            outgoingPreviousTime = previousTime;
            outgoingTickAdvance = tickAdvance;
            outgoingSpeed = this.speed;
            outgoingLoopOverride = this.loopOverride;
            outgoingAdvances = playing && !paused;
         } else {
            clearTransition();
         }
      } else {
         clearTransition();
      }
      this.active = name;
      this.time = 0.0F;
      this.previousTime = 0.0F;
      this.tickAdvance = 0.0F;
      this.speed = speed;
      this.loopOverride = loopOverride;
      this.fadeDuration = outgoingAnimation == null ? 0.0F : fadeDuration;
      this.fadeTime = 0.0F;
      this.previousFadeTime = 0.0F;
      this.fadeTickAdvance = 0.0D;
      this.restPosePending = false;
      this.restPoseApplied = false;
      this.restPoseAnimation = null;
      this.playing = true;
      this.paused = false;
      notifyStateChanged();
   }

   public void play(String name, MLuaBBModelAsset asset, boolean loop, float speed) {
      play(name, asset, Boolean.valueOf(loop), speed);
   }

   public void stop() {
      clearTransition();
      active = null;
      playing = false;
      paused = false;
      restPosePending = false;
      restPoseApplied = false;
      restPoseAnimation = null;
      time = previousTime = 0.0F;
      tickAdvance = 0.0D;
      notifyStateChanged();
   }

   public void pause() {
      if (active != null && playing && !paused) {
         paused = true;
         notifyStateChanged();
      }
   }

   public void resume() {
      if (active != null && paused) {
         paused = false;
         notifyStateChanged();
      }
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
      } else if (loops(animation)) {
         position %= length;
      } else {
         position = Math.min(position, length);
      }
      time = previousTime = position;
      tickAdvance = 0.0D;
      restPoseApplied = false;
      restPosePending = isOnceAnimation(animation) && position >= length;
      restPoseAnimation = restPosePending ? animation : null;
      if (length <= 0.0F) {
         playing = false;
      } else if (position >= length
            && !loops(animation)) {
         playing = false;
      } else if (!paused) {
         playing = true;
      }
      notifyStateChanged();
   }

   public void setSpeed(float speed) {
      if (!Float.isFinite(speed) || speed < 0.0F) {
         throw new IllegalArgumentException("BBModel animation speed must be finite and non-negative");
      }
      if (active == null) throw new IllegalStateException("No BBModel animation is selected");
      this.speed = speed;
   }

   public void tick(float seconds, MLuaBBModelAsset asset) {
      double elapsed = Float.isFinite(seconds) && seconds > 0.0F ? seconds : 0.0D;
      if (paused) elapsed = 0.0D;
      previousFadeTime = fadeTime;
      fadeTickAdvance = outgoingAnimation == null ? 0.0D : elapsed;
      if (outgoingAnimation != null) {
         if (fadeTime >= fadeDuration) {
            // Keep the last tick's transition data until its partial frames have rendered.
            clearTransition();
         } else {
            outgoingPreviousTime = outgoingTime;
            advanceOutgoing(elapsed);
            fadeTime = (float) Math.min(fadeDuration, fadeTime + elapsed);
         }
      }
      if (!paused && restPosePending) {
         restPosePending = false;
         restPoseApplied = true;
         notifyStateChanged();
      }
      if (!playing || paused || active == null) {
         previousTime = time;
         tickAdvance = 0.0D;
         return;
      }
      MLuaBBModelAsset.Animation animation = asset.animations().get(active);
      if (animation == null) {
         stop();
         return;
      }
      previousTime = time;
      if (animation.length() <= 0.0F) {
         time = previousTime = 0.0F;
         tickAdvance = 0.0D;
         playing = false;
         notifyStateChanged();
         return;
      }
      tickAdvance = elapsed * speed;
      double nextTime = (double) time + tickAdvance;
      if (animation.length() > 0.0F && nextTime >= animation.length()) {
         if (loops(animation)) {
            time = (float) (nextTime % animation.length());
         } else {
            time = animation.length();
            tickAdvance = Math.max(0.0D, (double) time - previousTime);
            playing = false;
            restPosePending = isOnceAnimation(animation);
            restPoseAnimation = restPosePending ? animation : null;
            notifyStateChanged();
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
   public boolean needsTick() {
      return !paused && ((playing && active != null) || outgoingAnimation != null || restPosePending);
   }

   /** Applies one Blockbench bone's base transform and the matching interpolated animation channels. */
   public void applyBoneTransform(PoseStack pose, MLuaBBModelAsset.Animation animation,
                                  MLuaBBModelAsset.Bone bone, float partialTick,
                                  float[] position, float[] rotation, float[] scale) {
      requireScratch(position, rotation, scale);
      if (animation == null) {
         Arrays.fill(position, 0.0F);
         Arrays.fill(rotation, 0.0F);
         Arrays.fill(scale, 1.0F);
      } else {
         sampleBoneInto(animation, bone, "position", position, partialTick);
         sampleBoneInto(animation, bone, "rotation", rotation, partialTick);
         sampleBoneInto(animation, bone, "scale", scale, partialTick);
      }
      pose.translate(position[0] / 16.0F, position[1] / 16.0F, position[2] / 16.0F);
      pose.translate(bone.pivot(0) / 16.0F, bone.pivot(1) / 16.0F, bone.pivot(2) / 16.0F);
      // Blockbench's free format uses ZYX Euler order; PoseStack composes these calls in order.
      pose.mulPose(Axis.ZP.rotationDegrees(bone.rotation(2) + rotation[2]));
      pose.mulPose(Axis.YP.rotationDegrees(bone.rotation(1) + rotation[1]));
      pose.mulPose(Axis.XP.rotationDegrees(bone.rotation(0) + rotation[0]));
      pose.scale(scale[0], scale[1], scale[2]);
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
         Arrays.fill(scale, 1.0F);
      } else {
         sampleBoneInto(animation, bone, "position", position, partialTick);
         sampleBoneInto(animation, bone, "rotation", rotation, partialTick);
         sampleBoneInto(animation, bone, "scale", scale, partialTick);
      }
      matrix.translate(position[0] / 16.0F, position[1] / 16.0F, position[2] / 16.0F);
      matrix.translate(bone.pivot(0) / 16.0F, bone.pivot(1) / 16.0F, bone.pivot(2) / 16.0F);
      matrix.rotate(Axis.ZP.rotationDegrees(bone.rotation(2) + rotation[2]));
      matrix.rotate(Axis.YP.rotationDegrees(bone.rotation(1) + rotation[1]));
      matrix.rotate(Axis.XP.rotationDegrees(bone.rotation(0) + rotation[0]));
      matrix.scale(scale[0], scale[1], scale[2]);
      matrix.translate(-bone.pivot(0) / 16.0F, -bone.pivot(1) / 16.0F, -bone.pivot(2) / 16.0F);
   }

   /** Applies cube keyed tracks around the cube's center, matching the Blockbench local origin. */
   public void applyCubeTransform(PoseStack pose, MLuaBBModelAsset.Animation animation,
                                  MLuaBBModelAsset.Cube cube, float partialTick,
                                  float[] position, float[] rotation, float[] scale) {
      requireScratch(position, rotation, scale);
      pose.translate(cube.origin(0) / 16.0F, cube.origin(1) / 16.0F, cube.origin(2) / 16.0F);
      pose.mulPose(Axis.ZP.rotationDegrees(cube.rotation(2)));
      pose.mulPose(Axis.YP.rotationDegrees(cube.rotation(1)));
      pose.mulPose(Axis.XP.rotationDegrees(cube.rotation(0)));
      pose.translate(-cube.origin(0) / 16.0F, -cube.origin(1) / 16.0F, -cube.origin(2) / 16.0F);
      if (animation == null) return;
      sampleInto(animation, cube.uuid(), "position", position, partialTick);
      sampleInto(animation, cube.uuid(), "rotation", rotation, partialTick);
      sampleInto(animation, cube.uuid(), "scale", scale, partialTick);
      float centerX = (cube.from(0) + cube.to(0)) / 32.0F;
      float centerY = (cube.from(1) + cube.to(1)) / 32.0F;
      float centerZ = (cube.from(2) + cube.to(2)) / 32.0F;
      pose.translate(centerX, centerY, centerZ);
      pose.translate(position[0] / 16.0F, position[1] / 16.0F, position[2] / 16.0F);
      pose.mulPose(Axis.ZP.rotationDegrees(rotation[2]));
      pose.mulPose(Axis.YP.rotationDegrees(rotation[1]));
      pose.mulPose(Axis.XP.rotationDegrees(rotation[0]));
      pose.scale(scale[0], scale[1], scale[2]);
      pose.translate(-centerX, -centerY, -centerZ);
   }

   /** Applies a cube transform to a captured matrix for deferred geometry submission. */
   public void applyCubeTransform(Matrix4f matrix, MLuaBBModelAsset.Animation animation,
                                  MLuaBBModelAsset.Cube cube, float partialTick,
                                  float[] position, float[] rotation, float[] scale) {
      requireScratch(position, rotation, scale);
      matrix.translate(cube.origin(0) / 16.0F, cube.origin(1) / 16.0F, cube.origin(2) / 16.0F);
      matrix.rotate(Axis.ZP.rotationDegrees(cube.rotation(2)));
      matrix.rotate(Axis.YP.rotationDegrees(cube.rotation(1)));
      matrix.rotate(Axis.XP.rotationDegrees(cube.rotation(0)));
      matrix.translate(-cube.origin(0) / 16.0F, -cube.origin(1) / 16.0F, -cube.origin(2) / 16.0F);
      if (animation == null) return;
      sampleInto(animation, cube.uuid(), "position", position, partialTick);
      sampleInto(animation, cube.uuid(), "rotation", rotation, partialTick);
      sampleInto(animation, cube.uuid(), "scale", scale, partialTick);
      float centerX = (cube.from(0) + cube.to(0)) / 32.0F;
      float centerY = (cube.from(1) + cube.to(1)) / 32.0F;
      float centerZ = (cube.from(2) + cube.to(2)) / 32.0F;
      matrix.translate(centerX, centerY, centerZ);
      matrix.translate(position[0] / 16.0F, position[1] / 16.0F, position[2] / 16.0F);
      matrix.rotate(Axis.ZP.rotationDegrees(rotation[2]));
      matrix.rotate(Axis.YP.rotationDegrees(rotation[1]));
      matrix.rotate(Axis.XP.rotationDegrees(rotation[0]));
      matrix.scale(scale[0], scale[1], scale[2]);
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
      this.restPosePending = other.restPosePending;
      this.restPoseApplied = other.restPoseApplied;
      this.restPoseAnimation = other.restPoseAnimation;
      this.loopOverride = other.loopOverride;
      this.outgoingAnimation = other.outgoingAnimation;
      this.outgoingTime = other.outgoingTime;
      this.outgoingPreviousTime = other.outgoingPreviousTime;
      this.outgoingTickAdvance = other.outgoingTickAdvance;
      this.outgoingSpeed = other.outgoingSpeed;
      this.outgoingLoopOverride = other.outgoingLoopOverride;
      this.outgoingAdvances = other.outgoingAdvances;
      this.fadeDuration = other.fadeDuration;
      this.fadeTime = other.fadeTime;
      this.previousFadeTime = other.previousFadeTime;
      this.fadeTickAdvance = other.fadeTickAdvance;
      notifyStateChanged();
   }

   private void notifyStateChanged() {
      if (stateChanged != null) stateChanged.run();
   }

   private void clearTransition() {
      outgoingAnimation = null;
      outgoingTime = outgoingPreviousTime = 0.0F;
      outgoingTickAdvance = 0.0D;
      outgoingSpeed = 0.0F;
      outgoingLoopOverride = null;
      outgoingAdvances = false;
      fadeDuration = fadeTime = previousFadeTime = 0.0F;
      fadeTickAdvance = 0.0D;
   }

   private void advanceOutgoing(double elapsed) {
      if (!outgoingAdvances) {
         outgoingTickAdvance = 0.0D;
         return;
      }
      float length = outgoingAnimation.length();
      if (length <= 0.0F) {
         outgoingTime = 0.0F;
         outgoingTickAdvance = 0.0D;
         return;
      }
      outgoingTickAdvance = elapsed * outgoingSpeed;
      double next = outgoingTime + outgoingTickAdvance;
      if (next >= length) {
         outgoingTime = loops(outgoingAnimation, outgoingLoopOverride)
            ? (float) (next % length) : length;
      } else {
         outgoingTime = (float) next;
      }
   }

   private boolean loops(MLuaBBModelAsset.Animation animation) {
      return loops(animation, loopOverride);
   }

   private static boolean loops(MLuaBBModelAsset.Animation animation, Boolean override) {
      return override != null ? override : "loop".equalsIgnoreCase(animation.loop());
   }

   private boolean isOnceAnimation(MLuaBBModelAsset.Animation animation) {
      return loopOverride == null && "once".equalsIgnoreCase(animation.loop());
   }

   public float[] sample(MLuaBBModelAsset.Animation animation, String bone, String channel) {
      float[] result = new float[3];
      sampleInto(animation, bone, channel, result);
      return result;
   }

   /** Samples a track into caller-owned storage so render loops can reuse their scratch vectors. */
   public void sampleInto(MLuaBBModelAsset.Animation animation, String bone, String channel, float[] result) {
      if (isRestPoseApplied(animation)) {
         MLuaBBModelAnimationSampler.clear(channel, result);
         return;
      }
      sampleIntoAt(animation, bone, channel, result, time, loops(animation));
   }

   /** Samples between tick states using Minecraft's render partial tick. */
   public void sampleInto(MLuaBBModelAsset.Animation animation, String bone, String channel,
                          float[] result, float partialTick) {
      if (isRestPoseApplied(animation)) {
         MLuaBBModelAnimationSampler.clear(channel, result);
         return;
      }
      sampleIntoAt(animation, bone, channel, result, sampleTime(animation, partialTick),
         loops(animation), partialTick);
   }

   private double sampleTime(MLuaBBModelAsset.Animation animation, float partialTick) {
      float alpha = Float.isFinite(partialTick) ? Math.max(0.0F, Math.min(1.0F, partialTick)) : 1.0F;
      double sampleTime = previousTime + tickAdvance * alpha;
      if (tickAdvance == 0.0D) sampleTime = time;
      if (animation.length() > 0.0F) {
         if (loops(animation)) {
            // Keep the exact endpoint on the final pose. Wrapping at `== length`
            // makes interpolation snap to the first pose during the frame that
            // crosses the loop boundary.
            if (sampleTime > animation.length()) sampleTime %= animation.length();
         } else {
            sampleTime = Math.min(animation.length(), sampleTime);
         }
      }
      return sampleTime;
   }

   private void sampleBoneInto(MLuaBBModelAsset.Animation animation, MLuaBBModelAsset.Bone bone,
                               String channel, float[] result, float partialTick) {
      if (isRestPoseApplied(animation)) {
         MLuaBBModelAnimationSampler.clear(channel, result);
         blendOutgoing(animation, bone.uuid(), bone, channel, result, partialTick);
         return;
      }
      MLuaBBModelAnimationSampler.sampleInto(animation, bone, channel, result,
         sampleTime(animation, partialTick), loops(animation),
         quaternionScratchFor(animation, bone.uuid(), channel));
      blendOutgoing(animation, bone.uuid(), bone, channel, result, partialTick);
   }

   private void sampleIntoAt(MLuaBBModelAsset.Animation animation, String bone, String channel,
                             float[] result, double sampleTime, boolean looping) {
      sampleIntoAt(animation, bone, channel, result, sampleTime, looping, Float.NaN);
   }

   private void sampleIntoAt(MLuaBBModelAsset.Animation animation, String bone, String channel,
                             float[] result, double sampleTime, boolean looping, float partialTick) {
      if (isRestPoseApplied(animation)) {
         MLuaBBModelAnimationSampler.clear(channel, result);
         blendOutgoing(animation, bone, null, channel, result, partialTick);
         return;
      }
      MLuaBBModelAnimationSampler.sampleInto(animation, bone, channel, result, sampleTime, looping,
         quaternionScratchFor(animation, bone, channel));
      blendOutgoing(animation, bone, null, channel, result, partialTick);
   }

   private boolean isRestPoseApplied(MLuaBBModelAsset.Animation animation) {
      return restPoseApplied && restPoseAnimation == animation;
   }

   private void blendOutgoing(MLuaBBModelAsset.Animation animation, String bone,
                              MLuaBBModelAsset.Bone modelBone, String channel,
                              float[] result, float partialTick) {
      if (outgoingAnimation == null || fadeDuration <= 0.0F) return;
      if (transitionSample == null) transitionSample = new float[3];
      double oldTime = outgoingPreviousTime + outgoingTickAdvance * sampleAlpha(partialTick);
      if (outgoingTickAdvance == 0.0D) oldTime = outgoingTime;
      if (outgoingAnimation.length() > 0.0F && loops(outgoingAnimation, outgoingLoopOverride)) {
         if (oldTime > outgoingAnimation.length()) oldTime %= outgoingAnimation.length();
      } else {
         oldTime = Math.min(outgoingAnimation.length(), oldTime);
      }
      MLuaBBModelAnimationSampler.sampleInto(outgoingAnimation, bone, modelBone, channel,
         transitionSample, oldTime, loops(outgoingAnimation, outgoingLoopOverride),
         quaternionScratchFor(outgoingAnimation, bone, channel));
      float fade = previousFadeTime + (float) (fadeTickAdvance * sampleAlpha(partialTick));
      if (fadeTickAdvance == 0.0D) fade = fadeTime;
      float amount = Math.max(0.0F, Math.min(1.0F, fade / fadeDuration));
      if (channel.equals("rotation")) {
         float bx = modelBone == null ? 0.0F : modelBone.rotation(0);
         float by = modelBone == null ? 0.0F : modelBone.rotation(1);
         float bz = modelBone == null ? 0.0F : modelBone.rotation(2);
         MLuaBBModelAnimationSampler.blendRotation(transitionSample, result, amount,
            bx, by, bz, result, transitionQuaternionScratch());
      } else {
         for (int axis = 0; axis < 3; axis++) {
            result[axis] = transitionSample[axis]
               + (result[axis] - transitionSample[axis]) * amount;
         }
      }
   }

   private static float sampleAlpha(float partialTick) {
      return Float.isFinite(partialTick) ? Math.max(0.0F, Math.min(1.0F, partialTick)) : 1.0F;
   }

   private MLuaBBModelAnimationSampler.QuaternionScratch transitionQuaternionScratch() {
      if (transitionQuaternionScratch == null) {
         transitionQuaternionScratch = new MLuaBBModelAnimationSampler.QuaternionScratch();
      }
      return transitionQuaternionScratch;
   }

   private MLuaBBModelAnimationSampler.QuaternionScratch quaternionScratchFor(
         MLuaBBModelAsset.Animation animation, String bone, String channel) {
      if (!channel.equals("rotation") || !animation.usesQuaternionRotation(bone)) return quaternionScratch;
      if (quaternionScratch == null) {
         quaternionScratch = new MLuaBBModelAnimationSampler.QuaternionScratch();
      }
      return quaternionScratch;
   }
}
