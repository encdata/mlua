package dev.mlua.modules.input.Service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import dev.mlua.api.MLuaDescription;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.minecraft.client.MLuaKeybindController;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.KeyMapping;

@MLuaDescription("Provides client input state and input change events.")
public final class MLuaInputService extends MLuaReflectiveService {
   public MLuaInputService(MLuaService.Context context) {
      super(context);
   }

   public String name() {
      return "InputService";
   }

   public LuaTable toLua() {
      LuaTable table = super.toLua();
      table.set("__side", "Client");
      return table;
   }

   @MLuaDescription("Registers one keybind for the calling mod.")
   public LuaTable RegisterKeybind(String name, Optional<LuaValue> properties) {
      this.ensureClient();
      LuaTable props = properties.isPresent() ? properties.get().checktable() : null;
      return this.registerKeybind(this.context.mlua().ownerModId(), name, props);
   }

   @MLuaDescription("Registers several keybinds from a keyed definition table.")
   public LuaTable RegisterKeybinds(LuaTable definitions) {
      this.ensureClient();
      LuaTable result = new LuaTable();
      int index = 1;
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs next = definitions.next(key);
         key = next.arg1();
         if (key.isnil()) return result;
         LuaValue value = next.arg(2);
         if (!value.istable()) continue;
         if (!key.isstring()) {
            throw new LuaError("[MLua] RegisterKeybinds requires string keys for keybind definitions, got " + key.typename());
         }
         String name = key.tojstring();
         if (name.isBlank()) throw new LuaError("[MLua] RegisterKeybinds entries need a non-blank name");
         result.set(index++, this.registerKeybind(this.context.mlua().ownerModId(), name, value.checktable()));
      }
   }

   @MLuaDescription("Returns the change signal for a keybind registered by this mod.")
   public LuaTable ListenForKeybind(String name) {
      this.ensureClient();
      String owner = this.context.mlua().ownerModId();
      if (!MLuaKeybindController.hasLocalKeybind(owner, name)) {
         throw new LuaError("[MLua] ListenForKeybind only works for MLua-registered keybinds.");
      }
      return MLuaLuaBindings.signalInstance(MLuaKeybindController.signal(owner, name, this.context.mlua().scheduler()),
         this.context.mlua().globals(), this.context.mlua().scheduler(), owner, this.context.mlua().side());
   }

   @MLuaDescription("Returns whether a registered or Minecraft keybind is currently pressed.")
   public boolean IsKeyDown(String name) {
      this.ensureClient();
      String owner = this.context.mlua().ownerModId();
      if (MLuaKeybindController.hasLocalKeybind(owner, name)) return MLuaKeybindController.isDown(owner, name);
      KeyMapping mapping = MLuaKeybindController.minecraftKeybind(name);
      return mapping != null && MLuaKeybindController.isDown(mapping);
   }

   @MLuaDescription("Changes the key assigned to a registered keybind.")
   public void SetKeybind(String name, LuaValue key) {
      this.ensureClient();
      MLuaKeybindController.setKey(this.context.mlua().ownerModId(), name, key);
   }

   @MLuaDescription("Returns the live keybind record for a registered or Minecraft keybind.")
   public LuaValue GetKeybind(String name) {
      this.ensureClient();
      String owner = this.context.mlua().ownerModId();
      if (MLuaKeybindController.hasLocalKeybind(owner, name)) {
         return keybindTable(owner, name, this.context.mlua().scheduler(), this.context.mlua().side());
      }
      KeyMapping mapping = MLuaKeybindController.minecraftKeybind(name);
      return mapping == null ? LuaValue.NIL : mcKeybindTable(mapping);
   }

   @MLuaDescription("Returns keybinds registered by the calling mod and available Minecraft keybinds.")
   public LuaTable GetKeybinds() {
      this.ensureClient();
      LuaTable result = new LuaTable();
      int index = 1;
      for (Map.Entry<String, String> entry : MLuaKeybindController.allKeys(this.context.mlua().ownerModId()).entrySet()) {
         result.set(index++, keybindTable(this.context.mlua().ownerModId(), entry.getKey(),
            this.context.mlua().scheduler(), this.context.mlua().side()));
      }
      for (KeyMapping mapping : MLuaKeybindController.minecraftKeybinds()) {
         result.set(index++, mcKeybindTable(mapping));
      }
      return result;
   }

   @MLuaDescription("Returns a Minecraft keybind by name, or nil when missing.")
   public LuaValue GetMinecraftKeybind(String name) {
      this.ensureClient();
      for (KeyMapping mapping : MLuaKeybindController.minecraftKeybinds()) {
         if (MLuaKeybindController.name(mapping).equals(name)) return mcKeybindTable(mapping);
      }
      return LuaValue.NIL;
   }

   @MLuaDescription("Returns the available Minecraft keybinds.")
   public LuaTable GetMinecraftKeybinds() {
      this.ensureClient();
      LuaTable result = new LuaTable();
      int index = 1;
      for (KeyMapping mapping : MLuaKeybindController.minecraftKeybinds()) {
         result.set(index++, mcKeybindTable(mapping));
      }
      return result;
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

}
