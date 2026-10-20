package dev.mlua.minecraft.command;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.mc.impl.entity.MCLocalPlayerImpl;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.client.player.LocalPlayer;

public final class MLuaCommandContext {
   private MLuaCommandContext() {
   }

   public static LuaTable toLua(final CommandSourceStack source) {
      return toLua(source, MLuaRuntime.get());
   }

   public static LuaTable toLua(final CommandSourceStack source, MLuaRegistration registration) {
      if (registration == null) registration = MLuaRuntime.get();
      LuaTable table = new LuaTable();
      table.set("Source", source.getTextName());
      table.set("IsPlayer", LuaValue.valueOf(source.isPlayer()));
      table.set("Position", MLuaPlayerWrapper.positionTable(source.getPosition().x, source.getPosition().y, source.getPosition().z));
      if (source.isPlayer() && source.getPlayer() != null) {
          table.set("Player", MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(source.getPlayer()), registration));
      } else {
         table.set("Player", LuaValue.NIL);
      }

      table.set("Reply", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            source.sendSuccess(() -> {
               return Component.literal(args.checkjstring(2));
            }, false);
            return LuaValue.NONE;
         }
      });
      table.set("Broadcast", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            source.getServer().getPlayerList().broadcastSystemMessage(Component.literal(args.checkjstring(2)), false);
            return LuaValue.NONE;
         }
      });
      table.set("Error", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            source.sendFailure(Component.literal(args.checkjstring(2)));
            return LuaValue.NONE;
         }
      });
      return table;
   }

   public static LuaTable toClientLua() {
      return toClientLua(MLuaRuntime.get());
   }

   public static LuaTable toClientLua(MLuaRegistration registration) {
      if (registration == null) registration = MLuaRuntime.get();
      LocalPlayer player = MLuaMinecraftClient.localPlayer();
      LuaTable table = new LuaTable();
      table.set("Source", LuaValue.valueOf(player != null ? player.getName().getString() : "client"));
      table.set("IsPlayer", LuaValue.valueOf(player != null));
      table.set("Position", player != null ? MLuaPlayerWrapper.positionTable(player.getX(), player.getY(), player.getZ()) : MLuaPlayerWrapper.positionTable(0.0, 0.0, 0.0));
      table.set("Player", player != null ? MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(player), registration) : LuaValue.NIL);
      table.set("Reply", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LocalPlayer current = MLuaMinecraftClient.localPlayer();
            if (current != null) {
               current.displayClientMessage(Component.literal(args.checkjstring(2)), false);
            }

            return LuaValue.NONE;
         }
      });
      table.set("Broadcast", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LocalPlayer current = MLuaMinecraftClient.localPlayer();
            if (current != null) {
               current.displayClientMessage(Component.literal(args.checkjstring(2)), false);
            }

            return LuaValue.NONE;
         }
      });
      table.set("Error", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LocalPlayer current = MLuaMinecraftClient.localPlayer();
            if (current != null) {
               current.displayClientMessage(Component.literal(args.checkjstring(2)), false);
            }

            return LuaValue.NONE;
         }
      });
      return table;
   }
}
