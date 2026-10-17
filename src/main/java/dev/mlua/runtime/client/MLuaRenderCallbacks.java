package dev.mlua.runtime.client;

import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Addon-owned Lua callbacks attached to Minecraft's client render points. */
public final class MLuaRenderCallbacks {
    private static volatile float frameDeltaTime = 0.05F;
    private static volatile boolean hasFrameDeltaTime;
    private static final String DEFAULT_PASS_ID = "mlua:default";
    /** Bounds callback dispatch work even when many addons register render hooks. */
    public static final int MAX_CALLBACKS = 4096;
    /** Prevents one entity type, render stage, or item from monopolizing every frame. */
    public static final int MAX_CALLBACKS_PER_TARGET = 256;
    private static final AtomicLong NEXT_REGISTRATION_ORDER = new AtomicLong();
    private static final AtomicInteger REGISTERED_CALLBACKS = new AtomicInteger();
    /** One registry owns every render hook; the target kind prevents unrelated hooks colliding. */
    private static final ConcurrentMap<HookTarget, HookBucket> HOOKS = new ConcurrentHashMap<>();
    private static final Object WORLD_REGISTRATION_LOCK = new Object();
    private static final HookTarget HUD_TARGET = new HookTarget(HookKind.HUD, "");
    private static final Class<?> RENDER_COLLECTOR_CLASS = findOptionalClass(
        "net.minecraft.client.renderer.SubmitNodeCollector");
    private static final ClassValue<PartialTickAccess> PARTIAL_TICK_ACCESS = new ClassValue<>() {
        @Override protected PartialTickAccess computeValue(Class<?> type) {
            return new PartialTickAccess(
                findNoArg(type, "getPartialTick", "partialTick", "getPartialTickTime", "getTickDelta", "tickDelta"),
                findNoArg(type, "tickCounter", "getTickCounter", "deltaTracker", "getDeltaTracker", "timer", "getTimer"),
                findNoArg(type, "getGameTimeDeltaPartialTick", "getTickDelta", "getPartialTick", "getPartialTickTime"),
                findBooleanArg(type, "getGameTimeDeltaPartialTick", "getTickDelta"));
        }
    };

    private MLuaRenderCallbacks() { }

    /** Stores the latest client render-frame duration in seconds for render callbacks. */
    public static void setFrameDeltaTime(double deltaTime) {
        if (Double.isFinite(deltaTime) && deltaTime >= 0.0D) {
            frameDeltaTime = (float)Math.min(deltaTime, 1.0D);
            hasFrameDeltaTime = true;
        }
    }

    public static void register(String entityId, LuaValue callback, MLuaContext context) {
        register(entityId, DEFAULT_PASS_ID, callback, context);
    }

