package dev.mlua.core.scheduler;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import dev.mlua.common.enums.Side;

public interface MLuaTaskSchedulerView {
   void spawn(Globals globals, LuaValue function, Varargs args);

   void defer(Globals globals, LuaValue function, Varargs args);

   void delay(Globals globals, long ticks, LuaValue function, Varargs args);

   Varargs waitForTicks(Globals globals, long ticks);

   void scheduleThread(Globals globals, long ticks, LuaThread thread, Varargs resumeArgs);

   void scheduleResumableThread(Globals globals, LuaThread thread, Varargs result);

   default void scheduleResumableThread(Globals globals, LuaThread thread, Varargs result, String ownerModId, Side side) {
      scheduleResumableThread(globals, thread, result);
   }
}
