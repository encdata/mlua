package dev.mlua.core.engine;

import dev.mlua.api.MLuaApiPolicy;
import dev.mlua.api.MLuaLuaCoercions;
import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.core.signal.MLuaConnection;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.module.MLuaModuleLoader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

public final class MLuaLuaBindings {
   private static final String API_VERSION = MLuaApiPolicy.VERSION;
   private static final List<String> CAPABILITIES = MLuaApiPolicy.capabilities().stream().sorted().toList();

   private MLuaLuaBindings() {
   }

    public static LuaTable minecraftTable(final MLuaContext context) {
        LuaTable table = new LuaTable();
        table.set("GetService", new TwoArgFunction() {
            public LuaValue call(LuaValue self, LuaValue name) {
                return MLuaServiceBindings.liveServiceProxy(context, name.checkjstring());
            }
        });
        table.set("GetServiceNames", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return MLuaServiceBindings.serviceNames(context);
            }
        });
        MLuaClassBindings.bindInto(table, context);
        table.set("RegisterServiceFunction", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (args.narg() != 4 && args.narg() != 5) {
                    throw new LuaError("[MLua] RegisterServiceFunction expects (service, function, description) "
                        + "or (service, function, implementation, description).");
                }
                MLuaRegistration registration = requireMutableRegistration(context);
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
                MLuaRegistration registration = requireMutableRegistration(context);
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
                MLuaRegistration registration = requireMutableRegistration(context);
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
                MLuaRegistration registration = requireMutableRegistration(context);
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
        table.set("IsServer", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(context.side() == Side.SERVER);
            }
        });
        table.set("IsClient", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(context.side() == Side.CLIENT);
            }
        });
        table.set("GetSide", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(context.side().name());
            }
        });
        table.set("GetApiVersion", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(API_VERSION);
            }
        });
        table.set("HasCapability", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue name) {
                return LuaValue.valueOf(supportsCapability(name.checkjstring()));
            }
        });
        table.set("ApiVersion", API_VERSION);
        table.set("Capabilities", capabilitiesTable());
        MLuaServiceBindings.addDynamicLookup(table, context);

        return table;
    }

   static MLuaRegistration requireMutableRegistration(MLuaContext context) {
      if (context.phase() != RegistrationPhase.REGISTRY) {
         throw new LuaError("[MLua] Class, mixin, and service registrations must run in a registry script.");
      }
      MLuaRegistration registration = context.registration();
      if (registration == null) {
         throw new LuaError("[MLua] Class registration is unavailable in this script context.");
      }
      return registration;
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

    private static LuaTable capabilitiesTable() {
        LuaTable table = new LuaTable();
        int i = 1;
        for (String capability : CAPABILITIES) {
            if (!supportsCapability(capability)) continue;
            table.set(i++, capability);
            table.set(capability, LuaValue.TRUE);
        }
        return table;
    }

    private static boolean supportsCapability(String capability) {
        try {
            return MLuaApiPolicy.supports(capability, LoaderApi.platform().loaderName());
        } catch (IllegalStateException unavailable) {
            return false;
        }
    }

    public static LuaTable scriptContextTable(final MLuaContext context) {
        LuaTable table = minecraftTable(context);
        table.set("Minecraft", table);
        table.set("ModId", context.ownerModId());
        table.set("ScriptName", context.script().displayName());
        table.set("Phase", LuaValue.valueOf(context.phase().name()));
        table.set("IsRuntime", LuaValue.valueOf(context.phase() == RegistrationPhase.RUNTIME));

        return table;
    }

   public static LuaTable materializeServiceTable(MLuaContext context, String serviceName) {
      return MLuaServiceBindings.materializeServiceTable(context, serviceName);
   }

   public static void bindContext(Globals globals, MLuaContext context) {
      MLuaContextBindings.bindContext(globals, context);
   }

   public static void configureContextGlobals(Globals globals, MLuaContext context) {
      MLuaContextBindings.configureContextGlobals(globals, context);
   }

   public static <T> T withContext(MLuaContext context, java.util.function.Supplier<T> callback) {
      return MLuaContextBindings.withContext(context, callback);
   }

   public static <T> T withExecutionLock(MLuaContext context, java.util.function.Supplier<T> callback) {
      return MLuaContextBindings.withExecutionLock(context, callback);
   }

   public static MLuaContext activeContext(MLuaRegistration registration) {
      return MLuaContextBindings.activeContext(registration);
   }

   public static MLuaContext currentContext() {
      return MLuaContextBindings.currentContext();
   }

   private static Map<String, String> readServiceMethodDescriptions(LuaValue value) {
      if (!value.istable()) {
         throw new LuaError("[MLua] RegisterServiceClass method descriptions must be a table of function names to descriptions.");
      }
      Map<String, String> descriptions = new java.util.LinkedHashMap<>();
      LuaTable table = value.checktable();
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs entry = table.next(key);
         key = entry.arg1();
         if (key.isnil()) break;
         if (!key.isstring() || key.tojstring().isBlank()
            || !entry.arg(2).isstring() || entry.arg(2).tojstring().isBlank()) {
            throw new LuaError("[MLua] RegisterServiceClass method descriptions must use non-empty function names and descriptions.");
         }
         descriptions.put(key.tojstring(), entry.arg(2).tojstring());
      }
      return descriptions;
   }

   public static MLuaContext boundContext(Globals globals) {
      return MLuaContextBindings.boundContext(globals);
   }

   public static LuaTable taskTable(final MLuaTaskSchedulerView scheduler, final Globals globals) {
      return MLuaSignalBindings.taskTable(scheduler, globals);
   }

   public static LuaTable signalTable(final Globals globals, final MLuaTaskSchedulerView scheduler) {
      return MLuaSignalBindings.signalTable(globals, scheduler);
   }

   public static LuaTable signalInstance(final MLuaSignal signal, final Globals globals) {
      return MLuaSignalBindings.signalInstance(signal, globals);
   }

   public static LuaTable signalInstance(final MLuaSignal signal, final Globals globals, final MLuaTaskSchedulerView scheduler) {
      return MLuaSignalBindings.signalInstance(signal, globals, scheduler);
   }

   public static LuaTable signalInstance(final MLuaSignal signal, final Globals globals, final MLuaTaskSchedulerView scheduler, final String ownerKey) {
      return MLuaSignalBindings.signalInstance(signal, globals, scheduler, ownerKey);
   }

   public static LuaTable signalInstance(final MLuaSignal signal, final Globals globals, final MLuaTaskSchedulerView scheduler, final String ownerKey, final Side side) {
      return MLuaSignalBindings.signalInstance(signal, globals, scheduler, ownerKey, side);
   }

   public static LuaTable connection(final MLuaConnection connection, MLuaRegistration registration) {
      return MLuaSignalBindings.connection(connection, registration);
   }

   public static Map<String, Object> mapFromLua(LuaTable table) {
      try {
         return MLuaLuaCoercions.mapFromLua(table);
      } catch (IllegalArgumentException failure) {
         throw new LuaError("[MLua] " + failure.getMessage());
      }
   }

   public static Object javaValue(LuaValue value) {
      return MLuaLuaCoercions.javaValue(value);
   }

   public static LuaValue toLuaValue(Object value) {
      return MLuaLuaCoercions.toLuaValue(value);
   }
}
