package dev.mlua.core;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import dev.mlua.core.engine.LuaJEngineBackend;
import dev.mlua.core.engine.MLuaEngine;
import dev.mlua.core.require.MLuaModuleCache;
import dev.mlua.core.scheduler.MLuaTaskScheduler;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.core.script.MLuaScriptLoader;
import dev.mlua.core.script.MLuaScriptRoot;
import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.mixin.MixinRuleEngine;
import dev.mlua.common.mixin.MLuaMixinCallbackRegistry;
import dev.mlua.common.mixin.MLuaClassViewer;
import dev.mlua.common.mixin.MLuaMappingResolvers;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.core.service.MLuaServiceRegistry;
import dev.mlua.core.service.MLuaService;
import dev.mlua.runtime.client.MLuaGeneratedAssetPack;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.core.classes.MLuaClassRegistry;
import dev.mlua.runtime.registry.MLuaContentTypeRegistry;
import dev.mlua.api.registry.MLuaRegistry;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.minecraft.network.MLuaNetworkManager;
import dev.mlua.module.MLuaModuleLoader;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaRegistration;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.IOException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.client.player.LocalPlayer;
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
   private static final int MAX_DIAGNOSTICS = 8;
   private static final long HOT_RELOAD_POLL_INTERVAL_MS = 500L;
   private static final long HOT_RELOAD_DEBOUNCE_MS = 900L;
   private static final long HOT_RELOAD_FULL_SCAN_INTERVAL_MS = 10000L;
   private static final MLuaRuntime INSTANCE = new MLuaRuntime();
   private final List<MLuaScriptRoot> roots = new ArrayList<>();
   private final Object diagnosticsLock = new Object();
   private final MLuaScriptLoader loader = new MLuaScriptLoader();
   private final List<MLuaScript> resourceScripts = new ArrayList<>();
   private final MLuaRuntimeSession session;
   private final MLuaServiceRegistry services = new MLuaServiceRegistry();
   private final MLuaClassRegistry classes = new MLuaClassRegistry();
   private volatile long lifecycleEpoch;
   private final Deque<String> pendingServerDiagnostics = new ArrayDeque();
   private final Deque<String> pendingClientDiagnostics = new ArrayDeque();
   private final Deque<String> recentServerDiagnostics = new ArrayDeque();
   private final Deque<String> recentClientDiagnostics = new ArrayDeque();
   private final Map<Path, Long> clientScriptSnapshot = new HashMap<>();
   private final Map<Path, MLuaScriptRegistration> registryScriptRegistrations = new LinkedHashMap<>();
   private final Set<Path> clientTrackedScriptFiles = new HashSet<>();
   private List<MLuaScript> scripts = List.of();
   private MLuaEngine engine;
   private boolean scriptsDirty = true;
   private boolean bootstrapped;
   private boolean registryScriptsRan;
   private boolean serverRan;
   private boolean clientRan;
   private boolean lastClientWindowActive;
   private boolean pendingClientHotReload;
   private long lastClientWatchPollAt;
   private long lastClientChangeAt;
   private long lastClientFullScanAt;

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
      if (level != null && !level.isClientSide() && level.getServer() != null) {
         MLuaRuntimeSession session = MLuaRuntimeSession.forServer(level.getServer());
         if (session != null && session.registration() != null) return session.registration();
      }
      return get();
   }

   public MLuaRuntimeSession session() {
      return this.session;
   }

   public void reportRuntimeFailure(String summary, Throwable throwable) {
      String detail = this.formatThrowableMessage(throwable);
      String message = "[MLua] " + summary + (detail.isBlank() ? "" : ": " + detail);
      MLuaLogger.LOGGER.error(message, throwable);
      synchronized(this.diagnosticsLock) {
         this.pushDiagnostic(this.pendingServerDiagnostics, message);
         this.pushDiagnostic(this.pendingClientDiagnostics, message);
         this.pushDiagnostic(this.recentServerDiagnostics, message);
         this.pushDiagnostic(this.recentClientDiagnostics, message);
      }
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
      this.roots.add(new MLuaScriptRoot(modId, path));
      this.scriptsDirty = true;
      if (this.registryScriptsRan) {
         MLuaLogger.LOGGER.info("Registered runtime script root for '{}': {}. Registry scripts in this root will not run this load cycle.", modId, path);
      }

   }

   public synchronized void replaceResourceScripts(List<MLuaScript> scripts) {
      Set<Path> replacedScriptPaths = new HashSet<>();
      for (MLuaScript script : this.resourceScripts) replacedScriptPaths.add(script.path());
      this.resourceScripts.clear();
      this.resourceScripts.addAll(scripts);
      this.scriptsDirty = true;
      MLuaLogger.LOGGER.info("Loaded {} resource-pack MLua scripts", scripts.size());
      this.onResourceScriptsReload(replacedScriptPaths);
   }

   private void onResourceScriptsReload(Set<Path> replacedScriptPaths) {
      this.lifecycleEpoch++;
      for (Path path : replacedScriptPaths) this.unregisterMixinScope(path);
      this.clearRegistryScriptRegistrations(replacedScriptPaths);
      this.session.serverScheduler().removeScripts(replacedScriptPaths);
      this.session.clientScheduler().removeScripts(replacedScriptPaths);
      this.forceRefreshScripts();
      this.session.clearModuleCache(Side.BOTH);
      MLuaModuleLoader.get().clearRuntimeState(this, this.session, Side.BOTH);
      for (MLuaScript script : this.resourceScripts) {
         if (script.type() != ScriptType.REGISTRY && script.type() != ScriptType.ENTRYPOINT) continue;
         try {
            this.executeRegistryScript(script, script.type() == ScriptType.ENTRYPOINT);
         } catch (Throwable throwable) {
            this.reportRuntimeFailure("Registry script '" + script.displayName() + "' failed during reload", throwable);
         }
      }
      for (MLuaScript script : this.resourceScripts) {
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
            this.reportRuntimeFailure("Resource-pack script '" + script.displayName() + "' failed during reload", throwable);
         }
      }
      MixinRuleEngine.triggerRetransform();
      MLuaGeneratedAssetPack.generate();
      MLuaDocumentation.generate(LoaderApi.platform().gameDir());
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

   /** @deprecated Register services with a service description and function descriptions. */
   @Deprecated(forRemoval = false)
   public void registerService(String name, Function<MLuaService.Context, MLuaService> factory) {
      this.registerService(name, Side.BOTH, factory, null, Map.of());
   }

   /** @deprecated Register services with a service description and function descriptions. */
   @Deprecated(forRemoval = false)
   public void registerService(String name, Side side, Function<MLuaService.Context, MLuaService> factory) {
      this.registerService(name, side, factory, null, Map.of());
   }

   public void registerService(String name, Function<MLuaService.Context, MLuaService> factory,
                               String description, Map<String, String> functionDescriptions) {
      this.registerService(name, Side.BOTH, factory, description, functionDescriptions);
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
         // A service subclass may override a documented parent method with its own
         // contract. A description already declared on this class remains authoritative.
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
   public LuaTable registerClass(String name, String parentName) {
      return this.classes.register(name, parentName);
   }

   @Override
   public LuaTable registerClass(String name, LuaTable classTable, String... parentNames) {
      return this.classes.register(name, classTable, parentNames);
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
      // A pending service class must release the session's strong reference to its
      // old live instance so class removal can finish once script contexts release it.
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

   public void runRegistryScripts() {
      this.ensureScriptsCurrent();
      this.clearRegistryScriptRegistrations();
      this.scripts.stream().filter((script) -> {
         return script.type() == ScriptType.REGISTRY;
      }).forEach((script) -> {
         try {
            this.executeRegistryScript(script, false);
         } catch (RuntimeException | Error failure) {
            MLuaLogger.LOGGER.error("Registry script '{}' failed and was skipped: {}", script.displayName(), failure.getMessage());
            this.reportRuntimeFailure("Registry script '" + script.displayName() + "' failed", failure);
         }

      });
      this.scripts.stream().filter((script) -> {
         return script.type() == ScriptType.ENTRYPOINT;
      }).forEach((script) -> {
         try {
            this.executeRegistryScript(script, true);
         } catch (RuntimeException | Error failure) {
            MLuaLogger.LOGGER.error("Registry entrypoint script '{}' failed and was skipped: {}", script.displayName(), failure.getMessage());
            this.reportRuntimeFailure("Registry entrypoint script '" + script.displayName() + "' failed", failure);
         }

      });
      MLuaGeneratedAssetPack.generate();
      if (LoaderApi.platform().isClient()) {
         try {
            MLuaMinecraftClient.instance().reloadResourcePacks();
         } catch (Throwable var2) {
         }
      }

      this.registryScriptsRan = true;
      MixinRuleEngine.triggerRetransform();
      MLuaDocumentation.generate(LoaderApi.platform().gameDir());
   }

   private void executeRegistryScript(MLuaScript script, boolean entrypoint) {
      Path key = script.path().toAbsolutePath().normalize();
      MLuaScriptRegistration previous = this.registryScriptRegistrations.remove(key);
      if (previous != null) this.unregisterMixinScope(key);
      if (previous != null) previous.rollback();
      MLuaScriptRegistration registration = new MLuaScriptRegistration(this);
      MLuaContext context = this.contextFor(script, Side.SERVER, RegistrationPhase.REGISTRY, null, registration);
      try {
         if (entrypoint) {
            this.engine.executeEntrypoint(script, context, this.entrypointName(Side.SERVER, RegistrationPhase.REGISTRY));
         } else {
            this.engine.execute(script, context);
         }
         this.registryScriptRegistrations.put(key, registration);
      } catch (RuntimeException | Error failure) {
         registration.rollback();
         throw failure;
      }
   }

   private void clearRegistryScriptRegistrations() {
      List<Map.Entry<Path, MLuaScriptRegistration>> registrations = new ArrayList<>(this.registryScriptRegistrations.entrySet());
      for (int index = registrations.size() - 1; index >= 0; index--) {
         Map.Entry<Path, MLuaScriptRegistration> entry = registrations.get(index);
         this.unregisterMixinScope(entry.getKey());
         entry.getValue().rollback();
      }
      this.registryScriptRegistrations.clear();
   }

   private void clearRegistryScriptRegistrations(Set<Path> scriptPaths) {
      Set<Path> keys = new HashSet<>();
      for (Path path : scriptPaths) keys.add(path.toAbsolutePath().normalize());
      List<Path> paths = new ArrayList<>(this.registryScriptRegistrations.keySet());
      for (int index = paths.size() - 1; index >= 0; index--) {
         Path path = paths.get(index);
         if (!keys.contains(path)) continue;
         MLuaScriptRegistration registration = this.registryScriptRegistrations.remove(path);
         this.unregisterMixinScope(path);
         if (registration != null) registration.rollback();
      }
   }

   private void unregisterMixinScope(Path path) {
      if (path != null) MixinRuleEngine.get().unregisterScope(MLuaMixinCallbackRegistry.scriptScope(path.toString()));
   }

   public void runMixinScripts() {
      this.ensureScriptsCurrent();
      this.scripts.stream().filter(script -> script.type().isMixin()).forEach(script -> {
         Side mixinSide = script.type().mixinSide();
         if (mixinSide == Side.CLIENT && !LoaderApi.platform().isClient()) return;
         this.unregisterMixinScope(script.path());
         try {
            this.execute(script, mixinSide, RegistrationPhase.MIXIN, null);
         } catch (LuaError error) {
            MLuaLogger.LOGGER.error("{} mixin script '{}' failed and was skipped: {}",
               mixinSide == Side.CLIENT ? "Client" : "Server", script.displayName(), error.getMessage());
            this.reportRuntimeFailure("Mixin script '" + script.displayName() + "' failed", error);
         }
      });
      this.scripts.stream().filter((script) -> {
         return script.type() == ScriptType.ENTRYPOINT;
      }).forEach((script) -> {
         try {
            this.executeEntrypoint(script, Side.SERVER, RegistrationPhase.MIXIN, (MinecraftServer)null);
         } catch (LuaError var3) {
            MLuaLogger.LOGGER.error("Mixin entrypoint script '{}' failed and was skipped: {}", script.displayName(), var3.getMessage());
            this.reportRuntimeFailure("Mixin entrypoint script '" + script.displayName() + "' failed", var3);
         }
      });
      MixinRuleEngine.markEarlyBootstrapDone();
   }

   public void runServerScripts(MinecraftServer server) {
      if (!this.serverRan) {
         this.session.attachServer(server);
         this.serverRan = true;
         this.ensureScriptsCurrent();
         this.scripts.stream().filter((script) -> {
            return script.type() == ScriptType.SERVER;
         }).forEach((script) -> {
            try {
               this.execute(script, Side.SERVER, RegistrationPhase.RUNTIME, server);
            } catch (LuaError var3) {
               MLuaLogger.LOGGER.error("Server script '{}' failed and was skipped: {}", script.displayName(), var3.getMessage());
               this.reportRuntimeFailure("Server script '" + script.displayName() + "' failed", var3);
            }

         });
         this.scripts.stream().filter((script) -> {
            return script.type() == ScriptType.ENTRYPOINT;
         }).forEach((script) -> {
            try {
               this.executeEntrypoint(script, Side.SERVER, RegistrationPhase.RUNTIME, server);
            } catch (LuaError var3) {
               MLuaLogger.LOGGER.error("Server entrypoint script '{}' failed and was skipped: {}", script.displayName(), var3.getMessage());
               this.reportRuntimeFailure("Server entrypoint script '" + script.displayName() + "' failed", var3);
            }

         });
      }
   }

   public synchronized void onServerStopping(MinecraftServer server) {
      if (this.session.server() != server) return;
      this.lifecycleEpoch++;
      MLuaModuleLoader.get().clearRuntimeState(this, this.session, Side.SERVER);
      this.session.clearScriptState(Side.SERVER);
      if (this.engine != null) this.engine.resetSide(Side.SERVER);
      MLuaModuleLoader.get().onReload(Side.SERVER);
      this.session.detachServer(server);
      this.serverRan = false;
   }

   public void runCommandScripts(CommandDispatcher<CommandSourceStack> dispatcher) {
      this.ensureScriptsCurrent();
      MLuaModuleLoader.get().beforeScriptPhase(this, this.session, Side.SERVER, RegistrationPhase.COMMANDS, dispatcher);
      this.scripts.stream().filter((script) -> {
         return script.type() == ScriptType.COMMANDS;
      }).forEach((script) -> {
         try {
            this.execute(script, Side.SERVER, RegistrationPhase.COMMANDS, this.session.server());
         } catch (LuaError var3) {
            MLuaLogger.LOGGER.error("Command script '{}' failed and was skipped: {}", script.displayName(), var3.getMessage());
            this.reportRuntimeFailure("Command script '" + script.displayName() + "' failed", var3);
         }

      });
      this.scripts.stream().filter((script) -> {
         return script.type() == ScriptType.ENTRYPOINT;
      }).forEach((script) -> {
         try {
            this.executeEntrypoint(script, Side.SERVER, RegistrationPhase.COMMANDS, this.session.server());
         } catch (LuaError var3) {
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
         this.scripts.stream().filter((script) -> {
            return script.type() == ScriptType.CLIENT;
         }).forEach((script) -> {
            try {
            this.execute(script, Side.CLIENT, RegistrationPhase.RUNTIME, this.session.server());
            } catch (LuaError var3) {
               MLuaLogger.LOGGER.error("Client script '{}' failed and was skipped: {}", script.displayName(), var3.getMessage());
               this.reportRuntimeFailure("Client script '" + script.displayName() + "' failed", var3);
            }

         });
         this.scripts.stream().filter((script) -> {
            return script.type() == ScriptType.ENTRYPOINT;
         }).forEach((script) -> {
            try {
            this.executeEntrypoint(script, Side.CLIENT, RegistrationPhase.RUNTIME, this.session.server());
            } catch (LuaError var3) {
               MLuaLogger.LOGGER.error("Client script '{}' failed and was skipped: {}", script.displayName(), var3.getMessage());
               this.reportRuntimeFailure("Client entrypoint script '" + script.displayName() + "' failed", var3);
            }

         });
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
      return new MLuaContext(
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
      if (!force && !this.scriptsDirty && this.engine != null) {
         return;
      }

      List<MLuaScript> discovered = new ArrayList<>(this.loader.discover(this.roots));
      discovered.addAll(this.resourceScripts);
      this.scripts = discovered;
      if (this.engine == null) {
         this.engine = new LuaJEngineBackend(this.scripts);
      } else {
         this.engine.replaceScripts(this.scripts);
      }
      this.scriptsDirty = false;
   }

   public void deliverPendingServerDiagnostics(ServerPlayer player) {
      List<String> messages;
      synchronized(this.diagnosticsLock) {
         messages = new ArrayList(this.recentServerDiagnostics);
      }

      for(String message : messages) {
         player.sendSystemMessage(Component.literal(message));
      }
   }

   private void flushPendingServerDiagnostics() {
      if (this.session.server() == null) {
         return;
      }

      List<String> messages = this.drainDiagnostics(this.pendingServerDiagnostics);
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
      LocalPlayer player;
      try {
         player = MLuaMinecraftClient.localPlayer();
      } catch (IllegalStateException ignored) {
         return;
      }

      if (player == null) {
         return;
      }

      for(String message : this.drainDiagnostics(this.pendingClientDiagnostics)) {
         player.displayClientMessage(Component.literal(message), false);
      }
   }

   private List<String> drainDiagnostics(Deque<String> source) {
      synchronized(this.diagnosticsLock) {
         List<String> drained = new ArrayList(source);
         source.clear();
         return drained;
      }
   }

   private void pushDiagnostic(Deque<String> queue, String message) {
      if (!queue.isEmpty() && message.equals(queue.peekLast())) {
         return;
      }

      queue.addLast(message);
      while(queue.size() > MAX_DIAGNOSTICS) {
         queue.removeFirst();
      }
   }

   private String formatThrowableMessage(Throwable throwable) {
      if (throwable == null) {
         return "";
      }

      String message = throwable.getMessage();
      if (message == null || message.isBlank()) {
         return throwable.getClass().getSimpleName();
      }

      return message.replace('\n', ' ').replace('\r', ' ').trim();
   }

   public void pollClientHotReload() {
      long now = System.currentTimeMillis();
      if (now - this.lastClientWatchPollAt < HOT_RELOAD_POLL_INTERVAL_MS) {
         this.flushPendingClientDiagnostics();
         return;
      }

      this.lastClientWatchPollAt = now;
      boolean windowActive = this.isClientWindowActive();
      boolean changed = this.refreshClientScriptSnapshot(now);
      if (changed) {
         this.pendingClientHotReload = true;
         this.lastClientChangeAt = now;
      }

      if (windowActive && this.pendingClientHotReload && now - this.lastClientChangeAt >= HOT_RELOAD_DEBOUNCE_MS) {
         this.pendingClientHotReload = false;
         MLuaLogger.LOGGER.info("[MLua] Script changes detected. Reloading client scripts.");
         this.reloadClientOnly();
         synchronized(this.diagnosticsLock) {
            this.pushDiagnostic(this.pendingClientDiagnostics, "[MLua] Script changes detected. Client scripts reloaded.");
         }
      }

      this.lastClientWindowActive = windowActive;
      this.flushPendingClientDiagnostics();
   }

   private boolean refreshClientScriptSnapshot(long now) {
      Map<Path, Long> current = now - this.lastClientFullScanAt >= HOT_RELOAD_FULL_SCAN_INTERVAL_MS || this.clientTrackedScriptFiles.isEmpty()
         ? this.scanAllClientScriptFiles()
         : this.scanTrackedClientScriptFiles();

      if (this.clientScriptSnapshot.isEmpty()) {
         this.clientScriptSnapshot.putAll(current);
         return false;
      }

      boolean changed = !this.clientScriptSnapshot.equals(current);
      this.clientScriptSnapshot.clear();
      this.clientScriptSnapshot.putAll(current);
      return changed;
   }

   private Map<Path, Long> scanTrackedClientScriptFiles() {
      Map<Path, Long> current = new HashMap<>();
      List<Path> missing = new ArrayList<>();

      for (Path path : this.clientTrackedScriptFiles) {
         try {
            if (Files.isRegularFile(path, new LinkOption[0])) {
               current.put(path, Files.getLastModifiedTime(path, new LinkOption[0]).toMillis());
            } else {
               missing.add(path);
            }
         } catch (IOException ignored) {
            missing.add(path);
         }
      }

      if (!missing.isEmpty()) {
         return this.scanAllClientScriptFiles();
      }

      return current;
   }

   private Map<Path, Long> scanAllClientScriptFiles() {
      Map<Path, Long> current = new HashMap<>();

      for(MLuaScriptRoot root : this.roots) {
         if (!this.isWatchableRoot(root.path())) {
            continue;
         }

         try (Stream<Path> stream = Files.walk(root.path())) {
            stream.filter(path -> Files.isRegularFile(path, new LinkOption[0]))
               .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".lua"))
               .sorted(Comparator.comparing(Path::toString))
               .forEach(path -> {
                  try {
                     current.put(path.toAbsolutePath().normalize(), Files.getLastModifiedTime(path, new LinkOption[0]).toMillis());
                  } catch (IOException ignored) {
                  }
               });
         } catch (IOException exception) {
            MLuaLogger.LOGGER.warn("[MLua] Failed to scan script root for hot reload: {}", root.path(), exception);
         }
      }

      this.clientTrackedScriptFiles.clear();
      this.clientTrackedScriptFiles.addAll(current.keySet());
      this.lastClientFullScanAt = System.currentTimeMillis();
      return current;
   }

   private boolean isWatchableRoot(Path path) {
      try {
         return Files.isDirectory(path, new LinkOption[0]) && "file".equalsIgnoreCase(path.getFileSystem().provider().getScheme());
      } catch (FileSystemNotFoundException ignored) {
         return false;
      }
   }

   private boolean isClientWindowActive() {
      try {
         return MLuaMinecraftClient.instance().isWindowActive();
      } catch (Throwable ignored) {
         return this.lastClientWindowActive;
      }
   }

    public void onRegisterCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
       this.registerMLuaCommand(dispatcher);
       this.runCommandScripts(dispatcher);
    }

    private void registerMLuaCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
       dispatcher.register(Commands.literal("mlua")
          .then(Commands.literal("reload")
             .executes(context -> {
                this.executeReload(context.getSource());
                return 1;
             })
          )
          .then(Commands.literal("inspect")
             .then(Commands.argument("class", StringArgumentType.word())
                .executes(context -> {
                   this.inspectClass(context.getSource(), StringArgumentType.getString(context, "class"));
                   return 1;
                })
             )
          )
          .then(Commands.literal("viewclass")
             .then(Commands.argument("class", StringArgumentType.word())
                .executes(context -> {
                   this.inspectClass(context.getSource(), StringArgumentType.getString(context, "class"));
                   return 1;
                })
             )
          )
       );
    }

    private void inspectClass(CommandSourceStack source, String canonicalName) {
       try {
          String runtimeName = MLuaMappingResolvers.current().mapClassName(canonicalName);
          Class<?> type = null;
          for (String name : new String[]{runtimeName, canonicalName}) {
             try {
                type = Class.forName(name, false, Thread.currentThread().getContextClassLoader());
                break;
             } catch (ClassNotFoundException ignored) {
             }
          }
          if (type == null) {
             source.sendFailure(Component.literal("[MLua] Class not found: " + canonicalName));
             return;
          }
          Class<?> resolvedType = type;
          String[] lines = MLuaClassViewer.renderLua(resolvedType).split("\\R");
          source.sendSuccess(() -> Component.literal("[MLua] Class view: " + canonicalName + " (runtime: " + resolvedType.getName() + ")"), false);
          for (String line : lines) {
             if (!line.isBlank()) {
                source.sendSuccess(() -> Component.literal(line), false);
             }
          }
       } catch (Throwable throwable) {
          source.sendFailure(Component.literal("[MLua] Inspect failed: " + throwable.getMessage()));
       }
    }

    private void executeReload(CommandSourceStack source) {
       if (source.hasPermission(4)) {
          this.lifecycleEpoch++;
          source.sendSuccess(() -> Component.literal("[MLua] Reloading server scripts..."), true);

            MLuaModuleLoader.get().clearRuntimeState(this, this.session, Side.SERVER);
            this.session.clearScriptState(Side.SERVER);
            this.forceRefreshScripts();
            this.engine.resetSide(Side.SERVER);
           MLuaModuleLoader.get().onReload(Side.SERVER);

          this.scripts.stream().filter(script -> script.type() == ScriptType.SERVER).forEach(script -> {
             try {
             this.execute(script, Side.SERVER, RegistrationPhase.RUNTIME, this.session.server());
             } catch (LuaError e) {
                MLuaLogger.LOGGER.error("Server script '{}' failed during reload: {}", script.displayName(), e.getMessage());
                this.reportRuntimeFailure("Server script '" + script.displayName() + "' failed during reload", e);
             }
          });

          this.scripts.stream().filter(script -> script.type() == ScriptType.ENTRYPOINT).forEach(script -> {
             try {
                this.executeEntrypoint(script, Side.SERVER, RegistrationPhase.RUNTIME, this.session.server());
             } catch (LuaError e) {
                MLuaLogger.LOGGER.error("Server entrypoint script '{}' failed during reload: {}", script.displayName(), e.getMessage());
                this.reportRuntimeFailure("Server entrypoint script '" + script.displayName() + "' failed during reload", e);
             }
          });

          if (this.session.server() != null) {
             this.runCommandScripts(this.session.server().getCommands().getDispatcher());
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

   public void reloadClientOnly() {
        this.lifecycleEpoch++;
        MLuaModuleLoader.get().clearRuntimeState(this, this.session, Side.CLIENT);
       this.session.clearScriptState(Side.CLIENT);
       this.forceRefreshScripts();
       this.engine.resetSide(Side.CLIENT);
       MLuaModuleLoader.get().onReload(Side.CLIENT);
       this.runClientCommandScripts();

       this.scripts.stream().filter(script -> script.type() == ScriptType.CLIENT).forEach(script -> {
          try {
             this.execute(script, Side.CLIENT, RegistrationPhase.RUNTIME, this.session.server());
          } catch (LuaError e) {
             MLuaLogger.LOGGER.error("Client script '{}' failed during reload: {}", script.displayName(), e.getMessage());
             this.reportRuntimeFailure("Client script '" + script.displayName() + "' failed during reload", e);
          }
       });

       this.scripts.stream().filter(script -> script.type() == ScriptType.ENTRYPOINT).forEach(script -> {
          try {
             this.executeEntrypoint(script, Side.CLIENT, RegistrationPhase.RUNTIME, this.session.server());
          } catch (LuaError e) {
             MLuaLogger.LOGGER.error("Client entrypoint script '{}' failed during reload: {}", script.displayName(), e.getMessage());
             this.reportRuntimeFailure("Client entrypoint script '" + script.displayName() + "' failed during reload", e);
          }
       });
    }

   private void runClientCommandScripts() {
      MLuaModuleLoader.get().beforeScriptPhase(this, this.session, Side.CLIENT, RegistrationPhase.COMMANDS, null);
      this.scripts.stream().filter((script) -> {
         return script.type() == ScriptType.COMMANDS;
      }).forEach((script) -> {
         try {
            this.execute(script, Side.CLIENT, RegistrationPhase.COMMANDS, this.session.server());
         } catch (LuaError var3) {
            MLuaLogger.LOGGER.error("Client command script '{}' failed and was skipped: {}", script.displayName(), var3.getMessage());
            this.reportRuntimeFailure("Client command script '" + script.displayName() + "' failed", var3);
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
      this.session.clientScheduler().tick();
      MLuaModuleLoader.get().onRuntimeTick(this, this.session, Side.CLIENT, 0.05D);
      MLuaModuleLoader.get().afterRuntimeTick(this, this.session, Side.CLIENT, 0.05D);
      this.pollClientHotReload();
   }

   /**
    * Removes this mod's signal listeners, entity components, and scheduled tasks for one side.
    * @return number of scheduled tasks removed
    */
   public int clearOwnedScriptState(Side side, String ownerModId) {
      if (side == null || ownerModId == null || ownerModId.isBlank()) return 0;
      MLuaSignal.destroyAllOwned(this.session, ownerModId, side);
      MLuaModuleLoader.get().clearOwnedState(this, this.session, side, ownerModId);
      return this.session.clearOwnedTasks(side, ownerModId);
   }

}
