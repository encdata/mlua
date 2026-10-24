package dev.mlua.runtime.client;

import static dev.mlua.runtime.client.MLuaRenderHookRegistry.*;

import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.signal.MLuaConnection;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import java.util.List;
import java.util.function.Supplier;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Addon-owned Lua callbacks attached to Minecraft's client render points. */
public final class MLuaRenderCallbacks {
    public static final long MAX_LUA_INSTRUCTIONS_PER_CALLBACK = MLuaCallbackBudget.MAX_INSTRUCTIONS;
    private static final int DEFAULT_WORLD_RENDER_LIGHT = 15_728_880;
    private static final String DEFAULT_PASS_ID = "mlua:default";
    /** Bounds callback dispatch work even when many addons register render hooks. */
    public static final int MAX_CALLBACKS = MLuaRenderHookRegistry.MAX_CALLBACKS;
    /** Prevents one entity type, render stage, or item from monopolizing every frame. */
    public static final int MAX_CALLBACKS_PER_TARGET = MLuaRenderHookRegistry.MAX_CALLBACKS_PER_TARGET;
    /** Keeps one addon runtime from consuming the callback capacity needed by other addons. */
    public static final int MAX_CALLBACKS_PER_OWNER = MLuaRenderHookRegistry.MAX_CALLBACKS_PER_OWNER;
    private MLuaRenderCallbacks() { }

    /** Stores the latest client render-frame duration in seconds for render callbacks. */
    public static void setFrameDeltaTime(double deltaTime) {
        // Each frame replaces the previous sample, including an unavailable one.
        MLuaRenderFrameClock.publish(deltaTime);
    }

    public static MLuaConnection register(String entityId, LuaValue callback, MLuaContext context) {
        return register(entityId, DEFAULT_PASS_ID, callback, context);
    }

    public static MLuaConnection register(String entityId, String passId, LuaValue callback, MLuaContext context) {
        if (context == null) throw new LuaError("[MLua] Render callback registration needs its owning Lua context.");
        requireCallback(callback);
        String id = normalizeId(entityId);
        HookKey key = new HookKey(owner(context), normalizeId(passId));
        return registerTargetConnection(new HookTarget(HookKind.ENTITY_MODEL, id), key, callback, context,
            "entity pass '" + key.id() + "' for " + id);
    }

    public static boolean unregister(String entityId, MLuaContext context) {
        return unregister(entityId, DEFAULT_PASS_ID, context);
    }

    public static boolean unregister(String entityId, String passId, MLuaContext context) {
        if (context == null) return false;
        String id = normalizeId(entityId);
        HookKey key = new HookKey(owner(context), normalizeId(passId));
        return unregisterTarget(new HookTarget(HookKind.ENTITY_MODEL, id), key);
    }

    public static MLuaConnection registerEntityDraw(String entityType, String passId, LuaValue callback, MLuaContext context) {
        if (context == null) throw new LuaError("[MLua] Render callback registration needs its owning Lua context.");
        String id = normalizeId(entityType);
        HookKey key = new HookKey(owner(context), normalizeId(passId));
        return registerTargetConnection(new HookTarget(HookKind.ENTITY_DRAW, id), key, callback, context,
            "entity draw pass '" + key.id() + "' for " + id);
    }

    public static boolean unregisterEntityDraw(String entityType, String passId, MLuaContext context) {
        if (context == null) return false;
        String id = normalizeId(entityType);
        HookKey key = new HookKey(owner(context), normalizeId(passId));
        return unregisterTarget(new HookTarget(HookKind.ENTITY_DRAW, id), key);
    }

    public static MLuaConnection registerEntityPostDraw(String entityType, String passId, LuaValue callback,
                                               MLuaContext context) {
        if (context == null) throw new LuaError("[MLua] Render callback registration needs its owning Lua context.");
        String id = normalizeId(entityType);
        HookKey key = new HookKey(owner(context), normalizeId(passId));
        return registerTargetConnection(new HookTarget(HookKind.ENTITY_DRAW_POST, id), key, callback, context,
            "post-entity render pass '" + key.id() + "' for " + id);
    }

