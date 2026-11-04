package dev.mlua.modules.region.service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.math.MLuaVector;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
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

@dev.mlua.api.MLuaDescription("Spatial trigger system for defining regions and detecting entity enter/exit/within events.")
public final class MLuaRegionService extends MLuaReflectiveService {
   private final MLuaRegionStateRegistry.RegionState state;

   public static void registerRegionClass(MLuaRegistration registration) {
      MLuaRegionClassBinding.register(registration);
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

   LuaValue entitiesInRegion(MLuaRegionStateRegistry.RegionKey key) {
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

   LuaValue signalInstance(MLuaRegionStateRegistry.RegionKey key, String signalName) {
      Map<MLuaRegionStateRegistry.RegionKey, MLuaSignal> signals = switch (signalName) {
         case "onEnter" -> this.state.onEnter;
         case "onExit" -> this.state.onExit;
         default -> this.state.onWithin;
      };
      return MLuaLuaBindings.signalInstance(signal(signals, key), this.context.mlua().globals(),
         this.context.mlua().scheduler(), this.context.mlua().ownerModId(), this.context.mlua().side());
   }

   LuaTable regionHandle(MLuaRegionStateRegistry.RegionKey key) {
      return MLuaRegionClassBinding.newHandle(this, key);
   }

   void removeRegion(MLuaRegionStateRegistry.RegionKey key) {
      MLuaRegionStateRegistry.removeRegion(this.state, key);
   }

   MLuaRegistration registration() {
      return this.context.mlua().registration();
   }

   boolean isRegionActive(MLuaRegionStateRegistry.RegionKey key) {
      return this.state.regions.containsKey(key);
   }

   int regionEntityCount(MLuaRegionStateRegistry.RegionKey key) {
      Set<MLuaEntityScope> entities = this.state.entities.get(key);
      return entities == null ? 0 : entities.size();
   }

   LuaValue regionCenter(MLuaRegionStateRegistry.RegionKey key) {
      MLuaRegionStateRegistry.RegionDef def = this.state.regions.get(key);
      return def == null || !def.spherical ? LuaValue.NIL
         : MLuaVector.vector3(def.centerX, def.centerY, def.centerZ);
   }

   LuaValue regionRadius(MLuaRegionStateRegistry.RegionKey key) {
      MLuaRegionStateRegistry.RegionDef def = this.state.regions.get(key);
      return def == null || !def.spherical ? LuaValue.NIL : LuaValue.valueOf(def.radius);
   }

   LuaValue regionBounds(MLuaRegionStateRegistry.RegionKey key) {
      MLuaRegionStateRegistry.RegionDef def = this.state.regions.get(key);
      if (def == null || def.spherical) return LuaValue.NIL;
      LuaTable bounds = new LuaTable();
      bounds.set("Min", MLuaVector.vector3(def.minX, def.minY, def.minZ));
      bounds.set("Max", MLuaVector.vector3(def.maxX, def.maxY, def.maxZ));
      return bounds;
   }

   private void recordRegistration(MLuaRegionStateRegistry.RegionKey key) {
      MLuaRegistration registration = this.context.mlua().registration();
      if (registration == null) this.state.registrations.remove(key);
      else this.state.registrations.put(key, registration);
   }


}
