package dev.mlua.modules.players.Service;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.api.MLuaDescription;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.core.signal.MLuaSignal;
import java.util.UUID;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

@MLuaDescription("Manages player connections and provides player instances.")
public final class MLuaPlayersService extends MLuaReflectiveService {
   public static final MLuaSignal PLAYER_ADDED = new MLuaSignal();
   public static final MLuaSignal PLAYER_REMOVING = new MLuaSignal();

   public static void DestroyAll() {
      PLAYER_ADDED.Destroy();
      PLAYER_REMOVING.Destroy();
   }

   public static void DestroySide(Side side) {
      PLAYER_ADDED.destroySide(side);
      PLAYER_REMOVING.destroySide(side);
   }

   public static void DestroyAll(String ownerModId) {
      PLAYER_ADDED.destroyOwned(ownerModId);
      PLAYER_REMOVING.destroyOwned(ownerModId);
   }
   public MLuaPlayersService(MLuaService.Context context) {
      super(context);
   }

   @Override
   public String name() {
      return "Players";
   }

   @Override
   public LuaTable toLua() {
      LuaTable table = super.toLua();
      String ownerModId = this.context.mlua().ownerModId();
      table.set("__side", "Both");
      table.set("PlayerAdded", MLuaLuaBindings.signalInstance(PLAYER_ADDED, this.context.mlua().globals(), this.context.mlua().scheduler(), ownerModId, this.context.mlua().side()));
      table.set("PlayerRemoving", MLuaLuaBindings.signalInstance(PLAYER_REMOVING, this.context.mlua().globals(), this.context.mlua().scheduler(), ownerModId, this.context.mlua().side()));
      return table;
   }

   @MLuaDescription("Returns the local player on the client, or nil on the server.")
   public LuaValue GetLocalPlayer() {
      return this.localPlayer();
   }

   @MLuaDescription("Returns the live player instances currently connected.")
   public LuaTable GetPlayers() {
      LuaTable players = new LuaTable();
      int index = 1;
      if (this.isClientSide()) {
         for (Object player : this.clientPlayers()) {
            players.set(index++, this.clientPlayerTable(player));
         }
      } else if (this.server() != null) {
         for (ServerPlayer player : this.server().getPlayerList().getPlayers()) {
            players.set(index++, playerTable(player, this.context.mlua().registration()));
         }
      }
      return players;
   }

   @MLuaDescription("Finds a connected player by exact name, or returns nil.")
   public LuaValue GetPlayerByName(String name) {
      if (!this.isClientSide() && this.server() != null) {
         ServerPlayer player = this.server().getPlayerList().getPlayerByName(name);
         return player == null ? LuaValue.NIL : playerTable(player, this.context.mlua().registration());
      }
      if (this.isClientSide()) {
         for (Object player : this.clientPlayers()) {
            if (this.clientPlayerName(player).equals(name)) {
               return this.clientPlayerTable(player);
            }
         }
      }
      return LuaValue.NIL;
   }

   @MLuaDescription("Finds a connected player by UUID, or returns nil.")
   public LuaValue GetPlayerByUUID(String value) {
      UUID uuid = UUID.fromString(value);
      ServerPlayer player = this.isClientSide() || this.server() == null
         ? null : this.server().getPlayerList().getPlayer(uuid);
      if (player != null) return playerTable(player, this.context.mlua().registration());
      if (this.isClientSide()) {
         for (Object local : this.clientPlayers()) {
            if (uuid.equals(this.invokeClient(local, "getUUID"))) {
               return this.clientPlayerTable(local);
            }
         }
      }
      return LuaValue.NIL;
   }

   @MLuaDescription("Finds a connected player by numeric entity id, or returns nil.")
   public LuaValue GetPlayerById(int id) {
      if (!this.isClientSide() && this.server() != null) {
         ServerPlayer player = this.server().getPlayerList().getPlayers().stream()
            .filter(value -> value.getId() == id).findFirst().orElse(null);
         if (player != null) return playerTable(player, this.context.mlua().registration());
      }
      if (this.isClientSide()) {
         for (Object local : this.clientPlayers()) {
            if (((Number)this.invokeClient(local, "getId")).intValue() == id) {
               return this.clientPlayerTable(local);
            }
         }
      }
      return LuaValue.NIL;
   }

