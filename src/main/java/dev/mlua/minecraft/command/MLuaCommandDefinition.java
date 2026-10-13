package dev.mlua.minecraft.command;

import java.util.List;
import org.luaj.vm2.LuaValue;

public record MLuaCommandDefinition(String name, String description, int permission, List<MLuaCommandArgument> arguments, List<String> aliases, LuaValue callback, LuaValue clientCallback) {
   public MLuaCommandDefinition(String name, String description, int permission, List<MLuaCommandArgument> arguments, List<String> aliases, LuaValue callback, LuaValue clientCallback) {
      this.name = name;
      this.description = description;
      this.permission = permission;
      this.arguments = arguments;
      this.aliases = aliases;
      this.callback = callback;
      this.clientCallback = clientCallback;
   }

   public String name() {
      return this.name;
   }

   public String description() {
      return this.description;
   }

   public int permission() {
      return this.permission;
   }

   public List<MLuaCommandArgument> arguments() {
      return this.arguments;
   }

   public List<String> aliases() {
      return this.aliases;
   }

   public LuaValue callback() {
      return this.callback;
   }

   public LuaValue clientCallback() {
      return this.clientCallback;
   }
}
