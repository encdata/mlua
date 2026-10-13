package dev.mlua.modules.animation.Service;

import dev.mlua.common.enums.AnimationPriority;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaAnimation;
import dev.mlua.minecraft.animation.MLuaBonePose;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.common.enums.Side;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.minecraft.entity.MLuaEntityLookup;
import dev.mlua.minecraft.binding.MLuaAnimationBindings;
import dev.mlua.modules.enums.MLuaEnumValue;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaAnimationService extends MLuaServiceBase {
    private final Map<String, MLuaAnimation> registeredAnimations = new ConcurrentHashMap<>();

    public MLuaAnimationService(MLuaService.Context context) {
        super(context);
    }

    @Override
    public String name() { return "Animation"; }

    @Override
    public LuaTable toLua() {
        LuaTable table = new LuaTable();

        table.set("Create", fn((args) -> {
            LuaTable keyframes = args.arg(2).checktable();
            MLuaAnimation anim = MLuaAnimation.fromLuaTable(keyframes);
            return MLuaAnimationBindings.wrapAnimation(anim, MLuaAnimationService.this.context.mlua().registration());
        }));

        table.set("RegisterAnimation", fn((args) -> {
            String id = args.arg(2).checkjstring();
            LuaTable animTable = args.arg(3).checktable();
            LuaValue animObj = animTable.get("__class");
            if (animObj.isnil() || !"Animation".equals(animObj.tojstring())) {
                LuaTable keyframes = animTable;
                MLuaAnimation anim = MLuaAnimation.fromLuaTable(keyframes);
                registeredAnimations.put(id, anim);
            } else {
                LuaValue lengthVal = animTable.get("Length");
                if (lengthVal.isnumber()) {
                    LuaValue getKeyframes = animTable.get("GetKeyframes");
                    if (getKeyframes.isfunction()) {
                        LuaValue kfResult = getKeyframes.call();
                        if (kfResult.istable()) {
                            MLuaAnimation anim = MLuaAnimation.fromLuaTable(kfResult.checktable());
                            registeredAnimations.put(id, anim);
                        }
                    }
                }
            }
            return LuaValue.NONE;
        }));

        table.set("GetAnimation", fn((args) -> {
            String id = args.arg(2).checkjstring();
            MLuaAnimation anim = registeredAnimations.get(id);
            if (anim == null) return LuaValue.NIL;
            return MLuaAnimationBindings.wrapAnimation(anim, MLuaAnimationService.this.context.mlua().registration());
        }));

        table.set("GetRegisteredAnimations", fn((args) -> {
            LuaTable result = new LuaTable();
            for (String id : registeredAnimations.keySet()) {
                result.set(id, MLuaAnimationBindings.wrapAnimation(registeredAnimations.get(id), MLuaAnimationService.this.context.mlua().registration()));
            }
            return result;
        }));

        table.set("GetPose", fn((args) -> {
            LuaValue entityVal = args.arg(2);
            MLuaEntityScope scope = resolveEntityScope(entityVal);
            Map<String, MLuaCFrame> pose = MLuaBonePose.getPose(scope, MLuaAnimationService.this.context.mlua().registration());
            LuaTable poseTable = new LuaTable();
            for (Map.Entry<String, MLuaCFrame> entry : pose.entrySet()) {
                poseTable.set(entry.getKey(), entry.getValue().toLua());
            }
            return poseTable;
        }));

        table.set("SetPose", fn((args) -> {
            LuaValue entityVal = args.arg(2);
            String bone = args.arg(3).checkjstring();
            MLuaCFrame cframe = MLuaCFrame.fromLua(args.arg(4));
            MLuaEntityScope scope = resolveEntityScope(entityVal);
            MLuaBonePose.setPose(scope, bone, cframe, MLuaAnimationService.this.context.mlua().registration());
            return LuaValue.NONE;
        }));

        table.set("ClearPose", fn((args) -> {
            LuaValue entityVal = args.arg(2);
            if (entityVal.isnil()) return LuaValue.NONE;
            MLuaEntityScope scope = resolveEntityScope(entityVal);
            MLuaBonePose.clearPose(scope, MLuaAnimationService.this.context.mlua().registration());
            return LuaValue.NONE;
        }));

        table.set("GetAnimationPriority", fn((args) -> {
            return MLuaEnumValue.enumConstants(AnimationPriority.class, null);
        }));

        return table;
    }

    private MLuaEntityScope resolveEntityScope(LuaValue value) {
        if (value.istable()) {
            LuaTable table = value.checktable();
            LuaValue entityObj = table.get("__entity");
            if (entityObj.isuserdata(net.minecraft.world.entity.Entity.class)) {
                return MLuaEntityScope.of((net.minecraft.world.entity.Entity) entityObj.checkuserdata(net.minecraft.world.entity.Entity.class));
            }
            LuaValue idVal = table.get("Id");
            if (idVal.isint()) return this.resolveEntityScope(idVal.toint());
        }
        if (value.isint()) return this.resolveEntityScope(value.toint());
        throw new LuaError("[MLua] Expected Entity or entity ID");
    }

    private MLuaEntityScope resolveEntityScope(int entityId) {
        if (this.context.mlua().side() == Side.CLIENT) {
            net.minecraft.world.level.Level level = MLuaMinecraftClient.instance().level;
            net.minecraft.world.entity.Entity entity = level == null ? null : level.getEntity(entityId);
            return entity == null ? MLuaEntityScope.legacy(entityId) : MLuaEntityScope.of(entity);
        }
        if (this.context.mlua().server() != null) {
            MLuaEntityLookup.LookupResult<net.minecraft.world.entity.Entity> lookup =
                    MLuaEntityLookup.findById(this.context.mlua().server(), entityId);
            if (lookup.ambiguous()) {
                throw new LuaError("[MLua] Entity id " + entityId + " exists in multiple dimensions; pass an entity object");
            }
            if (lookup.found()) return MLuaEntityScope.of(lookup.value());
        }
        return MLuaEntityScope.legacy(entityId);
    }

    private static VarArgFunction fn(final Function<Varargs, LuaValue> body) {
        return new VarArgFunction() {
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
