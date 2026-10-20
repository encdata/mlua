package dev.mlua.modules.players.classes;

import com.mojang.authlib.GameProfile;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.math.MLuaVector;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaMinecraftEnums;
import dev.mlua.modules.effects.service.MLuaPotionEffectsService;
import dev.mlua.runtime.data.MLuaDataManager;
import dev.mlua.mcapi.core.MCComponent;
import dev.mlua.mcapi.core.MCMutableComponent;
import dev.mlua.mcapi.core.MCResourceLocation;
import dev.mlua.mcapi.core.MCVec3;
import dev.mlua.mcapi.entity.MCPlayer;
import dev.mlua.mcapi.entity.MCLocalPlayer;
import dev.mlua.mcapi.entity.MCServerPlayer;
import dev.mlua.mcapi.item.MCItemStack;
import dev.mlua.mcapi.sound.MCSoundEvent;
import dev.mlua.mcapi.sound.MCSoundSource;
import dev.mlua.mcapi.world.MCGameType;
import dev.mlua.mc.impl.entity.MCEntityImpl;
import dev.mlua.mc.impl.entity.MCPlayerImpl;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.mc.impl.item.MCItemStackImpl;
import dev.mlua.mc.impl.world.MCLevelImpl;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import net.minecraft.world.level.Level;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.ResourceLocation;

public final class MLuaPlayerWrapper {
   private static final Method PLAYER_GET_GAME_PROFILE = findPlayerGetGameProfile();

   private MLuaPlayerWrapper() {
   }

   public static LuaTable classTable() {
      return classTable(MLuaRuntime.get());
   }

   public static LuaTable classTable(MLuaRegistration registration) {
      if (registration == null) throw new IllegalStateException("Runtime registration is required for Player instances");
      LuaTable classTable = registration.classTable("Player");
      if (classTable == null) throw new IllegalStateException("Lua class is not registered: Player");
      return classTable;
   }

   public static LuaTable playerEntityTable(Player player, MLuaRegistration registration) {
      if (player instanceof ServerPlayer serverPlayer) {
         return playerTable(MCServerPlayerImpl.fromReal(serverPlayer), registration);
      }
      if (player.level().isClientSide) return ClientPlayerDelegate.create(player, registration);
      throw new LuaError("[MLua] Unsupported server player implementation: " + player.getClass().getName());
   }

   /** Compatibility bridge for callers that already hold the MLua local-player adapter. */
   public static LuaTable localPlayerTable(MCLocalPlayer player) {
      Entity raw = ((MCEntityImpl) player).inner();
      return localPlayerTable(player, MLuaRuntime.registrationForLevel(raw.level()));
   }

   /** Compatibility bridge that keeps client-only Minecraft classes out of this shared facade. */
   public static LuaTable localPlayerTable(MCLocalPlayer player, MLuaRegistration registration) {
      return ClientPlayerDelegate.createLocal(player, registration);
   }

   public static LuaTable playerTable(MCServerPlayer player) {
      Entity raw = ((MCEntityImpl) player).inner();
      return playerTable(player, MLuaRuntime.registrationForLevel(raw.level()));
   }

