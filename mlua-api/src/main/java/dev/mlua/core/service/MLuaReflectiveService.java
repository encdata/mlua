package dev.mlua.core.service;

import dev.mlua.api.MLuaDescription;
import dev.mlua.api.MLuaLuaCoercions;
import dev.mlua.common.service.MLuaServiceMetadata;
import dev.mlua.module.MLuaRegistration;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public abstract class MLuaReflectiveService extends MLuaServiceBase {
   private static final ThreadLocal<MLuaServiceArgumentBuffers> ARGUMENT_BUFFERS =
      ThreadLocal.withInitial(MLuaServiceArgumentBuffers::new);

   private record DescribedMethod(Method method, MethodHandle invoker, String description, Type[] genericParameterTypes,
                                  Class<?>[] parameterTypes, boolean varArgs, int minimumArgumentCount,
                                  Type varArgComponentType) {
      private static DescribedMethod create(Method method, String description) {
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

      private static MethodHandle createInvoker(Method method) {
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

   public record ApiMetadata(String description, Map<String, String> functionDescriptions) {
      public ApiMetadata {
         if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Reflective service description must be non-empty");
         }
         Map<String, String> functions = new LinkedHashMap<>(
            java.util.Objects.requireNonNull(functionDescriptions, "functionDescriptions"));
         if (functions.isEmpty()) {
            throw new IllegalArgumentException("Reflective services must declare at least one described Lua method");
         }
         functions.forEach((name, methodDescription) -> {
            if (name == null || name.isBlank() || methodDescription == null || methodDescription.isBlank()) {
               throw new IllegalArgumentException("Reflective service method names and descriptions must be non-empty");
            }
            if (MLuaRegistration.isReservedFunctionName(name)) {
               throw new IllegalArgumentException("Reflective service method name is reserved for MLua metadata: " + name);
            }
         });
         functionDescriptions = java.util.Collections.unmodifiableMap(functions);
      }
   }

   public MLuaReflectiveService(Context context) {
      super(context);
   }

   public static ApiMetadata apiMetadata(Class<? extends MLuaReflectiveService> serviceType) {
      java.util.Objects.requireNonNull(serviceType, "serviceType");
      MLuaDescription serviceDescription = serviceType.getAnnotation(MLuaDescription.class);
      if (serviceDescription == null || serviceDescription.value().isBlank()) {
         throw new IllegalArgumentException("Reflective service '" + serviceType.getName()
            + "' requires a non-empty @MLuaDescription");
      }
      Map<String, String> functions = new LinkedHashMap<>();
      for (DescribedMethod method : describedMethods(serviceType))
         functions.put(method.method().getName(), method.description());
      if (functions.isEmpty()) {
         throw new IllegalArgumentException("Reflective service '" + serviceType.getName()
            + "' has no described Lua methods");
      }
      return new ApiMetadata(serviceDescription.value(), functions);
   }

   private static List<DescribedMethod> describedMethods(Class<?> serviceType) {
      return DESCRIBED_METHODS.get(serviceType);
   }

   private static List<DescribedMethod> scanDescribedMethods(Class<?> serviceType) {
      Method[] methods = serviceType.getMethods();
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
      Map<String, String> methodDescriptions = new LinkedHashMap<>();
      LuaTable accessorParameterCounts = new LuaTable();

      for (DescribedMethod describedMethod : describedMethods(this.getClass())) {
         Method method = describedMethod.method();
         String name = method.getName();
         MethodHandle invoker = bindInvoker(describedMethod, this);
         accessorParameterCounts.set(name, describedMethod.parameterTypes().length);
         if (!method.canAccess(this) && !method.trySetAccessible()) {
            throw new IllegalStateException("Reflective service '" + this.name() + "' cannot access public method '"
               + name + "' declared by " + method.getDeclaringClass().getName());
         }
         methodDescriptions.put(name, describedMethod.description());
         table.set(name, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
               MLuaServiceArgumentBuffers buffers = ARGUMENT_BUFFERS.get();
               Object[] javaArgs = buffers.acquire(describedMethod.parameterTypes().length);
               try {
                  int offset = serviceArgIndex(args, table);
                  convertArguments(describedMethod, args, offset, javaArgs);
                  Object result = MLuaReflectiveService.this.invokeServiceMethod(
                     () -> MLuaReflectiveService.this.invoke(describedMethod, invoker, javaArgs));
                  try {
                     return toLuaValue(result);
                  } catch (LuaError conversionError) {
                     throw conversionError;
                  } catch (RuntimeException conversionFailure) {
                     throw new LuaError(new RuntimeException("[MLua] Java service method '" + name
                        + "' on '" + MLuaReflectiveService.this.name()
                        + "' returned a value that cannot be converted to Lua", conversionFailure));
                  }
               } catch (InvocationTargetException failure) {
                  Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                  if (cause instanceof LuaError luaError) throw luaError;
                  if (cause instanceof IllegalArgumentException argumentError) {
                     throw argumentError(name, argumentError);
                  }
                  throw new LuaError(new RuntimeException("[MLua] Java service method '" + name + "' on '"
                     + MLuaReflectiveService.this.name() + "' failed: "
                     + (cause.getMessage() == null ? cause.getClass().getName() : cause.getMessage()), cause));
               } catch (LuaError error) {
                  throw error;
               } catch (IllegalArgumentException error) {
                  throw argumentError(name, error);
               } catch (Exception e) {
                  throw new LuaError(new RuntimeException("[MLua] Failed to invoke Java service method '" + name
                     + "' on '" + MLuaReflectiveService.this.name() + "'", e));
               } finally {
                  buffers.release(javaArgs);
               }
            }
         });
      }

      MLuaServiceMetadata.attachMethodDescriptions(table, methodDescriptions);
      table.set("__mlua_accessor_parameter_counts", accessorParameterCounts);

      return table;
   }

   private static MethodHandle bindInvoker(DescribedMethod describedMethod, MLuaReflectiveService service) {
      MethodHandle invoker = describedMethod.invoker();
      if (invoker == null) return null;
      try {
         return invoker.bindTo(service).asType(MethodType.methodType(Object.class, Object[].class));
      } catch (RuntimeException unavailable) {
         return null;
      }
   }

   private Object invoke(DescribedMethod describedMethod, MethodHandle invoker, Object[] arguments)
      throws InvocationTargetException, IllegalAccessException {
      if (invoker == null) return describedMethod.method().invoke(this, arguments);
      try {
         return (Object) invoker.invokeExact(arguments);
      } catch (Throwable failure) {
         throw new InvocationTargetException(failure);
      }
   }

   /** Lets a service marshal its Java implementation to the thread that owns its Minecraft state. */
   protected Object invokeServiceMethod(ServiceInvocation invocation) throws Exception {
      return invocation.invoke();
   }

   @FunctionalInterface
   protected interface ServiceInvocation {
      Object invoke() throws Exception;
   }

   private LuaError argumentError(String methodName, IllegalArgumentException cause) {
      String detail = cause.getMessage();
      String prefix = "[MLua] " + this.name() + ":" + methodName;
      return new LuaError(detail == null || detail.isBlank() ? prefix + " received invalid arguments"
         : prefix + ": " + detail);
   }

   protected int serviceArgIndex(Varargs args, LuaValue serviceTable) {
      return this.serviceArgumentIndex(args, serviceTable.checktable(), 1) - 1;
   }

   protected Object toJavaValue(LuaValue value, Type targetType) {
      return MLuaLuaCoercions.javaValue(value, targetType);
   }

   protected LuaValue toLuaValue(Object value) {
      return MLuaLuaCoercions.toLuaValue(value);
   }

   private void convertArguments(DescribedMethod plan, Varargs args, int offset, Object[] converted) {
      Method method = plan.method();
      int parameterCount = plan.parameterTypes().length;
      int suppliedCount = Math.max(0, args.narg() - offset);
      if (!plan.varArgs()) {
         int minimumCount = plan.minimumArgumentCount();
         if (suppliedCount < minimumCount || suppliedCount > parameterCount) {
            throw argumentCountError(method, minimumCount, parameterCount, suppliedCount);
         }
         MLuaLuaCoercions.LuaValueValidator validator = null;
         for (int index = 0; index < suppliedCount; index++) {
            validator = validateArgument(validator, plan.genericParameterTypes()[index], args.arg(index + 1 + offset));
         }
         for (int index = 0; index < parameterCount; index++) {
            LuaValue value = index < suppliedCount ? args.arg(index + 1 + offset) : LuaValue.NIL;
            converted[index] = toJavaValue(value, plan.genericParameterTypes()[index]);
         }
         return;
      }

      int fixedCount = plan.minimumArgumentCount();
      if (suppliedCount < fixedCount) {
         throw argumentCountError(method, fixedCount, suppliedCount, true);
      }
      MLuaLuaCoercions.LuaValueValidator validator = null;
      for (int index = 0; index < fixedCount; index++) {
         validator = validateArgument(validator, plan.genericParameterTypes()[index], args.arg(index + 1 + offset));
      }
      Type arrayType = plan.genericParameterTypes()[fixedCount];
      Type componentType = plan.varArgComponentType();
      int remaining = Math.max(0, args.narg() - offset - fixedCount);
      for (int index = 0; index < remaining; index++) {
         validator = validateArgument(validator, componentType, args.arg(fixedCount + index + 1 + offset));
      }
      for (int index = 0; index < fixedCount; index++) {
         converted[index] = toJavaValue(args.arg(index + 1 + offset), plan.genericParameterTypes()[index]);
      }
      Class<?> componentClass = plan.parameterTypes()[fixedCount].getComponentType();
      Object packed = Array.newInstance(componentClass, remaining);
      boolean suppliedPackedArray = false;
      if (remaining == 1 && args.arg(fixedCount + 1 + offset).istable()) {
         try {
            Object suppliedArray = toJavaValue(args.arg(fixedCount + 1 + offset), arrayType);
            if (suppliedArray != null) {
               packed = suppliedArray;
               suppliedPackedArray = true;
            }
         } catch (IllegalArgumentException notAnArray) {
            // A single Lua table may be one vararg value. Prefer the Java array
            // form when it converts cleanly; otherwise try it as one element.
         }
      }
      if (!suppliedPackedArray) {
         for (int index = 0; index < remaining; index++) {
            Array.set(packed, index, toJavaValue(args.arg(fixedCount + index + 1 + offset), componentType));
         }
      }
      converted[fixedCount] = packed;
   }

   private static MLuaLuaCoercions.LuaValueValidator validateArgument(
         MLuaLuaCoercions.LuaValueValidator validator, Type targetType, LuaValue value) {
      if (targetType instanceof Class<?> targetClass && LuaValue.class.isAssignableFrom(targetClass)) return validator;
      if (value == null || !value.istable()) return validator;
      if (validator == null) validator = new MLuaLuaCoercions.LuaValueValidator();
      validator.validate(value);
      return validator;
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
