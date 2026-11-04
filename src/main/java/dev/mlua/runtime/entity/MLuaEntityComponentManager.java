package dev.mlua.runtime.entity;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaContext;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.minecraft.entity.MLuaEntityLookup;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public final class MLuaEntityComponentManager {
    public static final int MAX_COMPONENTS = 65_536;
    public static final int MAX_COMPONENTS_PER_ENTITY = 128;
    // The callback adapter only reads and copies this table; keep it shared for hot tick dispatch.
    private static final LuaTable EMPTY_EVENT = new LuaTable();
    private static final ConcurrentHashMap<EntityComponentsKey, EntityComponents> COMPONENTS = new ConcurrentHashMap<>();
    private static final AtomicInteger ACTIVE_COMPONENTS = new AtomicInteger();
    private static final AtomicLong NEXT_COMPONENT_ORDER = new AtomicLong();

    private MLuaEntityComponentManager() {
    }

    public static LuaTable attach(Entity entity, Side side, String name, LuaTable spec) {
        return attach(entity, side, name, spec, null);
    }

    public static LuaTable attach(Entity entity, Side side, String name, LuaTable spec, String ownerModId) {
        return attach(entity, side, name, spec, ownerModId, null);
    }

    public static LuaTable attach(Entity entity, Side side, String name, LuaTable spec, String ownerModId, MLuaContext context) {
        if (entity == null) {
            throw new LuaError("[MLua] Cannot attach component to a null entity");
        }
        if (name == null || name.isBlank()) {
            throw new LuaError("[MLua] Component name must be a non-empty string");
        }
        validateSpec(name, spec);
        Object runtimeOwner = context != null ? context.session() : null;
        ComponentKey key = componentKey(runtimeOwner, ownerModId, name);
        EntityComponentsKey entityKey = entityKey(entity, side);
        boolean created = false;
        MLuaEntityComponentInstance instance;
        while (true) {
            EntityComponents entityComponents = components(entity, side);
            synchronized (entityComponents) {
                if (COMPONENTS.get(entityKey) != entityComponents) continue;
                instance = entityComponents.components.get(key);
                if (instance == null) {
                    if (entityComponents.components.size() >= MAX_COMPONENTS_PER_ENTITY) {
                        throw new LuaError("[MLua] Entity already has the maximum of " + MAX_COMPONENTS_PER_ENTITY
                            + " MLua behavior components.");
                    }
                    reserveComponent();
                    try {
                        instance = new MLuaEntityComponentInstance(entity, side, name, ownerModId, spec, context,
                            NEXT_COMPONENT_ORDER.getAndIncrement());
                    } catch (RuntimeException | Error failure) {
                        ACTIVE_COMPONENTS.decrementAndGet();
                        throw failure;
                    }
                    entityComponents.components.put(key, instance);
                    created = true;
                    rebuildSnapshot(entityComponents);
                }
                break;
            }
        }
        // Replacing callbacks may acquire the Lua execution lock. Do so after releasing the
        // bucket monitor: a running callback can remove itself while holding that same lock.
        if (!created) instance.replaceCallbacks(spec, context);
        if (created) invokeCallback(instance, "OnAdded", "added", EMPTY_EVENT);
        return instance.handle();
    }

    private static void validateSpec(String name, LuaTable spec) {
        String[] callbacks = {"OnAdded", "OnRemoved", "OnTick", "OnClientTick", "OnHurt", "OnDeath", "OnInteract"};
        for (String callbackName : callbacks) {
            LuaValue callback = spec.get(callbackName);
            if (callback.isnil()) {
                String lowerCamel = Character.toLowerCase(callbackName.charAt(0)) + callbackName.substring(1);
                callback = spec.get(lowerCamel);
            }
            if (!callback.isnil() && !callback.isfunction()) {
                throw new LuaError("[MLua] Component '" + name + "' callback '" + callbackName + "' must be a function");
            }
        }
        LuaValue state = spec.get("State");
        if (state.isnil()) state = spec.get("state");
        if (!state.isnil() && !state.istable()) {
            throw new LuaError("[MLua] Component '" + name + "' State must be a table");
        }
    }

    public static LuaTable get(Entity entity, Side side, String name) {
        return get(entity, side, name, null);
    }

    public static LuaTable get(Entity entity, Side side, String name, String ownerModId) {
        return get(entity, side, name, ownerModId, null);
    }

    public static LuaTable get(Entity entity, Side side, String name, String ownerModId, Object runtimeOwner) {
        LuaValue found = find(entity, side, name, ownerModId, runtimeOwner);
        return found.istable() ? found.checktable() : new LuaTable();
    }

    public static LuaValue find(Entity entity, Side side, String name, String ownerModId, Object runtimeOwner) {
        EntityComponents components = COMPONENTS.get(entityKey(entity, side));
        if (components == null) {
            return LuaValue.NIL;
        }
        MLuaEntityComponentInstance instance = components.components.get(componentKey(runtimeOwner, ownerModId, name));
        return instance != null ? instance.handle() : LuaValue.NIL;
    }

    public static boolean has(Entity entity, Side side, String name, String ownerModId, Object runtimeOwner) {
        return !find(entity, side, name, ownerModId, runtimeOwner).isnil();
    }

    public static List<String> list(Entity entity, Side side) {
        return list(entity, side, null);
    }

    public static List<String> list(Entity entity, Side side, String ownerModId) {
        return list(entity, side, ownerModId, null);
    }

    public static List<String> list(Entity entity, Side side, String ownerModId, Object runtimeOwner) {
        EntityComponents components = COMPONENTS.get(entityKey(entity, side));
        if (components == null) {
            return List.of();
        }
        var names = components.snapshot.stream()
                .filter(instance -> (ownerModId == null || ownerModId.equals(instance.ownerModId()))
                        && instance.runtimeOwner() == runtimeOwner)
                .map(MLuaEntityComponentInstance::name);
        // A scoped snapshot is already ordered by owner and then name. Preserve
        // the historical global name order when callers request every owner.
        return ownerModId == null ? names.sorted().toList() : names.toList();
    }

    public static List<LuaTable> handles(Entity entity, Side side, String ownerModId, Object runtimeOwner) {
        EntityComponents components = COMPONENTS.get(entityKey(entity, side));
        if (components == null) return List.of();
        var matches = components.snapshot.stream()
                .filter(instance -> (ownerModId == null || ownerModId.equals(instance.ownerModId()))
                        && instance.runtimeOwner() == runtimeOwner);
        if (ownerModId == null) {
            matches = matches.sorted(java.util.Comparator.comparing(MLuaEntityComponentInstance::name));
        }
        return matches.map(MLuaEntityComponentInstance::handle).toList();
    }

    public static boolean remove(Entity entity, Side side, String name) {
        return remove(entity, side, name, null);
    }

    public static boolean remove(Entity entity, Side side, String name, String ownerModId) {
        return remove(entity, side, name, ownerModId, null);
    }

    public static boolean remove(Entity entity, Side side, String name, String ownerModId, Object runtimeOwner) {
        EntityComponents components = COMPONENTS.get(entityKey(entity, side));
        if (components == null) {
            return false;
        }
        ComponentKey key = componentKey(runtimeOwner, ownerModId, name);
        MLuaEntityComponentInstance instance = removeComponent(components, key, null);
        if (instance != null) {
            removeInstance(instance);
        }
        pruneIfEmpty(entityKey(entity, side), components);
        return instance != null;
    }

    public static int clear(Entity entity, Side side) {
        return clear(entity, side, null);
    }

    public static int clear(Entity entity, Side side, String ownerModId) {
        return clear(entity, side, ownerModId, null);
    }

    public static int clear(Entity entity, Side side, String ownerModId, Object runtimeOwner) {
        EntityComponentsKey key = entityKey(entity, side);
        EntityComponents current = COMPONENTS.get(key);
        if (current == null) return 0;
        int removed = 0;
        for (Map.Entry<ComponentKey, MLuaEntityComponentInstance> entry : current.components.entrySet()) {
            MLuaEntityComponentInstance instance = entry.getValue();
            if ((ownerModId == null || ownerModId.equals(instance.ownerModId()))
                    && instance.runtimeOwner() == runtimeOwner
                    && removeComponent(current, entry.getKey(), instance) != null) {
                removed++;
                removeInstance(instance);
            }
        }
        pruneIfEmpty(key, current);
        return removed;
    }

    public static void clearAll() {
        for (Map.Entry<EntityComponentsKey, EntityComponents> entry : COMPONENTS.entrySet()) {
            detachAll(entry.getKey(), entry.getValue())
                .forEach(MLuaEntityComponentManager::removeInstance);
        }
    }

    public static void clearEntity(Entity entity) {
        if (entity == null) return;
        clearEntity(MLuaEntityScope.of(entity));
    }

    public static void clearEntity(MLuaEntityScope scope) {
        if (scope == null) return;
        for (Map.Entry<EntityComponentsKey, EntityComponents> entry : COMPONENTS.entrySet()) {
            if (!entry.getKey().scope().equals(scope)) continue;
            detachAll(entry.getKey(), entry.getValue())
                .forEach(MLuaEntityComponentManager::removeInstance);
        }
    }

    public static void clearSide(Side side) {
        for (Map.Entry<EntityComponentsKey, EntityComponents> entry : COMPONENTS.entrySet()) {
            EntityComponents components = entry.getValue();
            if (side != Side.BOTH && components.side != side) continue;
            detachAll(entry.getKey(), components)
                .forEach(MLuaEntityComponentManager::removeInstance);
        }
    }

    public static void clearOwned(Side side, String ownerModId) {
        clearOwned(null, side, ownerModId);
    }

    public static void clearOwned(Object runtimeOwner, Side side, String ownerModId) {
        if (ownerModId == null) return;
        for (Map.Entry<EntityComponentsKey, EntityComponents> entry : COMPONENTS.entrySet()) {
            EntityComponents components = entry.getValue();
            if (side != Side.BOTH && components.side != side) continue;
            for (Map.Entry<ComponentKey, MLuaEntityComponentInstance> componentEntry : components.components.entrySet()) {
                MLuaEntityComponentInstance instance = componentEntry.getValue();
                if (!ownerModId.equals(instance.ownerModId()) || instance.runtimeOwner() != runtimeOwner) continue;
                if (removeComponent(components, componentEntry.getKey(), instance) != null) {
                    removeInstance(instance);
                }
            }
            pruneIfEmpty(entry.getKey(), components);
        }
    }

    public static void clearRuntime(Object runtimeOwner, Side side) {
        if (runtimeOwner == null) return;
        for (Map.Entry<EntityComponentsKey, EntityComponents> entry : COMPONENTS.entrySet()) {
            EntityComponents components = entry.getValue();
            if (side != Side.BOTH && components.side != side) continue;
            for (Map.Entry<ComponentKey, MLuaEntityComponentInstance> componentEntry : components.components.entrySet()) {
                MLuaEntityComponentInstance instance = componentEntry.getValue();
                if (instance.runtimeOwner() == runtimeOwner
                        && removeComponent(components, componentEntry.getKey(), instance) != null) {
                    removeInstance(instance);
                }
            }
            pruneIfEmpty(entry.getKey(), components);
        }
    }

    public static void tick(Side side) {
        for (Map.Entry<EntityComponentsKey, EntityComponents> entry : COMPONENTS.entrySet()) {
            EntityComponents components = entry.getValue();
            if (components.side != side) {
                continue;
            }
            Entity entity = components.entityRef.get();
            if (entity == null || !entity.isAlive() || !components.scope.equals(MLuaEntityScope.of(entity))) {
                detachAll(entry.getKey(), components)
                    .forEach(MLuaEntityComponentManager::removeInstance);
                continue;
            }
            for (MLuaEntityComponentInstance instance : orderedComponents(components)) {
                if (!instance.enabled()) {
                    continue;
                }
                invokeCallback(instance, side == Side.CLIENT ? "OnClientTick" : "OnTick",
                    side == Side.CLIENT ? "client_tick" : "tick", EMPTY_EVENT);
            }
        }
    }

    public static void onHurt(Entity entity, LuaTable event) {
        dispatch(entity, Side.SERVER, "OnHurt", "hurt", event);
    }

    public static int activeComponentCount() {
        return ACTIVE_COMPONENTS.get();
    }

    static void releaseComponent() {
        ACTIVE_COMPONENTS.decrementAndGet();
    }

    private static void reserveComponent() {
        while (true) {
            int current = ACTIVE_COMPONENTS.get();
            if (current >= MAX_COMPONENTS) {
                throw new LuaError("[MLua] MLua already has the maximum of " + MAX_COMPONENTS
                    + " active entity behavior components; remove unused components before attaching more.");
            }
            if (ACTIVE_COMPONENTS.compareAndSet(current, current + 1)) return;
        }
    }

    private static List<MLuaEntityComponentInstance> orderedComponents(EntityComponents components) {
        return components.snapshot;
    }

    private static void rebuildSnapshot(EntityComponents components) {
        components.snapshot = components.components.values().stream()
            .sorted(java.util.Comparator.comparing((MLuaEntityComponentInstance instance) -> instance.ownerModId(),
                    java.util.Comparator.nullsFirst(String::compareTo))
                .thenComparing(MLuaEntityComponentInstance::name)
                .thenComparingLong(instance -> instance.registrationOrder()))
            .toList();
    }

    private static MLuaEntityComponentInstance removeComponent(EntityComponents components, ComponentKey key,
            MLuaEntityComponentInstance expected) {
        synchronized (components) {
            MLuaEntityComponentInstance removed = expected == null
                ? components.components.remove(key)
                : components.components.remove(key, expected) ? expected : null;
            if (removed == null) return null;
            rebuildSnapshot(components);
            return removed;
        }
    }

    /**
     * Removes a whole entity bucket under the same lock used by attach/remove, then returns its
     * instances for callbacks outside the lock. An attach that captured this bucket will observe
     * that it is no longer current and retry against a fresh bucket.
     */
    private static List<MLuaEntityComponentInstance> detachAll(EntityComponentsKey key, EntityComponents components) {
        synchronized (components) {
            if (!COMPONENTS.remove(key, components)) return List.of();
            List<MLuaEntityComponentInstance> removed = components.snapshot;
            components.components.clear();
            components.snapshot = List.of();
            return removed;
        }
    }

    private static void pruneIfEmpty(EntityComponentsKey key, EntityComponents components) {
        synchronized (components) {
            if (components.components.isEmpty()) COMPONENTS.remove(key, components);
        }
    }

    public static void onDeath(Entity entity, LuaTable event) {
        dispatch(entity, Side.SERVER, "OnDeath", "death", event);
    }

    public static void onInteract(Entity entity, LuaTable event) {
        dispatch(entity, Side.SERVER, "OnInteract", "interact", event);
    }

    public static Entity resolveEntity(LuaValue value, Side side) {
        return resolveEntity(value, side, MLuaRuntime.get().server());
    }

    public static Entity resolveEntity(LuaValue value, Side side, MinecraftServer server) {
        if (value.istable()) {
            LuaValue entity = value.get("__entity");
            if (entity.isuserdata(Entity.class)) {
                return (Entity) entity.checkuserdata(Entity.class);
            }
            LuaValue id = value.get("Id");
            if (id.isint()) {
                return resolveEntity(id.toint(), side, server);
            }
        }
        if (value.isint()) {
            return resolveEntity(value.toint(), side, server);
        }
        throw new LuaError("[MLua] Expected entity table or entity id");
    }

    private static Entity resolveEntity(int entityId, Side side, MinecraftServer server) {
        if (side == Side.CLIENT) {
            Minecraft mc = Minecraft.getInstance();
            return mc.level != null ? mc.level.getEntity(entityId) : null;
        }
        if (server == null) {
            return null;
        }
        MLuaEntityLookup.LookupResult<Entity> lookup = MLuaEntityLookup.findById(server, entityId);
        if (lookup.ambiguous()) {
            throw new LuaError("[MLua] Entity id " + entityId + " exists in multiple dimensions; pass an entity object");
        }
        return lookup.value();
    }

    private static void dispatch(Entity entity, Side side, String callbackName, String eventName, LuaTable event) {
        EntityComponents components = COMPONENTS.get(entityKey(entity, side));
        if (components == null) {
            return;
        }
        for (MLuaEntityComponentInstance instance : orderedComponents(components)) {
            if (!instance.enabled()) {
                continue;
            }
            invokeCallback(instance, callbackName, eventName, event);
        }
    }

    private static void invokeCallback(MLuaEntityComponentInstance instance, String callbackName, String eventName, LuaTable event) {
        MLuaEntityComponentCallbacks.invoke(instance, callbackName, eventName, event);
    }

    private static void removeInstance(MLuaEntityComponentInstance instance) {
        if (instance.markRemoved()) invokeCallback(instance, "OnRemoved", "removed", EMPTY_EVENT);
    }

    private static EntityComponents components(Entity entity, Side side) {
        return COMPONENTS.compute(entityKey(entity, side), (key, existing) -> {
            if (existing == null) return new EntityComponents(entity, side, key.scope());
            existing.rebind(entity);
            return existing;
        });
    }

    private static EntityComponentsKey entityKey(Entity entity, Side side) {
        return entityKey(MLuaEntityScope.of(entity), side);
    }

    static EntityComponentsKey entityKey(MLuaEntityScope scope, Side side) {
        return new EntityComponentsKey(side, scope);
    }

    private static ComponentKey componentKey(Object runtimeOwner, String ownerModId, String name) {
        return new ComponentKey(runtimeOwner, ownerModId, name);
    }

    static record ComponentKey(Object runtimeOwner, String ownerModId, String name) {}

    static record EntityComponentsKey(Side side, MLuaEntityScope scope) {
    }

    private static final class EntityComponents {
        private volatile WeakReference<Entity> entityRef;
        private final Side side;
        private final MLuaEntityScope scope;
        private final ConcurrentHashMap<ComponentKey, MLuaEntityComponentInstance> components = new ConcurrentHashMap<>();
        private volatile List<MLuaEntityComponentInstance> snapshot = List.of();

        private EntityComponents(Entity entity, Side side, MLuaEntityScope scope) {
            this.entityRef = new WeakReference<>(entity);
            this.side = side;
            this.scope = scope;
        }

        private void rebind(Entity entity) {
            this.entityRef = new WeakReference<>(entity);
            for (MLuaEntityComponentInstance instance : components.values()) instance.rebind(entity);
        }
    }

}
