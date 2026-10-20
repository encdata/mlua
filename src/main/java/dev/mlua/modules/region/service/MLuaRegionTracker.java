package dev.mlua.modules.region.service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaLogger;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.entity.MLuaEntityQuery;
import dev.mlua.modules.players.MLuaClientPlayerAccess;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Scans loaded levels and dispatches region membership transitions. */
final class MLuaRegionTracker {
    /** Above this extent, querying the level's spatial index can walk many empty sections. */
    private static final double MAX_INDEXED_REGION_EXTENT = 256.0D;

    private MLuaRegionTracker() { }

    static void tick(MinecraftServer server) {
        List<Level> levels = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) levels.add(level);
        tickStates(Side.SERVER, levels);
    }

    static void tickClient() {
        Level level = currentClientLevel();
        if (level != null) tickStates(Side.CLIENT, List.of(level));
    }

    static Level currentClientLevel() {
        try {
            Object level = MLuaClientPlayerAccess.level();
            return level instanceof Level clientLevel ? clientLevel : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    private static void tickStates(Side side, List<Level> levels) {
        if (levels.isEmpty()) return;
        for (MLuaRegionStateRegistry.RegionState state : MLuaRegionStateRegistry.statesSnapshot()) {
            tickState(state, side, levels);
        }
    }

    private static void tickState(MLuaRegionStateRegistry.RegionState state, Side side, List<Level> levels) {
        if (state.regions.isEmpty()) return;
        for (var entry : state.regions.entrySet()) {
            MLuaRegionStateRegistry.RegionKey key = entry.getKey();
            if (key.side() != side) continue;

            MLuaRegionStateRegistry.RegionDef definition = entry.getValue();
            Set<MLuaEntityScope> current = new HashSet<>();
            for (Level level : levels) {
                for (Entity entity : candidates(level, definition)) {
                    if (definition.contains(entity.position())) current.add(MLuaEntityScope.of(entity));
                }
            }

            Set<MLuaEntityScope> previous = state.entities.getOrDefault(key, Set.of());
            boolean unchanged = current.equals(previous);
            // Preserve the immutable snapshot when membership is stable; region polling often
            // observes the same set for many consecutive ticks.
            state.entities.put(key, unchanged ? previous : Set.copyOf(current));

            MLuaSignal entered = state.onEnter.get(key);
            if (entered != null && !unchanged) {
                fireChanged(state.registrations.get(key), entered, levels,
                    MLuaRegionMembership.difference(current, previous), key.name());
            }

            MLuaSignal exited = state.onExit.get(key);
            if (exited != null && !unchanged) {
                fireChanged(state.registrations.get(key), exited, levels,
                    MLuaRegionMembership.difference(previous, current), key.name());
            }

            MLuaSignal within = state.onWithin.get(key);
            if (within != null) {
                for (MLuaEntityScope scope : current) {
                    if (previous.contains(scope)) {
                        fireEntitySignal(state.registrations.get(key), within, levels, scope, key.name());
                    }
                }
            }
        }
    }

    private static Iterable<Entity> candidates(Level level, MLuaRegionStateRegistry.RegionDef definition) {
        // Keep small regions on Minecraft's indexed AABB query. Broad regions use loaded entities
        // directly so a large but valid region cannot make the section index scan empty space.
        if (definition.bounds.getXsize() <= MAX_INDEXED_REGION_EXTENT
                && definition.bounds.getYsize() <= MAX_INDEXED_REGION_EXTENT
                && definition.bounds.getZsize() <= MAX_INDEXED_REGION_EXTENT) {
            return level.getEntitiesOfClass(Entity.class, definition.bounds);
        }
        return MLuaEntityQuery.loadedEntities(level);
    }

    private static void fireChanged(MLuaRegistration registration, MLuaSignal signal, List<Level> levels,
                                    List<MLuaEntityScope> scopes, String regionName) {
        for (MLuaEntityScope scope : scopes) fireEntitySignal(registration, signal, levels, scope, regionName);
    }

    private static void fireEntitySignal(MLuaRegistration registration, MLuaSignal signal, List<Level> levels,
                                         MLuaEntityScope scope, String regionName) {
        Entity entity = resolveEntity(scope, levels);
        LuaTable arguments = new LuaTable();
        if (entity != null) arguments.set("Entity", MLuaEntityWrapper.entityTable(entity, registration));
        arguments.set("Region", LuaValue.valueOf(regionName));
        try {
            signal.fire(arguments);
        } catch (Exception failure) {
            MLuaLogger.LOGGER.error("[MLua] Region signal handler error", failure);
        }
    }

    static Entity resolveEntity(MLuaEntityScope scope, List<Level> levels) {
        for (Level level : levels) {
            if (!level.dimension().location().toString().equals(scope.dimension())) continue;
            Entity entity = level.getEntity(scope.entityId());
            if (entity != null && entity.getUUID().equals(scope.uuid())) return entity;
        }
        return null;
    }
}
