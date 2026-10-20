package dev.mlua.modules.chat.service;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.service.MLuaServerThreadReflectiveService;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.modules.chat.MLuaChatStateRegistry;
import org.luaj.vm2.LuaError;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@MLuaDescription("Server chat messaging, moderation, history, and the cancellable OnMessage event. Set event.Message to replace a message; MLua cancels the original signed chat packet and broadcasts the replacement using Minecraft's standard chat format.")
public final class MLuaChatService extends MLuaServerThreadReflectiveService {
   private static final int MAX_HISTORY_PER_PLAYER = 100;

   public static void clearRuntime(Object runtimeOwner) {
      MLuaChatStateRegistry.clear(runtimeOwner);
   }

   public static MLuaSignal onMessage(Object runtimeOwner) {
      return MLuaChatStateRegistry.get(runtimeOwner).onMessage;
   }

   public static boolean isPlayerMuted(Object runtimeOwner, String playerName) {
      if (playerName == null || playerName.isBlank()) return false;
      var mutedPlayers = MLuaChatStateRegistry.get(runtimeOwner).mutedPlayers;
      Long expiry = mutedPlayers.get(playerName);
      if (expiry == null) return false;
      if (expiry == 0L) return true;
      if (System.currentTimeMillis() < expiry) return true;
      mutedPlayers.remove(playerName);
      return false;
   }

   public static void addMessageToHistory(Object runtimeOwner, String playerName, String message) {
      if (playerName == null || playerName.isBlank() || message == null) return;
      var messageHistory = MLuaChatStateRegistry.get(runtimeOwner).messageHistory;
      ConcurrentLinkedDeque<String> history = messageHistory.computeIfAbsent(playerName, ignored -> new ConcurrentLinkedDeque<>());
      synchronized (history) {
         history.addLast(message);
         while (history.size() > MAX_HISTORY_PER_PLAYER) history.pollFirst();
      }
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

   @MLuaDescription("Sends a chat message to every online player.")
   public void Broadcast(String message) {
      var server = this.context.server();
      if (server != null) server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
   }

   @MLuaDescription("Sends a chat message to one online player by name.")
   public void SendMessage(String target, String message) {
      var server = this.context.server();
      if (server == null) return;
      var player = server.getPlayerList().getPlayerByName(target);
      if (player != null) player.sendSystemMessage(Component.literal(message));
   }

   @MLuaDescription("Prevents a player from sending chat; duration is in seconds, or omitted for a permanent mute.")
   public void MutePlayer(String playerName, Optional<Double> durationSeconds) {
      if (playerName == null || playerName.isBlank()) {
         throw new LuaError("[MLua] Chat:MutePlayer expects a non-empty player name.");
      }
      double seconds = durationSeconds.orElse(0.0);
      if (!Double.isFinite(seconds)) {
         throw new LuaError("[MLua] Chat:MutePlayer duration must be a finite number of seconds.");
      }
      long expiry = 0L;
      if (seconds > 0) {
         double durationMillis = seconds * 1000.0;
         long now = System.currentTimeMillis();
         long boundedDuration = durationMillis >= Long.MAX_VALUE - now
            ? Long.MAX_VALUE - now : Math.max(1L, (long) durationMillis);
         expiry = now + boundedDuration;
      }
      MLuaChatStateRegistry.get(runtimeOwner()).mutedPlayers.put(playerName, expiry);
   }

   @MLuaDescription("Allows a muted player to send chat again.")
   public void UnmutePlayer(String playerName) {
      if (playerName == null || playerName.isBlank()) {
         throw new LuaError("[MLua] Chat:UnmutePlayer expects a non-empty player name.");
      }
      MLuaChatStateRegistry.get(runtimeOwner()).mutedPlayers.remove(playerName);
   }

   @MLuaDescription("Returns whether a player is currently muted.")
   public boolean IsMuted(String playerName) {
      return isPlayerMuted(runtimeOwner(), playerName);
   }

   @MLuaDescription("Returns the names of currently muted players.")
   public List<String> GetMutedPlayers() {
      var mutedPlayers = MLuaChatStateRegistry.get(runtimeOwner()).mutedPlayers;
      List<String> result = new ArrayList<>();
      for (String playerName : mutedPlayers.keySet()) {
         if (isPlayerMuted(runtimeOwner(), playerName)) result.add(playerName);
      }
      Collections.sort(result);
      return result;
   }

   @MLuaDescription("Returns recent recorded chat messages for a player.")
   public List<String> GetMessageHistory(String playerName, Optional<Integer> count) {
      if (playerName == null || playerName.isBlank()) {
         throw new LuaError("[MLua] Chat:GetMessageHistory expects a non-empty player name.");
      }
      int limit = Math.min(MAX_HISTORY_PER_PLAYER, Math.max(0, count.orElse(10)));
      ConcurrentLinkedDeque<String> history = MLuaChatStateRegistry.get(runtimeOwner()).messageHistory.get(playerName);
      if (history == null || limit == 0) return List.of();
      List<String> result = new ArrayList<>(limit);
      synchronized (history) {
         var newestFirst = history.descendingIterator();
         while (newestFirst.hasNext() && result.size() < limit) {
            result.add(newestFirst.next());
         }
      }
      Collections.reverse(result);
      return result;
   }

   @MLuaDescription("Clears a player's visible chat by sending blank lines.")
   public void ClearChat(String playerName) {
      var server = this.context.server();
      if (server == null) return;
      var player = server.getPlayerList().getPlayerByName(playerName);
      if (player != null) {
         for (int index = 0; index < 100; index++) player.sendSystemMessage(Component.literal(""));
      }
   }

   @MLuaDescription("Returns the names of online players.")
   public List<String> GetOnlinePlayers() {
      var server = this.context.server();
      if (server == null) return List.of();
      return server.getPlayerList().getPlayers().stream()
         .map(ServerPlayer::getName)
         .map(Component::getString)
         .toList();
   }

   @MLuaDescription("Sends a message to server operators, or to everyone when permission is empty.")
   public void BroadcastToPermission(String permission, String message) {
      var server = this.context.server();
      if (server == null) return;
      Component component = Component.literal(message);
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         if (permission == null || permission.isEmpty()
               || hasPermission(server, player, server.getOperatorUserPermissionLevel())) {
            player.sendSystemMessage(component);
         }
      }
   }

