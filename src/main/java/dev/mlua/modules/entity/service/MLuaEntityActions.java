package dev.mlua.modules.entity.service;

import dev.mlua.core.service.MLuaService;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.modules.entity.MLuaEntitySpawner;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Owns EntityService mutations while the annotated service methods remain the Lua API surface. */
final class MLuaEntityActions {
    private final MLuaService.Context context;
    private final MLuaEntityService service;

    MLuaEntityActions(MLuaService.Context context, MLuaEntityService service) {
        this.context = context;
        this.service = service;
    }

    LuaValue spawn(String typeId, LuaValue positionValue, Optional<LuaValue> requestedWorld) {
        if (context.mlua().server() == null) {
            throw new LuaError("[MLua] Server is not available for EntityService:Spawn.");
        }
        Entity anchor = service.entityValue(positionValue);
        double[] position = service.position(positionValue);
        LuaValue worldValue = requestedWorld.orElse(LuaValue.NIL);
        ServerLevel level;
        if (worldValue.isnil() && anchor != null) {
            if (!(anchor.level() instanceof ServerLevel anchorLevel)) {
                throw new LuaError("[MLua] EntityService:Spawn requires a server-side entity anchor.");
            }
            level = anchorLevel;
        } else {
            level = service.resolveServerLevel(worldValue);
        }
        EntityType<?> type = MLuaEntitySpawner.resolveType(level, context.mlua().ownerModId(), typeId);
        if (type == null) {
            throw new LuaError("[MLua] Unknown entity type: " + typeId);
        }
        MLuaCFrame frame = MLuaCFrame.isCFrame(positionValue) ? MLuaCFrame.fromLua(positionValue) : null;
        float yaw = anchor != null ? anchor.getYRot() : frame != null ? frame.minecraftYaw() : 0.0F;
        float pitch = anchor != null ? anchor.getXRot() : frame != null ? frame.minecraftPitch() : 0.0F;
        Entity entity = MLuaEntitySpawner.spawn(level, type, position[0], position[1], position[2], yaw, pitch);
        return entity == null ? LuaValue.NIL : service.wrap(entity);
    }

    void remove(LuaValue value) {
        invokeEntityMethod(service.requireEntity(value), "Remove");
    }

    void kill(LuaValue value) {
        invokeEntityMethod(service.requireEntity(value), "Kill");
    }

    void teleport(LuaValue entityValue, LuaValue positionValue) {
        invokeEntityMethod(service.requireEntity(entityValue), "Teleport", positionValue);
    }

    void setVelocity(LuaValue entityValue, LuaValue vectorValue) {
        invokeEntityMethod(service.requireEntity(entityValue), "SetVelocity", vectorValue);
    }

    boolean damage(LuaValue entityValue, double amount, Optional<String> damageType, Optional<LuaValue> attacker) {
        Entity entity = service.requireEntity(entityValue);
        LuaTable instance = (LuaTable) service.wrap(entity);
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
        LuaTable instance = (LuaTable) service.wrap(entity);
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
}
