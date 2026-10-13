package dev.mlua.common.mixin;

import org.luaj.vm2.LuaValue;
import dev.mlua.common.enums.InjectionPoint;
import dev.mlua.common.enums.MixinType;
import org.luaj.vm2.LuaError;

/** Shared validation and registration path used by early bootstrap and runtime services. */
public final class MLuaMixinRegistrar {
   private MLuaMixinRegistrar() { }

   public static String optionalDescriptor(LuaValue value, String method) {
      if (value == null || value.isnil()) return null;
      if (!value.isstring()) {
         throw new LuaError("[MLua] Mixin:" + method + " 'descriptor' must be a string");
      }
      return value.checkjstring();
   }

   public static int argumentIndex(LuaValue value) {
      if (value == null || value.isnil()) return 0;
      if (!value.isnumber()) throw new LuaError("[MLua] Mixin:ModifyArg 'index' must be a non-negative integer");
      double number = value.todouble();
      if (!Double.isFinite(number) || number < 0 || number > Integer.MAX_VALUE || number != Math.rint(number)) {
         throw new LuaError("[MLua] Mixin:ModifyArg 'index' must be a non-negative integer");
      }
      return (int) number;
   }

   public static MLuaMixinRule inject(String scope, String targetClass, String method, String descriptor,
                                      String at, LuaValue callback) {
      requireCallback(callback, "Inject");
      InjectionPoint point;
      try {
         point = InjectionPoint.parse(at);
      } catch (IllegalArgumentException failure) {
         throw new LuaError("[MLua] " + failure.getMessage());
      }
      String id = callbackId(scope, targetClass, method, descriptor, "inject", point.name());
      return register(new MLuaMixinRule(targetClass, method, descriptor, MixinType.INJECT,
         point, null, 0, id), callback);
   }

   public static MLuaMixinRule overwrite(String scope, String targetClass, String method,
                                         String descriptor, LuaValue callback) {
      requireCallback(callback, "Overwrite");
      String id = callbackId(scope, targetClass, method, descriptor, "overwrite", "");
      return register(new MLuaMixinRule(targetClass, method, descriptor, MixinType.OVERWRITE,
         null, null, 0, id), callback);
   }

   public static MLuaMixinRule redirect(String scope, String targetClass, String method,
                                        String target, LuaValue callback) {
      requireCallback(callback, "Redirect");
      target = invocationTarget(target, "Redirect");
      String id = callbackId(scope, targetClass, method, null, "redirect", target);
      return register(new MLuaMixinRule(targetClass, method, null, MixinType.REDIRECT,
         null, target, 0, id), callback);
   }

   public static MLuaMixinRule modifyArg(String scope, String targetClass, String method,
                                         String target, int index, LuaValue callback) {
      requireCallback(callback, "ModifyArg");
      target = invocationTarget(target, "ModifyArg");
      if (index < 0) throw new LuaError("[MLua] Mixin:ModifyArg index must be non-negative");
      String id = callbackId(scope, targetClass, method, null, "modifyarg", target + "@" + index);
      return register(new MLuaMixinRule(targetClass, method, null, MixinType.MODIFY_ARG,
         null, target, index, id), callback);
   }

   private static MLuaMixinRule register(MLuaMixinRule requested, LuaValue callback) {
      MLuaMixinRule mapped;
      try {
         mapped = MLuaMixinTargetMapper.remapRule(requested);
      } catch (IllegalArgumentException failure) {
         throw new LuaError("[MLua] Unable to resolve mixin target: " + failure.getMessage());
      }
      if (mapped == null) throw new LuaError("[MLua] Failed to remap mixin rule");
      try {
         MixinRuleEngine.get().registerRule(mapped, callback);
      } catch (IllegalArgumentException | IllegalStateException failure) {
         throw new LuaError("[MLua] " + failure.getMessage());
      }
      return mapped;
   }

   private static void requireCallback(LuaValue callback, String method) {
      if (callback == null || !callback.isfunction()) {
         throw new LuaError("[MLua] Mixin:" + method + " requires a 'callback' function");
      }
   }

   private static String invocationTarget(String target, String method) {
      if (target == null || target.isBlank()) {
         throw new LuaError("[MLua] Mixin:" + method + " requires a 'target' (inner method with descriptor)");
      }
      int ownerEnd = target.indexOf(';');
      int descriptorStart = target.indexOf('(', ownerEnd + 1);
      int descriptorEnd = descriptorStart < 0 ? -1 : target.indexOf(')', descriptorStart + 1);
      if (!target.startsWith("L") || ownerEnd <= 1 || descriptorStart <= ownerEnd + 1
            || descriptorEnd < 0 || descriptorEnd == target.length() - 1) {
         throw new LuaError("[MLua] Mixin:" + method
            + " target must use 'Lowner;method(arguments)returnType' notation");
      }
      return "L" + target.substring(1, ownerEnd).replace('.', '/') + target.substring(ownerEnd);
   }

   private static String callbackId(String scope, String targetClass, String method,
                                    String descriptor, String type, String detail) {
      return MLuaMixinCallbackRegistry.callbackId(scope, targetClass, method, descriptor, type, detail);
   }
}
