package dev.mlua.modules.entity;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.runtime.data.MLuaDataManager;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Parses Lua entity filters into immutable predicates shared by entity and world queries. */
final class MLuaEntityQueryFilter {
    private static final ConcurrentMap<EntityType<?>, String> ENTITY_TYPE_IDS = new ConcurrentHashMap<>();
    private static final Compiled ALIVE_PLAYERS = new Compiled(null, null, Set.of(), true, null, true,
        null, null, null, Set.of(), Set.of(), null, Set.of());
    private static final Compiled ALIVE_ENTITIES = new Compiled(null, null, Set.of(), null, null, true,
        null, null, null, Set.of(), Set.of(), null, Set.of());

    private MLuaEntityQueryFilter() {
    }

    static Compiled compile(LuaValue filter, MLuaRegistration registration) {
        if (filter == null || filter.isnil()) return Compiled.EMPTY;
        if (filter.isstring()) {
            return new Compiled(filter.tojstring(), null, Set.of(), null, null, null, null, null,
                null, Set.of(), Set.of(), null, Set.of());
        }
        if (!filter.istable()) {
            throw new LuaError("[MLua] EntityQuery must be an entity type id or a filter table.");
        }

        LuaTable table = filter.checktable();
        String type = optionalString(table, "type");
        Set<String> types = optionalStringSet(table.get("types"), "EntityQuery.types");
        Set<String> excludedTypes = stringSet(table.get("exclude_types"), "EntityQuery.exclude_types");
        Boolean playersOnly = optionalBoolean(table, "players_only");
        Boolean excludePlayers = optionalBoolean(table, "exclude_players");
        Boolean alive = optionalBoolean(table, "alive");
        String name = optionalString(table, "name");
        String nameContains = optionalString(table, "name_contains");
        String tag = optionalString(table, "tag");
        Set<String> requiredTags = stringSet(table.get("tags"), "EntityQuery.tags");
        Set<String> excludedTags = stringSet(table.get("exclude_tags"), "EntityQuery.exclude_tags");

        LuaValue excluded = table.get("exclude");
        Entity excludedEntity = excluded.isnil() ? null
            : entityReference(excluded, registration, "EntityQuery.exclude");
        Set<Entity> excludedEntities = identitySet();
        LuaValue excludedValue = table.get("exclude_entities");
        if (!excludedValue.isnil() && !excludedValue.istable()) {
            throw new LuaError("[MLua] EntityQuery.exclude_entities must be an array of live Entity instances.");
        }
        if (excludedValue.istable()) {
            forEachDenseArray(excludedValue.checktable(), "EntityQuery.exclude_entities", entity ->
                excludedEntities.add(entityReference(entity, registration, "EntityQuery.exclude_entities")));
        }
        return new Compiled(type, types, excludedTypes, playersOnly, excludePlayers, alive,
            name, nameContains, tag, requiredTags, excludedTags, excludedEntity, immutable(excludedEntities));
    }

    static Compiled alivePlayers() {
        return ALIVE_PLAYERS;
    }

    static Compiled aliveEntities(String type) {
        if (type == null) return ALIVE_ENTITIES;
        return new Compiled(type, null, Set.of(), null, null, true,
            null, null, null, Set.of(), Set.of(), null, Set.of());
    }

    private static boolean matches(Compiled filter, Entity entity) {
        if (entity == null) return false;
        if (!matchesType(filter, entityTypeId(entity))) return false;
        if (Boolean.TRUE.equals(filter.playersOnly) && !(entity instanceof Player)) return false;
        if (Boolean.TRUE.equals(filter.excludePlayers) && entity instanceof Player) return false;
        if (filter.alive != null && filter.alive != entity.isAlive()) return false;
        if (filter.name != null || filter.nameContains != null) {
            String entityName = entity.getName().getString();
            if (filter.name != null && !filter.name.equals(entityName)) return false;
            if (filter.nameContains != null && !entityName.contains(filter.nameContains)) return false;
        }
        if (entity == filter.excludedEntity || filter.excludedEntities.contains(entity)) return false;
        if (filter.tag != null && !MLuaDataManager.hasEntityTag(entity, filter.tag)) return false;
        for (String requiredTag : filter.requiredTags) {
            if (!MLuaDataManager.hasEntityTag(entity, requiredTag)) return false;
        }
        for (String excludedTag : filter.excludedTags) {
            if (MLuaDataManager.hasEntityTag(entity, excludedTag)) return false;
        }
        return true;
    }

