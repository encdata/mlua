package dev.mlua.core.engine;

import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.core.MLuaContext;
import dev.mlua.module.MLuaModuleLoader;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.runtime.server.MLuaServerThread;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

/** Owns context binding, context globals, and serialized Java-to-Lua execution. */
final class MLuaContextBindings {
    private static final ThreadLocal<WeakReference<MLuaContext>> ACTIVE_CONTEXT = new ThreadLocal<>();
    private static final Map<Globals, Set<String>> CONTEXT_GLOBAL_NAMES = new WeakHashMap<>();
    private static final Map<Object, Integer> ACTIVE_EXECUTION_LOCKS = new IdentityHashMap<>();
    private static final Object EXECUTION_LOCK_TRACKER = new Object();
    private static final LuaValue CONTEXT_KEY = LuaValue.valueOf("__mlua_context");

    private MLuaContextBindings() { }

   static void bindContext(Globals globals, MLuaContext context) {
      if (context == null) ACTIVE_CONTEXT.remove();
      else ACTIVE_CONTEXT.set(new WeakReference<>(context));
      if (globals != null) {
         globals.set(CONTEXT_KEY, context != null ? CoerceJavaToLua.coerce(context) : LuaValue.NIL);
         if (context != null) globals.set("Docs", context.documentationTable(() -> MLuaDocumentationBindings.table(context)));
      }
   }

   /** Binds all globals whose values depend on the active script context. */
   static void configureContextGlobals(Globals globals, MLuaContext context) {
      if (globals == null) return;
      if (context == null) {
         bindContext(globals, null);
         clearContextGlobals(globals);
         return;
      }
      synchronized (context) {
         long moduleRevision = MLuaModuleLoader.get().globalsRevision();
         if (context.contextGlobalsConfigured() && context.contextGlobalsRevision() == moduleRevision) {
            boolean alreadyBound = boundContext(globals) == context;
            bindContext(globals, context);
            if (!alreadyBound) applyContextGlobals(globals, context);
            return;
         }
         // Addon modules can be registered or closed while a runtime is live. Remove
         // the previous context snapshot before collecting the current module set so
         // removed globals do not linger and newly registered globals become visible.
         clearContextGlobals(globals);
         Map<String, LuaValue> before = snapshotGlobals(globals);
         bindContext(globals, context);
         LuaTable task = MLuaLuaBindings.taskTable(context.scheduler(), globals);
         globals.set("task", task);
         globals.set("wait", task.get("wait"));
         globals.set("Minecraft", MLuaLuaBindings.minecraftTable(context));
         MLuaModuleLoader.get().contributeContextGlobals(globals, context);
         Map<String, LuaValue> after = snapshotGlobals(globals);
         Map<String, LuaValue> bindings = new LinkedHashMap<>();
         Set<String> keys = new java.util.TreeSet<>(before.keySet());
         keys.addAll(after.keySet());
         for (String key : keys) {
            LuaValue previous = before.getOrDefault(key, LuaValue.NIL);
            LuaValue current = after.getOrDefault(key, LuaValue.NIL);
            if (previous != current) bindings.put(key, current);
         }
         context.configureContextGlobals(bindings, moduleRevision);
         synchronized (CONTEXT_GLOBAL_NAMES) {
            Set<String> names = CONTEXT_GLOBAL_NAMES.computeIfAbsent(globals, ignored -> new java.util.TreeSet<>());
            names.addAll(bindings.keySet());
         }
      }
   }

   private static void clearContextGlobals(Globals globals) {
      synchronized (CONTEXT_GLOBAL_NAMES) {
         Set<String> names = CONTEXT_GLOBAL_NAMES.get(globals);
         if (names != null) for (String name : names) globals.set(name, LuaValue.NIL);
      }
   }

   private static Map<String, LuaValue> snapshotGlobals(Globals globals) {
      Map<String, LuaValue> snapshot = new LinkedHashMap<>();
      for (LuaValue key : globals.keys()) {
         if (key.isstring()) snapshot.put(key.tojstring(), globals.rawget(key));
      }
      return snapshot;
   }

