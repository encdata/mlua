package dev.mlua.modules.commands.Service;

import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.minecraft.command.MLuaCommandDefinition;
import dev.mlua.minecraft.command.MLuaCommandRegistry;
import java.util.Iterator;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaCommandsService extends MLuaServiceBase {
   public MLuaCommandsService(MLuaService.Context context) {
      super(context);
   }

   public String name() {
      return "Commands";
   }

   public LuaTable toLua() {
      LuaTable table = new LuaTable();
      table.set("__side", "Both");
      table.set("Register", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            String name = args.checkjstring(2);
            if (MLuaCommandsService.this.context.mlua().phase() != RegistrationPhase.COMMANDS) {
               throw new LuaError("[MLua] Cannot register command '" + name + "': commands must be registered during run_commands(ctx) / RegisterCommandsEvent.");
            } else {
               MLuaCommandDefinition definition = MLuaCommandRegistry.fromLua(name, args.checktable(3));
               if (MLuaCommandsService.this.context.mlua().isClient()) {
                  MLuaCommandRegistry.get().registerClientCallback(name, definition.clientCallback());
               } else {
                  MLuaCommandRegistry.get().register(definition);
               }

               return LuaValue.NONE;
            }
         }
      });
      table.set("GetRegisteredCommands", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaTable commands = new LuaTable();
            int index = 1;
            Iterator<String> iterator = MLuaCommandRegistry.get().names().iterator();

            while (iterator.hasNext()) {
               commands.set(index++, iterator.next());
            }

            return commands;
         }
      });
      table.set("Execute", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            if (MLuaCommandsService.this.context.mlua().server() == null) {
               throw new LuaError("[MLua] Commands:Execute requires an active server.");
            } else {
               String command = args.checkjstring(2);
               MLuaCommandsService.this.context.mlua().server().getCommands().performPrefixedCommand(MLuaCommandsService.this.context.mlua().server().createCommandSourceStack(), command);
               return LuaValue.valueOf(true);
            }
         }
      });
      return table;
   }
}
