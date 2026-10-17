package dev.mlua.core.scheduler;

import dev.mlua.core.MLuaContext;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.common.enums.Side;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public final class MLuaTaskScheduler implements MLuaTaskSchedulerView {
   public static final int MAX_PENDING_TASKS = 16_384;
   public static final int MAX_PENDING_TASKS_PER_OWNER = 2_048;
   public static final LuaValue TASK_WAIT_TOKEN = LuaValue.valueOf("__mlua_task_wait__");
   private final List<MLuaScheduledTask> tasks = new ArrayList<>();
   private final Map<TaskOwner, Integer> pendingByOwner = new HashMap<>();
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
      this.enqueue(new MLuaScheduledTask(Math.max(1L, ticks), globals, thread, (LuaValue)null, resumeArgs, this.contextFor(globals)));
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
         this.enqueue(new MLuaScheduledTask(ticks, globals, thread, (LuaValue)null, LuaValue.valueOf((double)ticks), context, ownerModId, side));
      } else if (result.narg() >= 2 && result.arg(2).isnumber()) {
         long ticks = Math.max(1L, result.arg(2).tolong());
         this.enqueue(new MLuaScheduledTask(ticks, globals, thread, (LuaValue)null, LuaValue.valueOf((double)ticks), context, ownerModId, side));
      } else {
         Varargs resumeArgs = result.narg() >= 2 ? result.subargs(2) : LuaValue.NONE;
         this.enqueue(new MLuaScheduledTask(1L, globals, thread, (LuaValue)null, resumeArgs, context, ownerModId, side));
      }
   }

   private void schedule(Globals globals, long ticks, LuaValue function, Varargs args) {
      if (!function.isfunction()) {
         throw new LuaError("task expected a function");
      } else {
         this.enqueue(new MLuaScheduledTask(Math.max(0L, ticks), globals, (LuaThread)null, function, args, this.contextFor(globals)));
      }
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
         iterator.remove();
         this.release(task);
         removed++;
      }
      return removed;
   }

    public synchronized void Destroy() {
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
      List<MLuaScheduledTask> ready = new ArrayList<>();
      synchronized (this) {
         Iterator<MLuaScheduledTask> iterator = this.tasks.iterator();
         while (iterator.hasNext()) {
            MLuaScheduledTask task = iterator.next();
            --task.ticksRemaining;
            if (task.ticksRemaining <= 0L) {
               iterator.remove();
               this.release(task);
               ready.add(task);
            }
         }
      }

      // LuaThread.resume waits for the coroutine thread. Do not hold the scheduler
      // monitor here: resumed Lua code is allowed to schedule or cancel tasks.
      for (MLuaScheduledTask task : ready) {
         try {
            this.runTask(task);
         } catch (LuaError failure) {
            MLuaLogger.LOGGER.error("Scheduled MLua task failed and was removed: {}", failure.getMessage());
            this.reportRuntimeFailure("Scheduled MLua task crashed and was removed", failure);
         } catch (RuntimeException failure) {
            MLuaLogger.LOGGER.error("Scheduled MLua task failed unexpectedly and was removed", failure);
            this.reportRuntimeFailure("Scheduled MLua task failed unexpectedly and was removed", failure);
         }
      }

   }

   private void runTask(MLuaScheduledTask task) {
      if (task.context != null) {
         MLuaLuaBindings.withContext(task.context, () -> { runTaskLocked(task); return null; });
      } else {
         synchronized (task.globals) {
            MLuaLuaBindings.bindContext(task.globals, null);
            runTaskLocked(task);
         }
      }
   }

   private void runTaskLocked(MLuaScheduledTask task) {
      LuaThread thread = task.thread != null ? task.thread : new LuaThread(task.globals, task.function);
      Varargs resumeArgs = task.resumeArgs == null ? LuaValue.NONE : task.resumeArgs;
      Varargs result = thread.resume(resumeArgs);
      if (!result.arg1().toboolean()) throw new LuaError(result.arg(2).tojstring());
      this.scheduleResumableThread(task.globals, thread, result, task.context, task.ownerModId, task.side);
   }

   private MLuaContext contextFor(Globals globals) {
      return MLuaLuaBindings.boundContext(globals);
   }

   private record TaskOwner(String ownerModId, Side side) { }
}
