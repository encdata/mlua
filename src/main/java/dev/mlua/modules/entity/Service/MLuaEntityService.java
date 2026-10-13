package dev.mlua.modules.entity.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.mcapi.MCApiRuntime;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.entity.MLuaEntityLookup;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaEntityService extends MLuaServiceBase {
    public MLuaEntityService(MLuaService.Context context) {
        super(context);
    }

    @Override
    public String name() {
        return "EntityService";
    }

    @Override
    public LuaTable toLua() {
        LuaTable table = new LuaTable();
        table.set("__side", "Both");
        table.set("GetById", fn(args -> wrap(resolveEntity(args.checkint(2)))));
        table.set("FindByUUID", fn(args -> wrap(resolveEntity(UUID.fromString(args.checkjstring(2))))));
        table.set("GetByUUID", table.get("FindByUUID"));
        table.set("GetAll", fn(args -> allEntities(args)));
        table.set("GetNearby", fn(args -> nearbyEntities(args)));
        table.set("Spawn", fn(args -> spawn(args)));
        table.set("Remove", fn(args -> {
            Entity entity = requireEntity(args.arg(2));
            entity.remove(Entity.RemovalReason.DISCARDED);
            return LuaValue.NONE;
        }));
        table.set("Kill", fn(args -> {
            Entity entity = requireEntity(args.arg(2));
            entity.kill();
            return LuaValue.NONE;
        }));
        table.set("Teleport", fn(args -> {
            Entity entity = requireEntity(args.arg(2));
            double[] pos = position(args.arg(3), args, 3);
            entity.teleportTo(pos[0], pos[1], pos[2]);
            entity.hurtMarked = true;
            return LuaValue.NONE;
        }));
        table.set("SetVelocity", fn(args -> {
            Entity entity = requireEntity(args.arg(2));
            double[] vel = position(args.arg(3), args, 3);
            entity.setDeltaMovement(vel[0], vel[1], vel[2]);
            entity.hurtMarked = true;
            return LuaValue.NONE;
        }));
        table.set("Damage", fn(args -> {
            Entity entity = requireEntity(args.arg(2));
            if (entity instanceof LivingEntity living) {
                float amount = (float) args.checkdouble(3);
                living.hurt(living.level().damageSources().generic(), amount);
                return LuaValue.TRUE;
            }
            return LuaValue.FALSE;
        }));
        return table;
    }

    private LuaValue spawn(Varargs args) {
        if (context.mlua().server() == null) {
            throw new LuaError("[MLua] EntityService:Spawn is server-only.");
        }
        String typeId = args.checkjstring(2);
        double[] pos = position(args.arg(3), args, 3);
        ServerLevel level = resolveServerLevel(args.arg(4));
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

    private LuaValue allEntities(Varargs args) {
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

    private LuaValue nearbyEntities(Varargs args) {
        Entity centerEntity = null;
        double[] center;
        if (args.arg(2).istable() && !args.arg(2).checktable().rawget("__entity").isnil()) {
            centerEntity = requireEntity(args.arg(2));
            center = new double[]{centerEntity.getX(), centerEntity.getY(), centerEntity.getZ()};
        } else {
            center = position(args.arg(2), args, 2);
        }
        double radius = args.arg(2).istable() ? args.checkdouble(3) : args.checkdouble(5);
        Level level = centerEntity != null ? centerEntity.level() : currentLevel();
        LuaTable out = new LuaTable();
        if (level == null) {
            return out;
        }
        AABB box = new AABB(center[0] - radius, center[1] - radius, center[2] - radius, center[0] + radius, center[1] + radius, center[2] + radius);
        int index = 1;
        Entity excluded = centerEntity;
        for (Entity entity : level.getEntities((Entity) null, box, entity -> entity != excluded)) {
            out.set(index++, wrap(entity));
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

    /** Resolve client-only state without linking client classes on a dedicated server. */
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
            // A server-side Level has no client rendering collection.
        }
        return List.of();
    }
    private LuaValue wrap(Entity entity) {
        if (entity == null) {
            return LuaValue.NIL;
        }
        return MLuaEntityWrapper.entityTable(entity, context.mlua().registration());
    }

    private static LuaTable positionTable(double x, double y, double z) {
        LuaTable table = new LuaTable();
        table.set("X", x);
        table.set("Y", y);
        table.set("Z", z);
        table.set("x", x);
        table.set("y", y);
        table.set("z", z);
        return table;
    }

    private static double[] position(LuaValue value, Varargs args, int start) {
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
        if (args.narg() >= start + 2) {
            return new double[]{args.checkdouble(start), args.checkdouble(start + 1), args.checkdouble(start + 2)};
        }
        throw new LuaError("[MLua] Expected Vector3/CFrame/entity position or x, y, z.");
    }

    private static VarArgFunction fn(Function<Varargs, LuaValue> body) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    return body.apply(args);
                } catch (LuaError e) {
                    throw e;
                } catch (Exception e) {
                    throw new LuaError("[MLua] " + e.toString());
                }
            }
        };
    }
}
