package dev.mlua.modules.events.Service;

import dev.mlua.api.MLuaDescription;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.modules.players.Service.MLuaPlayersService;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.Side;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@MLuaDescription("Provides lifecycle and gameplay signals. Each signal receives one event table containing the values available for that event.")
public final class MLuaEventsService extends MLuaReflectiveService {
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
   private static final Map<String, SignalEntry> SIGNALS = new LinkedHashMap<>();
   private static final Set<String> BUILT_IN_SIGNALS = new LinkedHashSet<>();
   private static final Set<String> RESERVED_NAMES = Set.of("PlayerAdded", "PlayerRemoving", "GetSignalNames",
      "GetUnavailableSignals", "RegisterSignal", "UnregisterSignal", "ClassName", "GetClassName",
      "GetType", "IsA", "ToString");

   static {
      builtIn("ServerTick", SERVER_TICK, "Fires after the server tick with its delta time in seconds.");
      builtIn("ClientTick", CLIENT_TICK, "Fires after the client tick with its delta time in seconds.");
      builtIn("RenderStepped", RENDER_STEPPED, "Fires at the start of each client render tick, before Minecraft draws the frame, with the frame delta time in seconds.");
      builtIn("PreRender", PRE_RENDER, "Fires at the start of each client render tick, immediately before RenderStepped.");
      builtIn("PostRender", POST_RENDER, "Fires at the end of each client render tick, after Minecraft draws the frame.");
      builtIn("EntityHurt", ENTITY_HURT, "Fires before server damage is applied; set Cancel in the event table to prevent it.");
      builtIn("EntityDeath", ENTITY_DEATH, "Fires after a living entity dies on the server.");
      builtIn("BlockBreak", BLOCK_BREAK, "Fires before a player breaks a block; set Cancel to prevent the break.");
      builtIn("BlockPlace", BLOCK_PLACE, "Fires after an entity places a block on the server.");
      builtIn("ItemUse", ITEM_USE, "Fires when a player uses an item; set Cancel to prevent the use.");
      builtIn("EntityInteract", ENTITY_INTERACT, "Fires when a player interacts with a living entity.");
      builtIn("PlayerRespawn", PLAYER_RESPAWN, "Fires after a player respawns on the server.");
      builtIn("EntitySpawn", ENTITY_SPAWN, "Fires when an entity is added to a server world.");
      builtIn("EntityUnload", ENTITY_UNLOAD, "Fires on the client when an entity leaves the loaded client world, including chunk unloads and dimension changes.");
      builtIn("WorldLoad", WORLD_LOAD, "Fires when a server world is loaded.");
      builtIn("WorldUnload", WORLD_UNLOAD, "Fires when a server world is unloaded.");
      builtIn("BlockRightClick", BLOCK_RIGHT_CLICK, "Fires when a player uses an item on a block.");
      builtIn("ItemDrop", ITEM_DROP, "Fires when a player drops an item stack.");
      builtIn("ItemPickup", ITEM_PICKUP, "Fires when a player picks up an item stack.");
      builtIn("ItemCraft", ITEM_CRAFT, "Fires when a player crafts an item stack.");
      builtIn("ContainerOpen", CONTAINER_OPEN, "Fires when a player opens a container menu.");
      builtIn("ContainerClose", CONTAINER_CLOSE, "Fires when a player closes a container menu.");
      builtIn("ProjectileHit", PROJECTILE_HIT, "Fires when a projectile collides with a block or entity.");
      builtIn("Explosion", EXPLOSION, "Fires when a server explosion starts, with its level and center position.");
      builtIn("AdvancementCompleted", ADVANCEMENT, "Fires when a player completes an advancement.");
   }

   private static void builtIn(String name, MLuaSignal signal, String description) {
      SIGNALS.put(name, new SignalEntry(signal, description, null));
      BUILT_IN_SIGNALS.add(name);
   }

   public static synchronized void registerSignal(String name, MLuaSignal signal, String description) {
      validateSignalRegistration(name, signal, description);
      if (SIGNALS.putIfAbsent(name, new SignalEntry(signal, description, null)) != null) {
         throw new IllegalStateException("Event signal is already registered: " + name);
      }
   }

   private static synchronized void registerSignal(String name, MLuaSignal signal, String description, String ownerModId) {
      validateSignalRegistration(name, signal, description);
      if (SIGNALS.putIfAbsent(name, new SignalEntry(signal, description, ownerModId)) != null) {
         throw new IllegalStateException("Event signal is already registered: " + name);
      }
   }

