package dev.mlua.common.enums;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

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

   public static InjectionPoint parse(LuaValue value) {
      if (value == null || value.isnil()) return HEAD;
      try {
         if (value.isstring()) return parse(value.checkjstring());
         if (value.istable() && value.checktable() instanceof MLuaTypedEnumValue enumValue
               && enumValue.enumClass() == InjectionPoint.class) {
            return parse(enumValue.enumName());
         }
      } catch (IllegalArgumentException failure) {
         throw new LuaError("[MLua] " + failure.getMessage());
      }
      throw new LuaError("[MLua] Mixin:Inject 'at' expects Enum.MixinAt or a supported injection point name");
   }
}