   @MLuaDescription("Returns the number of connected players.")
   public int GetPlayerCount() {
      return this.isClientSide() ? this.clientPlayers().size()
         : this.server() == null ? 0 : this.server().getPlayerCount();
   }

   @MLuaDescription("Returns the server's maximum player count; returns zero on the client.")
   public int GetMaxPlayers() {
      return this.isClientSide() || this.server() == null ? 0 : this.server().getMaxPlayers();
   }

   @MLuaDescription("Sends a system chat message to every player; server-side only.")
   public LuaValue Broadcast(String message) {
      if (!this.isClientSide() && this.server() != null) {
         this.server().getPlayerList().broadcastSystemMessage(Component.literal(message), false);
      }
      return LuaValue.NONE;
   }

   public static LuaTable playerTable(ServerPlayer player) {
      return playerTable(player, MLuaRuntime.registrationForLevel(player.level()));
   }

   public static LuaTable playerTable(ServerPlayer player, dev.mlua.module.MLuaRegistration registration) {
      return MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration);
   }

   private java.util.List<?> clientPlayers() {
      if (!this.isClientSide()) return java.util.List.of();
      try {
         Object minecraft = this.clientMinecraft();
         Object level = minecraft.getClass().getField("level").get(minecraft);
         if (level == null) return java.util.List.of();
         Object players = level.getClass().getMethod("players").invoke(level);
         return players instanceof java.util.List<?> values ? values : java.util.List.of();
      } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
         throw new IllegalStateException("Unable to read players from the active client world", error);
      }
   }

   private LuaValue localPlayer() {
      if (this.context.mlua().side() != Side.CLIENT) {
         return LuaValue.NIL;
      } else {
         try {
            Object minecraft = this.clientMinecraft();
            Object player = minecraft.getClass().getField("player").get(minecraft);
            return player == null ? LuaValue.NIL : this.clientPlayerTable(player);
         } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            throw new IllegalStateException("Unable to read the local client player", error);
         }
      }
   }

   private Object clientMinecraft() throws ReflectiveOperationException {
      Class<?> minecraftType = Class.forName("net.minecraft.client.Minecraft");
      return minecraftType.getMethod("getInstance").invoke(null);
   }

   private String clientPlayerName(Object player) {
      Object component = this.invokeClient(player, "getName");
      return String.valueOf(this.invokeClient(component, "getString"));
   }

   private Object invokeClient(Object target, String methodName) {
      try {
         return target.getClass().getMethod(methodName).invoke(target);
      } catch (InvocationTargetException error) {
         Throwable cause = error.getCause() == null ? error : error.getCause();
         if (cause instanceof RuntimeException runtime) throw runtime;
         throw new IllegalStateException("Client player method '" + methodName + "' failed", cause);
      } catch (ReflectiveOperationException | RuntimeException error) {
         throw new IllegalStateException("Unable to call client player method '" + methodName + "'", error);
      }
   }

   private LuaTable clientPlayerTable(Object player) {
      try {
         for (Method method : MLuaPlayerWrapper.class.getMethods()) {
            if (!method.getName().equals("clientPlayerTable") || method.getParameterCount() != 2
               || !method.getParameterTypes()[0].isInstance(player)
               || !method.getParameterTypes()[1].isInstance(this.context.mlua().registration())) continue;
            return (LuaTable)method.invoke(null, player, this.context.mlua().registration());
         }
      } catch (InvocationTargetException error) {
         Throwable cause = error.getCause() == null ? error : error.getCause();
         if (cause instanceof RuntimeException runtime) throw runtime;
         throw new IllegalStateException("Unable to create a Lua client player instance", cause);
      } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
         throw new IllegalStateException("Unable to create a Lua client player instance", error);
      }
      throw new IllegalStateException("No client player wrapper accepts " + player.getClass().getName());
   }

   private boolean isClientSide() {
      return this.context.mlua().side() == Side.CLIENT;
   }

   private net.minecraft.server.MinecraftServer server() {
      return this.context.mlua().server();
   }

}
