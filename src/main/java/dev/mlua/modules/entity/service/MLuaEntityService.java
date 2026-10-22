package dev.mlua.modules.entity.service;

import dev.mlua.minecraft.MLuaResourceLocations;
import net.minecraft.resources.ResourceLocation;

import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServerThreadReflectiveService;
import dev.mlua.api.MLuaDescription;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.modules.entity.MLuaEntityQuery;
import dev.mlua.modules.entity.MLuaEntitySpawner;
import dev.mlua.minecraft.entity.MLuaEntityLookup;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.players.MLuaClientPlayerAccess;
import dev.mlua.math.MLuaCFrame;
import java.util.UUID;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@MLuaDescription("Query, spawn, move, damage, and remove Minecraft entities from Lua.")
public final class MLuaEntityService extends MLuaServerThreadReflectiveService {
    private final MLuaEntityQueryOperations queryOperations;
    public MLuaEntityService(MLuaService.Context context) {
        super(context);
        this.queryOperations = new MLuaEntityQueryOperations(context, this);
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
        return this.queryOperations.getById(id);
    }

    @MLuaDescription("Returns a live entity instance by UUID, or nil when absent.")
    public LuaValue FindByUUID(String uuid) {
        return this.queryOperations.getByUuid(uuid);
    }

    @MLuaDescription("Returns a live entity instance by UUID, or nil when absent.")
    public LuaValue GetByUUID(String uuid) {
        return this.queryOperations.getByUuid(uuid);
    }

    @MLuaDescription("Returns loaded entities, optionally matching an EntityQuery filter. Servers search loaded dimensions; clients search the current world.")
    public LuaTable GetAll(Optional<LuaValue> requestedFilter) {
        return this.queryOperations.getAll(requestedFilter);
    }

    @MLuaDescription("Returns the first loaded entity matching an EntityQuery filter, or nil when no entity matches.")
    public LuaValue GetFirst(Optional<LuaValue> requestedFilter) {
        return this.queryOperations.getFirst(requestedFilter);
    }

    @MLuaDescription("Returns loaded entities in one world, dimension, or entity's world, optionally matching an EntityQuery filter.")
    public LuaTable GetInWorld(LuaValue worldValue, Optional<LuaValue> requestedFilter) {
        return this.queryOperations.getInWorld(worldValue, requestedFilter);
    }

    @MLuaDescription("Returns the first loaded entity in one world, dimension, or entity's world matching an EntityQuery filter, or nil when none matches.")
    public LuaValue GetFirstInWorld(LuaValue worldValue, Optional<LuaValue> requestedFilter) {
        return this.queryOperations.getFirstInWorld(worldValue, requestedFilter);
    }

    @MLuaDescription("Counts loaded entities in one world, dimension, or entity's world, optionally matching an EntityQuery filter.")
    public int CountInWorld(LuaValue worldValue, Optional<LuaValue> requestedFilter) {
        return this.queryOperations.countInWorld(worldValue, requestedFilter);
    }

    @MLuaDescription("Counts loaded entities across the searchable worlds, optionally matching an EntityQuery filter.")
    public int Count(Optional<LuaValue> requestedFilter) {
        return this.queryOperations.count(requestedFilter);
    }

    @MLuaDescription("Returns entities in the same world within a spherical radius, optionally matching an EntityQuery filter.")
    public LuaTable GetNearby(LuaValue centerValue, double radius, Optional<LuaValue> requestedFilter) {
        return this.queryOperations.getNearby(centerValue, radius, requestedFilter);
    }

    @MLuaDescription("Returns the nearest entity in the same world within a spherical radius matching an EntityQuery filter, or nil when none matches.")
    public LuaValue GetNearest(LuaValue centerValue, double radius, Optional<LuaValue> requestedFilter) {
        return this.queryOperations.getNearest(centerValue, radius, requestedFilter);
    }

    @MLuaDescription("Loads the target chunk and creates an entity in the supplied world, dimension, or the overworld by default. CFrame positions also set yaw and pitch; an entity anchor copies its rotation.")
    public LuaValue Spawn(String typeId, LuaValue positionValue, Optional<LuaValue> requestedWorld) {
        this.requireServerSide(this.name() + ":Spawn");
        if (context.mlua().server() == null) throw new LuaError("[MLua] Server is not available for EntityService:Spawn.");
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
        EntityType<?> type = MLuaEntitySpawner.resolveType(level, context.mlua().ownerModId(), typeId);
        if (type == null) {
            throw new LuaError("[MLua] Unknown entity type: " + typeId);
        }
        MLuaCFrame frame = MLuaCFrame.isCFrame(positionValue) ? MLuaCFrame.fromLua(positionValue) : null;
        float yaw = anchor != null ? anchor.getYRot() : frame != null ? frame.minecraftYaw() : 0.0F;
        float pitch = anchor != null ? anchor.getXRot() : frame != null ? frame.minecraftPitch() : 0.0F;
        Entity entity = MLuaEntitySpawner.spawn(level, type, pos[0], pos[1], pos[2], yaw, pitch);
        if (entity == null) return LuaValue.NIL;
        return wrap(entity);
    }

    @MLuaDescription("Removes an entity from its world. Server-side only.")
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

    Entity resolveEntity(int id) {
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

    Entity resolveEntity(UUID uuid) {
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

    ServerLevel resolveServerLevel(LuaValue value) {
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
            ResourceLocation dimension = MLuaResourceLocations.tryParseOrNull(value.tojstring());
            if (dimension == null) throw new LuaError("[MLua] Invalid dimension identifier: " + value.tojstring());
            for (ServerLevel level : context.mlua().server().getAllLevels()) {
                if (level.dimension().location().equals(dimension)) return level;
            }
            throw new LuaError("[MLua] Dimension is not loaded: " + value.tojstring());
        }
        return context.mlua().server().overworld();
    }

    Level currentLevel() {
        if (context.mlua().side() == Side.CLIENT) {
            return clientLevel();
        }
        return context.mlua().server() == null ? null : context.mlua().server().overworld();
    }

    static Level clientLevel() {
        try {
            Object level = MLuaClientPlayerAccess.level();
            return level instanceof Level rawLevel ? rawLevel : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    LuaValue wrap(Entity entity) {
        if (entity == null) {
            return LuaValue.NIL;
        }
        return MLuaEntityWrapper.entityTable(entity, context.mlua().registration());
    }

    Entity entityValue(LuaValue value) {
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
