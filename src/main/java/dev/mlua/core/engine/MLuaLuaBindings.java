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
        final LuaTable classes = registeredClasses(context.registration());
        final LuaTable behaviorMixins = registeredBehaviorMixins(context.registration());
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
        table.set("RegisterClass", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                MLuaRegistration registration = requireMutableRegistration(context);
                String name = args.arg(2).checkjstring();
                LuaValue parent = args.arg(3);
                String description = args.arg(4).checkjstring();
                if (description.isBlank()) {
                    throw new LuaError("[MLua] RegisterClass requires a non-empty class description.");
                }
                String parentName = classParentName(registration, parent);
                LuaTable registered = registration.registerInstantiableClass(name, parentName, description);
                classes.rawset(name, registered);
                return registered;
            }
        });
        table.set("UnregisterClass", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue name) {
                String className = name.checkjstring();
                boolean removed = requireMutableRegistration(context).unregisterClass(className);
                if (removed) classes.rawset(className, LuaValue.NIL);
                return LuaValue.valueOf(removed);
            }
        });
        table.set("GetClass", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue name) {
                LuaTable registered = requireRegistration(context).classTable(name.checkjstring());
                return registered == null ? LuaValue.NIL : registered;
            }
        });
        table.set("GetClasses", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                LuaTable names = new LuaTable();
                int index = 1;
                for (String name : requireRegistration(context).classNames()) {
                    names.rawset(index++, LuaValue.valueOf(name));
                }
                return names;
            }
        });
        table.set("GetBehaviorMixins", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                LuaTable names = new LuaTable();
                int index = 1;
                for (String name : requireRegistration(context).behaviorMixinNames()) {
                    names.rawset(index++, LuaValue.valueOf(name));
                }
                return names;
            }
        });
        table.set("RegisterBehaviorMixin", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                MLuaRegistration registration = requireMutableRegistration(context);
                String name = args.arg(2).checkjstring();
                String description = args.arg(3).checkjstring();
                LuaTable mixin = MLuaBehaviorMixinApi.register(registration, name, description, args.arg(4));
                behaviorMixins.rawset(name, mixin);
                return mixin;
            }
        });
        table.set("RegisterBehaviorMixinFunction", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return requireMutableRegistration(context).registerBehaviorMixinFunction(
                    args.arg(2).checkjstring(), args.arg(3).checkjstring(), args.arg(4), args.arg(5).checkjstring());
            }
        });
        table.set("UnregisterBehaviorMixin", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue name) {
                String mixinName = name.checkjstring();
                boolean removed = requireMutableRegistration(context).unregisterBehaviorMixin(mixinName);
                if (removed) behaviorMixins.rawset(mixinName, LuaValue.NIL);
                return LuaValue.valueOf(removed);
            }
        });
        table.set("UnregisterBehaviorMixinFunction", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                MLuaRegistration registration = requireMutableRegistration(context);
                String mixinName = args.arg(2).checkjstring();
                String functionName = args.arg(3).checkjstring();
                LuaTable mixin = registration.behaviorMixinTable(mixinName);
                LuaValue expected = args.narg() >= 4 ? args.arg(4) : mixin == null ? LuaValue.NIL : mixin.get(functionName);
                return LuaValue.valueOf(registration.unregisterBehaviorMixinFunction(mixinName, functionName, expected));
            }
        });
        table.set("ApplyBehaviorMixin", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                requireMutableRegistration(context).applyBehaviorMixin(args.arg(2).checkjstring(), args.arg(3).checkjstring());
                return LuaValue.NONE;
            }
        });
        table.set("RemoveBehaviorMixin", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(requireMutableRegistration(context).removeBehaviorMixin(
                    args.arg(2).checkjstring(), args.arg(3).checkjstring()));
            }
        });
        table.set("RegisterClassFunction", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                MLuaRegistration registration = requireMutableRegistration(context);
                return registration.registerClassFunction(args.arg(2).checkjstring(),
                    args.arg(3).checkjstring(), args.arg(4), args.arg(5).checkjstring());
            }
        });
        table.set("UnregisterClassFunction", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                MLuaRegistration registration = requireMutableRegistration(context);
                String className = args.arg(2).checkjstring();
                String functionName = args.arg(3).checkjstring();
                LuaTable type = registration.classTable(className);
                LuaValue expected = args.narg() >= 4 ? args.arg(4) : type == null ? LuaValue.NIL : type.rawget(functionName);
                return LuaValue.valueOf(registration.unregisterClassFunction(className, functionName, expected));
            }
        });
        table.set("RegisterClassProperty", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                MLuaRegistration registration = requireMutableRegistration(context);
                String className = args.arg(2).checkjstring();
                String propertyName = args.arg(3).checkjstring();
                LuaValue getter = args.arg(4);
                LuaValue setter;
                String description;
                if (args.narg() >= 7) {
                    boolean readOnly = args.narg() == 7;
                    setter = readOnly ? LuaValue.NIL : args.arg(5);
                    String valueType = args.arg(readOnly ? 5 : 6).checkjstring();
                    String side = args.arg(readOnly ? 6 : 7).checkjstring();
                    description = args.arg(readOnly ? 7 : 8).checkjstring();
                    return registration.registerClassProperty(className, propertyName,
                        getter, setter, valueType, side, description);
                } else if (args.narg() == 5 && args.arg(5).isstring()) {
                    setter = LuaValue.NIL;
                    description = args.arg(5).checkjstring();
                } else {
                    setter = args.arg(5);
                    description = args.arg(6).checkjstring();
                }
                return registration.registerClassProperty(className, propertyName,
                    getter, setter, description);
            }
        });
        table.set("UnregisterClassProperty", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                MLuaRegistration registration = requireMutableRegistration(context);
                String className = args.arg(2).checkjstring();
                String propertyName = args.arg(3).checkjstring();
                LuaTable type = registration.classTable(className);
                LuaValue descriptorValue = type == null ? LuaValue.NIL : type.rawget("__mlua_properties").get(propertyName);
                LuaValue expectedGetter = args.narg() >= 4 ? args.arg(4)
                    : descriptorValue.istable() ? descriptorValue.get("get") : LuaValue.NIL;
                return LuaValue.valueOf(registration.unregisterClassProperty(className, propertyName, expectedGetter));
            }
        });
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
        table.set("CreateInstance", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                MLuaRegistration registration = requireRegistration(context);
                LuaValue fields = args.arg(3);
                if (!fields.isnil() && !fields.istable()) {
                    throw new LuaError("[MLua] CreateInstance fields must be a table when provided.");
                }
                LuaValue classValue = args.arg(2);
                String className;
                if (classValue.isstring()) {
                    className = classValue.checkjstring();
                } else if (classValue.istable()) {
                    LuaTable classTable = classValue.checktable();
                    LuaValue type = classTable.rawget("__type");
                    LuaTable registeredClass = type.isstring() ? registration.classTable(type.tojstring()) : null;
                    boolean registeredView = registeredClass != null
                        && registeredClassForView(registration, classTable) == registeredClass;
                    if (!classTable.rawget("__class").toboolean() || registeredClass == null
                        || (registeredClass != classTable && !registeredView)) {
                        throw new LuaError("[MLua] CreateInstance requires a class registered in this runtime.");
                    }
                    className = type.tojstring();
                } else {
                    throw new LuaError("[MLua] CreateInstance expects a registered class or class name.");
                }
                return registration.createClassInstance(className,
                    fields.istable() ? fields.checktable() : null);
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
        table.set("Classes", classes);
        table.set("BehaviorMixins", behaviorMixins);
        MLuaServiceBindings.addDynamicLookup(table, context);

        return table;
    }

   private static MLuaRegistration requireRegistration(MLuaContext context) {
      MLuaRegistration registration = context.registration();
      if (registration == null) {
         throw new LuaError("[MLua] Class registration is unavailable in this script context.");
      }
      return registration;
   }

   private static LuaTable registeredClassForView(MLuaRegistration registration, LuaTable candidate) {
      LuaValue source = candidate.rawget("__mlua_registered_class_view_source");
      if (!source.isuserdata() || !(source.touserdata() instanceof LuaTable classTable)) return null;
      String className = classTable.rawget("__type").optjstring("");
      return !className.isBlank() && candidate.rawget("__type").optjstring("").equals(className)
         && registration.classTable(className) == classTable ? classTable : null;
   }

   static MLuaRegistration requireMutableRegistration(MLuaContext context) {
      if (context.phase() != RegistrationPhase.REGISTRY) {
         throw new LuaError("[MLua] Class, mixin, and service registrations must run in a registry script.");
      }
      return requireRegistration(context);
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

   private static String classParentName(MLuaRegistration registration, LuaValue value) {
      if (value.isnil()) return null;
      if (value.isstring()) return value.checkjstring();
      if (!value.istable()) {
         throw new LuaError("[MLua] RegisterClass parent must be one class name, registered class, or nil.");
      }
      LuaTable parentClass = value.checktable();
      LuaValue type = parentClass.rawget("__type");
      if (!parentClass.rawget("__class").toboolean() || !type.isstring()) {
         throw new LuaError("[MLua] RegisterClass parent must be one registered class, not a parent list.");
      }
      LuaTable registeredClass = registration.classTable(type.tojstring());
      boolean registeredView = registeredClass != null
         && registeredClassForView(registration, parentClass) == registeredClass;
      if (registeredClass == null || (registeredClass != parentClass && !registeredView)) {
         throw new LuaError("[MLua] RegisterClass parent must be a class registered in this runtime.");
      }
      return type.tojstring();
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

    private static LuaTable registeredClasses(MLuaRegistration registration) {
        LuaTable classes = new LuaTable();
        classes.rawset("__mlua_doc_category", LuaValue.valueOf("Classes"));
        if (registration == null) return classes;
        for (String name : registration.classNames()) {
            LuaTable classTable = registration.classTable(name);
            if (classTable != null) classes.rawset(name, classTable);
        }
        LuaTable meta = new LuaTable();
        meta.set("__index", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue key) {
                if (!key.isstring()) return LuaValue.NIL;
                LuaTable registered = registration.classTable(key.tojstring());
                return registered != null ? registered : LuaValue.NIL;
            }
        });
        classes.setmetatable(meta);
        return classes;
    }

    private static LuaTable registeredBehaviorMixins(MLuaRegistration registration) {
        LuaTable mixins = new LuaTable();
        if (registration == null) return mixins;
        for (String name : registration.behaviorMixinNames()) {
            LuaTable mixin = registration.behaviorMixinTable(name);
            if (mixin != null) mixins.rawset(name, mixin);
        }
        LuaTable meta = new LuaTable();
        meta.set("__index", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue key) {
                if (!key.isstring()) return LuaValue.NIL;
                LuaTable registered = registration.behaviorMixinTable(key.tojstring());
                return registered != null ? registered : LuaValue.NIL;
            }
        });
        mixins.setmetatable(meta);
      return mixins;
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
