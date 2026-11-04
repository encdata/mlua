package dev.mlua.modules.particles.service;

import static dev.mlua.modules.particles.service.MLuaParticleArguments.*;

import dev.mlua.core.service.MLuaService;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaGameThreadReflectiveService;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.runtime.client.MLuaClientParticleManager;
import dev.mlua.runtime.client.MLuaParticleOwnershipRegistry;
import java.util.Optional;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@MLuaDescription("Client-side particles, typed particle specs, and persistent emitters.")
public final class MLuaParticleService extends MLuaGameThreadReflectiveService {
    private final MLuaParticleEmitterInstanceFactory emitterInstances;
    public static void registerEmitterClass(MLuaRegistration registration) {
        MLuaParticleEmitterBinding.register(registration);
    }

    public MLuaParticleService(MLuaService.Context context) {
        super(context);
        this.emitterInstances = new MLuaParticleEmitterInstanceFactory(context);
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
        LuaTable props = MLuaLuaArguments.tableOrEmpty(properties.orElse(LuaValue.NIL));
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
        return this.emitterInstances.create(id, owner);
    }

    private MLuaParticleOwnershipRegistry.Owner owner() {
        Object runtimeOwner = context.mlua().registration() instanceof MLuaRuntime runtime ? runtime.session() : null;
        return new MLuaParticleOwnershipRegistry.Owner(runtimeOwner, context.mlua().ownerModId());
    }

    private static LuaError emitterCapacityError() {
        return new LuaError("[MLua] ParticleService emitter capacity reached ("
            + MLuaClientParticleManager.MAX_EMITTERS_PER_OWNER + " per addon runtime, "
            + MLuaClientParticleManager.MAX_EMITTERS + " total). Destroy unused emitters or clear the service.");
    }

}
