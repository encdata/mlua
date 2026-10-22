package dev.mlua.core;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import dev.mlua.core.engine.LuaJEngineBackend;
import dev.mlua.core.engine.MLuaEngine;
import dev.mlua.core.require.MLuaModuleCache;
import dev.mlua.core.scheduler.MLuaTaskScheduler;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.core.script.MLuaScriptRoot;
import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.mixin.MixinRuleEngine;
import dev.mlua.common.mixin.MLuaMixinCallbackRegistry;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.runtime.client.MLuaGeneratedAssetPack;
import dev.mlua.core.service.MLuaServiceRegistry;
import dev.mlua.core.service.MLuaService;
import dev.mlua.minecraft.binding.MLuaLevelWrapper;
import dev.mlua.core.classes.MLuaClassRegistry;
import dev.mlua.runtime.registry.MLuaContentTypeRegistry;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import dev.mlua.api.registry.MLuaRegistry;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.minecraft.network.MLuaNetworkManager;
import dev.mlua.module.MLuaModuleLoader;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaRegistration;
import com.mojang.brigadier.CommandDispatcher;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaTable;

public final class MLuaRuntime implements MLuaRegistration {
   private final MLuaRegistry<MLuaDocBuilder.Contribution> documentationContributions = new MLuaRegistry<>();

   @Override
   public void registerDocumentation(MLuaDocBuilder.Contribution contribution) {
      java.util.Objects.requireNonNull(contribution, "contribution");
      if (!this.documentationContributions.register(contribution.owner(), contribution.id(), contribution)) {
         throw new IllegalArgumentException("Duplicate documentation id: " + contribution.id());
      }
   }

   @Override
   public boolean unregisterDocumentation(String id, String owner) {
      return this.documentationContributions.remove(owner, id);
   }

   public List<MLuaDocBuilder.Contribution> documentationContributions() {
      return this.documentationContributions.snapshot().values().stream()
         .sorted(java.util.Comparator.comparing((MLuaDocBuilder.Contribution contribution) ->
               MLuaDocBuilder.sectionKey(contribution.section()))
            .thenComparingInt(MLuaDocBuilder.Contribution::order)
            .thenComparing(MLuaDocBuilder.Contribution::id))
         .toList();
   }
   private static final MLuaRuntime INSTANCE = new MLuaRuntime();
   private final MLuaRuntimeScriptCatalog scriptCatalog = new MLuaRuntimeScriptCatalog();
   private final MLuaRuntimeDiagnostics diagnostics = new MLuaRuntimeDiagnostics();
   private final MLuaRuntimeSession session;
   private final MLuaServiceRegistry services = new MLuaServiceRegistry();
   private final MLuaClassRegistry classes = new MLuaClassRegistry();
   private volatile long lifecycleEpoch;
   private final MLuaClientScriptWatcher clientScriptWatcher = new MLuaClientScriptWatcher();
   private final Map<Path, MLuaScriptRegistration> registryScriptRegistrations = new LinkedHashMap<>();
   private final Set<Level> entrypointWorlds = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
   private final ConcurrentLinkedQueue<ClientWorldLifecycleTransition> pendingClientWorldTransitions =
      new ConcurrentLinkedQueue<>();
   private MLuaEngine engine;
   private boolean bootstrapped;
   private boolean registryScriptsRan;
   private boolean serverRan;
   private boolean clientRan;

   public MLuaRuntime() {
      this(new MLuaRuntimeSession());
   }

   public MLuaRuntime(MLuaRuntimeSession session) {
      this.session = Objects.requireNonNull(session, "session");
      this.session.attachRegistration(this);
      this.session.setFailureReporter(this::reportRuntimeFailure);
   }

   public static MLuaRuntime get() {
      return INSTANCE;
   }

   public static MLuaRegistration registrationForLevel(Level level) {
      return registrationForSession(sessionForLevel(level));
   }

   public static MLuaRegistration registrationForServer(MinecraftServer server) {
      return registrationForSession(sessionForServer(server));
   }

   public static MLuaRuntimeSession sessionForLevel(Level level) {
      return level == null || level.isClientSide() ? get().session() : sessionForServer(level.getServer());
   }

   public static MLuaRuntimeSession sessionForServer(MinecraftServer server) {
      if (server != null) {
         MLuaRuntimeSession session = MLuaRuntimeSession.forServer(server);
         if (session != null) return session;
      }
      return get().session();
   }

   private static MLuaRegistration registrationForSession(MLuaRuntimeSession session) {
      return session.registration() != null ? session.registration() : get();
   }

   public MLuaRuntimeSession session() {
      return this.session;
   }

   public void reportRuntimeFailure(String summary, Throwable throwable) {
      String message = this.diagnostics.recordFailure(summary, throwable);
      MLuaLogger.LOGGER.error(message, throwable);
   }

   List<String> recentServerDiagnostics() {
      return this.diagnostics.recentServerMessages();
   }

   public void bootstrap() {
      if (!this.bootstrapped) {
         this.bootstrapped = true;
          this.registerBundledModScriptRoots();
          this.registerScriptRoot("mlua", LoaderApi.platform().mluaScriptDir());
          MLuaModuleLoader.get().loadAll();
          MLuaModuleLoader.get().registerClasses(this);
          this.services.names().forEach(name -> {
             registerServiceClassIfAvailable(name, this.serviceClassName(name));
          });
          MLuaModuleLoader.get().registerServices(this);
          MLuaModuleLoader.get().registerEventListeners();
          MLuaModuleLoader.get().initializeModules();
          this.ensureScriptsCurrent();
      }
   }

   public void registerScriptRoot(String modId, Path path) {
      MLuaScriptRoot root = new MLuaScriptRoot(modId, path);
      if (!this.scriptCatalog.registerRoot(root)) return;
      if (this.registryScriptsRan) {
         MLuaLogger.LOGGER.info("Registered runtime script root for '{}': {}. Registry scripts in this root will not run this load cycle.", root.modId(), root.path());
      }

   }

   public synchronized void replaceResourceScripts(List<MLuaScript> scripts) {
      MLuaRuntimeScriptCatalog.ResourceReload reload = this.scriptCatalog.replaceResourceScripts(scripts);
      MLuaLogger.LOGGER.info("Loaded {} resource-pack MLua scripts", scripts.size());
      this.onResourceScriptsReload(reload.replacedPaths(), reload.nativeRegistryScriptsChanged());
   }

