package dev.mlua.module;

import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.service.MLuaServiceRegistry;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.loaderapi.EventBridge;
import dev.mlua.common.enums.Side;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.ServiceLoader;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import org.luaj.vm2.Globals;

public final class MLuaModuleLoader {
    private static final MLuaModuleLoader INSTANCE = new MLuaModuleLoader();
    private final List<MLuaModule> modules = new ArrayList<>();
    private final List<MLuaReloadable> reloadables = new ArrayList<>();
    private MLuaServiceRegistry registry;
    private MLuaRuntime base;
    private boolean loaded;
    private boolean documentationLoaded;
    private final Set<MLuaModule> initialized = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<MLuaModule> eventRegistered = Collections.newSetFromMap(new IdentityHashMap<>());
    private final MLuaModuleRegistrations registrations = new MLuaModuleRegistrations();
    private final Set<String> failedModuleIds = new HashSet<>();
    private boolean initializing;
    private final EventBridge injectedEvents;
    private final Boolean injectedClientEnvironment;

    MLuaModuleLoader() {
        this(null, null, null, false);
    }

    MLuaModuleLoader(MLuaRuntime base, EventBridge events, Boolean clientEnvironment, boolean loaded) {
        this.base = base;
        this.registry = base == null ? null : base.services();
        this.injectedEvents = events;
        this.injectedClientEnvironment = clientEnvironment;
        this.loaded = loaded;
    }

    public static MLuaModuleLoader get() {
        return INSTANCE;
    }

    public void loadAll() {
        if (this.loaded) {
            return;
        }
        if (this.documentationLoaded) {
            this.loaded = true;
            this.modules.sort(Comparator.comparingInt(MLuaModule::priority));
            this.orderDependencies();
            MLuaLogger.LOGGER.info("Loaded {} MLua modules", this.modules.size());
            return;
        }
        this.loaded = true;
        Iterable<MLuaModule> providers = LoaderApi.platform().isClient()
            ? ServiceLoader.load(MLuaModule.class)
            : serverProviders();
        // Iterate defensively. A client-only module can fail class resolution on a
        // dedicated server before ServiceLoader.Provider#get returns. The failure
        // must isolate that module instead of aborting the entire server launch.
        var iterator = providers.iterator();
        while (true) {
            try {
                if (!iterator.hasNext()) break;
                MLuaModule module = iterator.next();
                if (module.side() == Side.CLIENT && !LoaderApi.platform().isClient()) {
                    MLuaLogger.LOGGER.debug("Skipped client-only MLua module on dedicated server: {}", module.id());
                    continue;
                }
                if (module.enabled()) this.register(module);
                else MLuaLogger.LOGGER.info("Skipped disabled MLua module: {}", module.id());
            } catch (Throwable error) {
                MLuaLogger.LOGGER.error("Failed to load one MLua module; continuing with the remaining modules", error);
            }
        }
        this.modules.sort(Comparator.comparingInt(MLuaModule::priority));
        this.orderDependencies();
        MLuaLogger.LOGGER.info("Loaded {} MLua modules", this.modules.size());
    }

    /** Loads module declarations for the offline documentation generator without initializing game hooks. */
    public void loadForDocumentation() {
        if (this.loaded || this.documentationLoaded) return;
        this.documentationLoaded = true;
        Iterable<MLuaModule> providers = ServiceLoader.load(MLuaModule.class);
        var iterator = providers.iterator();
        while (true) {
            try {
                if (!iterator.hasNext()) break;
                MLuaModule module = iterator.next();
                if (module.enabled()) this.register(module);
            } catch (Throwable error) {
                MLuaLogger.LOGGER.error("Failed to load one MLua module for documentation; continuing", error);
            }
        }
        this.modules.sort(Comparator.comparingInt(MLuaModule::priority));
        this.orderDependencies();
    }

