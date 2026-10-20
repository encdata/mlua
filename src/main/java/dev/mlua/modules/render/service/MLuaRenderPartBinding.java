package dev.mlua.modules.render.service;

import dev.mlua.core.MLuaLogger;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.runtime.client.MLuaClientRenderManager;
import dev.mlua.runtime.client.MLuaRenderOwnershipRegistry;
import dev.mlua.runtime.client.model.MLuaBBModelAsset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Owns the Lua class and handles for bones on an addon-owned Blockbench model. */
final class MLuaRenderPartBinding {
    private static final String HANDLE_KEY = "__mlua_render_model_part";
    private record MethodBinding(LuaValue function, String description) { }
    private static final Map<String, MethodBinding> METHODS = modelPartMethods();

    private MLuaRenderPartBinding() { }

    static void registerClass(MLuaRegistration registration) {
        registration.registerClass("RenderPart", registration.requireInstanceBaseClassName(),
            "A live handle to one bone in an owned Blockbench RenderObject. Part transforms are local to the bone and compose with its animation and parent transforms.");
        registration.registerClassFunction("RenderPart", "new", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                throw new LuaError("[MLua] RenderPart instances come from RenderObject:GetModelPart, GetModelPartById, or GetModelParts; they cannot be constructed directly.");
            }
        }, "RenderPart handles are created by RenderObject model-part lookup methods.");
        for (Map.Entry<String, MethodBinding> method : METHODS.entrySet()) {
            MethodBinding binding = method.getValue();
            registration.registerClassFunction("RenderPart", method.getKey(), binding.function(), binding.description());
        }
        registerProperty(registration, "Name", "GetName", "string", "The Blockbench bone name.");
        registerProperty(registration, "Id", "GetId", "string", "The stable Blockbench bone UUID; use it when names are duplicated.");
        registerProperty(registration, "Valid", "IsValid", "boolean", "Whether this handle still refers to a bone on a live Blockbench RenderObject.");
        registerProperty(registration, "Parent", "GetParent", "RenderPart|nil", "The direct parent part, or nil for a root bone.");
        registerProperty(registration, "Children", "GetChildren", "RenderPart[]", "The direct child parts in model order.");
        registerProperty(registration, "Transform", "GetTransform", "CFrame",
            "A local transform override composed with the part's base and animation transforms.", "SetTransform");
        registerProperty(registration, "Visible", "GetVisible", "boolean",
            "Whether this bone is drawn. Hiding a parent bone also hides its descendants.", "SetVisible");
    }

    static LuaTable create(MLuaRegistration registration, int objectId,
            MLuaRenderOwnershipRegistry.Owner owner, MLuaBBModelAsset.Bone bone) {
        LuaTable part = registration.createClassInstance("RenderPart", new LuaTable());
        part.rawset(HANDLE_KEY, LuaValue.userdataOf(new ModelPartHandle(objectId, owner, bone.uuid(), registration)));
        return part;
    }

    static LuaTable all(MLuaRegistration registration, int objectId,
            MLuaRenderOwnershipRegistry.Owner owner, MLuaBBModelAsset asset) {
        LuaTable parts = new LuaTable();
        int index = 1;
        for (MLuaBBModelAsset.Bone bone : asset.bones()) {
            parts.set(index++, create(registration, objectId, owner, bone));
        }
        return parts;
    }

    static LuaValue find(MLuaRegistration registration, int objectId,
            MLuaRenderOwnershipRegistry.Owner owner, MLuaBBModelAsset asset, String nameOrId) {
        MLuaBBModelAsset.Bone bone = findBone(asset, nameOrId);
        return bone == null ? LuaValue.NIL : create(registration, objectId, owner, bone);
    }

    static LuaValue findById(MLuaRegistration registration, int objectId,
            MLuaRenderOwnershipRegistry.Owner owner, MLuaBBModelAsset asset, String id) {
        MLuaBBModelAsset.Bone bone = asset.boneById(id);
        return bone == null ? LuaValue.NIL : create(registration, objectId, owner, bone);
    }

    private static void registerProperty(MLuaRegistration registration, String property, String getter,
            String type, String description) {
        registerProperty(registration, property, getter, type, description, null);
    }

    private static void registerProperty(MLuaRegistration registration, String property, String getter,
            String type, String description, String setter) {
        MethodBinding getterBinding = METHODS.get(getter);
        MethodBinding setterBinding = setter == null ? null : METHODS.get(setter);
        if (getterBinding == null || (setter != null && setterBinding == null)) {
            throw new IllegalStateException("RenderPart property references an unregistered method: " + property);
        }
        registration.registerClassProperty("RenderPart", property, getterBinding.function(),
            setterBinding == null ? LuaValue.NIL : setterBinding.function(), type, "client:Client", description);
    }

    private static Map<String, MethodBinding> modelPartMethods() {
        Map<String, MethodBinding> methods = new LinkedHashMap<>();
        methods.put("GetName", binding("GetName", "Returns the part's Blockbench bone name.",
            args -> LuaValue.valueOf(resolve(args.arg1()).bone().name())));
        methods.put("GetId", binding("GetId", "Returns the part's stable Blockbench bone UUID.",
            args -> LuaValue.valueOf(resolve(args.arg1()).bone().uuid())));
        methods.put("IsValid", binding("IsValid",
            "Returns whether this handle still refers to a bone on a live Blockbench RenderObject.",
            args -> LuaValue.valueOf(isValid(args.arg1()))));
        methods.put("GetParent", binding("GetParent", "Returns the parent RenderPart, or nil for a root bone.",
            MLuaRenderPartBinding::parent));
        methods.put("GetChildren", binding("GetChildren", "Returns direct child RenderPart instances in model order.",
            MLuaRenderPartBinding::children));
        methods.put("GetTransform", binding("GetTransform",
            "Returns the local CFrame override composed after the part's base and animated transforms.", args -> {
            ResolvedPart part = resolve(args.arg1());
            return part.object().modelPartTransform(part.handle().partId()).toLua();
        }));
        methods.put("SetTransform", binding("SetTransform",
            "Sets a local CFrame override composed after the part's base and animated transforms.", args -> {
            ResolvedPart part = resolve(args.arg1());
            try {
                part.object().setModelPartTransform(part.handle().partId(), MLuaCFrame.fromLua(args.arg(2)));
            } catch (IllegalArgumentException invalid) {
                throw new LuaError("[MLua] RenderPart:SetTransform expects a valid CFrame: " + invalid.getMessage());
            }
            return LuaValue.NONE;
        }));
        methods.put("ResetTransform", binding("ResetTransform",
            "Removes this part's local CFrame override.", args -> {
            ResolvedPart part = resolve(args.arg1());
            part.object().resetModelPartTransform(part.handle().partId());
            return LuaValue.NONE;
        }));
        methods.put("GetVisible", binding("GetVisible",
            "Returns whether this bone is drawn; a hidden ancestor also hides this bone.", args -> {
            ResolvedPart part = resolve(args.arg1());
            return LuaValue.valueOf(part.object().modelPartVisible(part.handle().partId()));
        }));
        methods.put("SetVisible", binding("SetVisible",
            "Sets whether this bone is drawn. Hiding a parent bone also hides all descendants.", args -> {
            ResolvedPart part = resolve(args.arg1());
            part.object().setModelPartVisible(part.handle().partId(), args.checkboolean(2));
            return LuaValue.NONE;
        }));
        methods.put("ResetVisibility", binding("ResetVisibility",
            "Removes this bone's visibility override and restores the visible default.", args -> {
            ResolvedPart part = resolve(args.arg1());
            part.object().resetModelPartVisibility(part.handle().partId());
            return LuaValue.NONE;
        }));
        methods.put("Equals", binding("Equals", "Compares handles for the same part on the same owned model.",
            MLuaRenderPartBinding::equals));
        return Collections.unmodifiableMap(methods);
    }

    private static MethodBinding binding(String name, String description, Function<Varargs, LuaValue> action) {
        return new MethodBinding(method(name, action), description);
    }

    private static LuaValue method(String name, Function<Varargs, LuaValue> action) {
        return new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                try {
                    return action.apply(args);
                } catch (NullPointerException failure) {
                    MLuaLogger.LOGGER.error("[MLua] RenderPart method '{}' failed", name, failure);
                    throw failure;
                }
            }
        };
    }

    private static LuaValue parent(Varargs args) {
        ResolvedPart part = resolve(args.arg1());
        String parentId = part.bone().parent();
        MLuaBBModelAsset.Bone parent = parentId == null ? null : part.object().bbmodel().boneById(parentId);
        return parent == null ? LuaValue.NIL : create(part.handle().registration(), part.handle().objectId(),
            part.handle().owner(), parent);
    }

    private static LuaValue children(Varargs args) {
        ResolvedPart part = resolve(args.arg1());
        LuaTable children = new LuaTable();
        int index = 1;
        for (MLuaBBModelAsset.Bone child : part.object().bbmodel().childrenOf(part.bone().uuid())) {
            children.set(index++, create(part.handle().registration(), part.handle().objectId(),
                part.handle().owner(), child));
        }
        return children;
    }

    private static LuaValue equals(Varargs args) {
        LuaValue other = args.arg(2);
        if (!other.istable()) return LuaValue.FALSE;
        LuaValue otherHandle = other.checktable().rawget(HANDLE_KEY);
        if (!otherHandle.isuserdata(ModelPartHandle.class)) return LuaValue.FALSE;
        return LuaValue.valueOf(handle(args.arg1()).equals(
            (ModelPartHandle)otherHandle.touserdata(ModelPartHandle.class)));
    }

    private static boolean isValid(LuaValue value) {
        if (!value.istable()) return false;
        LuaValue handleValue = value.checktable().rawget(HANDLE_KEY);
        if (!handleValue.isuserdata(ModelPartHandle.class)) return false;
        ModelPartHandle handle = (ModelPartHandle)handleValue.touserdata(ModelPartHandle.class);
        MLuaClientRenderManager.RenderObject object = MLuaClientRenderManager.get(handle.objectId(), handle.owner());
        return object != null && object.bbmodel() != null && object.bbmodel().boneById(handle.partId()) != null;
    }

    private static ResolvedPart resolve(LuaValue value) {
        ModelPartHandle handle = handle(value);
        MLuaClientRenderManager.RenderObject object = MLuaClientRenderManager.get(handle.objectId(), handle.owner());
        if (object == null || object.bbmodel() == null) {
            throw new LuaError("[MLua] This RenderPart is stale because its Blockbench RenderObject was destroyed.");
        }
        MLuaBBModelAsset.Bone bone = object.bbmodel().boneById(handle.partId());
        if (bone == null) {
            throw new LuaError("[MLua] This RenderPart is stale because its Blockbench model no longer contains that bone.");
        }
        return new ResolvedPart(handle, object, bone);
    }

    private static ModelPartHandle handle(LuaValue value) {
        LuaValue handleValue = value.istable() ? value.checktable().rawget(HANDLE_KEY) : LuaValue.NIL;
        if (!handleValue.isuserdata(ModelPartHandle.class)) {
            throw new LuaError("[MLua] Expected a live RenderPart instance.");
        }
        return (ModelPartHandle)handleValue.touserdata(ModelPartHandle.class);
    }

    private static MLuaBBModelAsset.Bone findBone(MLuaBBModelAsset asset, String nameOrId) {
        String selector = nameOrId == null ? "" : nameOrId;
        if (selector.isBlank()) throw new LuaError("[MLua] RenderObject:GetModelPart expects a non-empty bone name or UUID.");
        MLuaBBModelAsset.Bone byId = asset.boneById(selector);
        if (byId != null) return byId;
        List<MLuaBBModelAsset.Bone> matches = asset.bonesNamed(selector);
        if (matches.size() > 1) throw new LuaError("[MLua] Model part name '" + selector
            + "' is ambiguous; use RenderObject:GetModelPartById(uuid).");
        return matches.isEmpty() ? null : matches.get(0);
    }

    private record ModelPartHandle(int objectId, MLuaRenderOwnershipRegistry.Owner owner, String partId,
                                   MLuaRegistration registration) { }
    private record ResolvedPart(ModelPartHandle handle, MLuaClientRenderManager.RenderObject object,
                                MLuaBBModelAsset.Bone bone) { }
}
