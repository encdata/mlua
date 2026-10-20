package dev.mlua.modules.model.service;

import dev.mlua.api.MLuaLuaErrors;
import dev.mlua.common.enums.Side;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.math.MLuaVector;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.modules.entity.MLuaEntityScopeResolver;
import dev.mlua.runtime.client.MLuaClientModelManager;
import dev.mlua.runtime.client.MLuaModelAdapterRegistry;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.engine.MLuaLuaBindings;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

@MLuaDescription("Client-side model part overrides for living entities and Lua-registered bone adapters used by entity attachments. Adapter paths can target living models or models held by non-living entity renderers, including models resolved through an explicit field path; registrations have bounded class, bone, and path counts.")
public final class MLuaModelService extends MLuaReflectiveService {
    private static final java.util.Set<String> PART_PROPERTIES = java.util.Set.of("CFrame", "Offset", "Scale", "Visible");

    public MLuaModelService(MLuaService.Context context) {
        super(context);
    }

    @Override
    public String name() {
        return "ModelService";
    }

    @Override
    public LuaTable toLua() {
        LuaTable table = super.toLua();
        table.set("__side", "Client");
        return table;
    }

    @MLuaDescription("Normalizes a model part name for the active renderer.")
    public String ResolvePartName(String name) {
        return MLuaClientModelManager.normalizePartName(name);
    }

