package dev.mlua.modules.particles.service;

import static dev.mlua.modules.particles.service.MLuaParticleArguments.*;

import dev.mlua.common.enums.Side;
import dev.mlua.api.MLuaLuaErrors;
import dev.mlua.core.service.MLuaService;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.modules.entity.MLuaEntityScopeResolver;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.runtime.client.MLuaClientParticleManager;
import dev.mlua.runtime.client.MLuaParticleOwnershipRegistry;
import java.util.Optional;
import java.util.function.Function;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

@MLuaDescription("Client-side particles, typed particle specs, and persistent emitters.")
public final class MLuaParticleService extends MLuaReflectiveService {
    public static void registerEmitterClass(MLuaRegistration registration) {
        MLuaParticleEmitterBinding.register(registration);
    }

    public MLuaParticleService(MLuaService.Context context) {
        super(context);
    }

    @Override
    public String name() {
        return "ParticleService";
    }

    @Override
    public LuaTable toLua() {
        LuaTable table = super.toLua();
        table.set("__side", "Client");
        return table;
    }

    @MLuaDescription("Spawns one or more particles by registry identifier at a position.")
    public void SpawnLocal(String particleId, LuaValue position, Optional<Integer> requestedCount,
                           Optional<Double> requestedSpread, Optional<Double> requestedSpeed) {
        this.requireClientSide(this.name());
        double[] coordinates = position(position);
        MLuaClientParticleManager.spawnOnce(particleId(particleId), coordinates[0], coordinates[1], coordinates[2],
            particleCount(requestedCount, 1, "SpawnLocal"),
            nonNegative(requestedSpread.orElse(0.2), "SpawnLocal", "spread"),
            finite(requestedSpeed.orElse(0.0), "SpawnLocal", "speed"));
    }

    @MLuaDescription("Spawns particles from a typed particle specification.")
    public void Spawn(LuaValue specValue, LuaValue position, Optional<Integer> requestedCount,
                      Optional<Double> requestedSpread, Optional<Double> requestedSpeed) {
        this.requireClientSide(this.name());
        MLuaClientParticleManager.ParticleSpec spec = specFromLua(specValue);
        double[] coordinates = position(position);
        MLuaClientParticleManager.spawnOnce(spec, coordinates[0], coordinates[1], coordinates[2],
            particleCount(requestedCount, 1, "Spawn"),
            nonNegative(requestedSpread.orElse(0.2), "Spawn", "spread"),
            finite(requestedSpeed.orElse(0.0), "Spawn", "speed"));
    }

    @MLuaDescription("Spawns a burst of particles with spread and velocity options.")
    public void SpawnBurst(LuaValue specValue, LuaValue position, Optional<LuaValue> properties) {
        this.requireClientSide(this.name());
        MLuaClientParticleManager.ParticleSpec spec = specFromLua(specValue);
        LuaTable props = properties.orElse(LuaValue.NIL).opttable(new LuaTable());
        int count = MLuaLuaArguments.optionalWholeInt(firstNonNil(props.get("Count"), props.get("count")),
            12, "ParticleService:SpawnBurst", "Count");
        count = boundedCount(count, "SpawnBurst");
        LuaValue spread = firstNonNil(props.get("Spread"), props.get("spread"));
        double[] spreadVector = spread.isnumber()
            ? new double[]{spread.todouble(), spread.todouble(), spread.todouble()}
            : spread.isnil() ? new double[]{0.2, 0.2, 0.2} : vector3(spread, "Spread");
        double spreadX = optionalNonNegative(firstNonNil(props.get("SpreadX"), props.get("spreadX")), spreadVector[0], "SpawnBurst", "SpreadX");
        double spreadY = optionalNonNegative(firstNonNil(props.get("SpreadY"), props.get("spreadY")), spreadVector[1], "SpawnBurst", "SpreadY");
        double spreadZ = optionalNonNegative(firstNonNil(props.get("SpreadZ"), props.get("spreadZ")), spreadVector[2], "SpawnBurst", "SpreadZ");
        LuaValue velocity = firstNonNil(props.get("Velocity"), props.get("velocity"));
        double[] velocityVector = velocity.isnil() ? new double[]{0.0, 0.0, 0.0} : vector3(velocity, "Velocity");
        double[] coordinates = position(position);
        MLuaClientParticleManager.spawnBurst(spec, coordinates[0], coordinates[1], coordinates[2], count,
            spreadX, spreadY, spreadZ, finite(velocityVector[0], "SpawnBurst", "velocity.x"),
            finite(velocityVector[1], "SpawnBurst", "velocity.y"), finite(velocityVector[2], "SpawnBurst", "velocity.z"));
    }

