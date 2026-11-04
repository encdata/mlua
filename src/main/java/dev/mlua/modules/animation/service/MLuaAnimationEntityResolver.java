package dev.mlua.modules.animation.service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Resolves Lua entity handles into live client-world animation targets. */
final class MLuaAnimationEntityResolver {
    private final MLuaService.Context context;

    MLuaAnimationEntityResolver(MLuaService.Context context) {
        this.context = context;
    }

    MLuaEntityScope resolve(LuaValue value) {
        if (value.istable()) {
            if (!this.context.mlua().registration().isInstanceOf(value, "Entity")) {
                throw new LuaError("[MLua] Expected an Entity instance or entity ID");
            }
            LuaTable table = value.checktable();
            LuaValue entityValue = table.get("__entity");
            if (entityValue.isuserdata(Entity.class)) {
                Entity entity = (Entity) entityValue.checkuserdata(Entity.class);
                if (!entity.level().isClientSide()) {
                    throw new LuaError("[MLua] Animation service requires an entity from the client world.");
                }
                return MLuaEntityScope.of(entity);
            }
        }
        if (value.isint()) return this.resolveEntityId(value.toint());
        throw new LuaError("[MLua] Expected Entity or entity ID");
    }

    private MLuaEntityScope resolveEntityId(int entityId) {
        if (this.context.mlua().side() != Side.CLIENT) {
            throw new LuaError("[MLua] Animation service is available in client scripts only.");
        }
        Level level = MLuaMinecraftClient.instance().level;
        Entity entity = level == null ? null : level.getEntity(entityId);
        if (entity == null) {
            throw new LuaError("[MLua] Entity id " + entityId
                + " is not loaded in the client world; pass a live Entity instance.");
        }
        return MLuaEntityScope.of(entity);
    }
}
