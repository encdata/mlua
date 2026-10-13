package dev.mlua.modules.quest;

import java.util.concurrent.ConcurrentHashMap;
import org.luaj.vm2.LuaTable;

/** Quest definitions partitioned by runtime session and owning mod. */
public final class MLuaQuestRegistry {
    private static final ConcurrentHashMap<Key, LuaTable> QUESTS = new ConcurrentHashMap<>();

    private MLuaQuestRegistry() {}

    public static void put(Object runtimeOwner, String ownerModId, String id, LuaTable definition) {
        QUESTS.put(new Key(runtimeOwner, ownerModId, id), definition);
    }

    public static LuaTable get(Object runtimeOwner, String ownerModId, String id) {
        return QUESTS.get(new Key(runtimeOwner, ownerModId, id));
    }

    public static void remove(Object runtimeOwner, String ownerModId, String id) {
        QUESTS.remove(new Key(runtimeOwner, ownerModId, id));
    }

    public static ConcurrentHashMap<Key, LuaTable> snapshot(Object runtimeOwner, String ownerModId) {
        ConcurrentHashMap<Key, LuaTable> result = new ConcurrentHashMap<>();
        QUESTS.forEach((key, value) -> {
            if (key.runtimeOwner == runtimeOwner && key.ownerModId.equals(ownerModId)) result.put(key, value);
        });
        return result;
    }

    public static void clearOwned(Object runtimeOwner, String ownerModId) {
        QUESTS.keySet().removeIf(key -> key.runtimeOwner == runtimeOwner && key.ownerModId.equals(ownerModId));
    }

    public static void clearRuntime(Object runtimeOwner) {
        QUESTS.keySet().removeIf(key -> key.runtimeOwner == runtimeOwner);
    }

    public static String persistentId(String ownerModId, String id) {
        return ownerModId == null || ownerModId.isBlank() ? id : ownerModId + ":" + id;
    }

    public record Key(Object runtimeOwner, String ownerModId, String id) {
        public Key {
            if (ownerModId == null) ownerModId = "";
        }
    }
}
