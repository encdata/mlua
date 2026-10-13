package dev.mlua.modules.input.Service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.minecraft.client.MLuaKeybindController;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.KeyMapping;

public final class MLuaInputService extends MLuaServiceBase {
   public MLuaInputService(MLuaService.Context context) {
      super(context);
   }

   public String name() {
      return "InputService";
   }

   public LuaTable toLua() {
      LuaTable table = new LuaTable();
      table.set("__side", "Client");
      table.set("RegisterKeybind", fn((args) -> {
         this.ensureClient();
         String name = args.checkjstring(2);
         LuaTable props = args.arg(3).opttable((LuaTable)null);
         return this.registerKeybind(this.context.mlua().ownerModId(), name, props);
      }));
      table.set("RegisterKeybinds", fn((args) -> {
         this.ensureClient();
         LuaTable definitions = args.checktable(2);
         LuaTable out = new LuaTable();
         int i = 1;
         LuaValue key = LuaValue.NIL;

         while(true) {
            LuaValue value;
            do {
               Varargs next = definitions.next(key);
               key = next.arg1();
               if (key.isnil()) {
                  return out;
               }

               value = next.arg(2);
            } while(!value.istable());

             LuaTable props = value.checktable();
             if (!key.isstring()) {
                throw new LuaError("[MLua] RegisterKeybinds requires string keys for keybind definitions, got " + key.typename());
             }
             String name = key.tojstring();
             if (name.isBlank()) {
                throw new LuaError("[MLua] RegisterKeybinds entries need a non-blank name");
             }

            out.set(i++, this.registerKeybind(this.context.mlua().ownerModId(), name, props));
         }
      }));
      table.set("ListenForKeybind", fn((args) -> {
         this.ensureClient();
         String name = args.checkjstring(2);
         if (MLuaKeybindController.hasLocalKeybind(this.context.mlua().ownerModId(), name)) {
            return MLuaLuaBindings.signalInstance(MLuaKeybindController.signal(this.context.mlua().ownerModId(), name, this.context.mlua().scheduler()), this.context.mlua().globals(), this.context.mlua().scheduler(), this.context.mlua().ownerModId(), this.context.mlua().side());
         } else {
            throw new LuaError("[MLua] ListenForKeybind only works for MLua-registered keybinds.");
         }
      }));
      table.set("IsKeyDown", fn((args) -> {
         this.ensureClient();
         String name = args.checkjstring(2);
         if (MLuaKeybindController.hasLocalKeybind(this.context.mlua().ownerModId(), name)) {
            return LuaValue.valueOf(MLuaKeybindController.isDown(this.context.mlua().ownerModId(), name));
         } else {
            KeyMapping mapping = MLuaKeybindController.minecraftKeybind(name);
            return LuaValue.valueOf(mapping != null && MLuaKeybindController.isDown(mapping));
         }
      }));
      table.set("SetKeybind", fn((args) -> {
         this.ensureClient();
         MLuaKeybindController.setKey(this.context.mlua().ownerModId(), args.checkjstring(2), args.arg(3));
         return LuaValue.NONE;
      }));
      table.set("GetKeybind", fn((args) -> {
         this.ensureClient();
         String name = args.checkjstring(2);
         if (MLuaKeybindController.hasLocalKeybind(this.context.mlua().ownerModId(), name)) {
            return keybindTable(this.context.mlua().ownerModId(), name, this.context.mlua().scheduler(), this.context.mlua().side());
         } else {
            KeyMapping mapping = MLuaKeybindController.minecraftKeybind(name);
            return (LuaValue)(mapping != null ? mcKeybindTable(mapping) : LuaValue.NIL);
         }
      }));
      table.set("GetKeybinds", fn((args) -> {
         this.ensureClient();
         LuaTable out = new LuaTable();
         int i = 1;
         Iterator var4 = MLuaKeybindController.allKeys(this.context.mlua().ownerModId()).entrySet().iterator();

         while(var4.hasNext()) {
            Map.Entry<String, String> entry = (Map.Entry)var4.next();
            LuaTable keybind = keybindTable(this.context.mlua().ownerModId(), (String)entry.getKey(), this.context.mlua().scheduler(), this.context.mlua().side());
            out.set(i++, keybind);
         }

         var4 = MLuaKeybindController.minecraftKeybinds().iterator();

         while(var4.hasNext()) {
            KeyMapping mapping = (KeyMapping)var4.next();
            out.set(i++, mcKeybindTable(mapping));
         }

         return out;
      }));
      table.set("GetMinecraftKeybind", fn((args) -> {
         this.ensureClient();
         String name = args.checkjstring(2);
         Iterator var3 = MLuaKeybindController.minecraftKeybinds().iterator();

         KeyMapping mapping;
         do {
            if (!var3.hasNext()) {
               return LuaValue.NIL;
            }

            mapping = (KeyMapping)var3.next();
         } while(!MLuaKeybindController.name(mapping).equals(name));

         return mcKeybindTable(mapping);
      }));
      table.set("GetMinecraftKeybinds", fn((args) -> {
         this.ensureClient();
         LuaTable out = new LuaTable();
         int i = 1;
         Iterator var4 = MLuaKeybindController.minecraftKeybinds().iterator();

         while(var4.hasNext()) {
            KeyMapping mapping = (KeyMapping)var4.next();
            out.set(i++, mcKeybindTable(mapping));
         }

         return out;
      }));
      return table;
   }

   private void ensureClient() {
      if (this.context.mlua().side() != Side.CLIENT) {
         throw new LuaError("[MLua] InputService is client-side only. Use it from a .client.lua script.");
      }
   }

   private LuaTable keybindTable(String ownerModId, String name, MLuaTaskSchedulerView scheduler, Side side) {
      LuaTable table = new LuaTable();
      table.rawset("__keybind_owner", LuaValue.valueOf(ownerModId));
      table.rawset("__keybind_name", LuaValue.valueOf(name));
      table.set("Signal", MLuaLuaBindings.signalInstance(MLuaKeybindController.signal(ownerModId, name, scheduler),
         this.context.mlua().globals(), scheduler, ownerModId, side));
      return this.context.mlua().registration().attachClassInstance(table, "Keybind", null);
   }

   private LuaTable registerKeybind(String ownerModId, String name, LuaTable props) {
      String label = props != null ? props.get("label").optjstring(name) : name;
      String category = props != null ? props.get("category").optjstring("key.categories.mlua") : "key.categories.mlua";
      LuaValue defaultKey = props != null ? firstNonNil(props.get("defaultKey"), props.get("key")) : LuaValue.NIL;
      MLuaKeybindController.register(ownerModId, name, label, category, defaultKey);
      return keybindTable(ownerModId, name, this.context.mlua().scheduler(), this.context.mlua().side());
   }

   private LuaTable mcKeybindTable(KeyMapping mapping) {
      LuaTable table = new LuaTable();
      table.rawset("__key_mapping", LuaValue.userdataOf(mapping));
      return this.context.mlua().registration().attachClassInstance(table, "MinecraftKeybind", null);
   }

   private static LuaValue firstNonNil(LuaValue a, LuaValue b) {
      return !a.isnil() ? a : b;
   }

   private static VarArgFunction fn(final Function<Varargs, LuaValue> body) {
      return new VarArgFunction() {
         public Varargs invoke(Varargs args) {
            try {
               return (Varargs)body.apply(args);
            } catch (LuaError var3) {
               throw var3;
            } catch (Exception var4) {
                    throw new LuaError("[MLua] " + var4.toString());
            }
         }
      };
   }
}
