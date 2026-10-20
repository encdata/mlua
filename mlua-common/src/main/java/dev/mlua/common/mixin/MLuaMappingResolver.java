package dev.mlua.common.mixin;

/**
 * Resolves the canonical names exposed to Lua into the names used by the
 * active Minecraft loader/runtime.
 */
public interface MLuaMappingResolver {
   String loaderId();

   default MLuaMixinRule remapRule(MLuaMixinRule rule) {
      ResolvedMethod method = mapMethod(rule.targetClassName(), rule.methodName(), rule.methodDescriptor());
      return new MLuaMixinRule(
         method.owner(),
         method.name(),
         method.descriptor(),
         rule.type(),
         rule.at(),
         mapRedirectTarget(rule.redirectTarget()),
         rule.modifyIndex(),
         rule.callbackId()
      );
   }

   default String mapClassName(String className) {
      return className;
   }

   default String mapDescriptor(String descriptor) {
      return descriptor;
   }

   default ResolvedMethod mapMethod(String owner, String method, String descriptor) {
      return new ResolvedMethod(mapClassName(owner), method, mapDescriptor(descriptor));
   }

   default String mapRedirectTarget(String target) {
      return target;
   }

   record ResolvedMethod(String owner, String name, String descriptor) {
   }
}
