package dev.mlua.minecraft.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

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
      event.setFOV(MLuaCameraController.applyFovOverride(event.getFOV()));
   }
}
