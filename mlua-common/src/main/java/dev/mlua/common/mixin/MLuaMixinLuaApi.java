package dev.mlua.common.mixin;

import dev.mlua.common.enums.MixinType;
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

   private MLuaMixinLuaApi() { }

   public static LuaTable create(Supplier<String> activeScope) {
      return create(activeScope, () -> true);
   }

   public static LuaTable create(Supplier<String> activeScope, BooleanSupplier registrationAllowed) {
      LuaTable table = new LuaTable();
      table.set("CallOriginal", function(args -> MLuaMixinCallbackRegistry.callOriginalLua()));
      table.set("GetRules", function(args -> getRules(activeScope.get())));
      table.set("GetFailures", function(args -> getFailures()));
      table.set("Target", function(args -> targetApi(args.checkjstring(2), activeScope, registrationAllowed)));
      bindOperations(table, args -> args.checkjstring(2), 3, 4, activeScope, registrationAllowed);
      return table;
   }

   private static LuaTable targetApi(String targetClass, Supplier<String> activeScope,
                                     BooleanSupplier registrationAllowed) {
      LuaTable target = new LuaTable();
      bindOperations(target, args -> targetClass, 2, 3, activeScope, registrationAllowed);
      return target;
   }

   private static void bindOperations(LuaTable table, Function<Varargs, String> targetClass,
                                      int methodIndex, int optionsIndex, Supplier<String> activeScope,
                                      BooleanSupplier registrationAllowed) {
      for (MixinType type : MixinType.values()) {
         String methodName = type.name().equals("MODIFY_ARG") ? "ModifyArg" : titleCase(type.name());
         if (type != MixinType.INJECT && type != MixinType.OVERWRITE
               && type != MixinType.REDIRECT && type != MixinType.MODIFY_ARG) continue;
         table.set(methodName, function(args -> {
            requireRegistration(registrationAllowed);
            String target = targetClass.apply(args);
            String method = args.checkjstring(methodIndex);
            LuaTable options = args.checktable(optionsIndex);
            LuaValue callback = options.get("callback");
            MLuaMixinRule rule;
            switch (type) {
               case INJECT -> {
                  LuaValue at = options.get("at");
                  if (!at.isnil() && !at.isstring()) {
                     throw new LuaError("[MLua] Mixin:Inject 'at' must be \"HEAD\" or \"RETURN\"");
                  }
                  rule = MLuaMixinRegistrar.inject(activeScope.get(), target, method,
                     MLuaMixinRegistrar.optionalDescriptor(options.get("descriptor"), "Inject"),
                     at.isnil() ? null : at.checkjstring(), callback);
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
      for (java.util.Map.Entry<String, String> failure : MixinRuleEngine.get().getApplicationFailures().entrySet()) {
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
