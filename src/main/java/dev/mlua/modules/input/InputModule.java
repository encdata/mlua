package dev.mlua.modules.input;

import dev.mlua.modules.input.Service.MLuaInputService;
import dev.mlua.common.enums.Side;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import java.util.List;
import net.minecraft.client.KeyMapping;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

@dev.mlua.module.MLuaModuleSide(Side.CLIENT)
public class InputModule implements MLuaModule {
    @Override public Side side() { return Side.CLIENT; }

    @Override
    public void clearRuntimeState(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side) {
        dev.mlua.minecraft.client.MLuaKeybindController.clearSession(
            (dev.mlua.core.MLuaRuntimeSession) runtimeSession, side);
    }

    @Override
    public void onRuntimeTick(dev.mlua.module.MLuaRegistration runtime, Object runtimeSession, Side side, double deltaTime) {
        dev.mlua.minecraft.client.MLuaKeybindController.tick(
            ((dev.mlua.core.MLuaRuntimeSession) runtimeSession).clientScheduler());
    }
    @Override
    public String id() { return "input"; }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        registration.registerClass("Keybind", registration.requireInstanceBaseClassName(),
            "A live, addon-owned keybind with readable state, a change signal, and key reassignment.");
        registration.registerClass("MinecraftKeybind", registration.requireInstanceBaseClassName(),
            "A live read-only view of a keybind registered by Minecraft or another mod.");

