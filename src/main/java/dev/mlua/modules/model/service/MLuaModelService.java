package dev.mlua.modules.model.service;

import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.modules.entity.MLuaEntityScopeResolver;
import dev.mlua.runtime.client.MLuaClientModelManager;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@MLuaDescription("Client-side model part overrides for living entities and Lua-registered bone adapters used by entity attachments. Adapter paths can target living models or models held by non-living entity renderers, including models resolved through an explicit field path; registrations have bounded class, bone, and path counts.")
public final class MLuaModelService extends MLuaReflectiveService {
    private final MLuaModelPartOverrides partOverrides;
    private final MLuaModelAdapterOperations adapterOperations;

    public MLuaModelService(MLuaService.Context context) {
        super(context);
        this.partOverrides = new MLuaModelPartOverrides(this);
        this.adapterOperations = new MLuaModelAdapterOperations(context, this);
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
        return this.adapterOperations.resolvePartName(name);
    }

    @MLuaDescription("Registers named animation bones and dot-separated ModelPart paths for a Java model class. Living models apply poses after Minecraft prepares their model; addon-defined non-living renderers use the same mappings while their ModelParts render when paired with RegisterRendererAdapter. Addons can contribute different bones to the same class; duplicate bone names conflict. Subclasses inherit parent mappings and can override individual bone names. Use the full class name. Limits: 256 adapter classes, 512 bones per model class, 8192 bones total, 256 characters per class name, 128 per bone name, and 512 characters or 32 segments per part path.")
    public void RegisterAdapter(String modelClass, LuaTable parts) {
        this.adapterOperations.registerAdapter(modelClass, parts);
    }

    @MLuaDescription("Adds or updates one animation bone mapping for the calling addon, preserving its other mappings and mappings from other addons.")
    public void RegisterAdapterBone(String modelClass, String boneName, String partPath) {
        this.adapterOperations.registerAdapterBone(modelClass, boneName, partPath);
    }

    @MLuaDescription("Registers a Lua resolver for one Java model class and returns a Connection for this exact registration. The resolver receives a frame-scoped Java model reference and returns a table of animation bone names to dot-separated ModelPart paths. MLua calls it once for each live model instance, caches the validated mapping, and disables it if it errors.")
    public LuaTable RegisterAdapterResolver(String modelClass, LuaValue resolver) {
        return this.adapterOperations.registerAdapterResolver(modelClass, resolver);
    }

    @MLuaDescription("Registers an adapter for a Java model class whose animated parts are not Minecraft ModelPart objects and returns a Connection for this exact registration. Bones is an array of stable names. Apply(model, entity, bones) receives per-bone Animation, CFrame, Offset, Scale, and Visible state. MLua calls it after setupAnim for living models and before the renderer for registered non-living models. Optional ApplyPose(model, bone, poseStack, entity, state) applies a custom part transform when an MLua attachment uses that bone and returns true when it handled the bone. Without ApplyPose, model animation still works but attachments for custom bones have no model transform. Java references are valid only during each callback; callbacks run on the client render thread with a bounded instruction budget and are disabled after an error.")
    public LuaTable RegisterCustomAdapter(String modelClass, LuaTable definition) {
        return this.adapterOperations.registerCustomAdapter(modelClass, definition);
    }

    @MLuaDescription("Removes the custom model adapter registered by the calling addon for a Java model class.")
    public boolean UnregisterCustomAdapter(String modelClass) {
        return this.adapterOperations.unregisterCustomAdapter(modelClass);
    }

    @MLuaDescription("Removes the model resolver registered by the calling addon for a Java model class.")
    public boolean UnregisterAdapterResolver(String modelClass) {
        return this.adapterOperations.unregisterAdapterResolver(modelClass);
    }

    @MLuaDescription("Removes the model adapter registered by the calling addon for a Java model class.")
    public boolean UnregisterAdapter(String modelClass) {
        return this.adapterOperations.unregisterAdapter(modelClass);
    }

    @MLuaDescription("Removes one named bone mapping registered by the calling addon, leaving other mappings and addons intact.")
    public boolean UnregisterAdapterBone(String modelClass, String boneName) {
        return this.adapterOperations.unregisterAdapterBone(modelClass, boneName);
    }

    @MLuaDescription("Maps an entity renderer class to the field path of its model. Required for addon-defined non-living renderers when MLua must apply animation poses, inspect native parts, or draw bone attachments; use it when automatic discovery is ambiguous or the model is nested in a renderer field.")
    public void RegisterRendererAdapter(String rendererClass, String modelFieldPath) {
        this.adapterOperations.registerRendererAdapter(rendererClass, modelFieldPath);
    }

