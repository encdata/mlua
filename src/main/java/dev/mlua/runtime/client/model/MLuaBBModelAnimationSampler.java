package dev.mlua.runtime.client.model;

import java.util.List;
import java.util.Map;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Samples Blockbench transform channels between their keyframes. */
final class MLuaBBModelAnimationSampler {
   static final class QuaternionScratch {
      private final Quaternionf base = new Quaternionf();
      private final Quaternionf value = new Quaternionf();
      private final Quaternionf before = new Quaternionf();
      private final Quaternionf after = new Quaternionf();
      private final Quaternionf result = new Quaternionf();
      private final Vector3f euler = new Vector3f();
   }

   private MLuaBBModelAnimationSampler() {}

   static void sampleInto(MLuaBBModelAsset.Animation animation, String bone, String channel,
                          float[] result, double time, boolean looping) {
      sampleInto(animation, bone, channel, result, time, looping, null);
   }

   static void sampleInto(MLuaBBModelAsset.Animation animation, String bone, String channel,
                          float[] result, double time, boolean looping,
                          QuaternionScratch quaternionScratch) {
      sampleResolved(animation, bone, null, channel, result, time, looping, quaternionScratch);
   }

   static void sampleInto(MLuaBBModelAsset.Animation animation, MLuaBBModelAsset.Bone bone,
                          String channel, float[] result, double time, boolean looping,
                          QuaternionScratch quaternionScratch) {
      sampleResolved(animation, bone.uuid(), bone, channel, result, time, looping, quaternionScratch);
   }

   static void sampleInto(MLuaBBModelAsset.Animation animation, String boneName,
                          MLuaBBModelAsset.Bone bone, String channel, float[] result,
                          double time, boolean looping, QuaternionScratch quaternionScratch) {
      sampleResolved(animation, boneName, bone, channel, result, time, looping, quaternionScratch);
   }

   static void blendRotation(float[] from, float[] to, float amount,
                             float baseX, float baseY, float baseZ, float[] result,
                             QuaternionScratch scratch) {
      if (scratch == null) scratch = new QuaternionScratch();
      scratch.before.rotationZYX((float) Math.toRadians(baseZ + from[2]),
         (float) Math.toRadians(baseY + from[1]), (float) Math.toRadians(baseX + from[0]));
      scratch.after.rotationZYX((float) Math.toRadians(baseZ + to[2]),
         (float) Math.toRadians(baseY + to[1]), (float) Math.toRadians(baseX + to[0]));
      scratch.before.slerp(scratch.after, amount, scratch.result);
      toEulerZYX(scratch.result, scratch.euler);
      result[0] = (float) Math.toDegrees(scratch.euler.x) - baseX;
      result[1] = (float) Math.toDegrees(scratch.euler.y) - baseY;
      result[2] = (float) Math.toDegrees(scratch.euler.z) - baseZ;
   }

