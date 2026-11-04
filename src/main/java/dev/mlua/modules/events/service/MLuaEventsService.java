package dev.mlua.modules.events.service;

import dev.mlua.api.MLuaDescription;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.modules.players.service.MLuaPlayersService;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.Side;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.Varargs;

@MLuaDescription("Provides built-in lifecycle and gameplay signals, plus addon-owned signals. Payloads are specific to each signal: tick signals pass delta time directly, gameplay signals pass an event table, and lifecycle signals may pass no arguments.")
public final class MLuaEventsService extends MLuaReflectiveService {
   public static final int MAX_CUSTOM_SIGNALS = MLuaEventSignalRegistry.MAX_CUSTOM_SIGNALS;
   public static final int MAX_CUSTOM_SIGNALS_PER_ADDON = MLuaEventSignalRegistry.MAX_CUSTOM_SIGNALS_PER_ADDON;
   private static volatile Set<String> unavailableSignals = Set.of();
   private LuaTable liveServiceTable;
   public static final MLuaSignal SERVER_TICK = MLuaBuiltInEventSignals.SERVER_TICK;
   public static final MLuaSignal CLIENT_TICK = MLuaBuiltInEventSignals.CLIENT_TICK;
   public static final MLuaSignal RENDER_STEPPED = MLuaBuiltInEventSignals.RENDER_STEPPED;
   public static final MLuaSignal PRE_RENDER = MLuaBuiltInEventSignals.PRE_RENDER;
   public static final MLuaSignal POST_RENDER = MLuaBuiltInEventSignals.POST_RENDER;
   public static final MLuaSignal ENTITY_HURT = MLuaBuiltInEventSignals.ENTITY_HURT;
   public static final MLuaSignal ENTITY_DEATH = MLuaBuiltInEventSignals.ENTITY_DEATH;
   public static final MLuaSignal BLOCK_BREAK = MLuaBuiltInEventSignals.BLOCK_BREAK;
   public static final MLuaSignal BLOCK_PLACE = MLuaBuiltInEventSignals.BLOCK_PLACE;
   public static final MLuaSignal ITEM_USE = MLuaBuiltInEventSignals.ITEM_USE;
   public static final MLuaSignal ENTITY_INTERACT = MLuaBuiltInEventSignals.ENTITY_INTERACT;
   public static final MLuaSignal PLAYER_RESPAWN = MLuaBuiltInEventSignals.PLAYER_RESPAWN;
   public static final MLuaSignal ENTITY_SPAWN = MLuaBuiltInEventSignals.ENTITY_SPAWN;
   public static final MLuaSignal ENTITY_UNLOAD = MLuaBuiltInEventSignals.ENTITY_UNLOAD;
   public static final MLuaSignal WORLD_LOAD = MLuaBuiltInEventSignals.WORLD_LOAD;
   public static final MLuaSignal WORLD_UNLOAD = MLuaBuiltInEventSignals.WORLD_UNLOAD;
   public static final MLuaSignal BLOCK_RIGHT_CLICK = MLuaBuiltInEventSignals.BLOCK_RIGHT_CLICK;
   public static final MLuaSignal ITEM_DROP = MLuaBuiltInEventSignals.ITEM_DROP;
   public static final MLuaSignal ITEM_PICKUP = MLuaBuiltInEventSignals.ITEM_PICKUP;
   public static final MLuaSignal ITEM_CRAFT = MLuaBuiltInEventSignals.ITEM_CRAFT;
   public static final MLuaSignal CONTAINER_OPEN = MLuaBuiltInEventSignals.CONTAINER_OPEN;
   public static final MLuaSignal CONTAINER_CLOSE = MLuaBuiltInEventSignals.CONTAINER_CLOSE;
   public static final MLuaSignal PROJECTILE_HIT = MLuaBuiltInEventSignals.PROJECTILE_HIT;
   public static final MLuaSignal EXPLOSION = MLuaBuiltInEventSignals.EXPLOSION;
   public static final MLuaSignal ADVANCEMENT = MLuaBuiltInEventSignals.ADVANCEMENT;
   public static void registerSignal(String name, MLuaSignal signal, String description) {
      MLuaEventSignalRegistry.register(name, signal, description, null);
   }

   private static void registerSignal(String name, MLuaSignal signal, String description, String ownerModId) {
      MLuaEventSignalRegistry.register(name, signal, description, ownerModId);
   }

