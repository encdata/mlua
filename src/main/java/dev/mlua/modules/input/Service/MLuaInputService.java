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
import java.util.TreeMap;
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
      this.requireClientSide(this.name());
      String checkedName = requireName(name, "RegisterKeybind");
      LuaTable props = null;
      if (properties.isPresent()) {
         if (!properties.get().istable()) {
            throw new LuaError("[MLua] RegisterKeybind properties must be a definition table.");
         }
         props = properties.get().checktable();
      }
      return this.registerKeybind(this.context.mlua().ownerModId(), checkedName, props);
   }

   @MLuaDescription("Validates and registers every keybind in a keyed definition table, returning instances in sorted name order.")
   public LuaTable RegisterKeybinds(LuaTable definitions) {
      this.requireClientSide(this.name());
      Map<String, KeybindDefinition> pending = new TreeMap<>();
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs next = definitions.next(key);
         key = next.arg1();
         if (key.isnil()) break;
         LuaValue value = next.arg(2);
         if (!key.isstring()) {
            throw new LuaError("[MLua] RegisterKeybinds requires string keys for keybind definitions, got " + key.typename());
         }
         String name = requireName(key.tojstring(), "RegisterKeybinds");
         if (!value.istable()) {
            throw new LuaError("[MLua] RegisterKeybinds entry '" + name + "' must contain a keybind definition table.");
         }
         pending.put(name, keybindDefinition(name, value.checktable(), "RegisterKeybinds"));
      }

      LuaTable result = new LuaTable();
      int index = 1;
      String owner = this.context.mlua().ownerModId();
      for (KeybindDefinition definition : pending.values()) {
         result.set(index++, this.registerKeybind(owner, definition));
      }
      return result;
   }

   @MLuaDescription("Returns the change signal for a keybind registered by this mod.")
   public LuaTable ListenForKeybind(String name) {
      this.requireClientSide(this.name());
      String owner = this.context.mlua().ownerModId();
      if (!MLuaKeybindController.hasLocalKeybind(owner, name)) {
         throw new LuaError("[MLua] ListenForKeybind only works for MLua-registered keybinds.");
      }
      return MLuaLuaBindings.signalInstance(MLuaKeybindController.signal(owner, name, this.context.mlua().scheduler()),
         this.context.mlua().globals(), this.context.mlua().scheduler(), owner, this.context.mlua().side());
   }

   @MLuaDescription("Returns whether a registered or Minecraft keybind is currently pressed.")
   public boolean IsKeyDown(String name) {
      this.requireClientSide(this.name());
      String owner = this.context.mlua().ownerModId();
      if (MLuaKeybindController.hasLocalKeybind(owner, name)) return MLuaKeybindController.isDown(owner, name);
      KeyMapping mapping = MLuaKeybindController.minecraftKeybind(name);
      return mapping != null && MLuaKeybindController.isDown(mapping);
   }

   @MLuaDescription("Changes the key assigned to a registered keybind.")
   public void SetKeybind(String name, LuaValue key) {
      this.requireClientSide(this.name());
      MLuaKeybindController.setKey(this.context.mlua().ownerModId(), name, key);
   }

   @MLuaDescription("Returns the live keybind record for a registered or Minecraft keybind.")
   public LuaValue GetKeybind(String name) {
      this.requireClientSide(this.name());
      String owner = this.context.mlua().ownerModId();
      if (MLuaKeybindController.hasLocalKeybind(owner, name)) {
         return keybindTable(owner, name, this.context.mlua().scheduler(), this.context.mlua().side());
      }
      KeyMapping mapping = MLuaKeybindController.minecraftKeybind(name);
      return mapping == null ? LuaValue.NIL : mcKeybindTable(mapping);
   }

   @MLuaDescription("Returns keybinds registered by the calling mod and available Minecraft keybinds.")
   public LuaTable GetKeybinds() {
      this.requireClientSide(this.name());
      LuaTable result = new LuaTable();
      int index = 1;
      String owner = this.context.mlua().ownerModId();
      for (Map.Entry<String, String> entry : new TreeMap<>(MLuaKeybindController.allKeys(owner)).entrySet()) {
         result.set(index++, keybindTable(owner, entry.getKey(),
            this.context.mlua().scheduler(), this.context.mlua().side()));
      }
      for (KeyMapping mapping : MLuaKeybindController.minecraftKeybinds().stream()
            .sorted(java.util.Comparator.comparing(MLuaKeybindController::name)).toList()) {
         result.set(index++, mcKeybindTable(mapping));
      }
      return result;
   }

   @MLuaDescription("Returns a Minecraft keybind by name, or nil when missing.")
   public LuaValue GetMinecraftKeybind(String name) {
      this.requireClientSide(this.name());
      for (KeyMapping mapping : MLuaKeybindController.minecraftKeybinds()) {
         if (MLuaKeybindController.name(mapping).equals(name)) return mcKeybindTable(mapping);
      }
      return LuaValue.NIL;
   }

   @MLuaDescription("Returns the available Minecraft keybinds.")
   public LuaTable GetMinecraftKeybinds() {
      this.requireClientSide(this.name());
      LuaTable result = new LuaTable();
      int index = 1;
      for (KeyMapping mapping : MLuaKeybindController.minecraftKeybinds().stream()
            .sorted(java.util.Comparator.comparing(MLuaKeybindController::name)).toList()) {
         result.set(index++, mcKeybindTable(mapping));
      }
      return result;
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
      return this.registerKeybind(ownerModId, keybindDefinition(name, props, "RegisterKeybind"));
   }

   private LuaTable registerKeybind(String ownerModId, KeybindDefinition definition) {
      MLuaKeybindController.register(ownerModId, definition.name(), definition.label(), definition.category(), definition.defaultKey());
      return keybindTable(ownerModId, definition.name(), this.context.mlua().scheduler(), this.context.mlua().side());
   }

   private static KeybindDefinition keybindDefinition(String name, LuaTable props, String operation) {
      if (props == null) props = new LuaTable();
      String label = optionalString(props, "label", "Label", name, operation);
      String category = optionalString(props, "category", "Category", "key.categories.mlua", operation);
      LuaValue defaultKey = aliasedValue(props, "defaultKey", "DefaultKey", operation);
      LuaValue keyAlias = aliasedValue(props, "key", "Key", operation);
      if (!defaultKey.isnil() && !keyAlias.isnil()) {
         throw new LuaError("[MLua] " + operation + " options 'defaultKey' and 'key' are aliases; provide only one.");
      }
      LuaValue selectedKey = defaultKey.isnil() ? keyAlias : defaultKey;
      if (!selectedKey.isnil() && !selectedKey.isstring() && !selectedKey.isnumber() && !selectedKey.istable()) {
         throw new LuaError("[MLua] " + operation + " defaultKey must be a key name, key code, key table, or nil.");
      }
      return new KeybindDefinition(name, label, category, selectedKey);
   }

   private static String optionalString(LuaTable props, String lowerName, String upperName,
                                        String defaultValue, String operation) {
      LuaValue value = aliasedValue(props, lowerName, upperName, operation);
      if (value.isnil()) return defaultValue;
      if (!value.isstring()) {
         throw new LuaError("[MLua] " + operation + " option '" + lowerName + "' must be a string.");
      }
      String text = value.checkjstring().trim();
      if (text.isEmpty()) throw new LuaError("[MLua] " + operation + " option '" + lowerName + "' cannot be blank.");
      return text;
   }

   private static LuaValue aliasedValue(LuaTable props, String lowerName, String upperName, String operation) {
      LuaValue lower = props.rawget(lowerName);
      LuaValue upper = props.rawget(upperName);
      if (!lower.isnil() && !upper.isnil()) {
         throw new LuaError("[MLua] " + operation + " options '" + lowerName + "' and '" + upperName
            + "' are aliases; provide only one.");
      }
      return lower.isnil() ? upper : lower;
   }

   private static String requireName(String value, String operation) {
      String name = value == null ? "" : value.trim();
      if (name.isEmpty() || !name.equals(value)) {
         throw new LuaError("[MLua] " + operation + " requires a non-blank name without surrounding whitespace.");
      }
      return name;
   }

   private record KeybindDefinition(String name, String label, String category, LuaValue defaultKey) { }

   private LuaTable mcKeybindTable(KeyMapping mapping) {
      LuaTable table = new LuaTable();
      table.rawset("__key_mapping", LuaValue.userdataOf(mapping));
      return this.context.mlua().registration().attachClassInstance(table, "MinecraftKeybind", null);
   }

}