    public static boolean unregisterEntityPostDraw(String entityType, String passId, MLuaContext context) {
        if (context == null) return false;
        String id = normalizeId(entityType);
        HookKey key = new HookKey(owner(context), normalizeId(passId));
        return unregisterTarget(new HookTarget(HookKind.ENTITY_DRAW_POST, id), key);
    }

    public static void renderEntityDraw(Entity entity, Object renderer, Object poseStack, Object bufferSource,
            Float partialTick, int packedLight) {
        renderEntityDraw(entity, renderer, poseStack, bufferSource, partialTick, packedLight, null);
    }

    public static void renderEntityDraw(Entity entity, Object renderer, Object poseStack, Object bufferSource,
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

    public static boolean hasEntityDrawCallbacks(EntityType<?> entityType) {
        if (entityType == null) return false;
        var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        if (typeId == null) return false;
        HookBucket hooks = bucket(HookKind.ENTITY_DRAW, typeId.toString());
        return hooks != null && !hooks.isEmpty();
    }

    public static boolean hasEntityPostDrawCallbacks(EntityType<?> entityType) {
        if (entityType == null) return false;
        var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        if (typeId == null) return false;
        HookBucket hooks = bucket(HookKind.ENTITY_DRAW_POST, typeId.toString());
        return hooks != null && !hooks.isEmpty();
    }

    /** Runs after Minecraft's native entity renderer returns and before MLua attachments are submitted. */
    public static void renderEntityPostDraw(Entity entity, Object renderer, Object poseStack,
            Object bufferSource, Float partialTick, int packedLight, Object nativeContext) {
        renderEntityPostDraw(entity, renderer, poseStack, bufferSource, partialTick, packedLight,
            nativeContext, null);
    }

    /** Runs after Minecraft's entity renderer with an addon-adapted native model when available. */
    public static void renderEntityPostDraw(Entity entity, Object renderer, Object poseStack,
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

    public static MLuaConnection registerFirstPersonItem(String itemId, LuaValue callback, MLuaContext context) {
        return registerFirstPersonItem(itemId, DEFAULT_PASS_ID, callback, context);
    }

    public static MLuaConnection registerFirstPersonItem(String itemId, String passId, LuaValue callback, MLuaContext context) {
        if (context == null) throw new LuaError("[MLua] Render callback registration needs its owning Lua context.");
        String item = normalizeId(itemId);
        HookKey key = new HookKey(owner(context), normalizeId(passId));
        return registerTargetConnection(new HookTarget(HookKind.FIRST_PERSON_ITEM, item), key, callback, context,
            "first-person item pass '" + key.id() + "' for " + item);
    }

    public static boolean unregisterFirstPersonItem(String itemId, MLuaContext context) {
        return unregisterFirstPersonItem(itemId, DEFAULT_PASS_ID, context);
    }

    public static boolean unregisterFirstPersonItem(String itemId, String passId, MLuaContext context) {
        if (context == null) return false;
        String item = normalizeId(itemId);
        HookKey key = new HookKey(owner(context), normalizeId(passId));
        return unregisterTarget(new HookTarget(HookKind.FIRST_PERSON_ITEM, item), key);
    }

    public static MLuaConnection registerThirdPersonItem(String itemId, LuaValue callback, MLuaContext context) {
        return registerThirdPersonItem(itemId, DEFAULT_PASS_ID, callback, context);
    }

    public static MLuaConnection registerThirdPersonItem(String itemId, String passId, LuaValue callback, MLuaContext context) {
        if (context == null) throw new LuaError("[MLua] Render callback registration needs its owning Lua context.");
        String item = normalizeId(itemId);
        HookKey key = new HookKey(owner(context), normalizeId(passId));
        return registerTargetConnection(new HookTarget(HookKind.THIRD_PERSON_ITEM, item), key, callback, context,
            "third-person item pass '" + key.id() + "' for " + item);
    }

    public static boolean unregisterThirdPersonItem(String itemId, MLuaContext context) {
        return unregisterThirdPersonItem(itemId, DEFAULT_PASS_ID, context);
    }

    public static boolean unregisterThirdPersonItem(String itemId, String passId, MLuaContext context) {
        if (context == null) return false;
        String item = normalizeId(itemId);
        HookKey key = new HookKey(owner(context), normalizeId(passId));
        return unregisterTarget(new HookTarget(HookKind.THIRD_PERSON_ITEM, item), key);
    }

    public static void renderThirdPersonItem(LivingEntity entity, ItemStack stack,
            HumanoidArm arm, ItemDisplayContext displayContext,
            Object poseStack, Object bufferSource, int packedLight) {
        renderThirdPersonItem(entity, stack, arm, displayContext, poseStack, bufferSource, packedLight, null);
    }

    public static void renderThirdPersonItem(LivingEntity entity, ItemStack stack,
            HumanoidArm arm, ItemDisplayContext displayContext,
            Object poseStack, Object bufferSource, int packedLight, Float partialTick) {
        if (entity == null || stack == null || stack.isEmpty()) return;
        var itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemKey == null) return;
        HookBucket hooks = bucket(HookKind.THIRD_PERSON_ITEM, itemKey.toString());
        Float framePartialTick = partialTick == null ? currentClientPartialTick()
            : MLuaRenderPartialTickResolver.normalize(partialTick);
        MLuaClientRenderManager.renderHeldItemModels(
            MLuaClientRenderManager.RenderTarget.THIRD_PERSON_ITEM, itemKey.toString(), poseStack,
            bufferSource, packedLight, framePartialTick == null ? 1.0F : framePartialTick);
        if (hooks == null) return;
        List<Hook> renderHooks = renderHooks(hooks);
        if (renderHooks.isEmpty()) return;
        String hand = entity.getMainArm() == arm ? "MAIN_HAND" : "OFF_HAND";
        for (Hook hook : renderHooks) {
            invokeSafely(hook, () -> {
                LuaValue entityValue = MLuaEntityWrapper.entityTable(entity, hook.context.registration());
                return LuaValue.varargsOf(entityValue, renderContext(hook, "ThirdPersonItem",
                    poseStack, bufferSource, null, framePartialTick == null ? 0.0F : framePartialTick,
                    framePartialTick != null, packedLight, 0, null,
                    "Entity", entityValue,
                    "ItemStack", userdata(stack), "Hand", LuaValue.valueOf(hand),
                    "Arm", LuaValue.valueOf(arm.name()),
                    "DisplayContext", LuaValue.valueOf(displayContext.name())));
            });
        }
    }

    public static boolean hasThirdPersonItemCallbacks(ItemStack stack) {
        String itemId = heldItemId(stack);
        if (itemId == null) return false;
        HookBucket hooks = bucket(HookKind.THIRD_PERSON_ITEM, itemId);
        return hooks != null && !hooks.isEmpty();
    }

    /** True when either Lua passes or target-bound model objects need this held-item hook. */
    public static boolean hasThirdPersonItemRenderers(ItemStack stack) {
        String itemId = heldItemId(stack);
        if (itemId == null) return false;
        HookBucket hooks = bucket(HookKind.THIRD_PERSON_ITEM, itemId);
        return (hooks != null && !hooks.isEmpty()) || MLuaClientRenderManager.hasHeldItemModels(
            MLuaClientRenderManager.RenderTarget.THIRD_PERSON_ITEM, itemId);
    }

    private static String heldItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemKey == null ? null : itemKey.toString();
    }

