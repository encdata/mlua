package dev.mlua.minecraft.client;

import dev.mlua.math.MLuaCFrame;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Locale;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.luaj.vm2.LuaValue;
import dev.mlua.runtime.client.MLuaHandPoseRegistry;
import dev.mlua.runtime.client.MLuaRenderCallbacks;

public final class MLuaCameraController {
   private static MLuaCFrame scriptableCFrame;
   private static MLuaCFrame previousScriptableCFrame;
   private record CameraFrameSample(MLuaCFrame previous, MLuaCFrame current,
                                    int partialTickBits, MLuaCFrame value) { }
   private static volatile CameraFrameSample cameraFrameSample;
   private static Double fovOverride;
   private static Double previousFovOverride;
   private static double fovMultiplier = 1.0;
   private static double previousFovMultiplier = 1.0;
   private static String mode = "Custom";
   private static final MLuaHandPoseRegistry HAND_POSES = new MLuaHandPoseRegistry();

    @FunctionalInterface
    public interface HandOffsetSampler {
       MLuaCFrame sample(AbstractClientPlayer player, InteractionHand hand, ItemStack stack, HumanoidArm arm);
    }

   public static MLuaCFrame getHeldItemOffset() { return HAND_POSES.composed(HumanoidArm.RIGHT, 1.0f); }
   public static void setHeldItemOffset(MLuaCFrame offset) { setHeldItemOffset(null, offset); }
   public static void setHeldItemOffset(MLuaHandPoseRegistry.Owner owner, MLuaCFrame offset) {
      HAND_POSES.set(owner, HumanoidArm.RIGHT, offset);
   }
   public static MLuaCFrame getHeldItemLeftOffset() { return HAND_POSES.composed(HumanoidArm.LEFT, 1.0f); }
   public static void setHeldItemLeftOffset(MLuaCFrame offset) { setHeldItemLeftOffset(null, offset); }
   public static void setHeldItemLeftOffset(MLuaHandPoseRegistry.Owner owner, MLuaCFrame offset) {
      HAND_POSES.set(owner, HumanoidArm.LEFT, offset);
   }
   public static LuaValue getHeldItemOffsetValue() { return getHeldItemOffsetValue(null); }
   public static LuaValue getHeldItemOffsetValue(MLuaHandPoseRegistry.Owner owner) {
      return HAND_POSES.getValue(owner, HumanoidArm.RIGHT);
   }
   public static LuaValue getHeldItemLeftOffsetValue() { return getHeldItemLeftOffsetValue(null); }
   public static LuaValue getHeldItemLeftOffsetValue(MLuaHandPoseRegistry.Owner owner) {
      return HAND_POSES.getValue(owner, HumanoidArm.LEFT);
   }
   public static void setHeldItemOffsetCallback(LuaValue callback, Object executionLock) {
      setHeldItemOffsetCallback(null, callback,
         (player, hand, stack, arm) -> MLuaCFrame.fromLua(callback.invoke(LuaValue.NONE).arg1()), executionLock);
   }
   public static void setHeldItemLeftOffsetCallback(LuaValue callback, Object executionLock) {
      setHeldItemLeftOffsetCallback(null, callback,
         (player, hand, stack, arm) -> MLuaCFrame.fromLua(callback.invoke(LuaValue.NONE).arg1()), executionLock);
   }
   public static void setHeldItemOffsetCallback(LuaValue callback, HandOffsetSampler sampler, Object executionLock) {
      setHeldItemOffsetCallback(null, callback, sampler, executionLock);
   }
   public static void setHeldItemLeftOffsetCallback(LuaValue callback, HandOffsetSampler sampler, Object executionLock) {
      setHeldItemLeftOffsetCallback(null, callback, sampler, executionLock);
   }
   public static void setHeldItemOffsetCallback(MLuaHandPoseRegistry.Owner owner, LuaValue callback,
                                                HandOffsetSampler sampler, Object executionLock) {
      if (sampler == null) throw new IllegalArgumentException("Right hand offset sampler is required");
      HAND_POSES.setCallback(owner, HumanoidArm.RIGHT, callback, sampler::sample, executionLock);
   }
   public static void setHeldItemLeftOffsetCallback(MLuaHandPoseRegistry.Owner owner, LuaValue callback,
                                                    HandOffsetSampler sampler, Object executionLock) {
      if (sampler == null) throw new IllegalArgumentException("Left hand offset sampler is required");
      HAND_POSES.setCallback(owner, HumanoidArm.LEFT, callback, sampler::sample, executionLock);
   }
    public static void tickHandOffsets() {
       Minecraft minecraft = MLuaMinecraftClient.instance();
       Object clientPlayer = minecraft == null ? null : minecraft.player;
       AbstractClientPlayer player = clientPlayer instanceof AbstractClientPlayer abstractClientPlayer
          ? abstractClientPlayer : null;
       HAND_POSES.sample(player, HumanoidArm.RIGHT);
       HAND_POSES.sample(player, HumanoidArm.LEFT);
    }

