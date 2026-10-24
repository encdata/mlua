package dev.mlua.runtime.client;

import dev.mlua.core.MLuaContext;
import dev.mlua.core.signal.MLuaConnection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Owns render hook registration, ordering, capacity, and addon/runtime teardown. */
final class MLuaRenderHookRegistry {
    static final int MAX_CALLBACKS = 4096;
    static final int MAX_CALLBACKS_PER_TARGET = 256;
    static final int MAX_CALLBACKS_PER_OWNER = 512;
    private static final AtomicLong NEXT_REGISTRATION_ORDER = new AtomicLong();
    private static final AtomicInteger REGISTERED_CALLBACKS = new AtomicInteger();
    private static final Object CALLBACK_LIMIT_LOCK = new Object();
    /** Serializes callback installation with runtime/addon teardown. */
    private static final Object HOOK_LIFECYCLE_LOCK = new Object();
    private static final Map<Owner, Integer> REGISTERED_CALLBACKS_BY_OWNER = new HashMap<>();
    /** A target kind keeps entity, item, world, and HUD hooks in separate namespaces. */
    private static final ConcurrentMap<HookTarget, HookBucket> HOOKS = new ConcurrentHashMap<>();
    private static final Object WORLD_REGISTRATION_LOCK = new Object();
    static final HookTarget HUD_TARGET = new HookTarget(HookKind.HUD, "");
    static final HookTarget WORLD_GEOMETRY_TARGET = new HookTarget(HookKind.WORLD_GEOMETRY, "");

    private MLuaRenderHookRegistry() { }

    static Owner owner(MLuaContext context) {
        if (context == null) throw new LuaError("[MLua] Render callback registration needs its owning Lua context.");
        return new Owner(context.session(), context.ownerModId());
    }

    static String normalizeId(String id) {
        net.minecraft.resources.ResourceLocation location =
            dev.mlua.minecraft.MLuaResourceLocations.tryParseOrNull(id);
        if (location == null) {
            throw new LuaError("[MLua] Render registration id must be a valid resource location (namespace:path).");
        }
        return location.toString();
    }

    static Hook registerTarget(HookTarget target, HookKey key, LuaValue callback,
            MLuaContext context, String description) {
        return registerTarget(target, key, callback, context, description, null);
    }