    private static boolean matchesType(Compiled filter, String entityType) {
        if (filter.type != null && !filter.type.equals(entityType)) return false;
        if (filter.types != null && !filter.types.contains(entityType)) return false;
        return !filter.excludedTypes.contains(entityType);
    }

    private static String entityTypeId(Entity entity) {
        String id = ENTITY_TYPE_IDS.computeIfAbsent(entity.getType(), type -> {
            Object key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            return key == null ? null : key.toString();
        });
        return id == null ? "unknown" : id;
    }

    private static String optionalString(LuaTable table, String key) {
        LuaValue value = table.get(key);
        if (value.isnil()) return null;
        if (!value.isstring()) throw new LuaError("[MLua] EntityQuery." + key + " must be a string.");
        return value.tojstring();
    }

    private static Boolean optionalBoolean(LuaTable table, String key) {
        LuaValue value = table.get(key);
        if (value.isnil()) return null;
        if (!value.isboolean()) throw new LuaError("[MLua] EntityQuery." + key + " must be a boolean.");
        return value.toboolean();
    }

    private static Set<String> optionalStringSet(LuaValue value, String field) {
        return value.isnil() ? null : stringSet(value, field);
    }

    private static Set<String> stringSet(LuaValue value, String field) {
        if (value.isnil()) return Set.of();
        if (!value.istable()) throw new LuaError("[MLua] " + field + " must be an array of strings.");
        Set<String> strings = new HashSet<>();
        forEachDenseArray(value.checktable(), field, item -> {
            if (!item.isstring()) throw new LuaError("[MLua] " + field + " must contain only strings.");
            strings.add(item.tojstring());
        });
        return immutable(strings);
    }

    private static Set<Entity> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static <T> Set<T> immutable(Set<T> values) {
        return values.isEmpty() ? Set.of() : Collections.unmodifiableSet(values);
    }

    private static Entity entityReference(LuaValue value, MLuaRegistration registration, String field) {
        if (!value.istable()) throw new LuaError("[MLua] " + field + " must contain live Entity instances.");
        return MLuaEntityWrapper.requireLuaInstance(value, registration, "Entity", field);
    }

    private static void forEachDenseArray(LuaTable table, String field, Consumer<LuaValue> action) {
        int entries = 0;
        int maximumIndex = 0;
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) break;
            if (!key.isint() || key.toint() < 1) {
                throw new LuaError("[MLua] " + field + " must be a dense array with only positive integer keys.");
            }
            maximumIndex = Math.max(maximumIndex, key.toint());
            if (++entries > MLuaEntityQuery.MAX_FILTER_ARRAY_ENTRIES) {
                throw new LuaError("[MLua] " + field + " cannot contain more than "
                    + MLuaEntityQuery.MAX_FILTER_ARRAY_ENTRIES + " entries.");
            }
        }
        if (maximumIndex != entries) {
            throw new LuaError("[MLua] " + field + " must be dense; missing array values are not allowed.");
        }
        for (int index = 1; index <= entries; index++) action.accept(table.get(index));
    }

    static final class Compiled {
        private static final Compiled EMPTY = new Compiled(null, null, Set.of(), null, null, null,
            null, null, null, Set.of(), Set.of(), null, Set.of());

        private final String type;
        private final Set<String> types;
        private final Set<String> excludedTypes;
        private final Boolean playersOnly;
        private final Boolean excludePlayers;
        private final Boolean alive;
        private final String name;
        private final String nameContains;
        private final String tag;
        private final Set<String> requiredTags;
        private final Set<String> excludedTags;
        private final Entity excludedEntity;
        private final Set<Entity> excludedEntities;

        private Compiled(String type, Set<String> types, Set<String> excludedTypes,
                Boolean playersOnly, Boolean excludePlayers, Boolean alive, String name,
                String nameContains, String tag, Set<String> requiredTags, Set<String> excludedTags,
                Entity excludedEntity, Set<Entity> excludedEntities) {
            this.type = type;
            this.types = types;
            this.excludedTypes = excludedTypes;
            this.playersOnly = playersOnly;
            this.excludePlayers = excludePlayers;
            this.alive = alive;
            this.name = name;
            this.nameContains = nameContains;
            this.tag = tag;
            this.requiredTags = requiredTags;
            this.excludedTags = excludedTags;
            this.excludedEntity = excludedEntity;
            this.excludedEntities = excludedEntities;
        }

        boolean matches(Entity entity) {
            return MLuaEntityQueryFilter.matches(this, entity);
        }

        boolean matchesType(String entityType) {
            return MLuaEntityQueryFilter.matchesType(this, entityType);
        }
    }
}