    public static void register(String entityId, String passId, LuaValue callback, MLuaContext context) {
        if (context == null) throw new IllegalArgumentException("Render callback needs its owning Lua context");
        requireCallback(callback);
        String id = normalizeId(entityId);
        HookKey key = new HookKey(owner(context), normalizeId(passId));
        registerTarget(new HookTarget(HookKind.ENTITY_MODEL, id), key, callback, context,
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

    public static void registerEntityDraw(String entityType, String passId, LuaValue callback, MLuaContext context) {
        if (context == null) throw new IllegalArgumentException("Render callback needs its owning Lua context");
        String id = normalizeId(entityType);
        HookKey key = new HookKey(owner(context), normalizeId(passId));
        registerTarget(new HookTarget(HookKind.ENTITY_DRAW, id), key, callback, context,
            "entity draw pass '" + key.id() + "' for " + id);
    }

    public static boolean unregisterEntityDraw(String entityType, String passId, MLuaContext context) {
        if (context == null) return false;
        String id = normalizeId(entityType);
        HookKey key = new HookKey(owner(context), normalizeId(passId));
        return unregisterTarget(new HookTarget(HookKind.ENTITY_DRAW, id), key);
    }

    public static void renderEntityDraw(Entity entity, Object renderer, Object poseStack, Object bufferSource,
            Float partialTick, int packedLight) {
        renderEntityDraw(entity, renderer, poseStack, bufferSource, partialTick, packedLight, null);
    }

    public static void renderEntityDraw(Entity entity, Object renderer, Object poseStack, Object bufferSource,
            Float partialTick, int packedLight, Object nativeContext) {
        if (entity == null) return;
        var typeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (typeId == null) return;
        HookBucket hooks = bucket(HookKind.ENTITY_DRAW, typeId.toString());
        if (hooks == null || hooks.isEmpty()) return;
        LuaValue entityValue = MLuaEntityWrapper.entityTable(entity);
        for (Hook hook : hooks.snapshot()) {
            LuaValue[] args = {entityValue, renderContext(hook, "Entity", poseStack, bufferSource,
                null, partialTick == null ? 0.0F : partialTick, packedLight, 0, nativeContext,
                "HasPartialTick", LuaValue.valueOf(partialTick != null),
                "Renderer", userdata(renderer))};
            invoke(hook, args, LuaValue.varargsOf(args));
        }
    }

    public static boolean hasEntityDrawCallbacks(net.minecraft.world.entity.EntityType<?> entityType) {
        if (entityType == null) return false;
        var typeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        if (typeId == null) return false;
        HookBucket hooks = bucket(HookKind.ENTITY_DRAW, typeId.toString());
        return hooks != null && !hooks.isEmpty();
    }

    public static void registerFirstPersonItem(String itemId, LuaValue callback, MLuaContext context) {
        registerFirstPersonItem(itemId, DEFAULT_PASS_ID, callback, context);
    }

    public static void registerFirstPersonItem(String itemId, String passId, LuaValue callback, MLuaContext context) {
        if (context == null) throw new IllegalArgumentException("Render callback needs its owning Lua context");
        String item = normalizeId(itemId);
        HookKey key = new HookKey(owner(context), normalizeId(passId));
        registerTarget(new HookTarget(HookKind.FIRST_PERSON_ITEM, item), key, callback, context,
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

    public static void registerThirdPersonItem(String itemId, LuaValue callback, MLuaContext context) {
        registerThirdPersonItem(itemId, DEFAULT_PASS_ID, callback, context);
    }

    public static void registerThirdPersonItem(String itemId, String passId, LuaValue callback, MLuaContext context) {
        if (context == null) throw new IllegalArgumentException("Render callback needs its owning Lua context");
        String item = normalizeId(itemId);
        HookKey key = new HookKey(owner(context), normalizeId(passId));
        registerTarget(new HookTarget(HookKind.THIRD_PERSON_ITEM, item), key, callback, context,
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
            net.minecraft.world.entity.HumanoidArm arm, net.minecraft.world.item.ItemDisplayContext displayContext,
            Object poseStack, Object bufferSource, int packedLight) {
        renderThirdPersonItem(entity, stack, arm, displayContext, poseStack, bufferSource, packedLight, null);
    }

    public static void renderThirdPersonItem(LivingEntity entity, ItemStack stack,
            net.minecraft.world.entity.HumanoidArm arm, net.minecraft.world.item.ItemDisplayContext displayContext,
            Object poseStack, Object bufferSource, int packedLight, Float partialTick) {
        if (entity == null || stack == null || stack.isEmpty()) return;
        var itemKey = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemKey == null) return;
        HookBucket hooks = bucket(HookKind.THIRD_PERSON_ITEM, itemKey.toString());
        if (hooks == null) return;
        String hand = entity.getMainArm() == arm ? "MAIN_HAND" : "OFF_HAND";
        LuaValue entityValue = MLuaEntityWrapper.entityTable(entity);
        for (Hook hook : hooks.snapshot()) {
            LuaValue[] args = {entityValue, renderContext(hook, "ThirdPersonItem",
                poseStack, bufferSource, null, partialTick == null ? 0.0F : partialTick,
                packedLight, 0, null,
                "HasPartialTick", LuaValue.valueOf(partialTick != null),
                "ItemStack", userdata(stack), "Hand", LuaValue.valueOf(hand),
                "Arm", LuaValue.valueOf(arm.name()),
                "DisplayContext", LuaValue.valueOf(displayContext.name()))};
            invoke(hook, args, LuaValue.varargsOf(args));
        }
    }

    public static boolean hasThirdPersonItemCallbacks(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var itemKey = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemKey == null) return false;
        HookBucket hooks = bucket(HookKind.THIRD_PERSON_ITEM, itemKey.toString());
        return hooks != null && !hooks.isEmpty();
    }

    public static void renderFirstPersonItem(net.minecraft.client.player.AbstractClientPlayer player,
            net.minecraft.world.InteractionHand hand, ItemStack stack, Object poseStack, Object bufferSource,
            float partialTick, float pitch, float swingProgress, float equipProgress, int packedLight) {
        if (player == null || stack == null || stack.isEmpty()) return;
        var itemKey = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemKey == null) return;
        HookBucket hooks = bucket(HookKind.FIRST_PERSON_ITEM, itemKey.toString());
        if (hooks == null) return;
        LuaValue playerValue = MLuaEntityWrapper.entityTable(player);
        for (Hook hook : hooks.snapshot()) {
            LuaValue[] args = {playerValue, renderContext(hook, "FirstPersonItem",
                poseStack, bufferSource, null, partialTick, packedLight, 0, null,
                "Player", playerValue,
                "ItemStack", userdata(stack), "Hand", LuaValue.valueOf(hand.name()),
                "Pitch", LuaValue.valueOf(pitch), "SwingProgress", LuaValue.valueOf(swingProgress),
                "EquipProgress", LuaValue.valueOf(equipProgress))};
            invoke(hook, args, LuaValue.varargsOf(args));
        }
    }

    public static void registerWorld(LuaValue callback, MLuaContext context) {
        registerWorld("mlua:default", "AFTER_ENTITIES", callback, context);
    }

    public static void registerWorld(String stage, LuaValue callback, MLuaContext context) {
        registerWorld("mlua:default", stage, callback, context);
    }

    public static void registerWorld(String id, String stage, LuaValue callback, MLuaContext context) {
        requireCallback(callback);
        if (context == null) throw new IllegalArgumentException("Render callback needs its owning Lua context");
        String normalized = normalizeWorldStage(stage);
        String key = normalizeId(id);
        HookKey hookKey = new HookKey(owner(context), key);
        HookTarget destination = new HookTarget(HookKind.WORLD, normalized);
        synchronized (WORLD_REGISTRATION_LOCK) {
            HookBucket targetBucket = HOOKS.get(destination);
            boolean alreadyAtDestination = targetBucket != null && targetBucket.contains(hookKey);
            if (!alreadyAtDestination && targetBucket != null
                && targetBucket.size() >= MAX_CALLBACKS_PER_TARGET) {
                throw new IllegalStateException("Render target 'world stage " + normalized + "' already has the maximum of "
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
                registerTarget(destination, hookKey, callback, context,
                    "world pass '" + key + "' at " + normalized);
            } catch (RuntimeException failure) {
                for (PreviousWorldHook entry : previous) {
                    Hook old = entry.hook();
                    registerTarget(entry.target(), hookKey, old.callback, old.context, old.target);
                }
                throw failure;
            }
        }
    }

    public static boolean unregisterWorld(String stage, MLuaContext context) {
        String normalized = normalizeWorldStage(stage);
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

    public static void registerHud(LuaValue callback, MLuaContext context) {
        registerHud(DEFAULT_PASS_ID, callback, context);
    }

    public static void registerHud(String id, LuaValue callback, MLuaContext context) {
        String key = normalizeId(id);
        registerTarget(HUD_TARGET, new HookKey(owner(context), key), callback, context, "HUD pass '" + key + "'");
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
        if (entity == null) return;
        var typeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (typeId == null) return;
        HookBucket registered = bucket(HookKind.ENTITY_MODEL, typeId.toString());
        if (registered == null || registered.isEmpty()) return;
        Float validatedPartialTick = finitePartialTick(partialTick);
        boolean partialTickAvailable = hasPartialTick && validatedPartialTick != null;
        for (Hook hook : registered.snapshot()) {
            LuaValue[] args = {MLuaEntityWrapper.entityTable(entity), renderContext(hook, "EntityModel", poseStack,
                bufferSource, model, partialTickAvailable ? validatedPartialTick : 0.0F,
                packedLight, packedOverlay, null,
                "HasPartialTick", LuaValue.valueOf(partialTickAvailable))};
            invoke(hook, args, LuaValue.varargsOf(args));
        }
    }

    public static void renderWorld(String stage, Object nativeContext, Object poseStack, Object bufferSource, Object camera) {
        String normalizedStage = normalizeWorldStage(stage);
        HookBucket hooks = bucket(HookKind.WORLD, normalizedStage);
        if (hooks == null) return;
        Float partialTick = readPartialTick(nativeContext);
        for (Hook hook : hooks.snapshot()) {
            LuaValue[] args = {renderContext(hook, "World", poseStack, bufferSource, null,
                partialTick == null ? 0.0F : partialTick, 0, 0, nativeContext,
                "Stage", LuaValue.valueOf(normalizedStage), "Camera", userdata(camera),
                "HasPartialTick", LuaValue.valueOf(partialTick != null))};
            invoke(hook, args, LuaValue.varargsOf(args));
        }
    }

    private static Float readPartialTick(Object context) {
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

    private static Float finitePartialTick(Object value) {
        if (!(value instanceof Number number)) return null;
        float partialTick = number.floatValue();
        return Float.isFinite(partialTick) ? Math.max(0.0F, Math.min(1.0F, partialTick)) : null;
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

    public static void renderHud(Object graphics, int width, int height) {
        HookBucket hud = bucket(HookKind.HUD, "");
        if (hud == null) return;
        for (Hook hook : hud.snapshot()) {
            LuaValue[] args = {renderContext(hook, "Hud", null, null, null, 0.0F, 0, 0, null,
                "Graphics", userdata(graphics), "Width", LuaValue.valueOf(width), "Height", LuaValue.valueOf(height),
                "HasPartialTick", LuaValue.FALSE)};
            invoke(hook, args, LuaValue.varargsOf(args));
        }
    }

    public static void clearOwned(Object session, String modId) {
        java.util.function.Predicate<Owner> matches = owner -> owner.session() == session && owner.modId().equals(modId);
        synchronized (WORLD_REGISTRATION_LOCK) {
            removeOwned(matches);
        }
    }

    public static void clearRuntime(Object session) {
        java.util.function.Predicate<Owner> matches = owner -> owner.session() == session;
        synchronized (WORLD_REGISTRATION_LOCK) {
            removeOwned(matches);
        }
    }

    private static void removeOwned(java.util.function.Predicate<Owner> predicate) {
        HOOKS.forEach((target, ignored) -> HOOKS.computeIfPresent(target, (key, bucket) -> {
            bucket.removeOwned(predicate);
            return bucket.isEmpty() ? null : bucket;
        }));
    }

    private static String normalizeWorldStage(String stage) {
        String normalized = stage == null ? "" : stage.trim().toUpperCase(java.util.Locale.ROOT);
        for (String supported : dev.mlua.loaderapi.ClientBridge.WORLD_RENDER_STAGES) {
            if (supported.equals(normalized)) return normalized;
        }
        throw new IllegalArgumentException("Unknown world render stage '" + stage + "'. Supported stages: "
            + String.join(", ", dev.mlua.loaderapi.ClientBridge.WORLD_RENDER_STAGES));
    }

    private static String normalizeId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Render pass id cannot be blank");
        return MLuaResourceLocations.parse(id).toString();
    }

    private static void invoke(Hook hook, LuaValue[] args, Varargs invocationArgs) {
        MLuaRenderFrameScope frameScope = findFrameScope(args);
        if (!hook.active || hook.failed) {
            if (frameScope != null) frameScope.close();
            return;
        }
        MLuaContext context = hook.context;
        try {
            synchronized (context.executionLock()) {
                if (!hook.active || hook.failed) return;
                MLuaLuaBindings.bindContext(context.globals(), context);
                hook.callback.invoke(invocationArgs);
            }
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            hook.failed = true;
            removeFailedHook(hook);
            MLuaLogger.LOGGER.error("[MLua] Disabled Lua {} render callback after an error",
                hook.target, failure);
        } finally {
            if (frameScope != null) frameScope.close();
        }
    }

    private static MLuaRenderFrameScope findFrameScope(LuaValue[] args) {
        if (args == null) return null;
        for (LuaValue argument : args) {
            if (argument == null || !argument.istable()) continue;
            LuaValue value = argument.checktable().rawget("__mluaRenderFrameScope");
            if (value.isuserdata(MLuaRenderFrameScope.View.class)) {
                return ((MLuaRenderFrameScope.View)value.touserdata(MLuaRenderFrameScope.View.class)).scope();
            }
        }
        return null;
    }

    /** Remove a disabled callback from its exact bucket so the Lua closure and runtime context can be collected. */
    private static void removeFailedHook(Hook hook) {
        if (hook == null || hook.bucket == null) return;
        hook.bucket.removeFailed(hook.key, hook);
    }

    private static void registerTarget(HookTarget target, HookKey key, LuaValue callback,
            MLuaContext context, String description) {
        HOOKS.compute(target, (ignored, current) -> {
            HookBucket selected = current == null ? registryBucket(target) : current;
            selected.put(key, requireCallback(callback), context, description);
            return selected;
        });
    }

    private static boolean unregisterTarget(HookTarget target, HookKey key) {
        java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean();
        HOOKS.computeIfPresent(target, (ignored, bucket) -> {
            removed.set(bucket.remove(key));
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
            Object model, float partialTick, int packedLight, int packedOverlay, Object nativeContext,
            Object... additionalFields) {
        var context = new org.luaj.vm2.LuaTable();
        MLuaRenderFrameScope frameScope = new MLuaRenderFrameScope();
        context.set("__mluaRenderFrameScope", LuaValue.userdataOf(frameScope.view()));
        context.set("Target", LuaValue.valueOf(target));
        context.set("PoseStack", scopedUserdata(frameScope, poseStack));
        context.set("BufferSource", scopedUserdata(frameScope, bufferSource));
        context.set("RenderCollector", scopedUserdata(frameScope, isRenderCollector(bufferSource) ? bufferSource : null));
        context.set("Model", scopedUserdata(frameScope, model));
        context.set("PartialTick", LuaValue.valueOf(partialTick));
        context.set("HasPartialTick", LuaValue.TRUE);
        context.set("DeltaTime", LuaValue.valueOf(frameDeltaTime));
        context.set("HasDeltaTime", LuaValue.valueOf(hasFrameDeltaTime));
        context.set("PackedLight", LuaValue.valueOf(packedLight));
        context.set("PackedOverlay", LuaValue.valueOf(packedOverlay));
        context.set("NativeContext", scopedUserdata(frameScope, nativeContext));
        context.set("PassId", LuaValue.valueOf(hook.id));
        for (int index = 0; index + 1 < additionalFields.length; index += 2) {
            String name = (String) additionalFields[index];
            LuaValue value = (LuaValue) additionalFields[index + 1];
            if (value.isuserdata()) {
                Object nativeValue = value.touserdata();
                context.set(name, scopedUserdata(frameScope, nativeValue));
            } else {
                context.set(name, value);
            }
        }
        return hook.context.registration().attachClassInstance(context, "RenderContext", null);
    }

    private static LuaValue scopedUserdata(MLuaRenderFrameScope scope, Object value) {
        Object reference = scope.reference(value);
        return reference == null ? LuaValue.NIL : LuaValue.userdataOf(reference);
    }

    private static LuaValue requireCallback(LuaValue callback) {
        if (callback == null || !callback.isfunction()) {
            throw new IllegalArgumentException("Render callback must be a function");
        }
        return callback;
    }

    private static Owner owner(MLuaContext context) {
        if (context == null) throw new IllegalArgumentException("Render callback needs its owning Lua context");
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

    private enum HookKind {
        ENTITY_MODEL, ENTITY_DRAW, FIRST_PERSON_ITEM, THIRD_PERSON_ITEM, WORLD, HUD
    }

    private record HookTarget(HookKind kind, String key) { }
    private record Owner(Object session, String modId) { }
    private record HookKey(Owner owner, String id) { }
    private record PreviousWorldHook(HookTarget target, Hook hook) { }

    /** Keeps a sorted immutable dispatch list so render frames do not allocate or sort callback snapshots. */
    private static final class HookBucket {
        private final Map<HookKey, Hook> hooks = new HashMap<>();
        private final HookRemoval removal;
        private volatile List<Hook> snapshot = List.of();

        private HookBucket(HookRemoval removal) {
            this.removal = removal;
        }

        private synchronized void put(HookKey key, LuaValue callback, MLuaContext context, String target) {
            Hook previous = hooks.get(key);
            if (previous == null) {
                if (hooks.size() >= MAX_CALLBACKS_PER_TARGET) {
                    throw new IllegalStateException("Render target '" + target + "' already has the maximum of "
                        + MAX_CALLBACKS_PER_TARGET + " callbacks");
                }
                reserveCallback();
            } else {
                previous.active = false;
            }
            Hook hook = new Hook(callback, context, target, key, this, NEXT_REGISTRATION_ORDER.getAndIncrement());
            hooks.put(key, hook);
            rebuildSnapshot();
        }

        private synchronized boolean remove(HookKey key) {
            Hook removed = hooks.remove(key);
            if (removed == null) return false;
            removed.active = false;
            releaseCallback();
            rebuildSnapshot();
            return true;
        }

        private synchronized boolean remove(HookKey key, Hook expected) {
            if (!hooks.remove(key, expected)) return false;
            expected.active = false;
            releaseCallback();
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
                removed++;
            }
            if (removed > 0) {
                REGISTERED_CALLBACKS.addAndGet(-removed);
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
            ordered.sort(Comparator
                .comparing((Map.Entry<HookKey, Hook> entry) -> entry.getKey().owner().modId())
                .thenComparing(entry -> entry.getKey().id())
                .thenComparingLong(entry -> entry.getValue().registrationOrder));
            snapshot = ordered.stream().map(Map.Entry::getValue).toList();
        }
    }

    private static void reserveCallback() {
        while (true) {
            int current = REGISTERED_CALLBACKS.get();
            if (current >= MAX_CALLBACKS) {
                throw new IllegalStateException("MLua already has the maximum of " + MAX_CALLBACKS
                    + " active render callbacks; unregister unused passes or unload their addon");
            }
            if (REGISTERED_CALLBACKS.compareAndSet(current, current + 1)) return;
        }
    }

    public static int registeredCallbackCount() {
        return REGISTERED_CALLBACKS.get();
    }

    private static void releaseCallback() {
        int remaining = REGISTERED_CALLBACKS.decrementAndGet();
        if (remaining < 0) {
            REGISTERED_CALLBACKS.incrementAndGet();
            throw new IllegalStateException("Render callback accounting underflow");
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
