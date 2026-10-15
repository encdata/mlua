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
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.modules.enums.MLuaEnumValue;
import dev.mlua.module.MLuaRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.luaj.vm2.Varargs;

@MLuaDescription("Client-side camera control for transforms, subjects, field of view, and held-item offsets.")
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

   @MLuaDescription("Sets the first-person right-hand pose to a CFrame or a callback sampled once per client tick. The callback receives player, hand, itemStack, and arm.")
   public void SetRightHandOffset(LuaValue value) {
      ensureClient();
      setHandOffset(value, false);
   }

   @MLuaDescription("Returns the current first-person right-hand offset CFrame, callback, or nil.")
   public LuaValue GetRightHandOffset() {
      ensureClient();
      return MLuaCameraController.getHeldItemOffsetValue();
   }

   @MLuaDescription("Sets the first-person left-hand pose to a CFrame or a callback sampled once per client tick. The callback receives player, hand, itemStack, and arm.")
   public void SetLeftHandOffset(LuaValue value) {
      ensureClient();
      setHandOffset(value, true);
   }

   @MLuaDescription("Returns the current first-person left-hand offset CFrame, callback, or nil.")
   public LuaValue GetLeftHandOffset() {
      ensureClient();
      return MLuaCameraController.getHeldItemLeftOffsetValue();
   }

   @MLuaDescription("Clears scriptable camera overrides.")
   public void Reset() { ensureClient(); MLuaCameraController.reset(); }

   private void setHandOffset(LuaValue value, boolean leftHand) {
      if (value.isnil()) {
         if (leftHand) MLuaCameraController.setHeldItemLeftOffset(null);
         else MLuaCameraController.setHeldItemOffset(null);
         return;
      }
      if (value.isfunction()) {
         MLuaRegistration registration = context.mlua().registration();
         MLuaCameraController.HandOffsetSampler sampler = (player, hand, stack, arm) -> {
            LuaValue luaPlayer = player == null ? LuaValue.NIL
               : MLuaPlayerWrapper.playerEntityTable(player, registration);
            LuaValue luaHand = MLuaEnumValue.forJava(hand);
            LuaValue luaStack = MLuaItemWrapper.itemTable(stack == null ? ItemStack.EMPTY : stack, registration);
            LuaValue luaArm = LuaValue.valueOf(arm == HumanoidArm.RIGHT ? "Right" : "Left");
            Varargs result = value.invoke(LuaValue.varargsOf(new LuaValue[]{luaPlayer, luaHand, luaStack, luaArm}));
            return MLuaCFrame.fromLua(result.arg1());
         };
         if (leftHand) MLuaCameraController.setHeldItemLeftOffsetCallback(value, sampler, context.mlua().executionLock());
         else MLuaCameraController.setHeldItemOffsetCallback(value, sampler, context.mlua().executionLock());
         return;
      }
      try {
         MLuaCFrame offset = MLuaCFrame.fromLua(value);
         if (leftHand) MLuaCameraController.setHeldItemLeftOffset(offset);
         else MLuaCameraController.setHeldItemOffset(offset);
      } catch (IllegalArgumentException error) {
         throw new LuaError("[MLua] Hand offset must be a CFrame or a function returning CFrame: " + error.getMessage());
      }
   }

   private void ensureClient() {
      if (this.context.mlua().side() != Side.CLIENT) {
         throw new LuaError("[MLua] Camera is client-side only. Use Minecraft:GetService(\"Camera\") from a .client.lua script. Server camera control needs a future networked player.Camera API.");
      }
   }

}
