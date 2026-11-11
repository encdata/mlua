package dev.mlua.core;

import org.luaj.vm2.LuaValue;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.core.script.MLuaScriptRoot;
import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.runtime.assets.MLuaGeneratedAssetPack;
import dev.mlua.core.service.MLuaServiceRegistry;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.classes.MLuaClassRegistry;
import dev.mlua.runtime.registry.MLuaContentTypeRegistry;
import dev.mlua.api.registry.MLuaRegistry;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.module.MLuaModuleLoader;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaRegistration;
import com.mojang.brigadier.CommandDispatcher;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
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
   private final MLuaRuntimeServiceRegistration serviceRegistration;
   private final AtomicLong lifecycleEpoch = new AtomicLong();
   private final MLuaRuntimeWorldLifecycle worldLifecycle = new MLuaRuntimeWorldLifecycle();
   private final MLuaRuntimeScriptExecutor scriptExecutor;
   private final MLuaRuntimeRegistryScripts registryScripts;
   private final MLuaRuntimeMixinScripts mixinScripts;
   private final MLuaRuntimeCommandScripts commandScripts;
   private final MLuaRuntimeLifecycleScripts lifecycleScripts;
   private final MLuaRuntimeClientScripts clientScripts;
   private final MLuaRuntimeResourceReload resourceReload;
   private final MLuaRuntimeReload reload;
   private boolean bootstrapped;

   public MLuaRuntime() {
      this(new MLuaRuntimeSession());
   }

   public MLuaRuntime(MLuaRuntimeSession session) {
      this.session = Objects.requireNonNull(session, "session");
      this.serviceRegistration = new MLuaRuntimeServiceRegistration(this.services, this.classes, this);
      this.scriptExecutor = new MLuaRuntimeScriptExecutor(this);
      this.registryScripts = new MLuaRuntimeRegistryScripts(this, this.scriptCatalog,
         this.scriptExecutor, new MLuaRegistryScriptLifecycle(this));
      this.mixinScripts = new MLuaRuntimeMixinScripts(this, this.scriptCatalog, this.scriptExecutor);
      this.commandScripts = new MLuaRuntimeCommandScripts(this, this.session, this.scriptCatalog,
         this.scriptExecutor);
      this.lifecycleScripts = new MLuaRuntimeLifecycleScripts(this, this.session, this.scriptCatalog,
         this.scriptExecutor, this.worldLifecycle);
      this.clientScripts = new MLuaRuntimeClientScripts(this, this.session, this.scriptCatalog,
         this.scriptExecutor, this.commandScripts, this.worldLifecycle);
      this.resourceReload = new MLuaRuntimeResourceReload(this, this.scriptCatalog);
      this.reload = new MLuaRuntimeReload(this, this.session, this.scriptCatalog, this.scriptExecutor);
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

   void recordClientMessage(String message) {
      this.diagnostics.recordClientMessage(message);
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
             this.serviceRegistration.registerIfAvailable(name, this.serviceClassName(name));
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
      if (this.registryScripts.hasRun()) {
         MLuaLogger.LOGGER.info("Registered runtime script root for '{}': {}. Registry scripts in this root will not run this load cycle.", root.modId(), root.path());
      }

   }

   public synchronized void replaceResourceScripts(List<MLuaScript> scripts) {
      MLuaRuntimeScriptCatalog.ResourceReload reload = this.scriptCatalog.replaceResourceScripts(scripts);
      MLuaLogger.LOGGER.info("Loaded {} resource-pack MLua scripts", scripts.size());
      this.onResourceScriptsReload(reload.replacedPaths(), reload.nativeRegistryScriptsChanged());
   }

   private void onResourceScriptsReload(Set<Path> replacedScriptPaths, boolean nativeRegistryScriptsChanged) {
      this.resourceReload.reload(replacedScriptPaths, nativeRegistryScriptsChanged);
   }

   void runEntrypointScripts(List<MLuaScript> scripts, String entrypointName, Side side,
                                    MinecraftServer server) {
      for (MLuaScript script : scripts) {
         if (!script.type().supportsEntrypoint(entrypointName, side)
               || !ScriptType.declaresEntrypoint(script.source(), entrypointName)) continue;
         try {
            this.scriptExecutor.executeEntrypoint(script, side, RegistrationPhase.RUNTIME,
               server, entrypointName, null, this);
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
      this.serviceRegistration.register(name, className, side, factory, description, functionDescriptions);
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
      return this.lifecycleEpoch.get();
   }

   void advanceLifecycleEpoch() {
      this.lifecycleEpoch.incrementAndGet();
   }

   void executeRuntimeScript(MLuaScript script, Side side, RegistrationPhase phase, MinecraftServer server) {
      this.scriptExecutor.execute(script, side, phase, server);
   }

   public synchronized void runRegistryScripts() {
      this.registryScripts.run();
   }

   void executeRegistryScript(MLuaScript script, boolean entrypoint) {
      this.registryScripts.execute(script, entrypoint);
   }

   void clearRegistryScriptRegistrations(Set<Path> scriptPaths) {
      this.registryScripts.clearRegistrations(scriptPaths);
   }

   void unregisterMixinScope(Path path) {
      this.registryScripts.unregisterMixinScope(path);
   }

   public void runMixinScripts() {
      this.ensureScriptsCurrent();
      this.mixinScripts.run();
   }

   public void runServerScripts(MinecraftServer server) {
      this.lifecycleScripts.runServerScripts(server);
   }

   /** Dispatches world lifecycle entrypoints after the normal WorldLoad/WorldUnload signals. */
   public void runWorldLifecycleEntrypoint(Level level, boolean loaded) {
      this.lifecycleScripts.runWorldLifecycleEntrypoint(level, loaded);
   }

   public synchronized void onServerStopping(MinecraftServer server) {
      if (!this.lifecycleScripts.ownsServer(server)) return;
      this.lifecycleEpoch.incrementAndGet();
      this.lifecycleScripts.onServerStopping(server);
   }

   public void runCommandScripts(CommandDispatcher<CommandSourceStack> dispatcher) {
      this.ensureScriptsCurrent();
      this.commandScripts.run(Side.SERVER, dispatcher);
   }

   void runClientCommandScripts() {
      this.clientScripts.runCommandScripts();
   }

   public void runClientScriptsOnce() {
      this.clientScripts.runOnce();
   }

   void ensureScriptsCurrent() {
      this.refreshScripts(false);
   }

   void forceRefreshScripts() {
      this.refreshScripts(true);
   }

   private void refreshScripts(boolean force) {
      MLuaRuntimeScriptCatalog.ScriptSnapshot snapshot =
         this.scriptCatalog.refresh(force || !this.scriptExecutor.initialized());
      if (this.scriptExecutor.replaceScripts(snapshot.scripts(), snapshot.generation())) {
         this.session.clearModuleCache(Side.BOTH);
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
      if (!messages.isEmpty()) {
         MLuaRuntimeClientScripts.invokeClientHook("flushDiagnostics", new Class<?>[]{List.class}, messages);
      }
   }

   public void pollClientHotReload() {
      this.clientScripts.pollHotReload();
      this.flushPendingClientDiagnostics();
   }

   void generateAssetPack() {
      if (LoaderApi.platform().isClient()) this.clientScripts.generateAssets();
      else MLuaGeneratedAssetPack.generate();
   }

   public void onRegisterCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
      MLuaRuntimeCommands.register(this, dispatcher);
      this.runCommandScripts(dispatcher);
   }

   boolean hasPermissionForReload(CommandSourceStack source) {
      //--version 26.1
      return dev.mlua.mc.impl.command.MLuaPermissionAdapter.hasPermission(source, 4);
      //--endversion
      //--version 1.20.1,1.21.1
      return source.hasPermission(4);
      //--endversion
   }

   void executeReload(CommandSourceStack source) {
      this.reload.execute(source);
   }

   void reloadWorldLoadEntrypoints(MinecraftServer server) {
      this.lifecycleScripts.reloadWorldLoadEntrypoints(server);
   }

   void reloadClientWorldLoadEntrypoints(boolean forceReload) {
      this.clientScripts.reloadWorldLoadEntrypoints(forceReload);
   }

   public void reloadClientOnly() {
      this.lifecycleEpoch.incrementAndGet();
      this.clientScripts.reload();
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
      this.worldLifecycle.queueClientTransition(level, loaded);
   }

   private void drainClientWorldLifecycleTransitions() {
      if (!LoaderApi.platform().isClient()) {
         this.worldLifecycle.clearPendingClientTransitions();
         return;
      }
      this.worldLifecycle.drainClientTransitions(transition -> {
         try {
            this.runWorldLifecycleEntrypoint(transition.level(), transition.loaded());
         } catch (RuntimeException failure) {
            this.reportRuntimeFailure("Client world lifecycle entrypoint failed", failure);
         }
      });
   }

   public int clearOwnedScriptState(Side side, String ownerModId) {
      if (side == null || ownerModId == null || ownerModId.isBlank()) return 0;
      int removedTasks = this.session.clearOwnedTasks(side, ownerModId);
      MLuaSignal.destroyAllOwned(this.session, ownerModId, side);
      MLuaModuleLoader.get().clearOwnedState(this, this.session, side, ownerModId);
      return removedTasks;
   }

}
