package dev.mlua.minecraft.client;

import dev.mlua.math.MLuaCFrame;
import java.util.Locale;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
public final class MLuaCameraController {
   private static MLuaCFrame scriptableCFrame;
   private static Double fovOverride;
   private static double fovMultiplier = 1.0;
   private static String mode = "Custom";
    private static MLuaCFrame heldItemOffset;
    private static MLuaCFrame heldItemLeftOffset;

    public static MLuaCFrame getHeldItemOffset() { return heldItemOffset; }
    public static void setHeldItemOffset(MLuaCFrame offset) { heldItemOffset = offset; }
    public static MLuaCFrame getHeldItemLeftOffset() { return heldItemLeftOffset; }
    public static void setHeldItemLeftOffset(MLuaCFrame offset) { heldItemLeftOffset = offset; }

   private MLuaCameraController() {
   }

   public static MLuaCFrame getCFrame() {
      Minecraft mc = MLuaMinecraftClient.instance();
      Camera camera = mc.gameRenderer.getMainCamera();
      Vec3 pos = camera.getPosition();
      Vector3f look = camera.getLookVector();
      return MLuaCFrame.fromEntity(pos.x, pos.y, pos.z, (double)look.x(), (double)look.y(), (double)look.z());
   }

   public static void setCFrame(MLuaCFrame cframe) {
      scriptableCFrame = cframe;
   }

   public static void setFov(double fov) {
      fovOverride = Math.max(1.0, Math.min(179.0, fov));
   }

   public static void setFovMultiplier(double multiplier) {
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
      mode = newMode;
      Minecraft mc = MLuaMinecraftClient.instance();
      switch (newMode.toLowerCase(Locale.ROOT)) {
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
         case "scriptable":
         case "custom":
            if (newMode.equalsIgnoreCase("scriptable")) {
               mode = "Scriptable";
            }
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
      Minecraft mc = MLuaMinecraftClient.instance();
      scriptableCFrame = null;
      fovOverride = null;
      fovMultiplier = 1.0;
      mode = "Custom";
      if (mc.player != null) {
         mc.setCameraEntity(mc.player);
      }

   }

   public static Vec3 getScriptablePosition() {
      return scriptableCFrame != null && "Scriptable".equals(mode) ? new Vec3(scriptableCFrame.x(), scriptableCFrame.y(), scriptableCFrame.z()) : null;
   }

   public static float getScriptableYaw(float currentYaw) {
      return scriptableCFrame != null && "Scriptable".equals(mode) ? scriptableCFrame.minecraftYaw() : currentYaw;
   }

   public static float getScriptablePitch(float currentPitch) {
      return scriptableCFrame != null && "Scriptable".equals(mode) ? scriptableCFrame.minecraftPitch() : currentPitch;
   }

   public static double applyFovOverride(double currentFov) {
      if (fovOverride != null || fovMultiplier != 1.0) {
         double fov = fovOverride != null ? fovOverride : currentFov;
         return fov * fovMultiplier;
      }
      return currentFov;
   }

}