   public static LuaTable playerTable(MCServerPlayer player, MLuaRegistration registration) {
      classTable(registration);
      ServerPlayer raw = (ServerPlayer) ((MCEntityImpl) player).inner();
      LuaTable cachedPlayer = MLuaEntityWrapper.cachedPlayerTable(raw, registration);
      if (cachedPlayer != null) {
         LuaValue cachedEntity = cachedPlayer.rawget("__entity");
         if (cachedEntity.isuserdata(Entity.class)
               && cachedEntity.touserdata(Entity.class) == raw) {
            return cachedPlayer;
         }
      }
      LuaTable table = MLuaEntityWrapper.livingEntityTableBase((LivingEntity) raw, registration);
      registration.attachClassInstance(table, "Player", "LivingEntity");
      table.rawset("__entity", LuaValue.userdataOf(raw));
      MLuaClassMethods.setInstanceMethod(table, "GetDisplayName", luaFn(() -> {
         return LuaValue.valueOf(!MLuaEntityWrapper.entityDisplayName(player).isBlank() ? MLuaEntityWrapper.entityDisplayName(player) : playerName(player));
      }), "Returns this player's display name.");
      MLuaClassMethods.setInstanceMethod(table, "SendMessage", varArg((args) -> {
         String rawMsg = args.checkjstring(2);
         Component component;
         try {
            component = Component.literal(MCComponent.fromJson(rawMsg).getString());
         } catch (Exception e) {
            component = Component.literal(rawMsg);
         }
         raw.sendSystemMessage(component);
         return LuaValue.NONE;
      }), "Sends a chat message to this player. On the client, displays the message locally.");
      MLuaClassMethods.setInstanceMethod(table, "SendActionBar", varArg((args) -> {
         MCMutableComponent msg;
         try {
            msg = MCComponent.fromJson(args.checkjstring(2));
         } catch (Exception var4) {
            msg = MCComponent.literal(args.checkjstring(2));
         }
         player.displayClientMessage(msg, true);
         return LuaValue.NONE;
      }), "Sends an action bar message to this player.");
      MLuaClassMethods.setInstanceMethod(table, "SendTitle", varArg((args) -> {
         if (player.getConnection() != null) {
            String rawText = args.checkjstring(2);
            Component component;
            try {
               component = Component.literal(MCComponent.fromJson(rawText).getString());
            } catch (Exception e) {
               component = Component.literal(rawText);
            }
            raw.connection.send(new ClientboundSetTitleTextPacket(component));
         }

         return LuaValue.NONE;
      }), "Displays a title to this player.");
      MLuaPlayerFoodBindings.bind(table, player);
      MLuaClassMethods.setInstanceMethod(table, "GetExperienceLevel", luaFn(() -> {
         return LuaValue.valueOf(player.getExperienceLevel());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetExperienceProgress", luaFn(() -> {
         return LuaValue.valueOf((double)player.getExperienceProgress());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GiveExperience", varArg((args) -> {
         player.giveExperiencePoints(MLuaLuaArguments.wholeInt(args, 2, "Player:GiveExperience", "points"));
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GiveExperienceLevels", varArg((args) -> {
         player.giveExperienceLevels(MLuaLuaArguments.wholeInt(args, 2, "Player:GiveExperienceLevels", "levels"));
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "IsOnline", luaFn(() -> {
         return LuaValue.valueOf(player.getConnection() != null);
      }));
      MLuaClassMethods.setInstanceMethod(table, "IsCreative", luaFn(() -> {
         return LuaValue.valueOf(player.isCreative());
      }));
      MLuaClassMethods.setInstanceMethod(table, "IsSpectator", luaFn(() -> {
         return LuaValue.valueOf(player.isSpectator());
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
      MLuaClassMethods.setInstanceMethod(table, "GetBlockPosition", luaFn(() -> {
         return positionTable((double)player.getBlockPosition().getX(), (double)player.getBlockPosition().getY(), (double)player.getBlockPosition().getZ());
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetEyeCFrame", luaFn(() -> {
         MCVec3 look = MLuaEntityWrapper.lookAngle(player);
         return MLuaCFrame.fromEntity(player.getX(), raw.getEyeY(), player.getZ(), look.x(), look.y(), look.z()).toLua();
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetCameraCFrame", varArg((args) -> {
         throw new LuaError("[MLua] player:GetCameraCFrame() is client-side only. Use Minecraft:GetService(\"Camera\") in a .client.lua script.");
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetGameMode", luaFn(() -> {
         return getGameModeLua(player);
      }), "Returns this player's current game mode.");
      MLuaClassMethods.setInstanceMethod(table, "GetServerGameMode", luaFn(() -> {
         return getGameModeLua(player);
      }), "Returns this player's authoritative server game mode.");
      MLuaClassMethods.setInstanceMethod(table, "SetGameMode", varArg((args) -> {
         MCGameType type = MCGameType.byName(MLuaMinecraftEnums.resolveGameMode(args.arg(2)).getName());
         player.setGameMode(type);
         return LuaValue.NONE;
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
      MLuaClassMethods.setInstanceMethod(table, "HasPermission", varArg((args) -> {
         int level = MLuaLuaArguments.wholeInt(args, 2, "Player:HasPermission", "level");
         return LuaValue.valueOf(player.hasPermissions(level));
      }));
      MLuaClassMethods.setInstanceMethod(table, "IsOperator", luaFn(() -> {
         return LuaValue.valueOf(player.hasPermissions(4));
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetSelectedSlot", luaFn(() -> {
         return LuaValue.valueOf(player.getInventory().getSelected() + 1);
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetSelectedSlot", varArg((args) -> {
         int slot = MLuaLuaArguments.wholeInt(args, 2, "Player:SetSelectedSlot", "slot") - 1;
         if (slot < 0 || slot >= 9) {
            throw new LuaError("[MLua] Selected slot must be between 1 and 9.");
         }
         player.getInventory().setSelected(slot);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetHeldItem", varArg((args) -> {
         return heldItem(player.getMainHandItem(), registration);
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetOffhandItem", varArg((args) -> {
         return heldItem(player.getOffhandItem(), registration);
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetViewMode", varArg((args) -> {
         throw new LuaError("[MLua] Player.ViewMode is client-side only for server player tables.");
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetViewMode", varArg((args) -> {
         throw new LuaError("[MLua] Player.ViewMode can only be written from a .client.lua script.");
      }));
      MLuaClassMethods.setInstanceMethod(table, "GiveItem", varArg((args) -> {
         ItemStack stack = MLuaItemWrapper.toItemStack(args.arg(2), registration);
         if (stack.isEmpty()) return LuaValue.FALSE;
         return LuaValue.valueOf(player.addItem(MCItemStackImpl.fromReal(stack)));
      }));
      LuaTable inventory = MLuaPlayerInventoryBindings.attach(table, player, true, true, registration);
      MLuaClassMethods.setInstanceMethod(table, "SetSlot", varArg((args) -> {
         return MLuaClassMethods.invokeInstanceMethod(inventory, "SetSlot", args.arg(2), args.arg(3));
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetHotbarSlot", varArg((args) -> {
         return MLuaClassMethods.invokeInstanceMethod(inventory, "SetHotbarSlot", args.arg(2), args.arg(3));
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetArmorSlot", varArg((args) -> {
         return MLuaClassMethods.invokeInstanceMethod(inventory, "SetArmorSlot", args.arg(2), args.arg(3));
      }));
      MLuaClassMethods.setInstanceMethod(table, "SetOffhandSlot", varArg((args) -> {
         return MLuaClassMethods.invokeInstanceMethod(inventory, "SetOffhandSlot", args.arg(2));
      }));
      bindLevelMethods(table, ((MCLevelImpl) player.getLevel()).inner(), registration);
      attachNearbyPlayers(table, raw, registration);
      MLuaClassMethods.setInstanceMethod(table, "GiveEffect", varArg((args) -> {
         return MLuaPotionEffectsService.giveEffect((LivingEntity)((MCEntityImpl) player).inner(), args);
      }));
      MLuaClassMethods.setInstanceMethod(table, "RemoveEffect", varArg((args) -> {
         LivingEntity rawEntity = (LivingEntity)((MCEntityImpl) player).inner();
         //--version 1.21.1,26.1
         return LuaValue.valueOf(rawEntity.removeEffect(MLuaPotionEffectsService.resolveHolderFromLua(args.arg(2))));
         //--endversion
         //--version 1.20.1
         return LuaValue.valueOf(rawEntity.removeEffect(MLuaPotionEffectsService.resolveFromLua(args.arg(2))));
         //--endversion
      }));
      MLuaClassMethods.setInstanceMethod(table, "HasEffect", varArg((args) -> {
         LivingEntity rawEntity = (LivingEntity)((MCEntityImpl) player).inner();
         //--version 1.21.1,26.1
         return LuaValue.valueOf(rawEntity.hasEffect(MLuaPotionEffectsService.resolveHolderFromLua(args.arg(2))));
         //--endversion
         //--version 1.20.1
         return LuaValue.valueOf(rawEntity.hasEffect(MLuaPotionEffectsService.resolveFromLua(args.arg(2))));
         //--endversion
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetEffects", luaFn(() -> {
         return MLuaPotionEffectsService.effectsTable((LivingEntity)((MCEntityImpl) player).inner(), registration);
      }));
      MLuaClassMethods.setInstanceMethod(table, "ClearEffects", luaFn(() -> {
         LivingEntity rawEntity = (LivingEntity)((MCEntityImpl) player).inner();
         rawEntity.removeAllEffects();
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "PlaySound", varArg((args) -> {
         MCResourceLocation loc = MCResourceLocation.parse(args.checkjstring(2));
         ResourceLocation rawLoc = MLuaResourceLocations.parse(loc.toString());
         //--version 26.1
         SoundEvent rawSound = BuiltInRegistries.SOUND_EVENT.get(rawLoc).map(reference -> reference.value()).orElse(null);
         //--endversion
         //--version 1.20.1,1.21.1
         SoundEvent rawSound = BuiltInRegistries.SOUND_EVENT.get(rawLoc);
         //--endversion
         if (rawSound == null) {
            throw new LuaError("[MLua] Unknown sound: " + args.checkjstring(2));
         }
         float volume = MLuaLuaArguments.optionalNonNegativeFiniteFloat(args, 3, 1.0f, "Player:PlaySound", "volume");
         float pitch = MLuaLuaArguments.optionalNonNegativeFiniteFloat(args, 4, 1.0f, "Player:PlaySound", "pitch");
         player.playNotifySound(MCSoundEvent.create(loc), MCSoundSource.PLAYERS, volume, pitch);
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetPersistentData", luaFn(() -> MLuaDataManager.playerPersistentData(raw, true)));
      MLuaClassMethods.setInstanceMethod(table, "GetSyncedData", luaFn(() -> MLuaDataManager.playerSyncedData(raw)));
      MLuaClassMethods.setInstanceMethod(table, "SetData", varArg((args) -> {
         LuaTable persistent = MLuaDataManager.playerPersistentData(raw, true);
         persistent.get("Set").call(persistent, LuaValue.valueOf(args.checkjstring(2)), args.arg(3));
         return LuaValue.NONE;
      }));
      MLuaClassMethods.setInstanceMethod(table, "GetData", varArg((args) -> {
         LuaTable persistent = MLuaDataManager.playerPersistentData(raw, true);
         return persistent.get("Get").call(persistent, LuaValue.valueOf(args.checkjstring(2)));
      }));
      MLuaClassMethods.setInstanceMethod(table, "RemoveData", varArg((args) -> {
         LuaTable persistent = MLuaDataManager.playerPersistentData(raw, true);
         persistent.get("Remove").call(persistent, LuaValue.valueOf(args.checkjstring(2)));
         return LuaValue.NONE;
      }));
      LuaTable attached = MLuaEntityWrapper.attachEntityProperties(table);
      MLuaEntityWrapper.cachePlayerTable(raw, registration, attached);
      return attached;
   }

   public static LuaTable positionTable(double x, double y, double z) {
      return MLuaVector.vector3(x, y, z);
   }

    public static double[] positionArgs(Varargs args, int index) {
      return MLuaLuaArguments.finiteVector3(args.arg(index), "Entity position");
   }

   private static GameProfile gameProfile(MCPlayer player) {
      if (PLAYER_GET_GAME_PROFILE != null) {
         try {
            return (GameProfile)PLAYER_GET_GAME_PROFILE.invoke(((MCPlayerImpl) player).inner());
         } catch (Exception var2) {
         }
      }

      return new GameProfile(player.getUUID(), "");
   }

   static String playerName(MCPlayer player) {
      GameProfile profile = gameProfile(player);
      String name = profile.getName();
      return name != null ? name : "";
   }

   private static Method findPlayerGetGameProfile() {
      Iterator var0 = List.of("getGameProfile", "getGameProfile").iterator();

      while(var0.hasNext()) {
         String name = (String)var0.next();

         try {
            Method method = Player.class.getMethod(name);
            if (method.getReturnType() == GameProfile.class) {
               method.setAccessible(true);
               return method;
            }
         } catch (NoSuchMethodException var4) {
         }
      }

      Method[] var5 = Player.class.getMethods();
      int var6 = var5.length;

      for(int var7 = 0; var7 < var6; ++var7) {
         Method method = var5[var7];
         if (method.getParameterCount() == 0 && method.getReturnType() == GameProfile.class) {
            method.setAccessible(true);
            return method;
         }
      }

      return null;
   }

   private static LuaValue getGameModeLua(MCServerPlayer player) {
      ServerPlayer raw = (ServerPlayer) ((MCEntityImpl) player).inner();
      return MLuaMinecraftEnums.gameModeOf(raw.gameMode.getGameModeForPlayer());
   }

   static LuaValue unavailableRemotePlayerState(String state) {
      throw new LuaError("[MLua] " + state + " cannot be read or changed for a remote client player.");
   }

   static LuaValue unavailableLocalPlayerControl(String method) {
      throw new LuaError("[MLua] Player:" + method
         + " is only available for the local player in a .client.lua script.");
   }

   static LuaValue heldItem(MCItemStack stack, MLuaRegistration registration) {
      return (LuaValue)(stack.isEmpty() ? LuaValue.NIL : MLuaItemWrapper.itemTable(((MCItemStackImpl)stack).inner(), registration));
   }

   static void bindLevelMethods(LuaTable table, Level level, MLuaRegistration registration) {
      MLuaEntityWrapper.bindLevelMethods(table, level, registration);
   }

   static void attachNearbyPlayers(LuaTable table, Player player, MLuaRegistration registration) {
      MLuaPlayerRelationshipBindings.bindNearbyPlayers(table, player, registration);
   }

   static LuaValue luaFn(final Supplier<LuaValue> fn) {
      return MLuaClassMethods.zeroArg(fn);
   }

   static LuaValue varArg(final Function<Varargs, LuaValue> fn) {
      return MLuaClassMethods.varArg(fn);
   }

   /** Loads the client implementation only when a client-side Player is actually wrapped. */
   private static final class ClientPlayerDelegate {
      private static final Method FACTORY = findFactory();
      private static final Method LOCAL_FACTORY = findLocalFactory();

      private static Method findFactory() {
         try {
            Class<?> clientWrapper = Class.forName(
               "dev.mlua.modules.players.classes.MLuaClientPlayerWrapper", true,
               MLuaPlayerWrapper.class.getClassLoader());
            return clientWrapper.getMethod("playerEntityTable", Player.class, MLuaRegistration.class);
         } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("MLua client Player wrapper is unavailable", error);
         }
      }

      private static Method findLocalFactory() {
         try {
            Class<?> clientWrapper = Class.forName(
               "dev.mlua.modules.players.classes.MLuaClientPlayerWrapper", true,
               MLuaPlayerWrapper.class.getClassLoader());
            return clientWrapper.getMethod("localPlayerTable", MCLocalPlayer.class, MLuaRegistration.class);
         } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("MLua local Player wrapper is unavailable", error);
         }
      }

      private static LuaTable create(Player player, MLuaRegistration registration) {
         return invoke(FACTORY, player, registration, "client Player");
      }

      private static LuaTable createLocal(MCLocalPlayer player, MLuaRegistration registration) {
         return invoke(LOCAL_FACTORY, player, registration, "local Player");
      }

      private static LuaTable invoke(Method factory, Object player, MLuaRegistration registration, String label) {
         try {
            return (LuaTable) factory.invoke(null, player, registration);
         } catch (ReflectiveOperationException error) {
            Throwable cause = error instanceof java.lang.reflect.InvocationTargetException invocation
               && invocation.getCause() != null ? invocation.getCause() : error;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new LuaError("[MLua] Could not create a " + label + " instance: " + cause.getMessage());
         }
      }
   }
}
