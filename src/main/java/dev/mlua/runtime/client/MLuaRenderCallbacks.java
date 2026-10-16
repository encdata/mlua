package dev.mlua.runtime.client;

import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.world.entity.LivingEntity;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Addon-owned Lua callbacks attached to Minecraft's client render points. */
public final class MLuaRenderCallbacks {
    private static final ConcurrentMap<String, ConcurrentMap<Owner, Hook>> ENTITY_HOOKS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Owner, Hook> WORLD_HOOKS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Owner, Hook> HUD_HOOKS = new ConcurrentHashMap<>();

    private MLuaRenderCallbacks() { }

    public static void register(String entityId, LuaValue callback, MLuaContext context) {
        if (context == null) throw new IllegalArgumentException("Render callback needs its owning Lua context");
        if (callback == null || !callback.isfunction()) throw new IllegalArgumentException("Entity renderer callback must be a function");
        String id = MLuaResourceLocations.parse(entityId).toString();
        Owner owner = new Owner(context.session(), context.ownerModId());
        ENTITY_HOOKS.computeIfAbsent(id, ignored -> new ConcurrentHashMap<>())
            .put(owner, new Hook(callback, context));
    }

    public static boolean unregister(String entityId, MLuaContext context) {
        if (context == null) return false;
        String id = MLuaResourceLocations.parse(entityId).toString();
        Owner owner = new Owner(context.session(), context.ownerModId());
        ConcurrentMap<Owner, Hook> hooks = ENTITY_HOOKS.get(id);
        if (hooks == null || hooks.remove(owner) == null) return false;
        if (hooks.isEmpty()) ENTITY_HOOKS.remove(id, hooks);
        return true;
    }

    public static void registerWorld(LuaValue callback, MLuaContext context) {
        WORLD_HOOKS.put(owner(context), new Hook(requireCallback(callback), context, "world"));
    }

    public static boolean unregisterWorld(MLuaContext context) {
        return WORLD_HOOKS.remove(owner(context)) != null;
    }

    public static void registerHud(LuaValue callback, MLuaContext context) {
        HUD_HOOKS.put(owner(context), new Hook(requireCallback(callback), context, "HUD"));
    }

    public static boolean unregisterHud(MLuaContext context) {
        return HUD_HOOKS.remove(owner(context)) != null;
    }

    public static void render(LivingEntity entity, Object model, Object poseStack, Object bufferSource,
                              float partialTick, int packedLight, int packedOverlay) {
        if (entity == null) return;
        var typeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (typeId == null) return;
        ConcurrentMap<Owner, Hook> registered = ENTITY_HOOKS.get(typeId.toString());
        if (registered == null || registered.isEmpty()) return;
        List<Map.Entry<Owner, Hook>> snapshot = new ArrayList<>(registered.entrySet());
        snapshot.sort(java.util.Comparator.comparing(entry -> entry.getKey().modId()));
        var context = renderContext("EntityModel", poseStack, bufferSource, model, partialTick,
            packedLight, packedOverlay, null);
        for (Map.Entry<Owner, Hook> entry : snapshot) {
            invoke(entry.getValue(), new LuaValue[]{MLuaEntityWrapper.entityTable(entity), context});
        }
    }

    public static void renderWorld(Object nativeContext, Object poseStack, Object bufferSource, Object camera) {
        var context = renderContext("World", poseStack, bufferSource, null, 0.0F, 0, 0, nativeContext);
        context.set("Camera", userdata(camera));
        invokeSnapshot(WORLD_HOOKS, new LuaValue[]{context});
    }

    public static void renderHud(Object graphics, int width, int height) {
        var context = renderContext("Hud", null, null, null, 0.0F, 0, 0, null);
        context.set("Graphics", userdata(graphics));
        context.set("Width", LuaValue.valueOf(width));
        context.set("Height", LuaValue.valueOf(height));
        invokeSnapshot(HUD_HOOKS, new LuaValue[]{context});
    }

    public static void clearOwned(Object session, String modId) {
        java.util.function.Predicate<Owner> matches = owner -> owner.session() == session && owner.modId().equals(modId);
        remove(ENTITY_HOOKS, matches);
        WORLD_HOOKS.keySet().removeIf(matches);
        HUD_HOOKS.keySet().removeIf(matches);
    }

    public static void clearRuntime(Object session) {
        java.util.function.Predicate<Owner> matches = owner -> owner.session() == session;
        remove(ENTITY_HOOKS, matches);
        WORLD_HOOKS.keySet().removeIf(matches);
        HUD_HOOKS.keySet().removeIf(matches);
    }

    private static void remove(ConcurrentMap<String, ConcurrentMap<Owner, Hook>> source,
                               java.util.function.Predicate<Owner> predicate) {
        source.forEach((id, hooks) -> {
            hooks.keySet().removeIf(predicate);
            if (hooks.isEmpty()) source.remove(id, hooks);
        });
    }

    private static void invokeSnapshot(ConcurrentMap<Owner, Hook> hooks, LuaValue[] args) {
        List<Map.Entry<Owner, Hook>> snapshot = new ArrayList<>(hooks.entrySet());
        snapshot.sort(java.util.Comparator.comparing(entry -> entry.getKey().modId()));
        for (Map.Entry<Owner, Hook> entry : snapshot) invoke(entry.getValue(), args);
    }

    private static void invoke(Hook hook, LuaValue[] args) {
        if (hook.failed) return;
        MLuaContext context = hook.context;
        try {
            synchronized (context.executionLock()) {
                MLuaLuaBindings.bindContext(context.globals(), context);
                hook.callback.invoke(LuaValue.varargsOf(args));
            }
        } catch (Throwable failure) {
            hook.failed = true;
            MLuaLogger.LOGGER.error("[MLua] Disabled Lua {} render callback after an error",
                hook.target, failure);
        }
    }

    private static org.luaj.vm2.LuaTable renderContext(String target, Object poseStack, Object bufferSource,
            Object model, float partialTick, int packedLight, int packedOverlay, Object nativeContext) {
        var context = new org.luaj.vm2.LuaTable();
        context.set("Target", LuaValue.valueOf(target));
        context.set("PoseStack", userdata(poseStack));
        context.set("BufferSource", userdata(bufferSource));
        context.set("Model", userdata(model));
        context.set("PartialTick", LuaValue.valueOf(partialTick));
        context.set("PackedLight", LuaValue.valueOf(packedLight));
        context.set("PackedOverlay", LuaValue.valueOf(packedOverlay));
        context.set("NativeContext", userdata(nativeContext));
        return context;
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

    private static final class Hook {
        private final LuaValue callback;
        private final MLuaContext context;
        private final String target;
        private volatile boolean failed;

        private Hook(LuaValue callback, MLuaContext context) {
            this(callback, context, "entity");
        }

        private Hook(LuaValue callback, MLuaContext context, String target) {
            this.callback = callback;
            this.context = context;
            this.target = target;
        }
    }
}
