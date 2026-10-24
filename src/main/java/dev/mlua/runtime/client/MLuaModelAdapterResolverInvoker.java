package dev.mlua.runtime.client;

import dev.mlua.core.MLuaContext;
import dev.mlua.core.engine.MLuaLuaBindings;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaInstructionBudget;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Runs an addon model resolver in a bounded, frame-scoped Lua context. */
final class MLuaModelAdapterResolverInvoker {
    private MLuaModelAdapterResolverInvoker() { }

    static Map<String, String> invoke(Object model, MLuaModelAdapterRegistry.AdapterResolver resolver) {
        if (!resolver.active()) return Map.of();
        MLuaRenderFrameScope scope = new MLuaRenderFrameScope();
        MLuaContext context = resolver.context();
        try {
            LuaValue modelValue = LuaValue.userdataOf(scope.view().scopedReference(model));
            final LuaValue[] result = {LuaValue.NIL};
            try (MLuaRenderFrameScope.ScopeBinding ignored = scope.view().enter()) {
                MLuaLuaBindings.withContext(context, () -> {
                    if (!resolver.active()) return null;
                    if (context.globals() == null) {
                        result[0] = resolver.callback().call(modelValue);
                    } else {
                        try (LuaInstructionBudget.Scope budget = context.globals()
                                .beginInstructionBudget(MLuaModelAdapterRegistry.MAX_RESOLVER_LUA_INSTRUCTIONS)) {
                            result[0] = resolver.callback().call(modelValue);
                        }
                    }
                    return null;
                });
            }
            if (!resolver.active()) return Map.of();
            if (!result[0].istable()) {
                throw new LuaError("[MLua] Model adapter resolver for " + model.getClass().getName()
                    + " must return a table of bone names to model-part paths.");
            }
            LuaTable table = result[0].checktable();
            if (table.keyCount() > MLuaModelAdapterRegistry.MAX_PARTS_PER_MODEL) {
                throw new LuaError("[MLua] Model adapter resolver returned more than "
                    + MLuaModelAdapterRegistry.MAX_PARTS_PER_MODEL + " bone mappings.");
            }

            LinkedHashMap<String, String> mappings = new LinkedHashMap<>();
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs next = table.next(key);
                key = next.arg1();
                if (key.isnil()) break;
                LuaValue value = next.arg(2);
                if (!key.isstring() || !value.isstring()) {
                    throw new LuaError("[MLua] Model adapter resolver must return string bone names and paths.");
                }
                String bone = key.tojstring().trim();
                String path = value.tojstring().trim();
                if (bone.isEmpty() || path.isEmpty()) {
                    throw new LuaError("[MLua] Model adapter resolver returned an empty bone name or path.");
                }
                MLuaModelAdapterRegistry.validatePartPath(bone, path);
                mappings.put(bone, path);
            }
            return MLuaModelAdapterRegistry.orderedMap(mappings);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            MLuaModelAdapterRegistry.disableResolver(resolver, failure);
            return Map.of();
        } finally {
            scope.close();
        }
    }
}
