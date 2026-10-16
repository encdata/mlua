package dev.mlua.runtime.client;

import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.world.entity.LivingEntity;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Addon-owned Lua callbacks attached to Minecraft's client render points. */
public final class MLuaRenderCallbacks {
    private static final String DEFAULT_PASS_ID = "mlua:default";
    private static final ConcurrentMap<String, HookBucket> ENTITY_HOOKS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, HookBucket> WORLD_HOOKS = new ConcurrentHashMap<>();
    private static final HookBucket HUD_HOOKS = new HookBucket();

    private MLuaRenderCallbacks() { }

    public static void register(String entityId, LuaValue callback, MLuaContext context) {
        register(entityId, DEFAULT_PASS_ID, callback, context);
    }

    public static void register(String entityId, String passId, LuaValue callback, MLuaContext context) {
        if (context == null) throw new IllegalArgumentException("Render callback needs its owning Lua context");
        requireCallback(callback);
        String id = normalizeId(entityId);
        HookKey key = new HookKey(owner(context), normalizeId(passId));
        ENTITY_HOOKS.compute(id, (ignored, bucket) -> {
            HookBucket selected = bucket == null ? new HookBucket() : bucket;
            selected.put(key, new Hook(callback, context, "entity pass '" + key.id() + "' for " + id, key.id()));
            return selected;
        });
    }

    public static boolean unregister(String entityId, MLuaContext context) {
        return unregister(entityId, DEFAULT_PASS_ID, context);
    }

    public static boolean unregister(String entityId, String passId, MLuaContext context) {
        if (context == null) return false;
        String id = normalizeId(entityId);
        HookKey key = new HookKey(owner(context), normalizeId(passId));
        java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean();
        ENTITY_HOOKS.computeIfPresent(id, (ignored, bucket) -> {
            removed.set(bucket.remove(key));
            return bucket.isEmpty() ? null : bucket;
        });
        return removed.get();
    }

    public static void registerWorld(LuaValue callback, MLuaContext context) {
        registerWorld("mlua:default", "AFTER_ENTITIES", callback, context);
    }

    public static void registerWorld(String stage, LuaValue callback, MLuaContext context) {
        registerWorld("mlua:default", stage, callback, context);
    }

    public static void registerWorld(String id, String stage, LuaValue callback, MLuaContext context) {
        String normalized = normalizeWorldStage(stage);
        String key = normalizeId(id);
        HookKey hookKey = new HookKey(owner(context), key);
        WORLD_HOOKS.compute(normalized, (ignored, bucket) -> {
            HookBucket selected = bucket == null ? new HookBucket() : bucket;
            selected.put(hookKey, new Hook(requireCallback(callback), context,
                "world pass '" + key + "' at " + normalized, key));
            return selected;
        });
    }

    public static boolean unregisterWorld(String stage, MLuaContext context) {
        String normalized = normalizeWorldStage(stage);
        HookKey key = new HookKey(owner(context), DEFAULT_PASS_ID);
        java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean();
        WORLD_HOOKS.computeIfPresent(normalized, (ignored, bucket) -> {
            removed.set(bucket.remove(key));
            return bucket.isEmpty() ? null : bucket;
        });
        return removed.get();
    }

    public static boolean unregisterWorld(MLuaContext context) {
        return unregisterWorld("AFTER_ENTITIES", context);
    }

    public static boolean unregisterWorldPass(String id, MLuaContext context) {
        HookKey key = new HookKey(owner(context), normalizeId(id));
        java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean();
        WORLD_HOOKS.forEach((stage, ignored) -> WORLD_HOOKS.computeIfPresent(stage, (name, bucket) -> {
            removed.compareAndSet(false, bucket.remove(key));
            return bucket.isEmpty() ? null : bucket;
        }));
        return removed.get();
    }

    public static void registerHud(LuaValue callback, MLuaContext context) {
        registerHud(DEFAULT_PASS_ID, callback, context);
    }

    public static void registerHud(String id, LuaValue callback, MLuaContext context) {
        String key = normalizeId(id);
        HUD_HOOKS.put(new HookKey(owner(context), key), new Hook(requireCallback(callback), context,
            "HUD pass '" + key + "'", key));
    }

    public static boolean unregisterHud(MLuaContext context) {
        return unregisterHudPass(DEFAULT_PASS_ID, context);
    }

    public static boolean unregisterHudPass(String id, MLuaContext context) {
        return context != null && HUD_HOOKS.remove(new HookKey(owner(context), normalizeId(id)));
    }

    public static void render(LivingEntity entity, Object model, Object poseStack, Object bufferSource,
                              float partialTick, int packedLight, int packedOverlay) {
        if (entity == null) return;
        var typeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (typeId == null) return;
        HookBucket registered = ENTITY_HOOKS.get(typeId.toString());
        if (registered == null || registered.isEmpty()) return;
        for (Hook hook : registered.snapshot()) {
            LuaValue[] args = {MLuaEntityWrapper.entityTable(entity), renderContext(hook, "EntityModel", poseStack,
                bufferSource, model, partialTick, packedLight, packedOverlay, null)};
            invoke(hook, args, LuaValue.varargsOf(args));
        }
    }

