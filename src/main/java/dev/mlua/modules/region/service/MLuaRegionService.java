package dev.mlua.modules.region.service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

@dev.mlua.api.MLuaDescription("Spatial trigger system for defining regions and detecting entity enter/exit/within events.")
public final class MLuaRegionService extends MLuaReflectiveService {
   private record RegionHandle(MLuaRegionService service, MLuaRegionStateRegistry.RegionKey key) { }

   private final MLuaRegionStateRegistry.RegionState state;

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
         MLuaRegionStateRegistry.RegionDef def = handle.service().state.regions.get(handle.key());
         return def == null || !def.spherical ? LuaValue.NIL : MLuaPlayerWrapper.positionTable(def.centerX, def.centerY, def.centerZ);
      }), "Returns the center of a spherical region, or nil for a box region.");
      registration.registerClassFunction("Region", "GetRadius", regionProperty(handle -> {
         MLuaRegionStateRegistry.RegionDef def = handle.service().state.regions.get(handle.key());
         return def == null || !def.spherical ? LuaValue.NIL : LuaValue.valueOf(def.radius);
      }), "Returns the radius of a spherical region, or nil for a box region.");
      registration.registerClassFunction("Region", "GetBounds", regionProperty(handle -> {
         MLuaRegionStateRegistry.RegionDef def = handle.service().state.regions.get(handle.key());
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
      if (!value.istable()) throw new LuaError("[MLua] Expected a Region instance.");
      LuaValue state = value.checktable().rawget("__region_handle");
      if (!state.isuserdata(RegionHandle.class)) throw new LuaError("[MLua] Invalid Region instance.");
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

   public static void DestroyAll() {
      MLuaRegionStateRegistry.destroyAll();
   }

   public static void DestroySide(Side side) {
      MLuaRegionStateRegistry.destroySide(side);
   }

   public static void DestroyOwned(Side side, String ownerModId) {
      MLuaRegionStateRegistry.destroyOwned(side, ownerModId);
   }

   public static void clearForScheduler(MLuaTaskSchedulerView scheduler, Side side) {
      MLuaRegionStateRegistry.clearForScheduler(scheduler, side);
   }

   public static void destroyOwned(MLuaTaskSchedulerView scheduler, Side side, String ownerModId) {
      MLuaRegionStateRegistry.destroyOwned(scheduler, side, ownerModId);
   }

   public MLuaRegionService(MLuaService.Context context) {
      super(context);

      this.state = MLuaRegionStateRegistry.stateFor(context.mlua().scheduler());
   }

   private MLuaRegionStateRegistry.RegionKey key(String name) {
      return new MLuaRegionStateRegistry.RegionKey(this.context.mlua().ownerModId(), this.context.mlua().side(), name);
   }

   @Override
   public String name() {
      return "Region";
   }

   public static void tick(MinecraftServer server) {
      MLuaRegionTracker.tick(server);
   }

   public static void tickClient() {
      MLuaRegionTracker.tickClient();
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
      MLuaRegionStateRegistry.RegionKey key = this.key(name);
      return this.state.regions.containsKey(key) ? this.regionHandle(key) : LuaValue.NIL;
   }

   @dev.mlua.api.MLuaDescription("Returns the regions registered by the calling mod.")
   public LuaTable GetRegions() {
      LuaTable result = new LuaTable();
      String ownerModId = this.context.mlua().ownerModId();
      Side side = this.context.mlua().side();
      List<String> names = this.state.regions.keySet().stream()
         .filter(key -> key.ownerModId().equals(ownerModId) && key.side() == side)
         .map(MLuaRegionStateRegistry.RegionKey::name)
         .sorted()
         .toList();
      for (int index = 0; index < names.size(); index++) {
         result.set(index + 1, LuaValue.valueOf(names.get(index)));
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
      if (radius < 0) throw new LuaError("[MLua] Region radius must be zero or greater.");
      if (!Double.isFinite(centerX - radius) || !Double.isFinite(centerY - radius)
            || !Double.isFinite(centerZ - radius) || !Double.isFinite(centerX + radius)
            || !Double.isFinite(centerY + radius) || !Double.isFinite(centerZ + radius)) {
         throw new LuaError("[MLua] Region bounds must remain finite.");
      }
      MLuaRegionStateRegistry.RegionKey key = this.key(name);
      this.state.regions.put(key, new MLuaRegionStateRegistry.RegionDef(key, centerX, centerY, centerZ, radius));
      this.recordRegistration(key);
      this.state.entities.remove(key);
      return this.regionHandle(key);
   }

   private LuaValue createBoxRegion(String name, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      requireRegionName(name);
      requireFinite(minX, minY, minZ, maxX, maxY, maxZ);
      if (minX > maxX || minY > maxY || minZ > maxZ) {
         throw new LuaError("[MLua] Box region minimum coordinates must not exceed maximum coordinates.");
      }
      MLuaRegionStateRegistry.RegionKey key = this.key(name);
      this.state.regions.put(key, new MLuaRegionStateRegistry.RegionDef(key, minX, minY, minZ, maxX, maxY, maxZ));
      this.recordRegistration(key);
      this.state.entities.remove(key);
      return this.regionHandle(key);
   }

   private static void requireRegionName(String name) {
      if (name == null || name.isBlank()) throw new LuaError("[MLua] Region name must not be empty.");
   }

   private static void requireFinite(double... values) {
      for (double value : values) {
         if (!Double.isFinite(value)) throw new LuaError("[MLua] Region coordinates and dimensions must be finite numbers.");
      }
   }

   private static MLuaSignal signal(Map<MLuaRegionStateRegistry.RegionKey, MLuaSignal> map, MLuaRegionStateRegistry.RegionKey key) {
      return map.computeIfAbsent(key, ignored -> new MLuaSignal());
   }

   private LuaValue entitiesInRegion(MLuaRegionStateRegistry.RegionKey key) {
      LuaTable result = new LuaTable();
      Set<MLuaEntityScope> scopes = this.state.entities.get(key);
      if (scopes == null || scopes.isEmpty()) return result;
      int i = 1;
      List<Level> levels = new ArrayList<>();
      if (this.context.mlua().side() == Side.CLIENT) {
         Level clientLevel = MLuaRegionTracker.currentClientLevel();
         if (clientLevel != null) levels.add(clientLevel);
      } else if (this.context.mlua().server() != null) {
         for (ServerLevel level : this.context.mlua().server().getAllLevels()) levels.add(level);
      }
      for (MLuaEntityScope scope : MLuaRegionMembership.ordered(scopes)) {
         Entity entity = MLuaRegionTracker.resolveEntity(scope, levels);
         if (entity != null) result.set(i++, MLuaEntityWrapper.entityTable(entity, this.context.mlua().registration()));
      }
      return result;
   }

   private LuaValue signalInstance(MLuaRegionStateRegistry.RegionKey key, String signalName) {
      Map<MLuaRegionStateRegistry.RegionKey, MLuaSignal> signals = switch (signalName) {
         case "onEnter" -> this.state.onEnter;
         case "onExit" -> this.state.onExit;
         default -> this.state.onWithin;
      };
      return MLuaLuaBindings.signalInstance(signal(signals, key), this.context.mlua().globals(),
         this.context.mlua().scheduler(), this.context.mlua().ownerModId(), this.context.mlua().side());
   }

   private LuaTable regionHandle(MLuaRegionStateRegistry.RegionKey key) {
      LuaTable handle = new LuaTable();
      handle.rawset("__region_handle", LuaValue.userdataOf(new RegionHandle(this, key)));
      return this.context.mlua().registration().attachClassInstance(handle, "Region", null);
   }

   private void removeRegion(MLuaRegionStateRegistry.RegionKey key) {
      MLuaRegionStateRegistry.removeRegion(this.state, key);
   }

   private void recordRegistration(MLuaRegionStateRegistry.RegionKey key) {
      MLuaRegistration registration = this.context.mlua().registration();
      if (registration == null) this.state.registrations.remove(key);
      else this.state.registrations.put(key, registration);
   }


}
