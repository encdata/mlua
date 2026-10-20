package dev.mlua.minecraft.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(
   modid = "mlua",
   value = {Dist.CLIENT}
)
public final class MLuaForgeCameraEvents {
   private MLuaForgeCameraEvents() {
   }

   @SubscribeEvent
   public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
      event.setYaw(MLuaCameraController.getScriptableYaw(event.getYaw()));
      event.setPitch(MLuaCameraController.getScriptablePitch(event.getPitch()));
   }

   @SubscribeEvent
   public static void onFov(ViewportEvent.ComputeFov event) {
      event.setFOV(MLuaCameraController.applyFovOverride(
         event.getFOV(), MLuaCameraController.currentRenderPartialTick()));
   }
}
