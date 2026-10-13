package dev.mlua.core.signal;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.core.scheduler.MLuaTaskScheduler;
import dev.mlua.common.enums.Side;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class MLuaSignal {
   private static final Map<MLuaSignal, Boolean> ALL_SIGNALS = java.util.Collections.synchronizedMap(new WeakHashMap<>());
   private final List<ListenerEntry> listeners = new ArrayList<>();
   private volatile Globals globals;
   private volatile MLuaTaskSchedulerView scheduler;
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
      if (!listener.isfunction()) {
         throw new LuaError("Signal:Connect expected a function");
      } else {
         ListenerEntry entry = new ListenerEntry(listener, ownerKey, side, ownerGlobals, ownerScheduler, context);
         synchronized(this) {
            this.listeners.add(entry);
         }
         return new MLuaConnection(() -> {
            synchronized(this) {
               this.listeners.remove(entry);
            }
         });
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
      final ListenerEntry[] ref = new ListenerEntry[1];
      final MLuaConnection connection = new MLuaConnection(() -> {
         synchronized(this) {
            if (ref[0] != null) {
               this.listeners.remove(ref[0]);
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
         ref[0] = new ListenerEntry(wrapper, ownerKey, side, ownerGlobals, ownerScheduler, context);
         this.listeners.add(ref[0]);
      }
      return connection;
   }

   public Varargs wait(Globals globals) {
      return this.wait(globals, null, null);
   }

   public Varargs wait(Globals globals, String ownerKey, Side side) {
      synchronized (this) {
         MLuaContext context = MLuaLuaBindings.boundContext(globals);
         MLuaTaskSchedulerView ownerScheduler = context != null ? context.scheduler() : this.scheduler;
         this.waiters.add(new WaitEntry(globals, globals.running, ownerKey, side, context, ownerScheduler));
      }
      return globals.yield(LuaValue.NONE);
   }

    public void Destroy() {
       synchronized(this) {
          this.listeners.clear();
          this.waiters.clear();
       }
    }

   public void destroyOwned(String ownerKey) {
      if (ownerKey == null) {
         return;
      }

      synchronized(this) {
         this.listeners.removeIf(entry -> ownerKey.equals(entry.ownerKey()));
         this.waiters.removeIf(entry -> ownerKey.equals(entry.ownerKey()));
      }
   }

   public void destroyOwned(String ownerKey, Side side) {
      if (ownerKey == null || side == null) return;
      synchronized (this) {
         this.listeners.removeIf(entry -> ownerKey.equals(entry.ownerKey()) && (side == Side.BOTH || side == entry.side()));
         this.waiters.removeIf(entry -> ownerKey.equals(entry.ownerKey()) && (side == Side.BOTH || side == entry.side()));
      }
   }

   private void destroyOwned(MLuaRuntimeSession session, String ownerKey, Side side) {
      synchronized(this) {
         this.listeners.removeIf(entry -> ownerKey.equals(entry.ownerKey()) && belongsTo(entry.context(), entry.scheduler() != null ? entry.scheduler() : this.scheduler, session)
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
         this.listeners.removeIf(entry -> side == entry.side());
         this.waiters.removeIf(entry -> side == entry.side());
      }
   }

   private void destroySessionSide(MLuaRuntimeSession session, Side side) {
      synchronized(this) {
         this.listeners.removeIf(entry -> belongsTo(entry.context(), entry.scheduler() != null ? entry.scheduler() : this.scheduler, session)
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

   private void fire(Varargs args, Side side) {
      this.fire(args, side, null);
   }

   private void fire(Varargs args, Side side, MLuaRuntimeSession session) {
      this.resumeWaitingThread(args, side, session);

      for (ListenerEntry entry : this.listenerSnapshot()) {
         if (!matchesSide(entry.side(), side)) continue;
         if (!matchesSession(entry.context(), entry.scheduler() != null ? entry.scheduler() : this.scheduler, session)) continue;
         try {
              this.invokeListener(entry, args);
           } catch (Exception e) {
              synchronized(this) {
                 this.listeners.remove(entry);
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
      this.resumeWaitingThread(args, side, session);

      boolean cancelled = false;
      for (ListenerEntry entry : this.listenerSnapshot()) {
         if (!matchesSide(entry.side(), side)) continue;
         if (!matchesSession(entry.context(), entry.scheduler() != null ? entry.scheduler() : this.scheduler, session)) continue;
         try {
            Varargs result = this.invokeListener(entry, args);
            if (result.arg1().isboolean() && !result.arg1().toboolean()) {
               cancelled = true;
            }
         } catch (Exception e) {
            synchronized(this) {
               this.listeners.remove(entry);
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
            if (entry.context() != null) MLuaLuaBindings.bindContext(entry.globals(), entry.context());
            if (entry.thread() != null) {
               Varargs result = entry.thread().resume(args);
               if (entry.scheduler() != null) {
                  entry.scheduler().scheduleResumableThread(entry.globals(), entry.thread(), result, entry.ownerKey(), entry.side());
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
      LuaValue listener = entry.listener();
      Globals g = entry.globals() != null ? entry.globals() : this.globals;
      MLuaTaskSchedulerView scheduler = entry.context() != null ? entry.context().scheduler() : entry.scheduler() != null ? entry.scheduler() : this.scheduler;
      if (g != null && scheduler != null) {
         if (entry.context() != null) MLuaLuaBindings.bindContext(g, entry.context());
         LuaThread thread = new LuaThread(g, listener);
         Varargs result = thread.resume(args);
         if (!result.arg1().toboolean()) {
            throw new LuaError(result.arg(2).tojstring());
         }

         scheduler.scheduleResumableThread(g, thread, result, entry.ownerKey(), entry.side());
         return result;
      } else {
         return listener.invoke(args);
      }
   }

   private record ListenerEntry(LuaValue listener, String ownerKey, Side side, Globals globals, MLuaTaskSchedulerView scheduler, MLuaContext context) {
   }

   private record WaitEntry(Globals globals, LuaThread thread, String ownerKey, Side side, MLuaContext context, MLuaTaskSchedulerView scheduler) {
   }

   private List<ListenerEntry> listenerSnapshot() {
      synchronized(this) {
         return List.copyOf(this.listeners);
      }
   }
}
