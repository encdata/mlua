package dev.mlua.core.scheduler;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.engine.MLuaLuaBindings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

public final class MLuaTaskScheduler implements MLuaTaskSchedulerView {
   public static final int MAX_PENDING_TASKS = 16_384;
   public static final int MAX_PENDING_TASKS_PER_OWNER = 2_048;
   public static final LuaValue TASK_WAIT_TOKEN = LuaValue.valueOf("__mlua_task_wait__");
   private static final Comparator<MLuaScheduledTask> TASK_ORDER = Comparator
      .comparingLong((MLuaScheduledTask task) -> task.dueTick)
      .thenComparingLong(task -> task.sequence);
   private final PriorityQueue<MLuaScheduledTask> tasks = new PriorityQueue<>(TASK_ORDER);
   private final Map<TaskOwner, Integer> pendingByOwner = new HashMap<>();
   /** Due tasks are kept here until execution finishes so owner teardown can cancel them. */
   private final Set<MLuaScheduledTask> inFlight = new HashSet<>();
   private long currentTick;
   private long nextTaskSequence;
   private volatile BiConsumer<String, Throwable> failureReporter;

   public void setFailureReporter(BiConsumer<String, Throwable> failureReporter) {
      this.failureReporter = failureReporter;
   }

   public void reportRuntimeFailure(String summary, Throwable throwable) {
      BiConsumer<String, Throwable> reporter = this.failureReporter;
      if (reporter != null) {
         try {
            reporter.accept(summary, throwable);
            return;
         } catch (Throwable reporterFailure) {
            MLuaLogger.LOGGER.error("MLua runtime failure reporter itself failed", reporterFailure);
         }
      }
      MLuaLogger.LOGGER.error(summary, throwable);
   }

   public synchronized void spawn(Globals globals, LuaValue function, Varargs args) {
      this.schedule(globals, 0L, function, args);
   }

   public synchronized void defer(Globals globals, LuaValue function, Varargs args) {
      this.schedule(globals, 1L, function, args);
   }

   public synchronized void delay(Globals globals, long ticks, LuaValue function, Varargs args) {
      this.schedule(globals, ticks, function, args);
   }

   public Varargs waitForTicks(Globals globals, long ticks) {
      return globals.yield(LuaValue.varargsOf(new LuaValue[]{TASK_WAIT_TOKEN, LuaValue.valueOf((double)Math.max(1L, ticks))}));
   }

   public synchronized void scheduleThread(Globals globals, long ticks, LuaThread thread, Varargs resumeArgs) {
      MLuaContext context = this.contextFor(globals);
      this.enqueue(new MLuaScheduledTask(this.dueTick(Math.max(1L, ticks)), this.nextTaskSequence++, globals, thread, (LuaValue)null,
         resumeArgs, context));
   }

   @Override
   public synchronized void scheduleThread(Globals globals, long ticks, LuaThread thread, Varargs resumeArgs,
                                           String ownerModId, Side side) {
      MLuaContext context = this.contextFor(globals);
      if (context != null) {
         if (ownerModId == null) ownerModId = context.ownerModId();
         if (side == null) side = context.side();
      }
      this.enqueue(new MLuaScheduledTask(this.dueTick(Math.max(1L, ticks)), this.nextTaskSequence++, globals, thread, (LuaValue)null,
         resumeArgs, context, ownerModId, side));
   }

   public synchronized void scheduleResumableThread(Globals globals, LuaThread thread, Varargs result) {
      this.scheduleResumableThread(globals, thread, result, this.contextFor(globals), null, null);
   }

   @Override
   public synchronized void scheduleResumableThread(Globals globals, LuaThread thread, Varargs result, String ownerModId, Side side) {
      MLuaContext context = this.contextFor(globals);
      this.scheduleResumableThread(globals, thread, result, context, ownerModId, side);
   }

