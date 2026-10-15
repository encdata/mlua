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
            if (entity == excluded || !withinRadius(entity, x, y, z, radius, radiusSquared)
                || !matches(entity, filter, registration)) continue;
            matches.add(entity);
        }
        return matches;
    }

    private static boolean withinRadius(Entity entity, double x, double y, double z,
            double radius, double radiusSquared) {
        double distanceSquared = entity.distanceToSqr(x, y, z);
        if (Double.isFinite(distanceSquared) && Double.isFinite(radiusSquared)) {
            return distanceSquared <= radiusSquared;
        }
        return Math.hypot(Math.hypot(entity.getX() - x, entity.getY() - y), entity.getZ() - z) <= radius;
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
        String type = optionalString(table, "type");
        if (type != null && !entityType.equals(type)) return false;
        LuaValue types = table.get("types");
        if (!types.isnil() && !containsStringList(types, "EntityQuery.types", entityType)) return false;
        LuaValue excludedTypes = table.get("exclude_types");
        if (!excludedTypes.isnil() && containsStringList(excludedTypes, "EntityQuery.exclude_types", entityType)) return false;
        Boolean playersOnly = optionalBoolean(table, "players_only");
        if (Boolean.TRUE.equals(playersOnly) && !(entity instanceof Player)) return false;
        Boolean excludePlayers = optionalBoolean(table, "exclude_players");
        if (Boolean.TRUE.equals(excludePlayers) && entity instanceof Player) return false;
        Boolean alive = optionalBoolean(table, "alive");
        if (alive != null && alive != entity.isAlive()) return false;
        String name = optionalString(table, "name");
        if (name != null && !entity.getName().getString().equals(name)) return false;
        String nameContains = optionalString(table, "name_contains");
        if (nameContains != null && !entity.getName().getString().contains(nameContains)) return false;
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
        String tag = optionalString(table, "tag");
        if (tag != null && !MLuaDataManager.hasEntityTag(entity, tag)) return false;
        LuaValue requiredTags = table.get("tags");
        if (!requiredTags.isnil() && !hasAllTags(entity, requiredTags, "EntityQuery.tags")) return false;
        LuaValue excludedTags = table.get("exclude_tags");
        if (!excludedTags.isnil() && hasAnyTag(entity, excludedTags, "EntityQuery.exclude_tags")) return false;
        return true;
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

    private static boolean containsStringList(LuaValue value, String field, String expected) {
        if (!value.istable()) throw new LuaError("[MLua] " + field + " must be an array of strings.");
        LuaTable values = value.checktable();
        boolean found = false;
        for (int index = 1; !values.get(index).isnil(); index++) {
            LuaValue item = values.get(index);
            if (!item.isstring()) throw new LuaError("[MLua] " + field + " must contain only strings.");
            if (expected.equals(item.tojstring())) found = true;
        }
        return found;
    }

    private static boolean hasAllTags(Entity entity, LuaValue value, String field) {
        for (String tag : stringList(value, field)) {
            if (!MLuaDataManager.hasEntityTag(entity, tag)) return false;
        }
        return true;
    }

    private static boolean hasAnyTag(Entity entity, LuaValue value, String field) {
        for (String tag : stringList(value, field)) {
            if (MLuaDataManager.hasEntityTag(entity, tag)) return true;
        }
        return false;
    }

    private static List<String> stringList(LuaValue value, String field) {
        if (!value.istable()) throw new LuaError("[MLua] " + field + " must be an array of strings.");
        LuaTable values = value.checktable();
        List<String> strings = new ArrayList<>();
        for (int index = 1; !values.get(index).isnil(); index++) {
            LuaValue item = values.get(index);
            if (!item.isstring()) throw new LuaError("[MLua] " + field + " must contain only strings.");
            strings.add(item.tojstring());
        }
        return strings;
    }

    private static Entity entityReference(LuaValue value, MLuaRegistration registration, String field) {
        if (!value.istable()) throw new LuaError("[MLua] " + field + " must contain live Entity instances.");
        return MLuaEntityWrapper.requireLuaInstance(value, registration, "Entity", field);
    }

    private static String entityTypeId(Entity entity) {
        Object id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id == null ? "unknown" : id.toString();
    }

}