   private static void sampleResolved(MLuaBBModelAsset.Animation animation, String boneName,
                                      MLuaBBModelAsset.Bone bone, String channel,
                                      float[] result, double time, boolean looping,
                                      QuaternionScratch quaternionScratch) {
      if (result == null || result.length < 3) {
         throw new IllegalArgumentException("Animation sample output must contain at least three values");
      }

      Map<String, List<MLuaBBModelAsset.Keyframe>> tracks = animation.tracks().get(boneName);
      List<MLuaBBModelAsset.Keyframe> frames = tracks == null ? null : tracks.get(channel);
      if (frames == null || frames.isEmpty()) {
         clear(channel, result);
         return;
      }

      int nextIndex = lowerBound(frames, time);
      if (nextIndex < frames.size() && frames.get(nextIndex).time() == time) {
         while (nextIndex + 1 < frames.size() && frames.get(nextIndex + 1).time() == time) {
            nextIndex++;
         }
         MLuaBBModelAsset.Keyframe keyframe = frames.get(nextIndex);
         if (usesQuaternionRotation(animation, boneName, channel)) {
            sampleQuaternionRotation(keyframe, keyframe, false, false, 0.0F, bone, result, quaternionScratch);
         } else {
            copyValues(keyframe, result);
         }
         return;
      }
      if (nextIndex == 0) {
         MLuaBBModelAsset.Keyframe keyframe = frames.get(0);
         if (usesQuaternionRotation(animation, boneName, channel)) {
            sampleQuaternionRotation(keyframe, keyframe, false, false, 0.0F, bone, result, quaternionScratch);
         } else {
            copyValues(keyframe, result);
         }
         return;
      }
      if (nextIndex == frames.size()) {
         MLuaBBModelAsset.Keyframe keyframe = frames.get(frames.size() - 1);
         if (usesQuaternionRotation(animation, boneName, channel)) {
            sampleQuaternionRotation(keyframe, keyframe, true, true, 0.0F, bone, result, quaternionScratch);
         } else {
            copyOutgoing(keyframe, result);
         }
         return;
      }

      MLuaBBModelAsset.Keyframe before = frames.get(nextIndex - 1);
      MLuaBBModelAsset.Keyframe after = frames.get(nextIndex);
      double duration = after.time() - before.time();
      if (duration <= 0.0D) {
         copyValues(after, result);
         return;
      }

      float amount = (float) ((time - before.time()) / duration);
      String beforeInterpolation = before.interpolation();
      String afterInterpolation = after.interpolation();
      if ("step".equalsIgnoreCase(beforeInterpolation)) {
         if (usesQuaternionRotation(animation, boneName, channel)) {
            sampleQuaternionRotation(before, before, true, true, 0.0F, bone, result, quaternionScratch);
         } else {
            copyOutgoing(before, result);
         }
      } else if (usesQuaternionRotation(animation, boneName, channel)) {
         sampleQuaternionRotation(before, after, true, false, amount, bone, result, quaternionScratch);
      } else if ("catmullrom".equalsIgnoreCase(beforeInterpolation)
            || "catmullrom".equalsIgnoreCase(afterInterpolation)) {
         sampleCatmullRom(looping, frames, nextIndex - 1, amount, result);
      } else if ("bezier".equalsIgnoreCase(beforeInterpolation)
            || "bezier".equalsIgnoreCase(afterInterpolation)) {
         sampleBezier(before, after, time, result);
      } else {
         for (int axis = 0; axis < 3; axis++) {
            float start = before.outgoingValue(axis);
            float end = after.value(axis);
            result[axis] = start + (end - start) * amount;
         }
      }
   }

   private static boolean usesQuaternionRotation(MLuaBBModelAsset.Animation animation,
                                                String bone, String channel) {
      return channel.equals("rotation") && animation.usesQuaternionRotation(bone);
   }

   private static void sampleQuaternionRotation(MLuaBBModelAsset.Keyframe before,
                                                MLuaBBModelAsset.Keyframe after,
                                                boolean useOutgoingBefore,
                                                boolean useOutgoingAfter,
                                                float amount,
                                                MLuaBBModelAsset.Bone bone,
                                                float[] result, QuaternionScratch scratch) {
      if (scratch == null) scratch = new QuaternionScratch();
      float baseX = bone == null ? 0.0F : bone.rotation(0);
      float baseY = bone == null ? 0.0F : bone.rotation(1);
      float baseZ = bone == null ? 0.0F : bone.rotation(2);
      float beforeX = useOutgoingBefore ? before.outgoingValue(0) : before.value(0);
      float beforeY = useOutgoingBefore ? before.outgoingValue(1) : before.value(1);
      float beforeZ = useOutgoingBefore ? before.outgoingValue(2) : before.value(2);
      float afterX = useOutgoingAfter ? after.outgoingValue(0) : after.value(0);
      float afterY = useOutgoingAfter ? after.outgoingValue(1) : after.value(1);
      float afterZ = useOutgoingAfter ? after.outgoingValue(2) : after.value(2);
      // Blockbench combines the fixed bone rotation with each keyframe before slerping.
      setFixedQuaternion(scratch, scratch.before, beforeX, beforeY, beforeZ, baseX, baseY, baseZ);
      setFixedQuaternion(scratch, scratch.after, afterX, afterY, afterZ, baseX, baseY, baseZ);
      scratch.before.slerp(scratch.after, amount, scratch.result);
      toEulerZYX(scratch.result, scratch.euler);
      result[0] = (float) Math.toDegrees(scratch.euler.x - Math.toRadians(baseX));
      result[1] = (float) Math.toDegrees(scratch.euler.y - Math.toRadians(baseY));
      result[2] = (float) Math.toDegrees(scratch.euler.z - Math.toRadians(baseZ));
   }

