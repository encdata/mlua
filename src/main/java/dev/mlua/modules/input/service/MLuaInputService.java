package dev.mlua.modules.input.service;

import dev.mlua.api.MLuaDescription;
import dev.mlua.api.MLuaLuaErrors;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.scheduler.MLuaTaskSchedulerView;
import dev.mlua.minecraft.client.MLuaKeybindController;
import dev.mlua.runtime.client.MLuaClientThread;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.client.KeyMapping;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

@MLuaDescription("Provides client input state and input change events.")
public final class MLuaInputService extends MLuaReflectiveService {
   private final MLuaInputActions actions;
   public MLuaInputService(MLuaService.Context context) {
      super(context);
      this.actions = new MLuaInputActions(context);
   }

   @Override
   public String name() {
      return "InputService";
   }

   @Override
   public LuaTable toLua() {
      LuaTable table = super.toLua();
      table.set("__side", "Client");
      return table;
   }

   @MLuaDescription("Registers one keybind for the calling mod.")
   public LuaTable RegisterKeybind(String name, Optional<LuaValue> properties) {
      this.requireClientSide(this.name());
      String checkedName = MLuaKeybindDefinitions.requireName(name, "RegisterKeybind");
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
      Map<String, MLuaKeybindDefinitions.Definition> pending = new TreeMap<>();
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs next = definitions.next(key);
         key = next.arg1();
         if (key.isnil()) break;
         LuaValue value = next.arg(2);
         if (!key.isstring()) {
            throw new LuaError("[MLua] RegisterKeybinds requires string keys for keybind definitions, got " + key.typename());
         }
         String name = MLuaKeybindDefinitions.requireName(key.tojstring(), "RegisterKeybinds");
         if (!value.istable()) {
            throw new LuaError("[MLua] RegisterKeybinds entry '" + name + "' must contain a keybind definition table.");
         }
         pending.put(name, MLuaKeybindDefinitions.parse(name, value.checktable(), "RegisterKeybinds"));
      }

      LuaTable result = new LuaTable();
      int index = 1;
      String owner = this.context.mlua().ownerModId();
      List<MLuaKeybindDefinitions.Definition> orderedDefinitions = new ArrayList<>(pending.values());
      List<MLuaKeybindController.KeybindDefinition> controllerDefinitions = orderedDefinitions.stream()
         .map(MLuaKeybindDefinitions::toControllerDefinition).toList();
      try {
         this.onClientThread(() -> {
            MLuaKeybindController.registerBatch(owner, controllerDefinitions);
            return null;
         });
      } catch (IllegalArgumentException | IllegalStateException failure) {
         throw MLuaLuaErrors.javaFailure("InputService:RegisterKeybinds", failure);
      }
      for (MLuaKeybindDefinitions.Definition definition : orderedDefinitions) {
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
      return this.onClientThread(() -> {
         String owner = this.context.mlua().ownerModId();
         if (MLuaKeybindController.hasLocalKeybind(owner, name)) return MLuaKeybindController.isDown(owner, name);
         KeyMapping mapping = MLuaKeybindController.minecraftKeybind(name);
         return mapping != null && MLuaKeybindController.isDown(mapping);
      });
   }

   @MLuaDescription("Sets a Minecraft or addon key mapping's logical pressed state for this addon on the client thread. Other addons can hold the same mapping independently; the press is released when this addon runtime unloads.")
   public void SetKeyDown(String name, boolean pressed) {
      this.requireClientSide(this.name());
      this.actions.setKeyDown(name, pressed);
   }

   @MLuaDescription("Validates a table of mapping names and pressed states before applying them as one owner-scoped batch. If applying a state fails, prior states are restored where possible.")
   public void SetKeyStates(LuaTable states) {
      this.requireClientSide(this.name());
      this.actions.setKeyStates(states);
   }

   @MLuaDescription("Releases every simulated key and mouse mapping held by the calling addon runtime.")
   public void ReleasePressedKeys() {
      this.requireClientSide(this.name());
      this.actions.releasePressedKeys();
   }

   @MLuaDescription("Queues one Minecraft key mapping click, consumed by the normal game input loop.")
   public void ClickKey(String name) {
      this.requireClientSide(this.name());
      this.actions.clickKey(name);
   }

