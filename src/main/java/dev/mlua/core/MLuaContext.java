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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
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
   private volatile Object executionLock;
   private final Map<String, LuaTable> materializedServices = new LinkedHashMap<>();
   private volatile Map<String, LuaValue> contextGlobals = Map.of();
   private volatile boolean contextGlobalsConfigured;
   private volatile LuaTable documentationTable;
   private final Map<String, LuaTable> serviceProxies = new LinkedHashMap<>();
   private final ArrayDeque<String> serviceMaterializationStack = new ArrayDeque<>();

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
      this.executionLock = this;
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

   public synchronized LuaTable materializeService(String name, Supplier<LuaTable> factory) {
      if (name == null || name.isBlank()) throw new IllegalArgumentException("Service name must be non-empty");
      java.util.Objects.requireNonNull(factory, "factory");
      LuaTable existing = this.materializedServices.get(name);
      if (existing != null) return existing;
      if (this.serviceMaterializationStack.contains(name)) {
         List<String> cycle = new ArrayList<>();
         boolean inCycle = false;
         for (String active : this.serviceMaterializationStack) {
            if (active.equals(name)) inCycle = true;
            if (inCycle) cycle.add(active);
         }
         cycle.add(name);
         throw new IllegalStateException("[MLua] Circular service initialization: " + String.join(" -> ", cycle));
      }
      this.serviceMaterializationStack.addLast(name);
      try {
         LuaTable created = factory.get();
         if (created == null) throw new IllegalStateException("Service table factory returned null: " + name);
         this.materializedServices.put(name, created);
         return created;
      } catch (RuntimeException | Error failure) {
         this.materializedServices.remove(name);
         throw failure;
      } finally {
         this.serviceMaterializationStack.removeLast();
      }
   }

   public synchronized Map<String, LuaTable> materializedServices() {
      return Collections.unmodifiableMap(new LinkedHashMap<>(this.materializedServices));
   }

   public synchronized LuaTable serviceProxy(String name, Supplier<LuaTable> factory) {
      if (name == null || name.isBlank()) throw new IllegalArgumentException("Service name must be non-empty");
      java.util.Objects.requireNonNull(factory, "factory");
      LuaTable existing = this.serviceProxies.get(name);
      if (existing != null) return existing;
      LuaTable created = factory.get();
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

   public synchronized LuaTable documentationTable(Supplier<LuaTable> factory) {
      if (this.documentationTable == null) this.documentationTable = factory.get();
      return this.documentationTable;
   }

   public synchronized boolean contextGlobalsConfigured() { return this.contextGlobalsConfigured; }

   public synchronized void configureContextGlobals(Map<String, LuaValue> bindings) {
      this.contextGlobals = Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
      this.contextGlobalsConfigured = true;
   }

   public Map<String, LuaValue> contextGlobals() { return this.contextGlobals; }

   @Override
   public Object executionLock() { return this.executionLock; }

   public void bindExecutionLock(Object executionLock) {
      this.executionLock = executionLock == null ? this : executionLock;
   }

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
      context.bindExecutionLock(this.executionLock);
      return context;
   }
}
