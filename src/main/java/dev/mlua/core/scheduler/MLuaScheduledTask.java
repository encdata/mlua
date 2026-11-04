package dev.mlua.core.scheduler;

import dev.mlua.core.MLuaContext;
import dev.mlua.common.enums.Side;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

final class MLuaScheduledTask {
   final long dueTick;
   final long sequence;
   final Globals globals;
   final LuaThread thread;
   final LuaValue function;
   final Varargs resumeArgs;
   final MLuaContext context;
   final String ownerModId;
   final Side side;
   volatile boolean cancelled;

   MLuaScheduledTask(long dueTick, long sequence, Globals globals, LuaThread thread, LuaValue function,
                     Varargs resumeArgs, MLuaContext context) {
      this(dueTick, sequence, globals, thread, function, resumeArgs, context,
         context != null ? context.ownerModId() : null, context != null ? context.side() : null);
   }

   MLuaScheduledTask(long dueTick, long sequence, Globals globals, LuaThread thread, LuaValue function,
                     Varargs resumeArgs, MLuaContext context, String ownerModId, Side side) {
      this.dueTick = dueTick;
      this.sequence = sequence;
      this.globals = globals;
      this.thread = thread;
      this.function = function;
      this.resumeArgs = resumeArgs;
      this.context = context;
      this.ownerModId = ownerModId;
      this.side = side;
   }
}
