package dev.mlua.modules.model.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.runtime.client.MLuaClientModelManager;
import dev.mlua.core.MLuaRuntime;
import java.util.Map;
import java.util.function.Function;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaModelService extends MLuaServiceBase {
    public MLuaModelService(MLuaService.Context context) {
        super(context);
    }

    @Override
    public String name() {
        return "ModelService";
    }

    @Override
    public LuaTable toLua() {
        LuaTable table = new LuaTable();
        table.set("__side", "Client");
        table.set("ResolvePartName", fn(args -> LuaValue.valueOf(MLuaClientModelManager.normalizePartName(args.checkjstring(2)))));
        table.set("SetPartCFrame", fn(args -> {
            ensureClient();
            MLuaEntityScope scope = resolveEntityScope(args.arg(2));
            MLuaClientModelManager.setCFrame(scope, args.checkjstring(3), MLuaCFrame.fromLua(args.arg(4)), owner());
            return LuaValue.NONE;
        }));
        table.set("SetPartOffset", fn(args -> {
            ensureClient();
            MLuaEntityScope scope = resolveEntityScope(args.arg(2));
            MLuaClientModelManager.setOffset(scope, args.checkjstring(3), args.checktable(4), owner());
            return LuaValue.NONE;
        }));
        table.set("SetPartScale", fn(args -> {
            ensureClient();
            MLuaEntityScope scope = resolveEntityScope(args.arg(2));
            MLuaClientModelManager.setScale(scope, args.checkjstring(3), args.checktable(4), owner());
            return LuaValue.NONE;
        }));
        table.set("SetPartVisible", fn(args -> {
            ensureClient();
            MLuaEntityScope scope = resolveEntityScope(args.arg(2));
            MLuaClientModelManager.setVisible(scope, args.checkjstring(3), args.checkboolean(4), owner());
            return LuaValue.NONE;
        }));
        table.set("SetPart", fn(args -> {
            ensureClient();
            MLuaEntityScope scope = resolveEntityScope(args.arg(2));
            String part = args.checkjstring(3);
            LuaTable props = args.checktable(4);
            if (!props.get("CFrame").isnil()) {
                MLuaClientModelManager.setCFrame(scope, part, MLuaCFrame.fromLua(props.get("CFrame")), owner());
            }
            if (!props.get("Offset").isnil()) {
                MLuaClientModelManager.setOffset(scope, part, props.get("Offset").checktable(), owner());
            }
            if (!props.get("Scale").isnil()) {
                MLuaClientModelManager.setScale(scope, part, props.get("Scale").checktable(), owner());
            }
            if (!props.get("Visible").isnil()) {
                MLuaClientModelManager.setVisible(scope, part, props.get("Visible").checkboolean(), owner());
            }
            return LuaValue.NONE;
        }));
        table.set("ClearPart", fn(args -> {
            ensureClient();
            MLuaClientModelManager.clearPart(resolveEntityScope(args.arg(2)), args.checkjstring(3), owner());
            return LuaValue.NONE;
        }));
        table.set("ClearModel", fn(args -> {
            ensureClient();
            MLuaClientModelManager.clearEntity(resolveEntityScope(args.arg(2)), owner());
            return LuaValue.NONE;
        }));
        table.set("ClearAll", fn(args -> {
            ensureClient();
            MLuaClientModelManager.clearOwned(owner());
            return LuaValue.NONE;
        }));
        table.set("Clear", table.get("ClearAll"));
        table.set("GetModelState", fn(args -> {
            ensureClient();
            LuaTable out = new LuaTable();
            MLuaEntityScope scope = resolveEntityScope(args.arg(2));
            for (Map.Entry<String, MLuaClientModelManager.PartState> entry : MLuaClientModelManager.getState(scope, owner()).entrySet()) {
                LuaTable part = new LuaTable();
                if (entry.getValue().cframe() != null) {
                    part.set("CFrame", entry.getValue().cframe().toLua());
                }
                if (entry.getValue().offset() != null) {
                    part.set("Offset", entry.getValue().offset());
                }
                if (entry.getValue().scale() != null) {
                    part.set("Scale", entry.getValue().scale());
                }
                if (entry.getValue().visible() != null) {
                    part.set("Visible", LuaValue.valueOf(entry.getValue().visible()));
                }
                out.set(entry.getKey(), part);
            }
            return out;
        }));
        table.set("GetPartNames", fn(args -> {
            ensureClient();
            LuaTable out = new LuaTable();
            int i = 1;
            for (String partName : MLuaClientModelManager.getPartNames(resolveEntityScope(args.arg(2)), owner())) {
                out.set(i++, partName);
            }
            return out;
        }));
        return table;
    }

    private void ensureClient() {
        if (context.mlua().side() != Side.CLIENT) {
            throw new LuaError("[MLua] ModelService is client-side only.");
        }
    }

    private MLuaClientModelManager.Owner owner() {
        Object runtimeOwner = context.mlua().registration() instanceof MLuaRuntime runtime ? runtime.session() : null;
        return new MLuaClientModelManager.Owner(runtimeOwner, context.mlua().ownerModId());
    }

    private MLuaEntityScope resolveEntityScope(LuaValue value) {
        if (value.isint()) {
            return this.resolveEntityScope(value.toint());
        }
        if (value.istable()) {
            LuaValue entity = value.get("__entity");
            if (entity.isuserdata(net.minecraft.world.entity.Entity.class)) {
                return MLuaEntityScope.of((net.minecraft.world.entity.Entity) entity.checkuserdata(net.minecraft.world.entity.Entity.class));
            }
            LuaValue id = value.get("Id");
            if (id.isint()) {
                return this.resolveEntityScope(id.toint());
            }
        }
        throw new LuaError("[MLua] Expected entity table or entity id");
    }

    private MLuaEntityScope resolveEntityScope(int entityId) {
        net.minecraft.world.level.Level level = MLuaMinecraftClient.instance().level;
        net.minecraft.world.entity.Entity entity = level == null ? null : level.getEntity(entityId);
        if (entity == null) throw new LuaError("[MLua] Model target must exist in the current client world");
        return MLuaEntityScope.of(entity);
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
                    throw new LuaError("[MLua] " + e.getMessage());
                }
            }
        };
    }
}
