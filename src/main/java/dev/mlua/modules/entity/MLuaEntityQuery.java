package dev.mlua.modules.entity;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.runtime.data.MLuaDataManager;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Shared EntityQuery matching for world, service, and entity instance queries. */
public final class MLuaEntityQuery {
    public static final int MAX_FILTER_ARRAY_ENTRIES = 1024;
    /** Avoid scanning millions of empty entity sections for broad, finite radius queries. */
    private static final double MAX_INDEXED_QUERY_RADIUS = 128.0D;
    /** Entity type ids are stable after registration and are checked for every filtered candidate. */
    private static final ConcurrentMap<EntityType<?>, String> ENTITY_TYPE_IDS = new ConcurrentHashMap<>();
    private static final ClassValue<Optional<Method>> CLIENT_RENDER_ENTITIES = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> levelType) {
            try {
                return Optional.of(levelType.getMethod("entitiesForRendering"));
            } catch (NoSuchMethodException | SecurityException unavailable) {
                return Optional.empty();
            }
        }
    };

    private MLuaEntityQuery() {
    }

    /** Returns loaded entities inside a spherical query, applying the shared EntityQuery filter. */
    public static List<Entity> findWithinRadius(Level level, double x, double y, double z,
            double radius, Entity excluded, LuaValue filter, MLuaRegistration registration) {
        return findWithinRadius(level, x, y, z, radius, excluded, compileFilter(filter, registration));
    }

    /** Runs a query with a filter already compiled for the current Lua operation. */
    public static List<Entity> findWithinRadius(Level level, double x, double y, double z,
            double radius, Entity excluded, Filter query) {
        List<Entity> matches = new ArrayList<>();
        forEachWithinRadius(level, x, y, z, radius, excluded, query, matches::add);
        return matches;
    }

    /** Visits matching entities without allocating a result list. */
    public static void forEachWithinRadius(Level level, double x, double y, double z,
            double radius, Entity excluded, Filter query, Consumer<Entity> action) {
        java.util.Objects.requireNonNull(query, "query");
        java.util.Objects.requireNonNull(action, "action");
        if (level == null) return;
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
        // The spatial query is fast for local searches. For a broad radius, walking loaded
        // entities is cheaper and cannot cause Minecraft to traverse a huge empty section box.
        Iterable<Entity> candidates = candidates(level, radius, minX, minY, minZ, maxX, maxY, maxZ, finiteBounds);

        double radiusSquared = radius * radius;
        for (Entity entity : candidates) {
            if (entity == excluded || !withinRadius(entity, x, y, z, radius, radiusSquared)
                || !query.matches(entity)) continue;
            action.accept(entity);
        }
    }

    /** Finds the nearest match without allocating a result list for all candidates. */
    public static Entity findNearestWithinRadius(Level level, double x, double y, double z,
            double radius, Filter query) {
        return findNearestWithinRadius(level, x, y, z, radius, null, query);
    }

    /** Finds the nearest match while omitting an optional center entity. */
    public static Entity findNearestWithinRadius(Level level, double x, double y, double z,
            double radius, Entity excluded, Filter query) {
        java.util.Objects.requireNonNull(query, "query");
        if (level == null) return null;
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
        Entity nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        double nearestDistanceSquared = Double.POSITIVE_INFINITY;
        double radiusSquared = radius * radius;
        for (Entity entity : candidates(level, radius, minX, minY, minZ, maxX, maxY, maxZ, finiteBounds)) {
            if (entity == excluded || !query.matches(entity)) continue;

            double distanceSquared = entity.distanceToSqr(x, y, z);
            boolean canCompareSquared = Double.isFinite(distanceSquared)
                && Double.isFinite(radiusSquared)
                && !(radius > 0.0 && radiusSquared == 0.0)
                && !(nearestDistanceSquared == 0.0 && nearestDistance > 0.0)
                && !(distanceSquared == 0.0 && !samePosition(entity, x, y, z));
            if (canCompareSquared) {
                if (distanceSquared <= radiusSquared && distanceSquared < nearestDistanceSquared) {
                    nearest = entity;
                    nearestDistanceSquared = distanceSquared;
                    nearestDistance = Math.sqrt(distanceSquared);
                }
                continue;
            }

            // Keep hypot for extreme or underflowing coordinates, where squared distance
            // would lose the ordering or overflow even though the distance is representable.
            double distance = Math.hypot(Math.hypot(entity.getX() - x, entity.getY() - y), entity.getZ() - z);
            if (distance <= radius && distance < nearestDistance) {
                nearest = entity;
                nearestDistance = distance;
                nearestDistanceSquared = distance * distance;
            }
        }
        return nearest;
    }

    private static boolean samePosition(Entity entity, double x, double y, double z) {
        return entity.getX() == x && entity.getY() == y && entity.getZ() == z;
    }

    private static Iterable<Entity> candidates(Level level, double radius,
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
            boolean finiteBounds) {
        return finiteBounds && radius <= MAX_INDEXED_QUERY_RADIUS
            ? level.getEntities((Entity) null, new AABB(minX, minY, minZ, maxX, maxY, maxZ))
            : loadedEntities(level);
    }

    private static boolean withinRadius(Entity entity, double x, double y, double z,
            double radius, double radiusSquared) {
        double distanceSquared = entity.distanceToSqr(x, y, z);
        if (Double.isFinite(distanceSquared) && Double.isFinite(radiusSquared)
                && !(radius > 0.0 && radiusSquared == 0.0)
                && !(distanceSquared == 0.0 && !samePosition(entity, x, y, z))) {
            return distanceSquared <= radiusSquared;
        }
        return Math.hypot(Math.hypot(entity.getX() - x, entity.getY() - y), entity.getZ() - z) <= radius;
    }

    /** Returns the entities visible to this level's current side. */
    public static Iterable<Entity> loadedEntities(Level level) {
        if (level == null) return List.of();
        if (level instanceof ServerLevel serverLevel) return serverLevel.getAllEntities();
        try {
            Method accessor = CLIENT_RENDER_ENTITIES.get(level.getClass()).orElse(null);
            if (accessor == null) return List.of();
            Object value = accessor.invoke(level);
            if (value instanceof Iterable<?> iterable) {
                // Query paths often only need to count or find a matching entity. Materializing
                // the entire client entity view here made every such call allocate a second
                // world-sized list before applying its filter. Adapt the iterable lazily instead.
                return () -> new Iterator<>() {
                    private final Iterator<?> source = iterable.iterator();
                    private Entity next;
                    private boolean ready;

                    @Override
                    public boolean hasNext() {
                        if (ready) return true;
                        while (source.hasNext()) {
                            Object candidate = source.next();
                            if (candidate instanceof Entity entity) {
                                next = entity;
                                ready = true;
                                return true;
                            }
                        }
                        return false;
                    }

                    @Override
                    public Entity next() {
                        if (!hasNext()) throw new NoSuchElementException();
                        Entity result = next;
                        next = null;
                        ready = false;
                        return result;
                    }
                };
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
            for (LuaValue entity : denseArray(entities, "EntityQuery.exclude_entities")) {
                excludedEntities.add(entityReference(entity, registration, "EntityQuery.exclude_entities"));
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
            // Filter compilation already converts Lua arrays to immutable sets. Copying those
            // sets again here doubled allocation for every query filter without adding safety.
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
        String id = ENTITY_TYPE_IDS.computeIfAbsent(entity.getType(), type -> {
            Object key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            return key == null ? null : key.toString();
        });
        return id == null ? "unknown" : id;
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
        List<String> strings = new ArrayList<>();
        for (LuaValue item : denseArray(value.checktable(), field)) {
            if (!item.isstring()) throw new LuaError("[MLua] " + field + " must contain only strings.");
            strings.add(item.tojstring());
        }
        return strings;
    }

    private static List<LuaValue> denseArray(LuaTable table, String field) {
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
            if (++entries > MAX_FILTER_ARRAY_ENTRIES) {
                throw new LuaError("[MLua] " + field + " cannot contain more than "
                    + MAX_FILTER_ARRAY_ENTRIES + " entries.");
            }
        }
        if (maximumIndex != entries) {
            throw new LuaError("[MLua] " + field + " must be dense; missing array values are not allowed.");
        }
        List<LuaValue> values = new ArrayList<>(entries);
        for (int index = 1; index <= entries; index++) values.add(table.get(index));
        return values;
    }

}
