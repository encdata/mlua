package dev.mlua.modules.chat.Service;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.modules.chat.MLuaChatStateRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Server chat operations backed by the shared reflective service contract. */
public final class MLuaChatService extends MLuaReflectiveService {
   private static final int MAX_HISTORY_PER_PLAYER = 100;

   public static void clearRuntime(Object runtimeOwner) {
      MLuaChatStateRegistry.clear(runtimeOwner);
   }

   public static MLuaSignal onMessage(Object runtimeOwner) {
      return MLuaChatStateRegistry.get(runtimeOwner).onMessage;
   }

   public static boolean isPlayerMuted(Object runtimeOwner, String playerName) {
      var mutedPlayers = MLuaChatStateRegistry.get(runtimeOwner).mutedPlayers;
      Long expiry = mutedPlayers.get(playerName);
      if (expiry == null) return false;
      if (expiry == 0L) return true;
      if (System.currentTimeMillis() < expiry) return true;
      mutedPlayers.remove(playerName);
      return false;
   }

   public static void addMessageToHistory(Object runtimeOwner, String playerName, String message) {
      var messageHistory = MLuaChatStateRegistry.get(runtimeOwner).messageHistory;
      ConcurrentLinkedDeque<String> history = messageHistory.computeIfAbsent(playerName, ignored -> new ConcurrentLinkedDeque<>());
      history.addLast(message);
      while (history.size() > MAX_HISTORY_PER_PLAYER) history.pollFirst();
   }

   public MLuaChatService(MLuaService.Context context) {
      super(context);
   }

   private Object runtimeOwner() {
      return this.context.registration() instanceof MLuaRuntime runtime ? runtime.session() : null;
   }

   @Override
   public String name() {
      return "Chat";
   }

   @Override
   public LuaTable toLua() {
      LuaTable table = super.toLua();
      table.set("__side", "Server");
      table.set("OnMessage", MLuaLuaBindings.signalInstance(
         onMessage(runtimeOwner()), this.context.mlua().globals(), this.context.scheduler(),
         this.context.ownerModId(), this.context.side()));
      return table;
   }

   public void Broadcast(String message) {
      var server = this.context.server();
      if (server != null) server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
   }

   public void SendMessage(String target, String message) {
      var server = this.context.server();
      if (server == null) return;
      var player = server.getPlayerList().getPlayerByName(target);
      if (player != null) player.sendSystemMessage(Component.literal(message));
   }

   public void MutePlayer(String playerName, Optional<Double> durationSeconds) {
      double seconds = durationSeconds.orElse(0.0);
      long expiry = seconds > 0 ? System.currentTimeMillis() + (long) (seconds * 1000.0) : 0L;
      MLuaChatStateRegistry.get(runtimeOwner()).mutedPlayers.put(playerName, expiry);
   }

   public void UnmutePlayer(String playerName) {
      MLuaChatStateRegistry.get(runtimeOwner()).mutedPlayers.remove(playerName);
   }

   public boolean IsMuted(String playerName) {
      return isPlayerMuted(runtimeOwner(), playerName);
   }

   public List<String> GetMutedPlayers() {
      var mutedPlayers = MLuaChatStateRegistry.get(runtimeOwner()).mutedPlayers;
      List<String> result = new ArrayList<>();
      for (String playerName : mutedPlayers.keySet()) {
         if (isPlayerMuted(runtimeOwner(), playerName)) result.add(playerName);
      }
      return result;
   }

   public List<String> GetMessageHistory(String playerName, Optional<Integer> count) {
      int limit = Math.max(0, count.orElse(10));
      ConcurrentLinkedDeque<String> history = MLuaChatStateRegistry.get(runtimeOwner()).messageHistory.get(playerName);
      if (history == null || limit == 0) return List.of();
      List<String> result = new ArrayList<>(Math.min(limit, history.size()));
      for (String message : history) {
         if (result.size() >= limit) break;
         result.add(message);
      }
      return result;
   }

   public void ClearChat(String playerName) {
      var server = this.context.server();
      if (server == null) return;
      var player = server.getPlayerList().getPlayerByName(playerName);
      if (player != null) {
         for (int index = 0; index < 100; index++) player.sendSystemMessage(Component.literal(""));
      }
   }

   public List<String> GetOnlinePlayers() {
      var server = this.context.server();
      if (server == null) return List.of();
      return server.getPlayerList().getPlayers().stream()
         .map(ServerPlayer::getName)
         .map(Component::getString)
         .toList();
   }

   public void BroadcastToPermission(String permission, String message) {
      var server = this.context.server();
      if (server == null) return;
      Component component = Component.literal(message);
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         if (player.hasPermissions(server.getOperatorUserPermissionLevel()) || permission.isEmpty()) {
            player.sendSystemMessage(component);
         }
      }
   }
}