    public static void renderWorld(String stage, Object nativeContext, Object poseStack, Object bufferSource, Object camera) {
        HookBucket hooks = WORLD_HOOKS.get(stage);
        if (hooks == null) return;
        for (Hook hook : hooks.snapshot()) {
            LuaValue[] args = {renderContext(hook, "World", poseStack, bufferSource, null, 0.0F, 0, 0, nativeContext,
                "Stage", LuaValue.valueOf(stage), "Camera", userdata(camera))};
            invoke(hook, args, LuaValue.varargsOf(args));
        }
    }

    public static void renderHud(Object graphics, int width, int height) {
        for (Hook hook : HUD_HOOKS.snapshot()) {
            LuaValue[] args = {renderContext(hook, "Hud", null, null, null, 0.0F, 0, 0, null,
                "Graphics", userdata(graphics), "Width", LuaValue.valueOf(width), "Height", LuaValue.valueOf(height))};
            invoke(hook, args, LuaValue.varargsOf(args));
        }
    }

    public static void clearOwned(Object session, String modId) {
        java.util.function.Predicate<Owner> matches = owner -> owner.session() == session && owner.modId().equals(modId);
        removeOwned(ENTITY_HOOKS, matches);
        removeWorld(matches);
        HUD_HOOKS.removeOwned(matches);
    }

    public static void clearRuntime(Object session) {
        java.util.function.Predicate<Owner> matches = owner -> owner.session() == session;
        removeOwned(ENTITY_HOOKS, matches);
        removeWorld(matches);
        HUD_HOOKS.removeOwned(matches);
    }

    private static void removeOwned(ConcurrentMap<String, HookBucket> source,
                               java.util.function.Predicate<Owner> predicate) {
        source.forEach((id, ignored) -> source.computeIfPresent(id, (key, bucket) -> {
            bucket.removeOwned(predicate);
            return bucket.isEmpty() ? null : bucket;
        }));
    }

    private static void removeWorld(java.util.function.Predicate<Owner> predicate) {
        removeOwned(WORLD_HOOKS, predicate);
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
        if (hook.failed) return;
        MLuaContext context = hook.context;
        try {
            synchronized (context.executionLock()) {
                MLuaLuaBindings.bindContext(context.globals(), context);
                hook.callback.invoke(invocationArgs);
            }
        } catch (Throwable failure) {
            hook.failed = true;
            MLuaLogger.LOGGER.error("[MLua] Disabled Lua {} render callback after an error",
                hook.target, failure);
        }
    }

    private static LuaValue renderContext(Hook hook, String target, Object poseStack, Object bufferSource,
            Object model, float partialTick, int packedLight, int packedOverlay, Object nativeContext,
            Object... additionalFields) {
        var context = new org.luaj.vm2.LuaTable();
        context.set("Target", LuaValue.valueOf(target));
        context.set("PoseStack", userdata(poseStack));
        context.set("BufferSource", userdata(bufferSource));
        context.set("Model", userdata(model));
        context.set("PartialTick", LuaValue.valueOf(partialTick));
        context.set("PackedLight", LuaValue.valueOf(packedLight));
        context.set("PackedOverlay", LuaValue.valueOf(packedOverlay));
        context.set("NativeContext", userdata(nativeContext));
        context.set("PassId", LuaValue.valueOf(hook.id));
        for (int index = 0; index + 1 < additionalFields.length; index += 2) {
            context.set((String) additionalFields[index], (LuaValue) additionalFields[index + 1]);
        }
        return hook.context.mlua().registration().attachClassInstance(context, "RenderContext", null);
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

    private record Owner(Object session, String modId) { }
    private record HookKey(Owner owner, String id) { }

    /** Keeps a sorted immutable dispatch list so render frames do not allocate or sort callback snapshots. */
    private static final class HookBucket {
        private final Map<HookKey, Hook> hooks = new HashMap<>();
        private volatile List<Hook> snapshot = List.of();

        private synchronized void put(HookKey key, Hook hook) {
            hooks.put(key, hook);
            rebuildSnapshot();
        }

        private synchronized boolean remove(HookKey key) {
            if (hooks.remove(key) == null) return false;
            rebuildSnapshot();
            return true;
        }

        private synchronized void removeOwned(java.util.function.Predicate<Owner> predicate) {
            if (hooks.keySet().removeIf(key -> predicate.test(key.owner()))) rebuildSnapshot();
        }

        private boolean isEmpty() {
            return snapshot.isEmpty();
        }

        private List<Hook> snapshot() {
            return snapshot;
        }

        private void rebuildSnapshot() {
            List<Map.Entry<HookKey, Hook>> ordered = new ArrayList<>(hooks.size());
            hooks.forEach((key, hook) -> ordered.add(Map.entry(key, hook)));
            ordered.sort(Comparator
                .comparing((Map.Entry<HookKey, Hook> entry) -> entry.getKey().owner().modId())
                .thenComparing(entry -> entry.getKey().id()));
            snapshot = ordered.stream().map(Map.Entry::getValue).toList();
        }
    }

    private static final class Hook {
        private final LuaValue callback;
        private final MLuaContext context;
        private final String target;
        private final String id;
        private volatile boolean failed;

        private Hook(LuaValue callback, MLuaContext context, String target, String id) {
            this.callback = callback;
            this.context = context;
            this.target = target;
            this.id = id;
        }
    }
}