    public void register(MLuaModule module) {
        if (module == null || !module.enabled()) return;
        MLuaModuleSide sideMetadata = module.getClass().getAnnotation(MLuaModuleSide.class);
        if (sideMetadata != null && module.side() != sideMetadata.value()) {
            throw new IllegalArgumentException("MLua module '" + module.id() + "' declares side "
                + sideMetadata.value() + " but reports " + module.side());
        }
        boolean runtimeReady = this.loaded && this.registry != null;
        if (runtimeReady && !this.supportsSide(module.side())) return;
        if (this.modules.stream().anyMatch(existing -> existing.id().equals(module.id()))) {
            throw new IllegalArgumentException("Duplicate MLua module id: " + module.id());
        }
        List<MLuaModule> candidate = new ArrayList<>(this.modules);
        candidate.add(module);
        candidate.sort(Comparator.comparingInt(MLuaModule::priority));
        if (runtimeReady) candidate = orderedModules(candidate);

        List<MLuaServiceRegistry.Registration> serviceBatch = List.of();
        if (runtimeReady) {
            try {
                serviceBatch = this.registrations.serviceRegistrations(module);
            } catch (Throwable error) {
                this.registrations.forgetServiceEntries(module);
                MLuaLogger.LOGGER.error("MLua module '{}' failed while declaring services and was not registered", module.id(), error);
                return;
            }
            try {
                this.registry.validateBatch(module.side(), serviceBatch);
            } catch (Throwable error) {
                this.registrations.forgetServiceEntries(module);
                MLuaLogger.LOGGER.error("MLua module '{}' has conflicting services and was not registered", module.id(), error);
                return;
            }
            if (this.base != null && !this.registrations.registerClasses(module, this.base)) {
                this.registrations.forgetServiceEntries(module);
                return;
            }
            if (this.base != null && !this.registrations.registerServiceClasses(module, this.base)) {
                this.registrations.rollbackClasses(module, this.base);
                this.registrations.forgetServiceEntries(module);
                return;
            }
            try {
                this.registry.registerBatch(module.side(), serviceBatch);
                this.registrations.rememberServices(this.registry, module, serviceBatch);
            } catch (Throwable error) {
                this.registry.unregisterBatch(module.side(), serviceBatch);
                this.registrations.rollbackClasses(module, this.base);
                this.registrations.forgetServiceEntries(module);
                MLuaLogger.LOGGER.error("MLua module '{}' failed while registering services and was not registered", module.id(), error);
                return;
            }
        }

        this.modules.clear();
        this.modules.addAll(candidate);
        MLuaLogger.LOGGER.info("Registered module: {}", module.id());
        if (runtimeReady) {
            this.registerEvents(module);
            if (!this.initializing) this.initializeModules();
        }
    }

    /** Runs each discovered module after the runtime has installed base classes, services, and event hooks. */
    public void initializeModules() {
        if (this.initializing) return;
        this.initializing = true;
        try {
            while (true) {
                MLuaModule next = this.modules.stream().filter(module -> !this.initialized.contains(module)).findFirst().orElse(null);
                if (next == null) return;
                if (!this.eventRegistered.contains(next)) this.registerEvents(next);
                this.initialize(next);
            }
        } finally {
            this.initializing = false;
        }
    }

    private void initialize(MLuaModule module) {
        if (this.initialized.add(module)) this.invoke(module, "initialize", module::onInitialize);
    }

    private void registerEvents(MLuaModule module) {
        if (this.eventRegistered.add(module)) this.invoke(module, "events", () -> module.registerEvents(this.events()));
    }

    public void registerServices(MLuaServiceRegistry registry) {
        this.registry = registry;
        this.registerServices(registry, null);
    }

    public void registerServices(MLuaServiceRegistry registry, Side side) {
        for (MLuaModule module : this.modules) {
            if (side != null && module.side() != Side.BOTH && module.side() != side) continue;
            if (!this.dependenciesAvailable(module) || !this.registrations.registerServices(module, registry)) {
                this.failedModuleIds.add(module.id());
            }
        }
        this.pruneFailedModules();
    }

