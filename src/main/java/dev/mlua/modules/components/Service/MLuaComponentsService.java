package dev.mlua.modules.components.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.runtime.entity.MLuaEntityComponentManager;
import java.util.List;
import net.minecraft.world.entity.Entity;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@MLuaDescription("Attach reusable Lua behavior components to entities with tick and event callbacks.")
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

    @MLuaDescription("Attaches a named Lua behavior component to an entity.")
    public LuaTable Attach(LuaValue entityValue, String name, LuaTable spec) {
        Entity entity = requireEntity(entityValue);
        return MLuaEntityComponentManager.attach(entity, this.context.side(), name, spec,
            this.context.ownerModId(), MLuaLuaBindings.boundContext(this.context.mlua().globals()));
    }

    @MLuaDescription("Returns one of the calling mod's components attached to an entity.")
    public LuaTable Get(LuaValue entityValue, String name) {
        Entity entity = requireEntity(entityValue);
        return MLuaEntityComponentManager.get(entity, this.context.side(), name,
            this.context.ownerModId(), this.context.mlua().session());
    }

    @MLuaDescription("Returns component names attached by the calling mod to an entity.")
    public List<String> List(LuaValue entityValue) {
        Entity entity = requireEntity(entityValue);
        return MLuaEntityComponentManager.list(entity, this.context.side(),
            this.context.ownerModId(), this.context.mlua().session());
    }

    @MLuaDescription("Removes one of the calling mod's components from an entity.")
    public void Remove(LuaValue entityValue, String name) {
        Entity entity = requireEntity(entityValue);
        MLuaEntityComponentManager.remove(entity, this.context.side(), name,
            this.context.ownerModId(), this.context.mlua().session());
    }

    @MLuaDescription("Removes all components attached by the calling mod to an entity.")
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