   /** Registers a namespaced Java-addon signal and returns a handle for firing and removal. */
   public static AddonSignal registerAddonSignal(String ownerModId, String name, String description) {
      if (ownerModId == null || !ownerModId.matches("[a-z0-9_.-]{1,128}")) {
         throw new IllegalArgumentException("[MLua] Addon event signal owner must be a valid mod id");
      }
      if (name == null || name.isBlank()) {
         throw new IllegalArgumentException("[MLua] Addon event signal name must be non-empty");
      }
      String key = name != null && name.indexOf(':') >= 0 ? name : ownerModId + ":" + name;
      MLuaSignal signal = new MLuaSignal();
      try {
         registerSignal(key, signal, description, ownerModId);
      } catch (RuntimeException | Error failure) {
         signal.destroyPermanently();
         throw failure;
      }
      return new AddonSignal(key, ownerModId, signal);
   }

   /** Java owner handle for a signal exposed in each Lua runtime's Events service. */
   public static final class AddonSignal implements AutoCloseable {
      private final String name;
      private final String ownerModId;
      private final MLuaSignal signal;

      private AddonSignal(String name, String ownerModId, MLuaSignal signal) {
         this.name = name;
         this.ownerModId = ownerModId;
         this.signal = signal;
      }

      public String name() { return this.name; }

      public void fire(Varargs payload) {
         if (payload == null) throw new NullPointerException("payload");
         this.signal.fire(payload);
      }

      @Override public void close() {
         MLuaEventSignalRegistry.unregisterOwned(this.name, this.ownerModId, this.signal);
      }
   }

   public static boolean unregisterSignal(String name, MLuaSignal expected) {
      return MLuaEventSignalRegistry.unregister(name, expected);
   }

   private static boolean unregisterOwnedSignal(String name, String ownerModId, MLuaSignal expected) {
      return MLuaEventSignalRegistry.unregisterOwned(name, ownerModId, expected);
   }

   private static boolean ownsSignal(String name, String ownerModId) {
      return MLuaEventSignalRegistry.owns(name, ownerModId);
   }

   public static Set<String> signalNames() {
      return MLuaEventSignalRegistry.names();
   }

   public static void DestroyAll() {
      MLuaEventSignalRegistry.destroyAll();
   }

   public static void DestroySide(Side side) {
      MLuaEventSignalRegistry.destroySide(side);
   }

   public static void DestroyAll(String ownerModId) {
      MLuaEventSignalRegistry.destroyOwned(ownerModId);
   }
   public MLuaEventsService(MLuaService.Context context) {
      super(context);
   }

   public static void setUnavailableSignals(Collection<String> names) {
      unavailableSignals = Set.copyOf(names);
   }

   @Override
   public String name() {
      return "Events";
   }

   @Override
   public LuaTable toLua() {
      LuaTable table = super.toLua();
      table.set("__side", "Both");
      String ownerModId = this.context.mlua().ownerModId();
      for (Map.Entry<String, MLuaEventSignalRegistry.Entry> registered : MLuaEventSignalRegistry.snapshot().entrySet()) {
         table.set(registered.getKey(), this.signalValue(registered.getValue(), ownerModId));
      }
      table.set("PlayerAdded", MLuaLuaBindings.signalInstance(MLuaPlayersService.PLAYER_ADDED, this.context.mlua().globals(), this.context.mlua().scheduler(), ownerModId, this.context.mlua().side()));
      table.set("PlayerRemoving", MLuaLuaBindings.signalInstance(MLuaPlayersService.PLAYER_REMOVING, this.context.mlua().globals(), this.context.mlua().scheduler(), ownerModId, this.context.mlua().side()));
      for (String name : unavailableSignals) table.rawset(name, LuaValue.NIL);
      this.liveServiceTable = table;
      return table;
   }

   @MLuaDescription("Returns event signal names that the active loader does not support.")
   public LuaTable GetUnavailableSignals() {
      LuaTable names = new LuaTable();
      int index = 1;
      for (String name : new java.util.TreeSet<>(unavailableSignals)) {
         names.rawset(index++, LuaValue.valueOf(name));
      }
      return names;
   }

   @MLuaDescription("Returns registered event signal names, including signals added by addon modules.")
   public LuaTable GetSignalNames() {
      LuaTable names = new LuaTable();
      int index = 1;
      Set<String> registered = new java.util.TreeSet<>(signalNames());
      registered.add("PlayerAdded");
      registered.add("PlayerRemoving");
      for (String name : registered) names.rawset(index++, LuaValue.valueOf(name));
      return names;
   }

