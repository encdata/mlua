package dev.mlua.core.engine;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.core.signal.MLuaConnection;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.module.MLuaRegistration;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Owns Lua-facing task, signal, and connection instance bindings. */
final class MLuaSignalBindings {
    private MLuaSignalBindings() { }

   static LuaTable taskTable(final MLuaTaskSchedulerView scheduler, final Globals globals) {
      LuaTable task = new LuaTable();
      task.set("spawn", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaValue fn = args.arg1();
            scheduler.spawn(globals, fn, args.subargs(2));
            return LuaValue.NIL;
         }
      });
      task.set("defer", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaValue fn = args.arg1();
            scheduler.defer(globals, fn, args.subargs(2));
            return LuaValue.NIL;
         }
      });
      task.set("delay", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaValue ticks = args.arg1();
            LuaValue fn = args.arg(2);
            scheduler.delay(globals, Math.max(0L, ticks.tolong()), fn, args.subargs(3));
            return LuaValue.NIL;
         }
      });
      task.set("wait", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            long ticks = args.narg() != 0 && !args.arg1().isnil() ? Math.max(0L, args.arg1().tolong()) : 1L;
            return scheduler.waitForTicks(globals, ticks);
         }
      });
      return task;
   }

   static LuaTable signalTable(final Globals globals, final MLuaTaskSchedulerView scheduler) {
      LuaTable signal = new LuaTable();
      signal.set("new", new ZeroArgFunction() {
         public LuaValue call() {
            return MLuaSignalBindings.signalInstance(new MLuaSignal(), globals, scheduler);
         }
      });
      return signal;
   }

   static LuaTable signalInstance(final MLuaSignal signal, final Globals globals) {
      return signalInstance(signal, globals, null);
   }

   static LuaTable signalInstance(final MLuaSignal signal, final Globals globals, final MLuaTaskSchedulerView scheduler) {
      return signalInstance(signal, globals, scheduler, null);
   }

   static LuaTable signalInstance(final MLuaSignal signal, final Globals globals, final MLuaTaskSchedulerView scheduler, final String ownerKey) {
      return signalInstance(signal, globals, scheduler, ownerKey, null);
   }

   static LuaTable signalInstance(final MLuaSignal signal, final Globals globals, final MLuaTaskSchedulerView scheduler, final String ownerKey, final Side side) {
      MLuaContext ownerContext = MLuaLuaBindings.boundContext(globals);
      MLuaRegistration registration = ownerContext == null ? null : ownerContext.registration();
      String resolvedOwner = ownerKey != null ? ownerKey : ownerContext != null ? ownerContext.ownerModId() : null;
      Side resolvedSide = side != null ? side : ownerContext != null ? ownerContext.side() : null;
      signal.bind(globals, scheduler);
      LuaTable table = nativeInstance(new LuaTable(), registration, "Signal");
      table.rawset("__mlua_signal", LuaValue.userdataOf(signal));
      bindNativeInstanceMethod(table, registration, "Signal", "Connect", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue fn) {
            return MLuaSignalBindings.connection(signal.connectOwned(fn, resolvedOwner, resolvedSide, globals, scheduler, ownerContext), registration);
         }
      });
      bindNativeInstanceMethod(table, registration, "Signal", "Once", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue fn) {
            return MLuaSignalBindings.connection(signal.onceOwned(fn, resolvedOwner, resolvedSide, globals, scheduler, ownerContext), registration);
         }
      });
      bindNativeInstanceMethod(table, registration, "Signal", "Fire", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            signal.fire(args.subargs(2));
            return LuaValue.NONE;
         }
      });
      bindNativeInstanceMethod(table, registration, "Signal", "Wait", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            if (globals == null) {
               throw new LuaError("Signal:Wait is not supported on this signal");
            }
            return signal.wait(globals, resolvedOwner, resolvedSide);
         }
      });
      return table;
   }

   static LuaTable connection(final MLuaConnection connection, MLuaRegistration registration) {
      LuaTable table = nativeInstance(new LuaTable(), registration, "Connection");
      table.rawset("__mlua_connection", LuaValue.userdataOf(connection));
      bindNativeInstanceMethod(table, registration, "Connection", "Disconnect", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            connection.disconnect();
            return LuaValue.NIL;
         }
      });
      bindNativeInstanceMethod(table, registration, "Connection", "GetConnected", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(connection.connected());
         }
      });
      return table;
   }

   private static LuaTable nativeInstance(LuaTable instance, MLuaRegistration registration, String className) {
      return registration != null && registration.classTable(className) != null
         ? registration.attachClassInstance(instance, className, null) : instance;
   }

   private static void bindNativeInstanceMethod(LuaTable instance, MLuaRegistration registration,
                                                String className, String methodName, LuaValue implementation) {
      if (registration != null && registration.classTable(className) != null
         && registration.isInstanceOf(instance, className)) {
         MLuaClassMethods.setInstanceMethod(instance, methodName, implementation);
      } else {
         instance.set(methodName, implementation);
      }
   }

}
