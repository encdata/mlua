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
import java.util.Iterator;
import java.util.List;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.BiConsumer;

public final class MLuaTaskScheduler implements MLuaTaskSchedulerView {
   public static final LuaValue TASK_WAIT_TOKEN = LuaValue.valueOf("__mlua_task_wait__");
   private final List<MLuaScheduledTask> tasks = new ArrayList<>();
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
      this.tasks.add(new MLuaScheduledTask(Math.max(1L, ticks), globals, thread, (LuaValue)null, resumeArgs, this.contextFor(globals)));
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
      if ("dead".equals(thread.getStatus())) {
         return;
      }

      if (!"suspended".equals(thread.getStatus())) {
         return;
      }

      if (result.narg() >= 3 && result.arg(2).eq_b(TASK_WAIT_TOKEN) && result.arg(3).isnumber()) {
         long ticks = Math.max(1L, result.arg(3).tolong());
         this.tasks.add(new MLuaScheduledTask(ticks, globals, thread, (LuaValue)null, LuaValue.valueOf((double)ticks), context, ownerModId, side));
      } else if (result.narg() >= 2 && result.arg(2).isnumber()) {
         long ticks = Math.max(1L, result.arg(2).tolong());
         this.tasks.add(new MLuaScheduledTask(ticks, globals, thread, (LuaValue)null, LuaValue.valueOf((double)ticks), context, ownerModId, side));
      } else {
         Varargs resumeArgs = result.narg() >= 2 ? result.subargs(2) : LuaValue.NONE;
         this.tasks.add(new MLuaScheduledTask(1L, globals, thread, (LuaValue)null, resumeArgs, context, ownerModId, side));
      }
   }

   private void schedule(Globals globals, long ticks, LuaValue function, Varargs args) {
      if (!function.isfunction()) {
         throw new LuaError("task expected a function");
      } else {
         this.tasks.add(new MLuaScheduledTask(Math.max(0L, ticks), globals, (LuaThread)null, function, args, this.contextFor(globals)));
      }
   }

    public synchronized void Destroy() {
       this.tasks.clear();
    }

   public synchronized int removeOwned(String ownerModId) {
      if (ownerModId == null) return 0;
      int before = this.tasks.size();
      this.tasks.removeIf(task -> ownerModId.equals(task.ownerModId));
      return before - this.tasks.size();
   }

   public synchronized int removeOwned(Side side, String ownerModId) {
      if (side == null || ownerModId == null) return 0;
      int before = this.tasks.size();
      this.tasks.removeIf(task -> side == task.side && ownerModId.equals(task.ownerModId));
      return before - this.tasks.size();
   }

    public synchronized int removeSide(Side side) {
      if (side == null || side == Side.BOTH) return 0;
      int before = this.tasks.size();
      this.tasks.removeIf(task -> side == task.side);
      return before - this.tasks.size();
    }

   public synchronized int removeScripts(Set<Path> scriptPaths) {
      if (scriptPaths == null || scriptPaths.isEmpty()) return 0;
      int before = this.tasks.size();
      this.tasks.removeIf(task -> task.context != null && scriptPaths.contains(task.context.script().path()));
      return before - this.tasks.size();
   }

    public synchronized void tick() {
      Iterator<MLuaScheduledTask> iterator = this.tasks.iterator();
      List<MLuaScheduledTask> ready = new ArrayList();

      while(iterator.hasNext()) {
         MLuaScheduledTask task = (MLuaScheduledTask)iterator.next();
         --task.ticksRemaining;
         if (task.ticksRemaining <= 0L) {
            iterator.remove();
            ready.add(task);
         }
      }

      Iterator var8 = ready.iterator();

      while(var8.hasNext()) {
         MLuaScheduledTask task = (MLuaScheduledTask)var8.next();

         try {
            this.runTask(task);
         } catch (LuaError var6) {
            MLuaLogger.LOGGER.error("Scheduled MLua task failed and was removed: {}", var6.getMessage());
            this.reportRuntimeFailure("Scheduled MLua task crashed and was removed", var6);
         } catch (RuntimeException var7) {
            MLuaLogger.LOGGER.error("Scheduled MLua task failed unexpectedly and was removed", var7);
            this.reportRuntimeFailure("Scheduled MLua task failed unexpectedly and was removed", var7);
         }
      }

   }

   private void runTask(MLuaScheduledTask task) {
      Object executionLock = task.context == null ? task.globals : task.context.executionLock();
      synchronized (executionLock) {
         MLuaLuaBindings.bindContext(task.globals, task.context);
         LuaThread thread = task.thread != null ? task.thread : new LuaThread(task.globals, task.function);
         Varargs resumeArgs = task.resumeArgs == null ? LuaValue.NONE : task.resumeArgs;
         Varargs result = thread.resume(resumeArgs);
         if (!result.arg1().toboolean()) {
            throw new LuaError(result.arg(2).tojstring());
         } else {
            this.scheduleResumableThread(task.globals, thread, result, task.context, task.ownerModId, task.side);

         }
      }
   }

   private MLuaContext contextFor(Globals globals) {
      return MLuaLuaBindings.boundContext(globals);
   }
}
