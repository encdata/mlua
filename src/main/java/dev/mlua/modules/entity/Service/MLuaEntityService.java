package dev.mlua.modules.entity.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.mcapi.MCApiRuntime;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.api.MLuaDescription;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.math.MLuaVector;
import dev.mlua.minecraft.entity.MLuaEntityLookup;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
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

    @MLuaDescription("Returns entities in the same world within a spherical radius of a Vector3, CFrame, or entity.")
    public LuaTable GetNearby(LuaValue centerValue, double radius) {
        if (!Double.isFinite(radius) || radius < 0.0) {
            throw new LuaError("[MLua] EntityService:GetNearby radius must be a finite, non-negative number.");
        }
        Entity centerEntity = entityValue(centerValue);
        double[] center = centerEntity == null
            ? MLuaCFrame.vector(centerValue)
            : new double[]{centerEntity.getX(), centerEntity.getY(), centerEntity.getZ()};
        Level level = centerEntity != null ? centerEntity.level() : currentLevel();
        LuaTable out = new LuaTable();
        if (level == null) return out;
        AABB box = new AABB(center[0] - radius, center[1] - radius, center[2] - radius,
            center[0] + radius, center[1] + radius, center[2] + radius);
        int index = 1;
        double radiusSquared = radius * radius;
        for (Entity entity : level.getEntities((Entity) null, box, entity -> entity != centerEntity)) {
            if (entity.distanceToSqr(center[0], center[1], center[2]) <= radiusSquared) {
                out.set(index++, wrap(entity));
            }
        }
        return out;
    }

    @MLuaDescription("Loads the target chunk and creates an entity in the supplied world, dimension, or the overworld by default.")
    public LuaValue Spawn(String typeId, LuaValue positionValue, Optional<LuaValue> requestedWorld) {
        if (context.mlua().server() == null) {
            throw new LuaError("[MLua] EntityService:Spawn is server-only.");
        }
        Entity anchor = entityValue(positionValue);
        double[] pos = position(positionValue);
        LuaValue worldValue = requestedWorld.orElse(LuaValue.NIL);
        ServerLevel level;
        if (worldValue.isnil() && anchor != null) {
            if (!(anchor.level() instanceof ServerLevel anchorLevel)) {
                throw new LuaError("[MLua] EntityService:Spawn requires a server-side entity anchor.");
            }
            level = anchorLevel;
        } else {
            level = resolveServerLevel(worldValue);
        }
        BlockPos blockPosition = BlockPos.containing(pos[0], pos[1], pos[2]);
        level.getChunk(blockPosition.getX() >> 4, blockPosition.getZ() >> 4);
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
        if (!level.addFreshEntity(entity)) return LuaValue.NIL;
        return wrap(entity);
    }

    @MLuaDescription("Removes an entity from its world.")
    public void Remove(LuaValue value) {
        requireEntity(value).remove(Entity.RemovalReason.DISCARDED);
    }

    @MLuaDescription("Kills a living entity.")
    public void Kill(LuaValue value) {
        Entity entity = requireEntity(value);
        //--version 26.1
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            throw new LuaError("[MLua] Kill requires a server-side entity.");
        }
        entity.kill(serverLevel);
        //--endversion
        //--version 1.20.1,1.21.1
        entity.kill();
        //--endversion
    }

    @MLuaDescription("Moves an entity to a position.")
    public void Teleport(LuaValue entityValue, LuaValue positionValue) {
        Entity entity = requireEntity(entityValue);
        double[] pos = MLuaCFrame.vector(positionValue);
        entity.teleportTo(pos[0], pos[1], pos[2]);
        entity.hurtMarked = true;
    }

    @MLuaDescription("Sets an entity's motion vector.")
    public void SetVelocity(LuaValue entityValue, LuaValue vectorValue) {
        Entity entity = requireEntity(entityValue);
        if (!MLuaVector.isVector3(vectorValue)) {
            throw new LuaError("[MLua] EntityService:SetVelocity expects a native Vector3 value.");
        }
        double[] velocity = MLuaVector.components3(vectorValue);
        entity.setDeltaMovement(velocity[0], velocity[1], velocity[2]);
        entity.hurtMarked = true;
    }

    @MLuaDescription("Applies generic damage to a living entity. Returns false when the target is not living or Minecraft rejects the damage.")
    public boolean Damage(LuaValue entityValue, double amount) {
        if (!Double.isFinite(amount) || amount < 0.0 || amount > Float.MAX_VALUE) {
            throw new LuaError("[MLua] EntityService:Damage amount must be a finite, non-negative number within Minecraft's damage range.");
        }
        Entity entity = requireEntity(entityValue);
        if (!(entity instanceof LivingEntity living)) return false;
        //--version 26.1
        float healthBefore = living.getHealth();
        float absorptionBefore = living.getAbsorptionAmount();
        living.hurt(living.level().damageSources().generic(), (float) amount);
        return living.getHealth() < healthBefore || living.getAbsorptionAmount() < absorptionBefore;
        //--endversion
        //--version 1.20.1,1.21.1
        return living.hurt(living.level().damageSources().generic(), (float) amount);
        //--endversion
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
            return MLuaEntityWrapper.requireLuaInstance(value, context.mlua().registration(), "Entity", "Entity");
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
            boolean entityInstance = context.mlua().registration().isInstanceOf(value, "Entity");
            boolean worldInstance = context.mlua().registration().isInstanceOf(value, "World");
            if (!entityInstance && !worldInstance) {
                throw new LuaError("[MLua] Expected a World instance, Entity instance, dimension id, or nil.");
            }
            LuaValue raw = value.checktable().rawget("__entity");
            if (entityInstance && raw.isuserdata(Entity.class)) {
                Entity entity = (Entity) raw.touserdata(Entity.class);
                if (entity.level() instanceof ServerLevel serverLevel) {
                    return serverLevel;
                }
            }
            LuaValue world = value.checktable().rawget("__level");
            if (worldInstance && world.isuserdata(Level.class) && world.touserdata(Level.class) instanceof ServerLevel serverLevel) {
                return serverLevel;
            }
            throw new LuaError("[MLua] The supplied World or Entity is not backed by a loaded server world.");
        }
        if (value.isstring()) {
            net.minecraft.resources.ResourceLocation dimension = dev.mlua.minecraft.MLuaResourceLocations.tryParseOrNull(value.tojstring());
            if (dimension == null) throw new LuaError("[MLua] Invalid dimension identifier: " + value.tojstring());
            for (ServerLevel level : context.mlua().server().getAllLevels()) {
                if (level.dimension().location().equals(dimension)) return level;
            }
            throw new LuaError("[MLua] Dimension is not loaded: " + value.tojstring());
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

    private Entity entityValue(LuaValue value) {
        if (!value.istable()) return null;
        if (!context.mlua().registration().isInstanceOf(value, "Entity")) return null;
        return MLuaEntityWrapper.requireLuaInstance(value, context.mlua().registration(), "Entity", "Entity");
    }

    private double[] position(LuaValue value) {
        Entity entity = entityValue(value);
        if (entity != null) return new double[]{entity.getX(), entity.getY(), entity.getZ()};
        return MLuaCFrame.vector(value);
    }

}
