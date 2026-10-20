package dev.mlua.core.engine;

import dev.mlua.core.MLuaContext;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.core.service.MLuaServiceProperties;
import dev.mlua.module.MLuaRegistration;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

/** Owns Lua service discovery, stable proxies, and per-context service materialization. */
final class MLuaServiceBindings {
    private MLuaServiceBindings() { }

    static LuaTable serviceNames(MLuaContext context) {
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
        for (String name : available) names.rawset(index++, LuaValue.valueOf(name));
        return names;
    }

    static void addDynamicLookup(LuaTable table, MLuaContext context) {
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

    static LuaTable liveServiceProxy(MLuaContext context, String serviceName) {
        return context.serviceProxy(serviceName, () -> {
            MLuaRegistration registration = context.registration();
            String serviceClassName = null;
            if (registration != null) {
                String candidate = registration.serviceClassName(serviceName);
                LuaTable serviceClass = registration.classTable(candidate);
                boolean registeredJavaService = context.services().isRegistered(serviceName);
                if (registeredJavaService) {
                    if (!isServiceClass(serviceClass, serviceName)) {
                        throw missingServiceClass(serviceName, candidate);
                    }
                    serviceClassName = candidate;
                } else if (isServiceClass(serviceClass, serviceName)) {
                    serviceClassName = candidate;
                }
            }

            LuaTable proxy = new LuaTable();
            LuaTable meta = new LuaTable();
            meta.rawset("__mlua_service_name", LuaValue.valueOf(serviceName));
            meta.set("__mlua_service_resolver", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    LuaTable service = resolveServiceTable(context, serviceName);
                    meta.rawset("__mlua_service_instance", service);
                    return service;
                }
            });
            meta.set("__index", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    LuaTable service = resolveServiceTable(context, serviceName);
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
                    LuaTable service = resolveServiceTable(context, serviceName);
                    meta.rawset("__mlua_service_instance", service);
                    service.set(args.arg(2), args.arg(3));
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
        if (!isServiceClass(serviceClass, serviceName)) {
            className = registration.serviceClassName(serviceName);
            serviceClass = registration.classTable(className);
        }
        if (context.services().isRegistered(serviceName) && !isServiceClass(serviceClass, serviceName)) {
            throw missingServiceClass(serviceName, className);
        }
        if (!isServiceClass(serviceClass, serviceName)) return null;
        if (!className.equals(proxy.rawget("__type").optjstring(""))) {
            registration.attachClassInstance(proxy, className, registration.serviceBaseClassName());
        }
        return className;
    }

    private static boolean isServiceClass(LuaTable serviceClass, String serviceName) {
        return serviceClass != null && serviceClass.rawget("__mlua_service_class").toboolean()
            && serviceName.equals(serviceClass.rawget("__mlua_service_name").optjstring(""));
    }

    private static LuaError missingServiceClass(String serviceName, String className) {
        return new LuaError("[MLua] Service '" + serviceName + "' has no registered Lua class '"
            + className + "'. Register its service class before scripts request it.");
    }

    private static LuaTable resolveServiceTable(MLuaContext context, String serviceName) {
        return context.materializeService(serviceName, () -> {
            MLuaRegistration registration = context.registration();
            if (!context.services().isRegistered(serviceName) && registration != null) {
                String className = registration.serviceClassName(serviceName);
                LuaTable serviceClass = registration.classTable(className);
                if (isServiceClass(serviceClass, serviceName)) {
                    Supplier<LuaTable> createInstance = () -> createLuaServiceInstance(
                        context, registration, serviceName, className, serviceClass);
                    LuaTable instance = context.session() == null ? createInstance.get()
                        : context.session().materializeLuaService(
                            context.side(), serviceName, serviceClass, createInstance);
                    context.recordMaterializedService(serviceName, instance);
                    return instance;
                }
            }
            return materializeJavaService(context, serviceName);
        });
    }

    private static LuaTable createLuaServiceInstance(MLuaContext context, MLuaRegistration registration,
                                                     String serviceName, String className, LuaTable serviceClass) {
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
    }

    private static LuaTable materializeJavaService(MLuaContext context, String serviceName) {
        MLuaService service = context.services().create(serviceName, context);
        try {
            LuaTable table = service.toLua();
            if (table == null) throw new LuaError("[MLua] Service '" + serviceName + "' returned no Lua table");
            table.rawset("__service", LuaValue.TRUE);
            MLuaServiceBase.normalizeServiceCalls(table, serviceName);
            table = MLuaServiceProperties.attach(table);
            attachServiceClass(context.registration(), table, serviceName);
            return table;
        } catch (RuntimeException failure) {
            throw new LuaError(new RuntimeException("[MLua] Could not materialize service '" + serviceName
                + "' for mod '" + context.ownerModId() + "' on " + context.side()
                + " scripts during " + context.phase() + ": " + failure.getMessage(), failure));
        }
    }

    private static void attachServiceClass(MLuaRegistration registration, LuaTable instance, String serviceName) {
        if (registration == null) return;
        String className = registration.serviceClassName(serviceName);
        LuaTable serviceClass = registration.classTable(className);
        if (!isServiceClass(serviceClass, serviceName)) throw missingServiceClass(serviceName, className);
        LuaValue description = instance.rawget("__mlua_doc_description");
        registration.registerServiceClass(serviceName, className,
            description.isstring() ? description.tojstring() : null);
        MLuaClassMethods.registerServiceMethods(registration, className, instance);
        registration.attachClassInstance(instance, className, registration.serviceBaseClassName());
    }

    static LuaTable materializeServiceTable(MLuaContext context, String serviceName) {
        return resolveServiceTable(context, serviceName);
    }
}
