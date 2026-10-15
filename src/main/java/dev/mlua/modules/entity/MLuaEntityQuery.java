package dev.mlua.modules.entity;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.runtime.data.MLuaDataManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Shared EntityQuery matching for world, service, and entity instance queries. */
public final class MLuaEntityQuery {
    private MLuaEntityQuery() {
    }

    public static boolean matches(Entity entity, LuaValue filter, MLuaRegistration registration) {
        if (entity == null || filter == null || filter.isnil()) return true;
        if (filter.isstring()) return entityTypeId(entity).equals(filter.tojstring());
        if (!filter.istable()) {
            throw new LuaError("[MLua] EntityQuery must be an entity type id or a filter table.");
        }

        LuaTable table = filter.checktable();
        String entityType = entityTypeId(entity);
        LuaValue type = table.get("type");
        if (!type.isnil() && !entityType.equals(type.tojstring())) return false;
        LuaValue types = table.get("types");
        if (types.istable() && !containsString(types.checktable(), entityType)) return false;
        LuaValue excludedTypes = table.get("exclude_types");
        if (excludedTypes.istable() && containsString(excludedTypes.checktable(), entityType)) return false;
        LuaValue playersOnly = table.get("players_only");
        if (!playersOnly.isnil() && playersOnly.toboolean() && !(entity instanceof Player)) return false;
        LuaValue excludePlayers = table.get("exclude_players");
        if (!excludePlayers.isnil() && excludePlayers.toboolean() && entity instanceof Player) return false;
        LuaValue alive = table.get("alive");
        if (!alive.isnil() && alive.toboolean() != entity.isAlive()) return false;
        LuaValue name = table.get("name");
        if (!name.isnil() && !entity.getName().getString().equals(name.tojstring())) return false;
        LuaValue nameContains = table.get("name_contains");
        if (!nameContains.isnil() && !entity.getName().getString().contains(nameContains.tojstring())) return false;
        LuaValue excludedEntity = table.get("exclude");
        if (!excludedEntity.isnil() && entityReference(excludedEntity, registration, "EntityQuery.exclude") == entity) return false;
        LuaValue excludedEntities = table.get("exclude_entities");
        if (!excludedEntities.isnil() && !excludedEntities.istable()) {
            throw new LuaError("[MLua] EntityQuery.exclude_entities must be an array of live Entity instances.");
        }
        if (excludedEntities.istable()) {
            LuaTable entities = excludedEntities.checktable();
            for (int index = 1; !entities.get(index).isnil(); index++) {
                if (entityReference(entities.get(index), registration, "EntityQuery.exclude_entities") == entity) return false;
            }
        }
        LuaValue tag = table.get("tag");
        if (!tag.isnil() && !MLuaDataManager.hasEntityTag(entity, tag.tojstring())) return false;
        LuaValue requiredTags = table.get("tags");
        if (requiredTags.istable()) {
            LuaTable tags = requiredTags.checktable();
            for (int index = 1; !tags.get(index).isnil(); index++) {
                if (!MLuaDataManager.hasEntityTag(entity, tags.get(index).checkjstring())) return false;
            }
        }
        LuaValue excludedTags = table.get("exclude_tags");
        if (excludedTags.istable()) {
            LuaTable tags = excludedTags.checktable();
            for (int index = 1; !tags.get(index).isnil(); index++) {
                if (MLuaDataManager.hasEntityTag(entity, tags.get(index).checkjstring())) return false;
            }
        }
        return true;
    }

    private static Entity entityReference(LuaValue value, MLuaRegistration registration, String field) {
        if (!value.istable()) throw new LuaError("[MLua] " + field + " must contain live Entity instances.");
        return MLuaEntityWrapper.requireLuaInstance(value, registration, "Entity", field);
    }

    private static String entityTypeId(Entity entity) {
        Object id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id == null ? "unknown" : id.toString();
    }

    private static boolean containsString(LuaTable values, String expected) {
        for (int index = 1; !values.get(index).isnil(); index++) {
            LuaValue value = values.get(index);
            if (!value.isstring()) {
                throw new LuaError("[MLua] EntityQuery type lists must contain only entity type identifiers.");
            }
            if (expected.equals(value.tojstring())) return true;
        }
        return false;
    }
}
