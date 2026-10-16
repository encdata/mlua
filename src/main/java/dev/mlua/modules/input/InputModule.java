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
        docs.register(MLuaDocBuilder.Registration.clazz("KeyMapping", "net.minecraft.client.KeyMapping", "A live view of a Minecraft key mapping, including its identifier and pressed state.", "classes/key-mapping.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.clazz("Keybind", "MLuaInputService", "A live, addon-owned keybind with readable state, a change signal, and key reassignment.", "classes/keybind.html", "KeyMapping"));
        docs.register(MLuaDocBuilder.Registration.clazz("MinecraftKeybind", "MLuaInputService", "A live read-only view of a keybind registered by Minecraft or another mod.", "classes/minecraft-keybind.html", "KeyMapping"));
        docs.register(MLuaDocBuilder.Registration.property("KeyMapping", "KeyMapping.Name", "string", "The key mapping's registered identifier.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("KeyMapping", "KeyMapping.IsDown", "boolean", "Whether this key mapping is currently pressed.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("KeyMapping", "KeyMapping.Key", "string", "The assigned key; only addon-owned keybinds can be changed.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("KeyMapping", "KeyMapping.Category", "string", "The Controls category containing this key mapping.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.property("Keybind", "Keybind.Signal", "Signal", "Fires when this keybind is pressed.", "client:Client"));
        docs.register(MLuaDocBuilder.Registration.method("Keybind", "Keybind:SetKey(key: string|number|KeyDefinition)", "Changes the key assigned to this addon keybind.", "key name, numeric code, or key definition", "void", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.method("Keybind", "Keybind:Listen()", "Returns this keybind's change signal.", null, "Signal", "client:Client", null));
        docs.register(MLuaDocBuilder.Registration.signature("InputService", "InputService:RegisterKeybind(name: string, props: KeybindDefinition)", "Keybind"));
        docs.register(MLuaDocBuilder.Registration.signature("InputService", "InputService:RegisterKeybinds(definitions: { [name: string]: KeybindDefinition })", "Keybind[]"));
        docs.register(MLuaDocBuilder.Registration.signature("InputService", "InputService:SetKeybind(name: string, key: string|number|KeyDefinition)", null));
        docs.register(MLuaDocBuilder.Registration.signature("InputService", "InputService:GetKeybind(name: string)", "Keybind|MinecraftKeybind|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("InputService", "InputService:GetKeybinds()", "(Keybind|MinecraftKeybind)[]"));
        docs.register(MLuaDocBuilder.Registration.signature("InputService", "InputService:GetMinecraftKeybind(name: string)", "MinecraftKeybind|nil"));
        docs.register(MLuaDocBuilder.Registration.signature("InputService", "InputService:GetMinecraftKeybinds()", "MinecraftKeybind[]"));
        docs.register(MLuaDocBuilder.Registration.signature("InputService", "InputService:IsKeyDown(name: string)", null));
        docs.register(MLuaDocBuilder.Registration.signature("InputService", "InputService:ListenForKeybind(name: string)", "Signal"));
        docs.register(MLuaDocBuilder.Registration.signature("Keybind", "Keybind:SetKey(key: string|number|KeyDefinition)", "void"));
        docs.register(MLuaDocBuilder.Registration.signature("Keybind", "Keybind:Listen()", "Signal"));

        docs.register(MLuaDocBuilder.Registration.service("InputService", "MLuaInputService",
            "Provides client input state and input change events.", "services/input.html"));
        docs.register(MLuaDocBuilder.Registration.type("KeybindDefinition", null, "Options for registering one keybind. Use one casing per option. Batch registration validates every named definition before creating any keybinds."));
        docs.register(MLuaDocBuilder.Registration.field("KeybindDefinition", "label", "string", "Visible name shown in Minecraft's Controls screen."));
        docs.register(MLuaDocBuilder.Registration.field("KeybindDefinition", "category?", "string", "Optional Controls screen category."));
        docs.register(MLuaDocBuilder.Registration.field("KeybindDefinition", "defaultKey?", "string|number|KeyDefinition", "Optional key name, numeric key code, or a table with Name or Value."));
        docs.register(MLuaDocBuilder.Registration.type("KeyDefinition", null, "A key description table with a Name or Value field."));
        docs.register(MLuaDocBuilder.Registration.field("KeyDefinition", "Name?", "string", "Key name, such as KEY_R."));
        docs.register(MLuaDocBuilder.Registration.field("KeyDefinition", "Value?", "string|number", "Key name or numeric key code."));
    }
}
