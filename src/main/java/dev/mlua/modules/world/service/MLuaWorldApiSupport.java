package dev.mlua.modules.world.service;

import dev.mlua.api.MLuaLuaErrors;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.modules.world.MLuaChunkWrapper;
import dev.mlua.runtime.MLuaGameThreadAccess;
import dev.mlua.runtime.server.MLuaServerThread;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Shared callback gates and argument checks used by world API binders across source overlays. */
final class MLuaWorldApiSupport {
   private MLuaWorldApiSupport() {}

   static LuaValue levelFn(Supplier<Level> supplier, LevelFunction function) {
      return varArg(args -> {
         Level level = supplier.get();
         if (level == null) return LuaValue.NIL;
         // Lua callbacks run on coroutine workers; live world access belongs to its game thread.
         return MLuaGameThreadAccess.callUnchecked(level, () -> function.apply(level, args));
      });
   }

   static LuaValue serverLevelFn(MLuaService.Context context, Supplier<Level> supplier,
      ServerLevelFunction function) {
      return varArg(args -> {
         if (context.mlua().side() != Side.SERVER) {
            throw new LuaError("[MLua] World mutation methods is server-side only. Use it from a .server.lua script.");
         }
         Level level = supplier.get();
         if (!(level instanceof ServerLevel serverLevel)) {
            throw new LuaError("[MLua] This world is not a server world");
         }
         return MLuaGameThreadAccess.callUnchecked(serverLevel, () -> function.apply(serverLevel, args));
      });
   }

   @FunctionalInterface
   interface LevelFunction {
      LuaValue apply(Level level, Varargs args);
   }

   @FunctionalInterface
   interface ServerLevelFunction {
      LuaValue apply(ServerLevel level, Varargs args);
   }

   static boolean ensureChunkLoaded(ServerLevel level, BlockPos position, boolean load) {
      if (level.hasChunkAt(position)) return true;
      if (!load) return false;
      ChunkPos chunk = new ChunkPos(position.getX() >> 4, position.getZ() >> 4);
      MLuaServerThread.call(level.getServer(),
         () -> level.getChunk(MLuaChunkWrapper.chunkX(chunk), MLuaChunkWrapper.chunkZ(chunk)));
      return level.hasChunkAt(position);
   }

   static int checkedIntArgument(Varargs args, int index, int defaultValue,
      int minimum, int maximum, String label) {
      LuaValue value = args.arg(index);
      if (value.isnil()) return defaultValue;
      if (!value.isnumber()) throw new LuaError("[MLua] " + label + " must be a whole number");
      double number = value.todouble();
      if (!Double.isFinite(number) || number != Math.rint(number)
         || number < minimum || number > maximum) {
         throw new LuaError("[MLua] " + label + " must be a whole number from " + minimum + " to " + maximum);
      }
      return (int)number;
   }

   private static LuaValue varArg(Function<Varargs, Varargs> function) {
      return new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            try {
               return function.apply(args);
            } catch (LuaError error) {
               throw error;
            } catch (Exception error) {
               throw MLuaLuaErrors.javaFailure("World service call", error);
            }
         }
      };
   }
}
