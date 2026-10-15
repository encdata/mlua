package dev.mlua.common.mixin;

import dev.mlua.common.enums.MixinType;
import dev.mlua.common.enums.InjectionPoint;
import dev.mlua.common.service.MLuaServiceMetadata;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates the same Lua mixin service for early bootstrap and normal runtime scripts. */
public final class MLuaMixinLuaApi {
   private static final Logger LOGGER = LoggerFactory.getLogger("MLua");
   private static final Map<String, String> FUNCTION_DESCRIPTIONS = createFunctionDescriptions();

   private MLuaMixinLuaApi() { }

   public static Map<String, String> functionDescriptions() {
      return FUNCTION_DESCRIPTIONS;
   }

   private static Map<String, String> createFunctionDescriptions() {
      Map<String, String> descriptions = new LinkedHashMap<>();
      descriptions.put("CallOriginal", "Calls the original implementation from the matching Overwrite callback and returns its result. The original exception is reported with its cause.");
      descriptions.put("Target", "Binds a Java target class and returns target-scoped Inject, Overwrite, Redirect, and ModifyArg methods.");
      descriptions.put("GetRules", "Returns mixin rules registered by the current script.");
      descriptions.put("GetFailures", "Returns target classes whose registered mixin rules could not be applied.");
      descriptions.put("Inject", "Injects a callback at Enum.MixinAt.Head, Return, Tail, or Invoke. The callback receives self and enclosing method arguments; at Invoke it then receives the invocation receiver and arguments. For non-void target methods, callbacks at Head, Return, or Tail may return a compatible value to override the result; nil keeps execution. At Invoke, the callback return is ignored. At Head, false cancels a void target method.");
      descriptions.put("Overwrite", "Replaces a target method with a Lua callback that receives self followed by the target method arguments. Return a value compatible with the Java method; Java object and array returns are converted from Lua values.");
      descriptions.put("Redirect", "Replaces an exact invocation selected by target = 'Lowner;method(descriptor)returnType'. The callback receives the enclosing self, invocation receiver (nil for static calls), invocation arguments, then enclosing method arguments. Return nil to execute the original invocation.");
      descriptions.put("ModifyArg", "Changes a zero-based argument at the exact invocation selected by target = 'Lowner;method(descriptor)returnType'. The callback receives self, the old argument, then enclosing method arguments; return nil to keep the old value.");
      return Collections.unmodifiableMap(descriptions);
   }

   public static LuaTable create(Supplier<String> activeScope) {
      return create(activeScope, () -> true, () -> MLuaMixinLuaApi.class);
   }

   public static LuaTable create(Supplier<String> activeScope, BooleanSupplier registrationAllowed) {
      return create(activeScope, registrationAllowed, () -> MLuaMixinLuaApi.class);
   }

   public static LuaTable create(Supplier<String> activeScope, BooleanSupplier registrationAllowed,
                                 Supplier<Object> executionLock) {
      Supplier<Object> callbackLock = executionLock == null ? () -> MLuaMixinLuaApi.class : executionLock;
      LuaTable table = new LuaTable();
      table.set("CallOriginal", function(args -> MLuaMixinCallbackRegistry.callOriginalLua()));
      table.set("GetRules", function(args -> getRules(activeScope.get())));
      table.set("GetFailures", function(args -> getFailures()));
      table.set("Target", function(args -> targetApi(args.checkjstring(2), activeScope, registrationAllowed, callbackLock)));
      bindOperations(table, args -> args.checkjstring(2), 3, 4, activeScope, registrationAllowed, callbackLock);
      MLuaServiceMetadata.attachMethodDescriptions(table, FUNCTION_DESCRIPTIONS);
      return table;
   }

   private static LuaTable targetApi(String targetClass, Supplier<String> activeScope,
                                     BooleanSupplier registrationAllowed, Supplier<Object> executionLock) {
      LuaTable target = new LuaTable();
      bindOperations(target, args -> targetClass, 2, 3, activeScope, registrationAllowed, executionLock);
      MLuaServiceMetadata.attachMethodDescriptions(target, FUNCTION_DESCRIPTIONS);
      return target;
   }