   @MLuaDescription("Returns active signal usage and listener, waiter, and custom-signal capacities.")
   public LuaTable GetSignalLimits() {
      String ownerModId = this.context.mlua().ownerModId();
      MLuaEventSignalRegistry.Counts signalCounts = MLuaEventSignalRegistry.counts(ownerModId);
      MLuaSignal.Usage usage = MLuaSignal.usage(ownerModId);
      LuaTable limits = this.context.mlua().registration().createClassInstance("SignalLimits");
      limits.rawset("ActiveCustomSignals", LuaValue.valueOf(signalCounts.customSignals()));
      limits.rawset("AddonSignals", LuaValue.valueOf(signalCounts.addonSignals()));
      limits.rawset("MaxCustomSignals", LuaValue.valueOf(MAX_CUSTOM_SIGNALS));
      limits.rawset("MaxCustomSignalsPerAddon", LuaValue.valueOf(MAX_CUSTOM_SIGNALS_PER_ADDON));
      limits.rawset("ActiveListeners", LuaValue.valueOf(usage.listeners()));
      limits.rawset("AddonListeners", LuaValue.valueOf(usage.listenersForOwner()));
      limits.rawset("MaxListenersPerSignal", LuaValue.valueOf(MLuaSignal.MAX_LISTENERS_PER_SIGNAL));
      limits.rawset("MaxListenersPerAddonPerSignal", LuaValue.valueOf(MLuaSignal.MAX_LISTENERS_PER_OWNER));
      limits.rawset("ActiveWaiters", LuaValue.valueOf(usage.waiters()));
      limits.rawset("AddonWaiters", LuaValue.valueOf(usage.waitersForOwner()));
      limits.rawset("MaxWaitersPerSignal", LuaValue.valueOf(MLuaSignal.MAX_WAITERS_PER_SIGNAL));
      limits.rawset("MaxWaitersPerAddonPerSignal", LuaValue.valueOf(MLuaSignal.MAX_WAITERS_PER_OWNER));
      limits.freeze();
      return limits;
   }

   @MLuaDescription("Registers a described addon signal from a registry script. The runtime allows up to "
      + MAX_CUSTOM_SIGNALS_PER_ADDON + " signals per addon and " + MAX_CUSTOM_SIGNALS + " total custom signals.")
   public LuaValue RegisterSignal(String name, LuaValue value, String description) {
      requireRegistryPhase();
      LuaValue backing = value.istable() ? value.checktable().rawget("__mlua_signal") : LuaValue.NIL;
      Object raw = backing.isuserdata() ? backing.touserdata() : null;
      if (!(raw instanceof MLuaSignal signal)) {
         throw new LuaError("[MLua] Events:RegisterSignal expects a Signal.new() value.");
      }
      String ownerModId = this.context.mlua().ownerModId();
      boolean didRegister = false;
      try {
         registerSignal(name, signal, description, ownerModId);
         didRegister = true;
         this.thisOwnerRegistration().onRegistrationRollback(() -> unregisterOwnedSignal(name, ownerModId, signal));
      } catch (RuntimeException | Error failure) {
         if (didRegister) unregisterSignal(name, signal);
         throw failure;
      }
      MLuaEventSignalRegistry.Entry entry = MLuaEventSignalRegistry.snapshot().get(name);
      this.liveServiceTable.set(name, signalValue(entry, ownerModId));
      return LuaValue.NONE;
   }

   @MLuaDescription("Removes an event signal owned by the current addon.")
   public boolean UnregisterSignal(String name) {
      requireRegistryPhase();
      String ownerModId = this.context.mlua().ownerModId();
      MLuaEventSignalRegistry.Entry entry = MLuaEventSignalRegistry.snapshot().get(name);
      if (entry == null || !ownsSignal(name, ownerModId)) {
         throw new LuaError("[MLua] This addon does not own event signal '" + name + "'.");
      }
      boolean removed = unregisterOwnedSignal(name, ownerModId, entry.signal());
      if (removed) this.liveServiceTable.rawset(name, LuaValue.NIL);
      return removed;
   }

   private LuaTable signalValue(MLuaEventSignalRegistry.Entry entry, String ownerModId) {
      LuaTable signal = MLuaLuaBindings.signalInstance(entry.signal(), this.context.mlua().globals(),
         this.context.mlua().scheduler(), ownerModId, this.context.mlua().side());
      signal.rawset("__mlua_doc_description", LuaValue.valueOf(entry.description()));
      signal.rawset("__mlua_doc_category", LuaValue.valueOf("Events"));
      return signal;
   }

   private void requireRegistryPhase() {
      this.requirePhase(RegistrationPhase.REGISTRY,
         "Event signals can only be registered or removed from registry scripts.");
   }

   private dev.mlua.module.MLuaRegistration thisOwnerRegistration() {
      dev.mlua.module.MLuaRegistration registration = this.context.mlua().registration();
      if (registration == null) throw new LuaError("[MLua] Script registration scope is unavailable.");
      return registration;
   }

}
