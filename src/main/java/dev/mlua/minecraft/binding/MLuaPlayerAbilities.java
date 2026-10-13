package dev.mlua.minecraft.binding;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import dev.mlua.core.service.MLuaServiceProperties;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.world.entity.player.Player;

public final class MLuaPlayerAbilities {
   private MLuaPlayerAbilities() {
   }

   public static LuaTable abilitiesTable(final Player player) {
      LuaTable table = new LuaTable();
      table.rawset("GetWalkSpeed", luaFn(() -> {
         return LuaValue.valueOf((double)player.getAbilities().getWalkingSpeed());
      }));
      table.rawset("SetWalkSpeed", varArg((args) -> {
         player.getAbilities().setWalkingSpeed((float)args.checkdouble(2));
         player.onUpdateAbilities();
         return LuaValue.NONE;
      }));
      table.rawset("GetFlySpeed", luaFn(() -> {
         return LuaValue.valueOf((double)player.getAbilities().getFlyingSpeed());
      }));
      table.rawset("SetFlySpeed", varArg((args) -> {
         player.getAbilities().setFlyingSpeed((float)args.checkdouble(2));
         player.onUpdateAbilities();
         return LuaValue.NONE;
      }));
      table.rawset("GetFlying", luaFn(() -> {
         return LuaValue.valueOf(player.getAbilities().flying);
      }));
      table.rawset("SetFlying", varArg((args) -> {
         player.getAbilities().flying = args.checkboolean(2);
         player.onUpdateAbilities();
         return LuaValue.NONE;
      }));
      table.rawset("GetMayFly", luaFn(() -> {
         return LuaValue.valueOf(player.getAbilities().mayfly);
      }));
      table.rawset("SetMayFly", varArg((args) -> {
         player.getAbilities().mayfly = args.checkboolean(2);
         player.onUpdateAbilities();
         return LuaValue.NONE;
      }));
      table.rawset("GetInstabuild", luaFn(() -> {
         return LuaValue.valueOf(player.getAbilities().instabuild);
      }));
      table.rawset("SetInstabuild", varArg((args) -> {
         player.getAbilities().instabuild = args.checkboolean(2);
         player.onUpdateAbilities();
         return LuaValue.NONE;
      }));
      table.rawset("GetInvulnerable", luaFn(() -> {
         return LuaValue.valueOf(player.getAbilities().invulnerable);
      }));
      table.rawset("SetInvulnerable", varArg((args) -> {
         player.getAbilities().invulnerable = args.checkboolean(2);
         player.onUpdateAbilities();
         return LuaValue.NONE;
      }));
      return MLuaServiceProperties.attach(table);
   }

   private static LuaValue luaFn(final Supplier<LuaValue> fn) {
      return new ZeroArgFunction() {
         public LuaValue call() {
            try {
               return (LuaValue)fn.get();
            } catch (Exception var2) {
               throw new LuaError("[MLua] " + var2.getMessage());
            }
         }
      };
   }

   private static LuaValue varArg(final Function<Varargs, LuaValue> fn) {
      return new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            try {
               return (Varargs)fn.apply(args);
            } catch (LuaError var3) {
               throw var3;
            } catch (Exception var4) {
               throw new LuaError("[MLua] " + var4.getMessage());
            }
         }
      };
   }
}
