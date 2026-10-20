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
   public static final MLuaSignal SERVER_TICK = new MLuaSignal();
   public static final MLuaSignal CLIENT_TICK = new MLuaSignal();
   public static final MLuaSignal RENDER_STEPPED = new MLuaSignal();
   public static final MLuaSignal PRE_RENDER = new MLuaSignal();
   public static final MLuaSignal POST_RENDER = new MLuaSignal();
   public static final MLuaSignal ENTITY_HURT = new MLuaSignal();
   public static final MLuaSignal ENTITY_DEATH = new MLuaSignal();
   public static final MLuaSignal BLOCK_BREAK = new MLuaSignal();
   public static final MLuaSignal BLOCK_PLACE = new MLuaSignal();
   public static final MLuaSignal ITEM_USE = new MLuaSignal();
   public static final MLuaSignal ENTITY_INTERACT = new MLuaSignal();
   public static final MLuaSignal PLAYER_RESPAWN = new MLuaSignal();
   public static final MLuaSignal ENTITY_SPAWN = new MLuaSignal();
   public static final MLuaSignal ENTITY_UNLOAD = new MLuaSignal();
   public static final MLuaSignal WORLD_LOAD = new MLuaSignal();
   public static final MLuaSignal WORLD_UNLOAD = new MLuaSignal();
   public static final MLuaSignal BLOCK_RIGHT_CLICK = new MLuaSignal();
   public static final MLuaSignal ITEM_DROP = new MLuaSignal();
   public static final MLuaSignal ITEM_PICKUP = new MLuaSignal();
   public static final MLuaSignal ITEM_CRAFT = new MLuaSignal();
   public static final MLuaSignal CONTAINER_OPEN = new MLuaSignal();
   public static final MLuaSignal CONTAINER_CLOSE = new MLuaSignal();
   public static final MLuaSignal PROJECTILE_HIT = new MLuaSignal();
   public static final MLuaSignal EXPLOSION = new MLuaSignal();
   public static final MLuaSignal ADVANCEMENT = new MLuaSignal();

   static {
      builtIn("ServerTick", SERVER_TICK, "Fires after the server tick with its delta time in seconds.");
      builtIn("ClientTick", CLIENT_TICK, "Fires after the client tick with its delta time in seconds.");
      builtIn("RenderStepped", RENDER_STEPPED, "Fires at the start of each client render tick, before Minecraft draws the frame, with the frame delta time in seconds.");
      builtIn("PreRender", PRE_RENDER, "Fires at the start of each client render tick, immediately before RenderStepped, with the frame duration in seconds.");
      builtIn("PostRender", POST_RENDER, "Fires at the end of each client render tick, after Minecraft draws the frame, with that frame's duration in seconds.");
      builtIn("EntityHurt", ENTITY_HURT, "Fires on the server after the entity passes its invulnerability check and before armor and health mitigation. Amount is the damage value at the loader hook, not final health loss; earlier processing such as shield blocking may already have changed it. Set Cancel to prevent health damage.");
      builtIn("EntityDeath", ENTITY_DEATH, "Fires after a living entity dies on the server.");
      builtIn("BlockBreak", BLOCK_BREAK, "Fires before a player breaks a block; set Cancel to prevent the break.");
      builtIn("BlockPlace", BLOCK_PLACE, "Fires after an entity places a block on the server.");
      builtIn("ItemUse", ITEM_USE, "Fires when a player uses an item; set Cancel to prevent the use.");
      builtIn("EntityInteract", ENTITY_INTERACT, "Fires when a player interacts with a living entity.");
      builtIn("PlayerRespawn", PLAYER_RESPAWN, "Fires after a player respawns on the server.");
      builtIn("EntitySpawn", ENTITY_SPAWN, "Fires when an entity is added to a server world.");
      builtIn("EntityUnload", ENTITY_UNLOAD, "Fires on the client when an entity leaves the loaded client world, including chunk unloads and dimension changes.");
      builtIn("WorldLoad", WORLD_LOAD, "Fires when a server or client world becomes available. The event contains Level, Dimension, and Side.");
      builtIn("WorldUnload", WORLD_UNLOAD, "Fires when a server or client world is removed. The event contains Level, Dimension, and Side.");
      builtIn("BlockRightClick", BLOCK_RIGHT_CLICK, "Fires when a player uses an item on a block.");
      builtIn("ItemDrop", ITEM_DROP, "Fires when a player drops an item stack.");
      builtIn("ItemPickup", ITEM_PICKUP, "Fires after a player picks up items. The Item field contains a copy of the stack amount actually transferred, including partial pickups.");
      builtIn("ItemCraft", ITEM_CRAFT, "Fires when a player crafts an item stack.");
      builtIn("ContainerOpen", CONTAINER_OPEN, "Fires after a player opens a menu. The event table contains a live Container wrapper with slot mutation enabled while that menu remains open; retained wrappers become read-only after it closes or is replaced.");
      builtIn("ContainerClose", CONTAINER_CLOSE, "Fires while a player closes a menu. The event table contains a read-only Container wrapper for inspecting final slot contents.");
      builtIn("ProjectileHit", PROJECTILE_HIT, "Fires when a projectile collides with a block or entity.");
      builtIn("Explosion", EXPLOSION, "Fires when a server explosion starts, with its level and center position.");
      builtIn("AdvancementCompleted", ADVANCEMENT, "Fires when a player completes an advancement.");
   }

   private static void builtIn(String name, MLuaSignal signal, String description) {
      MLuaEventSignalRegistry.registerBuiltIn(name, signal, description);
   }

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