   private void onResourceScriptsReload(Set<Path> replacedScriptPaths, boolean nativeRegistryScriptsChanged) {
      this.lifecycleEpoch++;
      for (Path path : replacedScriptPaths) this.unregisterMixinScope(path);
      boolean nativeRegistriesFrozen = LoaderApi.registry().isFrozen();
      if (!nativeRegistriesFrozen) {
         this.clearRegistryScriptRegistrations(replacedScriptPaths);
      }
      this.session.serverScheduler().removeScripts(replacedScriptPaths);
      this.session.clientScheduler().removeScripts(replacedScriptPaths);
      this.forceRefreshScripts();
      this.session.clearModuleCache(Side.BOTH);
      MLuaModuleLoader.get().clearRuntimeState(this, this.session, Side.BOTH);
      if (nativeRegistriesFrozen && nativeRegistryScriptsChanged) {
         MLuaLogger.LOGGER.warn("[MLua] Registry-phase scripts changed during resource reload, but Minecraft registries are frozen. Existing registrations remain active; restart the game to apply the new script definitions.");
      }
      for (MLuaScript script : this.scriptCatalog.resourceScripts()) {
         if (!MLuaRuntimeScriptCatalog.isRegistryPhaseScript(script)) continue;
         if (nativeRegistriesFrozen) continue;
         try {
            this.executeRegistryScript(script, script.type() != ScriptType.REGISTRY);
         } catch (Throwable throwable) {
            this.reportRuntimeFailure("Registry script '" + script.displayName() + "' failed during reload", throwable);
         }
      }
      for (MLuaScript script : this.scriptCatalog.resourceScripts()) {
         try {
            if (script.type() == ScriptType.REGISTRY) {
               continue;
            } else if (script.type().isMixin()) {
               Side mixinSide = script.type().mixinSide();
               if (mixinSide == Side.CLIENT && !LoaderApi.platform().isClient()) continue;
               this.execute(script, mixinSide, RegistrationPhase.MIXIN, null);
            } else if (script.type() == ScriptType.SERVER && this.session.server() != null) {
               this.execute(script, Side.SERVER, RegistrationPhase.RUNTIME, this.session.server());
            } else if (script.type() == ScriptType.CLIENT && LoaderApi.platform().isClient()) {
               this.execute(script, Side.CLIENT, RegistrationPhase.RUNTIME, this.session.server());
            }
         } catch (Throwable throwable) {
            if (script.type().isMixin()) this.unregisterMixinScope(script.path());
            this.reportRuntimeFailure("Resource-pack script '" + script.displayName() + "' failed during reload", throwable);
         }
      }
      MinecraftServer activeServer = this.session.server();
      if (activeServer != null) {
         this.runCommandScripts(activeServer.getCommands().getDispatcher());
         this.runEntrypointScripts(this.scriptCatalog.resourceScripts(), "run_server", Side.SERVER, activeServer);
         this.reloadWorldLoadEntrypoints(activeServer);
      }
      if (LoaderApi.platform().isClient()) {
         this.runClientCommandScripts();
         this.runEntrypointScripts(this.scriptCatalog.resourceScripts(), "run_client", Side.CLIENT, activeServer);
         this.reloadClientWorldLoadEntrypoints(true);
      }
      MixinRuleEngine.triggerRetransform();
      this.generateAssetPack();
      MLuaDocumentation.generate(LoaderApi.platform().gameDir());
   }

   private void runEntrypointScripts(List<MLuaScript> scripts, String entrypointName, Side side,
                                    MinecraftServer server) {
      for (MLuaScript script : scripts) {
         if (!script.type().supportsEntrypoint(entrypointName, side)
               || !ScriptType.declaresEntrypoint(script.source(), entrypointName)) continue;
         try {
            MLuaContext context = this.contextFor(script, side, RegistrationPhase.RUNTIME, server);
            this.engine.executeEntrypoint(script, context, entrypointName);
         } catch (RuntimeException failure) {
            MLuaLogger.LOGGER.error("Resource reload entrypoint '{}' in '{}' failed: {}",
               entrypointName, script.displayName(), failure.getMessage());
            this.reportRuntimeFailure("Resource reload entrypoint '" + entrypointName + "' failed in "
               + script.displayName(), failure);
         }
      }
   }

   private void registerBundledModScriptRoots() {
      LoaderApi.platform().bundledScriptRoots().forEach(root -> {
         this.registerScriptRoot(root.modId(), root.path());
         MLuaLogger.LOGGER.info("Registered bundled MLua scripts for '{}': {}", root.modId(), root.path());
      });
   }

   public MLuaServiceRegistry services() {
      return this.services;
   }

   @Deprecated(forRemoval = false)
   public void registerService(String name, Function<MLuaService.Context, MLuaService> factory) {
      this.registerService(name, Side.BOTH, factory, null, Map.of());
   }

   @Deprecated(forRemoval = false)
   public void registerService(String name, Side side, Function<MLuaService.Context, MLuaService> factory) {
      this.registerService(name, side, factory, null, Map.of());
   }

   public void registerService(String name, Function<MLuaService.Context, MLuaService> factory,
                               String description, Map<String, String> functionDescriptions) {
      this.registerService(name, Side.BOTH, factory, description, functionDescriptions);
   }

   public void registerService(String name, String className,
                               Function<MLuaService.Context, MLuaService> factory,
                               String description, Map<String, String> functionDescriptions) {
      this.registerService(name, className, Side.BOTH, factory, description, functionDescriptions);
   }

   public synchronized void registerService(String name, Side side, Function<MLuaService.Context, MLuaService> factory,
                                            String description, Map<String, String> functionDescriptions) {
      registerService(name, null, side, factory, description, functionDescriptions);
   }

