package dev.mlua.core.scheduler;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;

final class MLuaScheduledTask {
   long ticksRemaining;
   final Globals globals;
   final LuaThread thread;
   final LuaValue function;

   MLuaScheduledTask(long ticksRemaining, Globals globals, LuaThread thread, LuaValue function) {
      this.ticksRemaining = ticksRemaining;
      this.globals = globals;
      this.thread = thread;
      this.function = function;
   }
}
