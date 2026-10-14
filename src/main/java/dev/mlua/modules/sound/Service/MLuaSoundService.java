package dev.mlua.modules.sound.Service;

import dev.mlua.math.MLuaCFrame;
import dev.mlua.math.MLuaVector;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.runtime.client.MLuaClientSoundManager;
import dev.mlua.runtime.client.MLuaSoundOwnershipRegistry;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.sounds.SoundSource;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

@MLuaDescription("Client-side sound playback with loop, stop, and entity-follow handles.")
public final class MLuaSoundService extends MLuaReflectiveService {
    private static final String HANDLE_OPERATIONS = "__sound_handle_operations";
    private static final String HANDLE_ID = "__sound_handle_id";
    public static void registerSoundHandleClass(MLuaRegistration registration) {
        String description = "A live client sound instance returned by SoundService playback functions. It exposes playback state, position, looping, volume, pitch, and entity-follow settings.";
        registration.registerClass("SoundHandle", registration.requireInstanceBaseClassName(), description);
        registerHandleProperty(registration, "Id", "number", "The sound's runtime identifier.", "", null);
        registerHandleProperty(registration, "IsPlaying", "boolean", "Whether this sound is currently playing.", "GetIsPlaying", null);
        registerHandleProperty(registration, "IsDestroyed", "boolean", "Whether this sound has been removed.", "GetIsDestroyed", null);
        registerHandleProperty(registration, "Looped", "boolean", "Whether playback repeats after reaching the end.", "GetLooped", "SetLooped");
        registerHandleProperty(registration, "Volume", "number", "The sound's playback volume.", "GetVolume", "SetVolume");
        registerHandleProperty(registration, "Pitch", "number", "The sound's playback pitch.", "GetPitch", "SetPitch");
        registerHandleProperty(registration, "Position", "Vector3|CFrame", "The sound's current position.", "GetPosition", "SetPosition");
        registerHandleProperty(registration, "FollowEntity", "Entity|number|nil", "The entity followed by this sound. Assign an entity instance or id, or nil to clear it.", "GetFollowEntity", "SetFollowEntity");
        registerHandleProperty(registration, "FollowOffset", "Vector3", "The offset from the followed entity.", "GetFollowOffset", "SetFollowOffset");
        registerHandleMethod(registration, "Stop", "Stops playback of this sound.");
        registerHandleMethod(registration, "Destroy", "Stops playback and removes this sound handle.");
        registerHandleMethod(registration, "GetIsPlaying", "Returns whether this sound is currently playing.");
        registerHandleMethod(registration, "GetIsDestroyed", "Returns whether this sound has been removed.");
        registerHandleMethod(registration, "GetLooped", "Returns whether playback repeats after reaching the end.");
        registerHandleMethod(registration, "SetLooped", "Enables or disables repeating playback.");
        registerHandleMethod(registration, "GetVolume", "Returns the sound's playback volume.");
        registerHandleMethod(registration, "SetVolume", "Changes the sound's playback volume.");
        registerHandleMethod(registration, "GetPitch", "Returns the sound's playback pitch.");
        registerHandleMethod(registration, "SetPitch", "Changes the sound's playback pitch.");
        registerHandleMethod(registration, "GetPosition", "Returns the sound's current position.");
        registerHandleMethod(registration, "SetPosition", "Moves the sound to a Vector3 or CFrame position and clears any entity-follow target.");
        registerHandleMethod(registration, "GetFollowEntity", "Returns the entity id followed by this sound, or nil.");
        registerHandleMethod(registration, "SetFollowEntity", "Attaches this sound to an entity.");
        registerHandleMethod(registration, "AttachToEntity", "Attaches this sound to an entity.");
        registerHandleMethod(registration, "ClearFollowEntity", "Stops this sound from following an entity.");
        registerHandleMethod(registration, "GetFollowOffset", "Returns the offset from the followed entity.");
        registerHandleMethod(registration, "SetFollowOffset", "Changes the offset from the followed entity.");
    }

