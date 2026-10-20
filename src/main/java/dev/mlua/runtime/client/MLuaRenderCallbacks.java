package dev.mlua.runtime.client;

import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.signal.MLuaConnection;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.loaderapi.FrameClock;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.LuaTable;

/** Addon-owned Lua callbacks attached to Minecraft's client render points. */
public final class MLuaRenderCallbacks {
    public static final long MAX_LUA_INSTRUCTIONS_PER_CALLBACK = MLuaCallbackBudget.MAX_INSTRUCTIONS;
    private static final int DEFAULT_WORLD_RENDER_LIGHT = 15_728_880;
    private static volatile RenderFrameTime frameTime = RenderFrameTime.EMPTY;
    private static final String DEFAULT_PASS_ID = "mlua:default";
    /** Bounds callback dispatch work even when many addons register render hooks. */
    public static final int MAX_CALLBACKS = 4096;
    /** Prevents one entity type, render stage, or item from monopolizing every frame. */
    public static final int MAX_CALLBACKS_PER_TARGET = 256;
    /** Keeps one addon runtime from consuming the callback capacity needed by other addons. */
    public static final int MAX_CALLBACKS_PER_OWNER = 512;
    private static final AtomicLong NEXT_REGISTRATION_ORDER = new AtomicLong();
    private static final AtomicInteger REGISTERED_CALLBACKS = new AtomicInteger();
    private static final Object CALLBACK_LIMIT_LOCK = new Object();
    /** Serializes callback installation with runtime/addon teardown. */
    private static final Object HOOK_LIFECYCLE_LOCK = new Object();
    private static final Map<Owner, Integer> REGISTERED_CALLBACKS_BY_OWNER = new HashMap<>();
    /** Frame lifetime is tracked outside the mutable Lua-facing context table. */
    private static final Map<LuaTable, MLuaRenderFrameScope> FRAME_SCOPES = new WeakHashMap<>();
    /** One registry owns every render hook; the target kind prevents unrelated hooks colliding. */
    private static final ConcurrentMap<HookTarget, HookBucket> HOOKS = new ConcurrentHashMap<>();
    private static final Object WORLD_REGISTRATION_LOCK = new Object();
    private static final HookTarget HUD_TARGET = new HookTarget(HookKind.HUD, "");
    private static final HookTarget WORLD_GEOMETRY_TARGET = new HookTarget(HookKind.WORLD_GEOMETRY, "");
    private static final Class<?> RENDER_COLLECTOR_CLASS = findOptionalClass(
        "net.minecraft.client.renderer.SubmitNodeCollector");
    private static final Class<?> MULTI_BUFFER_SOURCE_CLASS = findOptionalClass(
        "net.minecraft.client.renderer.MultiBufferSource");
    private static final Class<?> ENTITY_RENDER_STATE_CLASS = findOptionalClass(
        "net.minecraft.client.renderer.entity.state.EntityRenderState");
    private static final ClassValue<PartialTickAccess> PARTIAL_TICK_ACCESS = new ClassValue<>() {
        @Override protected PartialTickAccess computeValue(Class<?> type) {
            return new PartialTickAccess(
                findNoArg(type, "getPartialTick", "partialTick", "getPartialTickTime", "getTickDelta", "tickDelta", "getFrameTime"),
                findNoArg(type, "tickCounter", "getTickCounter", "deltaTracker", "getDeltaTracker", "timer", "getTimer"),
                findNoArg(type, "getGameTimeDeltaPartialTick", "getTickDelta", "getPartialTick", "getPartialTickTime"),
                findBooleanArg(type, "getGameTimeDeltaPartialTick", "getTickDelta"));
        }
    };
    private static final ClassValue<PoseStackDepthAccess> POSE_STACK_DEPTH_ACCESS = new ClassValue<>() {
        @Override protected PoseStackDepthAccess computeValue(Class<?> type) {
            for (String name : List.of("lastIndex", "poses", "poseStack")) {
                for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                    try {
                        Field field = current.getDeclaredField(name);
                        boolean index = field.getType() == int.class;
                        if (!index && !Collection.class.isAssignableFrom(field.getType())) continue;
                        field.setAccessible(true);
                        return new PoseStackDepthAccess(field, index);
                    } catch (ReflectiveOperationException | RuntimeException ignored) { }
                }
            }
            return PoseStackDepthAccess.UNAVAILABLE;
        }
    };
    private static final java.util.Set<Class<?>> WARNED_POSE_STACK_DEPTH = ConcurrentHashMap.newKeySet();

    private MLuaRenderCallbacks() { }

    /** Stores the latest client render-frame duration in seconds for render callbacks. */
    public static void setFrameDeltaTime(double deltaTime) {
        double bounded = FrameClock.boundedSeconds(deltaTime);
        // Treat this as the latest sample, including the first sample from a
        // newly initialized frame clock. Retaining an older positive value would
        // make contexts claim to have a current interval before one exists.
        frameTime = bounded > 0.0D
            ? new RenderFrameTime(bounded, true)
            : RenderFrameTime.EMPTY;
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
        Float validatedPartialTick = partialTick == null ? null : finitePartialTick(partialTick);
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
        Float validatedPartialTick = partialTick == null ? null : finitePartialTick(partialTick);
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
        Float framePartialTick = partialTick == null ? currentClientPartialTick() : finitePartialTick(partialTick);
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
        Float validatedPartialTick = partialTick == null ? null : finitePartialTick(partialTick);
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
        HookKey hookKey = new HookKey(owner(context), key);
        HookTarget destination = new HookTarget(HookKind.WORLD, normalized);
        synchronized (WORLD_REGISTRATION_LOCK) {
            synchronized (HOOK_LIFECYCLE_LOCK) {
                HookBucket targetBucket = HOOKS.get(destination);
                boolean alreadyAtDestination = targetBucket != null && targetBucket.contains(hookKey);
                if (!alreadyAtDestination && targetBucket != null
                    && targetBucket.size() >= MAX_CALLBACKS_PER_TARGET) {
                    throw new LuaError("[MLua] Render target 'world stage " + normalized + "' already has the maximum of "
                        + MAX_CALLBACKS_PER_TARGET + " callbacks");
                }

                List<PreviousWorldHook> previous = HOOKS.entrySet().stream()
                    .filter(entry -> entry.getKey().kind() == HookKind.WORLD
                        && !entry.getKey().equals(destination))
                    .map(entry -> new PreviousWorldHook(entry.getKey(), entry.getValue().get(hookKey)))
                    .filter(entry -> entry.hook() != null)
                    .toList();
                previous.forEach(entry -> unregisterTarget(entry.target(), hookKey));
                try {
                    Hook hook = registerTarget(destination, hookKey, callback, context,
                        "world pass '" + key + "' at " + normalized);
                    return connection(destination, hook);
                } catch (RuntimeException failure) {
                    for (PreviousWorldHook entry : previous) {
                        Hook old = entry.hook();
                        registerTarget(entry.target(), hookKey, old.callback, old.context, old.target,
                            old.registrationOrder);
                    }
                    throw failure;
                }
            }
        }
    }

    public static boolean unregisterWorld(String stage, MLuaContext context) {
        String normalized = dev.mlua.loaderapi.ClientBridge.normalizeWorldRenderStage(stage);
        HookKey key = new HookKey(owner(context), DEFAULT_PASS_ID);
        synchronized (WORLD_REGISTRATION_LOCK) {
            return unregisterTarget(new HookTarget(HookKind.WORLD, normalized), key);
        }
    }

    public static boolean unregisterWorld(MLuaContext context) {
        return unregisterWorld("AFTER_ENTITIES", context);
    }

    public static boolean unregisterWorldPass(String id, MLuaContext context) {
        HookKey key = new HookKey(owner(context), normalizeId(id));
        synchronized (WORLD_REGISTRATION_LOCK) {
            boolean removed = false;
            for (HookTarget target : HOOKS.keySet()) {
                if (target.kind() == HookKind.WORLD) removed |= unregisterTarget(target, key);
            }
            return removed;
        }
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
        Float validatedPartialTick = finitePartialTick(partialTick);
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

    static Float readPartialTick(Object context) {
        if (context == null) return null;
        PartialTickAccess access = PARTIAL_TICK_ACCESS.get(context.getClass());
        Object direct = invoke(context, access.direct());
        Float value = finitePartialTick(direct);
        if (value != null) return value;
        Object source = direct != null ? direct : invoke(context, access.tracker());
        if (source == null) return null;
        PartialTickAccess sourceAccess = PARTIAL_TICK_ACCESS.get(source.getClass());
        value = finitePartialTick(invoke(source, sourceAccess.tickNoArg()));
        if (value != null) return value;
        value = finitePartialTick(invokeBoolean(source, sourceAccess.tickWithBoolean()));
        return value;
    }

    public static Float currentClientPartialTick() {
        return readPartialTick(Minecraft.getInstance());
    }

    private static Float finitePartialTick(Object value) {
        if (!(value instanceof Number number)) return null;
        float partialTick = number.floatValue();
        return Float.isFinite(partialTick) ? Math.max(0.0F, Math.min(1.0F, partialTick)) : null;
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }

    private static float unitOrZero(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 0.0F;
    }

    private static Method findNoArg(Class<?> type, String... names) {
        for (String name : names) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) { }
        }
        return null;
    }

    private static Method findBooleanArg(Class<?> type, String... names) {
        for (String name : names) {
            try {
                return type.getMethod(name, boolean.class);
            } catch (NoSuchMethodException ignored) { }
        }
        return null;
    }

    private static Object invoke(Object target, Method method) {
        if (target == null || method == null) return null;
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object invokeBoolean(Object target, Method method) {
        if (target == null || method == null) return null;
        try {
            return method.invoke(target, false);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private record PartialTickAccess(Method direct, Method tracker, Method tickNoArg, Method tickWithBoolean) { }
    private record RenderFrameTime(double deltaTime, boolean available) {
        private static final RenderFrameTime EMPTY = new RenderFrameTime(0.0D, false);
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
        if (session == null || modId == null || modId.isBlank()) return;
        java.util.function.Predicate<Owner> matches = owner -> owner.session() == session && owner.modId().equals(modId);
        synchronized (WORLD_REGISTRATION_LOCK) {
            synchronized (HOOK_LIFECYCLE_LOCK) {
                removeOwned(matches);
            }
        }
    }

    public static void clearRuntime(Object session) {
        if (session == null) return;
        java.util.function.Predicate<Owner> matches = owner -> owner.session() == session;
        synchronized (WORLD_REGISTRATION_LOCK) {
            synchronized (HOOK_LIFECYCLE_LOCK) {
                removeOwned(matches);
            }
        }
        // DeltaTime belongs to the active client runtime's render frames. Do not
        // let a freshly loaded runtime observe the previous session's last sample.
        resetFrameTime();
    }

    /** Resets per-frame timing when the client leaves a world without unregistering script hooks. */
    public static void resetFrameTime() {
        frameTime = RenderFrameTime.EMPTY;
    }

    private static void removeOwned(java.util.function.Predicate<Owner> predicate) {
        HOOKS.forEach((target, ignored) -> HOOKS.computeIfPresent(target, (key, bucket) -> {
            bucket.removeOwned(predicate);
            return bucket.isEmpty() ? null : bucket;
        }));
    }

    private static String normalizeId(String id) {
        ResourceLocation location = MLuaResourceLocations.tryParseOrNull(id);
        if (location == null) {
            throw new LuaError("[MLua] Render registration id must be a valid resource location (namespace:path).");
        }
        return location.toString();
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
        FrameContextBinding frameBinding = findFrameScope(invocationArgs);
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
        PoseStackBoundary callbackPoseBoundary = null;
        try {
            if (frameBinding != null) {
                activeFrame = frameBinding.scope().view().enter();
                PoseStack callbackPose = framePoseStack(frameBinding);
                if (callbackPose != null) callbackPoseBoundary = new PoseStackBoundary(callbackPose);
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

    private static PoseStack framePoseStack(FrameContextBinding binding) {
        if (binding == null) return null;
        LuaValue value = binding.context().get("PoseStack");
        if (value == null || !value.isuserdata(MLuaRenderFrameScope.MLuaScopedRenderObject.class)) return null;
        Object nativePose = ((MLuaRenderFrameScope.MLuaScopedRenderObject) value.touserdata(
            MLuaRenderFrameScope.MLuaScopedRenderObject.class)).unwrap();
        return nativePose instanceof PoseStack pose ? pose : null;
    }

    private record PoseStackDepthAccess(Field field, boolean indexBased) {
        private static final PoseStackDepthAccess UNAVAILABLE = new PoseStackDepthAccess(null, false);
    }

    /** Keeps a callback's stack pushes and pose mutations inside the callback's own frame. */
    private static final class PoseStackBoundary implements AutoCloseable {
        private final PoseStack stack;
        private final int depthBefore;
        private boolean closed;

        private PoseStackBoundary(PoseStack stack) {
            this.stack = stack;
            this.depthBefore = poseStackDepth(stack);
            stack.pushPose();
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            try {
                if (depthBefore < 0) {
                    stack.popPose();
                    return;
                }
                int depth = poseStackDepth(stack);
                if (depth < depthBefore) {
                    warnPoseStackDepth(stack.getClass());
                    return;
                }
                while (depth > depthBefore) {
                    stack.popPose();
                    int nextDepth = poseStackDepth(stack);
                    if (nextDepth < 0 || nextDepth >= depth) {
                        warnPoseStackDepth(stack.getClass());
                        return;
                    }
                    depth = nextDepth;
                }
            } catch (RuntimeException failure) {
                warnPoseStackDepth(stack.getClass());
            }
        }
    }

    private static int poseStackDepth(PoseStack stack) {
        PoseStackDepthAccess access = POSE_STACK_DEPTH_ACCESS.get(stack.getClass());
        if (access.field() == null) return -1;
        try {
            if (access.indexBased()) return access.field().getInt(stack) + 1;
            Object poses = access.field().get(stack);
            return poses instanceof Collection<?> collection ? collection.size() : -1;
        } catch (IllegalAccessException | RuntimeException ignored) {
            return -1;
        }
    }

    private static void warnPoseStackDepth(Class<?> stackClass) {
        if (WARNED_POSE_STACK_DEPTH.add(stackClass)) {
            MLuaLogger.LOGGER.warn("[MLua] Could not fully restore PoseStack depth after a render callback on {}; "
                + "avoid unmatched pushPose()/popPose() calls", stackClass.getName());
        }
    }

    /**
     * Takes a stable render-hook snapshot only on Minecraft's client thread.
     * Loader hooks can originate on platform-specific executors; refusing them here
     * avoids even constructing or caching Lua wrappers on the wrong thread.
     */
    private static List<Hook> renderHooks(HookBucket bucket) {
        if (bucket == null || bucket.isEmpty()) return List.of();
        if (!MLuaClientRenderThread.require("Lua render callback dispatch")) return List.of();
        return bucket.snapshot();
    }

    private static void disableHook(Hook hook, Throwable failure) {
        hook.failed = true;
        removeFailedHook(hook);
        MLuaLogger.LOGGER.error("[MLua] Disabled render callback '{}' for addon '{}' at {} after an error",
            hook.id, hook.key.owner().modId(), hook.target, failure);
    }

    private static FrameContextBinding findFrameScope(Varargs args) {
        if (args == null) return null;
        for (int index = 1; index <= args.narg(); index++) {
            LuaValue argument = args.arg(index);
            if (argument == null || !argument.istable()) continue;
            LuaTable context = argument.checktable();
            synchronized (FRAME_SCOPES) {
                MLuaRenderFrameScope scope = FRAME_SCOPES.get(context);
                if (scope != null) return new FrameContextBinding(context, scope);
            }
        }
        return null;
    }

    private static void closeFrameScope(FrameContextBinding binding) {
        if (binding == null) return;
        synchronized (FRAME_SCOPES) {
            FRAME_SCOPES.remove(binding.context(), binding.scope());
        }
        binding.scope().close();
    }

    /** Returns true only while this exact Lua RenderContext is inside its creating callback. */
    public static boolean isRenderContextActive(LuaValue contextValue) {
        if (contextValue == null || !contextValue.istable()) return false;
        MLuaRenderFrameScope scope;
        synchronized (FRAME_SCOPES) {
            scope = FRAME_SCOPES.get(contextValue.checktable());
        }
        return scope != null && scope.view().isActive();
    }

    private record FrameContextBinding(LuaTable context, MLuaRenderFrameScope scope) { }

    /** Remove a disabled callback from its exact bucket so the Lua closure and runtime context can be collected. */
    private static void removeFailedHook(Hook hook) {
        if (hook == null || hook.bucket == null) return;
        hook.bucket.removeFailed(hook.key, hook);
    }

    private static MLuaConnection registerTargetConnection(HookTarget target, HookKey key, LuaValue callback,
            MLuaContext context, String description) {
        Hook hook = registerTarget(target, key, callback, context, description);
        return connection(target, hook);
    }

    private static MLuaConnection connection(HookTarget target, Hook hook) {
        return MLuaConnection.owned(hook,
            expected -> unregisterTarget(target, expected.key, expected),
            expected -> expected.active && !expected.failed);
    }

    private static Hook registerTarget(HookTarget target, HookKey key, LuaValue callback,
            MLuaContext context, String description) {
        return registerTarget(target, key, callback, context, description, null);
    }

    private static Hook registerTarget(HookTarget target, HookKey key, LuaValue callback,
            MLuaContext context, String description, Long registrationOrder) {
        java.util.concurrent.atomic.AtomicReference<Hook> registered = new java.util.concurrent.atomic.AtomicReference<>();
        synchronized (HOOK_LIFECYCLE_LOCK) {
            HOOKS.compute(target, (ignored, current) -> {
                HookBucket selected = current == null ? registryBucket(target) : current;
                registered.set(selected.put(key, requireCallback(callback), context, description, registrationOrder));
                return selected;
            });
        }
        return registered.get();
    }

    private static boolean unregisterTarget(HookTarget target, HookKey key) {
        java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean();
        HOOKS.computeIfPresent(target, (ignored, bucket) -> {
            removed.set(bucket.remove(key));
            return bucket.isEmpty() ? null : bucket;
        });
        return removed.get();
    }

    private static boolean unregisterTarget(HookTarget target, HookKey key, Hook expected) {
        java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean();
        HOOKS.computeIfPresent(target, (ignored, bucket) -> {
            removed.set(bucket.remove(key, expected));
            return bucket.isEmpty() ? null : bucket;
        });
        return removed.get();
    }

    private static HookBucket bucket(HookKind kind, String key) {
        return HOOKS.get(new HookTarget(kind, key));
    }

    private static HookBucket registryBucket(HookTarget endpoint) {
        return new HookBucket((bucket, key, expected) -> HOOKS.computeIfPresent(endpoint, (ignored, current) -> {
            if (current != bucket) return current;
            bucket.remove(key, expected);
            return bucket.isEmpty() ? null : bucket;
        }));
    }

    private static LuaValue renderContext(Hook hook, String target, Object poseStack, Object bufferSource,
            Object model, float partialTick, boolean hasPartialTick, int packedLight, int packedOverlay, Object nativeContext,
            Object... additionalFields) {
        var context = new LuaTable();
        LuaTable contextValues = new LuaTable();
        context.rawset("__context_values", contextValues);
        MLuaRenderFrameScope frameScope = new MLuaRenderFrameScope();
        try {
            boolean collectorAvailable = isRenderCollector(bufferSource);
            boolean bufferSourceAvailable = isMultiBufferSource(bufferSource);
            contextValues.set("Target", LuaValue.valueOf(target));
            contextValues.set("PoseStack", scopedUserdata(frameScope, poseStack));
            contextValues.set("BufferSource", scopedUserdata(frameScope, bufferSourceAvailable ? bufferSource : null));
            contextValues.set("RenderCollector", scopedUserdata(frameScope, collectorAvailable ? bufferSource : null));
            contextValues.set("HasPoseStack", LuaValue.valueOf(poseStack != null));
            contextValues.set("HasBufferSource", LuaValue.valueOf(bufferSourceAvailable));
            contextValues.set("HasRenderSource", LuaValue.valueOf(collectorAvailable || bufferSourceAvailable));
            LuaTable drawObjectTypes = drawObjectTypes(bufferSource, poseStack != null);
            contextValues.set("HasDrawObjectSource", LuaValue.valueOf(!drawObjectTypes.get(1).isnil()));
            contextValues.set("DrawObjectTypes", drawObjectTypes);
            contextValues.set("Model", scopedUserdata(frameScope, model));
            contextValues.set("PartialTick", LuaValue.valueOf(partialTick));
            contextValues.set("HasPartialTick", LuaValue.valueOf(hasPartialTick));
            RenderFrameTime currentFrame = frameTime;
            contextValues.set("DeltaTime", LuaValue.valueOf(currentFrame.deltaTime()));
            contextValues.set("HasDeltaTime", LuaValue.valueOf(currentFrame.available()));
            contextValues.set("PackedLight", LuaValue.valueOf(packedLight));
            contextValues.set("PackedOverlay", LuaValue.valueOf(packedOverlay));
            contextValues.set("NativeContext", scopedUserdata(frameScope, nativeContext));
            Object renderState = nativeContext != null && ENTITY_RENDER_STATE_CLASS != null
                && ENTITY_RENDER_STATE_CLASS.isInstance(nativeContext) ? nativeContext : null;
            contextValues.set("RenderState", scopedUserdata(frameScope, renderState));
            contextValues.set("PassId", LuaValue.valueOf(hook.id));
            for (int index = 0; index + 1 < additionalFields.length; index += 2) {
                String name = (String) additionalFields[index];
                LuaValue value = (LuaValue) additionalFields[index + 1];
                if (value.isuserdata()) {
                    Object nativeValue = value.touserdata();
                    contextValues.set(name, scopedUserdata(frameScope, nativeValue));
                } else {
                    contextValues.set(name, value);
                }
            }
            LuaValue instance = hook.context.registration().attachClassInstance(context, "RenderContext", null);
            if (!instance.istable()) {
                throw new IllegalStateException("RenderContext class attachment did not produce a Lua table");
            }
            instance.rawset("__mluaRenderDrawRequest", LuaValue.userdataOf(new RenderDrawRequest(
                frameScope, instance.get("PoseStack"),
                collectorAvailable ? instance.get("RenderCollector")
                    : bufferSourceAvailable ? instance.get("BufferSource") : LuaValue.NIL,
                new WeakReference<>(hook.context.session()), hook.context.ownerModId(),
                target, packedLight, packedOverlay, partialTick)));
            synchronized (FRAME_SCOPES) {
                FRAME_SCOPES.put(instance.checktable(), frameScope);
            }
            return instance;
        } catch (VirtualMachineError | ThreadDeath fatal) {
            frameScope.close();
            throw fatal;
        } catch (Throwable failure) {
            frameScope.close();
            disableHook(hook, failure);
            return LuaValue.NIL;
        }
    }

    /** Called by the registered RenderContext:DrawObject class method. */
    public static LuaValue drawObject(MLuaRegistration registration, LuaValue context,
                                     LuaValue handle, LuaValue transformValue) {
        LuaValue requestValue = context.istable() ? context.checktable().rawget("__mluaRenderDrawRequest") : LuaValue.NIL;
        if (!requestValue.isuserdata(RenderDrawRequest.class)) {
            throw new LuaError("[MLua] RenderContext:DrawObject requires a live render context.");
        }
        RenderDrawRequest request = (RenderDrawRequest)requestValue.touserdata(RenderDrawRequest.class);
        synchronized (FRAME_SCOPES) {
            if (FRAME_SCOPES.get(context.checktable()) != request.scope()) {
                throw new LuaError("[MLua] RenderContext:DrawObject requires the original active RenderContext table.");
            }
        }
        if (!request.scope().view().isActive()) {
            throw new LuaError("[MLua] RenderContext:DrawObject can only run during its render callback.");
        }
        String target = request.target();
        if ("Hud".equals(target)) {
            throw new LuaError("[MLua] RenderContext:DrawObject cannot submit a 3D RenderObject from a Hud callback; use the HUD render APIs for screen-space drawing.");
        }
        if (!handle.istable() || !registration.isInstanceOf(handle, "RenderObject")) {
            throw new LuaError("[MLua] RenderContext:DrawObject expects a live RenderObject instance.");
        }
        LuaValue idValue = handle.checktable().rawget(dev.mlua.modules.render.service.MLuaRenderService.OBJECT_ID);
        if (!idValue.isint()) {
            throw new LuaError("[MLua] RenderContext:DrawObject received an invalid RenderObject handle.");
        }
        MLuaCFrame transform = MLuaCFrame.IDENTITY;
        if (!transformValue.isnil()) {
            if (!MLuaCFrame.isCFrame(transformValue)) {
                throw new LuaError("[MLua] RenderContext:DrawObject transform must be a CFrame.");
            }
            transform = MLuaCFrame.fromLua(transformValue);
            double[] components = {transform.x(), transform.y(), transform.z(), transform.r00(), transform.r01(),
                transform.r02(), transform.r10(), transform.r11(), transform.r12(), transform.r20(),
                transform.r21(), transform.r22()};
            for (double component : components) {
                if (!Double.isFinite(component)) {
                    throw new LuaError("[MLua] RenderContext:DrawObject transform must be finite.");
                }
            }
        }
        Object livePose = unwrapFrameValue(request.poseStack(), "PoseStack", target);
        Object liveSource = unwrapFrameValue(request.bufferSource(), "render source", target);
        try {
            boolean drawn = MLuaClientRenderManager.drawObjectInRenderContext(idValue.toint(), request.owner(),
                livePose, liveSource, transform, request.light(), request.overlay(), request.partialTick());
            return LuaValue.valueOf(drawn);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            throw new LuaError("[MLua] RenderContext:DrawObject " + failure.getMessage());
        }
    }

    /** Runs a Lua draw operation inside a native pose-stack scope restored to its entry depth. */
    public static Varargs withPose(LuaValue contextValue, LuaValue callback) {
        if (callback == null || !callback.isfunction()) {
            throw new LuaError("[MLua] RenderContext:WithPose expects a callback function.");
        }
        if (contextValue == null || !contextValue.istable()) {
            throw new LuaError("[MLua] RenderContext:WithPose requires a live render context.");
        }
        MLuaRenderFrameScope scope;
        synchronized (FRAME_SCOPES) {
            scope = FRAME_SCOPES.get(contextValue.checktable());
        }
        if (scope == null || !scope.view().isActive()) {
            throw new LuaError("[MLua] RenderContext:WithPose can only run during its render callback.");
        }
        LuaValue poseValue = contextValue.get("PoseStack");
        if (!poseValue.isuserdata(MLuaRenderFrameScope.MLuaScopedRenderObject.class)) {
            throw new LuaError("[MLua] RenderContext:WithPose is unavailable because this callback has no PoseStack.");
        }
        Object nativePose = ((MLuaRenderFrameScope.MLuaScopedRenderObject)poseValue.touserdata(
            MLuaRenderFrameScope.MLuaScopedRenderObject.class)).unwrap();
        if (!(nativePose instanceof PoseStack poseStack)) {
            throw new LuaError("[MLua] RenderContext:WithPose is unavailable because this callback has no PoseStack.");
        }
        PoseStackBoundary poseBoundary = new PoseStackBoundary(poseStack);
        try {
            return callback.invoke();
        } finally {
            poseBoundary.close();
        }
    }

    private static Object unwrapFrameValue(LuaValue reference, String name, String target) {
        if (reference == null || !reference.isuserdata(MLuaRenderFrameScope.MLuaScopedRenderObject.class)) {
            throw new LuaError("[MLua] RenderContext:DrawObject is unavailable for " + target
                + " callbacks because Minecraft did not provide a " + name + ".");
        }
        try {
            return ((MLuaRenderFrameScope.MLuaScopedRenderObject)reference.touserdata(
                MLuaRenderFrameScope.MLuaScopedRenderObject.class)).unwrap();
        } catch (IllegalStateException expired) {
            throw new LuaError("[MLua] RenderContext:DrawObject cannot use an expired " + name + ".");
        }
    }

    private record RenderDrawRequest(MLuaRenderFrameScope scope, LuaValue poseStack, LuaValue bufferSource,
                                     WeakReference<Object> runtimeOwner, String ownerModId, String target,
                                     int light, int overlay, float partialTick) {
        private MLuaRenderOwnershipRegistry.Owner owner() {
            return new MLuaRenderOwnershipRegistry.Owner(runtimeOwner.get(), ownerModId);
        }
    }

    private static LuaValue scopedUserdata(MLuaRenderFrameScope scope, Object value) {
        Object reference = scope.reference(value);
        return reference == null ? LuaValue.NIL : LuaValue.userdataOf(reference);
    }

    private static LuaTable drawObjectTypes(Object renderSource, boolean hasPoseStack) {
        LuaTable types = new LuaTable();
        if (!hasPoseStack) return types;
        if (isMultiBufferSource(renderSource)) {
            types.set(1, LuaValue.valueOf("Mesh"));
            types.set(2, LuaValue.valueOf("BBModel"));
        } else if (isRenderCollector(renderSource)
                && MLuaClientRenderManager.supportsDrawObjectSource(renderSource)) {
            types.set(1, LuaValue.valueOf("Mesh"));
            types.set(2, LuaValue.valueOf("BBModel"));
        }
        return types;
    }

    private static LuaValue requireCallback(LuaValue callback) {
        if (callback == null || !callback.isfunction()) {
            throw new LuaError("[MLua] Render callback must be a function.");
        }
        return callback;
    }

    private static Owner owner(MLuaContext context) {
        if (context == null) throw new LuaError("[MLua] Render callback registration needs its owning Lua context.");
        return new Owner(context.session(), context.ownerModId());
    }

    private static LuaValue userdata(Object value) {
        return value == null ? LuaValue.NIL : LuaValue.userdataOf(value);
    }

    private static Class<?> findOptionalClass(String className) {
        try {
            return Class.forName(className, false, MLuaRenderCallbacks.class.getClassLoader());
        } catch (LinkageError | ClassNotFoundException unavailable) {
            return null;
        }
    }

    private static boolean isRenderCollector(Object value) {
        return value != null && RENDER_COLLECTOR_CLASS != null && RENDER_COLLECTOR_CLASS.isInstance(value);
    }

    private static boolean isMultiBufferSource(Object value) {
        return value != null && MULTI_BUFFER_SOURCE_CLASS != null && MULTI_BUFFER_SOURCE_CLASS.isInstance(value);
    }

    private enum HookKind {
        ENTITY_MODEL, ENTITY_DRAW, ENTITY_DRAW_POST, FIRST_PERSON_ITEM, THIRD_PERSON_ITEM, WORLD,
        WORLD_GEOMETRY, HUD
    }

    private record HookTarget(HookKind kind, String key) { }
    private record Owner(Object session, String modId) {
        private Owner { if (modId == null) modId = ""; }

        @Override
        public boolean equals(Object other) {
            return other instanceof Owner owner
                && session == owner.session()
                && modId.equals(owner.modId());
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(session) + modId.hashCode();
        }
    }
    private record HookKey(Owner owner, String id) { }
    private record PreviousWorldHook(HookTarget target, Hook hook) { }

    /** Keeps an immutable registration-ordered dispatch list so render frames do not allocate or sort snapshots. */
    private static final class HookBucket {
        private final Map<HookKey, Hook> hooks = new HashMap<>();
        private final HookRemoval removal;
        private volatile List<Hook> snapshot = List.of();

        private HookBucket(HookRemoval removal) {
            this.removal = removal;
        }

        private synchronized Hook put(HookKey key, LuaValue callback, MLuaContext context, String target,
                                      Long registrationOrder) {
            Hook previous = hooks.get(key);
            if (previous == null) {
                if (hooks.size() >= MAX_CALLBACKS_PER_TARGET) {
                    throw new LuaError("[MLua] Render target '" + target + "' already has the maximum of "
                        + MAX_CALLBACKS_PER_TARGET + " callbacks");
                }
                reserveCallback(key.owner());
            } else {
                previous.active = false;
            }
            long order = registrationOrder == null ? NEXT_REGISTRATION_ORDER.getAndIncrement() : registrationOrder;
            Hook hook = new Hook(callback, context, target, key, this, order);
            hooks.put(key, hook);
            rebuildSnapshot();
            return hook;
        }

        private synchronized boolean remove(HookKey key) {
            Hook removed = hooks.remove(key);
            if (removed == null) return false;
            removed.active = false;
            releaseCallback(key.owner());
            rebuildSnapshot();
            return true;
        }

        private synchronized boolean remove(HookKey key, Hook expected) {
            if (!hooks.remove(key, expected)) return false;
            expected.active = false;
            releaseCallback(key.owner());
            rebuildSnapshot();
            return true;
        }

        private void removeFailed(HookKey key, Hook expected) {
            if (removal == null) remove(key, expected);
            else removal.remove(this, key, expected);
        }

        private synchronized void removeOwned(java.util.function.Predicate<Owner> predicate) {
            int removed = 0;
            var iterator = hooks.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<HookKey, Hook> entry = iterator.next();
                if (!predicate.test(entry.getKey().owner())) continue;
                entry.getValue().active = false;
                iterator.remove();
                releaseCallback(entry.getKey().owner());
                removed++;
            }
            if (removed > 0) {
                rebuildSnapshot();
            }
        }

        private boolean isEmpty() {
            return snapshot.isEmpty();
        }

        private synchronized boolean contains(HookKey key) {
            return hooks.containsKey(key);
        }

        private synchronized int size() {
            return hooks.size();
        }

        private synchronized Hook get(HookKey key) {
            return hooks.get(key);
        }

        private List<Hook> snapshot() {
            return snapshot;
        }

        private void rebuildSnapshot() {
            List<Map.Entry<HookKey, Hook>> ordered = new ArrayList<>(hooks.size());
            hooks.forEach((key, hook) -> ordered.add(Map.entry(key, hook)));
            ordered.sort(Comparator.comparingLong(entry -> entry.getValue().registrationOrder));
            snapshot = ordered.stream().map(Map.Entry::getValue).toList();
        }
    }

    private static void reserveCallback(Owner owner) {
        synchronized (CALLBACK_LIMIT_LOCK) {
            int current = REGISTERED_CALLBACKS.get();
            if (current >= MAX_CALLBACKS) {
                throw new LuaError("[MLua] MLua already has the maximum of " + MAX_CALLBACKS
                    + " active render callbacks; unregister unused passes or unload their addon");
            }
            int owned = REGISTERED_CALLBACKS_BY_OWNER.getOrDefault(owner, 0);
            if (owned >= MAX_CALLBACKS_PER_OWNER) {
                throw new LuaError("[MLua] Addon '" + owner.modId() + "' already has the maximum of "
                    + MAX_CALLBACKS_PER_OWNER + " active render callbacks");
            }
            REGISTERED_CALLBACKS.set(current + 1);
            REGISTERED_CALLBACKS_BY_OWNER.put(owner, owned + 1);
        }
    }

    public static int registeredCallbackCount() {
        return REGISTERED_CALLBACKS.get();
    }

    public static int registeredCallbackCount(MLuaContext context) {
        if (context == null) return 0;
        synchronized (CALLBACK_LIMIT_LOCK) {
            return REGISTERED_CALLBACKS_BY_OWNER.getOrDefault(owner(context), 0);
        }
    }

    private static void releaseCallback(Owner owner) {
        synchronized (CALLBACK_LIMIT_LOCK) {
            int current = REGISTERED_CALLBACKS.get();
            Integer owned = REGISTERED_CALLBACKS_BY_OWNER.get(owner);
            if (current <= 0 || owned == null || owned <= 0) {
                throw new IllegalStateException("Render callback accounting underflow");
            }
            REGISTERED_CALLBACKS.set(current - 1);
            if (owned == 1) REGISTERED_CALLBACKS_BY_OWNER.remove(owner);
            else REGISTERED_CALLBACKS_BY_OWNER.put(owner, owned - 1);
        }
    }

    @FunctionalInterface
    private interface HookRemoval {
        void remove(HookBucket bucket, HookKey key, Hook expected);
    }

    private static final class Hook {
        private final LuaValue callback;
        private final MLuaContext context;
        private final String target;
        private final String id;
        private final HookKey key;
        private final HookBucket bucket;
        private final long registrationOrder;
        private volatile boolean active = true;
        private volatile boolean failed;

        private Hook(LuaValue callback, MLuaContext context, String target, HookKey key,
                     HookBucket bucket, long registrationOrder) {
            this.callback = callback;
            this.context = context;
            this.target = target;
            this.id = key.id();
            this.key = key;
            this.bucket = bucket;
            this.registrationOrder = registrationOrder;
        }
    }
}
