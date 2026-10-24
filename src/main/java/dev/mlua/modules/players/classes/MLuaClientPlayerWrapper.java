package dev.mlua.modules.players.classes;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.modules.effects.service.MLuaPotionEffectsService;
import dev.mlua.mcapi.entity.MCLocalPlayer;
import dev.mlua.mc.impl.entity.MCLocalPlayerImpl;
import dev.mlua.mc.impl.entity.MCPlayerImpl;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import static dev.mlua.modules.players.classes.MLuaPlayerWrapper.*;

/** Owns client-only Player adaptation and local control bindings. */
public final class MLuaClientPlayerWrapper {
   private MLuaClientPlayerWrapper() {}

   public static LuaTable playerEntityTable(Player player, MLuaRegistration registration) {
      if (player instanceof LocalPlayer local) {
         return localPlayerTable(MCLocalPlayerImpl.fromReal(local), registration);
      }
      if (player instanceof AbstractClientPlayer clientPlayer) {
         return clientPlayerTable(clientPlayer, registration);
      }
      return clientPlayerTable(player, registration);
   }

   public static LuaTable clientPlayerTable(AbstractClientPlayer player, MLuaRegistration registration) {
      return clientPlayerTable((Player) player, registration);
   }

   private static LuaTable clientPlayerTable(Player player,
                                             MLuaRegistration registration) {
      classTable(registration);
      if (player instanceof LocalPlayer local) {
         return localPlayerTable(MCLocalPlayerImpl.fromReal(local), registration);
      }
      LuaTable cachedPlayer = MLuaEntityWrapper.cachedPlayerTable(player, registration);
      if (cachedPlayer != null) {
         LuaValue cachedEntity = cachedPlayer.rawget("__entity");
         if (cachedEntity.isuserdata(Entity.class)
               && cachedEntity.touserdata(Entity.class) == player) {
            return cachedPlayer;
         }
      }
      LuaTable table = MLuaEntityWrapper.livingEntityTableBase(player, registration);
      registration.attachClassInstance(table, "Player", "LivingEntity");
      table.rawset("__entity", LuaValue.userdataOf(player));
      MLuaClassMethods.setInstanceMethod(table, "GetIsLocalPlayer", luaFn(() -> LuaValue.FALSE));
      MLuaClassMethods.setInstanceMethod(table, "GetDisplayName", luaFn(() -> LuaValue.valueOf(player.getDisplayName().getString())));
      MLuaClassMethods.setInstanceMethod(table, "IsOnline", luaFn(() -> LuaValue.TRUE));
      MLuaClassMethods.setInstanceMethod(table, "IsCreative", luaFn(() -> LuaValue.valueOf(player.isCreative())));
      MLuaClassMethods.setInstanceMethod(table, "IsSpectator", luaFn(() -> LuaValue.valueOf(player.isSpectator())));
      MLuaClassMethods.setInstanceMethod(table, "GetGameMode", luaFn(() -> MLuaLocalPlayerBindings.clientGameMode(player)),
         "Returns the game mode reported by the server for this remote player.");
      MLuaClassMethods.setInstanceMethod(table, "GetServerGameMode", luaFn(() -> MLuaLocalPlayerBindings.clientGameMode(player)),
         "Returns the game mode reported by the server, independently of the local client's selected mode.");
      MLuaClassMethods.setInstanceMethod(table, "SetGameMode", varArg(args -> {
         throw new LuaError("[MLua] Player:SetGameMode is server-side only. Client scripts cannot change a player's server game mode.");
      }), "Changes this player's game mode. This method is server-side only.");
      MLuaClassMethods.setInstanceMethod(table, "SetLocalGameMode",
         varArg(args -> unavailableLocalPlayerControl("SetLocalGameMode")));
      MLuaClassMethods.setInstanceMethod(table, "ResetLocalGameMode",
         varArg(args -> unavailableLocalPlayerControl("ResetLocalGameMode")));
      MLuaClassMethods.setInstanceMethod(table, "CanFly",
         luaFn(() -> unavailableLocalPlayerControl("CanFly")));
      MLuaClassMethods.setInstanceMethod(table, "SetCanFly",
         varArg(args -> unavailableLocalPlayerControl("SetCanFly")));
      MLuaClassMethods.setInstanceMethod(table, "IsFlying",
         luaFn(() -> unavailableLocalPlayerControl("IsFlying")));
      MLuaClassMethods.setInstanceMethod(table, "SetFlying",
         varArg(args -> unavailableLocalPlayerControl("SetFlying")));
      LuaValue unavailableFood = luaFn(() -> LuaValue.NIL);
      LuaValue unavailableFoodMutation = varArg(args -> unavailableRemotePlayerState("food"));
      MLuaClassMethods.setInstanceMethod(table, "GetFood", unavailableFood);
      MLuaClassMethods.setInstanceMethod(table, "GetFoodLevel", unavailableFood);
      MLuaClassMethods.setInstanceMethod(table, "SetFood", unavailableFoodMutation);
      MLuaClassMethods.setInstanceMethod(table, "SetFoodLevel", unavailableFoodMutation);
      MLuaClassMethods.setInstanceMethod(table, "GetSaturation", luaFn(() -> LuaValue.NIL));
      MLuaClassMethods.setInstanceMethod(table, "SetSaturation", varArg(args -> unavailableRemotePlayerState("food")));
      MLuaClassMethods.setInstanceMethod(table, "CauseFoodExhaustion", varArg(args -> unavailableRemotePlayerState("food")));
      MLuaClassMethods.setInstanceMethod(table, "GetExperienceLevel", luaFn(() -> LuaValue.NIL));
      MLuaClassMethods.setInstanceMethod(table, "GetExperienceProgress", luaFn(() -> LuaValue.NIL));
      MLuaClassMethods.setInstanceMethod(table, "GetSelectedSlot", luaFn(() -> LuaValue.NIL));
      MLuaClassMethods.setInstanceMethod(table, "SetSelectedSlot", varArg(args -> unavailableRemotePlayerState("inventory selection")));
      MLuaClassMethods.setInstanceMethod(table, "GetViewMode", luaFn(() -> LuaValue.NIL));
      MLuaClassMethods.setInstanceMethod(table, "SetViewMode", varArg(args -> {
         throw new LuaError("[MLua] ViewMode is only available on the local player.");
      }));
      bindLevelMethods(table, player.level(), registration);
      MLuaClassMethods.setInstanceMethod(table, "IsSleeping", luaFn(() -> LuaValue.valueOf(player.isSleeping())));
      MLuaClassMethods.setInstanceMethod(table, "IsCrouching", luaFn(() -> LuaValue.valueOf(player.isCrouching())));
      MLuaClassMethods.setInstanceMethod(table, "IsSprinting", luaFn(() -> LuaValue.valueOf(player.isSprinting())));
      MLuaClassMethods.setInstanceMethod(table, "GetEffects", luaFn(() ->
         MLuaPotionEffectsService.effectsTable(player, registration)));
      MLuaClassMethods.setInstanceMethod(table, "GetBlockPosition", luaFn(() ->
         positionTable(player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ())));
      MLuaClassMethods.setInstanceMethod(table, "GetHeldItem", luaFn(() -> {
         var stack = player.getMainHandItem();
         return stack.isEmpty() ? LuaValue.NIL : MLuaItemWrapper.itemTable(stack, registration);
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetOffhandItem", luaFn(() -> {
         var stack = player.getOffhandItem();
         return stack.isEmpty() ? LuaValue.NIL : MLuaItemWrapper.itemTable(stack, registration);
      }));
      MLuaPlayerInventoryBindings.attach(table, MCPlayerImpl.fromReal(player), false, false, registration);
      MLuaClassMethods.setInstanceMethod(table, "GetOpenContainer", luaFn(() -> LuaValue.NIL));
      MLuaClassMethods.setInstanceMethod(table, "GetOpenContainerTitle", luaFn(() -> LuaValue.NIL));
      MLuaClassMethods.setInstanceMethod(table, "GetOpenContainerSize", luaFn(() -> LuaValue.valueOf(0)));
      attachNearbyPlayers(table, player, registration);
      MLuaEntityWrapper.attachEntityProperties(table);
      MLuaEntityWrapper.cachePlayerTable(player, registration, table);
      return table;
   }

   public static LuaTable localPlayerTable(MCLocalPlayer player) {
      return MLuaLocalPlayerBindings.localPlayerTable(player);
   }

   public static LuaTable localPlayerTable(MCLocalPlayer player, MLuaRegistration registration) {
      return MLuaLocalPlayerBindings.localPlayerTable(player, registration);
   }
}