   public synchronized void registerService(String name, String className, Side side,
                                            Function<MLuaService.Context, MLuaService> factory,
                                            String description, Map<String, String> functionDescriptions) {
      if (className != null && className.isBlank()) {
         throw new IllegalArgumentException("Service class name must be non-empty");
      }
      String existingClassName = this.services.explicitClassName(name);
      String recordedClassName = existingClassName != null ? existingClassName : this.registeredServiceClassName(name);
      if (recordedClassName != null && className != null && !recordedClassName.equals(className)) {
         throw new IllegalStateException("Service '" + name + "' is already assigned to Lua class '"
            + recordedClassName + "'");
      }
      String selectedClassName = className != null ? className : recordedClassName != null ? recordedClassName : name;
      LuaTable namedClass = this.classes.get(selectedClassName);
      if (namedClass != null && (!namedClass.rawget("__mlua_service_class").optboolean(false)
         || !name.equals(namedClass.rawget("__mlua_service_name").optjstring("")))) {
         throw new IllegalStateException("Service '" + name + "' conflicts with Lua class '" + selectedClassName
            + "'; register it with a distinct explicit service class name");
      }
      MLuaServiceRegistry.Registration registration = new MLuaServiceRegistry.Registration(
         name, selectedClassName, factory, description, functionDescriptions);
      this.services.validateBatch(side, List.of(registration));
      validateServiceClassRegistration(selectedClassName, description, registration.functionDescriptions());
      boolean addedClassName = existingClassName == null;
      try {
         this.services.registerBatch(side, List.of(registration));
         if (addedClassName) this.services.assignClassName(name, selectedClassName);
         registerServiceClassIfAvailable(name, selectedClassName);
      } catch (RuntimeException | Error failure) {
         this.services.unregisterBatch(side, List.of(registration));
         if (addedClassName) this.services.clearClassName(name, selectedClassName);
         throw failure;
      }
   }

   private void validateServiceClassRegistration(String className, String description,
                                                Map<String, String> functionDescriptions) {
      if (this.serviceBaseClassName() == null) return;
      LuaTable serviceClass = this.classes.get(className);
      LuaValue existingDescription = serviceClass == null ? LuaValue.NIL : serviceClass.rawget("__mlua_doc_description");
      if (!existingDescription.isnil() && !existingDescription.isstring()) {
         throw new IllegalStateException("Lua service class '" + className + "' has invalid description metadata");
      }
      if (description != null && !description.isBlank() && existingDescription.isstring()
         && !description.equals(existingDescription.tojstring())) {
         throw new IllegalStateException("Lua service class '" + className
            + "' is already registered with a different description");
      }
      LuaValue docsValue = serviceClass == null ? LuaValue.NIL : serviceClass.rawget("__mlua_doc_methods");
      if (!docsValue.isnil() && !docsValue.istable()) {
         throw new IllegalStateException("Lua service class '" + className + "' method descriptions must be a table");
      }
      LuaTable docs = docsValue.istable() ? docsValue.checktable() : null;
      String inheritedOwner = serviceClass == null ? this.serviceBaseClassName() : className;
      functionDescriptions.forEach((methodName, methodDescription) -> {
         LuaValue existingMethod = serviceClass == null ? LuaValue.NIL : serviceClass.rawget(methodName);
         if (!existingMethod.isnil() && !existingMethod.isfunction()) {
            throw new IllegalStateException("Lua service class '" + className
               + "' already defines non-function member '" + methodName + "'");
         }
         if (this.hasClassProperty(inheritedOwner, methodName)) {
            throw new IllegalStateException("Lua service class '" + className
               + "' cannot register function '" + methodName + "' because it is an inherited property");
         }
         LuaValue existingMethodDescription = docs == null ? LuaValue.NIL : docs.rawget(methodName);
         if (!existingMethodDescription.isnil() && !existingMethodDescription.isstring()) {
            throw new IllegalStateException("Lua service class '" + className + "' function '" + methodName
               + "' has invalid description metadata");
         }
         if (existingMethodDescription.isstring() && !methodDescription.equals(existingMethodDescription.tojstring())) {
            throw new IllegalStateException("Lua service class '" + className + "' function '" + methodName
               + "' is already registered with a different description");
         }
      });
   }

   private String registeredServiceClassName(String serviceName) {
      String found = null;
      for (String candidate : this.classes.names()) {
         LuaTable type = this.classes.get(candidate);
         if (type == null) continue;
         LuaValue marker = type.rawget("__mlua_service_class");
         if (marker.isnil() || !marker.optboolean(false)
            || !serviceName.equals(type.rawget("__mlua_service_name").optjstring(""))) continue;
         if (found != null && !found.equals(candidate)) {
            throw new IllegalStateException("Service '" + serviceName + "' has multiple registered classes");
         }
         found = candidate;
      }
      return found;
   }

   private void registerServiceClassIfAvailable(String name, String className) {
      if (this.serviceBaseClassName() == null) return;
      MLuaServiceRegistry.ServiceMetadata metadata = this.services.metadata(name);
      if (metadata == null) return;
      String selectedClassName = metadata.className() == null ? className : metadata.className();
      this.registerServiceClass(name, selectedClassName, metadata.description(), metadata.functionDescriptions());
   }

   @Override
   public synchronized String serviceClassName(String serviceName) {
      String registered = this.services.explicitClassName(serviceName);
      return registered == null ? MLuaRegistration.super.serviceClassName(serviceName) : registered;
   }

   @Override
   @Deprecated(forRemoval = false)
   public LuaTable registerClass(String name, String parentName) {
      return this.classes.register(name, parentName);
   }

   @Override
   public LuaTable registerClass(String name, String parentName, String description) {
      return this.classes.register(name, parentName, description);
   }

   @Override
   public LuaTable registerClassTable(String name, LuaTable classTable, String parentName) {
      return this.classes.register(name, classTable, parentName);
   }

   @Override
   public LuaTable registerClassTable(String name, LuaTable classTable, String parentName,
                                      String description) {
      return this.classes.register(name, classTable, parentName, description);
   }

   @Override
   public LuaTable registerFunction(LuaTable owner, String name, LuaValue function, String description) {
      return this.classes.registerFunction(owner, name, function, description);
   }

   @Override
   public boolean unregisterClassFunction(String className, String functionName, LuaValue expectedFunction) {
      return this.classes.unregisterFunction(className, functionName, expectedFunction);
   }

   @Override
   public LuaTable registerClassProperty(String className, String propertyName,
                                         LuaValue getter, LuaValue setter, String description) {
      return this.classes.registerProperty(className, propertyName, getter, setter, description);
   }

