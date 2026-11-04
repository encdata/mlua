package dev.mlua.common.enums;

import org.luaj.vm2.LuaError;

public enum CommandArgumentType {
   STRING("string"),
   GREEDY_STRING("greedy_string"),
   INTEGER("integer"),
   NUMBER("number"),
   BOOLEAN("boolean"),
   PLAYER("player"),
   PLAYERS("players"),
   BLOCK_POS("block_pos"),
   VEC3("vec3"),
   RESOURCE_LOCATION("resource_location"),
   DIMENSION("dimension");

   private final String id;

   private CommandArgumentType(String id) {
      this.id = id;
   }

   public String id() {
      return this.id;
   }

   public static CommandArgumentType parse(String id) {
      for (CommandArgumentType type : values()) {
         if (type.id.equals(id)) {
            return type;
         }
      }
      throw new LuaError("[MLua] Unsupported command argument type: " + id);
   }
}