    static Hook registerTarget(HookTarget target, HookKey key, LuaValue callback,
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

    static MLuaConnection registerTargetConnection(HookTarget target, HookKey key, LuaValue callback,
            MLuaContext context, String description) {
        Hook hook = registerTarget(target, key, callback, context, description);
        return connection(target, hook);
    }

    static MLuaConnection connection(HookTarget target, Hook hook) {
        return MLuaConnection.owned(hook,
            expected -> unregisterTarget(target, expected.key, expected),
            expected -> expected.active && !expected.failed);
    }

    static boolean unregisterTarget(HookTarget target, HookKey key) {
        java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean();
        HOOKS.computeIfPresent(target, (ignored, bucket) -> {
            removed.set(bucket.remove(key));
            return bucket.isEmpty() ? null : bucket;
        });
        return removed.get();
    }

    static boolean unregisterTarget(HookTarget target, HookKey key, Hook expected) {
        java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean();
        HOOKS.computeIfPresent(target, (ignored, bucket) -> {
            removed.set(bucket.remove(key, expected));
            return bucket.isEmpty() ? null : bucket;
        });
        return removed.get();
    }

    static HookBucket bucket(HookKind kind, String key) {
        return HOOKS.get(new HookTarget(kind, key));
    }

    static void removeFailed(Hook hook) {
        if (hook != null && hook.bucket != null) hook.bucket.removeFailed(hook.key, hook);
    }

    static List<Hook> renderHooks(HookBucket bucket) {
        if (bucket == null || bucket.isEmpty()) return List.of();
        if (!MLuaClientRenderThread.require("Lua render callback dispatch")) return List.of();
        return bucket.snapshot();
    }

    static void clearOwned(Object session, String modId) {
        if (session == null || modId == null || modId.isBlank()) return;
        Predicate<Owner> matches = owner -> owner.session() == session && owner.modId().equals(modId);
        synchronized (WORLD_REGISTRATION_LOCK) {
            synchronized (HOOK_LIFECYCLE_LOCK) {
                removeOwned(matches);
            }
        }
    }

    static void clearRuntime(Object session) {
        if (session == null) return;
        Predicate<Owner> matches = owner -> owner.session() == session;
        synchronized (WORLD_REGISTRATION_LOCK) {
            synchronized (HOOK_LIFECYCLE_LOCK) {
                removeOwned(matches);
            }
        }
    }

    private static void removeOwned(Predicate<Owner> predicate) {
        HOOKS.forEach((target, ignored) -> HOOKS.computeIfPresent(target, (key, bucket) -> {
            bucket.removeOwned(predicate);
            return bucket.isEmpty() ? null : bucket;
        }));
    }

    static int registeredCallbackCount() {
        return REGISTERED_CALLBACKS.get();
    }

    static int registeredCallbackCount(MLuaContext context) {
        if (context == null) return 0;
        synchronized (CALLBACK_LIMIT_LOCK) {
            return REGISTERED_CALLBACKS_BY_OWNER.getOrDefault(owner(context), 0);
        }
    }

    static MLuaConnection registerWorldPass(String id, String stage, LuaValue callback, MLuaContext context) {
        HookKey key = new HookKey(owner(context), id);
        HookTarget destination = new HookTarget(HookKind.WORLD, stage);
        synchronized (WORLD_REGISTRATION_LOCK) {
            synchronized (HOOK_LIFECYCLE_LOCK) {
                HookBucket targetBucket = HOOKS.get(destination);
                boolean alreadyAtDestination = targetBucket != null && targetBucket.contains(key);
                if (!alreadyAtDestination && targetBucket != null && targetBucket.size() >= MAX_CALLBACKS_PER_TARGET) {
                    throw new LuaError("[MLua] Render target 'world stage " + stage
                        + "' already has the maximum of " + MAX_CALLBACKS_PER_TARGET + " callbacks");
                }

                List<PreviousWorldHook> previous = HOOKS.entrySet().stream()
                    .filter(entry -> entry.getKey().kind() == HookKind.WORLD
                        && !entry.getKey().equals(destination))
                    .map(entry -> new PreviousWorldHook(entry.getKey(), entry.getValue().get(key)))
                    .filter(entry -> entry.hook() != null)
                    .toList();
                previous.forEach(entry -> unregisterTarget(entry.target(), key));
                try {
                    Hook hook = registerTarget(destination, key, callback, context,
                        "world pass '" + id + "' at " + stage);
                    return connection(destination, hook);
                } catch (RuntimeException failure) {
                    for (PreviousWorldHook entry : previous) {
                        Hook old = entry.hook();
                        registerTarget(entry.target(), key, old.callback, old.context, old.target,
                            old.registrationOrder);
                    }
                    throw failure;
                }
            }
        }
    }

    static boolean unregisterWorldPass(HookKey key) {
        synchronized (WORLD_REGISTRATION_LOCK) {
            boolean removed = false;
            for (HookTarget target : HOOKS.keySet()) {
                if (target.kind() == HookKind.WORLD) removed |= unregisterTarget(target, key);
            }
            return removed;
        }
    }

    static boolean unregisterWorldStage(String stage, HookKey key) {
        synchronized (WORLD_REGISTRATION_LOCK) {
            return unregisterTarget(new HookTarget(HookKind.WORLD, stage), key);
        }
    }

    private static HookBucket registryBucket(HookTarget endpoint) {
        return new HookBucket((bucket, key, expected) -> HOOKS.computeIfPresent(endpoint, (ignored, current) -> {
            if (current != bucket) return current;
            bucket.remove(key, expected);
            return bucket.isEmpty() ? null : bucket;
        }));
    }

    static LuaValue requireCallback(LuaValue callback) {
        if (callback == null || !callback.isfunction()) {
            throw new LuaError("[MLua] Render callback must be a function.");
        }
        return callback;
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

    enum HookKind {
        ENTITY_MODEL, ENTITY_DRAW, ENTITY_DRAW_POST, FIRST_PERSON_ITEM, THIRD_PERSON_ITEM, WORLD,
        WORLD_GEOMETRY, HUD
    }

    record HookTarget(HookKind kind, String key) { }

    record Owner(Object session, String modId) {
        Owner { if (modId == null) modId = ""; }

        @Override public boolean equals(Object other) {
            return other instanceof Owner owner && session == owner.session() && modId.equals(owner.modId());
        }

        @Override public int hashCode() {
            return 31 * System.identityHashCode(session) + modId.hashCode();
        }
    }

    record HookKey(Owner owner, String id) { }
    private record PreviousWorldHook(HookTarget target, Hook hook) { }

    /** Publishes immutable ordered snapshots so render dispatch never sorts hook registrations. */
    static final class HookBucket {
        private final Map<HookKey, Hook> hooks = new HashMap<>();
        private final HookRemoval removal;
        private volatile List<Hook> snapshot = List.of();

        private HookBucket(HookRemoval removal) { this.removal = removal; }

        synchronized Hook put(HookKey key, LuaValue callback, MLuaContext context, String target,
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

        synchronized boolean remove(HookKey key) {
            Hook removed = hooks.remove(key);
            if (removed == null) return false;
            removed.active = false;
            releaseCallback(key.owner());
            rebuildSnapshot();
            return true;
        }

        synchronized boolean remove(HookKey key, Hook expected) {
            if (!hooks.remove(key, expected)) return false;
            expected.active = false;
            releaseCallback(key.owner());
            rebuildSnapshot();
            return true;
        }

        void removeFailed(HookKey key, Hook expected) {
            if (removal == null) remove(key, expected);
            else removal.remove(this, key, expected);
        }

        synchronized void removeOwned(Predicate<Owner> predicate) {
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
            if (removed > 0) rebuildSnapshot();
        }

        boolean isEmpty() { return snapshot.isEmpty(); }
        synchronized boolean contains(HookKey key) { return hooks.containsKey(key); }
        synchronized int size() { return hooks.size(); }
        synchronized Hook get(HookKey key) { return hooks.get(key); }
        List<Hook> snapshot() { return snapshot; }

        private void rebuildSnapshot() {
            List<Map.Entry<HookKey, Hook>> ordered = new ArrayList<>(hooks.size());
            hooks.forEach((key, hook) -> ordered.add(Map.entry(key, hook)));
            ordered.sort(Comparator.comparingLong(entry -> entry.getValue().registrationOrder));
            snapshot = ordered.stream().map(Map.Entry::getValue).toList();
        }
    }

    @FunctionalInterface
    private interface HookRemoval {
        void remove(HookBucket bucket, HookKey key, Hook expected);
    }

    static final class Hook {
        final LuaValue callback;
        final MLuaContext context;
        final String target;
        final String id;
        final HookKey key;
        final HookBucket bucket;
        final long registrationOrder;
        volatile boolean active = true;
        volatile boolean failed;

        Hook(LuaValue callback, MLuaContext context, String target, HookKey key,
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
