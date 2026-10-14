package dev.mlua.modules.camera.Service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.common.enums.Side;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
import dev.mlua.mc.impl.entity.MCLocalPlayerImpl;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.client.MLuaCameraController;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

@MLuaDescription("Client-side camera service.")
public final class MLuaCameraService extends MLuaReflectiveService {
   public MLuaCameraService(MLuaService.Context context) {
      super(context);
   }

   @Override
   public String name() {
      return "Camera";
   }

   @Override
   public LuaTable toLua() {
      LuaTable table = super.toLua();
      table.set("__side", "Client");
      return table;
   }

   @MLuaDescription("Returns the current camera transform.")
   public LuaTable GetCFrame() { ensureClient(); return MLuaCameraController.getCFrame().toLua(); }

   @MLuaDescription("Sets the scriptable camera transform.")
   public void SetCFrame(LuaValue value) { ensureClient(); MLuaCameraController.setCFrame(MLuaCFrame.fromLua(value)); }

   @MLuaDescription("Returns the current camera position.")
   public LuaValue GetPosition() { ensureClient(); return MLuaCameraController.getCFrame().toLua().get("Position"); }

   @MLuaDescription("Sets the scriptable camera position while keeping its current look direction.")
   public void SetPosition(LuaValue value) {
      ensureClient();
      double[] position = MLuaCFrame.vector(value);
      MLuaCFrame current = MLuaCameraController.getCFrame();
      double[] look = current.lookVector();
      MLuaCameraController.setCFrame(MLuaCFrame.lookAt(position[0], position[1], position[2],
         position[0] + look[0], position[1] + look[1], position[2] + look[2]));
   }

   @MLuaDescription("Returns the current camera forward direction.")
   public LuaValue GetLookVector() { ensureClient(); return MLuaCameraController.getCFrame().toLua().get("LookVector"); }

   @MLuaDescription("Turns the camera toward a position or entity.")
   public void LookAt(LuaValue value) {
      ensureClient();
      MLuaCFrame current = MLuaCameraController.getCFrame();
      double[] target = MLuaEntityWrapper.targetPosition(value);
      MLuaCameraController.setCFrame(MLuaCFrame.lookAt(current.x(), current.y(), current.z(), target[0], target[1], target[2]));
   }

   @MLuaDescription("Returns the camera field of view.")
   public double GetFOV() { ensureClient(); return MLuaCameraController.getFov(); }

   @MLuaDescription("Sets the camera field of view.")
   public void SetFOV(double value) { ensureClient(); MLuaCameraController.setFov(value); }

   @MLuaDescription("Returns the current field-of-view multiplier.")
   public double GetFOVMultiplier() { ensureClient(); return MLuaCameraController.getFovMultiplier(); }

   @MLuaDescription("Sets the field-of-view multiplier.")
   public void SetFOVMultiplier(double value) { ensureClient(); MLuaCameraController.setFovMultiplier(value); }

   @MLuaDescription("Multiplies the current field of view by a value.")
   public void MultiplyFOV(double value) { ensureClient(); MLuaCameraController.setFovMultiplier(MLuaCameraController.getFovMultiplier() * value); }

   @MLuaDescription("Returns the camera subject, if one is set.")
   public LuaValue GetSubject() {
      ensureClient();
      Entity entity = MLuaCameraController.getSubject();
      if (!(entity instanceof LivingEntity living)) return LuaValue.NIL;
      if (living instanceof LocalPlayer local) {
         return MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(local), context.mlua().registration());
      }
      return MLuaEntityWrapper.entityTable(living, context.mlua().registration());
   }

   @MLuaDescription("Sets the camera subject to an entity or player, or resets it to the local player when nil.")
   public void SetSubject(LuaValue value) {
      ensureClient();
      if (value.isnil()) {
         Minecraft minecraft = MLuaMinecraftClient.instance();
         if (minecraft != null && minecraft.player != null) MLuaCameraController.setSubject(minecraft.player);
         return;
      }
      Entity entity = MLuaEntityWrapper.requireLuaInstance(value, context.mlua().registration(), "Entity", "Player or Entity");
      MLuaCameraController.setSubject(entity);
   }

   @MLuaDescription("Returns the current camera control mode.")
   public String GetMode() { ensureClient(); return MLuaCameraController.getMode(); }

   @MLuaDescription("Sets the camera control mode.")
   public void SetMode(String value) { ensureClient(); MLuaCameraController.setMode(value); }

   @MLuaDescription("Returns the current first-person or third-person view mode.")
   public String GetViewMode() { ensureClient(); return MLuaCameraController.getViewMode(); }

   @MLuaDescription("Sets the first-person or third-person view mode.")
   public void SetViewMode(String value) { ensureClient(); MLuaCameraController.setViewMode(value); }

   @MLuaDescription("Sets the first-person right-hand camera offset.")
   public void SetRightHandOffset(LuaValue value) { ensureClient(); MLuaCameraController.setHeldItemOffset(MLuaCFrame.fromLua(value)); }

   @MLuaDescription("Returns the first-person right-hand camera offset.")
   public LuaValue GetRightHandOffset() {
      ensureClient();
      MLuaCFrame offset = MLuaCameraController.getHeldItemOffset();
      return offset != null ? offset.toLua() : LuaValue.NIL;
   }

   @MLuaDescription("Sets the first-person left-hand camera offset.")
   public void SetLeftHandOffset(LuaValue value) { ensureClient(); MLuaCameraController.setHeldItemLeftOffset(MLuaCFrame.fromLua(value)); }

   @MLuaDescription("Returns the first-person left-hand camera offset.")
   public LuaValue GetLeftHandOffset() {
      ensureClient();
      MLuaCFrame offset = MLuaCameraController.getHeldItemLeftOffset();
      return offset != null ? offset.toLua() : LuaValue.NIL;
   }

   @MLuaDescription("Clears scriptable camera overrides.")
   public void Reset() { ensureClient(); MLuaCameraController.reset(); }

   private void ensureClient() {
      if (this.context.mlua().side() != Side.CLIENT) {
         throw new LuaError("[MLua] Camera is client-side only. Use Minecraft:GetService(\"Camera\") from a .client.lua script. Server camera control needs a future networked player.Camera API.");
      }
   }

}
