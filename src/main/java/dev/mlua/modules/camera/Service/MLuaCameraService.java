package dev.mlua.modules.camera.Service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
import dev.mlua.mc.impl.entity.MCLocalPlayerImpl;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.client.MLuaCameraController;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class MLuaCameraService extends MLuaServiceBase {
   public MLuaCameraService(MLuaService.Context context) {
      super(context);
   }

   public String name() {
      return "Camera";
   }

   public LuaTable toLua() {
      LuaTable table = new LuaTable();
      table.set("__side", "Client");
      table.set("GetCFrame", fn((args) -> {
         this.ensureClient();
         return MLuaCameraController.getCFrame().toLua();
      }));
      table.set("SetCFrame", fn((args) -> {
         this.ensureClient();
         MLuaCameraController.setCFrame(MLuaCFrame.fromLua(args.arg(2)));
         return LuaValue.NONE;
      }));
      table.set("GetPosition", fn((args) -> {
         this.ensureClient();
         return MLuaCameraController.getCFrame().toLua().get("Position");
      }));
      table.set("SetPosition", fn((args) -> {
         this.ensureClient();
         double[] p = MLuaCFrame.vector(args.arg(2));
         MLuaCFrame current = MLuaCameraController.getCFrame();
         double[] look = current.lookVector();
         MLuaCameraController.setCFrame(MLuaCFrame.lookAt(p[0], p[1], p[2], p[0] + look[0], p[1] + look[1], p[2] + look[2]));
         return LuaValue.NONE;
      }));
      table.set("GetLookVector", fn((args) -> {
         this.ensureClient();
         return MLuaCameraController.getCFrame().toLua().get("LookVector");
      }));
      table.set("LookAt", fn((args) -> {
         this.ensureClient();
         MLuaCFrame current = MLuaCameraController.getCFrame();
         double[] target = MLuaEntityWrapper.targetPosition(args.arg(2));
         MLuaCameraController.setCFrame(MLuaCFrame.lookAt(current.x(), current.y(), current.z(), target[0], target[1], target[2]));
         return LuaValue.NONE;
      }));
      table.set("GetFOV", fn((args) -> {
         this.ensureClient();
         return LuaValue.valueOf(MLuaCameraController.getFov());
      }));
      table.set("SetFOV", fn((args) -> {
         this.ensureClient();
         MLuaCameraController.setFov(args.checkdouble(2));
         return LuaValue.NONE;
      }));
      table.set("GetFOVMultiplier", fn((args) -> {
         this.ensureClient();
         return LuaValue.valueOf(MLuaCameraController.getFovMultiplier());
      }));
      table.set("SetFOVMultiplier", fn((args) -> {
         this.ensureClient();
         MLuaCameraController.setFovMultiplier(args.checkdouble(2));
         return LuaValue.NONE;
      }));
      table.set("MultiplyFOV", fn((args) -> {
         this.ensureClient();
         MLuaCameraController.setFovMultiplier(MLuaCameraController.getFovMultiplier() * args.checkdouble(2));
         return LuaValue.NONE;
      }));
      table.set("GetSubject", fn((args) -> {
         this.ensureClient();
         Entity entity = MLuaCameraController.getSubject();
         if (entity instanceof LivingEntity living) {
            if (living instanceof LocalPlayer local) {
               return MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(local), context.mlua().registration());
            } else {
               return MLuaEntityWrapper.entityTable(living, context.mlua().registration());
            }
         } else {
            return LuaValue.NIL;
         }
      }));
       table.set("SetSubject", fn((args) -> {
          this.ensureClient();
          LuaValue value = args.arg(2);
          if (value.isnil()) {
              Minecraft mc = MLuaMinecraftClient.instance();
              if (mc == null || mc.player == null) {
                 return LuaValue.NONE;
              }
              MLuaCameraController.setSubject(mc.player);
              return LuaValue.NONE;
          } else {
             LuaValue entityValue = value.istable() ? value.checktable().rawget("__entity") : LuaValue.NIL;
             if (!entityValue.isuserdata(Entity.class)) {
                throw new LuaError("[MLua] Camera:SetSubject expects a Player or Entity from the client world");
             }
             MLuaCameraController.setSubject((Entity)entityValue.checkuserdata(Entity.class));
             return LuaValue.NONE;
          }
       }));
       table.set("GetMode", fn((args) -> {
         this.ensureClient();
         return LuaValue.valueOf(MLuaCameraController.getMode());
      }));
      table.set("SetMode", fn((args) -> {
         this.ensureClient();
         MLuaCameraController.setMode(args.arg(2).tojstring());
         return LuaValue.NONE;
      }));
      table.set("GetViewMode", fn((args) -> {
         this.ensureClient();
         return LuaValue.valueOf(MLuaCameraController.getViewMode());
      }));
      table.set("SetViewMode", fn((args) -> {
         this.ensureClient();
         MLuaCameraController.setViewMode(args.arg(2).tojstring());
         return LuaValue.NONE;
      }));
      table.set("SetRightHandOffset", fn((args) -> {
         this.ensureClient();
         MLuaCameraController.setHeldItemOffset(MLuaCFrame.fromLua(args.arg(2)));
         return LuaValue.NONE;
      }));
      table.set("GetRightHandOffset", fn((args) -> {
         this.ensureClient();
         MLuaCFrame offset = MLuaCameraController.getHeldItemOffset();
         return offset != null ? offset.toLua() : LuaValue.NIL;
      }));
      table.set("SetLeftHandOffset", fn((args) -> {
         this.ensureClient();
         MLuaCameraController.setHeldItemLeftOffset(MLuaCFrame.fromLua(args.arg(2)));
         return LuaValue.NONE;
      }));
      table.set("GetLeftHandOffset", fn((args) -> {
         this.ensureClient();
         MLuaCFrame offset = MLuaCameraController.getHeldItemLeftOffset();
         return offset != null ? offset.toLua() : LuaValue.NIL;
      }));
      table.set("Reset", fn((args) -> {
         this.ensureClient();
         MLuaCameraController.reset();
         return LuaValue.NONE;
      }));
      return table;
   }

   private void ensureClient() {
      if (this.context.mlua().side() != Side.CLIENT) {
         throw new LuaError("[MLua] Camera is client-side only. Use Minecraft:GetService(\"Camera\") from a .client.lua script. Server camera control needs a future networked player.Camera API.");
      }
   }

   private static VarArgFunction fn(final Function<Varargs, LuaValue> body) {
      return new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            try {
               return (Varargs)body.apply(args);
            } catch (LuaError var3) {
               throw var3;
            } catch (Exception var4) {
               throw new LuaError("[MLua] " + var4.getMessage());
            }
         }
      };
   }
}
