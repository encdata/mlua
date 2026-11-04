package dev.mlua.modules.entity;

import dev.mlua.module.MLuaRegistration;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Shared loaded-entity traversal and spatial queries used by world, service, and entity APIs. */
public final class MLuaEntityQuery {
    public static final int MAX_FILTER_ARRAY_ENTRIES = 1024;
    /** Avoid scanning millions of empty entity sections for broad, finite radius queries. */
    private static final double MAX_INDEXED_QUERY_RADIUS = 128.0D;
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
        return new Filter(MLuaEntityQueryFilter.compile(filter, registration));
    }

    /** Reuses the immutable filter used by built-in searches for living players. */
    public static Filter alivePlayersFilter() {
        return AlivePlayersFilter.VALUE;
    }

    /** Builds a lightweight alive-entity filter without creating a Lua filter table. */
    public static Filter aliveEntitiesFilter(String type) {
        if (type == null) return AliveEntitiesFilter.VALUE;
        return new Filter(MLuaEntityQueryFilter.aliveEntities(type));
    }

    /** Immutable compiled query filter; kept as the stable type consumed by existing callers. */
    public static final class Filter {
        private final MLuaEntityQueryFilter.Compiled compiled;

        private Filter(MLuaEntityQueryFilter.Compiled compiled) {
            this.compiled = compiled;
        }

        public boolean matches(Entity entity) {
            return this.compiled.matches(entity);
        }

        boolean matchesType(String entityType) {
            return this.compiled.matchesType(entityType);
        }
    }

    private static final class AlivePlayersFilter {
        private static final Filter VALUE = new Filter(MLuaEntityQueryFilter.alivePlayers());
    }

    private static final class AliveEntitiesFilter {
        private static final Filter VALUE = new Filter(MLuaEntityQueryFilter.aliveEntities(null));
    }

    /** Validates a filter, including when a query will find no candidates. */
    public static void validateFilter(LuaValue filter, MLuaRegistration registration) {
        compileFilter(filter, registration);
    }

}