    public void registerServices(MLuaRuntime base) {
        this.base = base;
        this.registry = base.services();
        for (MLuaModule module : this.modules) {
            if (!this.dependenciesAvailable(module) || !this.registrations.registerServices(module, this.registry)) {
                this.failedModuleIds.add(module.id());
            }
        }
        this.pruneFailedModules();
    }

    public void registerClasses(MLuaRegistration registration) {
        this.registerClasses(registration, null);
    }

    public void registerClasses(MLuaRegistration registration, Side side) {
        for (MLuaModule module : this.modules) {
            if (side != null && module.side() != Side.BOTH && module.side() != side) continue;
            if (!this.dependenciesAvailable(module) || !this.registrations.registerClasses(module, registration)) {
                this.failedModuleIds.add(module.id());
            }
        }
        for (MLuaModule module : this.modules) {
            if (side != null && module.side() != Side.BOTH && module.side() != side) continue;
            if (this.failedModuleIds.contains(module.id())) continue;
            if (!this.dependenciesAvailable(module)
                || !this.registrations.registerServiceClasses(module, registration)) {
                this.failedModuleIds.add(module.id());
            }
        }
        this.pruneFailedModules();
    }

    /** Dispatches runtime cleanup to loaded feature modules. */
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        for (MLuaModule module : List.copyOf(this.modules)) {
            if (side != Side.BOTH && module.side() != Side.BOTH && module.side() != side) continue;
            this.invoke(module, "runtime state cleanup", () -> module.clearRuntimeState(runtime, runtimeSession, side));
        }
    }

    /** Dispatches addon-owned cleanup to loaded feature modules. */
    public void clearOwnedState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, String ownerModId) {
        if (ownerModId == null || ownerModId.isBlank()) return;
        for (MLuaModule module : List.copyOf(this.modules)) {
            if (side != Side.BOTH && module.side() != Side.BOTH && module.side() != side) continue;
            this.invoke(module, "owned state cleanup", () -> module.clearOwnedState(runtime, runtimeSession, side, ownerModId));
        }
    }

    /** Dispatches side ticks to loaded feature modules. */
    public void onRuntimeTick(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, double deltaTime) {
        for (MLuaModule module : List.copyOf(this.modules)) {
            if (module.side() != Side.BOTH && module.side() != side) continue;
            this.invoke(module, "runtime tick", () -> module.onRuntimeTick(runtime, runtimeSession, side, deltaTime));
        }
    }

    public void afterRuntimeTick(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, double deltaTime) {
        for (MLuaModule module : List.copyOf(this.modules)) {
            if (module.side() != Side.BOTH && module.side() != side) continue;
            this.invoke(module, "after runtime tick", () -> module.afterRuntimeTick(runtime, runtimeSession, side, deltaTime));
        }
    }

    public void onRenderFrame(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, double deltaTime) {
        for (MLuaModule module : List.copyOf(this.modules)) {
            if (module.side() != Side.BOTH && module.side() != Side.CLIENT) continue;
            this.invoke(module, "render frame", () -> module.onRenderFrame(runtime, runtimeSession, deltaTime));
        }
    }

    public void afterRenderFrame(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, double deltaTime) {
        for (MLuaModule module : List.copyOf(this.modules)) {
            if (module.side() != Side.BOTH && module.side() != Side.CLIENT) continue;
            this.invoke(module, "after render frame", () -> module.afterRenderFrame(runtime, runtimeSession, deltaTime));
        }
    }

    public void onRenderFrameEnd(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession) {
        for (MLuaModule module : List.copyOf(this.modules)) {
            if (module.side() != Side.BOTH && module.side() != Side.CLIENT) continue;
            this.invoke(module, "render frame end", () -> module.onRenderFrameEnd(runtime, runtimeSession));
        }
    }

    public void beforeScriptPhase(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side,
                                  dev.mlua.common.enums.RegistrationPhase phase, Object phaseContext) {
        for (MLuaModule module : List.copyOf(this.modules)) {
            if (module.side() != Side.BOTH && module.side() != side) continue;
            this.invoke(module, "before script phase", () -> module.beforeScriptPhase(runtime, runtimeSession, side, phase, phaseContext));
        }
    }

    public void afterScriptPhase(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side,
                                 dev.mlua.common.enums.RegistrationPhase phase, Object phaseContext) {
        for (MLuaModule module : List.copyOf(this.modules)) {
            if (module.side() != Side.BOTH && module.side() != side) continue;
            this.invoke(module, "after script phase", () -> module.afterScriptPhase(runtime, runtimeSession, side, phase, phaseContext));
        }
    }

    private boolean dependenciesAvailable(MLuaModule module) {
        return module.dependencies().stream().noneMatch(this.failedModuleIds::contains);
    }

    private void pruneFailedModules() {
        boolean changed;
        do {
            changed = false;
            for (MLuaModule module : this.modules) {
                if (!this.failedModuleIds.contains(module.id()) && !this.dependenciesAvailable(module)) {
                    this.failedModuleIds.add(module.id());
                    changed = true;
                }
            }
        } while (changed);
        this.modules.removeIf(module -> {
            if (!this.failedModuleIds.contains(module.id())) return false;
            this.registrations.rollbackClasses(module, null);
            this.registrations.unregisterServices(module);
            this.initialized.remove(module);
            this.eventRegistered.remove(module);
            MLuaLogger.LOGGER.error("MLua module '{}' was disabled after its registration failed", module.id());
            return true;
        });
    }

    private boolean supportsSide(Side side) {
        boolean clientEnvironment = this.injectedClientEnvironment != null
            ? this.injectedClientEnvironment
            : LoaderApi.platform().isClient();
        return supportsSide(side, clientEnvironment);
    }

    private EventBridge events() {
        return this.injectedEvents == null ? LoaderApi.events() : this.injectedEvents;
    }

    static boolean supportsSide(Side moduleSide, boolean clientEnvironment) {
        return clientEnvironment || moduleSide != Side.CLIENT;
    }

    public void contributeGlobals(Globals globals) {
        this.contributeGlobals(globals, null);
    }

    public void contributeGlobals(Globals globals, Side side) {
        for (MLuaModule module : this.modules) {
            if (side != null && module.side() != Side.BOTH && module.side() != side) continue;
            this.invoke(module, "globals", () -> module.contributeGlobals(globals));
        }
    }

    public void contributeContextGlobals(Globals globals, MLuaContext context) {
        for (MLuaModule module : this.modules) {
            if (module.side() != Side.BOTH && module.side() != context.side()) continue;
            this.invoke(module, "globals", () -> module.contributeGlobals(globals, context));
        }
    }

    public void registerEventListeners() {
        for (MLuaModule module : this.modules) {
            this.registerEvents(module);
            MLuaLogger.LOGGER.debug("  Registered event hooks from module '{}'", module.id());
        }
    }

    private Iterable<MLuaModule> serverProviders() {
        List<MLuaModule> result = new ArrayList<>();
        String serviceName = "META-INF/services/" + MLuaModule.class.getName();
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = classLoader.getResources(serviceName);
            while (resources.hasMoreElements()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    resources.nextElement().openStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String providerName = line.replaceFirst("#.*$", "").trim();
                        if (providerName.isEmpty()) continue;
                        try {
                            Class<?> providerType = Class.forName(providerName, false, classLoader);
                            MLuaModuleSide metadata = providerType.getAnnotation(MLuaModuleSide.class);
                            if (metadata != null && metadata.value() == Side.CLIENT) {
                                MLuaLogger.LOGGER.debug("Skipped client-only MLua module on dedicated server: {}", providerName);
                                continue;
                            }
                            Class<?> provider = Class.forName(providerName, true, classLoader);
                            result.add((MLuaModule) provider.getDeclaredConstructor().newInstance());
                        } catch (Throwable error) {
                            MLuaLogger.LOGGER.debug("Skipped unavailable MLua server module provider {}", providerName, error);
                        }
                    }
                }
            }
        } catch (Throwable error) {
            MLuaLogger.LOGGER.warn("Could not discover MLua server module providers", error);
        }
        return result;
    }

    public void generateDocs(MLuaDocBuilder docs) {
        for (MLuaModule module : this.modules) {
            this.invoke(module, "docs", () -> module.contributeDocs(docs));
        }
    }

    public void onReload() {
        for (MLuaReloadable reloadable : this.reloadables) {
            try {
                reloadable.onReload();
            } catch (Throwable throwable) {
                MLuaLogger.LOGGER.error("Failed to reload MLua lifecycle owner {}", reloadable.getClass().getName(), throwable);
            }
        }
        for (MLuaModule module : this.modules) {
            if (!this.reloadables.contains(module)) {
                try {
                    module.onReload();
                } catch (Throwable throwable) {
                    MLuaLogger.LOGGER.error("Failed to reload MLua module '{}'", module.id(), throwable);
                }
            }
        }
    }

    public void onReload(Side side) {
        for (MLuaModule module : this.modules) {
            if (module.side() != Side.BOTH && module.side() != side) continue;
            try {
                module.onReload(side);
            } catch (Throwable throwable) {
                MLuaLogger.LOGGER.error("Failed to reload MLua module '{}' for {}", module.id(), side, throwable);
            }
        }
    }

    public void onUnload() {
        List<MLuaReloadable> reverse = new ArrayList<>(this.reloadables);
        Collections.reverse(reverse);
        for (MLuaReloadable reloadable : reverse) {
            try {
                reloadable.onUnload();
            } catch (Throwable throwable) {
                MLuaLogger.LOGGER.error("Failed to unload MLua lifecycle owner {}", reloadable.getClass().getName(), throwable);
            }
        }
        List<MLuaModule> reverseModules = new ArrayList<>(this.modules);
        Collections.reverse(reverseModules);
        for (MLuaModule module : reverseModules) {
            try {
                module.onUnload();
            } catch (Throwable throwable) {
                MLuaLogger.LOGGER.error("Failed to unload MLua module '{}'", module.id(), throwable);
            }
        }
    }

    public void registerReloadable(MLuaReloadable reloadable) {
        if (reloadable != null && !this.reloadables.contains(reloadable)) {
            this.reloadables.add(reloadable);
        }
    }

    public List<MLuaModule> modules() {
        return Collections.unmodifiableList(this.modules);
    }

    public MLuaModule module(String id) {
        return this.modules.stream().filter(module -> module.id().equals(id)).findFirst().orElse(null);
    }

    private void orderDependencies() {
        MLuaModuleOrder.Result result = MLuaModuleOrder.resolve(this.modules);
        result.failures().forEach((id, failure) -> {
            if (failure.cause() != null) {
                MLuaLogger.LOGGER.error("MLua module '{}' {} and was skipped", id, failure.message(), failure.cause());
            } else {
                MLuaLogger.LOGGER.error("MLua module '{}' {} and was skipped", id, failure.message());
            }
        });
        this.failedModuleIds.addAll(result.failures().keySet());
        this.modules.clear();
        this.modules.addAll(result.ordered());
        this.pruneFailedModules();
    }

    static List<MLuaModule> orderedModules(List<MLuaModule> input) {
        return MLuaModuleOrder.requireOrder(input);
    }

    private void invoke(MLuaModule module, String phase, Runnable action) {
        this.invokeSuccessfully(module, phase, action);
    }

    private boolean invokeSuccessfully(MLuaModule module, String phase, Runnable action) {
        try {
            action.run();
            return true;
        } catch (Throwable throwable) {
            MLuaLogger.LOGGER.error("MLua module '{}' failed during {} and was isolated", module.id(), phase, throwable);
            return false;
        }
    }
}
