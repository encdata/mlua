package dev.mlua.modules.entity.service;

import java.util.Optional;

import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.modules.entity.MLuaEntityQuery;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Owns EntityService's filtered lookups, world scans, and spatial query facade. */
final class MLuaEntityQueryOperations {
    private final MLuaService.Context context;
    private final MLuaEntityService service;

    MLuaEntityQueryOperations(MLuaService.Context context, MLuaEntityService service) {
        this.context = context;
        this.service = service;
    }

    LuaValue getById(int id) {
        return this.service.wrap(this.service.resolveEntity(id));
    }

    LuaValue getByUuid(String uuid) {
        return this.service.wrap(this.service.resolveEntity(MLuaLuaArguments.uuid(uuid, "EntityService:FindByUUID", "uuid")));
    }

    LuaTable getAll(Optional<LuaValue> requestedFilter) {
        MLuaEntityQuery.Filter filter = MLuaEntityQuery.compileFilter(
            requestedFilter.orElse(LuaValue.NIL), context.mlua().registration());
        return allEntities(filter);
    }

    LuaValue getFirst(Optional<LuaValue> requestedFilter) {
        MLuaEntityQuery.Filter filter = MLuaEntityQuery.compileFilter(
            requestedFilter.orElse(LuaValue.NIL), context.mlua().registration());
        return this.service.wrap(findFirstLoadedEntity(filter));
    }

    LuaTable getInWorld(LuaValue worldValue, Optional<LuaValue> requestedFilter) {
        MLuaEntityQuery.Filter filter = MLuaEntityQuery.compileFilter(
            requestedFilter.orElse(LuaValue.NIL), context.mlua().registration());
        Level level = resolveQueryLevel(worldValue);
        LuaTable result = new LuaTable();
        if (level == null) return result;
        int index = 1;
        for (Entity entity : MLuaEntityQuery.loadedEntities(level)) {
            if (filter.matches(entity)) result.set(index++, this.service.wrap(entity));
        }
        return result;
    }

    LuaValue getFirstInWorld(LuaValue worldValue, Optional<LuaValue> requestedFilter) {
        MLuaEntityQuery.Filter filter = MLuaEntityQuery.compileFilter(
            requestedFilter.orElse(LuaValue.NIL), context.mlua().registration());
        Level level = resolveQueryLevel(worldValue);
        if (level == null) return LuaValue.NIL;
        for (Entity entity : MLuaEntityQuery.loadedEntities(level)) {
            if (filter.matches(entity)) return this.service.wrap(entity);
        }
        return LuaValue.NIL;
    }

    int countInWorld(LuaValue worldValue, Optional<LuaValue> requestedFilter) {
        MLuaEntityQuery.Filter filter = MLuaEntityQuery.compileFilter(
            requestedFilter.orElse(LuaValue.NIL), context.mlua().registration());
        Level level = resolveQueryLevel(worldValue);
        if (level == null) return 0;
        return countInLevel(level, filter);
    }

    int count(Optional<LuaValue> requestedFilter) {
        MLuaEntityQuery.Filter filter = MLuaEntityQuery.compileFilter(
            requestedFilter.orElse(LuaValue.NIL), context.mlua().registration());
        if (this.context.side() == Side.CLIENT) {
            Level level = this.service.clientLevel();
            return level == null ? 0 : countInLevel(level, filter);
        }
        if (this.context.server() == null) return 0;
        int count = 0;
        for (ServerLevel level : this.context.server().getAllLevels()) {
            count += countInLevel(level, filter);
        }
        return count;
    }

    LuaTable getNearby(LuaValue centerValue, double radius, Optional<LuaValue> requestedFilter) {
        radius = MLuaLuaArguments.finiteNonNegativeNumber(radius, "EntityService:GetNearby", "radius");
        Entity centerEntity = this.service.entityValue(centerValue);
        double[] center = centerEntity == null
            ? MLuaLuaArguments.finiteVector3(centerValue, "EntityService:GetNearby center")
            : new double[]{centerEntity.getX(), centerEntity.getY(), centerEntity.getZ()};
        MLuaEntityQuery.Filter filter = MLuaEntityQuery.compileFilter(
            requestedFilter.orElse(LuaValue.NIL), context.mlua().registration());
        Level level = centerEntity != null ? centerEntity.level() : this.service.currentLevel();
        LuaTable out = new LuaTable();
        if (level == null) return out;
        int[] index = {1};
        MLuaEntityQuery.forEachWithinRadius(level, center[0], center[1], center[2],
            radius, centerEntity, filter, entity -> out.set(index[0]++, this.service.wrap(entity)));
        return out;
    }

