package dev.mlua.modules.players.classes;

import dev.mlua.common.enums.Side;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import org.luaj.vm2.LuaTable;
import net.minecraft.world.entity.Entity;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Weak Lua wrapper cache scoped to one concrete entity identity. */
final class MLuaEntityWrapperCache {
    record Key(Side side, String dimension, UUID uuid, int entityId) { }
    record PlayerKey(Side side, UUID uuid) { }

    static Key key(Entity entity) {
        MLuaEntityScope scope = MLuaEntityScope.of(entity);
        return key(scope);
    }

    static Key key(MLuaEntityScope scope) {
        return new Key(scope.side(), scope.dimension(), scope.uuid(), scope.entityId());
    }

    static PlayerKey playerKey(Entity entity) {
        return new PlayerKey(entity.level().isClientSide() ? Side.CLIENT : Side.SERVER, entity.getUUID());
    }

    private final Map<Key, WeakReference<LuaTable>> entries = new ConcurrentHashMap<>();
    // Live players must keep a stable Lua identity even when scripts temporarily
    // drop every reference to their wrapper. Entity wrappers remain weakly held.
    private final Map<PlayerKey, LuaTable> players = new ConcurrentHashMap<>();

    LuaTable get(Key key) {
        LuaTable player = this.players.get(new PlayerKey(key.side(), key.uuid()));
        if (player != null) return player;
        WeakReference<LuaTable> reference = this.entries.get(key);
        return reference == null ? null : reference.get();
    }

    LuaTable getPlayer(Key key) {
        return this.players.get(new PlayerKey(key.side(), key.uuid()));
    }

    void putPlayer(Key key, LuaTable table) {
        this.entries.remove(key);
        this.players.put(new PlayerKey(key.side(), key.uuid()), table);
    }

    void put(Key key, LuaTable table) {
        this.entries.put(key, new WeakReference<>(table));
    }

    void remove(Key key) {
        this.entries.remove(key);
        this.players.remove(new PlayerKey(key.side(), key.uuid()));
    }

    void removeEntityId(int entityId) {
        this.entries.keySet().removeIf(key -> key.entityId() == entityId);
    }

    void removeSide(Side side) {
        this.entries.keySet().removeIf(key -> key.side() == side);
        this.players.keySet().removeIf(key -> key.side() == side);
    }

    void clean() {
        this.entries.forEach((key, reference) -> {
            if (reference.get() == null) this.entries.remove(key, reference);
        });
    }

    void clear() {
        this.entries.clear();
        this.players.clear();
    }

    int size() {
        return this.entries.size() + this.players.size();
    }
}