   private static void bindOperations(LuaTable table, Function<Varargs, String> targetClass,
                                      int methodIndex, int optionsIndex, Supplier<String> activeScope,
                                      BooleanSupplier registrationAllowed, Supplier<Object> executionLock) {
      for (MixinType type : MixinType.values()) {
         String methodName = type.name().equals("MODIFY_ARG") ? "ModifyArg" : titleCase(type.name());
         if (type != MixinType.INJECT && type != MixinType.OVERWRITE
               && type != MixinType.REDIRECT && type != MixinType.MODIFY_ARG) continue;
         table.set(methodName, function(args -> {
            requireRegistration(registrationAllowed);
            String target = targetClass.apply(args);
            String method = args.checkjstring(methodIndex);
            LuaTable options = args.checktable(optionsIndex);
            validateOptions(options, type);
            LuaValue callback = serializeCallback(options.get("callback"), executionLock.get());
            MLuaMixinRule rule;
            switch (type) {
               case INJECT -> {
                  LuaValue at = options.get("at");
                  LuaValue invocation = options.get("target");
                  if (!invocation.isnil() && !invocation.isstring()) {
                     throw new LuaError("[MLua] Mixin:Inject 'target' must be an exact invocation descriptor");
                  }
                  rule = MLuaMixinRegistrar.inject(activeScope.get(), target, method,
                     MLuaMixinRegistrar.optionalDescriptor(options.get("descriptor"), "Inject"),
                     at.isnil() ? null : InjectionPoint.parse(at).name(),
                     invocation.isnil() ? null : invocation.checkjstring(), callback);
               }
               case OVERWRITE -> rule = MLuaMixinRegistrar.overwrite(activeScope.get(), target, method,
                  MLuaMixinRegistrar.optionalDescriptor(options.get("descriptor"), "Overwrite"), callback);
               case REDIRECT -> {
                  LuaValue invocation = options.get("target");
                  rule = MLuaMixinRegistrar.redirect(activeScope.get(), target, method,
                     invocation.isstring() ? invocation.checkjstring() : null, callback);
               }
               case MODIFY_ARG -> {
                  LuaValue invocation = options.get("target");
                  rule = MLuaMixinRegistrar.modifyArg(activeScope.get(), target, method,
                     invocation.isstring() ? invocation.checkjstring() : null,
                     MLuaMixinRegistrar.argumentIndex(options.get("index")), callback);
               }
               default -> throw new IllegalStateException("Unsupported mixin type: " + type);
            }
            log(methodName, rule);
            return LuaValue.NONE;
         }));
      }
   }

   private static void validateOptions(LuaTable options, MixinType type) {
      java.util.Set<String> accepted = switch (type) {
         case INJECT -> java.util.Set.of("at", "callback", "descriptor", "target");
         case OVERWRITE -> java.util.Set.of("callback", "descriptor");
         case REDIRECT -> java.util.Set.of("callback", "target");
         case MODIFY_ARG -> java.util.Set.of("callback", "index", "target");
      };
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs entry = options.next(key);
         key = entry.arg1();
         if (key.isnil()) return;
         if (!key.isstring() || !accepted.contains(key.tojstring())) {
            throw new LuaError("[MLua] Mixin:" + operationName(type)
               + " does not accept option '" + key.tojstring() + "'; expected one of "
               + accepted.stream().sorted().toList());
         }
      }
   }

   private static String operationName(MixinType type) {
      return type == MixinType.MODIFY_ARG ? "ModifyArg" : titleCase(type.name());
   }

   private static LuaValue serializeCallback(LuaValue callback, Object lock) {
      if (callback == null || !callback.isfunction()) return callback;
      Object executionLock = lock == null ? MLuaMixinLuaApi.class : lock;
      return new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            synchronized (executionLock) {
               return callback.invoke(args);
            }
         }
      };
   }

   private static String titleCase(String value) {
      String lower = value.toLowerCase(java.util.Locale.ROOT);
      return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
   }

   private static LuaTable getRules(String scope) {
      LuaTable result = new LuaTable();
      int index = 1;
      String prefix = (scope == null || scope.isBlank() ? "unscoped" : scope) + ":";
      for (MLuaMixinRule rule : MixinRuleEngine.get().getRules()) {
         if (!rule.callbackId().startsWith(prefix)) continue;
         LuaTable entry = new LuaTable();
         entry.set("TargetClass", rule.targetClassName());
         entry.set("Method", rule.methodName());
         if (rule.methodDescriptor() != null) entry.set("Descriptor", rule.methodDescriptor());
         entry.set("Type", rule.type().name());
         if (rule.at() != null) entry.set("At", rule.at().name());
         if (rule.redirectTarget() != null) entry.set("Target", rule.redirectTarget());
         if (rule.type() == MixinType.MODIFY_ARG) entry.set("Index", LuaValue.valueOf(rule.modifyIndex()));
         result.set(index++, entry);
      }
      return result;
   }

   private static LuaTable getFailures() {
      LuaTable result = new LuaTable();
      int index = 1;
      for (java.util.Map.Entry<String, String> failure : MixinRuleEngine.get().getApplicationFailures()
            .entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
         LuaTable entry = new LuaTable();
         entry.set("TargetClass", failure.getKey());
         entry.set("Message", failure.getValue());
         result.set(index++, entry);
      }
      return result;
   }

   private static void log(String type, MLuaMixinRule rule) {
      LOGGER.info("[MLua] Registered {} mixin: {}#{}{}", type, rule.targetClassName(),
         rule.methodName(), rule.methodDescriptor() == null ? "" : rule.methodDescriptor());
   }

   private static void requireRegistration(BooleanSupplier registrationAllowed) {
      if (!registrationAllowed.getAsBoolean()) {
         throw new LuaError("[MLua] Mixins can only be registered during the mixin or registry phase (.mixin.lua or .registry.lua files)");
      }
   }

   private static VarArgFunction function(Function<Varargs, LuaValue> action) {
      return new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            try {
               return action.apply(args);
            } catch (LuaError error) {
               throw error;
            } catch (Exception error) {
               throw new LuaError(new RuntimeException("[MLua] Mixin service call failed", error));
            }
         }
      };
   }
}