    @MLuaDescription("Spawns particles in a ring around a position.")
    public void SpawnRing(LuaValue specValue, LuaValue position, Optional<Integer> requestedCount,
                          Optional<Double> requestedRadius, Optional<Double> requestedSpeed) {
        this.requireClientSide(this.name());
        MLuaClientParticleManager.ParticleSpec spec = specFromLua(specValue);
        double[] coordinates = position(position);
        MLuaClientParticleManager.spawnRing(spec, coordinates[0], coordinates[1], coordinates[2],
            particleCount(requestedCount, 16, "SpawnRing"),
            nonNegative(requestedRadius.orElse(1.0), "SpawnRing", "radius"),
            finite(requestedSpeed.orElse(0.05), "SpawnRing", "speed"));
    }

    @MLuaDescription("Spawns particles in a cone along a direction.")
    public void SpawnCone(LuaValue specValue, LuaValue position, LuaValue direction,
                          Optional<Integer> requestedCount, Optional<Double> requestedAngle,
                          Optional<Double> requestedSpeed) {
        this.requireClientSide(this.name());
        MLuaClientParticleManager.ParticleSpec spec = specFromLua(specValue);
        double[] coordinates = position(position);
        double[] directionVector = vector3(direction, "Direction");
        MLuaClientParticleManager.spawnCone(spec, coordinates[0], coordinates[1], coordinates[2],
            directionVector[0], directionVector[1], directionVector[2], particleCount(requestedCount, 10, "SpawnCone"),
            bounded(requestedAngle.orElse(15.0), "SpawnCone", "angle", 0.0, 180.0),
            finite(requestedSpeed.orElse(0.1), "SpawnCone", "speed"));
    }

    @MLuaDescription("Creates a persistent emitter using a particle identifier. The client runtime allows "
        + MLuaClientParticleManager.MAX_EMITTERS + " active emitters total and "
        + MLuaClientParticleManager.MAX_EMITTERS_PER_OWNER + " per addon runtime.")
    public LuaTable CreateEmitter(String particleId, LuaValue position, Optional<Double> requestedRate,
                                  Optional<Double> requestedSpread, Optional<Double> requestedSpeed) {
        this.requireClientSide(this.name());
        double[] coordinates = position(position);
        int id = MLuaClientParticleManager.createEmitter(particleId(particleId), coordinates[0], coordinates[1],
            coordinates[2], bounded(requestedRate.orElse(8.0), "CreateEmitter", "rate", 0.0, 1000.0),
            nonNegative(requestedSpread.orElse(0.2), "CreateEmitter", "spread"),
            finite(requestedSpeed.orElse(0.0), "CreateEmitter", "speed"), owner());
        if (id < 0) throw emitterCapacityError();
        return emitterTable(id, owner());
    }

    @MLuaDescription("Creates a persistent emitter from a typed particle specification. The client runtime allows "
        + MLuaClientParticleManager.MAX_EMITTERS + " active emitters total and "
        + MLuaClientParticleManager.MAX_EMITTERS_PER_OWNER + " per addon runtime.")
    public LuaTable CreateTypedEmitter(LuaValue specValue, LuaValue position, Optional<Double> requestedRate,
                                       Optional<Double> requestedSpread, Optional<Double> requestedSpeed) {
        this.requireClientSide(this.name());
        MLuaClientParticleManager.ParticleSpec spec = specFromLua(specValue);
        double[] coordinates = position(position);
        int id = MLuaClientParticleManager.createEmitter(spec, coordinates[0], coordinates[1],
            coordinates[2], bounded(requestedRate.orElse(8.0), "CreateTypedEmitter", "rate", 0.0, 1000.0),
            nonNegative(requestedSpread.orElse(0.2), "CreateTypedEmitter", "spread"),
            finite(requestedSpeed.orElse(0.0), "CreateTypedEmitter", "speed"), owner());
        if (id < 0) throw emitterCapacityError();
        return emitterTable(id, owner());
    }

