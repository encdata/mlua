package dev.mlua.modules.input.service;

import dev.mlua.api.MLuaDescription;
import dev.mlua.api.MLuaLuaErrors;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.minecraft.client.MLuaKeybindController;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

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

   @MLuaDescription("Validates every keybind before installation, then registers the set as one batch. If Minecraft cannot install the mappings, the previous mapping array is restored and no partial keybind set is published. Returns instances in sorted name order.")
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
      List<KeybindDefinition> orderedDefinitions = new ArrayList<>(pending.values());
      List<MLuaKeybindController.KeybindDefinition> controllerDefinitions = orderedDefinitions.stream()
         .map(MLuaInputService::controllerDefinition).toList();
      try {
         MLuaKeybindController.registerBatch(owner, controllerDefinitions);
      } catch (IllegalArgumentException | IllegalStateException failure) {
         throw MLuaLuaErrors.javaFailure("InputService:RegisterKeybinds", failure);
      }
      for (KeybindDefinition definition : orderedDefinitions) {
         result.set(index++, keybindTable(owner, definition.name(), this.context.mlua().scheduler(),
            this.context.mlua().side()));
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

   @MLuaDescription("Sets a Minecraft or addon key mapping's logical pressed state for this addon on the client thread. Other addons can hold the same mapping independently; the press is released when this addon runtime unloads.")
   public void SetKeyDown(String name, boolean pressed) {
      this.requireClientSide(this.name());
      requireClientThread("SetKeyDown");
      KeyMapping mapping = resolveMapping(this.context.mlua().ownerModId(), name);
      if (mapping == null) throw new LuaError("[MLua] InputService:SetKeyDown could not find key mapping '" + name + "'.");
      MLuaKeybindController.setDown(mapping, pressed,
         (dev.mlua.core.MLuaRuntimeSession) this.context.mlua().session(), this.context.mlua().ownerModId());
   }

   @MLuaDescription("Validates a table of mapping names and pressed states before applying them as one owner-scoped batch. If applying a state fails, prior states are restored where possible.")
   public void SetKeyStates(LuaTable states) {
      this.requireClientSide(this.name());
      requireClientThread("SetKeyStates");
      String owner = this.context.mlua().ownerModId();
      Map<String, Boolean> requested = new TreeMap<>();
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs entry = states.next(key);
         key = entry.arg1();
         if (key.isnil()) break;
         LuaValue value = entry.arg(2);
         if (!key.isstring()) {
            throw new LuaError("[MLua] InputService:SetKeyStates requires mapping names as string keys.");
         }
         String name = requireName(key.checkjstring(), "SetKeyStates");
         if (!value.isboolean()) {
            throw new LuaError("[MLua] InputService:SetKeyStates value for '" + name + "' must be true or false.");
         }
         requested.put(name, value.toboolean());
      }

      List<MLuaKeybindController.KeyState> batch = new ArrayList<>(requested.size());
      for (Map.Entry<String, Boolean> state : requested.entrySet()) {
         KeyMapping mapping = resolveMapping(owner, state.getKey());
         if (mapping == null) {
            throw new LuaError("[MLua] InputService:SetKeyStates could not find key mapping '"
               + state.getKey() + "'. No key states were changed.");
         }
         batch.add(new MLuaKeybindController.KeyState(mapping, state.getValue()));
      }
      try {
         MLuaKeybindController.setDownBatch(batch,
            (dev.mlua.core.MLuaRuntimeSession) this.context.mlua().session(), owner);
      } catch (IllegalArgumentException | IllegalStateException failure) {
         throw MLuaLuaErrors.javaFailure("InputService:SetKeyStates", failure);
      }
   }

   @MLuaDescription("Releases every simulated key and mouse mapping held by the calling addon runtime.")
   public void ReleasePressedKeys() {
      this.requireClientSide(this.name());
      requireClientThread("ReleasePressedKeys");
      MLuaKeybindController.releaseOwnerPressedMappings(
         (dev.mlua.core.MLuaRuntimeSession) this.context.mlua().session(), this.context.mlua().ownerModId());
   }

   @MLuaDescription("Queues one Minecraft key mapping click, consumed by the normal game input loop.")
   public void ClickKey(String name) {
      this.requireClientSide(this.name());
      requireClientThread("ClickKey");
      KeyMapping mapping = resolveMapping(this.context.mlua().ownerModId(), name);
      if (mapping == null) throw new LuaError("[MLua] InputService:ClickKey could not find key mapping '" + name + "'.");
      MLuaKeybindController.click(mapping);
   }

   @MLuaDescription("Sets the pressed state of a Minecraft or other mod's key mapping by its registered identifier, bypassing this addon's keybind names.")
   public void SetMinecraftKeyDown(String name, boolean pressed) {
      this.requireClientSide(this.name());
      requireClientThread("SetMinecraftKeyDown");
      KeyMapping mapping = requireMinecraftKeyMapping(name, "SetMinecraftKeyDown");
      MLuaKeybindController.setDown(mapping, pressed,
         (dev.mlua.core.MLuaRuntimeSession) this.context.mlua().session(), this.context.mlua().ownerModId());
   }

   @MLuaDescription("Queues one click for a Minecraft or other mod's key mapping by its registered identifier, bypassing this addon's keybind names.")
   public void ClickMinecraftKey(String name) {
      this.requireClientSide(this.name());
      requireClientThread("ClickMinecraftKey");
      MLuaKeybindController.click(requireMinecraftKeyMapping(name, "ClickMinecraftKey"));
   }

   @MLuaDescription("Sets a standard Minecraft mouse action binding: 0 left attack, 1 right use, or 2 middle pick block.")
   public void SetMouseButtonDown(int button, boolean pressed) {
      this.SetMinecraftKeyDown(mouseBinding(button), pressed);
   }

   @MLuaDescription("Queues one standard Minecraft mouse action: 0 left attack, 1 right use, or 2 middle pick block.")
   public void ClickMouseButton(int button) {
      this.ClickMinecraftKey(mouseBinding(button));
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
      MLuaKeybindController.registerBatch(ownerModId, List.of(controllerDefinition(definition)));
      return keybindTable(ownerModId, definition.name(), this.context.mlua().scheduler(), this.context.mlua().side());
   }

   private static MLuaKeybindController.KeybindDefinition controllerDefinition(KeybindDefinition definition) {
      return new MLuaKeybindController.KeybindDefinition(definition.name(), definition.label(),
         definition.category(), definition.defaultKey());
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
      dev.mlua.minecraft.client.MLuaKeybindController.validateKey(selectedKey);
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

   private static KeyMapping resolveMapping(String ownerModId, String name) {
      if (name == null || name.isBlank()) throw new LuaError("[MLua] InputService requires a non-empty key mapping name.");
      return MLuaKeybindController.resolve(ownerModId, name);
   }

   private static String mouseBinding(int button) {
      return switch (button) {
         case 0 -> "key.attack";
         case 1 -> "key.use";
         case 2 -> "key.pickItem";
         default -> throw new LuaError("[MLua] Mouse button must be 0 (left), 1 (right), or 2 (middle).");
      };
   }

   private static KeyMapping requireMinecraftKeyMapping(String name, String operation) {
      if (name == null || name.isBlank()) {
         throw new LuaError("[MLua] InputService:" + operation + " requires a non-empty mapping identifier.");
      }
      KeyMapping mapping = MLuaKeybindController.minecraftKeybind(name);
      if (mapping == null) {
         throw new LuaError("[MLua] InputService:" + operation
            + " could not find Minecraft or another mod's key mapping '" + name + "'.");
      }
      return mapping;
   }

   private static void requireClientThread(String operation) {
      if (!dev.mlua.runtime.client.MLuaClientThread.isClientExecutionContext()) {
         throw new LuaError("[MLua] InputService:" + operation + " must run on Minecraft's client thread.");
      }
   }

}