   @Override
   public LuaTable registerClassProperty(String className, String propertyName,
                                         LuaValue getter, LuaValue setter, String valueType,
                                         String side, String description) {
      return this.classes.registerProperty(className, propertyName, getter, setter, valueType, side, description);
   }

   @Override
   public boolean unregisterClassProperty(String className, String propertyName, LuaValue expectedGetter) {
      return this.classes.unregisterProperty(className, propertyName, expectedGetter);
   }

   @Override
   public LuaTable classTable(String name) {
      return this.classes.get(name);
   }

   @Override public void registerContentType(String domain, String owner, String name, String base,
                                             String description, LuaTable defaults) {
      MLuaContentTypeRegistry.register(this, domain, owner, name, base, description, defaults);
   }

   @Override public LuaTable resolveContentType(String domain, String owner, LuaTable properties) {
      return MLuaContentTypeRegistry.resolve(this, domain, owner, properties);
   }

   @Override public Map<String, String> contentTypes(String domain, String owner) {
      return MLuaContentTypeRegistry.types(this, domain, owner);
   }

   @Override public boolean unregisterContentType(String domain, String owner, String name) {
      return MLuaContentTypeRegistry.unregister(this, domain, owner, name);
   }

   @Override
   public void refreshClassMetadata(String className) {
      this.classes.refreshMetadata(className);
   }

   @Override
   public LuaTable registerBehaviorMixin(String name, String description) {
      return this.classes.registerMixin(name, description);
   }

   @Override
   public LuaTable behaviorMixinTable(String name) {
      return this.classes.mixin(name);
   }

   @Override
   public Set<String> behaviorMixinNames() {
      return this.classes.mixinNames();
   }

   @Override
   public LuaTable registerBehaviorMixinFunction(String mixinName, String functionName,
                                                 LuaValue function, String description) {
      return this.classes.registerMixinFunction(mixinName, functionName, function, description);
   }

   @Override
   public boolean unregisterBehaviorMixinFunction(String mixinName, String functionName,
                                                  LuaValue expectedFunction) {
      return this.classes.unregisterMixinFunction(mixinName, functionName, expectedFunction);
   }

   @Override
   public void applyBehaviorMixin(String className, String mixinName) {
      this.classes.applyMixin(className, mixinName);
   }

   @Override
   public boolean hasBehaviorMixin(String className, String mixinName) {
      return this.classes.hasMixin(className, mixinName);
   }

   @Override
   public boolean unregisterBehaviorMixin(String mixinName) {
      return this.classes.removeMixin(mixinName);
   }

   @Override
   public boolean removeBehaviorMixin(String className, String mixinName) {
      return this.classes.removeMixin(className, mixinName);
   }

   @Override
   public Set<String> classNames() {
      return this.classes.names();
   }

   @Override
   public boolean isSubclassOf(String className, String parentName) {
      return this.classes.isSubclassOf(className, parentName);
   }

   @Override
   public boolean isInstanceOf(LuaValue instance, String className) {
      return this.classes.isA(instance, className);
   }

   @Override
   public String classNameOf(LuaValue instance) {
      return this.classes.classNameOf(instance);
   }

   @Override
   public boolean unregisterClass(String name) {
      LuaTable classTable = this.classes.get(name);
      boolean removed = this.classes.unregister(name);
      if (removed) this.session.forgetLuaServicesForClass(classTable);
      return removed;
   }

   @Override
   public boolean unregisterClassIfUnused(String name) {
      LuaTable classTable = this.classes.get(name);
      boolean removed = this.classes.unregisterIfUnused(name);
      if (classTable != null) this.session.forgetLuaServicesForClass(classTable);
      return removed;
   }

   @Override
   public LuaTable attachClassInstance(LuaTable instance, String name, String parentName) {
      return this.classes.attachInstance(instance, name, parentName);
   }

   public MinecraftServer server() {
      return this.session.server();
   }

   public long lifecycleEpoch() {
      return this.lifecycleEpoch;
   }

   public synchronized void runRegistryScripts() {
      if (this.registryScriptsRan) {
         throw new IllegalStateException("[MLua] Registry scripts already ran for this runtime. Native Minecraft registrations cannot be rolled back or hot-reloaded; restart the game to apply registry script changes.");
      }
      this.ensureScriptsCurrent();
      // Mark the phase before invoking scripts: loader registry submissions are irreversible,
      // even when a later script or generated-resource step fails.
      this.registryScriptsRan = true;
      this.clearRegistryScriptRegistrations();
      this.scriptCatalog.ofType(ScriptType.REGISTRY).forEach((script) -> {
         try {
            this.executeRegistryScript(script, false);
         } catch (RuntimeException | Error failure) {
            MLuaLogger.LOGGER.error("Registry script '{}' failed and was skipped: {}", script.displayName(), failure.getMessage());
            this.reportRuntimeFailure("Registry script '" + script.displayName() + "' failed", failure);
         }

      });
      this.scriptCatalog.withEntrypoint("run_registry", Side.SERVER).forEach((script) -> {
         try {
            this.executeRegistryScript(script, true);
         } catch (RuntimeException | Error failure) {
            MLuaLogger.LOGGER.error("Registry entrypoint script '{}' failed and was skipped: {}", script.displayName(), failure.getMessage());
            this.reportRuntimeFailure("Registry entrypoint script '" + script.displayName() + "' failed", failure);
         }

      });
      this.generateAssetPack();

      MixinRuleEngine.triggerRetransform();
      MLuaDocumentation.generate(LoaderApi.platform().gameDir());
   }

