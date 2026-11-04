package dev.mlua.minecraft.command;

import dev.mlua.common.enums.CommandArgumentType;

public record MLuaCommandArgument(String name, CommandArgumentType type) {
   public MLuaCommandArgument(String name, CommandArgumentType type) {
      this.name = name;
      this.type = type;
   }

   public String name() {
      return this.name;
   }

   public CommandArgumentType type() {
      return this.type;
   }
}
