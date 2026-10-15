package dev.mlua.modules.entity.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.mcapi.MCApiRuntime;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.api.MLuaDescription;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.modules.entity.MLuaEntityQuery;
import dev.mlua.minecraft.entity.MLuaEntityLookup;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@MLuaDescription("Query, spawn, move, damage, and remove Minecraft entities from Lua.")
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
        try {
            return wrap(resolveEntity(UUID.fromString(uuid)));
        } catch (IllegalArgumentException invalidUuid) {
            throw new LuaError("[MLua] EntityService:FindByUUID expects a valid UUID string.");
        }
    }

    @MLuaDescription("Returns a live entity instance by UUID, or nil when absent.")
    public LuaValue GetByUUID(String uuid) {
        return FindByUUID(uuid);
    }

    @MLuaDescription("Returns all entities currently loaded on this side.")
    public LuaTable GetAll() {
        return allEntities();
    }

    @MLuaDescription("Returns entities in the same world within a spherical radius, optionally matching an EntityQuery filter.")
    public LuaTable GetNearby(LuaValue centerValue, double radius, Optional<LuaValue> requestedFilter) {
        radius = MLuaLuaArguments.finiteNonNegativeNumber(radius, "EntityService:GetNearby", "radius");
        Entity centerEntity = entityValue(centerValue);
        double[] center = centerEntity == null
            ? MLuaLuaArguments.finiteVector3(centerValue, "EntityService:GetNearby center")
            : new double[]{centerEntity.getX(), centerEntity.getY(), centerEntity.getZ()};
        Level level = centerEntity != null ? centerEntity.level() : currentLevel();
        LuaTable out = new LuaTable();
        if (level == null) return out;
        int index = 1;
        for (Entity entity : MLuaEntityQuery.findWithinRadius(level, center[0], center[1], center[2],
                radius, centerEntity, requestedFilter.orElse(LuaValue.NIL), context.mlua().registration())) {
            out.set(index++, wrap(entity));
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
        invokeEntityMethod(requireEntity(value), "Remove");
    }

    @MLuaDescription("Kills an entity using the same server-side behavior as Entity:Kill.")
    public void Kill(LuaValue value) {
        invokeEntityMethod(requireEntity(value), "Kill");
    }

    @MLuaDescription("Moves an entity using the same position conversion as Entity:Teleport.")
    public void Teleport(LuaValue entityValue, LuaValue positionValue) {
        Entity entity = requireEntity(entityValue);
        invokeEntityMethod(entity, "Teleport", positionValue);
    }

    @MLuaDescription("Sets an entity's motion using the same validation as Entity:SetVelocity.")
    public void SetVelocity(LuaValue entityValue, LuaValue vectorValue) {
        Entity entity = requireEntity(entityValue);
        invokeEntityMethod(entity, "SetVelocity", vectorValue);
    }

    @MLuaDescription("Applies damage to a living entity using LivingEntity:Damage, with an optional damage type and attacker. Returns false for non-living targets or rejected damage.")
    public boolean Damage(LuaValue entityValue, double amount, Optional<String> damageType, Optional<LuaValue> attacker) {
        Entity entity = requireEntity(entityValue);
        LuaTable instance = (LuaTable) wrap(entity);
        LuaValue damage = instance.get("Damage");
        if (!(entity instanceof LivingEntity) || !damage.isfunction()) return false;
        return damage.invoke(LuaValue.varargsOf(new LuaValue[]{
            instance,
            LuaValue.valueOf(amount),
            LuaValue.valueOf(damageType.orElse("generic")),
            attacker.orElse(LuaValue.NIL)
        })).arg1().toboolean();
    }

    private LuaValue invokeEntityMethod(Entity entity, String methodName, LuaValue... arguments) {
        LuaTable instance = (LuaTable) wrap(entity);
        LuaValue method = instance.get(methodName);
        if (!method.isfunction()) {
            throw new LuaError("[MLua] Entity method '" + methodName + "' is not registered for "
                + entity.getType() + ".");
        }
        LuaValue[] callArguments = new LuaValue[arguments.length + 1];
        callArguments[0] = instance;
        System.arraycopy(arguments, 0, callArguments, 1, arguments.length);
        return method.invoke(LuaValue.varargsOf(callArguments)).arg1();
    }

    private LuaTable allEntities() {
        LuaTable out = new LuaTable();
        int index = 1;
        if (context.mlua().side() == Side.CLIENT) {
            Level level = clientLevel();
            if (level == null) {
                return out;
            }
            for (Entity entity : MLuaEntityQuery.loadedEntities(level)) {
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
            for (Entity entity : MLuaEntityQuery.loadedEntities(level)) {
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
        return MLuaLuaArguments.finiteVector3(value, "EntityService position");
    }

}