   private void executeRegistryScript(MLuaScript script, boolean entrypoint) {
      Path key = script.path().toAbsolutePath().normalize();
      MLuaScriptRegistration previous = this.registryScriptRegistrations.remove(key);
      if (previous != null) {
         Throwable rollbackFailure = MLuaRollback.run(null, () -> this.unregisterMixinScope(key));
         rollbackFailure = MLuaRollback.run(rollbackFailure, previous::rollback);
         MLuaRollback.rethrow(rollbackFailure);
      }
      MLuaScriptRegistration registration = new MLuaScriptRegistration(this);
      MLuaContext context = this.contextFor(script, Side.SERVER, RegistrationPhase.REGISTRY, null, registration);
      try (MLuaLoaderRegistry.RegistrationTransaction nativeRegistrations =
               MLuaLoaderRegistry.beginRegistrationTransaction()) {
         if (entrypoint) {
            this.engine.executeEntrypoint(script, context, this.entrypointName(Side.SERVER, RegistrationPhase.REGISTRY));
         } else {
            this.engine.execute(script, context);
         }
         nativeRegistrations.commit();
         this.registryScriptRegistrations.put(key, registration);
      } catch (RuntimeException | Error failure) {
         MLuaRollback.run(failure, () -> this.unregisterMixinScope(key));
         MLuaRollback.run(failure, registration::rollback);
         throw failure;
      }
   }

   private void clearRegistryScriptRegistrations() {
      List<Map.Entry<Path, MLuaScriptRegistration>> registrations = new ArrayList<>(this.registryScriptRegistrations.entrySet());
      Throwable failure = null;
      for (int index = registrations.size() - 1; index >= 0; index--) {
         Map.Entry<Path, MLuaScriptRegistration> entry = registrations.get(index);
         failure = MLuaRollback.run(failure, () -> this.unregisterMixinScope(entry.getKey()));
         failure = MLuaRollback.run(failure, entry.getValue()::rollback);
      }
      this.registryScriptRegistrations.clear();
      MLuaRollback.rethrow(failure);
   }

   private void clearRegistryScriptRegistrations(Set<Path> scriptPaths) {
      Set<Path> keys = new HashSet<>();
      for (Path path : scriptPaths) keys.add(path.toAbsolutePath().normalize());
      List<Path> paths = new ArrayList<>(this.registryScriptRegistrations.keySet());
      Throwable failure = null;
      for (int index = paths.size() - 1; index >= 0; index--) {
         Path path = paths.get(index);
         if (!keys.contains(path)) continue;
         MLuaScriptRegistration registration = this.registryScriptRegistrations.remove(path);
         failure = MLuaRollback.run(failure, () -> this.unregisterMixinScope(path));
         if (registration != null) failure = MLuaRollback.run(failure, registration::rollback);
      }
      MLuaRollback.rethrow(failure);
   }

   private void unregisterMixinScope(Path path) {
      if (path != null) MixinRuleEngine.get().unregisterScope(MLuaMixinCallbackRegistry.scriptScope(path.toString()));
   }

   public void runMixinScripts() {
      this.ensureScriptsCurrent();
      this.scriptCatalog.mixinScripts().forEach(script -> {
         Side mixinSide = script.type().mixinSide();
         if (mixinSide == Side.CLIENT && !LoaderApi.platform().isClient()) return;
         this.unregisterMixinScope(script.path());
         try {
            this.execute(script, mixinSide, RegistrationPhase.MIXIN, null);
         } catch (LuaError error) {
            this.unregisterMixinScope(script.path());
            MLuaLogger.LOGGER.error("{} mixin script '{}' failed and was skipped: {}",
               mixinSide == Side.CLIENT ? "Client" : "Server", script.displayName(), error.getMessage());
            this.reportRuntimeFailure("Mixin script '" + script.displayName() + "' failed", error);
         } catch (RuntimeException | Error failure) {
            this.unregisterMixinScope(script.path());
            throw failure;
         }
      });
      this.scriptCatalog.withEntrypoint("run_mixin", Side.SERVER).forEach((script) -> {
         try {
            this.executeEntrypoint(script, Side.SERVER, RegistrationPhase.MIXIN, (MinecraftServer)null);
         } catch (LuaError var3) {
            this.unregisterMixinScope(script.path());
            MLuaLogger.LOGGER.error("Mixin entrypoint script '{}' failed and was skipped: {}", script.displayName(), var3.getMessage());
            this.reportRuntimeFailure("Mixin entrypoint script '" + script.displayName() + "' failed", var3);
         } catch (RuntimeException | Error failure) {
            this.unregisterMixinScope(script.path());
            throw failure;
         }
      });
      MixinRuleEngine.markEarlyBootstrapDone();
   }

   public void runServerScripts(MinecraftServer server) {
      if (!this.serverRan) {
         this.session.attachServer(server);
         this.serverRan = true;
         this.ensureScriptsCurrent();
         this.scriptCatalog.ofType(ScriptType.SERVER).forEach((script) -> {
            try {
               this.execute(script, Side.SERVER, RegistrationPhase.RUNTIME, server);
            } catch (RuntimeException var3) {
               MLuaLogger.LOGGER.error("Server script '{}' failed and was skipped: {}", script.displayName(), var3.getMessage());
               this.reportRuntimeFailure("Server script '" + script.displayName() + "' failed", var3);
            }

         });
         this.scriptCatalog.withEntrypoint("run_server", Side.SERVER).forEach((script) -> {
            try {
               this.executeEntrypoint(script, Side.SERVER, RegistrationPhase.RUNTIME, server);
            } catch (RuntimeException var3) {
               MLuaLogger.LOGGER.error("Server entrypoint script '{}' failed and was skipped: {}", script.displayName(), var3.getMessage());
               this.reportRuntimeFailure("Server entrypoint script '" + script.displayName() + "' failed", var3);
            }

         });
         for (Level level : server.getAllLevels()) this.runWorldLifecycleEntrypoint(level, true);
      }
   }

   /** Dispatches world lifecycle entrypoints after the normal WorldLoad/WorldUnload signals. */
   public void runWorldLifecycleEntrypoint(Level level, boolean loaded) {
      if (level == null) return;
      Side side = level.isClientSide() ? Side.CLIENT : Side.SERVER;
      if (side == Side.SERVER && (!this.serverRan || this.session.server() != level.getServer())) return;
      this.ensureScriptsCurrent();
      String name = loaded ? "run_world_load" : "run_world_unload";
      synchronized (this.entrypointWorlds) {
         if (loaded) {
            if (!this.entrypointWorlds.add(level)) return;
         } else {
            if (!this.entrypointWorlds.remove(level)) return;
         }
      }
      MinecraftServer server = side == Side.SERVER ? level.getServer() : this.session.server();
      for (MLuaScript script : this.scriptCatalog.withEntrypoint(name, side)) {
         try {
            MLuaContext context = this.contextFor(script, side, RegistrationPhase.RUNTIME, server);
            LuaTable event = new LuaTable();
            event.set("Level", MLuaLevelWrapper.levelTable(level, registrationForLevel(level)));
            event.set("Dimension", LuaValue.valueOf(level.dimension().location().toString()));
            event.set("Side", LuaValue.valueOf(side == Side.CLIENT ? "Client" : "Server"));
            this.engine.executeEntrypoint(script, context, name, event);
         } catch (RuntimeException failure) {
            MLuaLogger.LOGGER.error("World lifecycle entrypoint '{}' in '{}' failed: {}",
               name, script.displayName(), failure.getMessage());
            this.reportRuntimeFailure("World lifecycle entrypoint '" + name + "' failed in "
               + script.displayName(), failure);
         }
      }
   }

