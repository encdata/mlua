package dev.mlua.modules.world.service;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.entity.MLuaEntityQuery;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Shared Lua result construction for the world's player and entity query APIs. */
final class MLuaWorldEntityQueries {
    private MLuaWorldEntityQueries() {}

    static LuaTable players(Level level, AABB bounds, MLuaRegistration registration) {
        LuaTable result = new LuaTable();
        int index = 1;
        for (Player player : level.players()) {
            if (bounds == null || bounds.contains(player.position())) {
                result.set(index++, MLuaPlayerWrapper.playerEntityTable(player, registration));
            }
        }
        return result;
    }

    static LuaTable playersInRadius(Level level, double[] center, double radius,
                                    MLuaRegistration registration) {
        LuaTable result = new LuaTable();
        LuaTable playerFilter = new LuaTable();
        playerFilter.set("players_only", LuaValue.TRUE);
        MLuaEntityQuery.Filter query = MLuaEntityQuery.compileFilter(playerFilter, registration);
        int[] index = {1};
        MLuaEntityQuery.forEachWithinRadius(level, center[0], center[1], center[2], radius, null, query,
            entity -> {
                if (entity instanceof Player player) {
                    result.set(index[0]++, MLuaPlayerWrapper.playerEntityTable(player, registration));
                }
            });
        return result;
    }

    static LuaTable entities(Level level, AABB bounds, LuaValue filter,
                             MLuaRegistration registration) {
        LuaTable result = new LuaTable();
        MLuaEntityQuery.Filter query = MLuaEntityQuery.compileFilter(filter, registration);
        Iterable<Entity> candidates = bounds == null
            ? MLuaEntityQuery.loadedEntities(level)
            : level.getEntities((Entity) null, bounds);
        int index = 1;
        for (Entity entity : candidates) {
            if (query.matches(entity)) {
                result.set(index++, MLuaEntityWrapper.entityTable(entity, registration));
            }
        }
        return result;
    }

    static LuaTable entitiesInRadius(Level level, double[] center, double radius, LuaValue filter,
                                     MLuaRegistration registration) {
        LuaTable result = new LuaTable();
        MLuaEntityQuery.Filter query = MLuaEntityQuery.compileFilter(filter, registration);
        int[] index = {1};
        MLuaEntityQuery.forEachWithinRadius(level, center[0], center[1], center[2], radius, null, query,
            entity -> result.set(index[0]++, MLuaEntityWrapper.entityTable(entity, registration)));
        return result;
    }
}