   private static void setFixedQuaternion(QuaternionScratch scratch, Quaternionf target,
                                          float xDegrees, float yDegrees, float zDegrees,
                                          float baseXDegrees, float baseYDegrees, float baseZDegrees) {
      scratch.base.rotationZYX((float) Math.toRadians(baseZDegrees),
         (float) Math.toRadians(baseYDegrees), (float) Math.toRadians(baseXDegrees));
      scratch.value.rotationZYX((float) Math.toRadians(zDegrees),
         (float) Math.toRadians(yDegrees), (float) Math.toRadians(xDegrees));
      scratch.base.mul(scratch.value, target);
   }

   private static void toEulerZYX(Quaternionf rotation, Vector3f result) {
      // Keep quaternion output in the same ZYX convention on every supported Minecraft version.
      float x = rotation.x;
      float y = rotation.y;
      float z = rotation.z;
      float w = rotation.w;
      double pitch = 2.0D * (w * y - z * x);
      pitch = Math.max(-1.0D, Math.min(1.0D, pitch));
      result.x = (float) Math.atan2(2.0D * (w * x + y * z), 1.0D - 2.0D * (x * x + y * y));
      result.y = (float) Math.asin(pitch);
      result.z = (float) Math.atan2(2.0D * (w * z + x * y), 1.0D - 2.0D * (y * y + z * z));
   }

   private static int lowerBound(List<MLuaBBModelAsset.Keyframe> frames, double time) {
      int low = 0;
      int high = frames.size();
      while (low < high) {
         int middle = (low + high) >>> 1;
         if (frames.get(middle).time() < time) low = middle + 1;
         else high = middle;
      }
      return low;
   }

   private static void sampleCatmullRom(boolean looping,
                                        List<MLuaBBModelAsset.Keyframe> frames,
                                        int beforeIndex, float amount, float[] result) {
      MLuaBBModelAsset.Keyframe before = frames.get(beforeIndex);
      MLuaBBModelAsset.Keyframe after = frames.get(beforeIndex + 1);
      MLuaBBModelAsset.Keyframe beforePrevious = beforeIndex > 0 ? frames.get(beforeIndex - 1) : null;
      MLuaBBModelAsset.Keyframe afterNext = beforeIndex + 2 < frames.size() ? frames.get(beforeIndex + 2) : null;
      if (looping && frames.size() > 2) {
         if (beforePrevious == null) beforePrevious = frames.get(frames.size() - 2);
         if (afterNext == null) afterNext = frames.get(1);
      }
      if (before.hasSeparateOutgoingValue()) beforePrevious = null;
      if (after.hasSeparateOutgoingValue()) afterNext = null;

      float t2 = amount * amount;
      float t3 = t2 * amount;
      for (int axis = 0; axis < 3; axis++) {
         float start = before.outgoingValue(axis);
         float end = after.value(axis);
         float previous = beforePrevious == null ? start : beforePrevious.outgoingValue(axis);
         float next = afterNext == null ? end : afterNext.value(axis);
         result[axis] = 0.5F * (2.0F * start
            + (-previous + end) * amount
            + (2.0F * previous - 5.0F * start + 4.0F * end - next) * t2
            + (-previous + 3.0F * start - 3.0F * end + next) * t3);
      }
   }

