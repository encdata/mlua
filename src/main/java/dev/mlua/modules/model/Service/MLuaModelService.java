package dev.mlua.modules.model.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.math.MLuaCFrame;
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

@MLuaDescription("Client-side model part overrides and Lua-registered bone adapters for living entity models.")
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

    @MLuaDescription("Registers named animation bones and model part paths for a Java model class. Use its full class name; another addon cannot claim an already registered class.")
    public void RegisterAdapter(String modelClass, LuaTable parts) {
        ensureClient();
        java.util.LinkedHashMap<String, String> mapping = new java.util.LinkedHashMap<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = parts.next(key);
            key = next.arg1();
            if (key.isnil()) break;
            LuaValue path = next.arg(2);
            if (!key.isstring() || !path.isstring()) {
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

    @MLuaDescription("Removes the model adapter registered by the calling addon for a Java model class.")
    public boolean UnregisterAdapter(String modelClass) {
        ensureClient();
        try {
            return MLuaModelAdapterRegistry.unregister(modelClass, adapterOwner());
        } catch (IllegalArgumentException invalid) {
            throw new LuaError("[MLua] " + invalid.getMessage());
        }
    }

    @MLuaDescription("Sets a model part transform override.")
    public void SetPartCFrame(LuaValue entity, String part, LuaValue cframe) {
        ensureClient();
        MLuaClientModelManager.setCFrame(resolveEntityScope(entity), part, MLuaCFrame.fromLua(cframe), owner());
    }

    @MLuaDescription("Sets a model part translation offset from a native Vector3 value.")
    public void SetPartOffset(LuaValue entity, String part, LuaValue offset) {
        ensureClient();
        MLuaClientModelManager.setOffset(resolveEntityScope(entity), part, offset, owner());
    }

    @MLuaDescription("Sets a model part scale override.")
    public void SetPartScale(LuaValue entity, String part, LuaValue scale) {
        ensureClient();
        MLuaClientModelManager.setScale(resolveEntityScope(entity), part, scale, owner());
    }

    @MLuaDescription("Sets whether a model part is rendered.")
    public void SetPartVisible(LuaValue entity, String part, boolean visible) {
        ensureClient();
        MLuaClientModelManager.setVisible(resolveEntityScope(entity), part, visible, owner());
    }

    @MLuaDescription("Sets multiple model part overrides from a definition table.")
    public void SetPart(LuaValue entity, String part, LuaTable properties) {
        ensureClient();
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
        ensureClient();
        MLuaClientModelManager.clearPart(resolveEntityScope(entity), part, owner());
    }

    @MLuaDescription("Clears overrides for one entity model.")
    public void ClearModel(LuaValue entity) {
        ensureClient();
        MLuaClientModelManager.clearEntity(resolveEntityScope(entity), owner());
    }

    @MLuaDescription("Clears model overrides owned by the calling script.")
    public void ClearAll() {
        ensureClient();
        MLuaClientModelManager.clearOwned(owner());
    }

    @MLuaDescription("Clears model overrides owned by the calling script.")
    public void Clear() {
        ClearAll();
    }

    @MLuaDescription("Returns current override state for an entity model.")
    public LuaTable GetModelState(LuaValue entity) {
        ensureClient();
        LuaTable result = new LuaTable();
        MLuaEntityScope scope = resolveEntityScope(entity);
        for (Map.Entry<String, MLuaClientModelManager.PartState> entry : MLuaClientModelManager.getState(scope, owner()).entrySet()) {
            LuaTable part = new LuaTable();
            if (entry.getValue().cframe() != null) part.set("CFrame", entry.getValue().cframe().toLua());
            if (entry.getValue().offset() != null) part.set("Offset", entry.getValue().offset());
            if (entry.getValue().scale() != null) part.set("Scale", entry.getValue().scale());
            if (entry.getValue().visible() != null) part.set("Visible", LuaValue.valueOf(entry.getValue().visible()));
            result.set(entry.getKey(), part);
        }
        return result;
    }

    @MLuaDescription("Returns the model part names supported by the active renderer.")
    public Iterable<String> GetPartNames(LuaValue entity) {
        ensureClient();
        return MLuaClientModelManager.getPartNames(resolveEntityScope(entity), owner());
    }

    private void ensureClient() {
        if (context.mlua().side() != Side.CLIENT) {
            throw new LuaError("[MLua] ModelService is client-side only.");
        }
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
