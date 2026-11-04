package dev.mlua.modules.players.classes;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.modules.effects.service.MLuaPotionEffectsService;
import dev.mlua.minecraft.client.MLuaCameraController;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.runtime.client.MLuaClientThread;
import dev.mlua.runtime.data.MLuaDataManager;
import dev.mlua.mcapi.core.MCVec3;
import dev.mlua.mcapi.entity.MCLocalPlayer;
import dev.mlua.mc.impl.entity.MCEntityImpl;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import static dev.mlua.modules.players.classes.MLuaPlayerWrapper.*;

/** Builds the local Player instance and binds client-only state and service links. */
final class MLuaLocalPlayerBindings {
   private MLuaLocalPlayerBindings() {}

   public static LuaTable localPlayerTable(MCLocalPlayer player) {
      Entity raw = ((MCEntityImpl) player).inner();
      return localPlayerTable(player, MLuaRuntime.registrationForLevel(raw.level()));
   }

   public static LuaTable localPlayerTable(MCLocalPlayer player, MLuaRegistration registration) {
      classTable(registration);
      LocalPlayer raw = (LocalPlayer) ((MCEntityImpl) player).inner();
      LuaTable cachedPlayer = MLuaEntityWrapper.cachedPlayerTable(raw, registration);
      if (cachedPlayer != null) {
         LuaValue cachedEntity = cachedPlayer.rawget("__entity");
         if (cachedEntity.isuserdata(Entity.class)
               && cachedEntity.touserdata(Entity.class) == raw) {
            return cachedPlayer;
         }
      }
      LuaTable table = MLuaEntityWrapper.livingEntityTableBase(raw, registration);
      registration.attachClassInstance(table, "Player", "LivingEntity");
      table.rawset("__entity", LuaValue.userdataOf(raw));
      table.rawset("__is_local_player", LuaValue.TRUE);
      MLuaClassMethods.setInstanceMethod(table, "GetDisplayName", luaFn(() -> {
         return LuaValue.valueOf(!MLuaEntityWrapper.entityDisplayName(player).isBlank() ? MLuaEntityWrapper.entityDisplayName(player) : playerName(player));
      }), "Returns this player's display name.");
      MLuaLocalPlayerChatBindings.bind(table, raw, player);
      MLuaPlayerFoodBindings.bind(table, player);
      MLuaLocalPlayerModeBindings.bind(table, raw);
      MLuaClassMethods.setInstanceMethod(table, "GetExperienceLevel", luaFn(() -> {
         return LuaValue.valueOf(player.getExperienceLevel());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetExperienceProgress", luaFn(() -> {
         return LuaValue.valueOf((double)player.getExperienceProgress());
      }));
      MLuaClassMethods.setInstanceMethod(table, "IsSleeping", luaFn(() -> {
         return LuaValue.valueOf(player.isSleeping());
      }));
      MLuaClassMethods.setInstanceMethod(table, "IsCrouching", luaFn(() -> {
         return LuaValue.valueOf(player.isCrouching());
      }));
      MLuaClassMethods.setInstanceMethod(table, "IsSprinting", luaFn(() -> {
         return LuaValue.valueOf(player.isSprinting());
      }));
      MLuaLocalPlayerActionBindings.bind(table, raw);
      MLuaClassMethods.setInstanceMethod(table, "IsOnline", luaFn(() -> {
         return LuaValue.TRUE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetBlockPosition", luaFn(() -> {
         return positionTable((double)player.getBlockPosition().getX(), (double)player.getBlockPosition().getY(), (double)player.getBlockPosition().getZ());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetEyeCFrame", luaFn(() -> {
         MCVec3 look = MLuaEntityWrapper.lookAngle(player);
         return MLuaCFrame.fromEntity(player.getX(), raw.getEyeY(), player.getZ(), look.x(), look.y(), look.z()).toLua();
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetCameraCFrame", luaFn(() -> {
         return MLuaCameraController.getCFrame().toLua();
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetViewMode", luaFn(() -> {
         return LuaValue.valueOf(MLuaCameraController.getViewMode());
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetViewMode", varArg((args) -> {
         String mode = args.checkjstring(2);
         return MLuaClientThread.callOnClientThread(() -> {
            requireActiveLocalPlayer(raw, "Player:SetViewMode");
            MLuaCameraController.setViewMode(mode);
            return LuaValue.NONE;
         });
      }));
      bindLevelMethods(table, raw.level(), registration);
      MLuaClassMethods.setInstanceMethod(table, "GetPersistentData", luaFn(() -> {
         return MLuaDataManager.playerPersistentData(raw, false);
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetSyncedData", luaFn(() -> {
         return MLuaDataManager.playerSyncedData(raw);
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetHeldItem", luaFn(() -> {
         return heldItem(player.getMainHandItem(), registration);
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetOffhandItem", luaFn(() -> {
         return heldItem(player.getOffhandItem(), registration);
      }));
      MLuaPlayerInventoryBindings.attach(table, player, false, false, registration);
      MLuaClassMethods.setInstanceMethod(table, "GetSelectedSlot", luaFn(() -> {
         return LuaValue.valueOf(player.getInventory().getSelected() + 1);
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetSelectedSlot", varArg((args) -> {
         int slot = MLuaLuaArguments.wholeInt(args, 2, "Player:SetSelectedSlot", "slot") - 1;
         if (slot < 0 || slot >= 9) {
            throw new LuaError("[MLua] Selected slot must be between 1 and 9.");
         }
         return MLuaLocalPlayerActionBindings.run(raw, "Player:SetSelectedSlot", () ->
            player.getInventory().setSelected(slot));
      }));
      attachNearbyPlayers(table, raw, registration);
      MLuaClassMethods.setInstanceMethod(table, "GetEffects", luaFn(() -> {
         return MLuaPotionEffectsService.effectsTable((LivingEntity)((MCEntityImpl) player).inner(), registration);
      }));
      LuaTable attached = MLuaEntityWrapper.attachEntityProperties(table);
      MLuaEntityWrapper.cachePlayerTable(raw, registration, attached);
      return attached;
   }

   static void requireActiveLocalPlayer(LocalPlayer player, String operation) {
      Minecraft minecraft = MLuaMinecraftClient.instance();
      if (!dev.mlua.runtime.client.MLuaClientThread.isClientExecutionContext()) {
         throw new LuaError("[MLua] " + operation + " must run on Minecraft's client thread.");
      }
      if (minecraft.player != player || minecraft.gameMode == null || minecraft.level == null) {
         throw new LuaError("[MLua] " + operation + " requires the active local player in a loaded world.");
      }
   }

}