    LuaValue getNearest(LuaValue centerValue, double radius, Optional<LuaValue> requestedFilter) {
        radius = MLuaLuaArguments.finiteNonNegativeNumber(radius, "EntityService:GetNearest", "radius");
        Entity centerEntity = this.service.entityValue(centerValue);
        double[] center = centerEntity == null
            ? MLuaLuaArguments.finiteVector3(centerValue, "EntityService:GetNearest center")
            : new double[]{centerEntity.getX(), centerEntity.getY(), centerEntity.getZ()};
        MLuaEntityQuery.Filter filter = MLuaEntityQuery.compileFilter(
            requestedFilter.orElse(LuaValue.NIL), context.mlua().registration());
        Level level = centerEntity != null ? centerEntity.level() : this.service.currentLevel();
        return this.service.wrap(MLuaEntityQuery.findNearestWithinRadius(
            level, center[0], center[1], center[2], radius, centerEntity, filter));
    }

    LuaTable allEntities(MLuaEntityQuery.Filter filter) {
        LuaTable out = new LuaTable();
        int index = 1;
        if (context.mlua().side() == Side.CLIENT) {
            Level level = this.service.clientLevel();
            if (level == null) {
                return out;
            }
            for (Entity entity : MLuaEntityQuery.loadedEntities(level)) {
                if (filter.matches(entity)) out.set(index++, this.service.wrap(entity));
            }
            return out;
        }
        if (context.mlua().server() == null) {
            return out;
        }
        for (ServerLevel level : context.server().getAllLevels()) {
            for (Entity entity : MLuaEntityQuery.loadedEntities(level)) {
                if (filter.matches(entity)) out.set(index++, this.service.wrap(entity));
            }
        }
        return out;
    }

    Entity findFirstLoadedEntity(MLuaEntityQuery.Filter filter) {
        if (context.mlua().side() == Side.CLIENT) {
            Level level = this.service.clientLevel();
            if (level == null) return null;
            for (Entity entity : MLuaEntityQuery.loadedEntities(level)) {
                if (filter.matches(entity)) return entity;
            }
            return null;
        }
        if (context.mlua().server() == null) return null;
        for (ServerLevel level : context.mlua().server().getAllLevels()) {
            for (Entity entity : MLuaEntityQuery.loadedEntities(level)) {
                if (filter.matches(entity)) return entity;
            }
        }
        return null;
    }

    private static int countInLevel(Level level, MLuaEntityQuery.Filter filter) {
        int count = 0;
        for (Entity entity : MLuaEntityQuery.loadedEntities(level)) {
            if (filter.matches(entity)) count++;
        }
        return count;
    }

    Level resolveQueryLevel(LuaValue value) {
        if (context.mlua().side() != Side.CLIENT) return this.service.resolveServerLevel(value);
        Level current = this.service.clientLevel();
        if (value.isnil()) return current;
        if (value.istable() && context.mlua().registration().isInstanceOf(value, "Entity")) {
            Entity entity = this.service.entityValue(value);
            if (entity != null && entity.level() == current) return current;
            throw new LuaError("[MLua] EntityService query entity is not in the current client world.");
        }
        if (!value.istable() || !context.mlua().registration().isInstanceOf(value, "World")) {
            throw new LuaError("[MLua] EntityService world queries expect a World or Entity instance on the client.");
        }
        LuaValue raw = value.checktable().rawget("__level");
        if (!raw.isuserdata(Level.class) || raw.touserdata(Level.class) != current) {
            throw new LuaError("[MLua] EntityService world queries can only query the current client world.");
        }
        return current;
    }
}
