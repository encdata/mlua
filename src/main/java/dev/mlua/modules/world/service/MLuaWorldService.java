package dev.mlua.modules.world.service;

import dev.mlua.api.MLuaLuaErrors;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.common.service.MLuaServiceMetadata;
import dev.mlua.modules.world.WorldModule;
import dev.mlua.modules.world.MLuaWorldChunkApi;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.modules.players.MLuaClientPlayerAccess;
import dev.mlua.runtime.server.MLuaServerThread;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

public final class MLuaWorldService extends MLuaServiceBase {
   private final MLuaWorldInstanceCache worldInstances = new MLuaWorldInstanceCache();

   public MLuaWorldService(MLuaService.Context context) {
      super(context);
   }

   public String name() {
      return "World";
   }

   public LuaTable toLua() {
      LuaTable table = new LuaTable();
      table.set("__side", LuaValue.valueOf(this.context.mlua().side().name()));
      table.set("Current", varArg((args) -> this.worldValue(this.currentLevel())));
      table.set("Get", varArg((args) -> this.worldValue(this.resolveLevel(args.checkjstring(serviceArgumentIndex(args, table, 1))))));
      table.set("GetAll", varArg((args) -> this.allWorlds()));
      table.set("GetSide", varArg((args) -> LuaValue.valueOf(this.context.mlua().side().name())));
      table.set("GetWeather", currentWorldMethod(table, "GetWeather"));
      table.set("SetWeather", currentWorldMethod(table, "SetWeather"));
      WorldModule.bindWorldServiceMethods(table, methodName -> this.currentWorldMethod(table, methodName));
      MLuaServiceMetadata.attachMethodDescriptions(table, WorldModule.worldServiceMethods());
      return supportColonAndDotCalls(table);
   }

   private LuaValue currentWorldMethod(LuaTable service, String methodName) {
      return varArg(args -> {
         Level level = this.currentLevel();
         if (level == null) return LuaValue.NIL;
         LuaTable instance = this.worldTable(() -> level);
         LuaValue method = instance.get(methodName);
         if (!method.isfunction()) {
            throw new LuaError("[MLua] World service class declares '" + methodName
               + "', but the current live World instance does not implement it.");
         }
         int firstArgument = serviceArgumentIndex(args, service, 1);
         int argumentCount = Math.max(0, args.narg() - firstArgument + 1);
         LuaValue[] forwarded = new LuaValue[argumentCount + 1];
         forwarded[0] = instance;
         for (int index = 0; index < argumentCount; index++) {
            forwarded[index + 1] = args.arg(firstArgument + index);
         }
         return method.invoke(LuaValue.varargsOf(forwarded));
      });
   }

   private LuaValue worldValue(Level level) {
      return level == null ? LuaValue.NIL : this.worldTable(() -> level);
   }

   private synchronized LuaTable worldTable(Supplier<Level> supplier) {
      Level initial = supplier.get();
      if (initial == null) return new LuaTable();
      LuaTable cached = worldInstances.get(initial);
      if (cached != null) return cached;
      LuaTable fields = new LuaTable();
      fields.set("__side", LuaValue.valueOf(initial.isClientSide ? "CLIENT" : "SERVER"));
      fields.set("__world", LuaValue.userdataOf(initial));
      fields.set("__level", LuaValue.userdataOf(initial));
      var registration = context.mlua().registration();
      LuaTable table = registration.createClassInstance("World", fields);

      MLuaWorldEnvironmentApi.bind(table, supplier, context);
      MLuaWorldBlockQueryApi.bind(table, supplier, context);
      MLuaWorldRaycastApi.bind(table, supplier, context.mlua().registration());
      MLuaWorldEntityQueryApi.bind(table, supplier, context.mlua().registration());

      MLuaWorldMutationApi.bind(table, supplier, context, context.mlua().registration(), context.mlua().ownerModId());
      MLuaWorldDataApi.bind(table, supplier, context);

      MLuaWorldChunkApi.bind(table, this.context.mlua().registration());
      WorldModule.bindWorldInstanceMethods(this.context.mlua().registration(), table);
      worldInstances.put(initial, table);
      return table;
   }

   private synchronized LuaTable allWorlds() {
      LuaTable worlds = new LuaTable();
      Set<Level> activeLevels = Collections.newSetFromMap(new IdentityHashMap<>());
      int index = 1;
      if (this.context.mlua().side() == Side.SERVER && this.context.mlua().server() != null) {
         for (ServerLevel level : this.context.mlua().server().getAllLevels()) {
            activeLevels.add(level);
            worlds.set(index++, this.worldTable(() -> level));
         }
      } else if (this.context.mlua().side() == Side.CLIENT) {
         Level current = this.currentLevel();
         if (current != null) {
            activeLevels.add(current);
            worlds.set(index, this.worldTable(() -> current));
         }
      }
      worldInstances.retain(activeLevels);
      return worlds;
   }

   private Level currentLevel() {
      if (this.context.mlua().side() == Side.CLIENT) {
         return clientLevel();
      }
      return this.context.mlua().server() == null ? null : this.context.mlua().server().overworld();
   }

   private Level resolveLevel(String id) {
      if (id == null || id.isEmpty()) {
         return this.currentLevel();
      }
      ResourceLocation target = MLuaResourceLocations.tryParseOrNull(id);
      if (target == null) {
         return null;
      }

      if (this.context.mlua().side() == Side.SERVER && this.context.mlua().server() != null) {
         for (ServerLevel level : this.context.mlua().server().getAllLevels()) {
            if (level.dimension().location().equals(target)) {
               return level;
            }
         }
      }

      if (this.context.mlua().side() == Side.CLIENT) {
         Level clientLevel = clientLevel();
         return clientLevel != null && clientLevel.dimension().location().equals(target) ? clientLevel : null;
      }
      return null;
   }

   private static Level clientLevel() {
      try {
         Object level = MLuaClientPlayerAccess.level();
         return level instanceof Level clientLevel ? clientLevel : null;
      } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
         return null;
      }
   }

   private LuaValue varArg(Function<Varargs, Varargs> fn) {
      return MLuaLuaErrors.varargsOperation("World service call", args -> {
         MinecraftServer server = context.mlua().server();
         if (context.mlua().side() == Side.SERVER && server != null && !server.isSameThread()) {
            return MLuaServerThread.call(server, () -> fn.apply(args));
         }
         return fn.apply(args);
      });
   }

}
