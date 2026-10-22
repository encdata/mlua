package dev.mlua.core.service;

import dev.mlua.api.MLuaDescription;
import dev.mlua.module.MLuaRegistration;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Caches validated reflective API plans once per service class. */
final class MLuaReflectiveServiceCatalog {
    private MLuaReflectiveServiceCatalog() { }

   record DescribedMethod(Method method, MethodHandle invoker, String description, Type[] genericParameterTypes,
                                  Class<?>[] parameterTypes, boolean varArgs, int minimumArgumentCount,
                                  Type varArgComponentType) {
      static DescribedMethod create(Method method, String description) {
         Type[] genericTypes = method.getGenericParameterTypes();
         Class<?>[] parameterTypes = method.getParameterTypes();
         boolean varArgs = method.isVarArgs();
         int minimumArgumentCount = parameterTypes.length;
         if (varArgs) {
            minimumArgumentCount--;
         } else {
            while (minimumArgumentCount > 0 && isOptional(genericTypes[minimumArgumentCount - 1])) {
               minimumArgumentCount--;
            }
         }
         Type varArgComponentType = null;
         if (varArgs) {
            Type arrayType = genericTypes[minimumArgumentCount];
            varArgComponentType = arrayType instanceof GenericArrayType genericArray
               ? genericArray.getGenericComponentType()
               : parameterTypes[minimumArgumentCount].getComponentType();
         }
         return new DescribedMethod(method, createInvoker(method), description, genericTypes, parameterTypes, varArgs,
            minimumArgumentCount, varArgComponentType);
      }

      static MethodHandle createInvoker(Method method) {
         try {
            if (!method.trySetAccessible()) return null;
            MethodHandle handle = MethodHandles.lookup().unreflect(method);
            if (handle.type().returnType() == void.class) {
               handle = MethodHandles.filterReturnValue(handle, MethodHandles.constant(Object.class, null));
            } else {
               handle = handle.asType(handle.type().changeReturnType(Object.class));
            }
            if (method.getParameterCount() == 0) {
               return MethodHandles.dropArguments(handle, handle.type().parameterCount(), Object[].class);
            }
            return handle.asSpreader(Object[].class, method.getParameterCount());
         } catch (IllegalAccessException | RuntimeException unavailable) {
            return null;
         }
      }
   }

   private static final ClassValue<List<DescribedMethod>> DESCRIBED_METHODS = new ClassValue<>() {
      @Override
      protected List<DescribedMethod> computeValue(Class<?> serviceType) {
         return scanDescribedMethods(serviceType);
      }
   };

   static List<DescribedMethod> methods(Class<?> serviceType) {
      return DESCRIBED_METHODS.get(serviceType);
   }

   private static List<DescribedMethod> scanDescribedMethods(Class<?> serviceType) {
      Method[] methods = publicServiceMethods(serviceType);
      Arrays.sort(methods, java.util.Comparator.comparing(Method::getName)
         .thenComparing(method -> Arrays.toString(method.getParameterTypes())));
      List<DescribedMethod> described = new java.util.ArrayList<>();
      Set<String> exportedNames = new HashSet<>();
      for (Method method : methods) {
         int modifiers = method.getModifiers();
         Class<?> declaring = method.getDeclaringClass();
         if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers)
            || method.isBridge() || method.isSynthetic() || method.isAnnotationPresent(MLuaServiceIgnore.class)
            || declaring == Object.class || declaring == MLuaReflectiveService.class
            || declaring == LuaBackedService.class || declaring == MLuaService.class
            || (method.getParameterCount() == 0 && (method.getName().equals("name") || method.getName().equals("toLua")))) {
            continue;
         }
         MLuaDescription description = findMethodDescription(method);
         if (description == null) continue;
         if (description.value().isBlank()) {
            throw new IllegalArgumentException("Reflective service '" + serviceType.getName()
               + "' method '" + method.getName() + "' has an empty description");
         }
         if (MLuaRegistration.isReservedFunctionName(method.getName())) {
            throw new IllegalArgumentException("Reflective service '" + serviceType.getName()
               + "' uses reserved function name '" + method.getName() + "'");
         }
         if (!exportedNames.add(method.getName())) {
            throw new IllegalArgumentException("Reflective service '" + serviceType.getName()
               + "' overloads Lua method '" + method.getName() + "'");
         }
         described.add(DescribedMethod.create(method, description.value()));
      }
      return List.copyOf(described);
   }

   /**
    * Class.getMethods() omits some public members inherited from non-public service
    * implementation bases. Include those declarations explicitly so internal base
    * classes can share reflective Lua APIs without becoming public Java API types.
    */
   private static Method[] publicServiceMethods(Class<?> serviceType) {
      List<Method> methods = new java.util.ArrayList<>(Arrays.asList(serviceType.getMethods()));
      Set<String> signatures = new HashSet<>();
      for (Method method : methods) signatures.add(methodSignature(method));
      for (Class<?> current = serviceType.getSuperclass(); current != null && current != Object.class;
            current = current.getSuperclass()) {
         for (Method method : current.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && signatures.add(methodSignature(method))) {
               methods.add(method);
            }
         }
      }
      return methods.toArray(Method[]::new);
   }

   private static String methodSignature(Method method) {
      return method.getName() + Arrays.toString(method.getParameterTypes());
   }

   private static boolean isOptional(Type type) {
      if (type instanceof Class<?> typeClass) return java.util.Optional.class.isAssignableFrom(typeClass);
      return type instanceof java.lang.reflect.ParameterizedType parameterized
         && parameterized.getRawType() instanceof Class<?> rawType
         && java.util.Optional.class.isAssignableFrom(rawType);
   }

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
         }
      }
      for (Class<?> parentInterface : type.getInterfaces()) {
         MLuaDescription description = findMethodDescription(parentInterface, methodName, parameterSignatures, visited);
         if (description != null) return description;
      }
      return findMethodDescription(type.getSuperclass(), methodName, parameterSignatures, visited);
   }
}