   public synchronized void onServerStopping(MinecraftServer server) {
      if (this.session.server() != server) return;
      for (Level level : server.getAllLevels()) this.runWorldLifecycleEntrypoint(level, false);
      this.runServerStoppingEntrypoints(server);
      this.lifecycleEpoch++;
      MLuaModuleLoader.get().clearRuntimeState(this, this.session, Side.SERVER);
      this.session.clearScriptState(Side.SERVER);
      if (this.engine != null) this.engine.resetSide(Side.SERVER);
      MLuaModuleLoader.get().onReload(Side.SERVER);
      this.session.detachServer(server);
      synchronized (this.entrypointWorlds) {
         this.entrypointWorlds.removeIf(level -> !level.isClientSide() && level.getServer() == server);
      }
      this.serverRan = false;
   }

   /** Runs script shutdown hooks after dimension unload hooks and before server runtime teardown. */
   private void runServerStoppingEntrypoints(MinecraftServer server) {
      this.ensureScriptsCurrent();
      for (MLuaScript script : this.scriptCatalog.withEntrypoint("run_server_stopping", Side.SERVER)) {
         try {
            MLuaContext context = this.contextFor(script, Side.SERVER, RegistrationPhase.RUNTIME, server);
            this.engine.executeEntrypoint(script, context, "run_server_stopping");
         } catch (RuntimeException failure) {
            MLuaLogger.LOGGER.error("Server stopping entrypoint in '{}' failed: {}",
               script.displayName(), failure.getMessage());
            this.reportRuntimeFailure("Server stopping entrypoint failed in " + script.displayName(), failure);
         }
      }
   }

   public void runCommandScripts(CommandDispatcher<CommandSourceStack> dispatcher) {
      this.ensureScriptsCurrent();
      MLuaModuleLoader.get().beforeScriptPhase(this, this.session, Side.SERVER, RegistrationPhase.COMMANDS, dispatcher);
      this.scriptCatalog.ofType(ScriptType.COMMANDS).forEach((script) -> {
         try {
            this.execute(script, Side.SERVER, RegistrationPhase.COMMANDS, this.session.server());
         } catch (RuntimeException var3) {
            MLuaLogger.LOGGER.error("Command script '{}' failed and was skipped: {}", script.displayName(), var3.getMessage());
            this.reportRuntimeFailure("Command script '" + script.displayName() + "' failed", var3);
         }

      });
      this.scriptCatalog.withEntrypoint("run_commands", Side.SERVER).forEach((script) -> {
         try {
            this.executeEntrypoint(script, Side.SERVER, RegistrationPhase.COMMANDS, this.session.server());
         } catch (RuntimeException var3) {
            MLuaLogger.LOGGER.error("Command entrypoint script '{}' failed and was skipped: {}", script.displayName(), var3.getMessage());
            this.reportRuntimeFailure("Command entrypoint script '" + script.displayName() + "' failed", var3);
         }

      });
      MLuaModuleLoader.get().afterScriptPhase(this, this.session, Side.SERVER, RegistrationPhase.COMMANDS, dispatcher);
   }

   public void runClientScriptsOnce() {
      if (!this.clientRan) {
         this.clientRan = true;
         this.runClientCommandScripts();
         this.scriptCatalog.ofType(ScriptType.CLIENT).forEach((script) -> {
            try {
            this.execute(script, Side.CLIENT, RegistrationPhase.RUNTIME, this.session.server());
            } catch (RuntimeException var3) {
               MLuaLogger.LOGGER.error("Client script '{}' failed and was skipped: {}", script.displayName(), var3.getMessage());
               this.reportRuntimeFailure("Client script '" + script.displayName() + "' failed", var3);
            }

         });
      this.scriptCatalog.withEntrypoint("run_client", Side.CLIENT).forEach((script) -> {
            try {
            this.executeEntrypoint(script, Side.CLIENT, RegistrationPhase.RUNTIME, this.session.server());
            } catch (RuntimeException var3) {
               MLuaLogger.LOGGER.error("Client script '{}' failed and was skipped: {}", script.displayName(), var3.getMessage());
               this.reportRuntimeFailure("Client entrypoint script '" + script.displayName() + "' failed", var3);
            }

         });
         this.reloadClientWorldLoadEntrypoints(false);
      }
   }

   private void execute(MLuaScript script, Side side, RegistrationPhase phase, MinecraftServer server) {
      MLuaContext context = this.contextFor(script, side, phase, server);
      MLuaLogger.LOGGER.info("Loading script {}", script.displayName());
      if (phase == RegistrationPhase.RUNTIME && (script.type() == ScriptType.SERVER || script.type() == ScriptType.CLIENT)) {
         this.engine.executeResumable(script, context);
      } else {
         this.engine.execute(script, context);
      }
   }

   private void executeEntrypoint(MLuaScript script, Side side, RegistrationPhase phase, MinecraftServer server) {
      MLuaContext context = this.contextFor(script, side, phase, server);
      String entrypointName = this.entrypointName(side, phase);
      if (entrypointName != null && this.engine.executeEntrypoint(script, context, entrypointName)) {
         MLuaLogger.LOGGER.info("Loading script {}#{}", script.displayName(), entrypointName);
      }
   }

   private MLuaContext contextFor(MLuaScript script, Side side, RegistrationPhase phase, MinecraftServer server) {
      return this.contextFor(script, side, phase, server, this);
   }