   private void scheduleResumableThread(Globals globals, LuaThread thread, Varargs result, MLuaContext context, String ownerModId, Side side) {
      if (context != null) {
         if (ownerModId == null) ownerModId = context.ownerModId();
         if (side == null) side = context.side();
      }
      if ("dead".equals(thread.getStatus())) {
         return;
      }

      if (!"suspended".equals(thread.getStatus())) {
         return;
      }

      if (result.narg() >= 3 && result.arg(2).eq_b(TASK_WAIT_TOKEN) && result.arg(3).isnumber()) {
         long ticks = Math.max(1L, result.arg(3).tolong());
         this.enqueue(new MLuaScheduledTask(this.dueTick(ticks), this.nextTaskSequence++, globals, thread,
            (LuaValue)null, LuaValue.valueOf((double)ticks), context, ownerModId, side));
      } else if (result.narg() >= 2 && result.arg(2).isnumber()) {
         long ticks = Math.max(1L, result.arg(2).tolong());
         this.enqueue(new MLuaScheduledTask(this.dueTick(ticks), this.nextTaskSequence++, globals, thread,
            (LuaValue)null, LuaValue.valueOf((double)ticks), context, ownerModId, side));
      } else {
         Varargs resumeArgs = result.narg() >= 2 ? result.subargs(2) : LuaValue.NONE;
         this.enqueue(new MLuaScheduledTask(this.dueTick(1L), this.nextTaskSequence++, globals, thread,
            (LuaValue)null, resumeArgs, context, ownerModId, side));
      }
   }

   private void schedule(Globals globals, long ticks, LuaValue function, Varargs args) {
      if (!function.isfunction()) {
         throw new LuaError("task expected a function");
      } else {
         long delay = Math.max(0L, ticks);
         this.enqueue(new MLuaScheduledTask(this.dueTick(delay), this.nextTaskSequence++, globals,
            (LuaThread)null, function, args, this.contextFor(globals)));
      }
   }

   private long dueTick(long delay) {
      return delay > Long.MAX_VALUE - this.currentTick ? Long.MAX_VALUE : this.currentTick + delay;
   }

   private void enqueue(MLuaScheduledTask task) {
      TaskOwner owner = new TaskOwner(task.ownerModId, task.side);
      if (this.tasks.size() >= MAX_PENDING_TASKS) {
         throw new LuaError("[MLua] Pending task limit reached (" + MAX_PENDING_TASKS + ")");
      }
      if (this.pendingByOwner.getOrDefault(owner, 0) >= MAX_PENDING_TASKS_PER_OWNER) {
         throw new LuaError("[MLua] Pending task limit reached for addon '"
            + (task.ownerModId == null ? "<unknown>" : task.ownerModId) + "' (" + MAX_PENDING_TASKS_PER_OWNER + ")");
      }
      this.tasks.add(task);
      this.pendingByOwner.merge(owner, 1, Integer::sum);
   }

   private void release(MLuaScheduledTask task) {
      TaskOwner owner = new TaskOwner(task.ownerModId, task.side);
      int remaining = this.pendingByOwner.getOrDefault(owner, 0) - 1;
      if (remaining <= 0) this.pendingByOwner.remove(owner);
      else this.pendingByOwner.put(owner, remaining);
   }

   private int removeTasks(Predicate<MLuaScheduledTask> predicate) {
      int removed = 0;
      Iterator<MLuaScheduledTask> iterator = this.tasks.iterator();
      while (iterator.hasNext()) {
         MLuaScheduledTask task = iterator.next();
         if (!predicate.test(task)) continue;
         task.cancelled = true;
         iterator.remove();
         this.release(task);
         removed++;
      }
      for (MLuaScheduledTask task : this.inFlight) {
         if (predicate.test(task) && !task.cancelled) {
            task.cancelled = true;
            removed++;
         }
      }
      return removed;
   }

   public synchronized void Destroy() {
      this.inFlight.forEach(task -> task.cancelled = true);
      this.inFlight.clear();
      this.tasks.clear();
      this.pendingByOwner.clear();
    }

   public synchronized int removeOwned(String ownerModId) {
      if (ownerModId == null) return 0;
      return this.removeTasks(task -> ownerModId.equals(task.ownerModId));
   }

   public synchronized int removeOwned(Side side, String ownerModId) {
      if (side == null || ownerModId == null) return 0;
      return this.removeTasks(task -> sideMatches(task.side, side) && ownerModId.equals(task.ownerModId));
   }