    @MLuaDescription("Removes particle emitters owned by the calling mod.")
    public void Clear() {
        this.requireClientSide(this.name());
        MLuaClientParticleManager.clearOwned(owner());
    }

    @MLuaDescription("Returns the calling addon runtime's live ParticleEmitter instances in stable id order.")
    public LuaTable GetAll() {
        this.requireClientSide(this.name());
        MLuaParticleOwnershipRegistry.Owner owner = owner();
        LuaTable result = new LuaTable();
        int index = 1;
        for (int id : MLuaClientParticleManager.ownedEmitterIds(owner)) {
            result.set(index++, emitterTable(id, owner));
        }
        return result;
    }

    @MLuaDescription("Returns the number of live emitters owned by the calling addon runtime.")
    public int GetCount() {
        this.requireClientSide(this.name());
        return MLuaClientParticleManager.ownedEmitterIds(owner()).size();
    }

    private LuaTable emitterTable(int id, MLuaParticleOwnershipRegistry.Owner owner) {
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
            var emitter = requireEmitter(id);
            return vector3(emitter.x(), emitter.y(), emitter.z());
        }));
        operations.set("SetPosition", fn("SetPosition", args -> {
            var emitter = requireEmitter(id);
            double[] position = position(args.arg(2));
            emitter.setPosition(position[0], position[1], position[2]);
            return LuaValue.NONE;
        }));
        operations.set("GetRate", fn("GetRate", args -> LuaValue.valueOf(requireEmitter(id).rate())));
        operations.set("SetRate", fn("SetRate", args -> {
            requireEmitter(id).setRate(bounded(args.arg(2).checkdouble(), "ParticleEmitter:SetRate", "rate", 0.0, 1000.0));
            return LuaValue.NONE;
        }));
        operations.set("GetSpread", fn("GetSpread", args -> LuaValue.valueOf(requireEmitter(id).spread())));
        operations.set("SetSpread", fn("SetSpread", args -> {
            requireEmitter(id).setSpread(nonNegative(args.arg(2).checkdouble(), "ParticleEmitter:SetSpread", "spread"));
            return LuaValue.NONE;
        }));
        operations.set("GetSpreadVector", fn("GetSpreadVector", args -> {
            var emitter = requireEmitter(id);
            return vector3(emitter.spreadX(), emitter.spreadY(), emitter.spreadZ());
        }));
        operations.set("SetSpreadVector", fn("SetSpreadVector", args -> {
            double[] spread = vector3(args.arg(2), "Spread");
            requireEmitter(id).setSpread(nonNegative(spread[0], "ParticleEmitter:SetSpreadVector", "x"),
                nonNegative(spread[1], "ParticleEmitter:SetSpreadVector", "y"),
                nonNegative(spread[2], "ParticleEmitter:SetSpreadVector", "z"));
            return LuaValue.NONE;
        }));
        operations.set("GetSpeed", fn("GetSpeed", args -> LuaValue.valueOf(requireEmitter(id).speed())));
        operations.set("SetSpeed", fn("SetSpeed", args -> {
            requireEmitter(id).setSpeed(finite(args.arg(2).checkdouble(), "ParticleEmitter:SetSpeed", "speed"));
            return LuaValue.NONE;
        }));
        operations.set("GetVelocity", fn("GetVelocity", args -> {
            var emitter = requireEmitter(id);
            return vector3(emitter.velocityX(), emitter.velocityY(), emitter.velocityZ());
        }));
        operations.set("SetVelocity", fn("SetVelocity", args -> {
            double[] velocity = vector3(args.arg(2), "Velocity");
            requireEmitter(id).setVelocity(finite(velocity[0], "ParticleEmitter:SetVelocity", "x"),
                finite(velocity[1], "ParticleEmitter:SetVelocity", "y"),
                finite(velocity[2], "ParticleEmitter:SetVelocity", "z"));
            return LuaValue.NONE;
        }));
        operations.set("GetFollowEntity", fn("GetFollowEntity", args -> {
            MLuaEntityScope scope = requireEmitter(id).followEntityScope();
            return scope != null ? LuaValue.valueOf(scope.entityId()) : LuaValue.NIL;
        }));
        operations.set("SetFollowEntity", fn("SetFollowEntity", args -> {
            requireEmitter(id).setFollowEntity(resolveOptionalEntityScope(args.arg(2)));
            return LuaValue.NONE;
        }));
        operations.set("ClearFollowEntity", fn("ClearFollowEntity", args -> {
            requireEmitter(id).setFollowEntity(null);
            return LuaValue.NONE;
        }));
        operations.set("GetOffset", fn("GetOffset", args -> {
            var emitter = requireEmitter(id);
            return vector3(emitter.offsetX(), emitter.offsetY(), emitter.offsetZ());
        }));
        operations.set("SetOffset", fn("SetOffset", args -> {
            double[] offset = vector3(args.arg(2), "Offset");
            requireEmitter(id).setOffset(offset[0], offset[1], offset[2]);
            return LuaValue.NONE;
        }));
        operations.set("GetEnabled", fn("GetEnabled", args -> LuaValue.valueOf(requireEmitter(id).enabled())));
        operations.set("SetEnabled", fn("SetEnabled", args -> {
            requireEmitter(id).setEnabled(args.checkboolean(2));
            return LuaValue.NONE;
        }));
        operations.set("SetParticle", fn("SetParticle", args -> {
            requireEmitter(id).setParticleId(particleId(args.checkjstring(2)));
            return LuaValue.NONE;
        }));
        operations.set("SetSpec", fn("SetSpec", args -> {
            requireEmitter(id).setSpec(specFromLua(args.arg(2)));
            return LuaValue.NONE;
        }));
        LuaTable instance = new LuaTable();
        instance.rawset(MLuaParticleEmitterBinding.ID, LuaValue.valueOf(id));
        instance.rawset("__mluaOwnerModId", LuaValue.valueOf(ownerModId));
        instance.rawset(MLuaParticleEmitterBinding.OPERATIONS, operations);
        LuaTable attached = context.mlua().registration().attachClassInstance(instance, "ParticleEmitter", null);
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

    private MLuaClientParticleManager.Emitter requireEmitter(int id) {
        var emitter = MLuaClientParticleManager.get(id, owner());
        if (emitter == null) {
            throw new LuaError("[MLua] Particle emitter no longer exists");
        }
        return emitter;
    }

    private static LuaError emitterCapacityError() {
        return new LuaError("[MLua] ParticleService emitter capacity reached ("
            + MLuaClientParticleManager.MAX_EMITTERS_PER_OWNER + " per addon runtime, "
            + MLuaClientParticleManager.MAX_EMITTERS + " total). Destroy unused emitters or clear the service.");
    }

    private MLuaParticleOwnershipRegistry.Owner owner() {
        Object runtimeOwner = context.mlua().registration() instanceof MLuaRuntime runtime ? runtime.session() : null;
        return new MLuaParticleOwnershipRegistry.Owner(runtimeOwner, context.mlua().ownerModId());
    }

    private MLuaEntityScope resolveOptionalEntityScope(LuaValue value) {
        return MLuaEntityScopeResolver.resolveOptionalClientTarget(value,
            context.mlua().registration(), "FollowEntity must reference an entity in the current client world");
    }

    private MLuaEntityScope resolveEntityScope(LuaValue value) {
        return MLuaEntityScopeResolver.resolveClientTarget(value,
            context.mlua().registration(), "FollowEntity must reference an entity in the current client world");
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
