package dev.mlua.module;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.service.MLuaService;
import dev.mlua.loaderapi.EventBridge;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaModuleLoaderRegistrationTest {
    @Test
    void runtimeModuleSnapshotIsReusedAndPublishedOnRegistrationChanges() {
        MLuaModuleLoader loader = new MLuaModuleLoader(null, null, true, false);
        List<MLuaModule> empty = loader.moduleSnapshot();

        assertSame(empty, loader.moduleSnapshot());
        MLuaModule module = recordingModule("snapshot", List.of(), 0, new ArrayList<>());
        loader.register(module);

        List<MLuaModule> registered = loader.moduleSnapshot();
        assertNotSame(empty, registered);
        assertSame(registered, loader.moduleSnapshot());
        assertEquals(List.of(module), registered);
        assertSame(module, loader.module("snapshot"));
        assertThrows(UnsupportedOperationException.class, () -> registered.add(module));

        assertTrue(loader.unregister(module));
        assertNotSame(registered, loader.moduleSnapshot());
        assertTrue(loader.moduleSnapshot().isEmpty());
        assertNull(loader.module("snapshot"));
    }

    @Test
    void lateModuleRegistersClassesServicesEventsAndInitializesOnce() {
        List<String> phases = new ArrayList<>();
        EventBridge events = (EventBridge) Proxy.newProxyInstance(
            EventBridge.class.getClassLoader(), new Class<?>[]{EventBridge.class},
            (proxy, method, args) -> null
        );
        MLuaRuntime runtime = runtimeWithCoreClasses();
        MLuaModuleLoader loader = new MLuaModuleLoader(runtime, events, true, true);

        loader.register(recordingModule("core", List.of(), 0, phases));
        loader.register(recordingModule("addon", List.of("core"), 5, phases));
        loader.initializeModules();

        assertEquals(List.of("core:classes", "core:events", "core:init", "addon:classes", "addon:events", "addon:init"), phases);
        assertEquals(List.of("core", "addon"), loader.modules().stream().map(MLuaModule::id).toList());
        assertTrue(runtime.services().names().contains("service_core"));
        assertTrue(runtime.services().names().contains("service_addon"));
    }

    @Test
    void missingDependencyLeavesLiveRegistrationUntouched() {
        MLuaRuntime runtime = runtimeWithCoreClasses();
        EventBridge events = (EventBridge) Proxy.newProxyInstance(
            EventBridge.class.getClassLoader(), new Class<?>[]{EventBridge.class}, (proxy, method, args) -> null
        );
        MLuaModuleLoader loader = new MLuaModuleLoader(runtime, events, true, true);

        assertThrows(IllegalStateException.class, () -> loader.register(recordingModule("orphan", List.of("absent"), 0, new ArrayList<>())));
        assertTrue(loader.modules().isEmpty());
        assertTrue(runtime.services().names().isEmpty());
    }

    @Test
    void conflictingServiceDoesNotRegisterTheNewModuleOrItsClass() {
        MLuaRuntime runtime = runtimeWithCoreClasses();
        EventBridge events = (EventBridge) Proxy.newProxyInstance(
            EventBridge.class.getClassLoader(), new Class<?>[]{EventBridge.class}, (proxy, method, args) -> null
        );
        MLuaModuleLoader loader = new MLuaModuleLoader(runtime, events, true, true);
        loader.register(serviceModule("first", "shared", "FirstClass", new ArrayList<>()));
        int count = loader.modules().size();

        loader.register(serviceModule("second", "shared", "SecondClass", new ArrayList<>()));

        assertEquals(count, loader.modules().size());
        assertNull(runtime.classTable("SecondClass"));
        assertTrue(runtime.services().names().contains("shared"));
    }

    @Test
    void failedClassHookRollsBackClassesAndRejectsModule() {
        MLuaRuntime runtime = runtimeWithCoreClasses();
        EventBridge events = (EventBridge) Proxy.newProxyInstance(
            EventBridge.class.getClassLoader(), new Class<?>[]{EventBridge.class}, (proxy, method, args) -> null
        );
        MLuaModuleLoader loader = new MLuaModuleLoader(runtime, events, true, true);
        MLuaModule broken = new MLuaModule() {
            @Override public String id() { return "broken"; }
            @Override public Side side() { return Side.BOTH; }
            @Override public void registerClasses(MLuaRegistration registration) {
                registration.registerClass("PartialClass", null, "A class used by the rollback fixture.");
                throw new IllegalStateException("registration failed");
            }
            @Override public List<ServiceEntry> services() {
                return List.of(new ServiceEntry("partial_service", context -> null,
                    "Service used by the rollback fixture.", java.util.Map.of("Probe", "Returns the fixture probe value.")));
            }
        };

        loader.register(broken);

        assertTrue(loader.modules().isEmpty());
        assertNull(runtime.classTable("PartialClass"));
        assertFalse(runtime.services().names().contains("partial_service"));
    }

    @Test
    void bootstrapServiceConflictRemovesOnlyTheFailingModuleAndItsClasses() {
        MLuaRuntime runtime = runtimeWithCoreClasses();
        MLuaModuleLoader loader = new MLuaModuleLoader();
        loader.register(serviceModule("first", "shared_service", "FirstClass", new ArrayList<>()));
        loader.register(serviceModule("second", "shared_service", "SecondClass", new ArrayList<>()));

        loader.registerClasses(runtime);
        loader.registerServices(runtime);

        assertEquals(List.of("first"), loader.modules().stream().map(MLuaModule::id).toList());
        assertTrue(runtime.services().names().contains("shared_service"));
        assertNull(runtime.classTable("SecondClass"));
    }

    @Test
    void moduleAddedDuringInitializationIsInstalledAndInitializedInOrder() {
        List<String> phases = new ArrayList<>();
        EventBridge events = (EventBridge) Proxy.newProxyInstance(
            EventBridge.class.getClassLoader(), new Class<?>[]{EventBridge.class}, (proxy, method, args) -> null
        );
        MLuaModuleLoader loader = new MLuaModuleLoader(runtimeWithCoreClasses(), events, true, true);
        MLuaModule child = recordingModule("child", List.of("parent"), 0, phases);
        MLuaModule parent = new MLuaModule() {
            @Override public String id() { return "parent"; }
            @Override public Side side() { return Side.BOTH; }
            @Override public void onInitialize() {
                phases.add("parent:init");
                loader.register(child);
            }
            @Override public void registerClasses(MLuaRegistration registration) {
                phases.add("parent:classes");
                registration.registerClass("Class_parent", null, "A class used by the parent fixture.");
            }
            @Override public void registerEvents(EventBridge bridge) { phases.add("parent:events"); }
        };

        loader.register(parent);

        assertEquals(List.of("parent:classes", "parent:events", "parent:init", "child:classes", "child:events", "child:init"), phases);
        assertEquals(List.of("parent", "child"), loader.modules().stream().map(MLuaModule::id).toList());
    }

    @Test
    void scopedModuleCloseRemovesServicesClassesAndEventCallbacks() throws Exception {
        List<Runnable> callbacks = new ArrayList<>();
        EventBridge events = (EventBridge) Proxy.newProxyInstance(
            EventBridge.class.getClassLoader(), new Class<?>[]{EventBridge.class},
            (proxy, method, args) -> {
                if (method.getName().equals("onClientTickEnd")) callbacks.add((Runnable) args[0]);
                return null;
            });
        MLuaRuntime runtime = runtimeWithCoreClasses();
        MLuaModuleLoader loader = new MLuaModuleLoader(runtime, events, true, true);
        AtomicInteger ticks = new AtomicInteger();
        AtomicInteger unloads = new AtomicInteger();
        MLuaModule module = new MLuaModule() {
            @Override public String id() { return "scoped_addon"; }
            @Override public Side side() { return Side.BOTH; }
            @Override public void registerClasses(MLuaRegistration registration) {
                registration.registerClass("ScopedAddonClass", null,
                    "Class owned by the scoped addon registration test.");
            }
            @Override public List<ServiceEntry> services() {
                return List.of(new ServiceEntry("scoped_addon_service", "ScopedAddonService", context -> null,
                    "Service owned by the scoped addon registration test.",
                    Map.of("Probe", "Returns the scoped addon service probe value.")));
            }
            @Override public void registerEvents(EventBridge bridge) { bridge.onClientTickEnd(ticks::incrementAndGet); }
            @Override public void onUnload() { unloads.incrementAndGet(); }
        };

        AutoCloseable registration = loader.registerScoped(module);
        assertTrue(runtime.services().isRegistered("scoped_addon_service"));
        assertTrue(runtime.classTable("ScopedAddonClass") != null);
        assertTrue(runtime.classTable("ScopedAddonService") != null);
        callbacks.get(0).run();
        assertEquals(1, ticks.get());

        registration.close();
        registration.close();
        callbacks.get(0).run();

        assertEquals(1, ticks.get());
        assertEquals(1, unloads.get());
        assertFalse(runtime.services().isRegistered("scoped_addon_service"));
        assertNull(runtime.classTable("ScopedAddonClass"));
        assertNull(runtime.classTable("ScopedAddonService"));
        assertNull(loader.module("scoped_addon"));
    }

    @Test
    void scopedModuleCannotCloseWhileDependentIsActiveAndCanRetryAfterward() throws Exception {
        MLuaModuleLoader loader = new MLuaModuleLoader();
        MLuaModule parent = recordingModule("scoped_parent", List.of(), 0, new ArrayList<>());
        MLuaModule child = recordingModule("scoped_child", List.of("scoped_parent"), 1, new ArrayList<>());
        AutoCloseable parentRegistration = loader.registerScoped(parent);
        AutoCloseable childRegistration = loader.registerScoped(child);

        assertThrows(IllegalStateException.class, parentRegistration::close);
        assertEquals(parent, loader.module("scoped_parent"));
        childRegistration.close();
        parentRegistration.close();
        assertNull(loader.module("scoped_parent"));
    }

    @Test
    void failedScopedInitializationRevokesPartialEventRegistration() {
        List<Runnable> callbacks = new ArrayList<>();
        EventBridge events = (EventBridge) Proxy.newProxyInstance(
            EventBridge.class.getClassLoader(), new Class<?>[]{EventBridge.class},
            (proxy, method, args) -> {
                if (method.getName().equals("onClientTickEnd")) callbacks.add((Runnable) args[0]);
                return null;
            });
        MLuaRuntime runtime = runtimeWithCoreClasses();
        MLuaModuleLoader loader = new MLuaModuleLoader(runtime, events, true, true);
        AtomicInteger callbackCalls = new AtomicInteger();
        AtomicInteger unloadCalls = new AtomicInteger();
        MLuaModule broken = new MLuaModule() {
            @Override public String id() { return "broken_scoped_addon"; }
            @Override public Side side() { return Side.BOTH; }
            @Override public void registerClasses(MLuaRegistration registration) {
                registration.registerClass("BrokenScopedClass", null,
                    "Class used to verify failed addon cleanup.");
            }
            @Override public void registerEvents(EventBridge bridge) {
                bridge.onClientTickEnd(callbackCalls::incrementAndGet);
            }
            @Override public void onInitialize() { throw new IllegalStateException("test initialization failure"); }
            @Override public void onUnload() { unloadCalls.incrementAndGet(); }
        };

        assertThrows(IllegalStateException.class, () -> loader.registerScoped(broken));
        assertEquals(1, callbacks.size());
        callbacks.get(0).run();
        assertEquals(0, callbackCalls.get());
        assertEquals(1, unloadCalls.get());
        assertNull(runtime.classTable("BrokenScopedClass"));
        assertNull(loader.module("broken_scoped_addon"));
    }

    private static MLuaModule recordingModule(String id, List<String> dependencies, int priority, List<String> phases) {
        return new MLuaModule() {
            @Override public String id() { return id; }
            @Override public Side side() { return Side.BOTH; }
            @Override public List<String> dependencies() { return dependencies; }
            @Override public int priority() { return priority; }
            @Override public void registerClasses(MLuaRegistration registration) {
                phases.add(id + ":classes");
                registration.registerClass("Class_" + id, null, "A class used by the " + id + " fixture.");
            }
            @Override public List<ServiceEntry> services() {
                return List.of(new ServiceEntry("service_" + id, context -> null,
                    "Service used by the loader fixture.", java.util.Map.of("Probe", "Returns the fixture probe value.")));
            }
            @Override public void registerEvents(EventBridge events) { phases.add(id + ":events"); }
            @Override public void onInitialize() { phases.add(id + ":init"); }
        };
    }

    private static MLuaModule serviceModule(String id, String service, String className, List<String> phases) {
        return new MLuaModule() {
            @Override public String id() { return id; }
            @Override public Side side() { return Side.BOTH; }
            @Override public void registerClasses(MLuaRegistration registration) {
                phases.add(id + ":classes");
                registration.registerClass(className, "Service", "Service used by the registration fixture.");
            }
            @Override public List<ServiceEntry> services() {
                return List.of(new ServiceEntry(service, className, context -> null,
                    "Service used by the registration fixture.", java.util.Map.of("Probe", "Returns the fixture probe value.")));
            }
        };
    }

    private static MLuaRuntime runtimeWithCoreClasses() {
        MLuaRuntime runtime = new MLuaRuntime();
        runtime.registerClass("Object", null, "Root class used by module registration tests.");
        runtime.registerInstanceBaseClass("Instance", "Object",
            "Base class for live module registration test instances.");
        runtime.registerServiceBaseClass("Service", "Instance",
            "Base class for module registration test services.");
        return runtime;
    }
}
