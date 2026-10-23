package dev.mlua.modules.input.service;

import dev.mlua.api.MLuaLuaErrors;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.core.service.MLuaService;
import dev.mlua.minecraft.client.MLuaKeybindController;
import dev.mlua.runtime.client.MLuaClientThread;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Owns owner-scoped key state mutation and Minecraft mapping actions for InputService. */
final class MLuaInputActions {
    private final MLuaService.Context context;

    MLuaInputActions(MLuaService.Context context) {
        this.context = context;
    }

    void setKeyDown(String name, boolean pressed) {
        requireClientThread("SetKeyDown");
        onClientThread(() -> {
            String owner = this.context.mlua().ownerModId();
            KeyMapping mapping = resolveMapping(owner, name);
            if (mapping == null) throw new LuaError("[MLua] InputService:SetKeyDown could not find key mapping '" + name + "'.");
            MLuaKeybindController.setDown(mapping, pressed, (MLuaRuntimeSession) this.context.mlua().session(), owner);
            return null;
        });
    }

    void setKeyStates(LuaTable states) {
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
            String name = MLuaKeybindDefinitions.requireName(key.checkjstring(), "SetKeyStates");
            if (!value.isboolean()) {
                throw new LuaError("[MLua] InputService:SetKeyStates value for '" + name + "' must be true or false.");
            }
            requested.put(name, value.toboolean());
        }

        onClientThread(() -> {
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
                MLuaKeybindController.setDownBatch(batch, (MLuaRuntimeSession) this.context.mlua().session(), owner);
            } catch (IllegalArgumentException | IllegalStateException failure) {
                throw MLuaLuaErrors.javaFailure("InputService:SetKeyStates", failure);
            }
            return null;
        });
    }

    void releasePressedKeys() {
        requireClientThread("ReleasePressedKeys");
        onClientThread(() -> {
            MLuaKeybindController.releaseOwnerPressedMappings((MLuaRuntimeSession) this.context.mlua().session(),
                this.context.mlua().ownerModId());
            return null;
        });
    }

    void clickKey(String name) {
        requireClientThread("ClickKey");
        onClientThread(() -> {
            KeyMapping mapping = resolveMapping(this.context.mlua().ownerModId(), name);
            if (mapping == null) throw new LuaError("[MLua] InputService:ClickKey could not find key mapping '" + name + "'.");
            MLuaKeybindController.click(mapping);
            return null;
        });
    }

    void setMinecraftKeyDown(String name, boolean pressed) {
        requireClientThread("SetMinecraftKeyDown");
        onClientThread(() -> {
            KeyMapping mapping = requireMinecraftKeyMapping(name, "SetMinecraftKeyDown");
            MLuaKeybindController.setDown(mapping, pressed, (MLuaRuntimeSession) this.context.mlua().session(),
                this.context.mlua().ownerModId());
            return null;
        });
    }

    void clickMinecraftKey(String name) {
        requireClientThread("ClickMinecraftKey");
        onClientThread(() -> {
            MLuaKeybindController.click(requireMinecraftKeyMapping(name, "ClickMinecraftKey"));
            return null;
        });
    }

    void setMouseButtonDown(int button, boolean pressed) {
        this.setMinecraftKeyDown(mouseBinding(button), pressed);
    }

    void clickMouseButton(int button) {
        this.clickMinecraftKey(mouseBinding(button));
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

    private static <T> T onClientThread(Supplier<T> action) {
        return MLuaClientThread.callOnClientThread(action);
    }

    private static void requireClientThread(String operation) {
        if (!dev.mlua.runtime.client.MLuaClientThread.isClientExecutionContext()) {
            throw new LuaError("[MLua] InputService:" + operation + " must run on Minecraft's client thread.");
        }
    }
}
