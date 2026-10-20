package dev.mlua.core.signal;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.core.scheduler.MLuaTaskScheduler;
import dev.mlua.common.enums.Side;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MLuaSignal {
   public static final int MAX_LISTENERS_PER_SIGNAL = 4_096;
   public static final int MAX_LISTENERS_PER_OWNER = 256;
   public static final int MAX_WAITERS_PER_SIGNAL = 4_096;
   public static final int MAX_WAITERS_PER_OWNER = 256;
   public record Usage(int listeners, int listenersForOwner, int waiters, int waitersForOwner) { }
   private static final Map<MLuaSignal, Boolean> ALL_SIGNALS = java.util.Collections.synchronizedMap(new WeakHashMap<>());
   // Event dispatch is frequent while listener changes are rare. This keeps each fire
   // from allocating a copied listener list and still gives dispatch a stable snapshot.
   private final List<ListenerEntry> listeners = new CopyOnWriteArrayList<>();
   private final Map<String, Integer> listenerCountsByOwner = new HashMap<>();
   private volatile Globals globals;
   private volatile MLuaTaskSchedulerView scheduler;
   private volatile boolean permanentlyDestroyed;
   private final List<WaitEntry> waiters = new ArrayList<>();

   public MLuaSignal() {
      ALL_SIGNALS.put(this, Boolean.TRUE);
   }

   public static void destroyAllOwned(String ownerModId, Side side) {
      if (ownerModId == null || side == null) return;
      for (MLuaSignal signal : signalSnapshot()) signal.destroyOwned(ownerModId, side);
   }

   public static void destroyAllOwned(MLuaRuntimeSession session, String ownerModId, Side side) {
      if (session == null || ownerModId == null || side == null) return;
      for (MLuaSignal signal : signalSnapshot()) signal.destroyOwned(session, ownerModId, side);
   }

   public static void destroyAllOnSide(Side side) {
      if (side == null) return;
      if (side == Side.BOTH) {
         for (MLuaSignal signal : signalSnapshot()) signal.Destroy();
         return;
      }
      for (MLuaSignal signal : signalSnapshot()) signal.destroySide(side);
   }

   public static void destroyAllOnSession(MLuaRuntimeSession session, Side side) {
      if (session == null || side == null) return;
      for (MLuaSignal signal : signalSnapshot()) signal.destroySessionSide(session, side);
   }

   private static List<MLuaSignal> signalSnapshot() {
      synchronized (ALL_SIGNALS) {
         return List.copyOf(ALL_SIGNALS.keySet());
      }
   }

   /** Returns a best-effort snapshot of signal use across live signal instances. */
   public static Usage usage(String ownerKey) {
      int listeners = 0;
      int ownedListeners = 0;
      int waiters = 0;
      int ownedWaiters = 0;
      for (MLuaSignal signal : signalSnapshot()) {
         synchronized (signal) {
            listeners += signal.listeners.size();
            waiters += signal.waiters.size();
            if (ownerKey != null) {
               ownedListeners += signal.listenerCountsByOwner.getOrDefault(ownerKey, 0);
               ownedWaiters += (int) signal.waiters.stream()
                  .filter(entry -> ownerKey.equals(entry.ownerKey())).count();
            }
         }
      }
      return new Usage(listeners, ownedListeners, waiters, ownedWaiters);
   }

   public synchronized void bind(Globals globals, MLuaTaskSchedulerView scheduler) {
      this.globals = globals;
      this.scheduler = scheduler;
   }

   public MLuaConnection connect(LuaValue listener) {
      return this.connectOwned(listener, null);
   }

   public MLuaConnection connectOwned(LuaValue listener, String ownerKey) {
      return this.connectOwned(listener, ownerKey, null);
   }

   public MLuaConnection connectOwned(LuaValue listener, String ownerKey, Side side) {
      return this.connectOwned(listener, ownerKey, side, null, null);
   }

   public MLuaConnection connectOwned(LuaValue listener, String ownerKey, Side side, Globals ownerGlobals, MLuaTaskSchedulerView ownerScheduler) {
      return this.connectOwned(listener, ownerKey, side, ownerGlobals, ownerScheduler, null);
   }

   public MLuaConnection connectOwned(LuaValue listener, String ownerKey, Side side, Globals ownerGlobals, MLuaTaskSchedulerView ownerScheduler, MLuaContext context) {
      if (listener == null || !listener.isfunction()) {
         throw new LuaError("Signal:Connect expected a function");
      } else {
         ListenerEntry[] reference = new ListenerEntry[1];
         MLuaConnection connection = new MLuaConnection(() -> {
            synchronized (this) {
               this.removeListener(reference[0]);
            }
         });
         ListenerEntry entry = new ListenerEntry(listener, ownerKey, side, ownerGlobals, ownerScheduler, context, connection);
         reference[0] = entry;
         this.addListener(entry);
         return connection;
      }
   }

   public MLuaConnection once(final LuaValue listener) {
      return this.onceOwned(listener, null);
   }

   public MLuaConnection onceOwned(final LuaValue listener, final String ownerKey) {
      return this.onceOwned(listener, ownerKey, null);
   }

   public MLuaConnection onceOwned(final LuaValue listener, final String ownerKey, final Side side) {
      return this.onceOwned(listener, ownerKey, side, null, null);
   }

   public MLuaConnection onceOwned(final LuaValue listener, final String ownerKey, final Side side, final Globals ownerGlobals, final MLuaTaskSchedulerView ownerScheduler) {
      return this.onceOwned(listener, ownerKey, side, ownerGlobals, ownerScheduler, null);
   }

   public MLuaConnection onceOwned(final LuaValue listener, final String ownerKey, final Side side, final Globals ownerGlobals, final MLuaTaskSchedulerView ownerScheduler, final MLuaContext context) {
      if (listener == null || !listener.isfunction()) {
         throw new LuaError("Signal:Once expected a function");
      }
      final ListenerEntry[] ref = new ListenerEntry[1];
      final MLuaConnection connection = new MLuaConnection(() -> {
         synchronized(this) {
            if (ref[0] != null) {
               this.removeListener(ref[0]);
            }
         }
      });

      LuaValue wrapper = new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            connection.disconnect();
            return listener.invoke(args);
         }
      };
      synchronized(this) {
         this.ensureOpen();
         ref[0] = new ListenerEntry(wrapper, ownerKey, side, ownerGlobals, ownerScheduler, context, connection);
         this.ensureListenerCapacity(ownerKey);
         this.addListenerLocked(ref[0]);
      }
      return connection;
   }

   public Varargs wait(Globals globals) {
      return this.wait(globals, null, null);
   }

   public Varargs wait(Globals globals, String ownerKey, Side side) {
      if (globals == null) {
         throw new LuaError("Signal:Wait requires an active Lua runtime");
      }
      LuaThread thread = globals.running;
      if (thread == null) {
         throw new LuaError("Signal:Wait must be called from a running Lua coroutine");
      }
      WaitEntry waiter;
      synchronized (this) {
         this.ensureOpen();
         MLuaContext context = MLuaLuaBindings.boundContext(globals);
         MLuaTaskSchedulerView ownerScheduler = context != null ? context.scheduler() : this.scheduler;
         this.ensureWaiterCapacity(ownerKey);
         waiter = new WaitEntry(globals, thread, ownerKey, side, context, ownerScheduler);
         this.waiters.add(waiter);
      }
      try {
         return globals.yield(LuaValue.NONE);
      } catch (RuntimeException | Error failure) {
         synchronized (this) {
            this.waiters.remove(waiter);
         }
         throw failure;
      }
   }

    public void Destroy() {
       synchronized(this) {
          this.clearListeners();
          this.waiters.clear();
       }
    }

   /** Permanently closes an addon signal after its registry definition is removed. */
   public void destroyPermanently() {
      synchronized (this) {
         if (this.permanentlyDestroyed) return;
         this.permanentlyDestroyed = true;
         this.clearListeners();
         this.waiters.clear();
      }
   }

   private void ensureOpen() {
      if (this.permanentlyDestroyed) {
         throw new LuaError("[MLua] This signal was unregistered and is no longer active.");
      }
   }

   public void destroyOwned(String ownerKey) {
      if (ownerKey == null) {
         return;
      }

      synchronized(this) {
         this.removeListeners(entry -> ownerKey.equals(entry.ownerKey()));
         this.waiters.removeIf(entry -> ownerKey.equals(entry.ownerKey()));
      }
   }

   public void destroyOwned(String ownerKey, Side side) {
      if (ownerKey == null || side == null) return;
      synchronized (this) {
         this.removeListeners(entry -> ownerKey.equals(entry.ownerKey()) && matchesCleanupSide(entry.side(), side));
         this.waiters.removeIf(entry -> ownerKey.equals(entry.ownerKey()) && matchesCleanupSide(entry.side(), side));
      }
   }

   private void destroyOwned(MLuaRuntimeSession session, String ownerKey, Side side) {
      synchronized(this) {
         this.removeListeners(entry -> ownerKey.equals(entry.ownerKey()) && belongsTo(entry.context(), entry.scheduler() != null ? entry.scheduler() : this.scheduler, session)
            && matchesSessionSide(entry.side(), entry.context(), side));
         this.waiters.removeIf(entry -> ownerKey.equals(entry.ownerKey()) && belongsTo(entry.context(), entry.scheduler() != null ? entry.scheduler() : this.scheduler, session)
            && matchesSessionSide(entry.side(), entry.context(), side));
      }
   }

   public void destroySide(Side side) {
      if (side == null) return;
      if (side == Side.BOTH) {
         this.Destroy();
         return;
      }
      synchronized (this) {
         this.removeListeners(entry -> matchesCleanupSide(entry.side(), side));
         this.waiters.removeIf(entry -> matchesCleanupSide(entry.side(), side));
      }
   }

   private void destroySessionSide(MLuaRuntimeSession session, Side side) {
      synchronized(this) {
         this.removeListeners(entry -> belongsTo(entry.context(), entry.scheduler() != null ? entry.scheduler() : this.scheduler, session)
            && matchesSessionSide(entry.side(), entry.context(), side));
         this.waiters.removeIf(entry -> belongsTo(entry.context(), entry.scheduler() != null ? entry.scheduler() : this.scheduler, session)
            && matchesSessionSide(entry.side(), entry.context(), side));
      }
   }

   private static boolean belongsTo(MLuaContext context, MLuaTaskSchedulerView scheduler, MLuaRuntimeSession session) {
      return (context != null && context.session() == session) || session.ownsScheduler(scheduler);
   }

   private static boolean matchesSessionSide(Side registeredSide, MLuaContext context, Side requestedSide) {
      if (requestedSide == Side.BOTH) return true;
      Side actualSide = registeredSide != null ? registeredSide : context == null ? null : context.side();
      return actualSide == null || actualSide == Side.BOTH || actualSide == requestedSide;
   }

   private static boolean matchesCleanupSide(Side registeredSide, Side requestedSide) {
      return requestedSide == Side.BOTH || registeredSide == null || registeredSide == Side.BOTH
         || registeredSide == requestedSide;
   }

   private void addListener(ListenerEntry entry) {
      synchronized (this) {
         this.ensureOpen();
         this.ensureListenerCapacity(entry.ownerKey());
         this.addListenerLocked(entry);
      }
   }

   private void removeListeners(java.util.function.Predicate<ListenerEntry> predicate) {
      this.listeners.removeIf(entry -> {
         if (!predicate.test(entry)) return false;
         entry.connection().markDisconnected();
         this.removeOwnerCount(entry.ownerKey());
         return true;
      });
   }

   private void addListenerLocked(ListenerEntry entry) {
      this.listeners.add(entry);
      if (entry.ownerKey() != null) {
         this.listenerCountsByOwner.merge(entry.ownerKey(), 1, Integer::sum);
      }
   }

   private void removeListener(ListenerEntry entry) {
      if (entry != null && this.listeners.remove(entry)) this.removeOwnerCount(entry.ownerKey());
   }

   private void removeOwnerCount(String ownerKey) {
      if (ownerKey == null) return;
      int remaining = this.listenerCountsByOwner.getOrDefault(ownerKey, 0) - 1;
      if (remaining <= 0) this.listenerCountsByOwner.remove(ownerKey);
      else this.listenerCountsByOwner.put(ownerKey, remaining);
   }

   private void clearListeners() {
      this.listeners.forEach(entry -> entry.connection().markDisconnected());
      this.listeners.clear();
      this.listenerCountsByOwner.clear();
   }

   private void ensureListenerCapacity(String ownerKey) {
      if (this.listeners.size() >= MAX_LISTENERS_PER_SIGNAL) {
         throw new LuaError("[MLua] Signal listener limit reached (" + MAX_LISTENERS_PER_SIGNAL + " per signal).");
      }
      if (ownerKey != null) {
         int owned = this.listenerCountsByOwner.getOrDefault(ownerKey, 0);
         if (owned >= MAX_LISTENERS_PER_OWNER) {
            throw new LuaError("[MLua] Addon '" + ownerKey + "' reached the signal listener limit ("
               + MAX_LISTENERS_PER_OWNER + " per signal).");
         }
      }
   }

   private void ensureWaiterCapacity(String ownerKey) {
      if (this.waiters.size() >= MAX_WAITERS_PER_SIGNAL) {
         throw new LuaError("[MLua] Signal waiter limit reached (" + MAX_WAITERS_PER_SIGNAL + " per signal).");
      }
      if (ownerKey != null) {
         long owned = this.waiters.stream().filter(entry -> ownerKey.equals(entry.ownerKey())).count();
         if (owned >= MAX_WAITERS_PER_OWNER) {
            throw new LuaError("[MLua] Addon '" + ownerKey + "' reached the signal waiter limit ("
               + MAX_WAITERS_PER_OWNER + " per signal).");
         }
      }
   }

   public synchronized boolean isEmpty() {
      return this.listeners.isEmpty() && this.waiters.isEmpty();
   }

   public void fire(Varargs args) {
      this.fire(args, null);
   }

   /** Fires only listeners registered by the given runtime side. */
   public void fireOnSide(Side side, Varargs args) {
      this.fire(args, side);
   }

   public void fireOnSession(MLuaRuntimeSession session, Side side, Varargs args) {
      this.fire(args, side, session);
   }

   /**
    * Fires listeners directly on the caller thread. Use for lifecycle callbacks that run on a
    * Minecraft owner thread and may synchronously call back into that same world. Starting a
    * Lua coroutine here would move the listener to a worker while the owner thread waits for it,
    * making synchronous world mutations re-enter the Lua runtime from two threads.
    */
   public void fireInlineOnSession(MLuaRuntimeSession session, Side side, Varargs args) {
      this.fire(args, side, session, true);
   }

   private void fire(Varargs args, Side side) {
      this.fire(args, side, null);
   }

   private void fire(Varargs args, Side side, MLuaRuntimeSession session) {
      this.fire(args, side, session, false);
   }

   private void fire(Varargs args, Side side, MLuaRuntimeSession session, boolean inline) {
      this.ensureOpen();
      this.resumeWaitingThread(args, side, session);

      for (ListenerEntry entry : this.listeners) {
         if (!matchesSide(entry.side(), side)) continue;
         if (!matchesSession(entry.context(), entry.scheduler() != null ? entry.scheduler() : this.scheduler, session)) continue;
         try {
              if (!entry.connection().connected()) continue;
              this.invokeListener(entry, args, inline);
           } catch (Exception e) {
              synchronized(this) {
                 this.removeListeners(candidate -> candidate == entry);
              }
              reportFailure(entry.context(), entry.scheduler() != null ? entry.scheduler() : this.scheduler,
                 "Signal listener crashed and was disconnected", e);
           }
        }
   }

   public boolean fireCancellable(Varargs args) {
      return this.fireCancellable(args, null);
   }

   /** Fires only listeners registered by the given runtime side. */
   public boolean fireCancellableOnSide(Side side, Varargs args) {
      return this.fireCancellable(args, side);
   }

   public boolean fireCancellableOnSession(MLuaRuntimeSession session, Side side, Varargs args) {
      return this.fireCancellable(args, side, session);
   }

   private boolean fireCancellable(Varargs args, Side side) {
      return this.fireCancellable(args, side, null);
   }

   private boolean fireCancellable(Varargs args, Side side, MLuaRuntimeSession session) {
      this.ensureOpen();
      this.resumeWaitingThread(args, side, session);

      boolean cancelled = false;
      for (ListenerEntry entry : this.listeners) {
         if (!matchesSide(entry.side(), side)) continue;
         if (!matchesSession(entry.context(), entry.scheduler() != null ? entry.scheduler() : this.scheduler, session)) continue;
         try {
            if (!entry.connection().connected()) continue;
            Varargs result = this.invokeListener(entry, args);
            if (result.arg1().isboolean() && !result.arg1().toboolean()) {
               cancelled = true;
            }
         } catch (Exception e) {
            synchronized(this) {
               this.removeListeners(candidate -> candidate == entry);
            }
            reportFailure(entry.context(), entry.scheduler() != null ? entry.scheduler() : this.scheduler,
               "Signal listener crashed and was disconnected", e);
         }
      }
      return cancelled;
   }

   private void resumeWaitingThread(Varargs args, Side side) {
      this.resumeWaitingThread(args, side, null);
   }

   private void resumeWaitingThread(Varargs args, Side side, MLuaRuntimeSession session) {
      List<WaitEntry> waiting;
      synchronized (this) {
         if (side == null && session == null) {
            waiting = List.copyOf(this.waiters);
            this.waiters.clear();
         } else {
            waiting = this.waiters.stream().filter(entry -> matchesSide(entry.side(), side)
                  && matchesSession(entry.context(), entry.scheduler(), session)).toList();
            this.waiters.removeIf(entry -> matchesSide(entry.side(), side)
                  && matchesSession(entry.context(), entry.scheduler(), session));
         }
      }

      for (WaitEntry entry : waiting) {
         try {
            if (entry.scheduler() != null) {
               entry.scheduler().scheduleThread(entry.globals(), 1L, entry.thread(), args,
                  entry.ownerKey(), entry.side());
               continue;
            }
            Runnable resume = () -> {
               if (entry.thread() != null) {
                  Varargs result = entry.thread().resume(args);
                  if (!result.arg1().toboolean()) {
                     throw new LuaError(result.arg(2).tojstring());
                  }
                  if (entry.scheduler() != null) {
                     entry.scheduler().scheduleResumableThread(entry.globals(), entry.thread(), result, entry.ownerKey(), entry.side());
                  }
               }
            };
            if (entry.context() != null) {
               MLuaLuaBindings.withContext(entry.context(), () -> MLuaCallbackBudget.run(
                  entry.context(), entry.globals(), () -> { resume.run(); return null; }));
            } else {
               synchronized (entry.globals()) {
                  MLuaCallbackBudget.run(null, entry.globals(), () -> { resume.run(); return null; });
               }
            }
         } catch (Exception e) {
            MLuaLogger.LOGGER.error("Signal:Wait resume failed: {}", e.getMessage());
            reportFailure(entry.context(), entry.scheduler(), "Signal waiter failed and was removed", e);
         }
      }
   }

   private static boolean matchesSession(MLuaContext context, MLuaTaskSchedulerView scheduler, MLuaRuntimeSession session) {
      if (session == null) return true;
      if (context != null && context.session() == session) return true;
      return session.ownsScheduler(scheduler);
   }

   private static void reportFailure(MLuaContext context, MLuaTaskSchedulerView scheduler, String summary, Throwable error) {
      if (context != null) {
         context.reportRuntimeFailure(summary, error);
      } else if (scheduler instanceof MLuaTaskScheduler runtimeScheduler) {
         runtimeScheduler.reportRuntimeFailure(summary, error);
      } else {
         MLuaLogger.LOGGER.error(summary, error);
      }
   }

   private static boolean matchesSide(Side listenerSide, Side eventSide) {
      return eventSide == null || listenerSide == null || listenerSide == Side.BOTH || listenerSide == eventSide;
   }

   private Varargs invokeListener(ListenerEntry entry, Varargs args) {
      return this.invokeListener(entry, args, false);
   }

   private Varargs invokeListener(ListenerEntry entry, Varargs args, boolean inline) {
      LuaValue listener = entry.listener();
      Globals g = entry.globals() != null ? entry.globals() : this.globals;
      MLuaTaskSchedulerView scheduler = entry.context() != null ? entry.context().scheduler() : entry.scheduler() != null ? entry.scheduler() : this.scheduler;
      if (entry.context() != null) {
         return MLuaLuaBindings.withContext(entry.context(), () -> MLuaCallbackBudget.run(
            entry.context(), g, () -> invokeListenerLocked(entry, listener, g, scheduler, args, inline)));
      }
      Object executionLock = g != null ? g : this;
      synchronized (executionLock) {
         return MLuaCallbackBudget.run(entry.context(), g,
            () -> invokeListenerLocked(entry, listener, g, scheduler, args, inline));
      }
   }

   private Varargs invokeListenerLocked(ListenerEntry entry, LuaValue listener, Globals globals,
                                        MLuaTaskSchedulerView scheduler, Varargs args, boolean inline) {
      if (inline) return listener.invoke(args);
      if (globals != null && scheduler != null) {
         LuaThread thread = new LuaThread(globals, listener);
         Varargs result = thread.resume(args);
         if (!result.arg1().toboolean()) throw new LuaError(result.arg(2).tojstring());
         scheduler.scheduleResumableThread(globals, thread, result, entry.ownerKey(), entry.side());
         return result;
      }
      return listener.invoke(args);
   }

   private record ListenerEntry(LuaValue listener, String ownerKey, Side side, Globals globals,
                                MLuaTaskSchedulerView scheduler, MLuaContext context,
                                MLuaConnection connection) {
   }

   private record WaitEntry(Globals globals, LuaThread thread, String ownerKey, Side side, MLuaContext context, MLuaTaskSchedulerView scheduler) {
   }

}
