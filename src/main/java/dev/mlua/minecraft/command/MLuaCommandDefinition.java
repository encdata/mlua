package dev.mlua.minecraft.command;

import dev.mlua.core.MLuaContext;
import java.util.List;
import java.util.Objects;
import org.luaj.vm2.LuaValue;

/** Immutable command registration and the Lua runtime that owns its callbacks. */
public record MLuaCommandDefinition(String name, String description, int permission,
                                    List<MLuaCommandArgument> arguments, List<String> aliases,
                                    LuaValue callback, LuaValue clientCallback, MLuaContext context) {
   public MLuaCommandDefinition {
      Objects.requireNonNull(name, "name");
      description = description == null ? "" : description;
      arguments = List.copyOf(arguments == null ? List.of() : arguments);
      aliases = List.copyOf(aliases == null ? List.of() : aliases);
      callback = callback == null ? LuaValue.NIL : callback;
      clientCallback = clientCallback == null ? LuaValue.NIL : clientCallback;
   }

   /** Compatibility constructor for Java integrations that do not own a Lua runtime context. */
   public MLuaCommandDefinition(String name, String description, int permission,
                                List<MLuaCommandArgument> arguments, List<String> aliases,
                                LuaValue callback, LuaValue clientCallback) {
      this(name, description, permission, arguments, aliases, callback, clientCallback, null);
   }
}
