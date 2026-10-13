package dev.mlua.common.enums;

public enum InjectionPoint {
   HEAD,
   RETURN;

   public static InjectionPoint parse(String value) {
      if (value == null || value.isEmpty() || "HEAD".equals(value)) return HEAD;
      if ("RETURN".equals(value)) return RETURN;
      throw new IllegalArgumentException("Mixin:Inject 'at' must be \"HEAD\" or \"RETURN\"");
   }
}
