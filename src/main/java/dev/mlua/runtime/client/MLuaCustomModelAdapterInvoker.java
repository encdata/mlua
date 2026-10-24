package dev.mlua.runtime.client;

import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.runtime.client.MLuaModelAdapterRegistry.CustomAdapter;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import com.mojang.blaze3d.vertex.PoseStack;
import org.luaj.vm2.LuaInstructionBudget;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Executes Lua hooks for custom model adapters within a scoped render frame. */
final class MLuaCustomModelAdapterInvoker {
    private MLuaCustomModelAdapterInvoker() { }

    static boolean apply(CustomAdapter adapter, Object model, MLuaEntityScope scope, List<String> bones) {
        Entity entity = entityForScope(scope);
        LuaValue entityValue = entity == null ? LuaValue.NIL
            : dev.mlua.modules.players.classes.MLuaEntityWrapper.entityTable(entity, adapter.context.registration());
        MLuaRenderFrameScope frameScope = new MLuaRenderFrameScope();
        try {
            LuaValue modelValue = LuaValue.userdataOf(frameScope.view().scopedReference(model));
            LuaTable states = customBoneStates(scope, bones);
            invoke(adapter, adapter.apply,
                LuaValue.varargsOf(new LuaValue[]{modelValue, entityValue, states}), frameScope);
            return adapter.active;
        } finally {
            frameScope.close();
        }
    }

    static boolean applyPose(CustomAdapter adapter, Object model, String bone, PoseStack poseStack,
            MLuaEntityScope scope) {
        if (adapter == null || !adapter.active || adapter.applyPose == null || adapter.applyPose.isnil()
                || !adapter.bones.contains(bone)) return false;
        Entity entity = entityForScope(scope);
        LuaValue entityValue = entity == null ? LuaValue.NIL
            : dev.mlua.modules.players.classes.MLuaEntityWrapper.entityTable(entity, adapter.context.registration());
        MLuaRenderFrameScope frameScope = new MLuaRenderFrameScope();
        try {
            LuaValue modelValue = LuaValue.userdataOf(frameScope.view().scopedReference(model));
            LuaValue poseValue = LuaValue.userdataOf(frameScope.view().scopedReference(poseStack));
            LuaTable state = customBoneStates(scope, List.of(bone)).get(bone).checktable();
            Varargs result = invoke(adapter, adapter.applyPose,
                LuaValue.varargsOf(new LuaValue[]{modelValue, LuaValue.valueOf(bone), poseValue,
                    entityValue, state}), frameScope);
            return adapter.active && result.arg1().isboolean() && result.arg1().toboolean();
        } finally {
            frameScope.close();
        }
    }

    private static LuaTable customBoneStates(MLuaEntityScope scope, List<String> bones) {
        LuaTable result = new LuaTable();
        Map<String, MLuaCFrame> animationPoses = MLuaEntityModelRenderState.renderPose(scope);
        Map<String, MLuaClientModelManager.PartState> partStates =
            MLuaClientModelManager.getMergedPartStates(scope, bones);
        for (String bone : bones) {
            LuaTable state = new LuaTable();
            MLuaCFrame animation = animationPoses.get(bone);
            if (animation != null) state.set("Animation", animation.toLua());
            MLuaClientModelManager.PartState override =
                partStates.get(MLuaClientModelManager.normalizePartName(bone));
            if (override != null) {
                if (override.cframe() != null) state.set("CFrame", override.cframe().toLua());
                if (override.offset() != null) state.set("Offset", override.offset());
                if (override.scale() != null) state.set("Scale", override.scale());
                if (override.visible() != null) state.set("Visible", LuaValue.valueOf(override.visible()));
            }
            result.set(bone, state);
        }
        return result;
    }

    private static Entity entityForScope(MLuaEntityScope scope) {
        Minecraft minecraft = Minecraft.getInstance();
        if (scope == null || minecraft.level == null
                || !minecraft.level.dimension().location().toString().equals(scope.dimension())) return null;
        Entity entity = minecraft.level.getEntity(scope.entityId());
        return entity != null && entity.getUUID().equals(scope.uuid()) ? entity : null;
    }

    private static Varargs invoke(CustomAdapter adapter, LuaValue callback, Varargs args,
            MLuaRenderFrameScope frameScope) {
        if (!adapter.active) return LuaValue.NONE;
        try {
            try (MLuaRenderFrameScope.ScopeBinding ignored = frameScope.view().enter()) {
                return dev.mlua.core.engine.MLuaLuaBindings.withContext(adapter.context, () -> {
                    if (!adapter.active) return LuaValue.NONE;
                    if (adapter.context.globals() == null) return callback.invoke(args);
                    try (LuaInstructionBudget.Scope budget = adapter.context.globals()
                            .beginInstructionBudget(MLuaModelAdapterRegistry.MAX_RESOLVER_LUA_INSTRUCTIONS)) {
                        return callback.invoke(args);
                    }
                });
            }
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            MLuaModelAdapterRegistry.disableCustomAdapter(adapter, failure);
            return LuaValue.NONE;
        }
    }
}