    public static void clearHandOffsets() {
       HAND_POSES.clear();
    }

    public static void clearHandOffsets(Object runtimeSession, String modId) {
       HAND_POSES.clearOwned(new MLuaHandPoseRegistry.Owner(runtimeSession, modId));
    }

    public static void clearHandOffsetsForRuntime(Object runtimeSession) {
       HAND_POSES.clearRuntime(runtimeSession);
    }

    /** Applies the scriptable physical left/right viewmodel pose inside Minecraft's hand render scope. */
    public static void applyFirstPersonHandOffset(PoseStack poseStack, AbstractClientPlayer player, InteractionHand hand,
                                                   float partialTick) {
       if (poseStack == null || player == null || hand == null) return;
       HumanoidArm arm = hand == InteractionHand.MAIN_HAND
          ? player.getMainArm()
          : player.getMainArm().getOpposite();
       MLuaCFrame offset = HAND_POSES.composed(arm, partialTick);
       if (offset == null) return;
       poseStack.translate(offset.x(), offset.y(), offset.z());
       double[] euler = offset.toEulerAnglesXYZ();
       if (euler[0] != 0.0) poseStack.mulPose(Axis.XP.rotation((float) euler[0]));
       if (euler[1] != 0.0) poseStack.mulPose(Axis.YP.rotation((float) euler[1]));
       if (euler[2] != 0.0) poseStack.mulPose(Axis.ZP.rotation((float) euler[2]));
    }

   private MLuaCameraController() {
   }

   public static MLuaCFrame getCFrame() {
      if (scriptableCFrame != null && "Scriptable".equals(mode)) return scriptableCFrame;
      Minecraft mc = MLuaMinecraftClient.instance();
      Camera camera = mc.gameRenderer.getMainCamera();
      Vec3 pos = camera.getPosition();
      Vector3f look = camera.getLookVector();
      return MLuaCFrame.fromEntity(pos.x, pos.y, pos.z, (double)look.x(), (double)look.y(), (double)look.z());
   }

   /** Returns the camera pose blended between script updates for this rendered frame. */
   public static MLuaCFrame getScriptableCFrame(float partialTick) {
      if (scriptableCFrame == null || !"Scriptable".equals(mode)) return null;
      MLuaCFrame current = scriptableCFrame;
      MLuaCFrame previous = previousScriptableCFrame;
      if (previous == null) return current;
      double alpha = Float.isFinite(partialTick) ? Math.max(0.0D, Math.min(1.0D, partialTick)) : 1.0D;
      int partialTickBits = Float.floatToIntBits(partialTick);
      CameraFrameSample cached = cameraFrameSample;
      if (cached != null && cached.previous() == previous && cached.current() == current
            && cached.partialTickBits() == partialTickBits) return cached.value();
      MLuaCFrame value = previous.lerp(current, alpha);
      cameraFrameSample = new CameraFrameSample(previous, current, partialTickBits, value);
      return value;
   }

   /** Uses the current Minecraft frame fraction when a loader hook has no direct DeltaTracker parameter. */
   public static float currentRenderPartialTick() {
      Float partialTick = MLuaRenderCallbacks.currentClientPartialTick();
      return partialTick == null ? 1.0F : partialTick;
   }

   public static void setCFrame(MLuaCFrame cframe) {
      if (cframe == null) throw new IllegalArgumentException("Camera CFrame cannot be null");
      previousScriptableCFrame = scriptableCFrame == null ? cframe : scriptableCFrame;
      scriptableCFrame = cframe;
      cameraFrameSample = null;
   }

   public static void setFov(double fov) {
      previousFovOverride = fovOverride;
      fovOverride = Math.max(1.0, Math.min(179.0, fov));
   }

   public static void setFovMultiplier(double multiplier) {
      previousFovMultiplier = fovMultiplier;
      fovMultiplier = Math.max(0.0, multiplier);
   }

   public static double getFov() {
      double fov = fovOverride != null ? fovOverride : (double)(Integer)MLuaMinecraftClient.instance().options.fov().get();
      return fov * fovMultiplier;
   }

   public static double getFovMultiplier() {
      return fovMultiplier;
   }

   public static void setSubject(Entity entity) {
      MLuaMinecraftClient.instance().setCameraEntity(entity);
   }

   public static Entity getSubject() {
      return MLuaMinecraftClient.instance().getCameraEntity();
   }

