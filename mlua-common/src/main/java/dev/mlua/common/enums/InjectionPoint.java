package dev.mlua.common.enums;

public enum InjectionPoint {
   HEAD,
   RETURN,
   TAIL;

   public static InjectionPoint parse(String value) {
      if (value == null || value.isEmpty()) return values()[0];
      for (InjectionPoint point : values()) {
         if (point.name().equals(value)) return point;
      }
      String accepted = java.util.Arrays.stream(values()).map(point -> "\"" + point.name() + "\"")
         .collect(java.util.stream.Collectors.joining(", "));
      throw new IllegalArgumentException("Mixin:Inject 'at' must be one of " + accepted);
   }
}
