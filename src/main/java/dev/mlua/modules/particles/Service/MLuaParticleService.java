package dev.mlua.modules.particles.Service;

import dev.mlua.math.MLuaVector;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.runtime.client.MLuaClientParticleManager;
import dev.mlua.runtime.client.MLuaParticleOwnershipRegistry;
import java.util.Optional;
import java.util.function.Function;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

@MLuaDescription("Client-side particles, typed particle specs, and persistent emitters.")
public final class MLuaParticleService extends MLuaReflectiveService {
    private static final String EMITTER_OPERATIONS = "__particle_emitter_operations";
    private static final String EMITTER_ID = "__particle_emitter_id";
    public static void registerEmitterClass(MLuaRegistration registration) {
        registration.registerClass("ParticleEmitter", registration.requireInstanceBaseClassName(),
            "A live client-side particle emitter with mutable position, rate, spread, velocity, and particle settings.");
        registerEmitterProperty(registration, "Id", "number", "The emitter's runtime identifier.", "", null);
        registerEmitterProperty(registration, "IsDestroyed", "boolean", "Whether the emitter has been removed.", "GetIsDestroyed", null);
        registerEmitterProperty(registration, "Position", "Vector3|CFrame", "The emitter's current position.", "GetPosition", "SetPosition");
        registerEmitterProperty(registration, "Rate", "number", "The emitter's particle rate.", "GetRate", "SetRate");
        registerEmitterProperty(registration, "Spread", "number", "The emitter's scalar spread.", "GetSpread", "SetSpread");
        registerEmitterProperty(registration, "Speed", "number", "The emitter's particle speed.", "GetSpeed", "SetSpeed");
        registerEmitterProperty(registration, "Velocity", "Vector3", "The emitter's current velocity.", "GetVelocity", "SetVelocity");
        registerEmitterProperty(registration, "FollowEntity", "Entity|number|nil", "The entity followed by this emitter. Assign an entity instance or id, or nil to clear it.", "GetFollowEntity", "SetFollowEntity");
        registerEmitterProperty(registration, "Offset", "Vector3", "The offset from the followed entity.", "GetOffset", "SetOffset");
        registerEmitterProperty(registration, "Enabled", "boolean", "Whether this emitter is enabled.", "GetEnabled", "SetEnabled");
        registerEmitterMethod(registration, "GetId", "Returns this emitter's runtime identifier.");
        registerEmitterMethod(registration, "GetIsDestroyed", "Returns whether this emitter has been removed.");
        registerEmitterMethod(registration, "Destroy", "Removes this emitter.");
        registerEmitterMethod(registration, "GetPosition", "Returns the emitter's current position.");
        registerEmitterMethod(registration, "SetPosition", "Moves the emitter to a Vector3 or CFrame position.");
        registerEmitterMethod(registration, "GetRate", "Returns the emitter's particle rate.");
        registerEmitterMethod(registration, "SetRate", "Changes the emitter's particle rate.");
        registerEmitterMethod(registration, "GetSpread", "Returns the emitter's scalar spread.");
        registerEmitterMethod(registration, "SetSpread", "Changes the emitter's scalar spread.");
        registerEmitterMethod(registration, "SetSpreadVector", "Changes spread independently on each axis.");
        registerEmitterMethod(registration, "GetSpeed", "Returns the emitter's particle speed.");
        registerEmitterMethod(registration, "SetSpeed", "Changes the emitter's particle speed.");
        registerEmitterMethod(registration, "GetVelocity", "Returns the emitter's current velocity.");
        registerEmitterMethod(registration, "SetVelocity", "Changes the emitter's velocity.");
        registerEmitterMethod(registration, "GetFollowEntity", "Returns the entity id followed by this emitter, or nil.");
        registerEmitterMethod(registration, "SetFollowEntity", "Attaches the emitter to an entity or clears the attachment with nil.");
        registerEmitterMethod(registration, "ClearFollowEntity", "Stops this emitter from following an entity.");
        registerEmitterMethod(registration, "GetOffset", "Returns the offset from the followed entity.");
        registerEmitterMethod(registration, "SetOffset", "Changes the offset from the followed entity.");
        registerEmitterMethod(registration, "GetEnabled", "Returns whether the emitter is enabled.");
        registerEmitterMethod(registration, "SetEnabled", "Enables or disables this emitter.");
        registerEmitterMethod(registration, "SetParticle", "Changes the particle registry identifier.");
        registerEmitterMethod(registration, "SetSpec", "Changes the typed particle specification.");
    }

