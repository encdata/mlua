package dev.mlua.modules.entity;

import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Resolves the entity references shared by client services into stable world scopes. */
public final class MLuaEntityScopeResolver {
    private MLuaEntityScopeResolver() { }

    public static MLuaEntityScope resolveClientTarget(LuaValue value, MLuaRegistration registration,
                                                       String missingEntityMessage) {
        if (value.isint()) {
            int entityId = value.toint();
            Level level = MLuaMinecraftClient.instance().level;
            Entity entity = level == null ? null : level.getEntity(entityId);
            if (entity == null) throw new LuaError("[MLua] " + missingEntityMessage);
            return MLuaEntityScope.of(entity);
        }
        if (value.istable()) {
            Entity entity = MLuaEntityWrapper.fromLuaInstance(value, registration);
            if (entity != null) return MLuaEntityScope.of(entity);
        }
        throw new LuaError("[MLua] Expected an Entity instance or entity ID");
    }

    public static MLuaEntityScope resolveOptionalClientTarget(LuaValue value, MLuaRegistration registration,
                                                               String missingEntityMessage) {
        return value.isnil() ? null : resolveClientTarget(value, registration, missingEntityMessage);
    }
}
