package dev.mlua.modules.camera.Service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.common.enums.Side;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.MLuaContext;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
import dev.mlua.mc.impl.entity.MCLocalPlayerImpl;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.client.MLuaCameraController;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
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
   public LuaTable GetCFrame() { this.requireClientSide(this.name()); return MLuaCameraController.getCFrame().toLua(); }

   @MLuaDescription("Sets the scriptable camera transform from a finite CFrame.")
   public void SetCFrame(LuaValue value) {
      this.requireClientSide(this.name());
      MLuaCameraController.setCFrame(requireFiniteFrame(MLuaCFrame.fromLua(value), "Camera:SetCFrame"));
   }

   @MLuaDescription("Returns the current camera position.")
   public LuaValue GetPosition() { this.requireClientSide(this.name()); return MLuaCameraController.getCFrame().toLua().get("Position"); }

   @MLuaDescription("Sets the scriptable camera position from a finite Vector3 or CFrame while keeping its current look direction.")
   public void SetPosition(LuaValue value) {
      this.requireClientSide(this.name());
      double[] position = MLuaLuaArguments.finiteVector3(value, "Camera:SetPosition position");
      MLuaCFrame current = MLuaCameraController.getCFrame();
      double[] look = current.lookVector();
      MLuaCameraController.setCFrame(requireFiniteFrame(MLuaCFrame.lookAt(position[0], position[1], position[2],
         position[0] + look[0], position[1] + look[1], position[2] + look[2]), "Camera:SetPosition"));
   }

   @MLuaDescription("Returns the current camera forward direction.")
   public LuaValue GetLookVector() { this.requireClientSide(this.name()); return MLuaCameraController.getCFrame().toLua().get("LookVector"); }

   @MLuaDescription("Turns the camera toward a position or entity.")
   public void LookAt(LuaValue value) {
      this.requireClientSide(this.name());
      MLuaCFrame current = MLuaCameraController.getCFrame();
      double[] target = MLuaEntityWrapper.targetPosition(value);
      for (double coordinate : target) {
         if (!Double.isFinite(coordinate)) throw new LuaError("[MLua] Camera:LookAt target coordinates must be finite.");
      }
      MLuaCameraController.setCFrame(requireFiniteFrame(
         MLuaCFrame.lookAt(current.x(), current.y(), current.z(), target[0], target[1], target[2]), "Camera:LookAt"));
   }

   @MLuaDescription("Returns the camera field of view.")
   public double GetFOV() { this.requireClientSide(this.name()); return MLuaCameraController.getFov(); }

   @MLuaDescription("Sets a finite camera field of view, clamped to Minecraft's supported range of 1 to 179 degrees.")
   public void SetFOV(double value) {
      this.requireClientSide(this.name());
      requireFinite(value, "Camera:SetFOV value");
      MLuaCameraController.setFov(value);
   }

   @MLuaDescription("Returns the current field-of-view multiplier.")
   public double GetFOVMultiplier() { this.requireClientSide(this.name()); return MLuaCameraController.getFovMultiplier(); }

   @MLuaDescription("Sets a finite field-of-view multiplier; negative values are clamped to zero.")
   public void SetFOVMultiplier(double value) {
      this.requireClientSide(this.name());
      requireFinite(value, "Camera:SetFOVMultiplier value");
      MLuaCameraController.setFovMultiplier(value);
   }

   @MLuaDescription("Multiplies the current field of view by a finite value.")
   public void MultiplyFOV(double value) {
      this.requireClientSide(this.name());
      requireFinite(value, "Camera:MultiplyFOV multiplier");
      double multiplier = MLuaCameraController.getFovMultiplier() * value;
      requireFinite(multiplier, "Camera:MultiplyFOV result");
      MLuaCameraController.setFovMultiplier(multiplier);
   }

   @MLuaDescription("Returns the camera subject, if one is set.")
   public LuaValue GetSubject() {
      this.requireClientSide(this.name());
      Entity entity = MLuaCameraController.getSubject();
      if (!(entity instanceof LivingEntity living)) return LuaValue.NIL;
      if (living instanceof LocalPlayer local) {
         return MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(local), context.mlua().registration());
      }
      return MLuaEntityWrapper.entityTable(living, context.mlua().registration());
   }

   @MLuaDescription("Sets the camera subject to an entity or player, or resets it to the local player when nil.")
   public void SetSubject(LuaValue value) {
      this.requireClientSide(this.name());
      if (value.isnil()) {
         Minecraft minecraft = MLuaMinecraftClient.instance();
         if (minecraft != null && minecraft.player != null) MLuaCameraController.setSubject(minecraft.player);
         return;
      }
      Entity entity = MLuaEntityWrapper.requireLuaInstance(value, context.mlua().registration(), "Entity", "Player or Entity");
      MLuaCameraController.setSubject(entity);
   }

   @MLuaDescription("Returns the canonical camera control mode: Custom, Scriptable, FirstPerson, SecondPerson, or ThirdPerson.")
   public String GetMode() { this.requireClientSide(this.name()); return MLuaCameraController.getMode(); }

   @MLuaDescription("Sets the camera control mode; rejects unknown modes without changing the current mode.")
   public void SetMode(String value) {
      this.requireClientSide(this.name());
      try {
         MLuaCameraController.setMode(value.trim());
      } catch (IllegalArgumentException invalid) {
         throw new LuaError(invalid.getMessage());
      }
   }

   @MLuaDescription("Returns the current first-person or third-person view mode.")
   public String GetViewMode() { this.requireClientSide(this.name()); return MLuaCameraController.getViewMode(); }

   @MLuaDescription("Sets the first-person or third-person view mode.")
   public void SetViewMode(String value) {
      this.requireClientSide(this.name());
      try {
         MLuaCameraController.setViewMode(value.trim());
      } catch (IllegalArgumentException invalid) {
         throw new LuaError(invalid.getMessage());
      }
   }

   @MLuaDescription("Sets this addon's first-person right-arm pose layer to a CFrame or a callback sampled once per client tick. Addon layers compose in registration order. The callback receives player, hand, itemStack, and arm.")
   public void SetRightHandOffset(LuaValue value) {
      this.requireClientSide(this.name());
      setHandOffset(value, false);
   }

   @MLuaDescription("Returns this addon's right-arm CFrame, callback, or nil.")
   public LuaValue GetRightHandOffset() {
      this.requireClientSide(this.name());
      return MLuaCameraController.getHeldItemOffsetValue(owner());
   }

   @MLuaDescription("Sets this addon's first-person left-arm pose layer to a CFrame or a callback sampled once per client tick. Addon layers compose in registration order. The callback receives player, hand, itemStack, and arm.")
   public void SetLeftHandOffset(LuaValue value) {
      this.requireClientSide(this.name());
      setHandOffset(value, true);
   }

   @MLuaDescription("Returns this addon's left-arm CFrame, callback, or nil.")
   public LuaValue GetLeftHandOffset() {
      this.requireClientSide(this.name());
      return MLuaCameraController.getHeldItemLeftOffsetValue(owner());
   }

   @MLuaDescription("Resets camera overrides and removes this addon's first-person hand-pose layers.")
   public void Reset() { this.requireClientSide(this.name()); MLuaCameraController.reset(owner()); }

   private void setHandOffset(LuaValue value, boolean leftHand) {
      if (value.isnil()) {
         if (leftHand) MLuaCameraController.setHeldItemLeftOffset(owner(), null);
         else MLuaCameraController.setHeldItemOffset(owner(), null);
         return;
      }
      if (value.isfunction()) {
         MLuaRegistration registration = context.mlua().registration();
         MLuaContext callbackContext = MLuaLuaBindings.currentContext();
         MLuaCameraController.HandOffsetSampler sampler = (player, hand, stack, arm) -> {
            LuaValue luaPlayer = player == null ? LuaValue.NIL
               : MLuaPlayerWrapper.playerEntityTable(player, registration);
            LuaValue luaHand = MLuaEnumValue.forJava(hand);
            LuaValue luaStack = MLuaItemWrapper.itemTable(stack == null ? ItemStack.EMPTY : stack, registration);
            LuaValue luaArm = LuaValue.valueOf(arm == HumanoidArm.RIGHT ? "Right" : "Left");
            Varargs result = MLuaLuaBindings.withContext(callbackContext,
               () -> value.invoke(LuaValue.varargsOf(new LuaValue[]{luaPlayer, luaHand, luaStack, luaArm})));
            return requireFiniteFrame(MLuaCFrame.fromLua(result.arg1()), "Camera hand offset callback");
         };
         if (leftHand) MLuaCameraController.setHeldItemLeftOffsetCallback(owner(), value, sampler, context.mlua().executionLock());
         else MLuaCameraController.setHeldItemOffsetCallback(owner(), value, sampler, context.mlua().executionLock());
         return;
      }
      try {
         MLuaCFrame offset = requireFiniteFrame(MLuaCFrame.fromLua(value), "Camera hand offset");
         if (leftHand) MLuaCameraController.setHeldItemLeftOffset(owner(), offset);
         else MLuaCameraController.setHeldItemOffset(owner(), offset);
      } catch (IllegalArgumentException error) {
         throw new LuaError("[MLua] Hand offset must be a CFrame or a function returning CFrame: " + error.getMessage());
      }
   }

   private dev.mlua.runtime.client.MLuaHandPoseRegistry.Owner owner() {
      return new dev.mlua.runtime.client.MLuaHandPoseRegistry.Owner(
         context.mlua().session(), context.mlua().ownerModId());
   }

   private static void requireFinite(double value, String argument) {
      if (!Double.isFinite(value)) throw new LuaError("[MLua] " + argument + " must be finite.");
   }

   private static MLuaCFrame requireFiniteFrame(MLuaCFrame frame, String argument) {
      double[] components = {frame.x(), frame.y(), frame.z(), frame.r00(), frame.r01(), frame.r02(),
         frame.r10(), frame.r11(), frame.r12(), frame.r20(), frame.r21(), frame.r22()};
      for (double component : components) requireFinite(component, argument + " CFrame component");
      return frame;
   }

}
