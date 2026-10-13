package dev.mlua.modules.players.Service;

import dev.mlua.core.MLuaRuntime;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import java.util.Iterator;
import java.util.UUID;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class MLuaPlayersService extends MLuaServiceBase {
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
      LuaTable table = new LuaTable();
      String ownerModId = this.context.mlua().ownerModId();
      table.set("__side", "Both");
      table.set("PlayerAdded", MLuaLuaBindings.signalInstance(PLAYER_ADDED, this.context.mlua().globals(), this.context.mlua().scheduler(), ownerModId, this.context.mlua().side()));
      table.set("PlayerRemoving", MLuaLuaBindings.signalInstance(PLAYER_REMOVING, this.context.mlua().globals(), this.context.mlua().scheduler(), ownerModId, this.context.mlua().side()));
      table.set("LocalPlayer", this.localPlayer());
      table.set("GetLocalPlayer", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return MLuaPlayersService.this.localPlayer();
         }
      });
      table.set("GetPlayers", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaTable players = new LuaTable();
            if (MLuaPlayersService.this.isClientSide()) {
               int index = 1;
               for (AbstractClientPlayer player : MLuaPlayersService.this.clientPlayers()) {
                  players.set(index++, MLuaPlayerWrapper.clientPlayerTable(player, MLuaPlayersService.this.context.mlua().registration()));
               }
            } else if (MLuaPlayersService.this.server() != null) {
               int index = 1;
               Iterator var4 = MLuaPlayersService.this.server().getPlayerList().getPlayers().iterator();

               while(var4.hasNext()) {
                  ServerPlayer player = (ServerPlayer)var4.next();
                  players.set(index++, MLuaPlayersService.playerTable(player, MLuaPlayersService.this.context.mlua().registration()));
               }
            }

            return players;
         }
      });
      table.set("GetPlayerByName", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            if (!MLuaPlayersService.this.isClientSide() && MLuaPlayersService.this.server() != null) {
               ServerPlayer player = MLuaPlayersService.this.server().getPlayerList().getPlayerByName(args.checkjstring(2));
               return (Varargs)(player == null ? LuaValue.NIL : MLuaPlayersService.playerTable(player, MLuaPlayersService.this.context.mlua().registration()));
            }
            if (MLuaPlayersService.this.isClientSide()) {
               for (AbstractClientPlayer player : MLuaPlayersService.this.clientPlayers()) {
                  if (player.getName().getString().equals(args.checkjstring(2))) return MLuaPlayerWrapper.clientPlayerTable(player, MLuaPlayersService.this.context.mlua().registration());
               }
            }
            return LuaValue.NIL;
         }
      });
      table.set("GetPlayerByUUID", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            UUID uuid = UUID.fromString(args.checkjstring(2));
            ServerPlayer player = MLuaPlayersService.this.isClientSide() || MLuaPlayersService.this.server() == null ? null
               : MLuaPlayersService.this.server().getPlayerList().getPlayer(uuid);
            if (player != null) return MLuaPlayersService.playerTable(player, MLuaPlayersService.this.context.mlua().registration());
            if (MLuaPlayersService.this.isClientSide()) {
               for (AbstractClientPlayer local : MLuaPlayersService.this.clientPlayers()) {
                  if (uuid.equals(local.getUUID())) return MLuaPlayerWrapper.clientPlayerTable(local, MLuaPlayersService.this.context.mlua().registration());
               }
            }
            return LuaValue.NIL;
         }
      });
      table.set("GetPlayerById", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            int id = args.checkint(2);
            if (!MLuaPlayersService.this.isClientSide() && MLuaPlayersService.this.server() != null) {
               ServerPlayer player = MLuaPlayersService.this.server().getPlayerList().getPlayers().stream()
                  .filter(value -> value.getId() == id).findFirst().orElse(null);
               if (player != null) return MLuaPlayersService.playerTable(player, MLuaPlayersService.this.context.mlua().registration());
            }
            if (MLuaPlayersService.this.isClientSide()) {
               for (AbstractClientPlayer local : MLuaPlayersService.this.clientPlayers()) {
                  if (local.getId() == id) return MLuaPlayerWrapper.clientPlayerTable(local, MLuaPlayersService.this.context.mlua().registration());
               }
            }
            return LuaValue.NIL;
         }
      });
      table.set("GetPlayerCount", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(MLuaPlayersService.this.isClientSide()
               ? MLuaPlayersService.this.clientPlayers().size()
               : MLuaPlayersService.this.server() == null ? 0 : MLuaPlayersService.this.server().getPlayerCount());
         }
      });
      table.set("GetMaxPlayers", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(MLuaPlayersService.this.isClientSide() || MLuaPlayersService.this.server() == null
               ? 0 : MLuaPlayersService.this.server().getMaxPlayers());
         }
      });
      table.set("Broadcast", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            if (!MLuaPlayersService.this.isClientSide() && MLuaPlayersService.this.server() != null) {
               MLuaPlayersService.this.server().getPlayerList().broadcastSystemMessage(Component.literal(args.checkjstring(2)), false);
            }

            return LuaValue.NONE;
         }
      });
      return table;
   }

   public static LuaTable playerTable(ServerPlayer player) {
      return playerTable(player, MLuaRuntime.registrationForLevel(player.level()));
   }

   public static LuaTable playerTable(ServerPlayer player, dev.mlua.module.MLuaRegistration registration) {
      return MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration);
   }

   private java.util.List<AbstractClientPlayer> clientPlayers() {
      if (!this.isClientSide()) return java.util.List.of();
      Minecraft minecraft = MLuaMinecraftClient.instance();
      return minecraft.level != null
         ? minecraft.level.players() : java.util.List.of();
   }

   private LuaValue localPlayer() {
      if (this.context.mlua().side() != Side.CLIENT) {
         return LuaValue.NIL;
      } else {
         LocalPlayer player = MLuaMinecraftClient.localPlayer();
         return (LuaValue)(player == null ? LuaValue.NIL : MLuaPlayerWrapper.clientPlayerTable(player, context.mlua().registration()));
      }
   }

   private boolean isClientSide() {
      return this.context.mlua().side() == Side.CLIENT;
   }

   private net.minecraft.server.MinecraftServer server() {
      return this.context.mlua().server();
   }

}
