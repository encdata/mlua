package dev.mlua.common.enums;

public enum InjectionPoint {
   HEAD,
   RETURN,
   TAIL,
   INVOKE;

   public static InjectionPoint parse(String value) {
      if (value == null || value.isEmpty()) return values()[0];
      for (InjectionPoint point : values()) {
         if (point.name().equals(value)) return point;
      }
      String accepted = java.util.Arrays.stream(values()).map(point -> "\"" + point.name() + "\"")
         .collect(java.util.stream.Collectors.joining(", "));
      throw new IllegalArgumentException("Mixin:Inject 'at' must be one of " + accepted);
   }

   public static InjectionPoint parse(org.luaj.vm2.LuaValue value) {
      if (value == null || value.isnil()) return HEAD;
      try {
         if (value.isstring()) return parse(value.checkjstring());
         if (value.istable()) {
            org.luaj.vm2.LuaValue metatable = value.getmetatable();
            if (metatable != null && metatable.get("__isEnum").toboolean()
                  && InjectionPoint.class.getName().equals(metatable.get("__enumClass").optjstring(null))) {
               return parse(value.get("Name").checkjstring());
            }
         }
      } catch (IllegalArgumentException failure) {
         throw new org.luaj.vm2.LuaError("[MLua] " + failure.getMessage());
      }
      throw new org.luaj.vm2.LuaError("[MLua] Mixin:Inject 'at' expects Enum.MixinAt or a supported injection point name");
   }
}
