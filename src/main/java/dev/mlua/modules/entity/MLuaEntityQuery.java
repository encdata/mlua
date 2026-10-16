package dev.mlua.modules.entity;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.runtime.data.MLuaDataManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
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
        Filter query = compileFilter(filter, registration);
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
                || !query.matches(entity)) continue;
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
        if (entity == null) return true;
        return compileFilter(filter, registration).matches(entity);
    }

    /** Compiles one Lua filter for reuse across candidate entities. */
    public static Filter compileFilter(LuaValue filter, MLuaRegistration registration) {
        if (filter == null || filter.isnil()) return new Filter(CompiledFilter.EMPTY);
        if (filter.isstring()) return new Filter(new CompiledFilter(filter.tojstring(), null, Set.of(),
            null, null, null, null, null, null, Set.of(), Set.of(), null, Set.of()));
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
        Entity excludedEntity = excluded.isnil() ? null : entityReference(excluded, registration, "EntityQuery.exclude");
        LuaValue excludedValue = table.get("exclude_entities");
        Set<Entity> excludedEntities = Collections.newSetFromMap(new IdentityHashMap<>());
        if (!excludedValue.isnil() && !excludedValue.istable()) {
            throw new LuaError("[MLua] EntityQuery.exclude_entities must be an array of live Entity instances.");
        }
        if (excludedValue.istable()) {
            LuaTable entities = excludedValue.checktable();
            for (int index = 1; !entities.get(index).isnil(); index++) {
                excludedEntities.add(entityReference(entities.get(index), registration, "EntityQuery.exclude_entities"));
            }
        }
        return new Filter(new CompiledFilter(type, types, excludedTypes, playersOnly, excludePlayers, alive,
            name, nameContains, tag, requiredTags, excludedTags, excludedEntity, excludedEntities));
    }

    public static final class Filter {
        private final CompiledFilter compiled;

        private Filter(CompiledFilter compiled) {
            this.compiled = compiled;
        }

        public boolean matches(Entity entity) {
            return this.compiled.matches(entity);
        }
    }

    private record CompiledFilter(String type, Set<String> types, Set<String> excludedTypes,
            Boolean playersOnly, Boolean excludePlayers, Boolean alive, String name, String nameContains,
            String tag, Set<String> requiredTags, Set<String> excludedTags, Entity excludedEntity,
            Set<Entity> excludedEntities) {
        private static final CompiledFilter EMPTY = new CompiledFilter(null, Set.of(), Set.of(),
            null, null, null, null, null, null, Set.of(), Set.of(), null, Set.of());

        private CompiledFilter {
            if (types != null) types = Set.copyOf(types);
            excludedTypes = Set.copyOf(excludedTypes);
            requiredTags = Set.copyOf(requiredTags);
            excludedTags = Set.copyOf(excludedTags);
            Set<Entity> excludedCopy = Collections.newSetFromMap(new IdentityHashMap<>());
            excludedCopy.addAll(excludedEntities);
            excludedEntities = Collections.unmodifiableSet(excludedCopy);
        }

        private boolean matches(Entity entity) {
            if (entity == null) return false;
            if (this.type != null || this.types != null || !this.excludedTypes.isEmpty()) {
                String entityType = entityTypeId(entity);
                if (this.type != null && !this.type.equals(entityType)) return false;
                if (this.types != null && !this.types.contains(entityType)) return false;
                if (this.excludedTypes.contains(entityType)) return false;
            }
            if (Boolean.TRUE.equals(this.playersOnly) && !(entity instanceof Player)) return false;
            if (Boolean.TRUE.equals(this.excludePlayers) && entity instanceof Player) return false;
            if (this.alive != null && this.alive != entity.isAlive()) return false;
            if (this.name != null || this.nameContains != null) {
                String entityName = entity.getName().getString();
                if (this.name != null && !this.name.equals(entityName)) return false;
                if (this.nameContains != null && !entityName.contains(this.nameContains)) return false;
            }
            if (entity == this.excludedEntity || this.excludedEntities.contains(entity)) return false;
            if (this.tag != null && !MLuaDataManager.hasEntityTag(entity, this.tag)) return false;
            for (String requiredTag : this.requiredTags) {
                if (!MLuaDataManager.hasEntityTag(entity, requiredTag)) return false;
            }
            for (String excludedTag : this.excludedTags) {
                if (MLuaDataManager.hasEntityTag(entity, excludedTag)) return false;
            }
            return true;
        }
    }

    private static Set<String> optionalStringSet(LuaValue value, String field) {
        return value.isnil() ? null : Set.copyOf(stringList(value, field));
    }

    private static Set<String> stringSet(LuaValue value, String field) {
        return value.isnil() ? Set.of() : Set.copyOf(stringList(value, field));
    }

    /** Validates a filter, including when a query will find no candidates. */
    public static void validateFilter(LuaValue filter, MLuaRegistration registration) {
        compileFilter(filter, registration);
    }

    /*
     * Filter validation and Lua-to-Java conversion happen once per query. Candidate checks only
     * touch immutable sets and entity state, so a large radius query doesn't repeatedly traverse
     * the same Lua tables.
     */
    private static String entityTypeId(Entity entity) {
        Object id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id == null ? "unknown" : id.toString();
    }

    private static Entity entityReference(LuaValue value, MLuaRegistration registration, String field) {
        if (!value.istable()) throw new LuaError("[MLua] " + field + " must contain live Entity instances.");
        return MLuaEntityWrapper.requireLuaInstance(value, registration, "Entity", field);
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

}
