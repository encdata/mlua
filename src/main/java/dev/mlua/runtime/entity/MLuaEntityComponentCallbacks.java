package dev.mlua.runtime.entity;

import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.engine.MLuaLuaBindings;
import net.minecraft.world.entity.Entity;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Executes entity-component callbacks and isolates failures from the component store. */
final class MLuaEntityComponentCallbacks {
    private static final int MAX_ERROR_LENGTH = 512;

    interface Component {
        String name();
        MLuaContext context();
        LuaTable callbacks();
        Entity entityForCallback();
        LuaTable handle();
        LuaTable state();
        boolean removed();
        boolean failed();
        void disable(String errorMessage);
    }

    private MLuaEntityComponentCallbacks() { }

    static void invoke(Component component, String callbackName, String eventName, LuaTable event) {
        boolean removing = "OnRemoved".equals(callbackName);
        if (!removing && (component.removed() || component.failed())) return;
        try {
            if (component.context() == null) {
                synchronized (component) {
                    if (!removing && (component.removed() || component.failed())) return;
                    invokeBody(component, callbackName, eventName, event);
                }
            } else {
                MLuaLuaBindings.withContext(component.context(), () -> {
                    if (!removing && (component.removed() || component.failed())) return null;
                    invokeBody(component, callbackName, eventName, event);
                    return null;
                });
            }
        } catch (LuaError error) {
            disableAfterFailure(component, callbackName, eventName, error);
        } catch (Error fatal) {
            throw fatal;
        } catch (Throwable error) {
            disableAfterFailure(component, callbackName, eventName, error);
        }
    }

    private static void invokeBody(Component component, String callbackName, String eventName, LuaTable event) {
        LuaValue function = callback(component.callbacks(), callbackName);
        if (!function.isfunction()) return;
        LuaTable payload = copy(event);
        payload.set("Event", LuaValue.valueOf(eventName));
        Entity entity = component.entityForCallback();
        payload.set("Entity", entity == null ? LuaValue.NIL : MLuaEntityComponentHandle.entityTable(entity));
        payload.set("Component", component.handle());
        payload.set("State", component.state());
        MLuaCallbackBudget.run(component.context(), () -> {
            function.call(payload);
            return null;
        });
    }

    private static void disableAfterFailure(Component component, String callbackName,
                                            String eventName, Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
        if (message.length() > MAX_ERROR_LENGTH) message = message.substring(0, MAX_ERROR_LENGTH - 1) + "…";
        component.disable(message);
        MLuaLogger.LOGGER.error("[MLua] Disabled entity component '{}' after its {} callback failed during {}",
            component.name(), callbackName, eventName, failure);
    }

    private static LuaValue callback(LuaTable table, String name) {
        LuaValue value = table.get(name);
        if (!value.isnil()) return value;
        return table.get(Character.toLowerCase(name.charAt(0)) + name.substring(1));
    }

    private static LuaTable copy(LuaTable source) {
        LuaTable copy = new LuaTable();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = source.next(key);
            key = next.arg1();
            if (key.isnil()) return copy;
            copy.set(key, next.arg(2));
        }
    }
}