   private static void sampleBezier(MLuaBBModelAsset.Keyframe before,
                                    MLuaBBModelAsset.Keyframe after,
                                    double time, float[] result) {
      double startTime = before.time();
      double endTime = after.time();
      double span = endTime - startTime;
      if (span <= 0.0D) {
         copyValues(after, result);
         return;
      }

      double targetX = Math.max(startTime, Math.min(endTime, time));
      for (int axis = 0; axis < 3; axis++) {
         double startValue = before.outgoingValue(axis);
         double endValue = after.value(axis);
         double firstControlTime = clamp(startTime + before.bezierRightTime(axis), startTime, endTime);
         double secondControlTime = clamp(endTime + after.bezierLeftTime(axis), startTime, endTime);

         // A crossed pair of time handles has no single-valued inverse. Keep the
         // curve monotonic by meeting the handles at the center of this segment.
         if (firstControlTime > secondControlTime) {
            firstControlTime = startTime + span * 0.5D;
            secondControlTime = firstControlTime;
         }

         double firstControlValue = startValue + before.bezierRightValue(axis);
         double secondControlValue = endValue + after.bezierLeftValue(axis);
         double curveAmount = solveBezierTime(targetX, startTime, firstControlTime,
            secondControlTime, endTime);
         result[axis] = (float) cubic(startValue, firstControlValue,
            secondControlValue, endValue, curveAmount);
      }
   }

   private static double solveBezierTime(double target, double start,
                                        double firstControl, double secondControl,
                                        double end) {
      double amount = (target - start) / (end - start);
      double low = 0.0D;
      double high = 1.0D;
      for (int iteration = 0; iteration < 8; iteration++) {
         double x = cubic(start, firstControl, secondControl, end, amount);
         double error = x - target;
         if (Math.abs(error) < 1.0e-7D) return amount;
         if (error < 0.0D) low = amount;
         else high = amount;

         double inverse = 1.0D - amount;
         double slope = 3.0D * inverse * inverse * (firstControl - start)
            + 6.0D * inverse * amount * (secondControl - firstControl)
            + 3.0D * amount * amount * (end - secondControl);
         double next = Math.abs(slope) < 1.0e-8D ? Double.NaN : amount - error / slope;
         if (!Double.isFinite(next) || next <= low || next >= high) next = (low + high) * 0.5D;
         amount = next;
      }
      return amount;
   }

   private static double cubic(double start, double firstControl, double secondControl,
                               double end, double amount) {
      double inverse = 1.0D - amount;
      return inverse * inverse * inverse * start
         + 3.0D * inverse * inverse * amount * firstControl
         + 3.0D * inverse * amount * amount * secondControl
         + amount * amount * amount * end;
   }

   private static void copyOutgoing(MLuaBBModelAsset.Keyframe keyframe, float[] result) {
      for (int axis = 0; axis < 3; axis++) result[axis] = keyframe.outgoingValue(axis);
   }

   private static void copyValues(MLuaBBModelAsset.Keyframe keyframe, float[] result) {
      result[0] = keyframe.value(0);
      result[1] = keyframe.value(1);
      result[2] = keyframe.value(2);
   }

   static void clear(String channel, float[] result) {
      if (result == null || result.length < 3) {
         throw new IllegalArgumentException("Animation sample output must contain at least three values");
      }
      float value = channel.equals("scale") ? 1.0F : 0.0F;
      result[0] = value;
      result[1] = value;
      result[2] = value;
   }

   private static double clamp(double value, double minimum, double maximum) {
      return Math.max(minimum, Math.min(maximum, value));
   }

}