    @MLuaDescription("Registers named animation bones and dot-separated ModelPart paths for a Java model class. Living models apply poses after Minecraft prepares their model; addon-defined non-living renderers use the same mappings while their ModelParts render when paired with RegisterRendererAdapter. Addons can contribute different bones to the same class; duplicate bone names conflict. Subclasses inherit parent mappings and can override individual bone names. Use the full class name. Limits: 256 adapter classes, 512 bones per model class, 8192 bones total, 256 characters per class name, 128 per bone name, and 512 characters or 32 segments per part path.")
    public void RegisterAdapter(String modelClass, LuaTable parts) {
        this.requireClientSide(this.name());
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
            MLuaModelAdapterRegistry.register(modelClass, mapping, adapterOwner());
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    @MLuaDescription("Adds or updates one animation bone mapping for the calling addon, preserving its other mappings and mappings from other addons.")
    public void RegisterAdapterBone(String modelClass, String boneName, String partPath) {
        this.requireClientSide(this.name());
        try {
            MLuaModelAdapterRegistry.registerBone(modelClass, boneName, partPath, adapterOwner());
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    @MLuaDescription("Registers a Lua resolver for one Java model class and returns a Connection for this exact registration. The resolver receives a frame-scoped Java model reference and returns a table of animation bone names to dot-separated ModelPart paths. MLua calls it once for each live model instance, caches the validated mapping, and disables it if it errors.")
    public LuaTable RegisterAdapterResolver(String modelClass, LuaValue resolver) {
        this.requireClientSide(this.name());
        dev.mlua.core.MLuaContext callbackContext = MLuaLuaBindings.boundContext(this.context.mlua().globals());
        if (callbackContext == null) {
            throw new LuaError("[MLua] RegisterAdapterResolver must be called while a Lua addon is executing.");
        }
        try {
            return MLuaLuaBindings.connection(MLuaModelAdapterRegistry.registerResolver(
                modelClass, resolver, callbackContext, adapterOwner()), this.context.registration());
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    @MLuaDescription("Registers an adapter for a Java model class whose animated parts are not Minecraft ModelPart objects and returns a Connection for this exact registration. Bones is an array of stable names. Apply(model, entity, bones) receives per-bone Animation, CFrame, Offset, Scale, and Visible state. MLua calls it after setupAnim for living models and before the renderer for registered non-living models. Optional ApplyPose(model, bone, poseStack, entity, state) applies a custom part transform when an MLua attachment uses that bone and returns true when it handled the bone. Without ApplyPose, model animation still works but attachments for custom bones have no model transform. Java references are valid only during each callback; callbacks run on the client render thread with a bounded instruction budget and are disabled after an error.")
    public LuaTable RegisterCustomAdapter(String modelClass, LuaTable definition) {
        this.requireClientSide(this.name());
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
                modelClass, bones, apply, applyPose, callbackContext, adapterOwner()), this.context.registration());
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    @MLuaDescription("Removes the custom model adapter registered by the calling addon for a Java model class.")
    public boolean UnregisterCustomAdapter(String modelClass) {
        this.requireClientSide(this.name());
        try {
            return MLuaModelAdapterRegistry.unregisterCustomAdapter(modelClass, adapterOwner());
        } catch (IllegalArgumentException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    @MLuaDescription("Removes the model resolver registered by the calling addon for a Java model class.")
    public boolean UnregisterAdapterResolver(String modelClass) {
        this.requireClientSide(this.name());
        try {
            return MLuaModelAdapterRegistry.unregisterResolver(modelClass, adapterOwner());
        } catch (IllegalArgumentException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    @MLuaDescription("Removes the model adapter registered by the calling addon for a Java model class.")
    public boolean UnregisterAdapter(String modelClass) {
        this.requireClientSide(this.name());
        try {
            return MLuaModelAdapterRegistry.unregister(modelClass, adapterOwner());
        } catch (IllegalArgumentException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    @MLuaDescription("Removes one named bone mapping registered by the calling addon, leaving other mappings and addons intact.")
    public boolean UnregisterAdapterBone(String modelClass, String boneName) {
        this.requireClientSide(this.name());
        try {
            return MLuaModelAdapterRegistry.unregisterBone(modelClass, boneName, adapterOwner());
        } catch (IllegalArgumentException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    @MLuaDescription("Maps an entity renderer class to the field path of its model. Required for addon-defined non-living renderers when MLua must apply animation poses, inspect native parts, or draw bone attachments; use it when automatic discovery is ambiguous or the model is nested in a renderer field.")
    public void RegisterRendererAdapter(String rendererClass, String modelFieldPath) {
        this.requireClientSide(this.name());
        try {
            MLuaModelAdapterRegistry.registerRendererAdapter(rendererClass, modelFieldPath, adapterOwner());
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    @MLuaDescription("Removes the renderer-to-model field mapping registered by the calling addon.")
    public boolean UnregisterRendererAdapter(String rendererClass) {
        this.requireClientSide(this.name());
        try {
            return MLuaModelAdapterRegistry.unregisterRendererAdapter(rendererClass, adapterOwner());
        } catch (IllegalArgumentException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    @MLuaDescription("Returns the registered model field path for one exact renderer class, or nil when none is registered.")
    public LuaValue GetRendererAdapter(String rendererClass) {
        this.requireClientSide(this.name());
        try {
            String path = MLuaModelAdapterRegistry.registeredRendererModelField(rendererClass);
            return path == null ? LuaValue.NIL : LuaValue.valueOf(path);
        } catch (IllegalArgumentException invalid) {
            throw MLuaLuaErrors.javaFailure("Model adapter registration", invalid);
        }
    }

    @MLuaDescription("Returns the merged bone-to-part paths registered for a Java model class, or nil when no addon has registered an adapter for it.")
    public LuaValue GetAdapter(String modelClass) {
        this.requireClientSide(this.name());
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

    @MLuaDescription("Returns the declared custom bone names for one exact Java model class, or nil when no custom adapter is registered.")
    public LuaValue GetCustomAdapter(String modelClass) {
        this.requireClientSide(this.name());
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

    @MLuaDescription("Returns the adapter MLua resolved for an entity's most recently rendered model, including its Java class, mapped bone paths, and paths that resolved. Returns nil before a mapped model has rendered.")
    public LuaValue GetAppliedAdapter(LuaValue entity) {
        this.requireClientSide(this.name());
        MLuaModelAdapterRegistry.AdapterSnapshot snapshot =
            MLuaModelAdapterRegistry.appliedAdapter(resolveEntityScope(entity));
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

    @MLuaDescription("Returns global model and renderer adapter usage and limits, plus bone, resolver, and custom-adapter usage for this addon runtime.")
    public LuaTable GetAdapterLimits() {
        this.requireClientSide(this.name());
        LuaTable limits = new LuaTable();
        limits.set("ActiveModels", MLuaModelAdapterRegistry.registeredModelClassCount());
        limits.set("ActiveParts", MLuaModelAdapterRegistry.registeredPartCount());
        limits.set("AddonParts", MLuaModelAdapterRegistry.registeredPartsForOwner(adapterOwner()));
        limits.set("ActiveRenderers", MLuaModelAdapterRegistry.registeredRendererClassCount());
        limits.set("ActiveRendererAdapters", MLuaModelAdapterRegistry.registeredRendererAdapterCount());
        limits.set("AddonRendererAdapters", MLuaModelAdapterRegistry.registeredRendererAdapterCount(adapterOwner()));
        limits.set("ActiveResolvers", MLuaModelAdapterRegistry.registeredResolverClassCount());
        limits.set("ActiveCustomAdapters", MLuaModelAdapterRegistry.registeredCustomAdapterClassCount());
        limits.set("AddonCustomAdapters", MLuaModelAdapterRegistry.registeredCustomAdapterCount(adapterOwner()));
        limits.set("ActiveCustomBones", MLuaModelAdapterRegistry.registeredCustomPartCount());
        limits.set("AddonResolvers", MLuaModelAdapterRegistry.registeredResolverCount(adapterOwner()));
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

    @MLuaDescription("Returns active model override usage and the global, per-addon, and per-entity limits.")
    public LuaTable GetOverrideLimits() {
        this.requireClientSide(this.name());
        LuaTable limits = new LuaTable();
        limits.set("ActiveOverrides", MLuaClientModelManager.overrideCount());
        limits.set("AddonOverrides", MLuaClientModelManager.overrideCount(owner()));
        limits.set("MaxOverrides", MLuaClientModelManager.MAX_OVERRIDES);
        limits.set("MaxOverridesPerAddon", MLuaClientModelManager.MAX_OVERRIDES_PER_OWNER);
        limits.set("MaxOverridesPerAddonPerEntity", MLuaClientModelManager.MAX_OVERRIDES_PER_ENTITY);
        limits.set("MaxPartNameLength", MLuaClientModelManager.MAX_PART_NAME_LENGTH);
        return limits;
    }

    @MLuaDescription("Offsets a model part from Minecraft's prepared pose using a CFrame translation and rotation.")
    public void SetPartCFrame(LuaValue entity, String part, LuaValue cframe) {
        this.requireClientSide(this.name());
        MLuaClientModelManager.setCFrame(resolveEntityScope(entity), part, MLuaCFrame.fromLua(cframe), owner());
    }

    @MLuaDescription("Sets a model part translation offset from a native Vector3 value.")
    public void SetPartOffset(LuaValue entity, String part, LuaValue offset) {
        this.requireClientSide(this.name());
        MLuaClientModelManager.setOffset(resolveEntityScope(entity), part, offset, owner());
    }

    @MLuaDescription("Sets a model part scale override.")
    public void SetPartScale(LuaValue entity, String part, LuaValue scale) {
        this.requireClientSide(this.name());
        MLuaClientModelManager.setScale(resolveEntityScope(entity), part, scale, owner());
    }

    @MLuaDescription("Sets whether a model part is rendered.")
    public void SetPartVisible(LuaValue entity, String part, boolean visible) {
        this.requireClientSide(this.name());
        MLuaClientModelManager.setVisible(resolveEntityScope(entity), part, visible, owner());
    }

    @MLuaDescription("Sets CFrame, Offset, Scale, and Visible model part overrides from a definition table. All supplied properties are validated before any override is changed; unknown properties and non-finite transforms are rejected.")
    public void SetPart(LuaValue entity, String part, LuaTable properties) {
        this.requireClientSide(this.name());
        MLuaEntityScope scope = resolveEntityScope(entity);
        LuaValue key = LuaValue.NIL;
        while (true) {
            var entry = properties.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            if (!key.isstring() || !PART_PROPERTIES.contains(key.tojstring())) {
                throw new LuaError("[MLua] ModelService:SetPart accepts only CFrame, Offset, Scale, and Visible properties.");
            }
        }

        LuaValue cframeValue = properties.get("CFrame");
        MLuaCFrame cframe = cframeValue.isnil() ? null : MLuaCFrame.fromLua(cframeValue);
        if (cframe != null) validateFinite(cframe);
        LuaValue offset = validatedVector(properties.get("Offset"), "Offset");
        LuaValue scale = validatedVector(properties.get("Scale"), "Scale");
        LuaValue visibleValue = properties.get("Visible");
        Boolean visible = null;
        if (!visibleValue.isnil()) {
            if (!visibleValue.isboolean()) throw new LuaError("[MLua] ModelService:SetPart Visible must be a boolean.");
            visible = visibleValue.toboolean();
        }

        // The manager validates capacity and applies all supplied properties as one update.
        MLuaClientModelManager.setPartProperties(scope, part, cframe, offset, scale, visible, owner());
    }

    @MLuaDescription("Clears overrides for one model part.")
    public void ClearPart(LuaValue entity, String part) {
        this.requireClientSide(this.name());
        MLuaClientModelManager.clearPart(resolveEntityScope(entity), part, owner());
    }

    @MLuaDescription("Clears overrides for one entity model.")
    public void ClearModel(LuaValue entity) {
        this.requireClientSide(this.name());
        MLuaClientModelManager.clearEntity(resolveEntityScope(entity), owner());
    }

    @MLuaDescription("Clears model overrides owned by the calling script.")
    public void ClearAll() {
        this.requireClientSide(this.name());
        MLuaClientModelManager.clearOwned(owner());
    }

    @MLuaDescription("Clears model overrides owned by the calling script.")
    public void Clear() {
        ClearAll();
    }

    @MLuaDescription("Returns current override state for an entity model.")
    public LuaTable GetModelState(LuaValue entity) {
        this.requireClientSide(this.name());
        LuaTable result = new LuaTable();
        MLuaEntityScope scope = resolveEntityScope(entity);
        for (Map.Entry<String, MLuaClientModelManager.PartState> entry : MLuaClientModelManager.getState(scope, owner()).entrySet()) {
            LuaTable part = new LuaTable();
            if (entry.getValue().cframe() != null) part.set("CFrame", entry.getValue().cframe().toLua());
            if (entry.getValue().offset() != null) part.set("Offset", copyVector3(entry.getValue().offset()));
            if (entry.getValue().scale() != null) part.set("Scale", copyVector3(entry.getValue().scale()));
            if (entry.getValue().visible() != null) part.set("Visible", LuaValue.valueOf(entry.getValue().visible()));
            result.set(entry.getKey(), part);
        }
        return result;
    }

    private static LuaTable copyVector3(LuaValue value) {
        double[] components = MLuaVector.components3(value);
        return MLuaVector.vector3(components[0], components[1], components[2]);
    }

    private static LuaValue validatedVector(LuaValue value, String property) {
        if (value.isnil()) return null;
        if (!MLuaVector.isVector3(value)) {
            throw new LuaError("[MLua] ModelService:SetPart " + property + " must be a native Vector3.");
        }
        for (double component : MLuaVector.components3(value)) {
            if (!Double.isFinite(component)) {
                throw new LuaError("[MLua] ModelService:SetPart " + property + " components must be finite.");
            }
        }
        return value;
    }

    private static void validateFinite(MLuaCFrame cframe) {
        double[] components = {cframe.x(), cframe.y(), cframe.z(), cframe.r00(), cframe.r01(), cframe.r02(),
            cframe.r10(), cframe.r11(), cframe.r12(), cframe.r20(), cframe.r21(), cframe.r22()};
        for (double component : components) {
            if (!Double.isFinite(component)) {
                throw new LuaError("[MLua] ModelService:SetPart CFrame components must be finite.");
            }
        }
    }

    @MLuaDescription("Returns standard part names, names with state on this entity, and adapter bones that resolved on the entity's most recently rendered model.")
    public Iterable<String> GetPartNames(LuaValue entity) {
        this.requireClientSide(this.name());
        return MLuaClientModelManager.getPartNames(resolveEntityScope(entity), owner());
    }

    private MLuaClientModelManager.Owner owner() {
        dev.mlua.core.MLuaContext active = MLuaLuaBindings.boundContext(context.mlua().globals());
        if (active != null) {
            return new MLuaClientModelManager.Owner(active.session(), active.ownerModId());
        }
        Object runtimeOwner = context.mlua().registration() instanceof MLuaRuntime runtime ? runtime.session() : null;
        return new MLuaClientModelManager.Owner(runtimeOwner, context.mlua().ownerModId());
    }

    private MLuaModelAdapterRegistry.Owner adapterOwner() {
        dev.mlua.core.MLuaContext active = MLuaLuaBindings.boundContext(context.mlua().globals());
        if (active != null) {
            return new MLuaModelAdapterRegistry.Owner(active.session(), active.ownerModId());
        }
        Object runtimeOwner = context.mlua().registration() instanceof MLuaRuntime runtime ? runtime.session() : null;
        return new MLuaModelAdapterRegistry.Owner(runtimeOwner, context.mlua().ownerModId());
    }

    private MLuaEntityScope resolveEntityScope(LuaValue value) {
        return MLuaEntityScopeResolver.resolveClientTarget(value,
            this.context.mlua().registration(), "Model target must exist in the current client world");
    }

}