   private static void validateSignalRegistration(String name, MLuaSignal signal, String description) {
      if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*") || name.startsWith("__")) {
         throw new IllegalArgumentException("Event signal name must be a Lua identifier");
      }
      if (RESERVED_NAMES.contains(name)) throw new IllegalArgumentException("Event signal name is reserved: " + name);
      if (signal == null) throw new NullPointerException("signal");
      if (description == null || description.isBlank()) throw new IllegalArgumentException("Event signal description must be non-empty");
      if (SIGNALS.values().stream().anyMatch(entry -> entry.signal() == signal)) {
         throw new IllegalStateException("MLua signal instance is already registered under another event name");
      }
   }

   public static synchronized boolean unregisterSignal(String name, MLuaSignal expected) {
      if (name == null || expected == null || BUILT_IN_SIGNALS.contains(name)) return false;
      SignalEntry entry = SIGNALS.get(name);
      if (entry == null || entry.signal() != expected) return false;
      SIGNALS.remove(name);
      entry.signal().Destroy();
      return true;
   }

   private static synchronized boolean unregisterOwnedSignal(String name, String ownerModId, MLuaSignal expected) {
      SignalEntry entry = SIGNALS.get(name);
      if (entry == null || entry.signal() != expected || entry.ownerModId() == null
         || !entry.ownerModId().equals(ownerModId)) return false;
      return unregisterSignal(name, expected);
   }

   private static synchronized boolean ownsSignal(String name, String ownerModId) {
      SignalEntry entry = SIGNALS.get(name);
      return entry != null && entry.ownerModId() != null && entry.ownerModId().equals(ownerModId);
   }

   public static synchronized Set<String> signalNames() {
      return java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(SIGNALS.keySet()));
   }

   private static synchronized Map<String, SignalEntry> signalSnapshot() {
      return new LinkedHashMap<>(SIGNALS);
   }

   public static void DestroyAll() {
      signalSnapshot().values().forEach(entry -> entry.signal().Destroy());
   }

   public static void DestroySide(Side side) {
      signalSnapshot().values().forEach(entry -> entry.signal().destroySide(side));
   }

   public static void DestroyAll(String ownerModId) {
      signalSnapshot().values().forEach(entry -> entry.signal().destroyOwned(ownerModId));
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
      for (Map.Entry<String, SignalEntry> registered : signalSnapshot().entrySet()) {
         table.set(registered.getKey(), this.signalValue(registered.getValue(), ownerModId));
      }
      table.set("PlayerAdded", MLuaLuaBindings.signalInstance(MLuaPlayersService.PLAYER_ADDED, this.context.mlua().globals(), this.context.mlua().scheduler(), ownerModId, this.context.mlua().side()));
      table.set("PlayerRemoving", MLuaLuaBindings.signalInstance(MLuaPlayersService.PLAYER_REMOVING, this.context.mlua().globals(), this.context.mlua().scheduler(), ownerModId, this.context.mlua().side()));
      for (String name : unavailableSignals) table.rawset(name, org.luaj.vm2.LuaValue.NIL);
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

   @MLuaDescription("Registers a described addon signal from a registry script.")
   public LuaValue RegisterSignal(String name, LuaValue value, String description) {
      requireRegistryPhase();
      LuaValue backing = value.istable() ? value.checktable().rawget("__mlua_signal") : LuaValue.NIL;
      Object raw = backing.isuserdata() ? backing.touserdata() : null;
      if (!(raw instanceof MLuaSignal signal)) {
         throw new org.luaj.vm2.LuaError("[MLua] Events:RegisterSignal expects a Signal.new() value.");
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
      SignalEntry entry = signalSnapshot().get(name);
      this.liveServiceTable.set(name, signalValue(entry, ownerModId));
      return LuaValue.NONE;
   }

   @MLuaDescription("Removes an event signal owned by the current addon.")
   public boolean UnregisterSignal(String name) {
      requireRegistryPhase();
      String ownerModId = this.context.mlua().ownerModId();
      SignalEntry entry = signalSnapshot().get(name);
      if (entry == null || !ownsSignal(name, ownerModId)) {
         throw new org.luaj.vm2.LuaError("[MLua] This addon does not own event signal '" + name + "'.");
      }
      boolean removed = unregisterOwnedSignal(name, ownerModId, entry.signal());
      if (removed) this.liveServiceTable.rawset(name, LuaValue.NIL);
      return removed;
   }

   private LuaTable signalValue(SignalEntry entry, String ownerModId) {
      LuaTable signal = MLuaLuaBindings.signalInstance(entry.signal(), this.context.mlua().globals(),
         this.context.mlua().scheduler(), ownerModId, this.context.mlua().side());
      signal.rawset("__mlua_doc_description", org.luaj.vm2.LuaValue.valueOf(entry.description()));
      signal.rawset("__mlua_doc_category", org.luaj.vm2.LuaValue.valueOf("Events"));
      return signal;
   }

   private void requireRegistryPhase() {
      this.requirePhase(RegistrationPhase.REGISTRY,
         "Event signals can only be registered or removed from registry scripts.");
   }

   private dev.mlua.module.MLuaRegistration thisOwnerRegistration() {
      dev.mlua.module.MLuaRegistration registration = this.context.mlua().registration();
      if (registration == null) throw new org.luaj.vm2.LuaError("[MLua] Script registration scope is unavailable.");
      return registration;
   }

   private record SignalEntry(MLuaSignal signal, String description, String ownerModId) { }
}
