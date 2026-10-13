package dev.mlua.core.engine;

import dev.mlua.module.MLuaRegistration;
import java.util.ArrayList;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Bridges Lua behavior-mixin declarations to the shared class registry. */
final class MLuaBehaviorMixinApi {
   private MLuaBehaviorMixinApi() { }

   static LuaTable register(MLuaRegistration registration, String name, String description, LuaValue declarations) {
      List<Method> methods = readMethods(declarations);
      boolean alreadyRegistered = registration.behaviorMixinTable(name) != null;
      LuaTable mixin = registration.registerBehaviorMixin(name, description);
      List<Method> addedMethods = new ArrayList<>();
      try {
         for (Method method : methods) {
            LuaValue existing = mixin.get(method.name());
            if (!existing.isnil() && existing != method.implementation()) {
               throw new IllegalStateException("Lua mixin '" + name + "' already defines function '"
                  + method.name() + "'");
            }
            registration.registerBehaviorMixinFunction(name, method.name(),
               method.implementation(), method.description());
            if (existing.isnil()) addedMethods.add(method);
         }
      } catch (RuntimeException | Error failure) {
         for (int index = addedMethods.size() - 1; index >= 0; index--) {
            Method method = addedMethods.get(index);
            try {
               registration.unregisterBehaviorMixinFunction(name, method.name(), method.implementation());
            } catch (RuntimeException | Error rollbackFailure) {
               failure.addSuppressed(rollbackFailure);
            }
         }
         if (!alreadyRegistered) {
            try {
               registration.unregisterBehaviorMixin(name);
            } catch (RuntimeException | Error rollbackFailure) {
               failure.addSuppressed(rollbackFailure);
            }
         }
         throw new LuaError("[MLua] Could not register behavior mixin '" + name + "': " + failure.getMessage());
      }
      return mixin;
   }

   private static List<Method> readMethods(LuaValue declarations) {
      if (declarations.isnil()) return List.of();
      if (!declarations.istable()) {
         throw new LuaError("[MLua] RegisterBehaviorMixin methods must be a table of { implementation, description } entries.");
      }
      List<Method> methods = new ArrayList<>();
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs entry = declarations.checktable().next(key);
         key = entry.arg1();
         if (key.isnil()) return List.copyOf(methods);
         if (!key.isstring() || key.tojstring().isBlank()) {
            throw new LuaError("[MLua] Behavior mixin method names must be non-empty strings.");
         }
         String name = key.tojstring();
         if (MLuaRegistration.isReservedFunctionName(name)) {
            throw new LuaError("[MLua] Behavior mixin method name is reserved for MLua metadata: " + name);
         }
         LuaValue spec = entry.arg(2);
         if (!spec.istable()) {
            throw new LuaError("[MLua] Behavior mixin method '" + name
               + "' must contain implementation and description fields.");
         }
         LuaTable method = spec.checktable();
         LuaValue implementation = method.rawget("implementation");
         LuaValue description = method.rawget("description");
         if (!implementation.isfunction()) {
            throw new LuaError("[MLua] Behavior mixin method '" + name + "' implementation must be a function.");
         }
         if (!description.isstring() || description.tojstring().isBlank()) {
            throw new LuaError("[MLua] Behavior mixin method '" + name + "' description must be non-empty.");
         }
         methods.add(new Method(name, implementation, description.tojstring()));
      }
   }

   private record Method(String name, LuaValue implementation, String description) { }
}
