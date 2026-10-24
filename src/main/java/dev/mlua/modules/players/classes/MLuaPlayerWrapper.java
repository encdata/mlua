package dev.mlua.modules.players.classes;

import com.mojang.authlib.GameProfile;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.math.MLuaVector;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.mcapi.entity.MCPlayer;
import dev.mlua.mcapi.entity.MCLocalPlayer;
import dev.mlua.mcapi.entity.MCServerPlayer;
import dev.mlua.mcapi.item.MCItemStack;
import dev.mlua.mc.impl.entity.MCEntityImpl;
import dev.mlua.mc.impl.entity.MCPlayerImpl;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.mc.impl.item.MCItemStackImpl;
import dev.mlua.mc.impl.world.MCLevelImpl;
import java.lang.reflect.Method;
import net.minecraft.world.level.Level;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

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
      MLuaPlayerServerBindings.bind(table, player, raw, registration);
      MLuaPlayerFoodBindings.bind(table, player);
      bindLevelMethods(table, ((MCLevelImpl) player.getLevel()).inner(), registration);
      attachNearbyPlayers(table, raw, registration);
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
      try {
         Method method = Player.class.getMethod("getGameProfile");
         if (method.getReturnType() == GameProfile.class) {
            method.setAccessible(true);
            return method;
         }
      } catch (NoSuchMethodException ignored) {
      }

      for (Method method : Player.class.getMethods()) {
         if (method.getParameterCount() == 0 && method.getReturnType() == GameProfile.class) {
            method.setAccessible(true);
            return method;
         }
      }

      return null;
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