   private static void applyContextGlobals(Globals globals, MLuaContext context) {
      Map<String, LuaValue> bindings = context.contextGlobals();
      synchronized (CONTEXT_GLOBAL_NAMES) {
         Set<String> names = CONTEXT_GLOBAL_NAMES.computeIfAbsent(globals, ignored -> new java.util.TreeSet<>());
         names.addAll(bindings.keySet());
         for (String name : names) globals.set(name, bindings.getOrDefault(name, LuaValue.NIL));
      }
      // The context marker and documentation table are bound by bindContext.
   }

   /** Runs Java-dispatched Lua work with its owner context and lock, restoring the caller context afterward. */
   static <T> T withContext(MLuaContext context, java.util.function.Supplier<T> callback) {
      java.util.Objects.requireNonNull(callback, "callback");
      if (context == null) return callback.get();
      Globals globals = context.globals();
      return withExecutionLock(context, () -> {
         MLuaContext previous = currentContext();
         MLuaContext previousBound = boundContext(globals);
         MLuaContext restore = previous != null && previous.globals() == globals ? previous : previousBound;
         // The server owner thread may pump a nested Lua event while a coroutine
         // is suspended with the execution lock held.
         configureContextGlobals(globals, context);
         try {
            return callback.get();
         } finally {
            configureContextGlobals(globals, restore);
         }
      });
   }

   /** Runs a Java callback under the Lua environment's execution lock when that lock is required. */
   static <T> T withExecutionLock(MLuaContext context, java.util.function.Supplier<T> callback) {
      java.util.Objects.requireNonNull(callback, "callback");
      if (context == null) return callback.get();
      Globals globals = context.globals();
      LuaThread running = globals == null ? null : globals.running;
      if (canBypassExecutionLock(context, running)) return callback.get();
      Object lock = context.executionLock();
      MinecraftServer server = context.server();
      if (server != null && server.isSameThread()
            && !MLuaServerThread.isExecutingLuaOwnerWork(server)
            && !Thread.holdsLock(lock) && isExecutionLockActive(lock)) {
         // A Lua coroutine can hold this lock while waiting for a server-thread
         // operation. Let the server process that queued operation before waiting
         // for the coroutine to release the lock, or the watchdog sees a deadlock.
         MLuaServerThread.runOwnerThreadLuaWait(server, () -> {
            server.managedBlock(() -> !isExecutionLockActive(lock));
            return null;
         });
      }
      synchronized (lock) {
         markExecutionLockActive(lock, 1);
         try {
            return callback.get();
         } finally {
            markExecutionLockActive(lock, -1);
         }
      }
   }

   private static boolean isExecutionLockActive(Object lock) {
      synchronized (EXECUTION_LOCK_TRACKER) {
         return ACTIVE_EXECUTION_LOCKS.containsKey(lock);
      }
   }

   private static void markExecutionLockActive(Object lock, int adjustment) {
      synchronized (EXECUTION_LOCK_TRACKER) {
         int count = ACTIVE_EXECUTION_LOCKS.getOrDefault(lock, 0) + adjustment;
         if (count <= 0) ACTIVE_EXECUTION_LOCKS.remove(lock);
         else ACTIVE_EXECUTION_LOCKS.put(lock, count);
      }
   }

   private static boolean canBypassExecutionLock(MLuaContext context, LuaThread running) {
      if (running != null && running.isRunningOnCurrentThread()) return true;
      MinecraftServer server = context.server();
      return server != null && server.isSameThread()
         && MLuaServerThread.isExecutingLuaOwnerWork(server);
   }

   /** Returns the Lua context active on this thread for a Java-backed callback. */
   static MLuaContext activeContext(MLuaRegistration registration) {
      MLuaContext context = currentContext();
      return context != null && context.registration() == registration ? context : null;
   }

   /** Returns the context active on this thread, if any. */
   static MLuaContext currentContext() {
      WeakReference<MLuaContext> reference = ACTIVE_CONTEXT.get();
      MLuaContext context = reference == null ? null : reference.get();
      if (context == null && reference != null) ACTIVE_CONTEXT.remove();
      return context;
   }

   static MLuaContext boundContext(Globals globals) {
      if (globals == null) {
         return null;
      }

      LuaValue value = globals.get(CONTEXT_KEY);
      return value.isuserdata(MLuaContext.class) ? (MLuaContext)value.touserdata(MLuaContext.class) : null;
   }

}
