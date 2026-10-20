package dev.mlua.common.mixin;

import dev.mlua.common.enums.InjectionPoint;
import dev.mlua.common.enums.MixinType;

public record MLuaMixinRule(
   String targetClassName,
   String methodName,
   String methodDescriptor,
   MixinType type,
   InjectionPoint at,
   String redirectTarget,
   int modifyIndex,
   String callbackId
) {
   /** Matches loader class names regardless of binary-name or internal-name notation. */
   public boolean targetsClass(String className) {
      return className != null
         && normalizeClassName(this.targetClassName).equals(normalizeClassName(className));
   }

   public static String normalizeClassName(String className) {
      return className == null ? "" : className.replace('/', '.');
   }
}