   public synchronized int removeSide(Side side) {
      if (side == null || side == Side.BOTH) return 0;
      return this.removeTasks(task -> sideMatches(task.side, side));
    }

   private static boolean sideMatches(Side taskSide, Side requestedSide) {
      return requestedSide == Side.BOTH || taskSide == Side.BOTH || taskSide == requestedSide;
   }

   public synchronized int removeScripts(Set<Path> scriptPaths) {
      if (scriptPaths == null || scriptPaths.isEmpty()) return 0;
      return this.removeTasks(task -> task.context != null && scriptPaths.contains(task.context.script().path()));
   }

   public synchronized int pendingTaskCount() { return this.tasks.size(); }

   public synchronized int pendingTaskCount(String ownerModId, Side side) {
      int count = 0;
      for (Map.Entry<TaskOwner, Integer> entry : this.pendingByOwner.entrySet()) {
         if (java.util.Objects.equals(ownerModId, entry.getKey().ownerModId())
            && (side == null || side == Side.BOTH || sideMatches(entry.getKey().side(), side))) {
            count += entry.getValue();
         }
      }
      return count;
   }

   public void tick() {
      List<MLuaScheduledTask> ready;
      synchronized (this) {
         if (this.currentTick < Long.MAX_VALUE) this.currentTick++;
         if (this.tasks.isEmpty() || this.tasks.peek().dueTick > this.currentTick) return;
         ready = new ArrayList<>();
         while (!this.tasks.isEmpty() && this.tasks.peek().dueTick <= this.currentTick) {
            MLuaScheduledTask task = this.tasks.remove();
            this.release(task);
            this.inFlight.add(task);
            ready.add(task);
         }
      }

      // LuaThread.resume waits for the coroutine thread. Do not hold the scheduler
      // monitor here: resumed Lua code is allowed to schedule or cancel tasks.
      for (MLuaScheduledTask task : ready) {
         synchronized (this) {
            if (task.cancelled) {
               this.inFlight.remove(task);
               continue;
            }
         }
         try {
            this.runTask(task);
         } catch (LuaError failure) {
            MLuaLogger.LOGGER.error("Scheduled MLua task failed and was removed: {}", failure.getMessage());
            this.reportRuntimeFailure("Scheduled MLua task crashed and was removed", failure);
         } catch (RuntimeException failure) {
            MLuaLogger.LOGGER.error("Scheduled MLua task failed unexpectedly and was removed", failure);
            this.reportRuntimeFailure("Scheduled MLua task failed unexpectedly and was removed", failure);
         } finally {
            synchronized (this) {
               this.inFlight.remove(task);
            }
         }
      }
   }

   private void runTask(MLuaScheduledTask task) {
      if (task.context != null) {
         MLuaLuaBindings.withContext(task.context, () -> { runTaskLocked(task); return null; });
      } else {
         synchronized (task.globals) {
            MLuaLuaBindings.configureContextGlobals(task.globals, null);
            runTaskLocked(task);
         }
      }
   }

   private void runTaskLocked(MLuaScheduledTask task) {
      // A due task can be waiting for its Lua context while the owning script is
      // reloaded. Recheck after taking the context lock so cleanup wins that race.
      if (task.cancelled) return;
      LuaThread thread = task.thread != null ? task.thread : new LuaThread(task.globals, task.function);
      Varargs resumeArgs = task.resumeArgs == null ? LuaValue.NONE : task.resumeArgs;
      Varargs result = MLuaCallbackBudget.run(task.context, task.globals,
         () -> thread.resume(resumeArgs));
      if (!result.arg1().toboolean()) throw new LuaError(result.arg(2).tojstring());
      synchronized (this) {
         if (!task.cancelled) {
            this.scheduleResumableThread(task.globals, thread, result, task.context, task.ownerModId, task.side);
         }
      }
   }

   private MLuaContext contextFor(Globals globals) {
      return MLuaLuaBindings.boundContext(globals);
   }

   private record TaskOwner(String ownerModId, Side side) { }
}