    @MLuaDescription("Removes the renderer-to-model field mapping registered by the calling addon.")
    public boolean UnregisterRendererAdapter(String rendererClass) {
        return this.adapterOperations.unregisterRendererAdapter(rendererClass);
    }

    @MLuaDescription("Returns the registered model field path for one exact renderer class, or nil when none is registered.")
    public LuaValue GetRendererAdapter(String rendererClass) {
        return this.adapterOperations.getRendererAdapter(rendererClass);
    }

    @MLuaDescription("Returns the merged bone-to-part paths registered for a Java model class, or nil when no addon has registered an adapter for it.")
    public LuaValue GetAdapter(String modelClass) {
        return this.adapterOperations.getAdapter(modelClass);
    }

    @MLuaDescription("Returns the declared custom bone names for one exact Java model class, or nil when no custom adapter is registered.")
    public LuaValue GetCustomAdapter(String modelClass) {
        return this.adapterOperations.getCustomAdapter(modelClass);
    }

    @MLuaDescription("Returns the adapter MLua resolved for an entity's most recently rendered model, including its Java class, mapped bone paths, and paths that resolved. Returns nil before a mapped model has rendered.")
    public LuaValue GetAppliedAdapter(LuaValue entity) {
        return this.adapterOperations.getAppliedAdapter(entity);
    }

    @MLuaDescription("Returns global model and renderer adapter usage and limits, plus bone, resolver, and custom-adapter usage for this addon runtime.")
    public LuaTable GetAdapterLimits() {
        return this.adapterOperations.getAdapterLimits();
    }

    @MLuaDescription("Returns active model override usage and the global, per-addon, and per-entity limits.")
    public LuaTable GetOverrideLimits() {
        return this.partOverrides.getLimits();
    }

    @MLuaDescription("Offsets a model part from Minecraft's prepared pose using a CFrame translation and rotation.")
    public void SetPartCFrame(LuaValue entity, String part, LuaValue cframe) {
        this.partOverrides.setCFrame(entity, part, cframe);
    }

    @MLuaDescription("Sets a model part translation offset from a native Vector3 value.")
    public void SetPartOffset(LuaValue entity, String part, LuaValue offset) {
        this.partOverrides.setOffset(entity, part, offset);
    }

    @MLuaDescription("Sets a model part scale override.")
    public void SetPartScale(LuaValue entity, String part, LuaValue scale) {
        this.partOverrides.setScale(entity, part, scale);
    }

    @MLuaDescription("Sets whether a model part is rendered.")
    public void SetPartVisible(LuaValue entity, String part, boolean visible) {
        this.partOverrides.setVisible(entity, part, visible);
    }

    @MLuaDescription("Sets CFrame, Offset, Scale, and Visible model part overrides from a definition table. All supplied properties are validated before any override is changed; unknown properties and non-finite transforms are rejected.")
    public void SetPart(LuaValue entity, String part, LuaTable properties) {
        this.partOverrides.setPart(entity, part, properties);
    }

    @MLuaDescription("Clears overrides for one model part.")
    public void ClearPart(LuaValue entity, String part) {
        this.partOverrides.clearPart(entity, part);
    }

    @MLuaDescription("Clears overrides for one entity model.")
    public void ClearModel(LuaValue entity) {
        this.partOverrides.clearModel(entity);
    }

    @MLuaDescription("Clears model overrides owned by the calling script.")
    public void ClearAll() {
        this.partOverrides.clearAll();
    }

    @MLuaDescription("Clears model overrides owned by the calling script.")
    public void Clear() {
        ClearAll();
    }

    @MLuaDescription("Returns current override state for an entity model.")
    public LuaTable GetModelState(LuaValue entity) {
        return this.partOverrides.getModelState(entity);
    }

    @MLuaDescription("Returns standard part names, names with state on this entity, and adapter bones that resolved on the entity's most recently rendered model.")
    public Iterable<String> GetPartNames(LuaValue entity) {
        return this.partOverrides.getPartNames(entity);
    }

    MLuaClientModelManager.Owner owner() {
        dev.mlua.core.MLuaContext active = MLuaLuaBindings.boundContext(context.mlua().globals());
        if (active != null) {
            return new MLuaClientModelManager.Owner(active.session(), active.ownerModId());
        }
        Object runtimeOwner = context.mlua().registration() instanceof MLuaRuntime runtime ? runtime.session() : null;
        return new MLuaClientModelManager.Owner(runtimeOwner, context.mlua().ownerModId());
    }



    MLuaEntityScope resolveEntityScope(LuaValue value) {
        return MLuaEntityScopeResolver.resolveClientTarget(value,
            this.context.mlua().registration(), "Model target must exist in the current client world");
    }

    void requireModelClient() {
        this.requireClientSide(this.name());
    }

}
