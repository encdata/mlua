package dev.mlua.core.classes;

import dev.mlua.module.MLuaRegistration;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.OneArgFunction;

public final class MLuaClassMethods {
   private MLuaClassMethods() {
   }

   public static void registerInstanceMethod(MLuaRegistration registration, String className,
                                             String methodName, String description) {
      if (className == null || className.isBlank()) throw new IllegalArgumentException("Class name must be non-empty");
      if (methodName == null || methodName.isBlank()) throw new IllegalArgumentException("Function name must be non-empty");
      if (description == null || description.isBlank()) throw new IllegalArgumentException("Function description must be non-empty");
      LuaTable classTable = registration.classTable(className);
      if (classTable == null) throw new IllegalStateException("Lua class is not registered: " + className);
      LuaValue dispatchersValue = classTable.rawget("__mlua_instance_dispatchers");
      if (!dispatchersValue.isnil() && !dispatchersValue.istable()) {
         throw new IllegalStateException("Lua class '" + className + "' has invalid instance dispatcher metadata");
      }
      LuaTable dispatchers = dispatchersValue.istable() ? dispatchersValue.checktable() : new LuaTable();
      LuaValue existing = classTable.rawget(methodName);
      if (existing.isfunction()) {
         registration.registerClassFunction(className, methodName, existing, description);
         if (dispatchersValue.isnil()) classTable.rawset("__mlua_instance_dispatchers", dispatchers);
         if (dispatchers.rawget(methodName).isnil()) dispatchers.rawset(methodName, LuaValue.FALSE);
         return;
      }
      if (!existing.isnil()) {
         throw new IllegalStateException("Lua class '" + className + "' already defines non-function member '" + methodName + "'");
      }
      LuaValue dispatcher = new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            LuaValue receiver = args.arg1();
            LuaValue method = instanceMethod(receiver, methodName);
            if (!method.isfunction() || method == this) {
               throw new LuaError("[MLua] " + className + " method '" + methodName + "' requires a live instance.");
            }
            return method.invoke(args);
         }
      };
      registration.registerClassFunction(className, methodName, dispatcher, description);
      if (dispatchersValue.isnil()) classTable.rawset("__mlua_instance_dispatchers", dispatchers);
      dispatchers.rawset(methodName, LuaValue.TRUE);
   }

   public static void registerInstanceProperty(MLuaRegistration registration, String className,
                                               String propertyName, String getterMethod,
                                               String setterMethod, String description) {
      registerInstanceProperty(registration, className, propertyName, getterMethod, setterMethod,
         "any", "shared:Shared", description);
   }

   public static void registerInstanceProperty(MLuaRegistration registration, String className,
                                               String propertyName, String getterMethod,
                                               String setterMethod, String valueType,
                                               String side, String description) {
      if (getterMethod == null || getterMethod.isBlank()) {
         throw new IllegalArgumentException("Property getter method must be non-empty");
      }
      requirePropertyMethod(registration, className, propertyName, getterMethod);
      if (setterMethod != null && !setterMethod.isBlank()) {
         requirePropertyMethod(registration, className, propertyName, setterMethod);
      }
      LuaValue getter = new OneArgFunction() {
         @Override
         public LuaValue call(LuaValue receiver) {
            LuaValue implementation = instanceMethod(receiver, getterMethod);
            if (!implementation.isfunction()) {
               throw new LuaError("[MLua] " + className + "." + propertyName
                  + " requires the live " + getterMethod + " method.");
            }
            return implementation.invoke(LuaValue.varargsOf(new LuaValue[]{receiver})).arg1();
         }
      };
      LuaValue setter = LuaValue.NIL;
      if (setterMethod != null && !setterMethod.isBlank()) {
         setter = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
               LuaValue receiver = args.arg1();
               LuaValue implementation = instanceMethod(receiver, setterMethod);
               if (!implementation.isfunction()) {
                  throw new LuaError("[MLua] " + className + "." + propertyName
                     + " cannot be changed because the live " + setterMethod + " method is unavailable.");
               }
               return implementation.invoke(LuaValue.varargsOf(new LuaValue[]{receiver, args.arg(2)}));
            }
         };
      }
      registration.registerClassProperty(className, propertyName, getter, setter, valueType, side, description);
   }

   private static void requirePropertyMethod(MLuaRegistration registration, String className,
                                             String propertyName, String methodName) {
      String methodDescription = registration.classMethodDescription(className, methodName);
      if (methodDescription == null || methodDescription.isBlank()) {
         throw new IllegalStateException("Lua class '" + className + "' property '" + propertyName
            + "' requires the registered method '" + methodName + "'");
      }
   }

   public static void bindInstanceMethod(MLuaRegistration registration, String className, LuaTable instance,
                                         String methodName, LuaValue implementation, String description) {
      if (instance == null) throw new NullPointerException("instance");
      if (methodName == null || methodName.isBlank()) throw new IllegalArgumentException("Method name must be non-empty");
      if (implementation == null || !implementation.isfunction()) {
         throw new IllegalArgumentException("Instance method must be a Lua function");
      }
      if (description == null || description.isBlank()) {
         throw new IllegalArgumentException("Method description must be non-empty");
      }
      if (!registration.isInstanceOf(instance, className)) {
         throw new IllegalArgumentException("Live Lua instance does not belong to class '" + className + "'");
      }
      LuaValue existingInstanceMethod = instance.rawget(methodName);
      if (!existingInstanceMethod.isnil() && existingInstanceMethod != implementation) {
         throw new IllegalStateException("Lua instance already has a field named '" + methodName + "'");
      }
      LuaValue documentedMethods = instance.rawget("__mlua_doc_methods");
      if (!documentedMethods.isnil() && !documentedMethods.istable()) {
         throw new IllegalStateException("Lua instance method descriptions must be a table");
      }
      LuaValue instanceDispatchers = instance.rawget("__mlua_instance_dispatchers");
      if (!instanceDispatchers.isnil() && !instanceDispatchers.istable()) {
         throw new IllegalStateException("Lua instance dispatcher metadata must be a table");
      }
      LuaValue instanceMeta = instance.getmetatable();
      if (!instanceMeta.istable()) throw new IllegalStateException("Live class instance has no lookup metatable");
      LuaValue instanceMethods = instanceMeta.checktable().rawget("__mlua_instance_methods");
      if (!instanceMethods.isnil() && !instanceMethods.istable()) {
         throw new IllegalStateException("Live class instance method storage must be a table");
      }
      LuaTable classTable = registration.classTable(className);
      if (classTable == null) throw new IllegalStateException("Lua class is not registered: " + className);
      LuaValue existing = classTable.rawget(methodName);
      if (!existing.isnil() && !existing.isfunction()) {
         throw new IllegalStateException("Lua class '" + className + "' already defines non-function member '"
            + methodName + "'");
      }
      if (existing.isfunction()) {
         LuaValue dispatchers = classTable.rawget("__mlua_instance_dispatchers");
         if (!dispatchers.istable() || !dispatchers.checktable().rawget(methodName).toboolean()) {
            throw new IllegalStateException("Lua class function '" + methodName + "' is not an instance dispatcher");
         }
      }
      registerInstanceMethod(registration, className, methodName, description);
      setInstanceMethod(instance, methodName, implementation, description);
      if (existingInstanceMethod == implementation) instance.rawset(methodName, LuaValue.NIL);
   }

   public static void setInstanceMethod(LuaTable instance, String methodName, LuaValue implementation) {
      validateInstanceMethod(instance, methodName, implementation, true);
      LuaTable meta = instance.getmetatable().checktable();
      LuaValue methodsValue = meta.rawget("__mlua_instance_methods");
      LuaTable methods = methodsValue.istable() ? methodsValue.checktable() : new LuaTable();
      methods.rawset(methodName, implementation);
      if (!methodsValue.istable()) meta.rawset("__mlua_instance_methods", methods);
   }

   private static void validateInstanceMethod(LuaTable instance, String methodName,
                                              LuaValue implementation, boolean requireDescription) {
      if (instance == null) throw new NullPointerException("instance");
      if (methodName == null || methodName.isBlank()) throw new IllegalArgumentException("Method name must be non-empty");
      if (implementation == null || !implementation.isfunction()) {
         throw new IllegalArgumentException("Instance method must be a Lua function");
      }
      if (!instance.get(methodName).isfunction()) {
         throw new IllegalStateException("Lua instance has no registered class function named '" + methodName + "'");
      }
      LuaValue dispatchers = instance.rawget("__mlua_instance_dispatchers");
      if (!dispatchers.istable() || !dispatchers.checktable().rawget(methodName).toboolean()) {
         throw new IllegalStateException("Lua class function '" + methodName + "' is not an instance dispatcher");
      }
      LuaValue documentedMethods = instance.rawget("__mlua_doc_methods");
      if (!documentedMethods.isnil() && !documentedMethods.istable()) {
         throw new IllegalStateException("Lua instance method descriptions must be a table");
      }
      if (requireDescription && (!documentedMethods.istable()
         || !documentedMethods.checktable().rawget(methodName).isstring())) {
         throw new IllegalStateException("Lua instance has no description for registered function '" + methodName + "'");
      }
      LuaValue metaValue = instance.getmetatable();
      if (!metaValue.istable()) throw new IllegalStateException("Live class instance has no lookup metatable");
      LuaValue methodsValue = metaValue.checktable().rawget("__mlua_instance_methods");
      if (!methodsValue.isnil() && !methodsValue.istable()) {
         throw new IllegalStateException("Live class instance method storage must be a table");
      }
   }

   public static void setInstanceMethod(LuaTable instance, String methodName, LuaValue implementation,
                                        String description) {
      if (description == null || description.isBlank()) {
         throw new IllegalArgumentException("Method description must be non-empty");
      }
      validateInstanceMethod(instance, methodName, implementation, false);
      LuaValue documentedMethods = instance.rawget("__mlua_doc_methods");
      LuaTable instanceDocs = documentedMethods.istable() ? documentedMethods.checktable() : new LuaTable();
      instanceDocs.rawset(methodName, LuaValue.valueOf(description));
      instance.rawset("__mlua_doc_methods", instanceDocs);
      setInstanceMethod(instance, methodName, implementation);
   }

   public static void bindInstanceMethod(MLuaRegistration registration, String className, LuaTable instance,
                                         String methodName, LuaValue implementation) {
      String description = registration.classMethodDescription(className, methodName);
      if (description == null || description.isBlank()) {
         throw new IllegalStateException("Lua class '" + className + "' has no description for function '" + methodName + "'");
      }
      bindInstanceMethod(registration, className, instance, methodName, implementation, description);
   }

   public static void registerClassMethod(MLuaRegistration registration, String className, LuaTable classTable,
                                          String methodName, LuaValue function, String description) {
      if (registration.classTable(className) != classTable) {
         throw new IllegalArgumentException("Lua class table does not belong to registered class '" + className + "'");
      }
      registration.registerClassFunction(className, methodName, function, description);
   }

   public static void registerServiceMethods(MLuaRegistration registration, String className,
                                             LuaTable serviceInstance) {
      LuaTable classTable = registration.classTable(className);
      if (classTable == null) throw new IllegalStateException("Lua class is not registered: " + className);
      LuaValue serviceNameValue = classTable.rawget("__mlua_service_name");
      if (!serviceNameValue.isstring() || serviceNameValue.tojstring().isBlank()) {
         throw new IllegalStateException("Lua class '" + className + "' is not assigned to a named service");
      }
      LuaValue classDescriptionValue = classTable.rawget("__mlua_doc_description");
      if (!classDescriptionValue.isnil() && !classDescriptionValue.isstring()) {
         throw new IllegalStateException("Service class '" + className + "' has invalid description metadata");
      }
      LuaValue docsValue = serviceInstance.rawget("__mlua_doc_methods");
      if (!docsValue.isnil() && !docsValue.istable()) {
         throw new IllegalStateException("Service '" + className + "' method descriptions must be a table");
      }

      LuaTable docs = docsValue.istable() ? docsValue.checktable() : new LuaTable();
      LuaValue classDocsValue = classTable.rawget("__mlua_doc_methods");
      if (!classDocsValue.isnil() && !classDocsValue.istable()) {
         throw new IllegalStateException("Service class '" + className + "' method descriptions must be a table");
      }
      LuaTable classDocs = classDocsValue.istable() ? classDocsValue.checktable() : new LuaTable();
      LuaValue declaredKey = LuaValue.NIL;
      while (true) {
         Varargs declaredEntry = classDocs.next(declaredKey);
         declaredKey = declaredEntry.arg1();
         if (declaredKey.isnil()) break;
         if (!declaredKey.isstring()) {
            throw new IllegalStateException("Service class '" + className + "' method names must be strings");
         }
         String methodName = declaredKey.tojstring();
         LuaValue declaredDescription = declaredEntry.arg(2);
         if (!declaredDescription.isstring() || declaredDescription.tojstring().isBlank()) {
            throw new IllegalStateException("Service class '" + className + "' method '" + methodName
               + "' must have a non-empty description");
         }
         LuaValue instanceDescription = docs.rawget(declaredKey);
         if (!instanceDescription.isnil()
            && (!instanceDescription.isstring()
               || !declaredDescription.tojstring().equals(instanceDescription.tojstring()))) {
            throw new IllegalStateException("Service '" + className + "' function '" + methodName
               + "' conflicts with its registered class description");
         }
         LuaValue classMethod = classTable.rawget(methodName);
         if (!classMethod.isfunction()) {
            throw new IllegalStateException("Service class '" + className + "' has no dispatcher for declared function '"
               + methodName + "'");
         }
         LuaValue dispatchers = classTable.rawget("__mlua_service_dispatchers");
         boolean usesInstanceDispatcher = dispatchers.istable()
            && dispatchers.checktable().rawget(methodName).toboolean();
         if (!usesInstanceDispatcher && serviceInstance.rawget(methodName).isfunction()) {
            throw new IllegalStateException("Service '" + className + "' already implements function '"
               + methodName + "' on its live instance; a service class function with the same name would be shadowed");
         }
         if (usesInstanceDispatcher && !instanceMethod(serviceInstance, methodName).isfunction()) {
            throw new IllegalStateException("Service '" + className + "' does not implement declared function '"
               + methodName + "'");
         }
      }

      LuaValue serviceKey = LuaValue.NIL;
      while (true) {
         Varargs serviceEntry = serviceInstance.next(serviceKey);
         serviceKey = serviceEntry.arg1();
         if (serviceKey.isnil()) break;
         if (!serviceKey.isstring() || serviceKey.tojstring().startsWith("__")
            || !serviceEntry.arg(2).isfunction()) continue;
         LuaValue description = docs.rawget(serviceKey);
         String registeredDescription = registration.classMethodDescription(className, serviceKey.tojstring());
         if ((!description.isstring() || description.tojstring().isBlank())
            && (registeredDescription == null || registeredDescription.isBlank())) {
            throw new IllegalStateException("Service '" + className + "' function '" + serviceKey.tojstring()
               + "' must have a non-empty description");
         }
      }

      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs entry = docs.next(key);
         key = entry.arg1();
         if (key.isnil()) break;
         if (!key.isstring()) {
            throw new IllegalStateException("Service '" + className + "' method names must be strings");
         }
         String methodName = key.tojstring();
         if (MLuaRegistration.isReservedFunctionName(methodName)) {
            throw new IllegalStateException("Service '" + className
               + "' method name is reserved for MLua metadata: " + methodName);
         }
         if (registration.hasClassProperty(className, methodName)) {
            throw new IllegalStateException("Service class '" + className + "' cannot register function '"
               + methodName + "' because it is an inherited property");
         }
         LuaValue description = entry.arg(2);
         LuaValue instanceMethod = instanceMethod(serviceInstance, methodName);
         if (!description.isstring() || description.tojstring().isBlank()) {
            throw new IllegalStateException("Service '" + className + "' method '" + methodName
               + "' must have a non-empty description");
         }
         if (!instanceMethod.isfunction()) {
            throw new IllegalStateException("Service '" + className + "' documents missing function '"
               + methodName + "'");
         }

         LuaValue classMethod = classTable.rawget(key);
         if (!classMethod.isnil() && !classMethod.isfunction()) {
            throw new IllegalStateException("Service class '" + className + "' already defines non-function member '"
               + methodName + "'");
         }
         String registeredDescription = registration.classMethodDescription(className, methodName);
         if (registeredDescription != null && !registeredDescription.equals(description.tojstring())) {
            throw new IllegalStateException("Service '" + className + "' method '" + methodName
               + "' conflicts with its registered class description");
         }
      }

      java.util.Map<String, String> serviceMethods = new java.util.LinkedHashMap<>();
      key = LuaValue.NIL;
      while (true) {
         Varargs entry = docs.next(key);
         key = entry.arg1();
         if (key.isnil()) break;
         serviceMethods.put(key.tojstring(), entry.arg(2).tojstring());
      }
      registration.registerServiceClass(serviceNameValue.tojstring(), className,
         classDescriptionValue.isstring() ? classDescriptionValue.tojstring() : null, serviceMethods);
   }

   private static LuaValue instanceMethod(LuaValue receiver, String methodName) {
      if (!receiver.istable()) return LuaValue.NIL;
      LuaValue key = LuaValue.valueOf(methodName);
      LuaValue method = receiver.rawget(key);
      if (method.isfunction()) return method;

      LuaValue metaValue = receiver.getmetatable();
      if (metaValue.istable()) {
         LuaTable meta = metaValue.checktable();
         LuaValue methods = meta.rawget("__mlua_instance_methods");
         if (methods.istable()) {
            method = methods.get(key);
            if (method.isfunction()) return method;
         }
         LuaValue service = meta.rawget("__mlua_service_instance");
         if (service.istable()) {
            method = service.checktable().rawget(key);
            if (method.isfunction()) return method;
         }
         LuaValue fallback = meta.rawget("__mlua_instance_fallback");
         if (fallback.istable()) method = fallback.get(key);
         else if (fallback.isfunction()) method = fallback.invoke(receiver, key).arg1();
         if (method.isfunction()) return method;
      }
      return receiver.get(key);
   }

}
