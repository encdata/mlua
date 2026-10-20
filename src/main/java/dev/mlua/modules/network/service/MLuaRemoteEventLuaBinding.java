package dev.mlua.modules.network.service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContextView;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.minecraft.network.MLuaRemoteEvent;
import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Builds the Lua class instance for a registered remote event. */
final class MLuaRemoteEventLuaBinding {
   private MLuaRemoteEventLuaBinding() {
   }

   static LuaTable create(MLuaRemoteEvent remote, MLuaContextView mlua,
                          Function<LuaValue, ServerPlayer> playerResolver) {
      LuaTable table = new LuaTable();
      LuaTable serverSignal = MLuaLuaBindings.signalInstance(remote.serverEvent(), mlua.globals(),
         mlua.scheduler(), mlua.ownerModId(), mlua.side());
      LuaTable clientSignal = MLuaLuaBindings.signalInstance(remote.clientEvent(), mlua.globals(),
         mlua.scheduler(), mlua.ownerModId(), mlua.side());
      LuaTable instance = mlua.registration().attachClassInstance(table, "RemoteEvent", null);
      MLuaClassMethods.setInstanceMethod(instance, "GetName", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(remote.name());
         }
      });
      MLuaClassMethods.setInstanceMethod(instance, "GetOnServerEvent", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            return serverSignal;
         }
      });
      MLuaClassMethods.setInstanceMethod(instance, "GetOnClientEvent", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            return clientSignal;
         }
      });
      MLuaClassMethods.setInstanceMethod(instance, "FireServer", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            MLuaRemoteEvent.requireSide(mlua.side(), Side.CLIENT, "FireServer");
            return LuaValue.valueOf(remote.fireServer(args));
         }
      });
      MLuaClassMethods.setInstanceMethod(instance, "FireServerOptional", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            MLuaRemoteEvent.requireSide(mlua.side(), Side.CLIENT, "FireServerOptional");
            return LuaValue.valueOf(remote.fireServer(args, true));
         }
      });
      MLuaClassMethods.setInstanceMethod(instance, "FireClient", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            MLuaRemoteEvent.requireSide(mlua.side(), Side.SERVER, "FireClient");
            return LuaValue.valueOf(remote.fireClient(playerResolver.apply(args.arg(2)), args));
         }
      });
      MLuaClassMethods.setInstanceMethod(instance, "FireClientOptional", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            MLuaRemoteEvent.requireSide(mlua.side(), Side.SERVER, "FireClientOptional");
            return LuaValue.valueOf(remote.fireClient(playerResolver.apply(args.arg(2)), args, true));
         }
      });
      MLuaClassMethods.setInstanceMethod(instance, "FireAllClients", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            MLuaRemoteEvent.requireSide(mlua.side(), Side.SERVER, "FireAllClients");
            return LuaValue.valueOf(remote.fireAllClients(args, false, mlua.server()));
         }
      });
      MLuaClassMethods.setInstanceMethod(instance, "FireAllClientsOptional", new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            MLuaRemoteEvent.requireSide(mlua.side(), Side.SERVER, "FireAllClientsOptional");
            return LuaValue.valueOf(remote.fireAllClients(args, true, mlua.server()));
         }
      });
      return instance;
   }
}
