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
        registration.registerClass("KeyMapping", registration.requireInstanceBaseClassName(),
            "A live view of a Minecraft key mapping, including its identifier and pressed state.");
        registration.registerClass("Keybind", "KeyMapping",
            "A live, addon-owned keybind with readable state, a change signal, and key reassignment.");
        registration.registerClass("MinecraftKeybind", "KeyMapping",
            "A live read-only view of a keybind registered by Minecraft or another mod.");

        registration.registerClassProperty("KeyMapping", "Name", mappingProperty("name"), LuaValue.NIL,
            "string", "client:Client", "The key mapping's registered identifier.");
        registration.registerClassProperty("KeyMapping", "IsDown", mappingProperty("down"), LuaValue.NIL,
            "boolean", "client:Client", "Whether this key mapping is currently pressed.");
        registration.registerClassProperty("KeyMapping", "Key", mappingProperty("key"), keyMappingKeySetter(),
            "string", "client:Client", "The current key; only addon-owned keybinds can be changed.");
        registration.registerClassProperty("KeyMapping", "Category", mappingProperty("category"), LuaValue.NIL,
            "string", "client:Client", "The Controls category containing this key mapping.");
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
    }

    private static TwoArgFunction keyMappingKeySetter() {
        return new TwoArgFunction() {
            @Override public LuaValue call(LuaValue self, LuaValue value) {
                LuaTable instance = self.checktable();
                LuaValue owner = instance.rawget("__keybind_owner");
                LuaValue name = instance.rawget("__keybind_name");
                if (!owner.isstring() || !name.isstring()) {
                    throw new org.luaj.vm2.LuaError("[MLua] Minecraft key mappings are read-only.");
                }
                dev.mlua.minecraft.client.MLuaKeybindController.setKey(
                    owner.tojstring(), name.tojstring(), value);
                return LuaValue.NONE;
            }
        };
    }

    private static OneArgFunction mappingProperty(String property) {
        return new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                LuaTable instance = self.checktable();
                LuaValue raw = instance.rawget("__key_mapping");
                KeyMapping mapping = raw.isuserdata(KeyMapping.class)
                    ? (KeyMapping) raw.touserdata(KeyMapping.class) : null;
                if (mapping == null) {
                    LuaValue owner = instance.rawget("__keybind_owner");
                    LuaValue name = instance.rawget("__keybind_name");
                    if (!owner.isstring() || !name.isstring()) return LuaValue.NIL;
                    return switch (property) {
                        case "name" -> name;
                        case "key" -> LuaValue.valueOf(dev.mlua.minecraft.client.MLuaKeybindController.keyName(
                            owner.tojstring(), name.tojstring()));
                        case "category" -> LuaValue.valueOf(dev.mlua.minecraft.client.MLuaKeybindController.category(
                            owner.tojstring(), name.tojstring()));
                        case "down" -> LuaValue.valueOf(dev.mlua.minecraft.client.MLuaKeybindController.isDown(
                            owner.tojstring(), name.tojstring()));
                        default -> LuaValue.NIL;
                    };
                }
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
            .reflective(MLuaInputService.class);
        return List.of(input.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.clazz("KeyMapping", "net.minecraft.client.KeyMapping", "A live view of a Minecraft key mapping, including its identifier and pressed state.", "classes/key-mapping.html", "Instance");
        docs.clazz("Keybind", "MLuaInputService", "A live, addon-owned keybind with readable state, a change signal, and key reassignment.", "classes/keybind.html", "KeyMapping");
        docs.clazz("MinecraftKeybind", "MLuaInputService", "A live read-only view of a keybind registered by Minecraft or another mod.", "classes/minecraft-keybind.html", "KeyMapping");
        docs.property("KeyMapping", "KeyMapping.Name", "string", "The key mapping's registered identifier.", "client:Client");
        docs.property("KeyMapping", "KeyMapping.IsDown", "boolean", "Whether this key mapping is currently pressed.", "client:Client");
        docs.property("KeyMapping", "KeyMapping.Key", "string", "The assigned key; only addon-owned keybinds can be changed.", "client:Client");
        docs.property("KeyMapping", "KeyMapping.Category", "string", "The Controls category containing this key mapping.", "client:Client");
        docs.property("Keybind", "Keybind.Signal", "Signal", "Fires when this keybind is pressed.", "client:Client");
        docs.method("Keybind", "Keybind:SetKey(key: string)", "Changes the key assigned to this addon keybind.", "key name", "void", "client:Client", null);
        docs.method("Keybind", "Keybind:Listen()", "Returns this keybind's change signal.", null, "Signal", "client:Client", null);
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
