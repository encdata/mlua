package dev.mlua.modules.players.service;

import net.minecraft.server.MinecraftServer;

import dev.mlua.core.MLuaRuntime;
import dev.mlua.api.MLuaDescription;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServerThreadReflectiveService;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.core.signal.MLuaSignal;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.luaj.vm2.LuaError;

@MLuaDescription("Manages player connections and provides player instances.")
public final class MLuaPlayersService extends MLuaServerThreadReflectiveService {
   private final MLuaPlayerDirectory directory;
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
      this.directory = new MLuaPlayerDirectory(context);
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
      return this.directory.getPlayers();
   }

   @MLuaDescription("Finds a connected player by exact name, or returns nil.")
   public LuaValue GetPlayerByName(String name) {
      return this.directory.getPlayerByName(name);
   }

   @MLuaDescription("Finds a connected player by UUID, or returns nil.")
   public LuaValue GetPlayerByUUID(String value) {
      return this.directory.getPlayerByUUID(value);
   }

   @MLuaDescription("Finds a connected player by numeric entity id, or returns nil.")
   public LuaValue GetPlayerById(int id) {
      return this.directory.getPlayerById(id);
   }

   @MLuaDescription("Returns the number of connected players.")
   public int GetPlayerCount() {
      return this.directory.getPlayerCount();
   }

   @MLuaDescription("Returns the server's maximum player count; returns zero on the client.")
   public int GetMaxPlayers() {
      return this.isClientSide() || this.server() == null ? 0 : this.server().getMaxPlayers();
   }

   @MLuaDescription("Sends a system chat message to every player; server-side only.")
   public LuaValue Broadcast(String message) {
      this.requireServerSide("Players:Broadcast");
      if (this.server() == null) {
         throw new LuaError("[MLua] Players:Broadcast requires an active server.");
      }
      if (message == null) {
         throw new LuaError("[MLua] Players:Broadcast message must not be nil.");
      }
      this.server().getPlayerList().broadcastSystemMessage(Component.literal(message), false);
      return LuaValue.NONE;
   }

   public static LuaTable playerTable(ServerPlayer player) {
      return playerTable(player, MLuaRuntime.registrationForLevel(player.level()));
   }

   public static LuaTable playerTable(ServerPlayer player, dev.mlua.module.MLuaRegistration registration) {
      return MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration);
   }

   private LuaValue localPlayer() {
      return this.directory.localPlayer();
   }

   private boolean isClientSide() {
      return this.context.mlua().side() == Side.CLIENT;
   }

   private MinecraftServer server() {
      return this.context.mlua().server();
   }


}
