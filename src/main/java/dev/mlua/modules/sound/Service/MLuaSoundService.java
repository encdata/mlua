package dev.mlua.modules.sound.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import dev.mlua.runtime.client.MLuaClientSoundManager;
import dev.mlua.runtime.client.MLuaSoundOwnershipRegistry;
import java.util.function.Function;
import net.minecraft.sounds.SoundSource;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

public final class MLuaSoundService extends MLuaServiceBase {
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
        registerHandleProperty(registration, "Position", "Vector3", "The sound's current position.", "GetPosition", "SetPosition");
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
        registerHandleMethod(registration, "SetPosition", "Moves the sound to the given coordinates.");
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
                if (name.equals("Position")) {
                    LuaTable position = value.checktable();
                    invokeHandleOperation(self, setter, LuaValue.valueOf(component(position, "X")),
                        LuaValue.valueOf(component(position, "Y")), LuaValue.valueOf(component(position, "Z")));
                } else if (name.equals("FollowEntity") && value.isnil()) {
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
        LuaTable table = new LuaTable();
        table.set("__side", "Client");
        table.set("PlayLocal", fn(args -> {
            ensureClient();
            return LuaValue.valueOf(MLuaClientSoundManager.playLocal(args.checkjstring(2), (float) args.optdouble(3, 1.0), (float) args.optdouble(4, 1.0), owner()));
        }));
        table.set("PlayAtPlayer", fn(args -> {
            ensureClient();
            return LuaValue.valueOf(MLuaClientSoundManager.playAtPlayer(args.checkjstring(2), (float) args.optdouble(3, 1.0), (float) args.optdouble(4, 1.0), owner()));
        }));
        table.set("Play", fn(args -> {
            ensureClient();
            String soundId = args.checkjstring(2);
            LuaTable props = args.arg(3).opttable(new LuaTable());
            float volume = (float) firstNonNil(props.get("Volume"), props.get("volume")).optdouble(1.0);
            float pitch = (float) firstNonNil(props.get("Pitch"), props.get("pitch")).optdouble(1.0);
            boolean loop = firstNonNil(firstNonNil(props.get("Looped"), props.get("looped")), firstNonNil(props.get("Loop"), props.get("loop"))).optboolean(false);
            MLuaEntityScope followEntity = resolveOptionalEntityScope(firstNonNil(props.get("FollowEntity"), props.get("followEntity")));
            SoundSource category = parseSoundSource(firstNonNil(props.get("Category"), props.get("category")).optjstring("players"));
            Double x = null;
            Double y = null;
            Double z = null;
            double followOffsetX = 0.0;
            double followOffsetY = 0.0;
            double followOffsetZ = 0.0;
            LuaValue position = firstNonNil(props.get("Position"), props.get("position"));
            if (position.istable()) {
                LuaTable pos = position.checktable();
                x = component(pos, "X");
                y = component(pos, "Y");
                z = component(pos, "Z");
            }
            LuaValue followOffset = firstNonNil(props.get("FollowOffset"), props.get("followOffset"));
            if (followOffset.istable()) {
                LuaTable offset = followOffset.checktable();
                followOffsetX = component(offset, "X");
                followOffsetY = component(offset, "Y");
                followOffsetZ = component(offset, "Z");
            }
            MLuaSoundOwnershipRegistry.Owner owner = owner();
            int id = MLuaClientSoundManager.play(soundId, volume, pitch, loop, x, y, z, followEntity, category, followOffsetX, followOffsetY, followOffsetZ, owner);
            return id >= 0 ? soundTable(id, owner) : LuaValue.NIL;
        }));
        table.set("Clear", fn(args -> {
            ensureClient();
            MLuaClientSoundManager.clearOwned(owner());
            return LuaValue.NONE;
        }));
        return table;
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
            requireSound(id).setPosition(args.checkdouble(2), args.checkdouble(3), args.checkdouble(4));
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
            LuaTable tableValue = args.checktable(2);
            requireSound(id).setFollowOffset(component(tableValue, "X"), component(tableValue, "Y"), component(tableValue, "Z"));
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

    private static double component(LuaTable table, String key) {
        LuaValue value = table.get(key);
        if (value.isnil()) {
            value = table.get(key.toLowerCase());
        }
        return value.optdouble(0.0);
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
        LuaTable out = new LuaTable();
        out.set("X", x);
        out.set("Y", y);
        out.set("Z", z);
        return out;
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
