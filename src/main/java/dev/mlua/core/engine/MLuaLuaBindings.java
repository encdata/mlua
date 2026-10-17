package dev.mlua.core.engine;

import dev.mlua.api.MLuaApiPolicy;
import dev.mlua.api.MLuaLuaCoercions;
import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.Side;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceProperties;
import dev.mlua.core.signal.MLuaConnection;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModuleLoader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

public final class MLuaLuaBindings {
   private static final ThreadLocal<WeakReference<MLuaContext>> ACTIVE_CONTEXT = new ThreadLocal<>();
   private static final Map<Globals, Set<String>> CONTEXT_GLOBAL_NAMES = new WeakHashMap<>();
   private static final String API_VERSION = MLuaApiPolicy.VERSION;
   private static final List<String> CAPABILITIES = MLuaApiPolicy.capabilities().stream().sorted().toList();
   private static final LuaValue CONTEXT_KEY = LuaValue.valueOf("__mlua_context");

   private MLuaLuaBindings() {
   }

    public static LuaTable minecraftTable(final MLuaContext context) {
        LuaTable table = new LuaTable();
        final LuaTable classes = registeredClasses(context.registration());
        final LuaTable behaviorMixins = registeredBehaviorMixins(context.registration());
        table.set("GetService", new TwoArgFunction() {
            public LuaValue call(LuaValue self, LuaValue name) {
                return MLuaLuaBindings.liveServiceProxy(context, name.checkjstring());
            }
        });
        table.set("GetServiceNames", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                LuaTable names = new LuaTable();
                int index = 1;
                Set<String> available = new TreeSet<>(context.services().availableNames(context.side()));
                MLuaRegistration registration = context.registration();
                if (registration != null) {
                    Set<String> registeredServiceNames = context.services().names();
                    for (String className : registration.classNames()) {
                        LuaTable serviceClass = registration.classTable(className);
                        if (serviceClass == null || !serviceClass.rawget("__mlua_service_class").toboolean()) continue;
                        String serviceName = serviceClass.rawget("__mlua_service_name").optjstring("");
                        if (!serviceName.isBlank() && !registeredServiceNames.contains(serviceName)) {
                            available.add(serviceName);
                        }
                    }
                }
                for (String name : available) {
                    names.rawset(index++, LuaValue.valueOf(name));
                }
                return names;
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
        addDynamicServiceLookup(table, context);

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

   private static MLuaRegistration requireMutableRegistration(MLuaContext context) {
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

    private static void addDynamicServiceLookup(LuaTable table, MLuaContext context) {
        LuaTable meta = new LuaTable();
        meta.set("__index", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue self, LuaValue key) {
                if (!key.isstring()) return LuaValue.NIL;
                String name = key.tojstring();
                if (!context.services().isAvailable(name, context.side())
                    && (context.services().isRegistered(name)
                        || !isLuaBackedService(context.registration(), name))) return LuaValue.NIL;
                return liveServiceProxy(context, name);
            }
        });
        table.setmetatable(meta);
    }

   private static boolean isLuaBackedService(MLuaRegistration registration, String serviceName) {
      if (registration == null) return false;
      String className = registration.serviceClassName(serviceName);
      LuaTable type = registration.classTable(className);
      return type != null && type.rawget("__mlua_service_class").toboolean()
         && serviceName.equals(type.rawget("__mlua_service_name").optjstring(""));
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

   private static LuaTable liveServiceProxy(final MLuaContext context, final String serviceName) {
      return context.serviceProxy(serviceName, () -> {
         MLuaRegistration registration = context.registration();
         String serviceClassName = null;
         if (registration != null) {
            String candidate = registration.serviceClassName(serviceName);
            LuaTable serviceClass = registration.classTable(candidate);
            boolean registeredJavaService = context.services().isRegistered(serviceName);
            if (registeredJavaService) {
               if (serviceClass == null || !serviceClass.rawget("__mlua_service_class").toboolean()
                   || !serviceName.equals(serviceClass.rawget("__mlua_service_name").optjstring(""))) {
                  throw missingServiceClass(serviceName, candidate);
               }
               serviceClassName = candidate;
            } else if (serviceClass != null && serviceClass.rawget("__mlua_service_class").toboolean()
                && serviceName.equals(serviceClass.rawget("__mlua_service_name").optjstring(""))) {
               serviceClassName = candidate;
            }
         }
         LuaTable proxy = new LuaTable();
         LuaTable meta = new LuaTable();
         meta.rawset("__mlua_service_name", LuaValue.valueOf(serviceName));
         meta.set("__mlua_service_resolver", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
               LuaTable service = MLuaLuaBindings.resolveServiceTable(context, serviceName);
               meta.rawset("__mlua_service_instance", service);
               return service;
            }
         });
         meta.set("__index", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
               LuaTable service = MLuaLuaBindings.resolveServiceTable(context, serviceName);
               meta.rawset("__mlua_service_instance", service);
               LuaValue key = args.arg(2);
               if (registration != null) {
                  String activeClassName = attachServiceClassIfAvailable(
                     context, registration, serviceName, proxy);
                  LuaTable serviceClass = activeClassName == null
                     ? null : registration.classTable(activeClassName);
                  LuaValue registeredMethod = serviceClass == null ? LuaValue.NIL : serviceClass.get(key);
                  if (registeredMethod.isfunction()) return registeredMethod;
                  if (service.rawget(key).isfunction()) return LuaValue.NIL;
               }
               return service.get(key);
            }
         });
         meta.set("__newindex", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
               LuaTable service = MLuaLuaBindings.resolveServiceTable(context, serviceName);
               meta.rawset("__mlua_service_instance", service);
               LuaValue key = args.arg(2);
               LuaValue value = args.arg(3);
               service.set(key, value);
               return LuaValue.NONE;
            }
         });
         proxy.setmetatable(meta);
         if (registration != null && serviceClassName != null) {
            registration.attachClassInstance(proxy, serviceClassName, registration.serviceBaseClassName());
         }
         return proxy;
      });
   }

   private static String attachServiceClassIfAvailable(MLuaContext context, MLuaRegistration registration,
                                                       String serviceName, LuaTable proxy) {
      String className = proxy.rawget("__type").optjstring(null);
      LuaTable serviceClass = className == null ? null : registration.classTable(className);
      if (serviceClass == null || !serviceClass.rawget("__mlua_service_class").toboolean()
          || !serviceName.equals(serviceClass.rawget("__mlua_service_name").optjstring(""))) {
         className = registration.serviceClassName(serviceName);
         serviceClass = registration.classTable(className);
      }
      boolean registeredJavaService = context.services().isRegistered(serviceName);
      if (registeredJavaService && (serviceClass == null
          || !serviceClass.rawget("__mlua_service_class").toboolean()
          || !serviceName.equals(serviceClass.rawget("__mlua_service_name").optjstring("")))) {
         throw missingServiceClass(serviceName, className);
      }
      if (serviceClass == null || !serviceClass.rawget("__mlua_service_class").toboolean()
          || !serviceName.equals(serviceClass.rawget("__mlua_service_name").optjstring(""))) {
         return null;
      }
      if (!className.equals(proxy.rawget("__type").optjstring(""))) {
         registration.attachClassInstance(proxy, className, registration.serviceBaseClassName());
      }
      return className;
   }

   private static LuaError missingServiceClass(String serviceName, String className) {
      return new LuaError("[MLua] Service '" + serviceName + "' has no registered Lua class '"
         + className + "'. Register its service class before scripts request it.");
   }

   private static LuaTable resolveServiceTable(final MLuaContext context, final String serviceName) {
      return context.materializeService(serviceName, () -> {
         if (!context.services().isRegistered(serviceName)) {
            MLuaRegistration registration = context.registration();
            if (registration != null) {
               String className = registration.serviceClassName(serviceName);
               LuaTable serviceClass = registration.classTable(className);
               if (serviceClass != null && serviceClass.rawget("__mlua_service_class").toboolean()
                   && serviceName.equals(serviceClass.rawget("__mlua_service_name").optjstring(""))) {
                  java.util.function.Supplier<LuaTable> createInstance = () -> {
                     LuaTable instance = new LuaTable();
                     instance.rawset("__service", LuaValue.TRUE);
                     LuaValue description = serviceClass.rawget("__mlua_doc_description");
                     if (description.isstring()) instance.rawset("__mlua_doc_description", description);
                     registration.attachClassInstance(instance, className, registration.serviceBaseClassName());
                     context.recordMaterializedService(serviceName, instance);
                     try {
                        registration.initializeClassInitializers(instance, className, new LuaTable());
                     } catch (RuntimeException | Error failure) {
                        context.forgetMaterializedService(serviceName, instance);
                        throw failure;
                     }
                     return instance;
                  };
                  LuaTable instance = context.session() == null ? createInstance.get()
                     : context.session().materializeLuaService(context.side(), serviceName, serviceClass, createInstance);
                  context.recordMaterializedService(serviceName, instance);
                  return instance;
               }
            }
         }
         MLuaService service = context.services().create(serviceName, context);
         try {
            LuaTable table = service.toLua();
            if (table == null) throw new LuaError("[MLua] Service '" + serviceName + "' returned no Lua table");
            table.rawset("__service", LuaValue.TRUE);
            table = MLuaServiceProperties.attach(table);
            attachServiceClass(context.registration(), table, serviceName);
            return table;
         } catch (RuntimeException failure) {
            throw new LuaError(new RuntimeException("[MLua] Could not materialize service '" + serviceName
               + "' for mod '" + context.ownerModId() + "' on " + context.side()
               + " scripts during " + context.phase() + ": " + failure.getMessage(), failure));
         }
      });
   }

   private static void attachServiceClass(MLuaRegistration registration, LuaTable instance, String serviceName) {
      if (registration == null) return;
      String className = registration.serviceClassName(serviceName);
      LuaTable serviceClass = registration.classTable(className);
      if (serviceClass == null || !serviceClass.rawget("__mlua_service_class").toboolean()
          || !serviceName.equals(serviceClass.rawget("__mlua_service_name").optjstring(""))) {
         throw missingServiceClass(serviceName, className);
      }
      LuaValue description = instance.rawget("__mlua_doc_description");
      registration.registerServiceClass(serviceName, className,
         description.isstring() ? description.tojstring() : null);
      MLuaClassMethods.registerServiceMethods(registration, className, instance);
      registration.attachClassInstance(instance, className, registration.serviceBaseClassName());
   }

   public static LuaTable materializeServiceTable(MLuaContext context, String serviceName) {
      return resolveServiceTable(context, serviceName);
   }

   public static void bindContext(Globals globals, MLuaContext context) {
      if (context == null) ACTIVE_CONTEXT.remove();
      else ACTIVE_CONTEXT.set(new WeakReference<>(context));
      if (globals != null) {
         globals.set(CONTEXT_KEY, context != null ? CoerceJavaToLua.coerce(context) : LuaValue.NIL);
         if (context != null) globals.set("Docs", context.documentationTable(() -> documentationTable(context)));
      }
   }

   /** Binds all globals whose values depend on the active script context. */
   public static void configureContextGlobals(Globals globals, MLuaContext context) {
      if (globals == null) return;
      if (context == null) {
         bindContext(globals, null);
         clearContextGlobals(globals);
         return;
      }
      synchronized (context) {
         if (context.contextGlobalsConfigured()) {
            boolean alreadyBound = boundContext(globals) == context;
            bindContext(globals, context);
            if (!alreadyBound) applyContextGlobals(globals, context);
            return;
         }
         clearContextGlobals(globals);
         Map<String, LuaValue> before = snapshotGlobals(globals);
         bindContext(globals, context);
         LuaTable task = taskTable(context.scheduler(), globals);
         globals.set("task", task);
         globals.set("wait", task.get("wait"));
         globals.set("Minecraft", minecraftTable(context));
         MLuaModuleLoader.get().contributeContextGlobals(globals, context);
         Map<String, LuaValue> after = snapshotGlobals(globals);
         Map<String, LuaValue> bindings = new LinkedHashMap<>();
         Set<String> keys = new java.util.TreeSet<>(before.keySet());
         keys.addAll(after.keySet());
         for (String key : keys) {
            LuaValue previous = before.getOrDefault(key, LuaValue.NIL);
            LuaValue current = after.getOrDefault(key, LuaValue.NIL);
            if (previous != current) bindings.put(key, current);
         }
         context.configureContextGlobals(bindings);
         synchronized (CONTEXT_GLOBAL_NAMES) {
            Set<String> names = CONTEXT_GLOBAL_NAMES.computeIfAbsent(globals, ignored -> new java.util.TreeSet<>());
            names.addAll(bindings.keySet());
         }
      }
   }

   private static void clearContextGlobals(Globals globals) {
      synchronized (CONTEXT_GLOBAL_NAMES) {
         Set<String> names = CONTEXT_GLOBAL_NAMES.get(globals);
         if (names != null) for (String name : names) globals.set(name, LuaValue.NIL);
      }
   }

   private static Map<String, LuaValue> snapshotGlobals(Globals globals) {
      Map<String, LuaValue> snapshot = new LinkedHashMap<>();
      for (LuaValue key : globals.keys()) {
         if (key.isstring()) snapshot.put(key.tojstring(), globals.rawget(key));
      }
      return snapshot;
   }

   private static void applyContextGlobals(Globals globals, MLuaContext context) {
      Map<String, LuaValue> bindings = context.contextGlobals();
      synchronized (CONTEXT_GLOBAL_NAMES) {
         Set<String> names = CONTEXT_GLOBAL_NAMES.computeIfAbsent(globals, ignored -> new java.util.TreeSet<>());
         names.addAll(bindings.keySet());
         for (String name : names) globals.set(name, bindings.getOrDefault(name, LuaValue.NIL));
      }
      // The context marker and documentation table are bound by bindContext.
   }

   /** Runs Java-dispatched Lua work with its owner context and lock, restoring the caller context afterward. */
   public static <T> T withContext(MLuaContext context, java.util.function.Supplier<T> callback) {
      java.util.Objects.requireNonNull(callback, "callback");
      if (context == null) return callback.get();
      Globals globals = context.globals();
      org.luaj.vm2.LuaThread running = globals == null ? null : globals.running;
      if (running != null && running.isRunningOnCurrentThread()) {
         // LuaJ resumes coroutines on a child thread while the resuming thread
         // waits with the execution monitor held. Rebinding context on that same
         // coroutine is safe, but reacquiring the monitor would deadlock.
         MLuaContext previousBound = boundContext(globals);
         MLuaContext previous = currentContext();
         MLuaContext restore = previous != null && previous.globals() == globals ? previous : previousBound;
         configureContextGlobals(globals, context);
         try {
            return callback.get();
         } finally {
            configureContextGlobals(globals, restore);
         }
      }
      MLuaContext previous = currentContext();
      MLuaContext previousBound = boundContext(globals);
      MLuaContext restore = previous != null && previous.globals() == globals ? previous : previousBound;
      synchronized (context.executionLock()) {
         configureContextGlobals(globals, context);
         try {
            return callback.get();
         } finally {
            configureContextGlobals(globals, restore);
         }
      }
   }

   /** Returns the Lua context active on this thread for a Java-backed callback. */
   public static MLuaContext activeContext(MLuaRegistration registration) {
      MLuaContext context = currentContext();
      return context != null && context.registration() == registration ? context : null;
   }

   /** Returns the context active on this thread, if any. */
   public static MLuaContext currentContext() {
      WeakReference<MLuaContext> reference = ACTIVE_CONTEXT.get();
      MLuaContext context = reference == null ? null : reference.get();
      if (context == null && reference != null) ACTIVE_CONTEXT.remove();
      return context;
   }

   private static LuaTable documentationTable(MLuaContext context) {
      LuaTable docs = new LuaTable();
      docs.rawset("__type", LuaValue.valueOf("DocumentationRegistry"));
      docs.rawset("__mlua_doc_description", LuaValue.valueOf("Register addon guides, tutorial steps, examples, and reusable script templates."));
      LuaTable methodDocs = new LuaTable();
      methodDocs.rawset("Register", LuaValue.valueOf("Adds an addon authored guide, Getting Started steps, examples, or reusable script templates during registry startup."));
      methodDocs.rawset("Unregister", LuaValue.valueOf("Removes a guide registered by this addon. Accepts the addon's short id or its owner-qualified id."));
      docs.rawset("__mlua_doc_methods", methodDocs);
      docs.set("Register", new TwoArgFunction() {
         @Override public LuaValue call(LuaValue self, LuaValue value) {
            MLuaRegistration registration = requireMutableRegistration(context);
            if (!value.istable()) throw new LuaError("[MLua] Docs:Register expects a table.");
            LuaTable input = value.checktable();
            String owner = context.ownerModId();
            String id = requiredDocString(input, "id");
            if (!id.contains(":")) id = owner + ":" + id;
            else if (!id.startsWith(owner + ":")) {
               throw new LuaError("[MLua] Documentation id must use this addon's namespace ('" + owner + "').");
            }
            String section = requiredDocString(input, "section");
            String title = requiredDocString(input, "title");
            String description = requiredDocString(input, "description");
            int order = input.rawget("order").isnumber() ? input.rawget("order").toint() : 0;
            List<MLuaDocBuilder.GuideStep> steps = new ArrayList<>();
            readDocArray(input.rawget("steps"), step -> steps.add(new MLuaDocBuilder.GuideStep(
               "Docs", requiredDocString(step, "title"), requiredDocString(step, "description"), requiredDocString(step, "example"))));
            List<MLuaDocBuilder.Example> examples = new ArrayList<>();
            readDocArray(input.rawget("examples"), example -> examples.add(new MLuaDocBuilder.Example(
               requiredDocString(example, "title"), requiredDocString(example, "description"), requiredDocString(example, "code"))));
            List<MLuaDocBuilder.Template> templates = new ArrayList<>();
            readDocArray(input.rawget("templates"), template -> templates.add(new MLuaDocBuilder.Template(
            requiredDocString(template, "title"), requiredDocString(template, "description"),
               requiredDocString(template, "filename"), requiredDocString(template, "code"))));
            try {
               registration.registerDocumentation(new MLuaDocBuilder.Contribution(
                  id, section, title, description, owner, order, steps, examples, templates));
            } catch (RuntimeException failure) {
               throw new LuaError("[MLua] Could not register documentation: " + failure.getMessage());
            }
            return LuaValue.valueOf(id);
         }
      });
      docs.set("Unregister", new TwoArgFunction() {
         @Override public LuaValue call(LuaValue self, LuaValue value) {
            MLuaRegistration registration = requireMutableRegistration(context);
            if (!value.isstring() || value.tojstring().isBlank()) {
               throw new LuaError("[MLua] Docs:Unregister expects a non-empty documentation id.");
            }
            String owner = context.ownerModId();
            String id = value.tojstring();
            if (!id.contains(":")) id = owner + ":" + id;
            else if (!id.startsWith(owner + ":")) {
               throw new LuaError("[MLua] Documentation id must use this addon's namespace ('" + owner + "').");
            }
            return LuaValue.valueOf(registration.unregisterDocumentation(id, owner));
         }
      });
      return docs;
   }

   private static String requiredDocString(LuaTable table, String key) {
      LuaValue value = table.rawget(key);
      if (!value.isstring() || value.tojstring().isBlank()) {
         throw new LuaError("[MLua] Documentation field '" + key + "' must be a non-empty string.");
      }
      return value.tojstring();
   }

    private static void readDocArray(LuaValue value, java.util.function.Consumer<LuaTable> consumer) {
      if (value.isnil()) return;
      if (!value.istable()) throw new LuaError("[MLua] Documentation steps, examples, and templates must be arrays.");
      LuaTable array = value.checktable();
      java.util.Set<Integer> indices = new java.util.TreeSet<>();
      LuaValue key = LuaValue.NIL;
      while (true) {
         org.luaj.vm2.Varargs entry = array.next(key);
         key = entry.arg1();
         if (key.isnil()) break;
         if (!key.isnumber()) {
            throw new LuaError("[MLua] Documentation steps, examples, and templates must use dense array tables.");
         }
         double numericIndex = key.todouble();
         int index = key.toint();
         if (index < 1 || numericIndex != index || !indices.add(index)) {
            throw new LuaError("[MLua] Documentation steps, examples, and templates must use dense array tables.");
         }
      }
      int expected = 1;
      for (int index : indices) {
         if (index != expected++) {
            throw new LuaError("[MLua] Documentation steps, examples, and templates must use dense array tables.");
         }
      }
      for (int i = 1; i <= indices.size(); i++) {
         LuaValue item = array.rawget(i);
         if (!item.istable()) throw new LuaError("[MLua] Each documentation step, example, or template must be a table.");
         consumer.accept(item.checktable());
      }
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
      if (globals == null) {
         return null;
      }

      LuaValue value = globals.get(CONTEXT_KEY);
      return value.isuserdata(MLuaContext.class) ? (MLuaContext)value.touserdata(MLuaContext.class) : null;
   }

   public static LuaTable taskTable(final MLuaTaskSchedulerView scheduler, final Globals globals) {
      LuaTable task = new LuaTable();
      task.set("spawn", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaValue fn = args.arg1();
            scheduler.spawn(globals, fn, args.subargs(2));
            return LuaValue.NIL;
         }
      });
      task.set("defer", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaValue fn = args.arg1();
            scheduler.defer(globals, fn, args.subargs(2));
            return LuaValue.NIL;
         }
      });
      task.set("delay", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            LuaValue ticks = args.arg1();
            LuaValue fn = args.arg(2);
            scheduler.delay(globals, Math.max(0L, ticks.tolong()), fn, args.subargs(3));
            return LuaValue.NIL;
         }
      });
      task.set("wait", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            long ticks = args.narg() != 0 && !args.arg1().isnil() ? Math.max(0L, args.arg1().tolong()) : 1L;
            return scheduler.waitForTicks(globals, ticks);
         }
      });
      return task;
   }

   public static LuaTable signalTable(final Globals globals, final MLuaTaskSchedulerView scheduler) {
      LuaTable signal = new LuaTable();
      signal.set("new", new ZeroArgFunction() {
         public LuaValue call() {
            return MLuaLuaBindings.signalInstance(new MLuaSignal(), globals, scheduler);
         }
      });
      return signal;
   }

   public static LuaTable signalInstance(final MLuaSignal signal, final Globals globals) {
      return signalInstance(signal, globals, null);
   }

   public static LuaTable signalInstance(final MLuaSignal signal, final Globals globals, final MLuaTaskSchedulerView scheduler) {
      return signalInstance(signal, globals, scheduler, null);
   }

   public static LuaTable signalInstance(final MLuaSignal signal, final Globals globals, final MLuaTaskSchedulerView scheduler, final String ownerKey) {
      return signalInstance(signal, globals, scheduler, ownerKey, null);
   }

   public static LuaTable signalInstance(final MLuaSignal signal, final Globals globals, final MLuaTaskSchedulerView scheduler, final String ownerKey, final Side side) {
      MLuaContext ownerContext = boundContext(globals);
      MLuaRegistration registration = ownerContext == null ? null : ownerContext.registration();
      String resolvedOwner = ownerKey != null ? ownerKey : ownerContext != null ? ownerContext.ownerModId() : null;
      Side resolvedSide = side != null ? side : ownerContext != null ? ownerContext.side() : null;
      signal.bind(globals, scheduler);
      LuaTable table = nativeInstance(new LuaTable(), registration, "Signal");
      table.rawset("__mlua_signal", LuaValue.userdataOf(signal));
      bindNativeInstanceMethod(table, registration, "Signal", "Connect", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue fn) {
            return MLuaLuaBindings.connection(signal.connectOwned(fn, resolvedOwner, resolvedSide, globals, scheduler, ownerContext), registration);
         }
      });
      bindNativeInstanceMethod(table, registration, "Signal", "Once", new TwoArgFunction() {
         public LuaValue call(LuaValue self, LuaValue fn) {
            return MLuaLuaBindings.connection(signal.onceOwned(fn, resolvedOwner, resolvedSide, globals, scheduler, ownerContext), registration);
         }
      });
      bindNativeInstanceMethod(table, registration, "Signal", "Fire", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            signal.fire(args.subargs(2));
            return LuaValue.NONE;
         }
      });
      bindNativeInstanceMethod(table, registration, "Signal", "Wait", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            if (globals == null) {
               throw new LuaError("Signal:Wait is not supported on this signal");
            }
            return signal.wait(globals, resolvedOwner, resolvedSide);
         }
      });
      return table;
   }

   private static LuaTable connection(final MLuaConnection connection, MLuaRegistration registration) {
      LuaTable table = nativeInstance(new LuaTable(), registration, "Connection");
      table.rawset("__mlua_connection", LuaValue.userdataOf(connection));
      bindNativeInstanceMethod(table, registration, "Connection", "Disconnect", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            connection.disconnect();
            return LuaValue.NIL;
         }
      });
      bindNativeInstanceMethod(table, registration, "Connection", "GetConnected", new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            return LuaValue.valueOf(connection.connected());
         }
      });
      return table;
   }

   private static LuaTable nativeInstance(LuaTable instance, MLuaRegistration registration, String className) {
      return registration != null && registration.classTable(className) != null
         ? registration.attachClassInstance(instance, className, null) : instance;
   }

   private static void bindNativeInstanceMethod(LuaTable instance, MLuaRegistration registration,
                                                String className, String methodName, LuaValue implementation) {
      if (registration != null && registration.classTable(className) != null
         && registration.isInstanceOf(instance, className)) {
         MLuaClassMethods.setInstanceMethod(instance, methodName, implementation);
      } else {
         instance.set(methodName, implementation);
      }
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
