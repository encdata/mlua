package dev.mlua.minecraft.network;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.common.enums.Side;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.modules.players.service.MLuaPlayersService;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

public final class MLuaRemoteEvent {
   private final String name;
   private final MLuaSignal serverEvent = new MLuaSignal();
   private final MLuaSignal clientEvent = new MLuaSignal();

   MLuaRemoteEvent(String name) {
      this.name = name;
   }

   public String name() {
      return this.name;
   }

   public MLuaSignal serverEvent() {
      return this.serverEvent;
   }

   public MLuaSignal clientEvent() {
      return this.clientEvent;
   }

   void receiveFromClient(ServerPlayer player, List<MLuaNetworkValue> arguments) {
      try {
         LuaValue[] payload = MLuaNetworkSerializer.toLuaArray(arguments);
         LuaValue[] values = new LuaValue[payload.length + 1];
         MLuaRuntimeSession session = MLuaRuntime.sessionForLevel(player.level());
         var registration = MLuaRuntime.registrationForLevel(player.level());
         values[0] = MLuaPlayersService.playerTable(player, registration);
         System.arraycopy(payload, 0, values, 1, payload.length);
         this.serverEvent.fireOnSession(session, Side.SERVER, LuaValue.varargsOf(values));
      } catch (RuntimeException var5) {
         MLuaLogger.LOGGER.error("MLua Network OnServerEvent handler failed for remote '{}'", this.name, var5);
      }

   }

   void receiveFromServer(List<MLuaNetworkValue> arguments) {
      try {
         this.clientEvent.fireOnSession(MLuaRuntime.get().session(), Side.CLIENT,
            LuaValue.varargsOf(MLuaNetworkSerializer.toLuaArray(arguments)));
      } catch (RuntimeException var3) {
         MLuaLogger.LOGGER.error("MLua Network OnClientEvent handler failed for remote '{}'", this.name, var3);
      }

   }

   public boolean fireServer(Varargs args) {
      return this.fireServer(args, false);
   }

   public boolean fireServer(Varargs args, boolean optional) {
      return MLuaNetworkManager.get().sendToServer(this.name, MLuaNetworkSerializer.fromLuaArgs(args, 2), optional);
   }

   public boolean fireClient(ServerPlayer player, Varargs args) {
      return this.fireClient(player, args, false);
   }

   public boolean fireClient(ServerPlayer player, Varargs args, boolean optional) {
      return MLuaNetworkManager.get().sendToClient(player, this.name, MLuaNetworkSerializer.fromLuaArgs(args, 3), optional);
   }

   public boolean fireAllClients(Varargs args) {
      return this.fireAllClients(args, false);
   }

   public boolean fireAllClients(Varargs args, boolean optional) {
      return MLuaNetworkManager.get().sendToAllClients(this.name, MLuaNetworkSerializer.fromLuaArgs(args, 2), optional);
   }

   public boolean fireAllClients(Varargs args, boolean optional, MinecraftServer server) {
      return MLuaNetworkManager.get().sendToAllClients(server, this.name, MLuaNetworkSerializer.fromLuaArgs(args, 2), optional);
   }

   public static void requireSide(Side actual, Side expected, String method) {
      if (actual != expected) {
         throw new LuaError("[MLua] Network:" + method + " must be called from " + String.valueOf(expected) + " scripts");
      }
   }
}
