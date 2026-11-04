package dev.mlua.modules.particles.service;

import static dev.mlua.modules.particles.service.MLuaParticleArguments.*;

import dev.mlua.api.MLuaLuaErrors;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.service.MLuaService;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.modules.entity.MLuaEntityScopeResolver;
import dev.mlua.runtime.client.MLuaClientParticleManager;
import dev.mlua.runtime.client.MLuaParticleOwnershipRegistry;
import java.util.function.Function;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Builds owner-scoped Lua ParticleEmitter instances and their backed methods. */
final class MLuaParticleEmitterInstanceFactory {
    private final MLuaService.Context context;

    MLuaParticleEmitterInstanceFactory(MLuaService.Context context) {
        this.context = context;
    }

    LuaTable create(int id, MLuaParticleOwnershipRegistry.Owner owner) {
        LuaTable operations = new LuaTable();
        String ownerModId = owner.ownerModId();
        operations.set("GetId", fn("GetId", args -> LuaValue.valueOf(id)));
        operations.set("Equals", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue other = args.arg(2);
                if (!other.istable() || !context.mlua().registration().isInstanceOf(other, "ParticleEmitter")) {
                    return LuaValue.FALSE;
                }
                LuaTable otherHandle = other.checktable();
                return LuaValue.valueOf(id == otherHandle.rawget(MLuaParticleEmitterBinding.ID).optint(-1)
                    && ownerModId.equals(otherHandle.rawget("__mluaOwnerModId").optjstring("")));
            }
        });
        operations.set("GetHashCode", fn("GetHashCode", args -> LuaValue.valueOf(java.util.Objects.hash(ownerModId, id))));
        operations.set("ToString", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue self = args.arg1();
                String className = context.mlua().registration().classNameOf(self);
                return LuaValue.valueOf((className == null ? "ParticleEmitter" : className)
                    + "[" + ownerModId + ":" + id + "]");
            }
        });
        operations.set("GetIsDestroyed", fn("GetIsDestroyed", args -> LuaValue.valueOf(MLuaClientParticleManager.get(id, owner) == null)));
        operations.set("Destroy", fn("Destroy", args -> {
            MLuaClientParticleManager.remove(id, owner);
            return LuaValue.NONE;
        }));
        operations.set("GetPosition", fn("GetPosition", args -> {
            var emitter = requireEmitter(id, owner);
            return vector3(emitter.x(), emitter.y(), emitter.z());
        }));
        operations.set("SetPosition", fn("SetPosition", args -> {
            var emitter = requireEmitter(id, owner);
            double[] position = position(args.arg(2));
            emitter.setPosition(position[0], position[1], position[2]);
            return LuaValue.NONE;
        }));
        operations.set("GetRate", fn("GetRate", args -> LuaValue.valueOf(requireEmitter(id, owner).rate())));
        operations.set("SetRate", fn("SetRate", args -> {
            requireEmitter(id, owner).setRate(bounded(args.arg(2).checkdouble(), "ParticleEmitter:SetRate", "rate", 0.0, 1000.0));
            return LuaValue.NONE;
        }));
        operations.set("GetSpread", fn("GetSpread", args -> LuaValue.valueOf(requireEmitter(id, owner).spread())));
        operations.set("SetSpread", fn("SetSpread", args -> {
            requireEmitter(id, owner).setSpread(nonNegative(args.arg(2).checkdouble(), "ParticleEmitter:SetSpread", "spread"));
            return LuaValue.NONE;
        }));
        operations.set("GetSpreadVector", fn("GetSpreadVector", args -> {
            var emitter = requireEmitter(id, owner);
            return vector3(emitter.spreadX(), emitter.spreadY(), emitter.spreadZ());
        }));
        operations.set("SetSpreadVector", fn("SetSpreadVector", args -> {
            double[] spread = vector3(args.arg(2), "Spread");
            requireEmitter(id, owner).setSpread(nonNegative(spread[0], "ParticleEmitter:SetSpreadVector", "x"),
                nonNegative(spread[1], "ParticleEmitter:SetSpreadVector", "y"),
                nonNegative(spread[2], "ParticleEmitter:SetSpreadVector", "z"));
            return LuaValue.NONE;
        }));
        operations.set("GetSpeed", fn("GetSpeed", args -> LuaValue.valueOf(requireEmitter(id, owner).speed())));
        operations.set("SetSpeed", fn("SetSpeed", args -> {
            requireEmitter(id, owner).setSpeed(finite(args.arg(2).checkdouble(), "ParticleEmitter:SetSpeed", "speed"));
            return LuaValue.NONE;
        }));
        operations.set("GetVelocity", fn("GetVelocity", args -> {
            var emitter = requireEmitter(id, owner);
            return vector3(emitter.velocityX(), emitter.velocityY(), emitter.velocityZ());
        }));
        operations.set("SetVelocity", fn("SetVelocity", args -> {
            double[] velocity = vector3(args.arg(2), "Velocity");
            requireEmitter(id, owner).setVelocity(finite(velocity[0], "ParticleEmitter:SetVelocity", "x"),
                finite(velocity[1], "ParticleEmitter:SetVelocity", "y"),
                finite(velocity[2], "ParticleEmitter:SetVelocity", "z"));
            return LuaValue.NONE;
        }));
        operations.set("GetFollowEntity", fn("GetFollowEntity", args -> {
            MLuaEntityScope scope = requireEmitter(id, owner).followEntityScope();
            return scope != null ? LuaValue.valueOf(scope.entityId()) : LuaValue.NIL;
        }));
        operations.set("SetFollowEntity", fn("SetFollowEntity", args -> {
            requireEmitter(id, owner).setFollowEntity(resolveOptionalEntityScope(args.arg(2)));
            return LuaValue.NONE;
        }));
        operations.set("ClearFollowEntity", fn("ClearFollowEntity", args -> {
            requireEmitter(id, owner).setFollowEntity(null);
            return LuaValue.NONE;
        }));
        operations.set("GetOffset", fn("GetOffset", args -> {
            var emitter = requireEmitter(id, owner);
            return vector3(emitter.offsetX(), emitter.offsetY(), emitter.offsetZ());
        }));
        operations.set("SetOffset", fn("SetOffset", args -> {
            double[] offset = vector3(args.arg(2), "Offset");
            requireEmitter(id, owner).setOffset(offset[0], offset[1], offset[2]);
            return LuaValue.NONE;
        }));
        operations.set("GetEnabled", fn("GetEnabled", args -> LuaValue.valueOf(requireEmitter(id, owner).enabled())));
        operations.set("SetEnabled", fn("SetEnabled", args -> {
            requireEmitter(id, owner).setEnabled(args.checkboolean(2));
            return LuaValue.NONE;
        }));
        operations.set("SetParticle", fn("SetParticle", args -> {
            requireEmitter(id, owner).setParticleId(particleId(args.checkjstring(2)));
            return LuaValue.NONE;
        }));
        operations.set("SetSpec", fn("SetSpec", args -> {
            requireEmitter(id, owner).setSpec(specFromLua(args.arg(2)));
            return LuaValue.NONE;
        }));

        LuaTable instance = new LuaTable();
        instance.rawset(MLuaParticleEmitterBinding.ID, LuaValue.valueOf(id));
        instance.rawset("__mluaOwnerModId", LuaValue.valueOf(ownerModId));
        instance.rawset(MLuaParticleEmitterBinding.OPERATIONS, operations);
        LuaTable attached = this.context.mlua().registration().attachClassInstance(instance, "ParticleEmitter", null);
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs entry = operations.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            LuaValue operation = entry.arg(2);
            if (key.isstring() && operation.isfunction()) {
                MLuaClassMethods.setInstanceMethod(attached, key.checkjstring(), operation);
            }
        }
        return attached;
    }

    private MLuaClientParticleManager.Emitter requireEmitter(int id, MLuaParticleOwnershipRegistry.Owner owner) {
        MLuaClientParticleManager.Emitter emitter = MLuaClientParticleManager.get(id, owner);
        if (emitter == null) throw new LuaError("[MLua] Particle emitter no longer exists");
        return emitter;
    }

    private MLuaEntityScope resolveOptionalEntityScope(LuaValue value) {
        return MLuaEntityScopeResolver.resolveOptionalClientTarget(value,
            this.context.mlua().registration(), "FollowEntity must reference an entity in the current client world");
    }

    private static VarArgFunction fn(String operation, Function<Varargs, LuaValue> body) {
        return MLuaLuaErrors.operation("ParticleEmitter:" + operation, body, failure -> {
            MLuaLogger.LOGGER.error("ParticleEmitter:{} failed", operation, failure);
            return new LuaError("[MLua] ParticleEmitter:" + operation + " failed: "
                + failure.getClass().getSimpleName() + ": "
                + (failure.getMessage() == null ? "no details" : failure.getMessage()));
        });
    }
}
