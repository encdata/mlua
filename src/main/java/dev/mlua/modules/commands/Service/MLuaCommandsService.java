package dev.mlua.modules.commands.Service;

import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.minecraft.command.MLuaCommandDefinition;
import dev.mlua.minecraft.command.MLuaCommandRegistry;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@MLuaDescription("Registers, inspects, and executes MLua commands.")
public final class MLuaCommandsService extends MLuaReflectiveService {
   public MLuaCommandsService(Context context) {
      super(context);
   }

   @Override
   public String name() {
      return "Commands";
   }

   @Override
   public LuaTable toLua() {
      LuaTable table = super.toLua();
      table.set("__side", "Both");
      return table;
   }

   @MLuaDescription("Registers a command definition during the command registration phase.")
   public void Register(String name, LuaTable definitionTable) {
      if (!this.context.isPhase(RegistrationPhase.COMMANDS)) {
         throw new LuaError("[MLua] Cannot register command '" + name
            + "': commands must be registered during run_commands(ctx) / RegisterCommandsEvent.");
      }
      MLuaCommandDefinition definition = MLuaCommandRegistry.fromLua(name, definitionTable);
      if (this.context.isClient()) {
         MLuaCommandRegistry.get().registerClientCallback(name, definition.clientCallback(), this.context.registration());
      } else {
         MLuaCommandRegistry.get().register(definition);
      }
   }

   @MLuaDescription("Returns the command names registered with MLua.")
   public List<String> GetRegisteredCommands() {
      return List.copyOf(MLuaCommandRegistry.get().names());
   }

   @MLuaDescription("Executes a command as the server.")
   public LuaValue Execute(String command) {
      if (this.context.server() == null) {
         throw new LuaError("[MLua] Commands:Execute requires an active server.");
      }
      //--version 1.20.1
      this.context.server().getCommands().performPrefixedCommand(
         this.context.server().createCommandSourceStack(), command);
      return LuaValue.TRUE;
      //--endversion
      //--version 1.21.1
      this.context.server().getCommands().performPrefixedCommand(
         this.context.server().createCommandSourceStack(), command);
      return LuaValue.valueOf(true);
      //--endversion
      //--version 26.1
      this.context.server().getCommands().performPrefixedCommand(
         this.context.server().createCommandSourceStack(), command);
      return LuaValue.TRUE;
      //--endversion
   }
}
