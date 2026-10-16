package dev.mlua.modules.sound.Service;

import dev.mlua.math.MLuaVector;
import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
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
        registerHandleProperty(registration, "Volume", "number", "The sound's finite, non-negative playback volume.", "GetVolume", "SetVolume");
        registerHandleProperty(registration, "Pitch", "number", "The sound's finite, non-negative playback pitch.", "GetPitch", "SetPitch");
        registerHandleProperty(registration, "Position", "Vector3|CFrame", "The sound's current position with finite coordinates.", "GetPosition", "SetPosition");
        registerHandleProperty(registration, "FollowEntity", "Entity|number|nil", "The entity followed by this sound. Assign an entity instance or id, or nil to clear it.", "GetFollowEntity", "SetFollowEntity");
        registerHandleProperty(registration, "FollowOffset", "Vector3", "The finite offset from the followed entity.", "GetFollowOffset", "SetFollowOffset");
        registerHandleMethod(registration, "Stop", "Stops playback of this sound.");
        registerHandleMethod(registration, "Destroy", "Stops playback and removes this sound handle.");
        registerHandleMethod(registration, "GetIsPlaying", "Returns whether this sound is currently playing.");
        registerHandleMethod(registration, "GetIsDestroyed", "Returns whether this sound has been removed.");
        registerHandleMethod(registration, "GetLooped", "Returns whether playback repeats after reaching the end.");
        registerHandleMethod(registration, "SetLooped", "Enables or disables repeating playback.");
        registerHandleMethod(registration, "GetVolume", "Returns the sound's playback volume.");
        registerHandleMethod(registration, "SetVolume", "Sets a finite, non-negative playback volume.");
        registerHandleMethod(registration, "GetPitch", "Returns the sound's playback pitch.");
        registerHandleMethod(registration, "SetPitch", "Sets a finite, non-negative playback pitch.");
        registerHandleMethod(registration, "GetPosition", "Returns the sound's current position.");
        registerHandleMethod(registration, "SetPosition", "Moves the sound to a Vector3 or CFrame with finite coordinates and clears any entity-follow target.");
        registerHandleMethod(registration, "GetFollowEntity", "Returns the entity id followed by this sound, or nil.");
        registerHandleMethod(registration, "SetFollowEntity", "Attaches this sound to an entity.");
        registerHandleMethod(registration, "AttachToEntity", "Attaches this sound to an entity.");
        registerHandleMethod(registration, "ClearFollowEntity", "Stops this sound from following an entity.");
        registerHandleMethod(registration, "GetFollowOffset", "Returns the offset from the followed entity.");
        registerHandleMethod(registration, "SetFollowOffset", "Sets a finite Vector3 offset from the followed entity.");
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

    @MLuaDescription("Plays a non-spatial sound and returns its live SoundHandle. Volume and pitch default to one and must be finite and non-negative.")
    public LuaValue PlayLocal(String soundId, Optional<Double> volume, Optional<Double> pitch) {
        ensureClient();
        float checkedVolume = MLuaLuaArguments.finiteNonNegativeFloat(
            volume.orElse(1.0), "SoundService:PlayLocal", "volume");
        float checkedPitch = MLuaLuaArguments.finiteNonNegativeFloat(
            pitch.orElse(1.0), "SoundService:PlayLocal", "pitch");
        int id = MLuaClientSoundManager.playLocalId(soundId, checkedVolume, checkedPitch, owner());
        return id >= 0 ? soundTable(id, owner()) : LuaValue.NIL;
    }

    @MLuaDescription("Plays a sound at the local player's position and returns its live SoundHandle. Volume and pitch default to one and must be finite and non-negative.")
    public LuaValue PlayAtPlayer(String soundId, Optional<Double> volume, Optional<Double> pitch) {
        ensureClient();
        float checkedVolume = MLuaLuaArguments.finiteNonNegativeFloat(
            volume.orElse(1.0), "SoundService:PlayAtPlayer", "volume");
        float checkedPitch = MLuaLuaArguments.finiteNonNegativeFloat(
            pitch.orElse(1.0), "SoundService:PlayAtPlayer", "pitch");
        int id = MLuaClientSoundManager.playAtPlayerId(soundId, checkedVolume, checkedPitch, owner());
        return id >= 0 ? soundTable(id, owner()) : LuaValue.NIL;
    }

    @MLuaDescription("Plays a sound with optional position, loop, and entity-follow settings. Volume and pitch default to one and must be finite and non-negative.")
    public LuaValue Play(String soundId, Optional<LuaValue> properties) {
        ensureClient();
        LuaValue propertiesValue = properties.orElse(LuaValue.NIL);
        if (!propertiesValue.isnil() && !propertiesValue.istable()) {
            throw new LuaError("[MLua] SoundService:Play properties must be a table when provided.");
        }
        LuaTable props = propertiesValue.istable() ? propertiesValue.checktable() : new LuaTable();
        float volume = MLuaLuaArguments.optionalNonNegativeFiniteFloat(
            option(props, "Volume"), 1.0f, "SoundService:Play", "volume");
        float pitch = MLuaLuaArguments.optionalNonNegativeFiniteFloat(
            option(props, "Pitch"), 1.0f, "SoundService:Play", "pitch");
        LuaValue loopedOption = option(props, "Looped");
        LuaValue loopOption = option(props, "Loop");
        if (!loopedOption.isnil() && !loopOption.isnil()) {
            throw new LuaError("[MLua] SoundService:Play options Looped and Loop are aliases; provide only one.");
        }
        boolean loop = (!loopedOption.isnil() ? loopedOption : loopOption).optboolean(false);
        MLuaEntityScope followEntity = resolveOptionalEntityScope(
            option(props, "FollowEntity"));
        SoundSource category = parseSoundSource(
            option(props, "Category").optjstring("players"));
        Double x = null;
        Double y = null;
        Double z = null;
        double followOffsetX = 0.0;
        double followOffsetY = 0.0;
        double followOffsetZ = 0.0;
        LuaValue position = option(props, "Position");
        if (!position.isnil()) {
            double[] coordinates = MLuaLuaArguments.finiteVector3(position, "SoundService:Play position");
            x = coordinates[0];
            y = coordinates[1];
            z = coordinates[2];
        }
        LuaValue followOffset = option(props, "FollowOffset");
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
            requireSound(id).setVolumeValue(MLuaLuaArguments.finiteNonNegativeFloat(
                args.arg(2), "SoundHandle:SetVolume", "volume"));
            return LuaValue.NONE;
        }));
        operations.set("GetPitch", fn(args -> LuaValue.valueOf(requireSound(id).pitchValue())));
        operations.set("SetPitch", fn(args -> {
            requireSound(id).setPitchValue(MLuaLuaArguments.finiteNonNegativeFloat(
                args.arg(2), "SoundHandle:SetPitch", "pitch"));
            return LuaValue.NONE;
        }));
        operations.set("GetPosition", fn(args -> {
            var sound = requireSound(id);
            return vector3(sound.xPos(), sound.yPos(), sound.zPos());
        }));
        operations.set("SetPosition", fn(args -> {
            double[] position = MLuaLuaArguments.finiteVector3(args.arg(2), "SoundHandle:SetPosition");
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
        this.requireClientSide(this.name());
    }

    private MLuaSoundOwnershipRegistry.Owner owner() {
        Object runtimeOwner = context.mlua().registration() instanceof MLuaRuntime runtime ? runtime.session() : null;
        return new MLuaSoundOwnershipRegistry.Owner(runtimeOwner, context.mlua().ownerModId());
    }

    private static LuaValue option(LuaTable options, String name) {
        LuaValue canonical = options.rawget(name);
        String aliasName = name.isEmpty() ? name
            : Character.toLowerCase(name.charAt(0)) + name.substring(1);
        LuaValue alias = options.rawget(aliasName);
        if (!canonical.isnil() && !alias.isnil()) {
            throw new LuaError("[MLua] SoundService:Play options '" + name + "' and '" + aliasName
                + "' are aliases; provide only one.");
        }
        return canonical.isnil() ? alias : canonical;
    }

    private static double[] vector3(LuaValue value, String name) {
        if (!MLuaVector.isVector3(value)) throw new LuaError("[MLua] Sound " + name + " requires a native Vector3 value.");
        return MLuaLuaArguments.finiteVector3(value, "Sound " + name);
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
