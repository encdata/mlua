package dev.mlua.runtime.entity;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.service.MLuaServiceProperties;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.mcapi.MCApiRuntime;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.minecraft.entity.MLuaEntityLookup;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
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
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaEntityComponentManager {
    public static final int MAX_COMPONENTS = 65_536;
    public static final int MAX_COMPONENTS_PER_ENTITY = 128;
    private static final int MAX_COMPONENT_ERROR_LENGTH = 512;
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
        boolean[] created = {false};
        ComponentInstance instance;
        while (true) {
            EntityComponents entityComponents = components(entity, side);
            synchronized (entityComponents) {
                if (COMPONENTS.get(entityKey) != entityComponents) continue;
                instance = entityComponents.components.compute(key, (ignored, existing) -> {
                    if (existing != null) {
                        existing.replaceCallbacks(spec, context);
                        return existing;
                    }
                    if (entityComponents.components.size() >= MAX_COMPONENTS_PER_ENTITY) {
                        throw new LuaError("[MLua] Entity already has the maximum of " + MAX_COMPONENTS_PER_ENTITY
                            + " MLua behavior components.");
                    }
                    reserveComponent();
                    created[0] = true;
                    try {
                        return new ComponentInstance(entity, side, name, ownerModId, spec, context);
                    } catch (RuntimeException | Error failure) {
                        ACTIVE_COMPONENTS.decrementAndGet();
                        created[0] = false;
                        throw failure;
                    }
                });
                if (created[0]) rebuildSnapshot(entityComponents);
                break;
            }
        }
        if (created[0]) invokeCallback(instance, "OnAdded", "added", new LuaTable());
        return instance.handle;
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
        ComponentInstance instance = components.components.get(componentKey(runtimeOwner, ownerModId, name));
        return instance != null ? instance.handle : LuaValue.NIL;
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
        return components.snapshot.stream()
                .filter(instance -> (ownerModId == null || ownerModId.equals(instance.ownerModId))
                        && instance.runtimeOwner == runtimeOwner)
                .map(instance -> instance.name)
                .sorted()
                .toList();
    }

    public static List<LuaTable> handles(Entity entity, Side side, String ownerModId, Object runtimeOwner) {
        EntityComponents components = COMPONENTS.get(entityKey(entity, side));
        if (components == null) return List.of();
        return components.snapshot.stream()
                .filter(instance -> (ownerModId == null || ownerModId.equals(instance.ownerModId))
                        && instance.runtimeOwner == runtimeOwner)
                .sorted(java.util.Comparator.comparing(instance -> instance.name))
                .map(instance -> instance.handle)
                .toList();
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
        ComponentInstance instance = removeComponent(components, key, null);
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
        for (Map.Entry<ComponentKey, ComponentInstance> entry : current.components.entrySet()) {
            ComponentInstance instance = entry.getValue();
            if ((ownerModId == null || ownerModId.equals(instance.ownerModId))
                    && instance.runtimeOwner == runtimeOwner
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
        MLuaEntityScope scope = MLuaEntityScope.of(entity);
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
            for (Map.Entry<ComponentKey, ComponentInstance> componentEntry : components.components.entrySet()) {
                ComponentInstance instance = componentEntry.getValue();
                if (!ownerModId.equals(instance.ownerModId) || instance.runtimeOwner != runtimeOwner) continue;
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
            for (Map.Entry<ComponentKey, ComponentInstance> componentEntry : components.components.entrySet()) {
                ComponentInstance instance = componentEntry.getValue();
                if (instance.runtimeOwner == runtimeOwner
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
            for (ComponentInstance instance : orderedComponents(components)) {
                if (!instance.enabled) {
                    continue;
                }
                invokeCallback(instance, side == Side.CLIENT ? "OnClientTick" : "OnTick", side == Side.CLIENT ? "client_tick" : "tick", new LuaTable());
            }
        }
    }

    public static void onHurt(Entity entity, LuaTable event) {
        dispatch(entity, Side.SERVER, "OnHurt", "hurt", event);
    }

    public static int activeComponentCount() {
        return ACTIVE_COMPONENTS.get();
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

    private static List<ComponentInstance> orderedComponents(EntityComponents components) {
        return components.snapshot;
    }

    private static void rebuildSnapshot(EntityComponents components) {
        components.snapshot = components.components.values().stream()
            .sorted(java.util.Comparator.comparing((ComponentInstance instance) -> instance.ownerModId,
                    java.util.Comparator.nullsFirst(String::compareTo))
                .thenComparing(instance -> instance.name)
                .thenComparingLong(instance -> instance.registrationOrder))
            .toList();
    }

    private static ComponentInstance removeComponent(EntityComponents components, ComponentKey key,
            ComponentInstance expected) {
        synchronized (components) {
            ComponentInstance removed = expected == null
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
    private static List<ComponentInstance> detachAll(EntityComponentsKey key, EntityComponents components) {
        synchronized (components) {
            if (!COMPONENTS.remove(key, components)) return List.of();
            List<ComponentInstance> removed = components.snapshot;
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
        for (ComponentInstance instance : orderedComponents(components)) {
            if (!instance.enabled) {
                continue;
            }
            invokeCallback(instance, callbackName, eventName, event);
        }
    }

    private static void invokeCallback(ComponentInstance instance, String callbackName, String eventName, LuaTable event) {
        boolean removing = "OnRemoved".equals(callbackName);
        if (!removing && (instance.removed || instance.failed)) return;
        try {
            if (instance.context == null) {
                synchronized (instance) {
                    if (!removing && (instance.removed || instance.failed)) return;
                    invokeCallbackBody(instance, callbackName, eventName, event);
                }
            } else {
                synchronized (instance.context.executionLock()) {
                    if (!removing && (instance.removed || instance.failed)) return;
                    MLuaLuaBindings.bindContext(instance.context.globals(), instance.context);
                    invokeCallbackBody(instance, callbackName, eventName, event);
                }
            }
        } catch (LuaError error) {
            disableAfterFailure(instance, callbackName, eventName, error);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable error) {
            disableAfterFailure(instance, callbackName, eventName, error);
        }
    }

    private static void invokeCallbackBody(ComponentInstance instance, String callbackName,
            String eventName, LuaTable event) {
        LuaValue function = callback(instance.callbacks, callbackName);
        if (!function.isfunction()) return;
        LuaTable payload = copy(event);
        payload.set("Event", LuaValue.valueOf(eventName));
        Entity entity = instance.entityRef.get();
        payload.set("Entity", entity == null ? LuaValue.NIL : entityTable(entity));
        payload.set("Component", instance.handle);
        payload.set("State", instance.state);
        function.call(payload);
    }

    private static void disableAfterFailure(ComponentInstance instance, String callbackName,
            String eventName, Throwable failure) {
        instance.failed = true;
        instance.enabled = false;
        String message = failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
        instance.errorMessage = message.length() <= MAX_COMPONENT_ERROR_LENGTH
            ? message : message.substring(0, MAX_COMPONENT_ERROR_LENGTH - 1) + "…";
        MLuaLogger.LOGGER.error("[MLua] Disabled entity component '{}' after its {} callback failed during {}",
            instance.name, callbackName, eventName, failure);
    }

    private static void removeInstance(ComponentInstance instance) {
        if (instance.markRemoved()) invokeCallback(instance, "OnRemoved", "removed", new LuaTable());
    }

    private static LuaValue callback(LuaTable table, String name) {
        LuaValue value = table.get(name);
        if (!value.isnil()) {
            return value;
        }
        String camel = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        return table.get(camel);
    }

    private static LuaTable copy(LuaTable source) {
        LuaTable copy = new LuaTable();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = source.next(key);
            key = next.arg1();
            if (key.isnil()) {
                return copy;
            }
            copy.set(key, next.arg(2));
        }
    }

    private static EntityComponents components(Entity entity, Side side) {
        return COMPONENTS.compute(entityKey(entity, side), (key, existing) -> {
            if (existing == null) return new EntityComponents(entity, side, key.scope());
            existing.rebind(entity);
            return existing;
        });
    }

    private static LuaTable entityTable(Entity entity) {
        var level = entity.level();
        var registration = MLuaRuntime.registrationForLevel(level);
        return MLuaEntityWrapper.entityTable(entity, registration);
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
        private final ConcurrentHashMap<ComponentKey, ComponentInstance> components = new ConcurrentHashMap<>();
        private volatile List<ComponentInstance> snapshot = List.of();

        private EntityComponents(Entity entity, Side side, MLuaEntityScope scope) {
            this.entityRef = new WeakReference<>(entity);
            this.side = side;
            this.scope = scope;
        }

        private void rebind(Entity entity) {
            this.entityRef = new WeakReference<>(entity);
            for (ComponentInstance instance : components.values()) instance.rebind(entity);
        }
    }

    private static final class ComponentInstance {
        private volatile WeakReference<Entity> entityRef;
        private final Side side;
        private final String name;
        private final String ownerModId;
        private final Object runtimeOwner;
        private volatile MLuaContext context;
        private final LuaTable state;
        private final LuaTable handle;
        private final long registrationOrder = NEXT_COMPONENT_ORDER.getAndIncrement();
        private volatile LuaTable callbacks;
        private volatile boolean enabled = true;
        private volatile boolean failed;
        private volatile boolean removed;
        private volatile String errorMessage;

        private ComponentInstance(Entity entity, Side side, String name, String ownerModId, LuaTable callbacks, MLuaContext context) {
            this.entityRef = new WeakReference<>(entity);
            this.side = side;
            this.name = name;
            this.ownerModId = ownerModId;
            this.runtimeOwner = context != null ? context.session() : null;
            this.context = context;
            this.callbacks = callbacks;
            this.state = new LuaTable();
            LuaValue initialState = callbacks.get("State");
            if (initialState.isnil()) {
                initialState = callbacks.get("state");
            }
            if (initialState.istable()) {
                LuaTable table = initialState.checktable();
                LuaValue key = LuaValue.NIL;
                while (true) {
                    Varargs next = table.next(key);
                    key = next.arg1();
                    if (key.isnil()) {
                        break;
                    }
                    this.state.set(key, next.arg(2));
                }
            }
            this.handle = createHandle();
        }

        private Entity entity() {
            Entity entity = entityRef.get();
            if (entity == null) {
                throw new LuaError("[MLua] Component entity is no longer available");
            }
            return entity;
        }

        private void rebind(Entity entity) {
            this.entityRef = new WeakReference<>(entity);
        }

        private void replaceCallbacks(LuaTable replacement, MLuaContext replacementContext) {
            MLuaContext currentContext = context;
            Object lock = currentContext != null ? currentContext.executionLock()
                : replacementContext != null ? replacementContext.executionLock() : this;
            synchronized (lock) {
                if (removed) return;
                callbacks = replacement;
                if (replacementContext != null) context = replacementContext;
                if (failed) {
                    failed = false;
                    errorMessage = null;
                    enabled = true;
                }
            }
        }

        private synchronized boolean markRemoved() {
            if (removed) return false;
            removed = true;
            enabled = false;
            ACTIVE_COMPONENTS.decrementAndGet();
            return true;
        }

        private LuaTable createHandle() {
            LuaTable table = new LuaTable();
            MLuaRegistration registration = context == null ? null : context.registration();
            boolean typed = registration != null && registration.classTable("EntityComponent") != null;
            table.rawset("__side", LuaValue.valueOf(side.name()));
            table.rawset("__component_name", LuaValue.valueOf(name));
            if (typed) registration.attachClassInstance(table, "EntityComponent", null);
            else table.rawset("Name", LuaValue.valueOf(name));
            bindHandleMethod(registration, typed, table, "GetName", args -> LuaValue.valueOf(name));
            bindHandleMethod(registration, typed, table, "GetEntity", args -> entityTable(entity()));
            bindHandleMethod(registration, typed, table, "GetState", args ->
                args.narg() < 2 || args.arg(2).isnil() ? state : state.get(args.arg(2)));
            bindHandleMethod(registration, typed, table, "SetState", args -> {
                state.set(args.arg(2), args.arg(3));
                return LuaValue.NONE;
            });
            bindHandleMethod(registration, typed, table, "GetEnabled", args -> LuaValue.valueOf(enabled));
            bindHandleMethod(registration, typed, table, "GetFailed", args -> LuaValue.valueOf(failed));
            bindHandleMethod(registration, typed, table, "GetError", args ->
                errorMessage == null ? LuaValue.NIL : LuaValue.valueOf(errorMessage));
            bindHandleMethod(registration, typed, table, "SetEnabled", args -> {
                enabled = !removed && !failed && args.checkboolean(2);
                return LuaValue.NONE;
            });
            bindHandleMethod(registration, typed, table, "Destroy", args -> {
                MLuaEntityComponentManager.remove(entity(), side, name, ownerModId, runtimeOwner);
                return LuaValue.NONE;
            });
            if (typed) registration.initializeClassInstance(table, "EntityComponent", null);
            return MLuaServiceProperties.attach(table);
        }

        private static void bindHandleMethod(MLuaRegistration registration, boolean typed, LuaTable table,
                                             String name,
                                             java.util.function.Function<Varargs, Varargs> implementation) {
            VarArgFunction function = new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) { return implementation.apply(args); }
            };
            if (typed) MLuaClassMethods.bindInstanceMethod(registration, "EntityComponent", table, name, function);
            else table.set(name, function);
        }
    }
}
