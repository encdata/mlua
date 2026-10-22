package dev.mlua.modules.model.service;

import java.util.List;
import java.util.Map;

import dev.mlua.api.MLuaLuaErrors;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.service.MLuaService;
import dev.mlua.runtime.client.MLuaClientModelManager;
import dev.mlua.runtime.client.MLuaModelAdapterRegistry;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Owns Lua adapter registrations, queries, limits, and their addon-scoped handles. */
final class MLuaModelAdapterOperations {
    private final MLuaService.Context context;
    private final MLuaModelService service;

    MLuaModelAdapterOperations(MLuaService.Context context, MLuaModelService service) {
        this.context = context;
        this.service = service;
    }

    String resolvePartName(String name) {
        return MLuaClientModelManager.normalizePartName(name);
    }

    void registerAdapter(String modelClass, LuaTable parts) {
        this.service.requireModelClient();
        java.util.LinkedHashMap<String, String> mapping = new java.util.LinkedHashMap<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = parts.next(key);
            key = next.arg1();
            if (key.isnil()) break;
            LuaValue path = next.arg(2);
            if (key.type() != LuaValue.TSTRING || path.type() != LuaValue.TSTRING) {
                throw new LuaError("[MLua] RegisterAdapter expects a table of string bone names to string model part paths.");
            }
            if (mapping.size() >= MLuaModelAdapterRegistry.MAX_PARTS_PER_MODEL) {
                throw new LuaError("[MLua] RegisterAdapter accepts at most "
                    + MLuaModelAdapterRegistry.MAX_PARTS_PER_MODEL + " bone mappings at a time.");
            }
            mapping.put(key.tojstring(), path.tojstring());
        }
        try {
            MLuaModelAdapterRegistry.register(modelClass, mapping, this.adapterOwner());
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    void registerAdapterBone(String modelClass, String boneName, String partPath) {
        this.service.requireModelClient();
        try {
            MLuaModelAdapterRegistry.registerBone(modelClass, boneName, partPath, this.adapterOwner());
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    LuaTable registerAdapterResolver(String modelClass, LuaValue resolver) {
        this.service.requireModelClient();
        dev.mlua.core.MLuaContext callbackContext = MLuaLuaBindings.boundContext(this.context.mlua().globals());
        if (callbackContext == null) {
            throw new LuaError("[MLua] RegisterAdapterResolver must be called while a Lua addon is executing.");
        }
        try {
            return MLuaLuaBindings.connection(MLuaModelAdapterRegistry.registerResolver(
                modelClass, resolver, callbackContext, this.adapterOwner()), this.context.registration());
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    LuaTable registerCustomAdapter(String modelClass, LuaTable definition) {
        this.service.requireModelClient();
        if (definition == null) throw new LuaError("[MLua] RegisterCustomAdapter expects a definition table.");
        LuaValue bonesValue = definition.get("Bones");
        if (!bonesValue.istable()) throw new LuaError("[MLua] RegisterCustomAdapter requires Bones as an array of names.");
        LuaTable bonesTable = bonesValue.checktable();
        int count = bonesTable.rawlen();
        if (count < 1 || count > MLuaModelAdapterRegistry.MAX_PARTS_PER_MODEL) {
            throw new LuaError("[MLua] Custom adapter Bones must contain between 1 and "
                + MLuaModelAdapterRegistry.MAX_PARTS_PER_MODEL + " names.");
        }
        int entries = 0;
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = bonesTable.next(key);
            key = next.arg1();
            if (key.isnil()) break;
            if (!key.isint() || key.toint() < 1 || key.toint() > count) {
                throw new LuaError("[MLua] Custom adapter Bones must be a dense array with no extra keys.");
            }
            entries++;
        }
        if (entries != count) {
            throw new LuaError("[MLua] Custom adapter Bones must be a dense array with no missing entries.");
        }
        java.util.ArrayList<String> bones = new java.util.ArrayList<>(count);
        for (int index = 1; index <= count; index++) {
            LuaValue value = bonesTable.rawget(index);
            if (value.type() != LuaValue.TSTRING) {
                throw new LuaError("[MLua] Custom adapter Bones must be a dense array of strings.");
            }
            bones.add(value.tojstring());
        }
        LuaValue apply = definition.get("Apply");
        LuaValue applyPose = definition.get("ApplyPose");
        if (!apply.isfunction()) {
            throw new LuaError("[MLua] Custom adapter definition requires an Apply function.");
        }
        if (!applyPose.isnil() && !applyPose.isfunction()) {
            throw new LuaError("[MLua] Custom adapter ApplyPose must be a function when provided.");
        }
        dev.mlua.core.MLuaContext callbackContext = MLuaLuaBindings.boundContext(this.context.mlua().globals());
        if (callbackContext == null) {
            throw new LuaError("[MLua] RegisterCustomAdapter must be called while a Lua addon is executing.");
        }
        try {
            return MLuaLuaBindings.connection(MLuaModelAdapterRegistry.registerCustomAdapter(
                modelClass, bones, apply, applyPose, callbackContext, this.adapterOwner()), this.context.registration());
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    boolean unregisterCustomAdapter(String modelClass) {
        this.service.requireModelClient();
        try {
            return MLuaModelAdapterRegistry.unregisterCustomAdapter(modelClass, this.adapterOwner());
        } catch (IllegalArgumentException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    boolean unregisterAdapterResolver(String modelClass) {
        this.service.requireModelClient();
        try {
            return MLuaModelAdapterRegistry.unregisterResolver(modelClass, this.adapterOwner());
        } catch (IllegalArgumentException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    boolean unregisterAdapter(String modelClass) {
        this.service.requireModelClient();
        try {
            return MLuaModelAdapterRegistry.unregister(modelClass, this.adapterOwner());
        } catch (IllegalArgumentException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    boolean unregisterAdapterBone(String modelClass, String boneName) {
        this.service.requireModelClient();
        try {
            return MLuaModelAdapterRegistry.unregisterBone(modelClass, boneName, this.adapterOwner());
        } catch (IllegalArgumentException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    void registerRendererAdapter(String rendererClass, String modelFieldPath) {
        this.service.requireModelClient();
        try {
            MLuaModelAdapterRegistry.registerRendererAdapter(rendererClass, modelFieldPath, this.adapterOwner());
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    boolean unregisterRendererAdapter(String rendererClass) {
        this.service.requireModelClient();
        try {
            return MLuaModelAdapterRegistry.unregisterRendererAdapter(rendererClass, this.adapterOwner());
        } catch (IllegalArgumentException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    LuaValue getRendererAdapter(String rendererClass) {
        this.service.requireModelClient();
        try {
            String path = MLuaModelAdapterRegistry.registeredRendererModelField(rendererClass);
            return path == null ? LuaValue.NIL : LuaValue.valueOf(path);
        } catch (IllegalArgumentException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    LuaValue getAdapter(String modelClass) {
        this.service.requireModelClient();
        try {
            Map<String, String> mappings = MLuaModelAdapterRegistry.registeredMappings(modelClass);
            if (mappings.isEmpty()) return LuaValue.NIL;
            LuaTable result = new LuaTable();
            mappings.forEach((bone, path) -> result.set(bone, LuaValue.valueOf(path)));
            return result;
        } catch (IllegalArgumentException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    LuaValue getCustomAdapter(String modelClass) {
        this.service.requireModelClient();
        try {
            List<String> bones = MLuaModelAdapterRegistry.registeredCustomBones(modelClass);
            if (bones.isEmpty()) return LuaValue.NIL;
            LuaTable result = new LuaTable();
            for (int index = 0; index < bones.size(); index++) {
                result.set(index + 1, LuaValue.valueOf(bones.get(index)));
            }
            return result;
        } catch (IllegalArgumentException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    LuaValue getAppliedAdapter(LuaValue entity) {
        this.service.requireModelClient();
        MLuaModelAdapterRegistry.AdapterSnapshot snapshot =
            MLuaModelAdapterRegistry.appliedAdapter(this.service.resolveEntityScope(entity));
        if (snapshot == null) return LuaValue.NIL;
        LuaTable result = new LuaTable();
        result.set("ModelClass", LuaValue.valueOf(snapshot.modelClass()));
        LuaTable mappings = new LuaTable();
        snapshot.mappings().forEach((bone, path) -> mappings.set(bone, LuaValue.valueOf(path)));
        result.set("Mappings", mappings);
        LuaTable resolvedBones = new LuaTable();
        int index = 1;
        for (String bone : snapshot.resolvedBones()) resolvedBones.set(index++, LuaValue.valueOf(bone));
        result.set("ResolvedBones", resolvedBones);
        LuaTable customBones = new LuaTable();
        int customIndex = 1;
        for (String bone : snapshot.customBones()) customBones.set(customIndex++, LuaValue.valueOf(bone));
        result.set("CustomBones", customBones);
        return result;
    }

    LuaTable getAdapterLimits() {
        this.service.requireModelClient();
        MLuaModelAdapterRegistry.Owner owner = this.adapterOwner();
        LuaTable limits = new LuaTable();
        limits.set("ActiveModels", MLuaModelAdapterRegistry.registeredModelClassCount());
        limits.set("ActiveParts", MLuaModelAdapterRegistry.registeredPartCount());
        limits.set("AddonParts", MLuaModelAdapterRegistry.registeredPartsForOwner(owner));
        limits.set("ActiveRenderers", MLuaModelAdapterRegistry.registeredRendererClassCount());
        limits.set("ActiveRendererAdapters", MLuaModelAdapterRegistry.registeredRendererAdapterCount());
        limits.set("AddonRendererAdapters", MLuaModelAdapterRegistry.registeredRendererAdapterCount(owner));
        limits.set("ActiveResolvers", MLuaModelAdapterRegistry.registeredResolverClassCount());
        limits.set("ActiveCustomAdapters", MLuaModelAdapterRegistry.registeredCustomAdapterClassCount());
        limits.set("AddonCustomAdapters", MLuaModelAdapterRegistry.registeredCustomAdapterCount(owner));
        limits.set("ActiveCustomBones", MLuaModelAdapterRegistry.registeredCustomPartCount());
        limits.set("AddonResolvers", MLuaModelAdapterRegistry.registeredResolverCount(owner));
        limits.set("MaxModels", MLuaModelAdapterRegistry.MAX_MODEL_CLASSES);
        limits.set("MaxRenderers", MLuaModelAdapterRegistry.MAX_RENDERER_CLASSES);
        limits.set("MaxRendererAdapters", MLuaModelAdapterRegistry.MAX_RENDERER_ADAPTERS);
        limits.set("MaxRendererAdaptersPerAddon", MLuaModelAdapterRegistry.MAX_RENDERER_ADAPTERS_PER_OWNER);
        limits.set("MaxResolvers", MLuaModelAdapterRegistry.MAX_RESOLVER_CLASSES);
        limits.set("MaxResolversPerAddon", MLuaModelAdapterRegistry.MAX_RESOLVERS_PER_OWNER);
        limits.set("MaxCustomAdapters", MLuaModelAdapterRegistry.MAX_CUSTOM_ADAPTER_CLASSES);
        limits.set("MaxCustomAdaptersPerAddon", MLuaModelAdapterRegistry.MAX_CUSTOM_ADAPTERS_PER_OWNER);
        limits.set("MaxPartsPerModel", MLuaModelAdapterRegistry.MAX_PARTS_PER_MODEL);
        limits.set("MaxParts", MLuaModelAdapterRegistry.MAX_TOTAL_PARTS);
        limits.set("MaxPartsPerAddon", MLuaModelAdapterRegistry.MAX_PARTS_PER_OWNER);
        limits.set("MaxResolverInstructions", MLuaModelAdapterRegistry.MAX_RESOLVER_LUA_INSTRUCTIONS);
        limits.set("MaxClassNameLength", MLuaModelAdapterRegistry.MAX_CLASS_NAME_LENGTH);
        limits.set("MaxBoneNameLength", MLuaModelAdapterRegistry.MAX_BONE_NAME_LENGTH);
        limits.set("MaxPathLength", MLuaModelAdapterRegistry.MAX_PART_PATH_LENGTH);
        limits.set("MaxPathDepth", MLuaModelAdapterRegistry.MAX_PART_PATH_DEPTH);
        return limits;
    }

    private MLuaModelAdapterRegistry.Owner adapterOwner() {
        MLuaContext active = MLuaLuaBindings.boundContext(this.context.mlua().globals());
        if (active != null) {
            return new MLuaModelAdapterRegistry.Owner(active.session(), active.ownerModId());
        }
        Object runtimeOwner = this.context.registration() instanceof MLuaRuntime runtime ? runtime.session() : null;
        return new MLuaModelAdapterRegistry.Owner(runtimeOwner, this.context.ownerModId());
    }
}
