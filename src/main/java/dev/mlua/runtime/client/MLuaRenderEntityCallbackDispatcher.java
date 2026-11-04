package dev.mlua.runtime.client;

import static dev.mlua.runtime.client.MLuaRenderHookRegistry.*;
import static dev.mlua.runtime.client.MLuaRenderCallbackInvoker.*;

import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import org.luaj.vm2.LuaValue;

/** Owns entity draw and living-model callback dispatch for the current render frame. */
final class MLuaRenderEntityCallbackDispatcher {
    private MLuaRenderEntityCallbackDispatcher() { }

    static void renderEntityDraw(Entity entity, Object renderer, Object poseStack, Object bufferSource,
            Float partialTick, int packedLight) {
        renderEntityDraw(entity, renderer, poseStack, bufferSource, partialTick, packedLight, null);
    }

    static void renderEntityDraw(Entity entity, Object renderer, Object poseStack, Object bufferSource,
            Float partialTick, int packedLight, Object nativeContext) {
        if (entity == null) return;
        var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (typeId == null) return;
        HookBucket hooks = bucket(HookKind.ENTITY_DRAW, typeId.toString());
        if (hooks == null || hooks.isEmpty()) return;
        List<Hook> renderHooks = renderHooks(hooks);
        if (renderHooks.isEmpty()) return;
        Float validatedPartialTick = partialTick == null ? null : MLuaRenderPartialTickResolver.normalize(partialTick);
        for (Hook hook : renderHooks) {
            invokeSafely(hook, () -> {
                LuaValue entityValue = MLuaEntityWrapper.entityTable(entity, hook.context.registration());
                return LuaValue.varargsOf(entityValue, renderContext(hook, "Entity", poseStack, bufferSource,
                    null, validatedPartialTick == null ? 0.0F : validatedPartialTick,
                    validatedPartialTick != null, packedLight, 0, nativeContext,
                    "Entity", entityValue,
                    "Renderer", userdata(renderer)));
            });
        }
    }

    static boolean hasEntityDrawCallbacks(EntityType<?> entityType) {
        if (entityType == null) return false;
        var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        if (typeId == null) return false;
        HookBucket hooks = bucket(HookKind.ENTITY_DRAW, typeId.toString());
        return hooks != null && !hooks.isEmpty();
    }

    static boolean hasEntityPostDrawCallbacks(EntityType<?> entityType) {
        if (entityType == null) return false;
        var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        if (typeId == null) return false;
        HookBucket hooks = bucket(HookKind.ENTITY_DRAW_POST, typeId.toString());
        return hooks != null && !hooks.isEmpty();
    }

    static void renderEntityPostDraw(Entity entity, Object renderer, Object poseStack,
            Object bufferSource, Float partialTick, int packedLight, Object nativeContext) {
        renderEntityPostDraw(entity, renderer, poseStack, bufferSource, partialTick, packedLight,
            nativeContext, null);
    }

    static void renderEntityPostDraw(Entity entity, Object renderer, Object poseStack,
            Object bufferSource, Float partialTick, int packedLight, Object nativeContext, Object model) {
        if (entity == null) return;
        var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (typeId == null) return;
        HookBucket hooks = bucket(HookKind.ENTITY_DRAW_POST, typeId.toString());
        if (hooks == null || hooks.isEmpty()) return;
        List<Hook> renderHooks = renderHooks(hooks);
        if (renderHooks.isEmpty()) return;
        Float validatedPartialTick = partialTick == null ? null : MLuaRenderPartialTickResolver.normalize(partialTick);
        for (Hook hook : renderHooks) {
            invokeSafely(hook, () -> {
                LuaValue entityValue = MLuaEntityWrapper.entityTable(entity, hook.context.registration());
                return LuaValue.varargsOf(entityValue, renderContext(hook, "EntityPostRender", poseStack, bufferSource,
                    model, validatedPartialTick == null ? 0.0F : validatedPartialTick,
                    validatedPartialTick != null, packedLight, 0, nativeContext,
                    "Entity", entityValue,
                    "Renderer", userdata(renderer)));
            });
        }
    }

    static void render(LivingEntity entity, Object model, Object poseStack, Object bufferSource,
            float partialTick, int packedLight, int packedOverlay) {
        render(entity, model, poseStack, bufferSource, partialTick, packedLight, packedOverlay, true);
    }

    static void render(LivingEntity entity, Object model, Object poseStack, Object bufferSource,
            float partialTick, int packedLight, int packedOverlay, boolean hasPartialTick) {
        render(entity, model, poseStack, bufferSource, partialTick, packedLight, packedOverlay,
            hasPartialTick, null);
    }

    /** Supplies the native render-state object on versions that use state-based entity rendering. */
    static void render(LivingEntity entity, Object model, Object poseStack, Object bufferSource,
            float partialTick, int packedLight, int packedOverlay, boolean hasPartialTick,
            Object renderState) {
        if (entity == null) return;
        var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (typeId == null) return;
        HookBucket registered = bucket(HookKind.ENTITY_MODEL, typeId.toString());
        if (registered == null || registered.isEmpty()) return;
        List<Hook> renderHooks = renderHooks(registered);
        if (renderHooks.isEmpty()) return;
        Float validatedPartialTick = MLuaRenderPartialTickResolver.normalize(partialTick);
        boolean partialTickAvailable = hasPartialTick && validatedPartialTick != null;
        for (Hook hook : renderHooks) {
            invokeSafely(hook, () -> {
                LuaValue entityValue = MLuaEntityWrapper.entityTable(entity, hook.context.registration());
                return LuaValue.varargsOf(entityValue, renderContext(hook, "EntityModel", poseStack,
                    bufferSource, model, partialTickAvailable ? validatedPartialTick : 0.0F, partialTickAvailable,
                    packedLight, packedOverlay, null,
                    "Entity", entityValue,
                    "RenderState", userdata(renderState)));
            });
        }
    }
}