        registration.registerClassProperty("Keybind", "Name", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return self.checktable().rawget("__keybind_name"); }
        }, LuaValue.NIL, "string", "client:Client", "The addon keybind's registered name.");
        registration.registerClassProperty("Keybind", "Key", keybindKeyGetter(), keybindKeySetter(),
            "string", "client:Client", "The current key assigned to this addon keybind.");
        registration.registerClassProperty("Keybind", "IsDown", new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                LuaTable instance = self.checktable();
                return LuaValue.valueOf(dev.mlua.minecraft.client.MLuaKeybindController.isDown(
                    instance.rawget("__keybind_owner").tojstring(), instance.rawget("__keybind_name").tojstring()));
            }
        }, LuaValue.NIL, "boolean", "client:Client", "Whether this keybind is currently pressed.");
        registration.registerClassFunction("Keybind", "SetKey", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable self = args.arg1().checktable();
                dev.mlua.minecraft.client.MLuaKeybindController.setKey(
                    self.rawget("__keybind_owner").tojstring(), self.rawget("__keybind_name").tojstring(), args.arg(2));
                return LuaValue.NONE;
            }
        }, "Changes the key assigned to this addon keybind.");
        registration.registerClassFunction("Keybind", "Listen", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return args.arg1().checktable().get("Signal"); }
        }, "Returns this keybind's change signal.");

        registration.registerClassProperty("MinecraftKeybind", "Name", mappingProperty("name"), LuaValue.NIL,
            "string", "client:Client", "The Minecraft keybind identifier.");
        registration.registerClassProperty("MinecraftKeybind", "Key", mappingProperty("key"), LuaValue.NIL,
            "string", "client:Client", "The current key assigned to this Minecraft keybind.");
        registration.registerClassProperty("MinecraftKeybind", "Category", mappingProperty("category"), LuaValue.NIL,
            "string", "client:Client", "The controls category containing this keybind.");
        registration.registerClassProperty("MinecraftKeybind", "IsDown", mappingProperty("down"), LuaValue.NIL,
            "boolean", "client:Client", "Whether this keybind is currently pressed.");
    }

    private static OneArgFunction keybindKeyGetter() {
        return new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                LuaTable instance = self.checktable();
                return LuaValue.valueOf(dev.mlua.minecraft.client.MLuaKeybindController.keyName(
                    instance.rawget("__keybind_owner").tojstring(), instance.rawget("__keybind_name").tojstring()));
            }
        };
    }

    private static TwoArgFunction keybindKeySetter() {
        return new TwoArgFunction() {
            @Override public LuaValue call(LuaValue self, LuaValue value) {
                LuaTable instance = self.checktable();
                dev.mlua.minecraft.client.MLuaKeybindController.setKey(
                    instance.rawget("__keybind_owner").tojstring(), instance.rawget("__keybind_name").tojstring(), value);
                return LuaValue.NONE;
            }
        };
    }

    private static OneArgFunction mappingProperty(String property) {
        return new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                LuaValue raw = self.checktable().rawget("__key_mapping");
                if (!raw.isuserdata(KeyMapping.class)) return LuaValue.NIL;
                KeyMapping mapping = (KeyMapping) raw.touserdata(KeyMapping.class);
                return switch (property) {
                    case "name" -> LuaValue.valueOf(dev.mlua.minecraft.client.MLuaKeybindController.name(mapping));
                    case "key" -> LuaValue.valueOf(dev.mlua.minecraft.client.MLuaKeybindController.key(mapping).toString());
                    case "category" -> LuaValue.valueOf(dev.mlua.minecraft.client.MLuaKeybindController.category(mapping));
                    case "down" -> LuaValue.valueOf(dev.mlua.minecraft.client.MLuaKeybindController.isDown(mapping));
                    default -> LuaValue.NIL;
                };
            }
        };
    }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder input = ServiceEntry.builder("InputService", MLuaInputService::new)
            .description("Provides client input state and input change events.");
        input.function("RegisterKeybind", "Registers one keybind for the calling mod.")
            .function("RegisterKeybinds", "Registers several keybinds from a keyed definition table.")
            .function("ListenForKeybind", "Returns the change signal for a keybind registered by this mod.")
            .function("IsKeyDown", "Returns whether a registered or Minecraft keybind is currently pressed.")
            .function("SetKeybind", "Changes the key assigned to a registered keybind.")
            .function("GetKeybind", "Returns the live keybind record for a registered or Minecraft keybind.")
            .function("GetKeybinds", "Returns keybinds registered by the calling mod.")
            .function("GetMinecraftKeybind", "Returns a Minecraft keybind by name, or nil when missing.")
            .function("GetMinecraftKeybinds", "Returns the available Minecraft keybinds.");
        return List.of(input.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.clazz("Keybind", "MLuaInputService", "A live, addon-owned keybind with readable state, a change signal, and key reassignment.", "classes/keybind.html", "Instance");
        docs.clazz("MinecraftKeybind", "MLuaInputService", "A live read-only view of a keybind registered by Minecraft or another mod.", "classes/minecraft-keybind.html", "Instance");
        docs.property("Keybind", "Keybind.Name", "string", "The addon keybind's registered name.", "client:Client");
        docs.property("Keybind", "Keybind.Key", "string", "The current key assigned to this addon keybind.", "client:Client");
        docs.property("Keybind", "Keybind.IsDown", "boolean", "Whether this keybind is currently pressed.", "client:Client");
        docs.property("Keybind", "Keybind.Signal", "Signal", "Fires when this keybind is pressed.", "client:Client");
        docs.method("Keybind", "Keybind:SetKey(key: string)", "Changes the key assigned to this addon keybind.", "key name", "void", "client:Client", null);
        docs.method("Keybind", "Keybind:Listen()", "Returns this keybind's change signal.", null, "Signal", "client:Client", null);
        docs.property("MinecraftKeybind", "MinecraftKeybind.Name", "string", "The Minecraft keybind identifier.", "client:Client");
        docs.property("MinecraftKeybind", "MinecraftKeybind.Key", "string", "The current assigned key.", "client:Client");
        docs.property("MinecraftKeybind", "MinecraftKeybind.Category", "string", "The controls category containing this keybind.", "client:Client");
        docs.property("MinecraftKeybind", "MinecraftKeybind.IsDown", "boolean", "Whether this keybind is currently pressed.", "client:Client");
        docs.signature("InputService", "InputService:RegisterKeybind(name: string, props: KeybindDefinition)", "Keybind");
        docs.signature("InputService", "InputService:RegisterKeybinds(definitions: KeybindDefinition[])", "Keybind[]");
        docs.signature("InputService", "InputService:SetKeybind(name: string, key: string)", null);
        docs.signature("InputService", "InputService:GetKeybind(name: string)", "Keybind|MinecraftKeybind|nil");
        docs.signature("InputService", "InputService:GetKeybinds()", "(Keybind|MinecraftKeybind)[]");
        docs.signature("InputService", "InputService:GetMinecraftKeybind(name: string)", "MinecraftKeybind|nil");
        docs.signature("InputService", "InputService:GetMinecraftKeybinds()", "MinecraftKeybind[]");
        docs.signature("InputService", "InputService:IsKeyDown(name: string)", null);
        docs.signature("InputService", "InputService:ListenForKeybind(name: string)", "Signal");
        docs.signature("Keybind", "Keybind:SetKey(key: string)", "void");
        docs.signature("Keybind", "Keybind:Listen()", "Signal");

        docs.service("InputService", "MLuaInputService",
            "Provides client input state and input change events.", "services/input.html");
        docs.type("KeybindDefinition", null, "Options for registering one keybind.");
        docs.field("KeybindDefinition", "label", "string", "Visible name shown in Minecraft's Controls screen.");
        docs.field("KeybindDefinition", "category?", "string", "Optional Controls screen category.");
        docs.field("KeybindDefinition", "defaultKey?", "string", "Optional default key name, such as KEY_R.");
    }
}
