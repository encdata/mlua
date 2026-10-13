package dev.mlua.runtime.entity;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.service.MLuaServiceProperties;
import dev.mlua.mcapi.MCApiRuntime;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.minecraft.entity.MLuaEntityLookup;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

public final class MLuaEntityComponentManager {
    private static final ConcurrentHashMap<EntityComponentsKey, EntityComponents> COMPONENTS = new ConcurrentHashMap<>();

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
        Object runtimeOwner = context != null ? context.session() : null;
        ComponentKey key = componentKey(runtimeOwner, ownerModId, name);
        ComponentInstance instance = components(entity, side).components.compute(key, (ignored, existing) -> {
            if (existing != null) {
                existing.callbacks = spec;
                return existing;
            }
            return new ComponentInstance(entity, side, name, ownerModId, spec, context);
        });
        invokeCallback(instance, "OnAdded", "added", new LuaTable());
        return instance.handle;
    }

    public static LuaTable get(Entity entity, Side side, String name) {
        return get(entity, side, name, null);
    }

    public static LuaTable get(Entity entity, Side side, String name, String ownerModId) {
        return get(entity, side, name, ownerModId, null);
    }

    public static LuaTable get(Entity entity, Side side, String name, String ownerModId, Object runtimeOwner) {
        EntityComponents components = COMPONENTS.get(entityKey(entity, side));
        if (components == null) {
            return new LuaTable();
        }
        ComponentInstance instance = components.components.get(componentKey(runtimeOwner, ownerModId, name));
        return instance != null ? instance.handle : new LuaTable();
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
        return components.components.values().stream()
                .filter(instance -> (ownerModId == null || ownerModId.equals(instance.ownerModId))
                        && instance.runtimeOwner == runtimeOwner)
                .map(instance -> instance.name)
                .toList();
    }

    public static void remove(Entity entity, Side side, String name) {
        remove(entity, side, name, null);
    }

    public static void remove(Entity entity, Side side, String name, String ownerModId) {
        remove(entity, side, name, ownerModId, null);
    }

    public static void remove(Entity entity, Side side, String name, String ownerModId, Object runtimeOwner) {
        EntityComponents components = COMPONENTS.get(entityKey(entity, side));
        if (components == null) {
            return;
        }
        ComponentKey key = componentKey(runtimeOwner, ownerModId, name);
        ComponentInstance instance = components.components.remove(key);
        if (instance != null) {
            invokeCallback(instance, "OnRemoved", "removed", new LuaTable());
        }
        if (components.components.isEmpty()) {
            COMPONENTS.remove(entityKey(entity, side), components);
        }
    }

    public static void clear(Entity entity, Side side) {
        clear(entity, side, null);
    }

    public static void clear(Entity entity, Side side, String ownerModId) {
        clear(entity, side, ownerModId, null);
    }

    public static void clear(Entity entity, Side side, String ownerModId, Object runtimeOwner) {
        EntityComponentsKey key = entityKey(entity, side);
        EntityComponents current = COMPONENTS.get(key);
        if (current == null) return;
        for (Map.Entry<ComponentKey, ComponentInstance> entry : current.components.entrySet()) {
            ComponentInstance instance = entry.getValue();
            if ((ownerModId == null || ownerModId.equals(instance.ownerModId))
                    && instance.runtimeOwner == runtimeOwner
                    && current.components.remove(entry.getKey(), instance)) {
                invokeCallback(instance, "OnRemoved", "removed", new LuaTable());
            }
        }
        if (current.components.isEmpty()) COMPONENTS.remove(key, current);
    }

    public static void clearAll() {
        for (EntityComponents components : COMPONENTS.values()) {
            for (ComponentInstance instance : components.components.values()) {
                invokeCallback(instance, "OnRemoved", "removed", new LuaTable());
            }
        }
        COMPONENTS.clear();
    }

    /** Remove components attached to this exact entity, regardless of runtime-side bucket. */
    public static void clearEntity(Entity entity) {
        if (entity == null) return;
        MLuaEntityScope scope = MLuaEntityScope.of(entity);
        for (Map.Entry<EntityComponentsKey, EntityComponents> entry : COMPONENTS.entrySet()) {
            if (!entry.getKey().scope().equals(scope)) continue;
            EntityComponents components = entry.getValue();
            if (!COMPONENTS.remove(entry.getKey(), components)) continue;
            for (ComponentInstance instance : components.components.values()) {
                invokeCallback(instance, "OnRemoved", "removed", new LuaTable());
            }
        }
    }

    public static void clearSide(Side side) {
        for (Map.Entry<EntityComponentsKey, EntityComponents> entry : COMPONENTS.entrySet()) {
            EntityComponents components = entry.getValue();
            if (side != Side.BOTH && components.side != side) continue;
            for (ComponentInstance instance : components.components.values()) {
                invokeCallback(instance, "OnRemoved", "removed", new LuaTable());
            }
            COMPONENTS.remove(entry.getKey(), components);
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
                if (components.components.remove(componentEntry.getKey(), instance)) {
                    invokeCallback(instance, "OnRemoved", "removed", new LuaTable());
                }
            }
            if (components.components.isEmpty()) COMPONENTS.remove(entry.getKey(), components);
        }
    }

    /** Remove every component created by one runtime session, preserving other sessions. */
    public static void clearRuntime(Object runtimeOwner, Side side) {
        if (runtimeOwner == null) return;
        for (Map.Entry<EntityComponentsKey, EntityComponents> entry : COMPONENTS.entrySet()) {
            EntityComponents components = entry.getValue();
            if (side != Side.BOTH && components.side != side) continue;
            for (Map.Entry<ComponentKey, ComponentInstance> componentEntry : components.components.entrySet()) {
                ComponentInstance instance = componentEntry.getValue();
                if (instance.runtimeOwner == runtimeOwner
                        && components.components.remove(componentEntry.getKey(), instance)) {
                    invokeCallback(instance, "OnRemoved", "removed", new LuaTable());
                }
            }
            if (components.components.isEmpty()) COMPONENTS.remove(entry.getKey(), components);
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
                for (ComponentInstance instance : components.components.values()) {
                    invokeCallback(instance, "OnRemoved", "removed", new LuaTable());
                }
                COMPONENTS.remove(entry.getKey(), components);
                continue;
            }
            for (ComponentInstance instance : components.components.values()) {
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
        for (ComponentInstance instance : components.components.values()) {
            if (!instance.enabled) {
                continue;
            }
            invokeCallback(instance, callbackName, eventName, event);
        }
    }

    private static void invokeCallback(ComponentInstance instance, String callbackName, String eventName, LuaTable event) {
        LuaValue callback = callback(instance.callbacks, callbackName);
        if (!callback.isfunction()) {
            return;
        }
        LuaTable payload = copy(event);
        payload.set("Event", LuaValue.valueOf(eventName));
        payload.set("Entity", entityTable(instance.entity()));
        payload.set("Component", instance.handle);
        payload.set("State", instance.state);
        try {
            if (instance.context != null) MLuaLuaBindings.bindContext(instance.context.globals(), instance.context);
            callback.call(payload);
        } catch (LuaError error) {
            throw error;
        } catch (Exception error) {
            throw new LuaError("[MLua] Component '" + instance.name + "' callback failed: " + error.getMessage());
        }
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
        private final MLuaContext context;
        private final LuaTable state;
        private final LuaTable handle;
        private volatile LuaTable callbacks;
        private volatile boolean enabled = true;

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

        private LuaTable createHandle() {
            LuaTable table = new LuaTable();
            table.set("Name", LuaValue.valueOf(name));
            table.set("GetName", new ZeroArgFunction() {
                @Override
                public LuaValue call() {
                    return LuaValue.valueOf(name);
                }
            });
            table.set("GetEntity", new ZeroArgFunction() {
                @Override
                public LuaValue call() {
                    return entityTable(entity());
                }
            });
            table.set("GetState", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    if (args.narg() < 2 || args.arg(2).isnil()) {
                        return state;
                    }
                    return state.get(args.arg(2));
                }
            });
            table.set("SetState", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    state.set(args.arg(2), args.arg(3));
                    return LuaValue.NONE;
                }
            });
            table.set("GetEnabled", new ZeroArgFunction() {
                @Override
                public LuaValue call() {
                    return LuaValue.valueOf(enabled);
                }
            });
            table.set("SetEnabled", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    enabled = args.checkboolean(2);
                    return LuaValue.NONE;
                }
            });
            table.set("Destroy", new ZeroArgFunction() {
                @Override
                public LuaValue call() {
                    remove(entity(), side, name, ownerModId, runtimeOwner);
                    return LuaValue.NONE;
                }
            });
            return MLuaServiceProperties.attach(table);
        }
    }
}
