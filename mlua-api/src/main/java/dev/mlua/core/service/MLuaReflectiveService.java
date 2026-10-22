package dev.mlua.core.service;

import dev.mlua.api.MLuaDescription;
import dev.mlua.api.MLuaLuaCoercions;
import dev.mlua.common.service.MLuaServiceMetadata;
import dev.mlua.module.MLuaRegistration;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public abstract class MLuaReflectiveService extends MLuaServiceBase {
   private static final ThreadLocal<MLuaServiceArgumentBuffers> ARGUMENT_BUFFERS =
      ThreadLocal.withInitial(MLuaServiceArgumentBuffers::new);

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
      for (MLuaReflectiveServiceCatalog.DescribedMethod method : MLuaReflectiveServiceCatalog.methods(serviceType))
         functions.put(method.method().getName(), method.description());
      if (functions.isEmpty()) {
         throw new IllegalArgumentException("Reflective service '" + serviceType.getName()
            + "' has no described Lua methods");
      }
      return new ApiMetadata(serviceDescription.value(), functions);
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

      for (MLuaReflectiveServiceCatalog.DescribedMethod describedMethod : MLuaReflectiveServiceCatalog.methods(this.getClass())) {
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
                  MLuaServiceArgumentConverter.convertArguments(
                     MLuaReflectiveService.this, describedMethod, args, offset, javaArgs);
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

   private static MethodHandle bindInvoker(MLuaReflectiveServiceCatalog.DescribedMethod describedMethod, MLuaReflectiveService service) {
      MethodHandle invoker = describedMethod.invoker();
      if (invoker == null) return null;
      try {
         return invoker.bindTo(service).asType(MethodType.methodType(Object.class, Object[].class));
      } catch (RuntimeException unavailable) {
         return null;
      }
   }

   private Object invoke(MLuaReflectiveServiceCatalog.DescribedMethod describedMethod, MethodHandle invoker, Object[] arguments)
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


}
