package dev.mlua.modules.entity;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.runtime.data.MLuaDataManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Shared EntityQuery matching for world, service, and entity instance queries. */
public final class MLuaEntityQuery {
    private MLuaEntityQuery() {
    }

    /** Returns loaded entities inside a spherical query, applying the shared EntityQuery filter. */
    public static List<Entity> findWithinRadius(Level level, double x, double y, double z,
            double radius, Entity excluded, LuaValue filter, MLuaRegistration registration) {
        if (level == null) return List.of();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new LuaError("[MLua] Entity query center must contain only finite coordinates.");
        }
        if (!Double.isFinite(radius) || radius < 0.0) {
            throw new LuaError("[MLua] Entity query radius must be finite and non-negative.");
        }

        double minX = x - radius;
        double minY = y - radius;
        double minZ = z - radius;
        double maxX = x + radius;
        double maxY = y + radius;
        double maxZ = z + radius;
        boolean finiteBounds = Double.isFinite(minX) && Double.isFinite(minY) && Double.isFinite(minZ)
            && Double.isFinite(maxX) && Double.isFinite(maxY) && Double.isFinite(maxZ)
            && Double.isFinite(maxX - minX) && Double.isFinite(maxY - minY) && Double.isFinite(maxZ - minZ);
        Iterable<Entity> candidates = finiteBounds
            ? level.getEntities((Entity) null, new AABB(minX, minY, minZ, maxX, maxY, maxZ))
            : loadedEntities(level);

        double radiusSquared = radius * radius;
        List<Entity> matches = new ArrayList<>();
        for (Entity entity : candidates) {
            if (entity == excluded
                || entity.distanceToSqr(x, y, z) > radiusSquared
                || !matches(entity, filter, registration)) continue;
            matches.add(entity);
        }
        return matches;
    }

    /** Returns the entities visible to this level's current side. */
    public static Iterable<Entity> loadedEntities(Level level) {
        if (level == null) return List.of();
        if (level instanceof ServerLevel serverLevel) return serverLevel.getAllEntities();
        try {
            Object value = level.getClass().getMethod("entitiesForRendering").invoke(level);
            if (value instanceof Iterable<?> iterable) {
                List<Entity> entities = new ArrayList<>();
                for (Object entity : iterable) {
                    if (entity instanceof Entity rawEntity) entities.add(rawEntity);
                }
                return entities;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
        return List.of();
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
