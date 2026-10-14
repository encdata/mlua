package dev.mlua.modules.region.Service;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.OneArgFunction;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.common.enums.Side;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.math.MLuaCFrame;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@dev.mlua.api.MLuaDescription("Spatial trigger system for defining regions and detecting entity enter/exit/within events.")
public final class MLuaRegionService extends MLuaReflectiveService {
   private record RegionHandle(MLuaRegionService service, RegionKey key) { }

   public static void registerRegionClass(MLuaRegistration registration) {
      registration.registerClass("Region", registration.requireInstanceBaseClassName(),
         "A live spatial region that tracks matching entities and exposes enter, exit, and within signals.");
      registration.registerClassFunction("Region", "GetName", regionProperty(handle -> LuaValue.valueOf(handle.key().name())),
         "Returns this region's registered name.");
      registration.registerClassFunction("Region", "GetIsActive", regionProperty(handle -> LuaValue.valueOf(handle.service().state.regions.containsKey(handle.key()))),
         "Returns whether this region is currently registered.");
      registration.registerClassFunction("Region", "GetEntityCount", regionProperty(handle -> {
         Set<MLuaEntityScope> entities = handle.service().state.entities.get(handle.key());
         return LuaValue.valueOf(entities == null ? 0 : entities.size());
      }), "Returns the number of entities currently inside this region.");
      registration.registerClassFunction("Region", "GetCenter", regionProperty(handle -> {
         RegionDef def = handle.service().state.regions.get(handle.key());
         return def == null || !def.spherical ? LuaValue.NIL : MLuaPlayerWrapper.positionTable(def.centerX, def.centerY, def.centerZ);
      }), "Returns the center of a spherical region, or nil for a box region.");
      registration.registerClassFunction("Region", "GetRadius", regionProperty(handle -> {
         RegionDef def = handle.service().state.regions.get(handle.key());
         return def == null || !def.spherical ? LuaValue.NIL : LuaValue.valueOf(def.radius);
      }), "Returns the radius of a spherical region, or nil for a box region.");
      registration.registerClassFunction("Region", "GetBounds", regionProperty(handle -> {
         RegionDef def = handle.service().state.regions.get(handle.key());
         if (def == null || def.spherical) return LuaValue.NIL;
         LuaTable bounds = new LuaTable();
         bounds.set("Min", MLuaPlayerWrapper.positionTable(def.minX, def.minY, def.minZ));
         bounds.set("Max", MLuaPlayerWrapper.positionTable(def.maxX, def.maxY, def.maxZ));
         return bounds;
      }), "Returns the minimum and maximum corners of a box region, or nil for a spherical region.");
      MLuaClassMethods.registerInstanceProperty(registration, "Region", "Name", "GetName", null,
         "string", "shared:Shared", "The region's registered name.");
      MLuaClassMethods.registerInstanceProperty(registration, "Region", "IsActive", "GetIsActive", null,
         "boolean", "shared:Shared", "Whether this region is currently registered.");
      MLuaClassMethods.registerInstanceProperty(registration, "Region", "EntityCount", "GetEntityCount", null,
         "number", "shared:Shared", "The number of entities currently inside the region.");
      MLuaClassMethods.registerInstanceProperty(registration, "Region", "Center", "GetCenter", null,
         "Vector3|nil", "shared:Shared", "The center of a spherical region, or nil for a box region.");
      MLuaClassMethods.registerInstanceProperty(registration, "Region", "Radius", "GetRadius", null,
         "number|nil", "shared:Shared", "The radius of a spherical region, or nil for a box region.");
      MLuaClassMethods.registerInstanceProperty(registration, "Region", "Bounds", "GetBounds", null,
         "RegionBounds|nil", "shared:Shared", "The minimum and maximum corners of a box region, or nil for a spherical region.");
      registration.registerClassFunction("Region", "Remove", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            RegionHandle handle = regionHandle(args.arg1());
            handle.service().removeRegion(handle.key());
            return LuaValue.NONE;
         }
      }, "Removes this region and clears its tracked entities and signals.");
      registration.registerClassFunction("Region", "GetEntities", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            RegionHandle handle = regionHandle(args.arg1());
            return handle.service().entitiesInRegion(handle.key());
         }
      }, "Returns the live entity instances currently inside this region.");
      registration.registerClassFunction("Region", "OnEnter", regionSignal("onEnter"), "Returns the signal fired when an entity enters this region.");
      registration.registerClassFunction("Region", "OnExit", regionSignal("onExit"), "Returns the signal fired when an entity leaves this region.");
      registration.registerClassFunction("Region", "OnWithin", regionSignal("onWithin"), "Returns the signal fired for entities inside this region.");
   }

   private static OneArgFunction regionProperty(java.util.function.Function<RegionHandle, LuaValue> getter) {
      return new OneArgFunction() {
         @Override public LuaValue call(LuaValue self) { return getter.apply(regionHandle(self)); }
      };
   }

   private static RegionHandle regionHandle(LuaValue value) {
      if (!value.istable()) throw new org.luaj.vm2.LuaError("[MLua] Expected a Region instance.");
      LuaValue state = value.checktable().rawget("__region_handle");
      if (!state.isuserdata(RegionHandle.class)) throw new org.luaj.vm2.LuaError("[MLua] Invalid Region instance.");
      return (RegionHandle) state.touserdata(RegionHandle.class);
   }

   private static VarArgFunction regionSignal(String name) {
      return new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            RegionHandle handle = regionHandle(args.arg1());
            return handle.service().signalInstance(handle.key(), name);
         }
      };
   }

   static record RegionKey(String ownerModId, Side side, String name) {
   }

   static final class RegionDef {
      final RegionKey key;
      final boolean spherical;
      final double centerX, centerY, centerZ;
      final double radius;
      final double minX, minY, minZ, maxX, maxY, maxZ;
      final AABB bounds;

      RegionDef(RegionKey key, double centerX, double centerY, double centerZ, double radius) {
         this.key = key;
         this.spherical = true;
         this.centerX = centerX; this.centerY = centerY; this.centerZ = centerZ;
         this.radius = radius;
         this.minX = this.minY = this.minZ = this.maxX = this.maxY = this.maxZ = 0;
         this.bounds = new AABB(centerX - radius, centerY - radius, centerZ - radius,
                                centerX + radius, centerY + radius, centerZ + radius);
      }

      RegionDef(RegionKey key, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
         this.key = key;
         this.spherical = false;
         this.centerX = this.centerY = this.centerZ = this.radius = 0;
         this.minX = minX; this.minY = minY; this.minZ = minZ;
         this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
         this.bounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
      }

      boolean contains(Vec3 pos) {
         if (spherical) {
            double dx = pos.x - centerX;
            double dy = pos.y - centerY;
            double dz = pos.z - centerZ;
            return dx * dx + dy * dy + dz * dz <= radius * radius;
         }
         return pos.x >= minX && pos.x <= maxX &&
                pos.y >= minY && pos.y <= maxY &&
                pos.z >= minZ && pos.z <= maxZ;
      }
   }

   static final class RegionState {
      final Map<RegionKey, RegionDef> regions = new ConcurrentHashMap<>();
      final Map<RegionKey, MLuaRegistration> registrations = new ConcurrentHashMap<>();
      final Map<RegionKey, Set<MLuaEntityScope>> entities = new ConcurrentHashMap<>();
      final Map<RegionKey, MLuaSignal> onEnter = new ConcurrentHashMap<>();
      final Map<RegionKey, MLuaSignal> onExit = new ConcurrentHashMap<>();
      final Map<RegionKey, MLuaSignal> onWithin = new ConcurrentHashMap<>();

      void clear() {
         for (RegionKey key : List.copyOf(this.regions.keySet())) MLuaRegionService.removeRegion(this, key);
         MLuaRegionService.destroySignals(this, key -> true);
      }

      void clearSide(Side side) {
         for (RegionKey key : List.copyOf(this.regions.keySet())) {
            if (key.side() == side) MLuaRegionService.removeRegion(this, key);
         }
         MLuaRegionService.destroySignals(this, key -> key.side() == side);
      }

      void clearOwned(Side side, String ownerModId) {
         for (RegionKey key : List.copyOf(this.regions.keySet())) {
            if (ownerModId.equals(key.ownerModId()) && (side == Side.BOTH || key.side() == side)) {
               MLuaRegionService.removeRegion(this, key);
            }
         }
         MLuaRegionService.destroySignals(this, key -> ownerModId.equals(key.ownerModId())
               && (side == Side.BOTH || key.side() == side));
      }

   }

   private static final Map<MLuaTaskSchedulerView, RegionState> STATES = new WeakHashMap<>();

   public static void DestroyAll() {
      synchronized (STATES) {
         STATES.values().forEach(RegionState::clear);
      }
   }

   public static void DestroySide(Side side) {
      if (side == null || side == Side.BOTH) return;
      synchronized (STATES) { STATES.values().forEach(state -> state.clearSide(side)); }
   }

   public static void DestroyOwned(Side side, String ownerModId) {
      if (side == null || ownerModId == null) return;
      synchronized (STATES) { STATES.values().forEach(state -> state.clearOwned(side, ownerModId)); }
   }

   public static void clearForScheduler(MLuaTaskSchedulerView scheduler, Side side) {
      if (scheduler == null) return;
      synchronized (STATES) {
         RegionState state = STATES.get(scheduler);
         if (state == null) return;
         if (side == null || side == Side.BOTH) {
            state.clear();
         } else {
            state.clearSide(side);
         }
      }
   }

   public static void destroyOwned(MLuaTaskSchedulerView scheduler, Side side, String ownerModId) {
      if (scheduler == null || side == null || ownerModId == null) return;
      synchronized (STATES) {
         RegionState state = STATES.get(scheduler);
         if (state == null) return;
         state.clearOwned(side, ownerModId);
      }
   }

   private static void destroySignals(RegionState state, Predicate<RegionKey> shouldDestroy) {
      for (Map<RegionKey, MLuaSignal> signals : List.of(state.onEnter, state.onExit, state.onWithin)) {
         for (Map.Entry<RegionKey, MLuaSignal> entry : List.copyOf(signals.entrySet())) {
            if (shouldDestroy.test(entry.getKey()) && signals.remove(entry.getKey(), entry.getValue())) entry.getValue().Destroy();
         }
      }
   }

   private static RegionState stateFor(MLuaTaskSchedulerView scheduler) {
      synchronized (STATES) { return STATES.computeIfAbsent(scheduler, ignored -> new RegionState()); }
   }

   static RegionState stateForTests(MLuaTaskSchedulerView scheduler) { return stateFor(scheduler); }
   private final RegionState state;

   public MLuaRegionService(MLuaService.Context context) {
      super(context);

      this.state = stateFor(context.mlua().scheduler());
   }

   private RegionKey key(String name) {
      return new RegionKey(this.context.mlua().ownerModId(), this.context.mlua().side(), name);
   }

   @Override
   public String name() {
      return "Region";
   }

    public static void tick(MinecraftServer server) {
       List<RegionState> states = statesSnapshot();
       if (states.isEmpty()) return;

       List<Level> levels = new ArrayList<>();
       for (ServerLevel level : server.getAllLevels()) {
          levels.add(level);
       }
       for (RegionState state : states) tickSide(state, Side.SERVER, levels);
    }

    public static void tickClient(Minecraft client) {
       if (client.level == null) return;
       for (RegionState state : statesSnapshot()) tickSide(state, Side.CLIENT, List.of(client.level));
    }

    private static List<RegionState> statesSnapshot() {
       synchronized (STATES) { return List.copyOf(STATES.values()); }
    }

    private static void tickSide(RegionState state, Side side, List<Level> levels) {
       if (state.regions.isEmpty()) return;
       if (levels.isEmpty()) return;

       for (Map.Entry<RegionKey, RegionDef> entry : state.regions.entrySet()) {
          RegionKey key = entry.getKey();
          if (key.side() != side) continue;
          String name = key.name();
          RegionDef def = entry.getValue();

          Set<MLuaEntityScope> currentScopes = new HashSet<>();
          for (Level level : levels) {
             List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, def.bounds);
             for (LivingEntity living : entities) {
                if (def.contains(living.position())) {
                   currentScopes.add(MLuaEntityScope.of(living));
                }
             }
          }

          Set<MLuaEntityScope> previousScopes = state.entities.getOrDefault(key, Collections.emptySet());
          MLuaRegionMembership.Delta delta = MLuaRegionMembership.transition(currentScopes, previousScopes);
          state.entities.put(key, Set.copyOf(currentScopes));

          MLuaSignal enterSig = state.onEnter.get(key);
          if (enterSig != null) {
             for (MLuaEntityScope scope : delta.entered()) {
                fireEntitySignal(state.registrations.get(key), enterSig, levels, scope, name);
             }
          }

          MLuaSignal exitSig = state.onExit.get(key);
          if (exitSig != null) {
             for (MLuaEntityScope scope : delta.exited()) {
                fireEntitySignal(state.registrations.get(key), exitSig, levels, scope, name);
             }
          }

          MLuaSignal withinSig = state.onWithin.get(key);
          if (withinSig != null) {
             for (MLuaEntityScope scope : delta.within()) {
                fireEntitySignal(state.registrations.get(key), withinSig, levels, scope, name);
             }
          }
       }
    }

    private static void fireEntitySignal(MLuaRegistration registration, MLuaSignal signal, List<Level> levels, MLuaEntityScope scope, String regionName) {
       Entity entity = resolveEntity(scope, levels);
       LuaTable args = new LuaTable();
       if (entity instanceof LivingEntity living) {
          args.set("Entity", MLuaEntityWrapper.entityTable(living, registration));
       }
       args.set("Region", LuaValue.valueOf(regionName));
       try {
          signal.fire(args);
       } catch (Exception e) {
          MLuaLogger.LOGGER.error("[MLua] Region signal handler error", e);
       }
    }

    private static Entity resolveEntity(MLuaEntityScope scope, List<Level> levels) {
       for (Level level : levels) {
          if (!level.dimension().location().toString().equals(scope.dimension())) continue;
          Entity entity = level.getEntity(scope.entityId());
          if (entity != null && entity.getUUID().equals(scope.uuid())) return entity;
       }
       return null;
    }

   public LuaTable toLua() {
      LuaTable table = super.toLua();
      table.set("__side", "Both");
      return table;
   }

   @dev.mlua.api.MLuaDescription("Creates a spherical region from a native Vector3 or CFrame center position and radius.")
   public LuaValue CreateRegion(String name, LuaValue center, double radius) {
      double[] position = MLuaCFrame.vector(center);
      return this.createRegion(name, position[0], position[1], position[2], radius);
   }

   @dev.mlua.api.MLuaDescription("Creates a spherical region from a Vector3 or CFrame position and radius.")
   public LuaValue CreateSphere(String name, LuaValue center, double radius) {
      double[] position = MLuaCFrame.vector(center);
      return this.createRegion(name, position[0], position[1], position[2], radius);
   }

   @dev.mlua.api.MLuaDescription("Creates an axis-aligned box region from native minimum and maximum Vector3 or CFrame positions.")
   public LuaValue CreateBoxRegion(String name, LuaValue minimum, LuaValue maximum) {
      double[] min = MLuaCFrame.vector(minimum);
      double[] max = MLuaCFrame.vector(maximum);
      return this.createBoxRegion(name, min[0], min[1], min[2], max[0], max[1], max[2]);
   }

   @dev.mlua.api.MLuaDescription("Creates an axis-aligned box region from minimum and maximum Vector3 or CFrame positions.")
   public LuaValue CreateBox(String name, LuaValue minimum, LuaValue maximum) {
      double[] min = MLuaCFrame.vector(minimum);
      double[] max = MLuaCFrame.vector(maximum);
      return this.createBoxRegion(name, min[0], min[1], min[2], max[0], max[1], max[2]);
   }

   @dev.mlua.api.MLuaDescription("Removes a region by identifier.")
   public void RemoveRegion(String name) {
      this.removeRegion(this.key(name));
   }

   @dev.mlua.api.MLuaDescription("Returns a region by identifier, or nil when missing.")
   public LuaValue GetRegion(String name) {
      RegionKey key = this.key(name);
      return this.state.regions.containsKey(key) ? this.regionHandle(key) : LuaValue.NIL;
   }

   @dev.mlua.api.MLuaDescription("Returns the regions registered by the calling mod.")
   public LuaTable GetRegions() {
      LuaTable result = new LuaTable();
      int index = 1;
      String ownerModId = this.context.mlua().ownerModId();
      Side side = this.context.mlua().side();
      for (RegionKey key : this.state.regions.keySet()) {
         if (key.ownerModId().equals(ownerModId) && key.side() == side) {
            result.set(index++, LuaValue.valueOf(key.name()));
         }
      }
      return result;
   }

   @dev.mlua.api.MLuaDescription("Returns the signal fired for entities entering a region.")
   public LuaValue OnEnter(String name) {
      return this.signalInstance(this.key(name), "onEnter");
   }

   @dev.mlua.api.MLuaDescription("Returns the signal fired for entities leaving a region.")
   public LuaValue OnExit(String name) {
      return this.signalInstance(this.key(name), "onExit");
   }

   @dev.mlua.api.MLuaDescription("Returns the signal fired for entities inside a region.")
   public LuaValue OnWithin(String name) {
      return this.signalInstance(this.key(name), "onWithin");
   }

   @dev.mlua.api.MLuaDescription("Returns entities currently inside a region.")
   public LuaValue GetEntitiesInRegion(String name) {
      return this.entitiesInRegion(this.key(name));
   }

   private LuaValue createRegion(String name, double centerX, double centerY, double centerZ, double radius) {
      requireRegionName(name);
      requireFinite(centerX, centerY, centerZ, radius);
      if (radius < 0) throw new org.luaj.vm2.LuaError("[MLua] Region radius must be zero or greater.");
      RegionKey key = this.key(name);
      this.state.regions.put(key, new RegionDef(key, centerX, centerY, centerZ, radius));
      this.recordRegistration(key);
      this.state.entities.remove(key);
      return this.regionHandle(key);
   }

   private LuaValue createBoxRegion(String name, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      requireRegionName(name);
      requireFinite(minX, minY, minZ, maxX, maxY, maxZ);
      if (minX > maxX || minY > maxY || minZ > maxZ) {
         throw new org.luaj.vm2.LuaError("[MLua] Box region minimum coordinates must not exceed maximum coordinates.");
      }
      RegionKey key = this.key(name);
      this.state.regions.put(key, new RegionDef(key, minX, minY, minZ, maxX, maxY, maxZ));
      this.recordRegistration(key);
      this.state.entities.remove(key);
      return this.regionHandle(key);
   }

   private static void requireRegionName(String name) {
      if (name == null || name.isBlank()) throw new org.luaj.vm2.LuaError("[MLua] Region name must not be empty.");
   }

   private static void requireFinite(double... values) {
      for (double value : values) {
         if (!Double.isFinite(value)) throw new org.luaj.vm2.LuaError("[MLua] Region coordinates and dimensions must be finite numbers.");
      }
   }

   private static MLuaSignal signal(Map<RegionKey, MLuaSignal> map, RegionKey key) {
      return map.computeIfAbsent(key, ignored -> new MLuaSignal());
   }

   private LuaValue entitiesInRegion(RegionKey key) {
      LuaTable result = new LuaTable();
      Set<MLuaEntityScope> scopes = this.state.entities.get(key);
      if (scopes == null || scopes.isEmpty()) return result;
      int i = 1;
      List<Level> levels = new ArrayList<>();
      if (this.context.mlua().side() == Side.CLIENT) {
         if (Minecraft.getInstance().level != null) levels.add(Minecraft.getInstance().level);
      } else if (this.context.mlua().server() != null) {
         for (ServerLevel level : this.context.mlua().server().getAllLevels()) levels.add(level);
      }
      for (MLuaEntityScope scope : scopes) {
         Entity entity = resolveEntity(scope, levels);
         if (entity instanceof LivingEntity living) result.set(i++, MLuaEntityWrapper.entityTable(living, this.context.mlua().registration()));
      }
      return result;
   }

   private LuaValue signalInstance(RegionKey key, String signalName) {
      Map<RegionKey, MLuaSignal> signals = switch (signalName) {
         case "onEnter" -> this.state.onEnter;
         case "onExit" -> this.state.onExit;
         default -> this.state.onWithin;
      };
      return MLuaLuaBindings.signalInstance(signal(signals, key), this.context.mlua().globals(),
         this.context.mlua().scheduler(), this.context.mlua().ownerModId(), this.context.mlua().side());
   }

   private LuaTable regionHandle(RegionKey key) {
      LuaTable handle = new LuaTable();
      handle.rawset("__region_handle", LuaValue.userdataOf(new RegionHandle(this, key)));
      return this.context.mlua().registration().attachClassInstance(handle, "Region", null);
   }

   private void removeRegion(RegionKey key) {
      removeRegion(this.state, key);
   }

   private void recordRegistration(RegionKey key) {
      MLuaRegistration registration = this.context.mlua().registration();
      if (registration == null) this.state.registrations.remove(key);
      else this.state.registrations.put(key, registration);
   }

   private static void removeRegion(RegionState state, RegionKey key) {
      state.regions.remove(key);
      state.registrations.remove(key);
      state.entities.remove(key);
      MLuaSignal s = state.onEnter.remove(key);
      if (s != null) s.Destroy();
      s = state.onExit.remove(key);
      if (s != null) s.Destroy();
      s = state.onWithin.remove(key);
      if (s != null) s.Destroy();
   }

}