    private static void registerEmitterProperty(MLuaRegistration registration, String name, String type,
                                                String description, String getter, String setter) {
        LuaValue get = new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                if (name.equals("Id")) return self.checktable().rawget(EMITTER_ID);
                return invokeEmitterOperation(self, getter).arg1();
            }
        };
        LuaValue set = setter == null || setter.isBlank() ? LuaValue.NIL : new TwoArgFunction() {
            @Override public LuaValue call(LuaValue self, LuaValue value) {
                invokeEmitterOperation(self, setter, value);
                return LuaValue.NONE;
            }
        };
        registration.registerClassProperty("ParticleEmitter", name, get, set, type,
            "client:Client", description);
    }

    private static Varargs invokeEmitterOperation(LuaValue self, String name, LuaValue... values) {
        LuaValue operations = self.checktable().rawget(EMITTER_OPERATIONS);
        LuaValue operation = operations.istable() ? operations.checktable().rawget(name) : LuaValue.NIL;
        if (!operation.isfunction()) throw new LuaError("[MLua] ParticleEmitter." + name + " is unavailable on this instance.");
        LuaValue[] args = new LuaValue[values.length + 1];
        args[0] = self;
        System.arraycopy(values, 0, args, 1, values.length);
        return operation.invoke(LuaValue.varargsOf(args));
    }

    private static void registerEmitterMethod(MLuaRegistration registration, String name, String description) {
        registration.registerClassFunction("ParticleEmitter", name, new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue operations = args.arg1().checktable().rawget(EMITTER_OPERATIONS);
                LuaValue operation = operations.istable() ? operations.checktable().rawget(name) : LuaValue.NIL;
                if (!operation.isfunction()) throw new LuaError("[MLua] ParticleEmitter." + name + " is unavailable on this instance.");
                return operation.invoke(args);
            }
        }, description);
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
        ensureClient();
        double[] coordinates = position(position);
        MLuaClientParticleManager.spawnOnce(particleId, coordinates[0], coordinates[1], coordinates[2], requestedCount.orElse(1),
            requestedSpread.orElse(0.2), requestedSpeed.orElse(0.0));
    }

    @MLuaDescription("Spawns particles from a typed particle specification.")
    public void Spawn(LuaValue specValue, LuaValue position, Optional<Integer> requestedCount,
                      Optional<Double> requestedSpread, Optional<Double> requestedSpeed) {
        ensureClient();
        MLuaClientParticleManager.ParticleSpec spec = specFromLua(specValue);
        double[] coordinates = position(position);
        MLuaClientParticleManager.spawnOnce(spec, coordinates[0], coordinates[1], coordinates[2],
            requestedCount.orElse(1), requestedSpread.orElse(0.2),
            requestedSpeed.orElse(0.0));
    }

    @MLuaDescription("Spawns a burst of particles with spread and velocity options.")
    public void SpawnBurst(LuaValue specValue, LuaValue position, Optional<LuaValue> properties) {
        ensureClient();
        MLuaClientParticleManager.ParticleSpec spec = specFromLua(specValue);
        LuaTable props = properties.orElse(LuaValue.NIL).opttable(new LuaTable());
        int count = firstNonNil(props.get("Count"), props.get("count")).optint(12);
        LuaValue spread = firstNonNil(props.get("Spread"), props.get("spread"));
        double[] spreadVector = spread.isnumber()
            ? new double[]{spread.todouble(), spread.todouble(), spread.todouble()}
            : spread.isnil() ? new double[]{0.2, 0.2, 0.2} : vector3(spread, "Spread");
        double spreadX = firstNonNil(props.get("SpreadX"), props.get("spreadX")).optdouble(spreadVector[0]);
        double spreadY = firstNonNil(props.get("SpreadY"), props.get("spreadY")).optdouble(spreadVector[1]);
        double spreadZ = firstNonNil(props.get("SpreadZ"), props.get("spreadZ")).optdouble(spreadVector[2]);
        LuaValue velocity = firstNonNil(props.get("Velocity"), props.get("velocity"));
        double[] velocityVector = velocity.isnil() ? new double[]{0.0, 0.0, 0.0} : vector3(velocity, "Velocity");
        double[] coordinates = position(position);
        MLuaClientParticleManager.spawnBurst(spec, coordinates[0], coordinates[1], coordinates[2], count,
            spreadX, spreadY, spreadZ, velocityVector[0], velocityVector[1], velocityVector[2]);
    }

    @MLuaDescription("Spawns particles in a ring around a position.")
    public void SpawnRing(LuaValue specValue, LuaValue position, Optional<Integer> requestedCount,
                          Optional<Double> requestedRadius, Optional<Double> requestedSpeed) {
        ensureClient();
        MLuaClientParticleManager.ParticleSpec spec = specFromLua(specValue);
        double[] coordinates = position(position);
        MLuaClientParticleManager.spawnRing(spec, coordinates[0], coordinates[1], coordinates[2],
            requestedCount.orElse(16), requestedRadius.orElse(1.0),
            requestedSpeed.orElse(0.05));
    }

    @MLuaDescription("Spawns particles in a cone along a direction.")
    public void SpawnCone(LuaValue specValue, LuaValue position, LuaValue direction,
                          Optional<Integer> requestedCount, Optional<Double> requestedAngle,
                          Optional<Double> requestedSpeed) {
        ensureClient();
        MLuaClientParticleManager.ParticleSpec spec = specFromLua(specValue);
        double[] coordinates = position(position);
        double[] directionVector = vector3(direction, "Direction");
        MLuaClientParticleManager.spawnCone(spec, coordinates[0], coordinates[1], coordinates[2],
            directionVector[0], directionVector[1], directionVector[2], requestedCount.orElse(10), requestedAngle.orElse(15.0),
            requestedSpeed.orElse(0.1));
    }

    @MLuaDescription("Creates a persistent emitter using a particle identifier.")
    public LuaTable CreateEmitter(String particleId, LuaValue position, Optional<Double> requestedRate,
                                  Optional<Double> requestedSpread, Optional<Double> requestedSpeed) {
        ensureClient();
        double[] coordinates = position(position);
        int id = MLuaClientParticleManager.createEmitter(particleId, coordinates[0], coordinates[1],
            coordinates[2], requestedRate.orElse(8.0),
            requestedSpread.orElse(0.2), requestedSpeed.orElse(0.0), owner());
        return emitterTable(id, owner());
    }

    @MLuaDescription("Creates a persistent emitter from a typed particle specification.")
    public LuaTable CreateTypedEmitter(LuaValue specValue, LuaValue position, Optional<Double> requestedRate,
                                       Optional<Double> requestedSpread, Optional<Double> requestedSpeed) {
        ensureClient();
        MLuaClientParticleManager.ParticleSpec spec = specFromLua(specValue);
        double[] coordinates = position(position);
        int id = MLuaClientParticleManager.createEmitter(spec, coordinates[0], coordinates[1],
            coordinates[2], requestedRate.orElse(8.0), requestedSpread.orElse(0.2),
            requestedSpeed.orElse(0.0), owner());
        return emitterTable(id, owner());
    }

    @MLuaDescription("Removes particle emitters owned by the calling mod.")
    public void Clear() {
        ensureClient();
        MLuaClientParticleManager.clearOwned(owner());
    }

    private LuaTable emitterTable(int id, MLuaParticleOwnershipRegistry.Owner owner) {
        LuaTable operations = new LuaTable();
        operations.set("GetId", fn(args -> LuaValue.valueOf(id)));
        operations.set("GetIsDestroyed", fn(args -> LuaValue.valueOf(MLuaClientParticleManager.get(id, owner) == null)));
        operations.set("Destroy", fn(args -> {
            MLuaClientParticleManager.remove(id, owner);
            return LuaValue.NONE;
        }));
        operations.set("GetPosition", fn(args -> {
            var emitter = requireEmitter(id);
            return vector3(emitter.x(), emitter.y(), emitter.z());
        }));
        operations.set("SetPosition", fn(args -> {
            var emitter = requireEmitter(id);
            double[] position = position(args.arg(2));
            emitter.setPosition(position[0], position[1], position[2]);
            return LuaValue.NONE;
        }));
        operations.set("GetRate", fn(args -> LuaValue.valueOf(requireEmitter(id).rate())));
        operations.set("SetRate", fn(args -> {
            requireEmitter(id).setRate(args.checkdouble(2));
            return LuaValue.NONE;
        }));
        operations.set("GetSpread", fn(args -> LuaValue.valueOf(requireEmitter(id).spread())));
        operations.set("SetSpread", fn(args -> {
            requireEmitter(id).setSpread(args.checkdouble(2));
            return LuaValue.NONE;
        }));
        operations.set("SetSpreadVector", fn(args -> {
            double[] spread = vector3(args.arg(2), "Spread");
            requireEmitter(id).setSpread(spread[0], spread[1], spread[2]);
            return LuaValue.NONE;
        }));
        operations.set("GetSpeed", fn(args -> LuaValue.valueOf(requireEmitter(id).speed())));
        operations.set("SetSpeed", fn(args -> {
            requireEmitter(id).setSpeed(args.checkdouble(2));
            return LuaValue.NONE;
        }));
        operations.set("GetVelocity", fn(args -> {
            var emitter = requireEmitter(id);
            return vector3(emitter.velocityX(), emitter.velocityY(), emitter.velocityZ());
        }));
        operations.set("SetVelocity", fn(args -> {
            double[] velocity = vector3(args.arg(2), "Velocity");
            requireEmitter(id).setVelocity(velocity[0], velocity[1], velocity[2]);
            return LuaValue.NONE;
        }));
        operations.set("GetFollowEntity", fn(args -> {
            MLuaEntityScope scope = requireEmitter(id).followEntityScope();
            return scope != null ? LuaValue.valueOf(scope.entityId()) : LuaValue.NIL;
        }));
        operations.set("SetFollowEntity", fn(args -> {
            requireEmitter(id).setFollowEntity(resolveOptionalEntityScope(args.arg(2)));
            return LuaValue.NONE;
        }));
        operations.set("ClearFollowEntity", fn(args -> {
            requireEmitter(id).setFollowEntity(null);
            return LuaValue.NONE;
        }));
        operations.set("GetOffset", fn(args -> {
            var emitter = requireEmitter(id);
            return vector3(emitter.offsetX(), emitter.offsetY(), emitter.offsetZ());
        }));
        operations.set("SetOffset", fn(args -> {
            double[] offset = vector3(args.arg(2), "Offset");
            requireEmitter(id).setOffset(offset[0], offset[1], offset[2]);
            return LuaValue.NONE;
        }));
        operations.set("GetEnabled", fn(args -> LuaValue.valueOf(requireEmitter(id).enabled())));
        operations.set("SetEnabled", fn(args -> {
            requireEmitter(id).setEnabled(args.checkboolean(2));
            return LuaValue.NONE;
        }));
        operations.set("SetParticle", fn(args -> {
            requireEmitter(id).setParticleId(args.checkjstring(2));
            return LuaValue.NONE;
        }));
        operations.set("SetSpec", fn(args -> {
            requireEmitter(id).setSpec(specFromLua(args.arg(2)));
            return LuaValue.NONE;
        }));
        LuaTable instance = new LuaTable();
        instance.rawset(EMITTER_ID, LuaValue.valueOf(id));
        instance.rawset(EMITTER_OPERATIONS, operations);
        return context.mlua().registration().attachClassInstance(instance, "ParticleEmitter", null);
    }

    private static LuaTable vector3(double x, double y, double z) {
        return MLuaVector.vector3(x, y, z);
    }

    private static double[] position(LuaValue value) {
        return MLuaCFrame.vector(value);
    }

    private static double[] vector3(LuaValue value, String property) {
        if (!MLuaVector.isVector3(value)) {
            throw new LuaError("[MLua] Particle " + property + " requires a native Vector3 value.");
        }
        return MLuaVector.components3(value);
    }

    private MLuaClientParticleManager.Emitter requireEmitter(int id) {
        var emitter = MLuaClientParticleManager.get(id, owner());
        if (emitter == null) {
            throw new LuaError("[MLua] Particle emitter no longer exists");
        }
        return emitter;
    }

    private void ensureClient() {
        if (context.mlua().side() != Side.CLIENT) {
            throw new LuaError("[MLua] ParticleService is client-side only.");
        }
    }

    private MLuaParticleOwnershipRegistry.Owner owner() {
        Object runtimeOwner = context.mlua().registration() instanceof MLuaRuntime runtime ? runtime.session() : null;
        return new MLuaParticleOwnershipRegistry.Owner(runtimeOwner, context.mlua().ownerModId());
    }

    private static MLuaClientParticleManager.ParticleSpec specFromLua(LuaValue value) {
        if (value.isstring()) {
            return new MLuaClientParticleManager.ParticleSpec(value.tojstring(), "simple", null, null, null, 1.0F);
        }
        if (!value.istable()) {
            throw new LuaError("[MLua] Expected particle id string or particle spec table");
        }
        LuaTable table = value.checktable();
        String kind = firstNonNil(table.get("Kind"), table.get("kind")).optjstring("simple").toLowerCase();
        String id = firstNonNil(table.get("Id"), table.get("id")).optjstring("minecraft:poof");
        Float red = numberOrNull(firstNonNil(table.get("Red"), table.get("red")));
        Float green = numberOrNull(firstNonNil(table.get("Green"), table.get("green")));
        Float blue = numberOrNull(firstNonNil(table.get("Blue"), table.get("blue")));
        float size = (float) firstNonNil(table.get("Size"), table.get("size")).optdouble(1.0);
        return new MLuaClientParticleManager.ParticleSpec(id, kind, red, green, blue, size);
    }

    private static float numberOrZero(Float value) {
        return value != null ? value : 0.0F;
    }

    private static Float numberOrNull(LuaValue value) {
        return value.isnil() ? null : (float) value.todouble();
    }

    private static LuaValue firstNonNil(LuaValue a, LuaValue b) {
        return !a.isnil() ? a : b;
    }

    private MLuaEntityScope resolveOptionalEntityScope(LuaValue value) {
        return value.isnil() ? null : resolveEntityScope(value);
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
            if (id.isint()) return this.resolveEntityScope(id.toint());
        }
        throw new LuaError("[MLua] Expected entity table or entity id");
    }

    private MLuaEntityScope resolveEntityScope(int entityId) {
        net.minecraft.world.level.Level level = MLuaMinecraftClient.instance().level;
        net.minecraft.world.entity.Entity entity = level == null ? null : level.getEntity(entityId);
        if (entity == null) throw new LuaError("[MLua] FollowEntity must reference an entity in the current client world");
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