    private static void registerHandleProperty(MLuaRegistration registration, String name, String type,
                                              String description, String getter, String setter) {
        LuaValue get = new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                return name.equals("Id") ? self.checktable().rawget(HANDLE_ID) : invokeHandleOperation(self, getter).arg1();
            }
        };
        LuaValue set = setter == null ? LuaValue.NIL : new TwoArgFunction() {
            @Override public LuaValue call(LuaValue self, LuaValue value) {
                if (name.equals("FollowEntity") && value.isnil()) {
                    invokeHandleOperation(self, "ClearFollowEntity");
                } else {
                    invokeHandleOperation(self, setter, value);
                }
                return LuaValue.NONE;
            }
        };
        registration.registerClassProperty("SoundHandle", name, get, set, type, "client:Client", description);
    }

    private static void registerHandleMethod(MLuaRegistration registration, String name, String description) {
        registration.registerClassFunction("SoundHandle", name, new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return invokeHandleOperation(args.arg1(), name, args.subargs(2)); }
        }, description);
    }

    private static Varargs invokeHandleOperation(LuaValue self, String name, LuaValue... values) {
        LuaValue operations = self.checktable().rawget(HANDLE_OPERATIONS);
        LuaValue operation = operations.istable() ? operations.checktable().rawget(name) : LuaValue.NIL;
        if (!operation.isfunction()) throw new LuaError("[MLua] SoundHandle." + name + " is unavailable on this instance.");
        LuaValue[] args = new LuaValue[values.length + 1];
        args[0] = self;
        System.arraycopy(values, 0, args, 1, values.length);
        return operation.invoke(LuaValue.varargsOf(args));
    }

    private static Varargs invokeHandleOperation(LuaValue self, String name, Varargs values) {
        LuaValue[] args = new LuaValue[values.narg() + 1];
        args[0] = self;
        for (int i = 1; i < args.length; i++) args[i] = values.arg(i);
        LuaValue operations = self.checktable().rawget(HANDLE_OPERATIONS);
        LuaValue operation = operations.istable() ? operations.checktable().rawget(name) : LuaValue.NIL;
        if (!operation.isfunction()) throw new LuaError("[MLua] SoundHandle." + name + " is unavailable on this instance.");
        return operation.invoke(LuaValue.varargsOf(args));
    }

    public MLuaSoundService(MLuaService.Context context) {
        super(context);
    }

    @Override
    public String name() {
        return "SoundService";
    }

    @Override
    public LuaTable toLua() {
        LuaTable table = super.toLua();
        table.set("__side", "Client");
        return table;
    }

    @MLuaDescription("Plays a sound at the local player.")
    public boolean PlayLocal(String soundId, Optional<Double> volume, Optional<Double> pitch) {
        ensureClient();
        return MLuaClientSoundManager.playLocal(soundId, volume.orElse(1.0).floatValue(),
            pitch.orElse(1.0).floatValue(), owner());
    }

    @MLuaDescription("Plays a sound at the local player's position.")
    public boolean PlayAtPlayer(String soundId, Optional<Double> volume, Optional<Double> pitch) {
        ensureClient();
        return MLuaClientSoundManager.playAtPlayer(soundId, volume.orElse(1.0).floatValue(),
            pitch.orElse(1.0).floatValue(), owner());
    }

    @MLuaDescription("Plays a sound with optional position, loop, and entity-follow settings.")
    public LuaValue Play(String soundId, Optional<LuaValue> properties) {
        ensureClient();
        LuaTable props = properties.orElse(LuaValue.NIL).opttable(new LuaTable());
        float volume = (float) firstNonNil(props.get("Volume"), props.get("volume")).optdouble(1.0);
        float pitch = (float) firstNonNil(props.get("Pitch"), props.get("pitch")).optdouble(1.0);
        boolean loop = firstNonNil(firstNonNil(props.get("Looped"), props.get("looped")),
            firstNonNil(props.get("Loop"), props.get("loop"))).optboolean(false);
        MLuaEntityScope followEntity = resolveOptionalEntityScope(
            firstNonNil(props.get("FollowEntity"), props.get("followEntity")));
        SoundSource category = parseSoundSource(
            firstNonNil(props.get("Category"), props.get("category")).optjstring("players"));
        Double x = null;
        Double y = null;
        Double z = null;
        double followOffsetX = 0.0;
        double followOffsetY = 0.0;
        double followOffsetZ = 0.0;
        LuaValue position = firstNonNil(props.get("Position"), props.get("position"));
        if (!position.isnil()) {
            double[] coordinates = MLuaCFrame.vector(position);
            x = coordinates[0];
            y = coordinates[1];
            z = coordinates[2];
        }
        LuaValue followOffset = firstNonNil(props.get("FollowOffset"), props.get("followOffset"));
        if (!followOffset.isnil()) {
            double[] offset = vector3(followOffset, "FollowOffset");
            followOffsetX = offset[0];
            followOffsetY = offset[1];
            followOffsetZ = offset[2];
        }
        MLuaSoundOwnershipRegistry.Owner owner = owner();
        int id = MLuaClientSoundManager.play(soundId, volume, pitch, loop, x, y, z, followEntity,
            category, followOffsetX, followOffsetY, followOffsetZ, owner);
        return id >= 0 ? soundTable(id, owner) : LuaValue.NIL;
    }

    @MLuaDescription("Stops sounds owned by the calling mod.")
    public void Clear() {
        ensureClient();
        MLuaClientSoundManager.clearOwned(owner());
    }

    private LuaTable soundTable(int id, MLuaSoundOwnershipRegistry.Owner owner) {
        LuaTable operations = new LuaTable();
        operations.set("Stop", fn(args -> {
            MLuaClientSoundManager.stop(id, owner);
            return LuaValue.NONE;
        }));
        operations.set("Destroy", operations.get("Stop"));
        operations.set("GetIsPlaying", fn(args -> LuaValue.valueOf(MLuaClientSoundManager.isActive(id, owner))));
        operations.set("GetIsDestroyed", fn(args -> LuaValue.valueOf(MLuaClientSoundManager.get(id, owner) == null)));
        operations.set("GetLooped", fn(args -> LuaValue.valueOf(requireSound(id).loopingValue())));
        operations.set("SetLooped", fn(args -> {
            requireSound(id).setLoopingValue(args.checkboolean(2));
            return LuaValue.NONE;
        }));
        operations.set("GetVolume", fn(args -> LuaValue.valueOf(requireSound(id).volumeValue())));
        operations.set("SetVolume", fn(args -> {
            requireSound(id).setVolumeValue((float) args.checkdouble(2));
            return LuaValue.NONE;
        }));
        operations.set("GetPitch", fn(args -> LuaValue.valueOf(requireSound(id).pitchValue())));
        operations.set("SetPitch", fn(args -> {
            requireSound(id).setPitchValue((float) args.checkdouble(2));
            return LuaValue.NONE;
        }));
        operations.set("GetPosition", fn(args -> {
            var sound = requireSound(id);
            return vector3(sound.xPos(), sound.yPos(), sound.zPos());
        }));
        operations.set("SetPosition", fn(args -> {
            double[] position = MLuaCFrame.vector(args.arg(2));
            requireSound(id).setPosition(position[0], position[1], position[2]);
            return LuaValue.NONE;
        }));
        operations.set("GetFollowEntity", fn(args -> {
            MLuaEntityScope scope = requireSound(id).followEntityScope();
            return scope != null ? LuaValue.valueOf(scope.entityId()) : LuaValue.NIL;
        }));
        operations.set("FollowEntity", fn(args -> {
            requireSound(id).followEntity(resolveEntityScope(args.arg(2)));
            return LuaValue.NONE;
        }));
        operations.set("AttachToEntity", operations.get("FollowEntity"));
        operations.set("SetFollowEntity", operations.get("FollowEntity"));
        operations.set("ClearFollowEntity", fn(args -> {
            requireSound(id).followEntity(null);
            return LuaValue.NONE;
        }));
        operations.set("GetFollowOffset", fn(args -> {
            var sound = requireSound(id);
            return vector3(sound.followOffsetX(), sound.followOffsetY(), sound.followOffsetZ());
        }));
        operations.set("SetFollowOffset", fn(args -> {
            double[] offset = vector3(args.arg(2), "FollowOffset");
            requireSound(id).setFollowOffset(offset[0], offset[1], offset[2]);
            return LuaValue.NONE;
        }));
        LuaTable instance = new LuaTable();
        instance.rawset(HANDLE_ID, LuaValue.valueOf(id));
        instance.rawset(HANDLE_OPERATIONS, operations);
        return context.mlua().registration().attachClassInstance(instance, "SoundHandle", null);
    }

    private MLuaClientSoundManager.ManagedSoundInstance requireSound(int id) {
        var sound = MLuaClientSoundManager.get(id, owner());
        if (sound == null) {
            throw new LuaError("[MLua] Sound handle no longer exists");
        }
        return sound;
    }

    private void ensureClient() {
        if (context.mlua().side() != Side.CLIENT) {
            throw new LuaError("[MLua] SoundService is client-side only.");
        }
    }

    private MLuaSoundOwnershipRegistry.Owner owner() {
        Object runtimeOwner = context.mlua().registration() instanceof MLuaRuntime runtime ? runtime.session() : null;
        return new MLuaSoundOwnershipRegistry.Owner(runtimeOwner, context.mlua().ownerModId());
    }

    private static LuaValue firstNonNil(LuaValue a, LuaValue b) {
        return !a.isnil() ? a : b;
    }

    private static double[] vector3(LuaValue value, String name) {
        if (!MLuaVector.isVector3(value)) throw new LuaError("[MLua] Sound " + name + " requires a native Vector3 value.");
        return MLuaVector.components3(value);
    }

    private MLuaEntityScope resolveOptionalEntityScope(LuaValue value) {
        return value.isnil() ? null : resolveEntityScope(value);
    }

    private MLuaEntityScope resolveEntityScope(LuaValue value) {
        if (value.isint()) {
            return this.resolveEntityScope(value.toint());
        }
        if (value.istable()) {
            net.minecraft.world.entity.Entity entity = MLuaEntityWrapper.fromLuaInstance(value, this.context.mlua().registration());
            if (entity != null) return MLuaEntityScope.of(entity);
        }
        throw new LuaError("[MLua] Expected an Entity instance or entity ID");
    }

    private MLuaEntityScope resolveEntityScope(int entityId) {
        net.minecraft.world.level.Level level = MLuaMinecraftClient.instance().level;
        net.minecraft.world.entity.Entity entity = level == null ? null : level.getEntity(entityId);
        if (entity == null) throw new LuaError("[MLua] FollowEntity must reference an entity in the current client world");
        return MLuaEntityScope.of(entity);
    }

    private static SoundSource parseSoundSource(String value) {
        if (value == null || value.isBlank()) {
            return SoundSource.PLAYERS;
        }
        try {
            return SoundSource.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return SoundSource.PLAYERS;
        }
    }

    private static LuaTable vector3(double x, double y, double z) {
        return MLuaVector.vector3(x, y, z);
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
