package dev.mlua.core.engine;

import dev.mlua.core.MLuaContext;
import dev.mlua.module.MLuaRegistration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Owns script registration of documented service classes and their functions. */
final class MLuaServiceRegistrationBindings {
    private MLuaServiceRegistrationBindings() { }

    static void bindInto(LuaTable table, MLuaContext context) {
        table.set("RegisterServiceFunction", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (args.narg() != 4 && args.narg() != 5) {
                    throw new LuaError("[MLua] RegisterServiceFunction expects (service, function, description) "
                        + "or (service, function, implementation, description).");
                }
                MLuaRegistration registration = MLuaLuaBindings.requireMutableRegistration(context);
                String serviceName = args.arg(2).checkjstring();
                String functionName = args.arg(3).checkjstring();
                LuaValue implementation = args.arg(4);
                boolean hasImplementation = implementation.isfunction();
                String description = hasImplementation ? args.arg(5).checkjstring() : implementation.checkjstring();
                if (serviceName.isBlank() || functionName.isBlank() || description.isBlank()) {
                    throw new LuaError("[MLua] RegisterServiceFunction requires non-empty service, function, and description values.");
                }
                try {
                    if (hasImplementation) {
                        requireDescribedServiceClass(registration, serviceName);
                        return registration.registerServiceClassFunction(serviceName, functionName,
                            implementation, description);
                    }
                    if (args.narg() == 5) {
                        throw new LuaError("[MLua] RegisterServiceFunction implementation must be a Lua function.");
                    }
                    if (!context.services().isAvailable(serviceName, context.side())) {
                        throw new LuaError("[MLua] RegisterServiceFunction without an implementation requires a service "
                            + "registered on this side. For a Lua-backed service, pass a function implementation or use "
                            + "RegisterServiceClassFunction.");
                    }
                    requireDescribedServiceClass(registration, serviceName);
                    return registration.registerServiceFunction(registration.serviceClassName(serviceName),
                        functionName, description);
                } catch (LuaError failure) {
                    throw failure;
                } catch (RuntimeException failure) {
                    throw new LuaError("[MLua] Could not register service function '" + functionName
                        + "' on '" + serviceName + "': " + failure.getMessage());
                }
            }
        });
        table.set("RegisterServiceClass", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                MLuaRegistration registration = MLuaLuaBindings.requireMutableRegistration(context);
                String serviceName = args.arg(2).checkjstring();
                boolean hasMethods = args.arg(args.narg()).istable();
                boolean explicitClassName = (args.narg() == 4 && !hasMethods) || args.narg() == 5;
                if (args.narg() < 3 || args.narg() > 5
                    || (hasMethods && args.narg() != 4 && args.narg() != 5)
                    || (args.narg() == 5 && !hasMethods)) {
                    throw new LuaError("[MLua] RegisterServiceClass expects (service, description[, methodDescriptions]) "
                        + "or (service, className, description[, methodDescriptions]).");
                }
                String className = explicitClassName ? args.arg(3).checkjstring()
                    : registration.serviceClassName(serviceName);
                LuaValue descriptionValue = args.arg(explicitClassName ? 4 : 3);
                if (descriptionValue.isnil() || !descriptionValue.isstring()
                    || descriptionValue.checkjstring().isBlank()) {
                    throw new LuaError("[MLua] RegisterServiceClass requires a non-empty service class description.");
                }
                String description = descriptionValue.checkjstring();
                Map<String, String> methodDescriptions = hasMethods
                    ? readServiceMethodDescriptions(args.arg(args.narg())) : Map.of();
                try {
                    return registration.registerServiceClass(serviceName, className, description, methodDescriptions);
                } catch (RuntimeException failure) {
                    throw new LuaError("[MLua] Could not register service class '" + className + "': "
                        + failure.getMessage());
                }
            }
        });
        table.set("RegisterServiceClassFunction", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (args.narg() != 5 && args.narg() != 6) {
                    throw new LuaError("[MLua] RegisterServiceClassFunction expects (service, function, implementation, description) "
                        + "or (service, className, function, implementation, description).");
                }
                MLuaRegistration registration = MLuaLuaBindings.requireMutableRegistration(context);
                String serviceName = args.arg(2).checkjstring();
                boolean explicitClassName = args.narg() >= 6;
                String className = explicitClassName ? args.arg(3).checkjstring()
                    : registration.serviceClassName(serviceName);
                String functionName = args.arg(explicitClassName ? 4 : 3).checkjstring();
                LuaValue implementation = args.arg(explicitClassName ? 5 : 4);
                String description = args.arg(explicitClassName ? 6 : 5).checkjstring();
                if (serviceName.isBlank() || functionName.isBlank() || description.isBlank()) {
                    throw new LuaError("[MLua] RegisterServiceClassFunction requires non-empty service, function, and description values.");
                }
                if (className.isBlank()) {
                    throw new LuaError("[MLua] RegisterServiceClassFunction requires a non-empty service class name.");
                }
                if (!implementation.isfunction()) {
                    throw new LuaError("[MLua] RegisterServiceClassFunction requires a Lua function implementation.");
                }
                try {
                    requireDescribedServiceClass(registration, serviceName, className);
                    if (explicitClassName) {
                        return registration.registerServiceClassFunction(serviceName, className, functionName,
                            implementation, description);
                    }
                    return registration.registerServiceClassFunction(serviceName, functionName,
                        implementation, description);
                } catch (LuaError failure) {
                    throw failure;
                } catch (RuntimeException failure) {
                    throw new LuaError("[MLua] Could not register service class function '" + functionName
                        + "' on '" + className + "': " + failure.getMessage());
                }
            }
        });
        table.set("UnregisterServiceFunction", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                MLuaRegistration registration = MLuaLuaBindings.requireMutableRegistration(context);
                String serviceName = args.arg(2).checkjstring();
                boolean explicitClassName = args.narg() >= 4 && args.arg(4).isstring();
                String className = explicitClassName ? args.arg(3).checkjstring()
                    : registration.serviceClassName(serviceName);
                String functionName = args.arg(explicitClassName ? 4 : 3).checkjstring();
                LuaTable serviceClass = registration.classTable(className);
                LuaValue expected = args.narg() > (explicitClassName ? 4 : 3)
                    ? args.arg(explicitClassName ? 5 : 4)
                    : serviceClass == null ? LuaValue.NIL : serviceClass.rawget(functionName);
                return LuaValue.valueOf(registration.unregisterServiceClassFunction(
                    serviceName, className, functionName, expected));
            }
        });
    }

   private static void requireDescribedServiceClass(MLuaRegistration registration, String serviceName) {
      String className = registration.serviceClassName(serviceName);
      requireDescribedServiceClass(registration, serviceName, className);
   }

   private static void requireDescribedServiceClass(MLuaRegistration registration, String serviceName,
                                                    String className) {
      LuaTable serviceClass = registration.classTable(className);
      LuaValue description = serviceClass == null ? LuaValue.NIL
         : serviceClass.rawget("__mlua_doc_description");
      if (serviceClass == null || !serviceClass.rawget("__mlua_service_class").toboolean()
          || !serviceName.equals(serviceClass.rawget("__mlua_service_name").optjstring(""))
          || !description.isstring() || description.tojstring().isBlank()) {
         throw new LuaError("[MLua] RegisterServiceClass with a non-empty description before registering "
            + "functions for service '" + serviceName + "'.");
      }
   }

}
