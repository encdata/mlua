package dev.mlua.modules.components.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.runtime.entity.MLuaEntityComponentManager;
import java.util.List;
import net.minecraft.world.entity.Entity;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Entity component operations exposed through the shared reflective Java service base. */
public final class MLuaComponentsService extends MLuaReflectiveService {
    public MLuaComponentsService(Context context) {
        super(context);
    }

    @Override
    public String name() {
        return "Components";
    }

    @Override
    public LuaTable toLua() {
        LuaTable table = super.toLua();
        table.set("__side", "Both");
        return table;
    }

    public LuaTable Attach(LuaValue entityValue, String name, LuaTable spec) {
        Entity entity = requireEntity(entityValue);
        return MLuaEntityComponentManager.attach(entity, this.context.side(), name, spec,
            this.context.ownerModId(), MLuaLuaBindings.boundContext(this.context.mlua().globals()));
    }

    public LuaTable Get(LuaValue entityValue, String name) {
        Entity entity = requireEntity(entityValue);
        return MLuaEntityComponentManager.get(entity, this.context.side(), name,
            this.context.ownerModId(), this.context.mlua().session());
    }

    public List<String> List(LuaValue entityValue) {
        Entity entity = requireEntity(entityValue);
        return MLuaEntityComponentManager.list(entity, this.context.side(),
            this.context.ownerModId(), this.context.mlua().session());
    }

    public void Remove(LuaValue entityValue, String name) {
        Entity entity = requireEntity(entityValue);
        MLuaEntityComponentManager.remove(entity, this.context.side(), name,
            this.context.ownerModId(), this.context.mlua().session());
    }

    public void Clear(LuaValue entityValue) {
        Entity entity = requireEntity(entityValue);
        MLuaEntityComponentManager.clear(entity, this.context.side(),
            this.context.ownerModId(), this.context.mlua().session());
    }

    private Entity requireEntity(LuaValue value) {
        Entity entity = MLuaEntityComponentManager.resolveEntity(value, this.context.side(), this.context.server());
        if (entity == null) {
            throw new LuaError("[MLua] Entity could not be resolved on "
                + this.context.side().name().toLowerCase(java.util.Locale.ROOT));
        }
        return entity;
    }
}