   @MLuaDescription("Sends a message to online players who meet the supplied vanilla permission level, from 0 through 4.")
   public void BroadcastToPermissionLevel(int permissionLevel, String message) {
      if (permissionLevel < 0 || permissionLevel > 4) {
         throw new LuaError("[MLua] Chat:BroadcastToPermissionLevel expects a permission level from 0 to 4.");
      }
      var server = this.context.server();
      if (server == null) return;
      Component component = Component.literal(message);
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         if (hasPermission(server, player, permissionLevel)) player.sendSystemMessage(component);
      }
   }

   private static boolean hasPermission(net.minecraft.server.MinecraftServer server,
                                        ServerPlayer player, int permissionLevel) {
      if (permissionLevel <= 0) return true;
//--version 26.1
      return isOperatorInCurrentVersion(server.getPlayerList(), player);
//--endversion
//--version 1.20.1,1.21.1
      return player.hasPermissions(permissionLevel);
//--endversion
   }

   //--version 26.1
   private static boolean isOperatorInCurrentVersion(Object playerList, ServerPlayer player) {
      for (java.lang.reflect.Method operatorCheck : playerList.getClass().getMethods()) {
         if (!operatorCheck.getName().equals("isOp") || operatorCheck.getParameterCount() != 1) continue;
         Class<?> expectedType = operatorCheck.getParameterTypes()[0];
         for (java.lang.reflect.Method accessor : player.getClass().getMethods()) {
            if (accessor.getParameterCount() != 0 || !expectedType.isAssignableFrom(accessor.getReturnType())) continue;
            try {
               Object identity = accessor.invoke(player);
               if (identity != null) return Boolean.TRUE.equals(operatorCheck.invoke(playerList, identity));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
               // Try another mapped identity accessor when mappings expose aliases.
            }
         }
      }
      return false;
   }
   //--endversion
}
