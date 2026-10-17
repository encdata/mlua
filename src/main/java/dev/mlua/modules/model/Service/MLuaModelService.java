package dev.mlua.modules.model.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.math.MLuaVector;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.runtime.client.MLuaClientModelManager;
import dev.mlua.runtime.client.MLuaModelAdapterRegistry;
import dev.mlua.core.MLuaRuntime;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

@MLuaDescription("Client-side model part overrides and Lua-registered bone adapters for living entity models. Adapters have bounded class, bone, and path counts.")
public final class MLuaModelService extends MLuaReflectiveService {
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

    @MLuaDescription("Registers named animation bones and model part paths for a Java model class. Addons can contribute different bones to the same class; duplicate bone names conflict. Subclasses inherit parent mappings and can override individual bone names. Use the full class name. Limits: 256 adapter classes, 512 bones per model class, 8192 bones total, 256 characters per class name, 128 per bone name, and 512 characters or 32 segments per part path.")
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
            mapping.put(key.tojstring(), path.tojstring());
        }
        try {
            MLuaModelAdapterRegistry.register(modelClass, mapping, adapterOwner());
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw new LuaError("[MLua] " + invalid.getMessage());
        }
    }

    @MLuaDescription("Adds or updates one animation bone mapping for the calling addon, preserving its other mappings and mappings from other addons.")
    public void RegisterAdapterBone(String modelClass, String boneName, String partPath) {
        this.requireClientSide(this.name());
        try {
            MLuaModelAdapterRegistry.registerBone(modelClass, boneName, partPath, adapterOwner());
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw new LuaError("[MLua] " + invalid.getMessage());
        }
    }

    @MLuaDescription("Removes the model adapter registered by the calling addon for a Java model class.")
    public boolean UnregisterAdapter(String modelClass) {
        this.requireClientSide(this.name());
        try {
            return MLuaModelAdapterRegistry.unregister(modelClass, adapterOwner());
        } catch (IllegalArgumentException invalid) {
            throw new LuaError("[MLua] " + invalid.getMessage());
        }
    }

    @MLuaDescription("Removes one named bone mapping registered by the calling addon, leaving other mappings and addons intact.")
    public boolean UnregisterAdapterBone(String modelClass, String boneName) {
        this.requireClientSide(this.name());
        try {
            return MLuaModelAdapterRegistry.unregisterBone(modelClass, boneName, adapterOwner());
        } catch (IllegalArgumentException invalid) {
            throw new LuaError("[MLua] " + invalid.getMessage());
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
            throw new LuaError("[MLua] " + invalid.getMessage());
        }
    }

    @MLuaDescription("Returns current adapter usage and registration limits. Counts include adapters registered by all addons.")
    public LuaTable GetAdapterLimits() {
        this.requireClientSide(this.name());
        LuaTable limits = new LuaTable();
        limits.set("ActiveModels", MLuaModelAdapterRegistry.registeredModelClassCount());
        limits.set("ActiveParts", MLuaModelAdapterRegistry.registeredPartCount());
        limits.set("MaxModels", MLuaModelAdapterRegistry.MAX_MODEL_CLASSES);
        limits.set("MaxPartsPerModel", MLuaModelAdapterRegistry.MAX_PARTS_PER_MODEL);
        limits.set("MaxParts", MLuaModelAdapterRegistry.MAX_TOTAL_PARTS);
        limits.set("MaxClassNameLength", MLuaModelAdapterRegistry.MAX_CLASS_NAME_LENGTH);
        limits.set("MaxBoneNameLength", MLuaModelAdapterRegistry.MAX_BONE_NAME_LENGTH);
        limits.set("MaxPathLength", MLuaModelAdapterRegistry.MAX_PART_PATH_LENGTH);
        limits.set("MaxPathDepth", MLuaModelAdapterRegistry.MAX_PART_PATH_DEPTH);
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

    @MLuaDescription("Sets multiple model part overrides from a definition table.")
    public void SetPart(LuaValue entity, String part, LuaTable properties) {
        this.requireClientSide(this.name());
        MLuaEntityScope scope = resolveEntityScope(entity);
        if (!properties.get("CFrame").isnil()) {
            MLuaClientModelManager.setCFrame(scope, part, MLuaCFrame.fromLua(properties.get("CFrame")), owner());
        }
        if (!properties.get("Offset").isnil()) {
            MLuaClientModelManager.setOffset(scope, part, properties.get("Offset"), owner());
        }
        if (!properties.get("Scale").isnil()) {
            MLuaClientModelManager.setScale(scope, part, properties.get("Scale"), owner());
        }
        if (!properties.get("Visible").isnil()) {
            MLuaClientModelManager.setVisible(scope, part, properties.get("Visible").checkboolean(), owner());
        }
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

    @MLuaDescription("Returns standard part names, names with state on this entity, and adapter bones that resolved on the entity's most recently rendered model.")
    public Iterable<String> GetPartNames(LuaValue entity) {
        this.requireClientSide(this.name());
        return MLuaClientModelManager.getPartNames(resolveEntityScope(entity), owner());
    }

    private MLuaClientModelManager.Owner owner() {
        Object runtimeOwner = context.mlua().registration() instanceof MLuaRuntime runtime ? runtime.session() : null;
        return new MLuaClientModelManager.Owner(runtimeOwner, context.mlua().ownerModId());
    }

    private MLuaModelAdapterRegistry.Owner adapterOwner() {
        Object runtimeOwner = context.mlua().registration() instanceof MLuaRuntime runtime ? runtime.session() : null;
        return new MLuaModelAdapterRegistry.Owner(runtimeOwner, context.mlua().ownerModId());
    }

    private MLuaEntityScope resolveEntityScope(LuaValue value) {
        if (value.isint()) {
            return this.resolveEntityScope(value.toint());
        }
        if (value.istable()) {
            net.minecraft.world.entity.Entity entity = MLuaEntityWrapper.fromLuaInstance(value, this.context.mlua().registration());
            if (entity != null) return MLuaEntityScope.of(entity);
        }
        throw new LuaError("[MLua] Expected an Entity instance or entity ID");
    }

    private MLuaEntityScope resolveEntityScope(int entityId) {
        net.minecraft.world.level.Level level = MLuaMinecraftClient.instance().level;
        net.minecraft.world.entity.Entity entity = level == null ? null : level.getEntity(entityId);
        if (entity == null) throw new LuaError("[MLua] Model target must exist in the current client world");
        return MLuaEntityScope.of(entity);
    }

}
