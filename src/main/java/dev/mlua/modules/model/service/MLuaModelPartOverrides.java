package dev.mlua.modules.model.service;

import java.util.Map;
import java.util.Set;

import dev.mlua.math.MLuaCFrame;
import dev.mlua.math.MLuaVector;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.runtime.client.MLuaClientModelManager;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Owns validation and lifecycle for per-entity model part overrides. */
final class MLuaModelPartOverrides {
    private static final Set<String> PART_PROPERTIES = Set.of("CFrame", "Offset", "Scale", "Visible");

    private final MLuaModelService service;

    MLuaModelPartOverrides(MLuaModelService service) {
        this.service = service;
    }

    LuaTable getLimits() {
        service.requireModelClient();
        LuaTable limits = new LuaTable();
        limits.set("ActiveOverrides", MLuaClientModelManager.overrideCount());
        limits.set("AddonOverrides", MLuaClientModelManager.overrideCount(service.owner()));
        limits.set("MaxOverrides", MLuaClientModelManager.MAX_OVERRIDES);
        limits.set("MaxOverridesPerAddon", MLuaClientModelManager.MAX_OVERRIDES_PER_OWNER);
        limits.set("MaxOverridesPerAddonPerEntity", MLuaClientModelManager.MAX_OVERRIDES_PER_ENTITY);
        limits.set("MaxPartNameLength", MLuaClientModelManager.MAX_PART_NAME_LENGTH);
        return limits;
    }

    void setCFrame(LuaValue entity, String part, LuaValue cframe) {
        service.requireModelClient();
        MLuaClientModelManager.setCFrame(service.resolveEntityScope(entity), part, MLuaCFrame.fromLua(cframe), service.owner());
    }

    void setOffset(LuaValue entity, String part, LuaValue offset) {
        service.requireModelClient();
        MLuaClientModelManager.setOffset(service.resolveEntityScope(entity), part, offset, service.owner());
    }

    void setScale(LuaValue entity, String part, LuaValue scale) {
        service.requireModelClient();
        MLuaClientModelManager.setScale(service.resolveEntityScope(entity), part, scale, service.owner());
    }

    void setVisible(LuaValue entity, String part, boolean visible) {
        service.requireModelClient();
        MLuaClientModelManager.setVisible(service.resolveEntityScope(entity), part, visible, service.owner());
    }

    void setPart(LuaValue entity, String part, LuaTable properties) {
        service.requireModelClient();
        MLuaEntityScope scope = service.resolveEntityScope(entity);
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

        // Validate the entire update before the manager applies any property.
        MLuaClientModelManager.setPartProperties(scope, part, cframe, offset, scale, visible, service.owner());
    }

    void clearPart(LuaValue entity, String part) {
        service.requireModelClient();
        MLuaClientModelManager.clearPart(service.resolveEntityScope(entity), part, service.owner());
    }

    void clearModel(LuaValue entity) {
        service.requireModelClient();
        MLuaClientModelManager.clearEntity(service.resolveEntityScope(entity), service.owner());
    }

    void clearAll() {
        service.requireModelClient();
        MLuaClientModelManager.clearOwned(service.owner());
    }

    LuaTable getModelState(LuaValue entity) {
        service.requireModelClient();
        LuaTable result = new LuaTable();
        MLuaEntityScope scope = service.resolveEntityScope(entity);
        for (Map.Entry<String, MLuaClientModelManager.PartState> entry
                : MLuaClientModelManager.getState(scope, service.owner()).entrySet()) {
            LuaTable part = new LuaTable();
            if (entry.getValue().cframe() != null) part.set("CFrame", entry.getValue().cframe().toLua());
            if (entry.getValue().offset() != null) part.set("Offset", copyVector3(entry.getValue().offset()));
            if (entry.getValue().scale() != null) part.set("Scale", copyVector3(entry.getValue().scale()));
            if (entry.getValue().visible() != null) part.set("Visible", LuaValue.valueOf(entry.getValue().visible()));
            result.set(entry.getKey(), part);
        }
        return result;
    }

    Iterable<String> getPartNames(LuaValue entity) {
        service.requireModelClient();
        return MLuaClientModelManager.getPartNames(service.resolveEntityScope(entity), service.owner());
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
}
