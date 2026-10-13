package dev.mlua.core.service;

import dev.mlua.api.MLuaDescription;
import dev.mlua.api.MLuaLuaCoercions;
import dev.mlua.module.MLuaRegistration;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Builds a live Lua service from public Java instance methods. Each exported method
 * must be documented with {@link MLuaDescription} or the service registration's
 * function descriptions; collections and arrays are converted to Lua tables, and
 * trailing {@link java.util.Optional} parameters accept omitted or nil Lua arguments.
 * Addon-specific values can use {@link MLuaLuaCoercions#registerAdapter}.
 */
public abstract class MLuaReflectiveService extends MLuaServiceBase {
   public MLuaReflectiveService(Context context) {
      super(context);
   }

   @Override
   public LuaTable toLua() {
      LuaTable table = new LuaTable();
      MLuaDescription serviceDescription = this.getClass().getAnnotation(MLuaDescription.class);
      if (serviceDescription != null) {
         if (serviceDescription.value().isBlank()) {
            throw new IllegalStateException("Reflective service '" + this.name() + "' has an empty class description");
         }
         table.set("__mlua_doc_description", LuaValue.valueOf(serviceDescription.value()));
      }
      LuaTable methodDescriptions = new LuaTable();
      LuaTable accessorParameterCounts = new LuaTable();

      Method[] methods = this.getClass().getMethods();
      Arrays.sort(methods, java.util.Comparator.comparing(Method::getName)
         .thenComparing(method -> Arrays.toString(method.getParameterTypes())));
      Set<String> exportedNames = new HashSet<>();
      for (Method method : methods) {
         int modifiers = method.getModifiers();
         if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers)
            || method.isBridge() || method.isSynthetic()) {
            continue;
         }

         Class<?> declaring = method.getDeclaringClass();
         if (declaring == Object.class || declaring == MLuaReflectiveService.class || declaring == LuaBackedService.class || declaring == MLuaService.class) {
            continue;
         }
         if (method.isAnnotationPresent(MLuaServiceIgnore.class)) continue;

         String name = method.getName();
         if (MLuaRegistration.isReservedFunctionName(name)) {
            throw new IllegalStateException("Reflective service '" + this.name()
               + "' method name is reserved for MLua metadata: " + name);
         }
         // These methods define the Java service contract; they are not Lua service functions.
         if ((name.equals("name") || name.equals("toLua")) && method.getParameterCount() == 0) continue;
         if (!exportedNames.add(name)) {
            throw new IllegalStateException("Reflective service '" + this.name() + "' has overloaded Lua method '" + name + "'; Lua services require unique exported method names");
         }
         accessorParameterCounts.set(name, method.getParameterCount());
         if (!method.canAccess(this) && !method.trySetAccessible()) {
            throw new IllegalStateException("Reflective service '" + this.name() + "' cannot access public method '"
               + name + "' declared by " + declaring.getName());
         }
         MLuaDescription methodDescription = findMethodDescription(method);
         if (methodDescription != null && methodDescription.value().isBlank()) {
            throw new IllegalStateException("Reflective service '" + this.name() + "' method '" + name + "' has an empty description");
         }
         if (methodDescription != null) {
            methodDescriptions.set(name, LuaValue.valueOf(methodDescription.value()));
         }
         table.set(name, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
               try {
                  int paramCount = method.getParameterCount();
                  int offset = serviceArgIndex(args, table);
                  Object[] javaArgs = convertArguments(method, args, offset);
                  Object result = method.invoke(MLuaReflectiveService.this, javaArgs);
                  return toLuaValue(result);
               } catch (InvocationTargetException failure) {
                  Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                  if (cause instanceof LuaError luaError) throw luaError;
                  throw new LuaError(new RuntimeException("[MLua] Java service method '" + name + "' on '"
                     + MLuaReflectiveService.this.name() + "' failed", cause));
               } catch (Exception e) {
                  throw new LuaError(new RuntimeException("[MLua] Failed to invoke Java service method '" + name
                     + "' on '" + MLuaReflectiveService.this.name() + "'", e));
               }
            }
         });
      }

      table.set("__mlua_doc_methods", methodDescriptions);
      table.set("__mlua_accessor_parameter_counts", accessorParameterCounts);

      return table;
   }

   protected int serviceArgIndex(Varargs args, LuaValue serviceTable) {
      if (args.arg1() == serviceTable) return 1;
      LuaValue receiver = args.arg1();
      if (receiver.istable()) {
         LuaValue metatable = receiver.getmetatable();
         if (metatable != null && metatable.istable()
            && metatable.rawget("__mlua_service_name").optjstring("").equals(this.name())) return 1;
      }
      return 0;
   }

   /** Converts one Lua argument; override to support addon-specific Java parameter types. */
   protected Object toJavaValue(LuaValue value, Type targetType) {
      return MLuaLuaCoercions.javaValue(value, targetType);
   }

   /** Converts one Java result to Lua; override to expose addon-specific values as live wrappers. */
   protected LuaValue toLuaValue(Object value) {
      return MLuaLuaCoercions.toLuaValue(value);
   }

   private Object[] convertArguments(Method method, Varargs args, int offset) {
      int parameterCount = method.getParameterCount();
      Object[] converted = new Object[parameterCount];
      Type[] genericTypes = method.getGenericParameterTypes();
      int suppliedCount = Math.max(0, args.narg() - offset);
      if (!method.isVarArgs()) {
         int minimumCount = parameterCount;
         while (minimumCount > 0 && isOptional(genericTypes[minimumCount - 1])) minimumCount--;
         if (suppliedCount < minimumCount || suppliedCount > parameterCount) {
            throw argumentCountError(method, minimumCount, parameterCount, suppliedCount);
         }
         for (int index = 0; index < parameterCount; index++) {
            LuaValue value = index < suppliedCount ? args.arg(index + 1 + offset) : LuaValue.NIL;
            converted[index] = toJavaValue(value, genericTypes[index]);
         }
         return converted;
      }

      int fixedCount = parameterCount - 1;
      if (suppliedCount < fixedCount) {
         throw argumentCountError(method, fixedCount, suppliedCount, true);
      }
      for (int index = 0; index < fixedCount; index++) {
         converted[index] = toJavaValue(args.arg(index + 1 + offset), genericTypes[index]);
      }
      Type arrayType = genericTypes[fixedCount];
      Type componentType = arrayType instanceof GenericArrayType genericArray
         ? genericArray.getGenericComponentType()
         : method.getParameterTypes()[fixedCount].getComponentType();
      Class<?> componentClass = method.getParameterTypes()[fixedCount].getComponentType();
      int remaining = Math.max(0, args.narg() - offset - fixedCount);
      Object packed = Array.newInstance(componentClass, remaining);
      if (remaining == 1 && args.arg(fixedCount + 1 + offset).istable()) {
         Object suppliedArray = toJavaValue(args.arg(fixedCount + 1 + offset), arrayType);
         if (suppliedArray != null) packed = suppliedArray;
      } else {
         for (int index = 0; index < remaining; index++) {
            Array.set(packed, index, toJavaValue(args.arg(fixedCount + index + 1 + offset), componentType));
         }
      }
      converted[fixedCount] = packed;
      return converted;
   }

   private IllegalArgumentException argumentCountError(Method method, int expected, int supplied, boolean minimum) {
      String requirement = minimum ? "at least " + expected : Integer.toString(expected);
      return new IllegalArgumentException("Java service method '" + method.getName() + "' expects "
         + requirement + " argument(s), got " + supplied);
   }

   private IllegalArgumentException argumentCountError(Method method, int minimum, int maximum, int supplied) {
      String requirement = minimum == maximum ? Integer.toString(minimum) : minimum + " to " + maximum;
      return new IllegalArgumentException("Java service method '" + method.getName() + "' expects "
         + requirement + " argument(s), got " + supplied);
   }

   private static boolean isOptional(Type type) {
      if (type instanceof Class<?> typeClass) return java.util.Optional.class.isAssignableFrom(typeClass);
      return type instanceof java.lang.reflect.ParameterizedType parameterized
         && parameterized.getRawType() instanceof Class<?> rawType
         && java.util.Optional.class.isAssignableFrom(rawType);
   }

   /** Method annotations are not inherited by Java, so preserve the contract when a service overrides a method. */
   private static MLuaDescription findMethodDescription(Method method) {
      MLuaDescription description = method.getAnnotation(MLuaDescription.class);
      if (description != null) return description;
      List<Class<?>[]> parameterSignatures = new java.util.ArrayList<>();
      parameterSignatures.add(method.getParameterTypes());
      for (Method bridge : method.getDeclaringClass().getDeclaredMethods()) {
         if (bridge.isBridge() && bridge.getName().equals(method.getName())
            && bridge.getParameterCount() == method.getParameterCount()) {
            parameterSignatures.add(bridge.getParameterTypes());
         }
      }
      return findMethodDescription(method.getDeclaringClass(), method.getName(), parameterSignatures,
         new HashSet<>());
   }

   private static MLuaDescription findMethodDescription(Class<?> type, String methodName,
                                                         List<Class<?>[]> parameterSignatures,
                                                         Set<Class<?>> visited) {
      if (type == null || !visited.add(type)) return null;
      for (Class<?>[] signature : parameterSignatures) {
         try {
            MLuaDescription description = type.getDeclaredMethod(methodName, signature)
               .getAnnotation(MLuaDescription.class);
            if (description != null) return description;
         } catch (NoSuchMethodException ignored) {
            // This parent does not declare this signature; another bridge signature or parent may.
         }
      }
      for (Class<?> parentInterface : type.getInterfaces()) {
         MLuaDescription description = findMethodDescription(parentInterface, methodName, parameterSignatures, visited);
         if (description != null) return description;
      }
      return findMethodDescription(type.getSuperclass(), methodName, parameterSignatures, visited);
   }
}
