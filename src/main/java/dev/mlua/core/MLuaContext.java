package dev.mlua.core;

import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.core.require.MLuaModuleCache;
import dev.mlua.core.scheduler.MLuaTaskScheduler;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.core.service.MLuaServiceRegistry;
import dev.mlua.module.MLuaRegistration;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.luaj.vm2.LuaTable;
import net.minecraft.server.MinecraftServer;
import org.luaj.vm2.Globals;

public final class MLuaContext implements MLuaContextView {
   private final MLuaScript script;
   private final Side side;
   private final RegistrationPhase phase;
   private final MLuaServiceRegistry services;
   private final MLuaTaskScheduler scheduler;
   private final MLuaModuleCache moduleCache;
   private final MinecraftServer server;
   private final Path filesRoot;
   private final BiConsumer<String, Throwable> failureReporter;
   private final MLuaRuntimeSession session;
   private final MLuaRegistration registration;
   private volatile Globals globals;
   private final Map<String, LuaTable> materializedServices = new LinkedHashMap<>();
   private final Map<String, LuaTable> serviceProxies = new LinkedHashMap<>();

   public MLuaContext(MLuaScript script, Side side, RegistrationPhase phase, MLuaServiceRegistry services, MLuaTaskScheduler scheduler, MLuaModuleCache moduleCache, MinecraftServer server, Path filesRoot) {
      this(script, side, phase, services, scheduler, moduleCache, server, filesRoot, null, null);
   }

   public MLuaContext(MLuaScript script, Side side, RegistrationPhase phase, MLuaServiceRegistry services, MLuaTaskScheduler scheduler, MLuaModuleCache moduleCache, MinecraftServer server, Path filesRoot, BiConsumer<String, Throwable> failureReporter) {
      this(script, side, phase, services, scheduler, moduleCache, server, filesRoot, failureReporter, null);
   }

   public MLuaContext(MLuaScript script, Side side, RegistrationPhase phase, MLuaServiceRegistry services, MLuaTaskScheduler scheduler, MLuaModuleCache moduleCache, MinecraftServer server, Path filesRoot, BiConsumer<String, Throwable> failureReporter, MLuaRuntimeSession session) {
      this(script, side, phase, services, scheduler, moduleCache, server, filesRoot, failureReporter, session, null);
   }

   public MLuaContext(MLuaScript script, Side side, RegistrationPhase phase, MLuaServiceRegistry services, MLuaTaskScheduler scheduler, MLuaModuleCache moduleCache, MinecraftServer server, Path filesRoot, BiConsumer<String, Throwable> failureReporter, MLuaRuntimeSession session, MLuaRegistration registration) {
      this.script = script;
      this.side = side;
      this.phase = phase;
      this.services = services;
      this.scheduler = scheduler;
      this.moduleCache = moduleCache;
      this.server = server;
      this.filesRoot = filesRoot;
      this.failureReporter = failureReporter;
      this.session = session;
      this.registration = registration;
   }

   public MLuaScript script() {
      return this.script;
   }

   @Override
   public String ownerModId() {
      return this.script.ownerModId();
   }

   @Override
   public Side side() {
      return this.side;
   }

   @Override
   public RegistrationPhase phase() {
      return this.phase;
   }

   public MLuaServiceRegistry services() {
      return this.services;
   }

   public synchronized LuaTable materializedService(String name) {
      return this.materializedServices.get(name);
   }

   public synchronized void recordMaterializedService(String name, LuaTable table) {
      this.materializedServices.putIfAbsent(name, table);
   }

   public synchronized void forgetMaterializedService(String name, LuaTable expected) {
      this.materializedServices.remove(name, expected);
   }

   /** Resolves a service table once for this script context, including concurrent first access. */
   public synchronized LuaTable materializeService(String name, Supplier<LuaTable> factory) {
      LuaTable existing = this.materializedServices.get(name);
      if (existing != null) return existing;
      LuaTable created = java.util.Objects.requireNonNull(factory, "factory").get();
      if (created == null) throw new IllegalStateException("Service table factory returned null: " + name);
      this.materializedServices.put(name, created);
      return created;
   }

   public synchronized Map<String, LuaTable> materializedServices() {
      return Collections.unmodifiableMap(new LinkedHashMap<>(this.materializedServices));
   }

   /** Returns one stable Lua service instance per service name and script context. */
   public synchronized LuaTable serviceProxy(String name, Supplier<LuaTable> factory) {
      LuaTable existing = this.serviceProxies.get(name);
      if (existing != null) return existing;
      LuaTable created = java.util.Objects.requireNonNull(factory, "factory").get();
      if (created == null) throw new IllegalStateException("Service proxy factory returned null: " + name);
      this.serviceProxies.put(name, created);
      return created;
   }

   public MLuaTaskScheduler scheduler() {
      return this.scheduler;
   }

   @Override
   public Globals globals() { return this.globals; }

   public void bindGlobals(Globals globals) { this.globals = globals; }

   public void reportRuntimeFailure(String summary, Throwable throwable) {
      if (this.failureReporter == null) {
         this.scheduler.reportRuntimeFailure(summary, throwable);
         return;
      }
      try {
         this.failureReporter.accept(summary, throwable);
      } catch (Throwable reporterFailure) {
         MLuaLogger.LOGGER.error("MLua runtime failure reporter itself failed", reporterFailure);
         this.scheduler.reportRuntimeFailure(summary, throwable);
      }
   }

   public MLuaRuntimeSession session() {
      return this.session;
   }

   @Override
   public <T> T serviceState(String serviceName, String key, Class<T> stateType,
                             Supplier<? extends T> factory) {
      if (this.session == null) return MLuaContextView.super.serviceState(serviceName, key, stateType, factory);
      return this.session.sharedServiceState(this.side, serviceName, key, stateType, factory);
   }

   @Override
   public MLuaRegistration registration() { return this.registration != null ? this.registration : MLuaRuntime.get(); }

   public MLuaModuleCache moduleCache() {
      return this.moduleCache;
   }

   @Override
   public MinecraftServer server() {
      return this.server;
   }

   @Override
   public Path filesRoot() {
      return this.filesRoot.resolve(this.ownerModId());
   }

   @Override
   public boolean isClient() {
      return this.side == Side.CLIENT;
   }

   @Override
   public boolean isServer() {
      return this.side == Side.SERVER;
   }

   @Override
   public boolean isRegistration() {
      return this.phase != RegistrationPhase.RUNTIME;
   }

   @Override
   public boolean isPhase(RegistrationPhase phase) {
      return this.phase == phase;
   }

   @Override
   public boolean isScriptType(ScriptType type) {
      return this.script.type() == type;
   }

   public MLuaContext withScript(MLuaScript script) {
      MLuaContext context = new MLuaContext(script, this.side, this.phase, this.services, this.scheduler, this.moduleCache, this.server, this.filesRoot, this.failureReporter, this.session, this.registration);
      context.bindGlobals(this.globals);
      return context;
   }
}