   private MLuaContext contextFor(MLuaScript script, Side side, RegistrationPhase phase,
                                 MinecraftServer server, MLuaRegistration registration) {
      boolean serverSide = side == Side.SERVER;
      MLuaContext context = new MLuaContext(
         script,
         side,
         phase,
         this.services,
         serverSide ? this.session.serverScheduler() : this.session.clientScheduler(),
         serverSide ? this.session.serverModules() : this.session.clientModules(),
         server,
         LoaderApi.platform().mluaDataDir(),
         this::reportRuntimeFailure,
         this.session,
         registration
      );
      context.bindExecutionLock(this.engine);
      return context;
   }

   private String entrypointName(Side side, RegistrationPhase phase) {
      if (phase == RegistrationPhase.MIXIN) {
         return "run_mixin";
      } else if (phase == RegistrationPhase.REGISTRY) {
         return "run_registry";
      } else if (phase == RegistrationPhase.COMMANDS) {
         return "run_commands";
      } else if (side == Side.CLIENT) {
         return "run_client";
      } else {
         return side == Side.SERVER ? "run_server" : null;
      }
   }

   private void ensureScriptsCurrent() {
      this.refreshScripts(false);
   }

   private void forceRefreshScripts() {
      this.refreshScripts(true);
   }

   private void refreshScripts(boolean force) {
      List<MLuaScript> discovered = this.scriptCatalog.refresh(force || this.engine == null);
      if (this.engine == null) {
         this.engine = new LuaJEngineBackend(discovered);
      } else {
         this.engine.replaceScripts(discovered);
      }
   }

   public void deliverPendingServerDiagnostics(ServerPlayer player) {
      List<String> messages = this.diagnostics.recentServerMessages();
      for(String message : messages) {
         player.sendSystemMessage(Component.literal(message));
      }
   }

   private void flushPendingServerDiagnostics() {
      if (this.session.server() == null) {
         return;
      }

      List<String> messages = this.diagnostics.drainServerMessages();
      if (messages.isEmpty()) {
         return;
      }

      for(ServerPlayer player : this.session.server().getPlayerList().getPlayers()) {
         for(String message : messages) {
            player.sendSystemMessage(Component.literal(message));
         }
      }
   }

   private void flushPendingClientDiagnostics() {
      if (!LoaderApi.platform().isClient()) return;
      List<String> messages = this.diagnostics.drainClientMessages();
      if (!messages.isEmpty()) invokeClientHook("flushDiagnostics", new Class<?>[]{List.class}, messages);
   }

   public void pollClientHotReload() {
      long now = System.currentTimeMillis();
      if (this.clientScriptWatcher.poll(this.scriptCatalog.roots(), now, this::isClientWindowActive)) {
         MLuaLogger.LOGGER.info("[MLua] Script changes detected. Reloading client scripts.");
         this.reloadClientOnly();
         this.diagnostics.recordClientMessage("[MLua] Script changes detected. Client scripts reloaded.");
      }

      this.flushPendingClientDiagnostics();
   }

   private boolean isClientWindowActive() {
      boolean lastWindowActive = this.clientScriptWatcher.lastWindowActive();
      if (!LoaderApi.platform().isClient()) return lastWindowActive;
      try { return (boolean) invokeClientHook("isWindowActive", new Class<?>[]{boolean.class}, lastWindowActive); }
      catch (Throwable ignored) { return lastWindowActive; }
   }

   private void generateClientAssets() {
      if (LoaderApi.platform().isClient()) invokeClientHook("generateAssets", new Class<?>[0]);
   }

   private void generateAssetPack() {
      if (LoaderApi.platform().isClient()) generateClientAssets();
      else MLuaGeneratedAssetPack.generate();
   }

   private static Object invokeClientHook(String methodName, Class<?>[] parameterTypes, Object... arguments) {
      try {
         Class<?> hooks = Class.forName("dev.mlua.runtime.client.MLuaClientRuntimeHooks", true,
            MLuaRuntime.class.getClassLoader());
         return hooks.getMethod(methodName, parameterTypes).invoke(null, arguments);
      } catch (java.lang.reflect.InvocationTargetException failure) {
         Throwable cause = failure.getCause() == null ? failure : failure.getCause();
         if (cause instanceof RuntimeException runtimeFailure) throw runtimeFailure;
         if (cause instanceof Error error) throw error;
         throw new IllegalStateException("MLua client runtime hook '" + methodName + "' failed", cause);
      } catch (ReflectiveOperationException failure) {
         throw new IllegalStateException("MLua client runtime hook '" + methodName + "' is unavailable", failure);
      }
   }