   @MLuaDescription("Sets the pressed state of a Minecraft or other mod's key mapping by its registered identifier, bypassing this addon's keybind names.")
   public void SetMinecraftKeyDown(String name, boolean pressed) {
      this.requireClientSide(this.name());
      this.actions.setMinecraftKeyDown(name, pressed);
   }

   @MLuaDescription("Queues one click for a Minecraft or other mod's key mapping by its registered identifier, bypassing this addon's keybind names.")
   public void ClickMinecraftKey(String name) {
      this.requireClientSide(this.name());
      this.actions.clickMinecraftKey(name);
   }

   @MLuaDescription("Sets a standard Minecraft mouse action binding: 0 left attack, 1 right use, or 2 middle pick block.")
   public void SetMouseButtonDown(int button, boolean pressed) {
      this.requireClientSide(this.name());
      this.actions.setMouseButtonDown(button, pressed);
   }

   @MLuaDescription("Queues one standard Minecraft mouse action: 0 left attack, 1 right use, or 2 middle pick block.")
   public void ClickMouseButton(int button) {
      this.requireClientSide(this.name());
      this.actions.clickMouseButton(button);
   }

   @MLuaDescription("Changes the key assigned to a registered keybind.")
   public void SetKeybind(String name, LuaValue key) {
      this.requireClientSide(this.name());
      this.onClientThread(() -> {
         MLuaKeybindController.setKey(this.context.mlua().ownerModId(), name, key);
         return null;
      });
   }

   @MLuaDescription("Returns the live keybind record for a registered or Minecraft keybind.")
   public LuaValue GetKeybind(String name) {
      this.requireClientSide(this.name());
      return this.onClientThread(() -> {
         String owner = this.context.mlua().ownerModId();
         if (MLuaKeybindController.hasLocalKeybind(owner, name)) {
            return keybindTable(owner, name, this.context.mlua().scheduler(), this.context.mlua().side());
         }
         KeyMapping mapping = MLuaKeybindController.minecraftKeybind(name);
         return mapping == null ? LuaValue.NIL : mcKeybindTable(mapping);
      });
   }

   @MLuaDescription("Returns keybinds registered by the calling mod and available Minecraft keybinds.")
   public LuaTable GetKeybinds() {
      this.requireClientSide(this.name());
      return this.onClientThread(() -> {
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
      });
   }

   @MLuaDescription("Returns a Minecraft keybind by name, or nil when missing.")
   public LuaValue GetMinecraftKeybind(String name) {
      this.requireClientSide(this.name());
      return this.onClientThread(() -> {
         KeyMapping mapping = MLuaKeybindController.minecraftKeybind(name);
         return mapping == null ? LuaValue.NIL : mcKeybindTable(mapping);
      });
   }

   @MLuaDescription("Returns the available Minecraft keybinds.")
   public LuaTable GetMinecraftKeybinds() {
      this.requireClientSide(this.name());
      return this.onClientThread(() -> {
         LuaTable result = new LuaTable();
         int index = 1;
         for (KeyMapping mapping : MLuaKeybindController.minecraftKeybinds().stream()
               .sorted(java.util.Comparator.comparing(MLuaKeybindController::name)).toList()) {
            result.set(index++, mcKeybindTable(mapping));
         }
         return result;
      });
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
      return this.registerKeybind(ownerModId, MLuaKeybindDefinitions.parse(name, props, "RegisterKeybind"));
   }

   private LuaTable registerKeybind(String ownerModId, MLuaKeybindDefinitions.Definition definition) {
      this.onClientThread(() -> {
         MLuaKeybindController.registerBatch(ownerModId,
            List.of(MLuaKeybindDefinitions.toControllerDefinition(definition)));
         return null;
      });
      return keybindTable(ownerModId, definition.name(), this.context.mlua().scheduler(), this.context.mlua().side());
   }

   private <T> T onClientThread(java.util.function.Supplier<T> action) {
      return MLuaClientThread.callOnClientThread(action);
   }

   private LuaTable mcKeybindTable(KeyMapping mapping) {
      LuaTable table = new LuaTable();
      table.rawset("__key_mapping", LuaValue.userdataOf(mapping));
      return this.context.mlua().registration().attachClassInstance(table, "MinecraftKeybind", null);
   }


}
