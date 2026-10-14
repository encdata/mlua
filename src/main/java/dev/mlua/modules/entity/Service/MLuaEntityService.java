package dev.mlua.modules.entity.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.mcapi.MCApiRuntime;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.api.MLuaDescription;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.entity.MLuaEntityLookup;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public final class MLuaEntityService extends MLuaReflectiveService {
    public MLuaEntityService(MLuaService.Context context) {
        super(context);
    }

    @Override
    public String name() {
        return "EntityService";
    }

    @Override
    public LuaTable toLua() {
        LuaTable table = super.toLua();
        table.set("__side", "Both");
        return table;
    }

    @MLuaDescription("Returns a live entity instance by runtime ID, or nil when absent.")
    public LuaValue GetById(int id) {
        return wrap(resolveEntity(id));
    }

    @MLuaDescription("Returns a live entity instance by UUID, or nil when absent.")
    public LuaValue FindByUUID(String uuid) {
        return wrap(resolveEntity(UUID.fromString(uuid)));
    }

    @MLuaDescription("Returns a live entity instance by UUID, or nil when absent.")
    public LuaValue GetByUUID(String uuid) {
        return FindByUUID(uuid);
    }

    @MLuaDescription("Returns all entities currently loaded on this side.")
    public LuaTable GetAll() {
        return allEntities();
    }

    @MLuaDescription("Returns entities near a Vector3, CFrame, or entity within a radius. Coordinates may also be supplied as x, y, z, radius.")
    public LuaTable GetNearby(LuaValue centerValue, Optional<LuaValue> second, Optional<LuaValue> third, Optional<LuaValue> fourth) {
        Entity centerEntity = centerValue.istable() && !centerValue.checktable().rawget("__entity").isnil()
            ? requireEntity(centerValue) : null;
        double[] center = centerEntity == null
            ? position(centerValue, second.orElse(LuaValue.NIL), third.orElse(LuaValue.NIL))
            : new double[]{centerEntity.getX(), centerEntity.getY(), centerEntity.getZ()};
        LuaValue radiusValue = centerEntity != null || isPosition(centerValue)
            ? second.orElse(LuaValue.NIL)
            : fourth.orElse(LuaValue.NIL);
        double radius = radiusValue.checkdouble();
        Level level = centerEntity != null ? centerEntity.level() : currentLevel();
        LuaTable out = new LuaTable();
        if (level == null) return out;
        AABB box = new AABB(center[0] - radius, center[1] - radius, center[2] - radius,
            center[0] + radius, center[1] + radius, center[2] + radius);
        int index = 1;
        for (Entity entity : level.getEntities((Entity) null, box, entity -> entity != centerEntity)) {
            out.set(index++, wrap(entity));
        }
        return out;
    }

    @MLuaDescription("Creates an entity at a position in a world.")
    public LuaValue Spawn(String typeId, LuaValue positionValue, Optional<LuaValue> second,
                          Optional<LuaValue> third, Optional<LuaValue> fourth) {
        if (context.mlua().server() == null) {
            throw new LuaError("[MLua] EntityService:Spawn is server-only.");
        }
        double[] pos = position(positionValue, second.orElse(LuaValue.NIL), third.orElse(LuaValue.NIL));
        LuaValue worldValue = isPosition(positionValue) ? second.orElse(LuaValue.NIL) : fourth.orElse(LuaValue.NIL);
        ServerLevel level = resolveServerLevel(worldValue);
        EntityType<?> type = (EntityType<?>) MCApiRuntime.lookupRegistry("entity_type", typeId);
        if (type == null) {
            throw new LuaError("[MLua] Unknown entity type: " + typeId);
        }
        //--version 26.1
        Entity entity = type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        //--endversion
        //--version 1.20.1,1.21.1
        Entity entity = type.create(level);
        //--endversion
        if (entity == null) {
            throw new LuaError("[MLua] Failed to create entity: " + typeId);
        }
        //--version 26.1
        entity.setPos(pos[0], pos[1], pos[2]);
        entity.setYRot(0.0F);
        entity.setXRot(0.0F);
        //--endversion
        //--version 1.20.1,1.21.1
        entity.moveTo(pos[0], pos[1], pos[2], 0.0F, 0.0F);
        //--endversion
        level.addFreshEntity(entity);
        return wrap(entity);
    }

    @MLuaDescription("Removes an entity from its world.")
    public void Remove(LuaValue value) {
        requireEntity(value).remove(Entity.RemovalReason.DISCARDED);
    }

    @MLuaDescription("Kills a living entity.")
    public void Kill(LuaValue value) {
        requireEntity(value).kill();
    }

    @MLuaDescription("Moves an entity to a position.")
    public void Teleport(LuaValue entityValue, LuaValue positionValue, Optional<LuaValue> y, Optional<LuaValue> z) {
        Entity entity = requireEntity(entityValue);
        double[] pos = position(positionValue, y.orElse(LuaValue.NIL), z.orElse(LuaValue.NIL));
        entity.teleportTo(pos[0], pos[1], pos[2]);
        entity.hurtMarked = true;
    }

    @MLuaDescription("Sets an entity's motion vector.")
    public void SetVelocity(LuaValue entityValue, LuaValue vectorValue, Optional<LuaValue> y, Optional<LuaValue> z) {
        Entity entity = requireEntity(entityValue);
        double[] velocity = position(vectorValue, y.orElse(LuaValue.NIL), z.orElse(LuaValue.NIL));
        entity.setDeltaMovement(velocity[0], velocity[1], velocity[2]);
        entity.hurtMarked = true;
    }

    @MLuaDescription("Applies generic damage to a living entity and returns whether it was applied.")
    public boolean Damage(LuaValue entityValue, double amount) {
        Entity entity = requireEntity(entityValue);
        if (!(entity instanceof LivingEntity living)) return false;
        living.hurt(living.level().damageSources().generic(), (float) amount);
        return true;
    }

    private LuaTable allEntities() {
        LuaTable out = new LuaTable();
        int index = 1;
        if (context.mlua().side() == Side.CLIENT) {
            Level level = clientLevel();
            if (level == null) {
                return out;
            }
            for (Entity entity : clientRenderingEntities(level)) {
                out.set(index++, wrap(entity));
            }
            return out;
        }
        if (context.mlua().server() == null) {
            return out;
        }
        for (ServerLevel level : context.mlua().server().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                out.set(index++, wrap(entity));
            }
        }
        return out;
    }

    private Entity resolveEntity(int id) {
        if (context.mlua().side() == Side.CLIENT) {
            Level level = clientLevel();
            return level != null ? level.getEntity(id) : null;
        }
        if (context.mlua().server() == null) {
            return null;
        }
        MLuaEntityLookup.LookupResult<Entity> lookup = MLuaEntityLookup.findById(context.mlua().server(), id);
        if (lookup.ambiguous()) {
            throw new LuaError("[MLua] Entity id " + id + " exists in multiple dimensions; pass an entity object");
        }
        return lookup.value();
    }

    private Entity resolveEntity(UUID uuid) {
        if (context.mlua().side() == Side.CLIENT) {
            Level level = clientLevel();
            if (level == null) {
                return null;
            }
            for (Entity entity : clientRenderingEntities(level)) {
                if (uuid.equals(entity.getUUID())) {
                    return entity;
                }
            }
            return null;
        }
        if (context.mlua().server() == null) {
            return null;
        }
        for (ServerLevel level : context.mlua().server().getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private Entity requireEntity(LuaValue value) {
        if (value.istable()) {
            LuaValue raw = value.checktable().rawget("__entity");
            if (raw.isuserdata(Entity.class)) {
                return (Entity) raw.touserdata(Entity.class);
            }
            LuaValue id = value.get("Id");
            if (id.isint()) {
                Entity entity = resolveEntity(id.toint());
                if (entity != null) {
                    return entity;
                }
            }
        }
        if (value.isint()) {
            Entity entity = resolveEntity(value.toint());
            if (entity != null) {
                return entity;
            }
        }
        throw new LuaError("[MLua] Expected entity table or entity id.");
    }

    private ServerLevel resolveServerLevel(LuaValue value) {
        if (context.mlua().server() == null) {
            throw new LuaError("[MLua] Server is not available.");
        }
        if (value.istable()) {
            LuaValue raw = value.checktable().rawget("__entity");
            if (raw.isuserdata(Entity.class)) {
                Entity entity = (Entity) raw.touserdata(Entity.class);
                if (entity.level() instanceof ServerLevel serverLevel) {
                    return serverLevel;
                }
            }
        }
        ServerPlayer player = context.mlua().server().getPlayerList().getPlayers().isEmpty()
            ? null
            : context.mlua().server().getPlayerList().getPlayers().get(0);
        if (player != null) {
            return player.serverLevel();
        }
        return context.mlua().server().overworld();
    }

    private Level currentLevel() {
        if (context.mlua().side() == Side.CLIENT) {
            return clientLevel();
        }
        return context.mlua().server() == null ? null : context.mlua().server().overworld();
    }

    private static Level clientLevel() {
        try {
            Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft");
            Object instance = minecraft.getMethod("getInstance").invoke(null);
            Object level = instance == null ? null : minecraft.getField("level").get(instance);
            return level instanceof Level rawLevel ? rawLevel : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Iterable<Entity> clientRenderingEntities(Level level) {
        try {
            Object value = level.getClass().getMethod("entitiesForRendering").invoke(level);
            if (value instanceof Iterable<?> iterable) {
                List<Entity> entities = new ArrayList<>();
                for (Object entity : iterable) {
                    if (entity instanceof Entity rawEntity) entities.add(rawEntity);
                }
                return entities;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return List.of();
    }
    private LuaValue wrap(Entity entity) {
        if (entity == null) {
            return LuaValue.NIL;
        }
        return MLuaEntityWrapper.entityTable(entity, context.mlua().registration());
    }

    private static boolean isPosition(LuaValue value) {
        if (!value.istable()) return false;
        if (MLuaCFrame.isCFrame(value)) return true;
        LuaTable table = value.checktable();
        if (table.rawget("__entity").isuserdata(Entity.class)) return true;
        double x = table.get("X").optdouble(table.get("x").optdouble(Double.NaN));
        double y = table.get("Y").optdouble(table.get("y").optdouble(Double.NaN));
        double z = table.get("Z").optdouble(table.get("z").optdouble(Double.NaN));
        return !Double.isNaN(x) && !Double.isNaN(y) && !Double.isNaN(z);
    }

    private static double[] position(LuaValue value, LuaValue yValue, LuaValue zValue) {
        if (value.istable()) {
            if (MLuaCFrame.isCFrame(value)) {
                MLuaCFrame cf = MLuaCFrame.fromLua(value);
                return new double[]{cf.x(), cf.y(), cf.z()};
            }
            LuaTable table = value.checktable();
            LuaValue rawEntity = table.rawget("__entity");
            if (rawEntity.isuserdata(Entity.class)) {
                Entity entity = (Entity) rawEntity.touserdata(Entity.class);
                return new double[]{entity.getX(), entity.getY(), entity.getZ()};
            }
            double x = table.get("X").optdouble(table.get("x").optdouble(Double.NaN));
            double y = table.get("Y").optdouble(table.get("y").optdouble(Double.NaN));
            double z = table.get("Z").optdouble(table.get("z").optdouble(Double.NaN));
            if (!Double.isNaN(x) && !Double.isNaN(y) && !Double.isNaN(z)) {
                return new double[]{x, y, z};
            }
        }
        if (value.isnumber() && yValue.isnumber() && zValue.isnumber()) {
            return new double[]{value.checkdouble(), yValue.checkdouble(), zValue.checkdouble()};
        }
        throw new LuaError("[MLua] Expected Vector3/CFrame/entity position or x, y, z.");
    }

}