    public void onRegisterCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
       MLuaRuntimeCommands.register(this, dispatcher);
       this.runCommandScripts(dispatcher);
    }

    void executeReload(CommandSourceStack source) {
       if (source.hasPermission(4)) {
          this.lifecycleEpoch++;
          source.sendSuccess(() -> Component.literal("[MLua] Reloading server scripts..."), true);

            MLuaModuleLoader.get().clearRuntimeState(this, this.session, Side.SERVER);
            this.session.clearScriptState(Side.SERVER);
            this.forceRefreshScripts();
            this.engine.resetSide(Side.SERVER);
           MLuaModuleLoader.get().onReload(Side.SERVER);

          this.scriptCatalog.ofType(ScriptType.SERVER).forEach(script -> {
             try {
             this.execute(script, Side.SERVER, RegistrationPhase.RUNTIME, this.session.server());
             } catch (RuntimeException e) {
                MLuaLogger.LOGGER.error("Server script '{}' failed during reload: {}", script.displayName(), e.getMessage());
                this.reportRuntimeFailure("Server script '" + script.displayName() + "' failed during reload", e);
             }
          });

          this.scriptCatalog.withEntrypoint("run_server", Side.SERVER).forEach(script -> {
             try {
                this.executeEntrypoint(script, Side.SERVER, RegistrationPhase.RUNTIME, this.session.server());
             } catch (RuntimeException e) {
                MLuaLogger.LOGGER.error("Server entrypoint script '{}' failed during reload: {}", script.displayName(), e.getMessage());
                this.reportRuntimeFailure("Server entrypoint script '" + script.displayName() + "' failed during reload", e);
             }
          });

          if (this.session.server() != null) {
             this.runCommandScripts(this.session.server().getCommands().getDispatcher());
             this.reloadWorldLoadEntrypoints(this.session.server());
          }

          MLuaNetworkManager.get().sendReloadToAll(this.session.server());
          source.sendSuccess(() -> Component.literal("[MLua] Server reload complete."), true);
       } else {
          ServerPlayer player = source.getPlayer();
          if (player != null) {
             source.sendSuccess(() -> Component.literal("[MLua] Reloading client scripts..."), true);
             MLuaNetworkManager.get().sendClientReload(player);
          }
       }
    }

   private void reloadWorldLoadEntrypoints(MinecraftServer server) {
      for (Level level : server.getAllLevels()) {
         synchronized (this.entrypointWorlds) {
            this.entrypointWorlds.remove(level);
         }
         this.runWorldLifecycleEntrypoint(level, true);
      }
   }

   private void reloadClientWorldLoadEntrypoints(boolean forceReload) {
      if (!LoaderApi.platform().isClient()) return;
      Level level;
      try { level = (Level) invokeClientHook("currentLevel", new Class<?>[0]); }
      catch (RuntimeException unavailable) { return; }
      if (level == null || !level.isClientSide()) return;
      if (forceReload) {
         synchronized (this.entrypointWorlds) {
            this.entrypointWorlds.remove(level);
         }
      }
      this.runWorldLifecycleEntrypoint(level, true);
   }

   public void reloadClientOnly() {
        this.lifecycleEpoch++;
        MLuaModuleLoader.get().clearRuntimeState(this, this.session, Side.CLIENT);
       this.session.clearScriptState(Side.CLIENT);
       this.forceRefreshScripts();
       this.engine.resetSide(Side.CLIENT);
       MLuaModuleLoader.get().onReload(Side.CLIENT);
       this.runClientCommandScripts();

       this.scriptCatalog.ofType(ScriptType.CLIENT).forEach(script -> {
          try {
             this.execute(script, Side.CLIENT, RegistrationPhase.RUNTIME, this.session.server());
          } catch (RuntimeException e) {
             MLuaLogger.LOGGER.error("Client script '{}' failed during reload: {}", script.displayName(), e.getMessage());
             this.reportRuntimeFailure("Client script '" + script.displayName() + "' failed during reload", e);
          }
       });

       this.scriptCatalog.withEntrypoint("run_client", Side.CLIENT).forEach(script -> {
          try {
             this.executeEntrypoint(script, Side.CLIENT, RegistrationPhase.RUNTIME, this.session.server());
          } catch (RuntimeException e) {
             MLuaLogger.LOGGER.error("Client entrypoint script '{}' failed during reload: {}", script.displayName(), e.getMessage());
             this.reportRuntimeFailure("Client entrypoint script '" + script.displayName() + "' failed during reload", e);
          }
       });
       this.reloadClientWorldLoadEntrypoints(true);
    }

   private void runClientCommandScripts() {
      MLuaModuleLoader.get().beforeScriptPhase(this, this.session, Side.CLIENT, RegistrationPhase.COMMANDS, null);
      this.scriptCatalog.ofType(ScriptType.COMMANDS).forEach((script) -> {
         try {
            this.execute(script, Side.CLIENT, RegistrationPhase.COMMANDS, this.session.server());
         } catch (RuntimeException var3) {
            MLuaLogger.LOGGER.error("Client command script '{}' failed and was skipped: {}", script.displayName(), var3.getMessage());
            this.reportRuntimeFailure("Client command script '" + script.displayName() + "' failed", var3);
         }
      });
      this.scriptCatalog.withEntrypoint("run_commands", Side.CLIENT).forEach(script -> {
            try {
               this.executeEntrypoint(script, Side.CLIENT, RegistrationPhase.COMMANDS, this.session.server());
            } catch (RuntimeException failure) {
               MLuaLogger.LOGGER.error("Client command entrypoint script '{}' failed and was skipped: {}",
                  script.displayName(), failure.getMessage());
               this.reportRuntimeFailure("Client command entrypoint script '" + script.displayName() + "' failed",
                  failure);
            }
         });
      MLuaModuleLoader.get().afterScriptPhase(this, this.session, Side.CLIENT, RegistrationPhase.COMMANDS, null);
   }

   public void onServerTickEnd() {
      this.session.serverScheduler().tick();
      MLuaModuleLoader.get().onRuntimeTick(this, this.session, Side.SERVER, 0.05D);
      MLuaModuleLoader.get().afterRuntimeTick(this, this.session, Side.SERVER, 0.05D);
      this.flushPendingServerDiagnostics();
   }

   public void onClientTickEnd() {
      this.drainClientWorldLifecycleTransitions();
      this.session.clientScheduler().tick();
      MLuaModuleLoader.get().onRuntimeTick(this, this.session, Side.CLIENT, 0.05D);
      MLuaModuleLoader.get().afterRuntimeTick(this, this.session, Side.CLIENT, 0.05D);
      this.pollClientHotReload();
   }

   /** Queues client-world callbacks so they run after client startup scripts on the next client tick. */
   public void queueClientWorldLifecycle(Level level, boolean loaded) {
      if (level == null || !level.isClientSide()) return;
      this.pendingClientWorldTransitions.add(new ClientWorldLifecycleTransition(level, loaded));
   }

   private void drainClientWorldLifecycleTransitions() {
      if (!LoaderApi.platform().isClient()) {
         this.pendingClientWorldTransitions.clear();
         return;
      }
      ClientWorldLifecycleTransition transition;
      while ((transition = this.pendingClientWorldTransitions.poll()) != null) {
         try {
            this.runWorldLifecycleEntrypoint(transition.level(), transition.loaded());
         } catch (RuntimeException failure) {
            this.reportRuntimeFailure("Client world lifecycle entrypoint failed", failure);
         }
      }
   }

   private record ClientWorldLifecycleTransition(Level level, boolean loaded) { }

   public int clearOwnedScriptState(Side side, String ownerModId) {
      if (side == null || ownerModId == null || ownerModId.isBlank()) return 0;
      MLuaSignal.destroyAllOwned(this.session, ownerModId, side);
      MLuaModuleLoader.get().clearOwnedState(this, this.session, side, ownerModId);
      return this.session.clearOwnedTasks(side, ownerModId);
   }

}
