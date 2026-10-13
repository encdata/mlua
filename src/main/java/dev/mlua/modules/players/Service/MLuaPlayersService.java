package dev.mlua.modules.players.Service;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.mc.impl.entity.MCLocalPlayerImpl;
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
                  players.set(index++, MLuaPlayersService.clientPlayerTable(player, MLuaPlayersService.this.context.mlua().registration()));
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
                  if (player.getName().getString().equals(args.checkjstring(2))) return MLuaPlayersService.clientPlayerTable(player, MLuaPlayersService.this.context.mlua().registration());
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
                  if (uuid.equals(local.getUUID())) return MLuaPlayersService.clientPlayerTable(local, MLuaPlayersService.this.context.mlua().registration());
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
                  if (local.getId() == id) return MLuaPlayersService.clientPlayerTable(local, MLuaPlayersService.this.context.mlua().registration());
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
      return playerTable(player, dev.mlua.minecraft.scripted.MLuaScriptRuntimeRegistration.forLevel(player.level()));
   }

   public static LuaTable playerTable(ServerPlayer player, dev.mlua.module.MLuaRegistration registration) {
      return MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(player), registration);
   }

    private static LuaTable clientPlayerTable(AbstractClientPlayer player, dev.mlua.module.MLuaRegistration registration) {
      MLuaPlayerWrapper.classTable(registration);
      if (player instanceof LocalPlayer local) {
         return MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(local), registration);
      }
      LuaTable cachedPlayer = MLuaEntityWrapper.cachedPlayerTable(player, registration);
      if (cachedPlayer != null) {
         LuaValue cachedEntity = cachedPlayer.rawget("__entity");
         if (cachedEntity.isuserdata(net.minecraft.world.entity.Entity.class)
               && cachedEntity.touserdata(net.minecraft.world.entity.Entity.class) == player) {
            return cachedPlayer;
         }
      }
      LuaTable table = MLuaEntityWrapper.entityTable(player, registration);
      registration.attachClassInstance(table, "Player", "LivingEntity");
      table.rawset("__entity", LuaValue.userdataOf(player));
      MLuaClassMethods.setInstanceMethod(table, "GetIsLocalPlayer", valueFunction(() -> LuaValue.FALSE));
      MLuaClassMethods.setInstanceMethod(table, "GetUserId", valueFunction(() -> LuaValue.valueOf(player.getId())));
      MLuaClassMethods.setInstanceMethod(table, "GetDisplayName", valueFunction(() -> LuaValue.valueOf(player.getDisplayName().getString())));
      MLuaClassMethods.setInstanceMethod(table, "IsOnline", valueFunction(() -> LuaValue.TRUE));
      MLuaEntityWrapper.attachEntityProperties(table);
      MLuaEntityWrapper.cachePlayerTable(player, registration, table);
      return table;
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
         return (LuaValue)(player == null ? LuaValue.NIL : clientPlayerTable(player, context.mlua().registration()));
      }
   }

   private boolean isClientSide() {
      return this.context.mlua().side() == Side.CLIENT;
   }

   private net.minecraft.server.MinecraftServer server() {
      return this.context.mlua().server();
   }

   private static VarArgFunction valueFunction(java.util.function.Supplier<LuaValue> value) {
      return new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) { return value.get(); }
      };
   }
}