   public static void setMode(String newMode) {
      if (newMode == null) throw new IllegalArgumentException("[MLua] CameraMode must be a string.");
      Minecraft mc = MLuaMinecraftClient.instance();
      switch (newMode.toLowerCase(Locale.ROOT)) {
         case "firstperson":
         case "first_person":
         case "first person":
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mode = "FirstPerson";
            break;
         case "secondperson":
         case "second_person":
         case "second person":
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            mode = "SecondPerson";
            break;
         case "thirdperson":
         case "third_person":
         case "third person":
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mode = "ThirdPerson";
            break;
         case "scriptable":
         case "custom":
            mode = newMode.equalsIgnoreCase("scriptable") ? "Scriptable" : "Custom";
            break;
         default:
            throw new IllegalArgumentException("[MLua] Unknown CameraMode: " + newMode);
      }

   }

   public static String getMode() {
      return mode;
   }

   public static String getViewMode() {
      CameraType type = MLuaMinecraftClient.instance().options.getCameraType();
      String var10000;
      switch (type) {
         case FIRST_PERSON:
            var10000 = "FirstPerson";
            break;
         case THIRD_PERSON_FRONT:
            var10000 = "SecondPerson";
            break;
         case THIRD_PERSON_BACK:
            var10000 = "ThirdPerson";
            break;
         default:
            throw new IncompatibleClassChangeError();
      }

      return var10000;
   }

   public static void setViewMode(String viewMode) {
      if (viewMode == null) throw new IllegalArgumentException("[MLua] ViewMode must be a string.");
      Minecraft mc = MLuaMinecraftClient.instance();
      switch (viewMode.toLowerCase(Locale.ROOT)) {
         case "firstperson":
         case "first_person":
         case "first person":
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            break;
         case "secondperson":
         case "second_person":
         case "second person":
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            break;
         case "thirdperson":
         case "third_person":
         case "third person":
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            break;
         default:
            throw new IllegalArgumentException("[MLua] Unknown ViewMode: " + viewMode);
      }

   }

   public static void reset() {
      reset(null);
   }

   public static void reset(MLuaHandPoseRegistry.Owner owner) {
      Minecraft mc = MLuaMinecraftClient.instance();
      scriptableCFrame = null;
      previousScriptableCFrame = null;
      cameraFrameSample = null;
      fovOverride = null;
      previousFovOverride = null;
      fovMultiplier = 1.0;
      previousFovMultiplier = 1.0;
      if (owner == null) clearHandOffsets();
      else HAND_POSES.clearOwned(owner);
      mode = "Custom";
      if (mc.player != null) {
         mc.setCameraEntity(mc.player);
      }

   }

   public static Vec3 getScriptablePosition() {
      return scriptableCFrame != null && "Scriptable".equals(mode) ? new Vec3(scriptableCFrame.x(), scriptableCFrame.y(), scriptableCFrame.z()) : null;
   }

   public static Vec3 getScriptablePosition(float partialTick) {
      MLuaCFrame frame = getScriptableCFrame(partialTick);
      return frame == null ? null : new Vec3(frame.x(), frame.y(), frame.z());
   }

   public static float getScriptableYaw(float currentYaw) {
      return scriptableCFrame != null && "Scriptable".equals(mode) ? scriptableCFrame.minecraftYaw() : currentYaw;
   }

   public static float getScriptableYaw(float currentYaw, float partialTick) {
      MLuaCFrame frame = getScriptableCFrame(partialTick);
      return frame == null ? currentYaw : frame.minecraftYaw();
   }

   public static float getScriptablePitch(float currentPitch) {
      return scriptableCFrame != null && "Scriptable".equals(mode) ? scriptableCFrame.minecraftPitch() : currentPitch;
   }

   public static float getScriptablePitch(float currentPitch, float partialTick) {
      MLuaCFrame frame = getScriptableCFrame(partialTick);
      return frame == null ? currentPitch : frame.minecraftPitch();
   }

   public static double applyFovOverride(double currentFov) {
      return applyFovOverride(currentFov, 1.0F);
   }

   /** Interpolates scripted FOV writes across Minecraft's tick and render phases. */
   public static double applyFovOverride(double currentFov, float partialTick) {
      double alpha = Float.isFinite(partialTick) ? Math.max(0.0D, Math.min(1.0D, partialTick)) : 1.0D;
      if (fovOverride != null || fovMultiplier != 1.0) {
         double fov = fovOverride == null ? currentFov
            : lerp(previousFovOverride == null ? currentFov : previousFovOverride, fovOverride, alpha);
         double multiplier = lerp(previousFovMultiplier, fovMultiplier, alpha);
         return fov * multiplier;
      }
      if (previousFovOverride != null) {
         double fov = lerp(previousFovOverride, currentFov, alpha);
         return fov * lerp(previousFovMultiplier, fovMultiplier, alpha);
      }
      return currentFov;
   }

   private static double lerp(double from, double to, double alpha) {
      return from + (to - from) * alpha;
   }

}