    public static void renderFirstPersonItem(AbstractClientPlayer player,
            InteractionHand hand, ItemStack stack, Object poseStack, Object bufferSource,
            float partialTick, float pitch, float swingProgress, float equipProgress, int packedLight) {
        renderFirstPersonItem(player, hand, stack, poseStack, bufferSource, Float.valueOf(partialTick),
            pitch, swingProgress, equipProgress, packedLight);
    }

    /** Preserves whether the render hook actually had an interpolation sample. */
    public static void renderFirstPersonItem(AbstractClientPlayer player,
            InteractionHand hand, ItemStack stack, Object poseStack, Object bufferSource,
            Float partialTick, float pitch, float swingProgress, float equipProgress, int packedLight) {
        if (player == null || stack == null || stack.isEmpty()) return;
        var itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemKey == null) return;
        HookBucket hooks = bucket(HookKind.FIRST_PERSON_ITEM, itemKey.toString());
        Float validatedPartialTick = partialTick == null ? null : MLuaRenderPartialTickResolver.normalize(partialTick);
        MLuaClientRenderManager.renderHeldItemModels(
            MLuaClientRenderManager.RenderTarget.FIRST_PERSON_ITEM, itemKey.toString(), poseStack,
            bufferSource, packedLight, validatedPartialTick == null ? 1.0F : validatedPartialTick);
        if (hooks == null) return;
        List<Hook> renderHooks = renderHooks(hooks);
        if (renderHooks.isEmpty()) return;
        float validatedPitch = finiteOrZero(pitch);
        float validatedSwingProgress = unitOrZero(swingProgress);
        float validatedEquipProgress = unitOrZero(equipProgress);
        HumanoidArm arm = player.getMainArm();
        if (hand == InteractionHand.OFF_HAND) {
            arm = arm == HumanoidArm.LEFT
                ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
        }
        final HumanoidArm renderedArm = arm;
        ItemDisplayContext displayContext = renderedArm == HumanoidArm.LEFT
            ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND
            : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        for (Hook hook : renderHooks) {
            invokeSafely(hook, () -> {
                LuaValue playerValue = MLuaEntityWrapper.entityTable(player, hook.context.registration());
                return LuaValue.varargsOf(playerValue, renderContext(hook, "FirstPersonItem",
                    poseStack, bufferSource, null, validatedPartialTick == null ? 0.0F : validatedPartialTick,
                    validatedPartialTick != null, packedLight, 0, null,
                    "Entity", playerValue,
                    "Player", playerValue,
                    "ItemStack", userdata(stack), "Hand", LuaValue.valueOf(hand.name()),
                    "Arm", LuaValue.valueOf(renderedArm.name()),
                    "DisplayContext", LuaValue.valueOf(displayContext.name()),
                    "Pitch", LuaValue.valueOf(validatedPitch), "SwingProgress", LuaValue.valueOf(validatedSwingProgress),
                    "EquipProgress", LuaValue.valueOf(validatedEquipProgress)));
            });
        }
    }

    public static MLuaConnection registerWorld(LuaValue callback, MLuaContext context) {
        return registerWorld("mlua:default", "AFTER_ENTITIES", callback, context);
    }

    public static MLuaConnection registerWorld(String stage, LuaValue callback, MLuaContext context) {
        return registerWorld("mlua:default", stage, callback, context);
    }

    public static MLuaConnection registerWorld(String id, String stage, LuaValue callback, MLuaContext context) {
        requireCallback(callback);
        if (context == null) throw new LuaError("[MLua] Render callback registration needs its owning Lua context.");
        String normalized;
        try {
            normalized = dev.mlua.loaderapi.ClientBridge.normalizeWorldRenderStage(stage);
        } catch (IllegalArgumentException invalidStage) {
            throw new LuaError("[MLua] " + invalidStage.getMessage());
        }
        String key = normalizeId(id);
        return MLuaRenderHookRegistry.registerWorldPass(key, normalized, callback, context);
    }

    public static boolean unregisterWorld(String stage, MLuaContext context) {
        String normalized = dev.mlua.loaderapi.ClientBridge.normalizeWorldRenderStage(stage);
        HookKey key = new HookKey(owner(context), DEFAULT_PASS_ID);
        return MLuaRenderHookRegistry.unregisterWorldStage(normalized, key);
    }

    public static boolean unregisterWorld(MLuaContext context) {
        return unregisterWorld("AFTER_ENTITIES", context);
    }

    public static boolean unregisterWorldPass(String id, MLuaContext context) {
        return MLuaRenderHookRegistry.unregisterWorldPass(new HookKey(owner(context), normalizeId(id)));
    }

    /** Registers a callback at the loader's native custom-geometry submission point. */
    public static MLuaConnection registerWorldGeometry(String id, LuaValue callback, MLuaContext context) {
        if (context == null) throw new LuaError("[MLua] Render callback registration needs its owning Lua context.");
        String key = normalizeId(id);
        HookKey hookKey = new HookKey(owner(context), key);
        return registerTargetConnection(WORLD_GEOMETRY_TARGET, hookKey, callback, context,
            "world geometry pass '" + key + "'");
    }

    public static boolean unregisterWorldGeometry(String id, MLuaContext context) {
        if (context == null) return false;
        return unregisterTarget(WORLD_GEOMETRY_TARGET,
            new HookKey(owner(context), normalizeId(id)));
    }

    public static MLuaConnection registerHud(LuaValue callback, MLuaContext context) {
        return registerHud(DEFAULT_PASS_ID, callback, context);
    }

    public static MLuaConnection registerHud(String id, LuaValue callback, MLuaContext context) {
        if (context == null) throw new LuaError("[MLua] Render callback registration needs its owning Lua context.");
        String key = normalizeId(id);
        HookKey hookKey = new HookKey(owner(context), key);
        return registerTargetConnection(HUD_TARGET, hookKey, callback, context, "HUD pass '" + key + "'");
    }

    public static boolean unregisterHud(MLuaContext context) {
        return unregisterHudPass(DEFAULT_PASS_ID, context);
    }

    public static boolean unregisterHudPass(String id, MLuaContext context) {
        return context != null && unregisterTarget(HUD_TARGET, new HookKey(owner(context), normalizeId(id)));
    }

    public static void render(LivingEntity entity, Object model, Object poseStack, Object bufferSource,
                              float partialTick, int packedLight, int packedOverlay) {
        render(entity, model, poseStack, bufferSource, partialTick, packedLight, packedOverlay, true);
    }

    public static void render(LivingEntity entity, Object model, Object poseStack, Object bufferSource,
                              float partialTick, int packedLight, int packedOverlay, boolean hasPartialTick) {
        render(entity, model, poseStack, bufferSource, partialTick, packedLight, packedOverlay,
            hasPartialTick, null);
    }

    /** Supplies the native render-state object on versions that use state-based entity rendering. */
    public static void render(LivingEntity entity, Object model, Object poseStack, Object bufferSource,
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

    public static void renderWorld(String stage, Object nativeContext, Object poseStack, Object bufferSource, Object camera) {
        String normalizedStage = dev.mlua.loaderapi.ClientBridge.normalizeWorldRenderStage(stage);
        HookBucket hooks = bucket(HookKind.WORLD, normalizedStage);
        if (hooks == null) return;
        List<Hook> renderHooks = renderHooks(hooks);
        if (renderHooks.isEmpty()) return;
        Float partialTick = readPartialTick(nativeContext);
        if (partialTick == null) partialTick = currentClientPartialTick();
        Float renderPartialTick = partialTick;
        for (Hook hook : renderHooks) {
            invokeSafely(hook, () -> renderContext(hook, "World", poseStack, bufferSource, null,
                renderPartialTick == null ? 0.0F : renderPartialTick, renderPartialTick != null,
                DEFAULT_WORLD_RENDER_LIGHT, 0, nativeContext,
                "Stage", LuaValue.valueOf(normalizedStage), "Camera", userdata(camera)));
        }
    }

    /** Runs callbacks while the loader accepts custom world geometry for the current frame. */
    public static void renderWorldGeometry(Object nativeContext, Object poseStack, Object renderSource, Object camera) {
        HookBucket hooks = bucket(HookKind.WORLD_GEOMETRY, "");
        if (hooks == null) return;
        List<Hook> renderHooks = renderHooks(hooks);
        if (renderHooks.isEmpty()) return;
        Float partialTick = readPartialTick(nativeContext);
        if (partialTick == null) partialTick = currentClientPartialTick();
        Float renderPartialTick = partialTick;
        for (Hook hook : renderHooks) {
            invokeSafely(hook, () -> renderContext(hook, "WorldGeometry", poseStack, renderSource, null,
                renderPartialTick == null ? 0.0F : renderPartialTick, renderPartialTick != null,
                DEFAULT_WORLD_RENDER_LIGHT, 0, nativeContext,
                "Stage", LuaValue.valueOf("CUSTOM_GEOMETRY"), "Camera", userdata(camera)));
        }
    }

    /** Reads the render fraction from loader-specific contexts and delta trackers. */
    public static Float readPartialTick(Object context) {
        return MLuaRenderPartialTickResolver.read(context);
    }

    public static Float currentClientPartialTick() {
        return MLuaRenderPartialTickResolver.currentClient();
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }

    private static float unitOrZero(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 0.0F;
    }

    public static void renderHud(Object graphics, int width, int height) {
        HookBucket hud = bucket(HookKind.HUD, "");
        if (hud == null) return;
        List<Hook> renderHooks = renderHooks(hud);
        if (renderHooks.isEmpty()) return;
        for (Hook hook : renderHooks) {
            invokeSafely(hook, () -> renderContext(hook, "Hud", null, null, null, 0.0F, false, 0, 0, null,
                "Graphics", userdata(graphics), "Width", LuaValue.valueOf(width), "Height", LuaValue.valueOf(height)));
        }
    }

    public static void clearOwned(Object session, String modId) {
        MLuaRenderHookRegistry.clearOwned(session, modId);
    }

    public static void clearRuntime(Object session) {
        if (session == null) return;
        MLuaRenderHookRegistry.clearRuntime(session);
        // DeltaTime belongs to the active client runtime's render frames. Do not
        // let a freshly loaded runtime observe the previous session's last sample.
        resetFrameTime();
    }

    /** Resets per-frame timing when the client leaves a world without unregistering script hooks. */
    public static void resetFrameTime() {
        MLuaRenderFrameClock.reset();
    }

    /** Includes Lua wrapper and frame-context construction in the per-addon failure boundary. */
    private static void invokeSafely(Hook hook, Supplier<Varargs> argumentFactory) {
        if (hook == null || !hook.active || hook.failed) return;
        if (!MLuaClientRenderThread.require("Lua render callback setup")) return;
        try {
            Varargs args = argumentFactory.get();
            if (args == null) throw new IllegalStateException("Render callback argument factory returned null");
            invoke(hook, args);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            disableHook(hook, failure);
        }
    }

    private static void invoke(Hook hook, Varargs invocationArgs) {
        MLuaRenderFrameContexts.Binding frameBinding = findFrameScope(invocationArgs);
        if (!hook.active || hook.failed) {
            closeFrameScope(frameBinding);
            return;
        }
        if (!MLuaClientRenderThread.require("Lua render callback")) {
            closeFrameScope(frameBinding);
            return;
        }
        MLuaContext context = hook.context;
        MLuaRenderFrameScope.ScopeBinding activeFrame = null;
        MLuaRenderPoseScope callbackPoseBoundary = null;
        try {
            if (frameBinding != null) {
                activeFrame = frameBinding.scope().view().enter();
                PoseStack callbackPose = framePoseStack(frameBinding);
                callbackPoseBoundary = MLuaRenderPoseScope.open(callbackPose);
            }
            MLuaLuaBindings.withContext(context, () -> {
                if (!hook.active || hook.failed) return null;
                return MLuaCallbackBudget.run(context, () -> {
                    hook.callback.invoke(invocationArgs);
                    return null;
                });
            });
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            disableHook(hook, failure);
        } finally {
            try {
                if (callbackPoseBoundary != null) callbackPoseBoundary.close();
            } finally {
                try {
                    if (activeFrame != null) activeFrame.close();
                } finally {
                    closeFrameScope(frameBinding);
                }
            }
        }
    }

    private static PoseStack framePoseStack(MLuaRenderFrameContexts.Binding binding) {
        if (binding == null) return null;
        LuaValue value = binding.context().get("PoseStack");
        if (value == null || !value.isuserdata(MLuaRenderFrameScope.MLuaScopedRenderObject.class)) return null;
        Object nativePose = ((MLuaRenderFrameScope.MLuaScopedRenderObject) value.touserdata(
            MLuaRenderFrameScope.MLuaScopedRenderObject.class)).unwrap();
        return nativePose instanceof PoseStack pose ? pose : null;
    }

    /** Disables a failing hook and releases its closure from the target registry. */
    private static void disableHook(Hook hook, Throwable failure) {
        hook.failed = true;
        MLuaRenderHookRegistry.removeFailed(hook);
        MLuaLogger.LOGGER.error("[MLua] Disabled render callback '{}' for addon '{}' at {} after an error",
            hook.id, hook.key.owner().modId(), hook.target, failure);
    }

    private static MLuaRenderFrameContexts.Binding findFrameScope(Varargs args) {
        return MLuaRenderFrameContexts.find(args);
    }

    private static void closeFrameScope(MLuaRenderFrameContexts.Binding binding) {
        if (binding == null) return;
        MLuaRenderFrameContexts.remove(binding);
        binding.scope().close();
    }

    /** Returns true only while this exact Lua RenderContext is inside its creating callback. */
    public static boolean isRenderContextActive(LuaValue contextValue) {
        if (contextValue == null || !contextValue.istable()) return false;
        MLuaRenderFrameScope scope = MLuaRenderFrameContexts.get(contextValue.checktable());
        return scope != null && scope.view().isActive();
    }

    private static LuaValue renderContext(Hook hook, String target, Object poseStack, Object bufferSource,
            Object model, float partialTick, boolean hasPartialTick, int packedLight, int packedOverlay,
            Object nativeContext, Object... additionalFields) {
        return MLuaRenderFrameContextFactory.create(hook.context, hook.id,
            failure -> disableHook(hook, failure), target, poseStack, bufferSource, model, partialTick,
            hasPartialTick, packedLight, packedOverlay, nativeContext, additionalFields);
    }

    /** Called by the registered RenderContext:DrawObject class method. */
    public static LuaValue drawObject(MLuaRegistration registration, LuaValue context,
            LuaValue handle, LuaValue transformValue) {
        return MLuaRenderFrameContextFactory.drawObject(registration, context, handle, transformValue);
    }

    /** Runs a Lua draw operation inside a native pose-stack scope restored to its entry depth. */
    public static Varargs withPose(LuaValue contextValue, LuaValue callback) {
        return MLuaRenderFrameContextFactory.withPose(contextValue, callback);
    }

    private static LuaValue userdata(Object value) {
        return value == null ? LuaValue.NIL : LuaValue.userdataOf(value);
    }

    public static int registeredCallbackCount() {
        return MLuaRenderHookRegistry.registeredCallbackCount();
    }

    public static int registeredCallbackCount(MLuaContext context) {
        return MLuaRenderHookRegistry.registeredCallbackCount(context);
    }

}
