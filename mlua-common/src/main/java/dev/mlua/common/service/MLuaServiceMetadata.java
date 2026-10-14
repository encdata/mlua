package dev.mlua.common.service;

import java.util.Map;
import java.util.Objects;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public final class MLuaServiceMetadata {
   private MLuaServiceMetadata() { }

   public static void attachMethodDescriptions(LuaTable service, Map<String, String> descriptions) {
      Objects.requireNonNull(service, "service");
      Objects.requireNonNull(descriptions, "descriptions");
      LuaTable methods = new LuaTable();
      descriptions.forEach((name, description) -> {
         if (name == null || name.isBlank() || description == null || description.isBlank()) {
            throw new IllegalArgumentException("Service method descriptions require non-empty names and descriptions");
         }
         if (service.rawget(name).isfunction()) {
            methods.rawset(name, LuaValue.valueOf(description));
         }
      });
      service.rawset("__mlua_doc_methods", methods);
   }
}
