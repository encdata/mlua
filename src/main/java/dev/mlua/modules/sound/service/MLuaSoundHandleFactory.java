package dev.mlua.modules.sound.service;

import dev.mlua.api.MLuaLuaErrors;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.modules.entity.MLuaEntityScopeResolver;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import dev.mlua.runtime.MLuaGameThreadAccess;
import dev.mlua.runtime.client.MLuaClientSoundManager;
import dev.mlua.runtime.client.MLuaSoundOwnershipRegistry;
import dev.mlua.math.MLuaVector;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import java.util.function.Function;

final class MLuaSoundHandleFactory {
    private static final String HANDLE_OPERATIONS = MLuaSoundHandleBinding.OPERATIONS;
    private static final String HANDLE_ID = MLuaSoundHandleBinding.ID;
    private final MLuaService.Context context;

    MLuaSoundHandleFactory(MLuaService.Context context) {
        this.context = context;
    }

    LuaTable create(int id, MLuaSoundOwnershipRegistry.Owner owner) {
        LuaTable operations = new LuaTable();
        String ownerModId = owner.ownerModId();
        operations.set("GetId", fn("GetId", args -> LuaValue.valueOf(id)));
        operations.set("Equals", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue other = args.arg(2);
                if (!other.istable() || !context.mlua().registration().isInstanceOf(other, "SoundHandle")) {
                    return LuaValue.FALSE;
                }
                LuaTable otherHandle = other.checktable();
                return LuaValue.valueOf(id == otherHandle.rawget(HANDLE_ID).optint(-1)
                    && ownerModId.equals(otherHandle.rawget("__mluaOwnerModId").optjstring("")));
            }
        });
        operations.set("GetHashCode", fn("GetHashCode", args -> LuaValue.valueOf(java.util.Objects.hash(ownerModId, id))));
        operations.set("ToString", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue self = args.arg1();
                String className = context.mlua().registration().classNameOf(self);
                return LuaValue.valueOf((className == null ? "SoundHandle" : className)
                    + "[" + ownerModId + ":" + id + "]");
            }
        });
        operations.set("Stop", fn("Stop", args -> {
            MLuaClientSoundManager.stop(id, owner);
            return LuaValue.NONE;
        }));
        operations.set("Destroy", operations.get("Stop"));
        operations.set("GetIsPlaying", fn("GetIsPlaying", args -> LuaValue.valueOf(MLuaClientSoundManager.isActive(id, owner))));
        operations.set("GetIsDestroyed", fn("GetIsDestroyed", args -> LuaValue.valueOf(MLuaClientSoundManager.get(id, owner) == null)));
        operations.set("GetLooped", fn("GetLooped", args -> LuaValue.valueOf(requireSound(id, owner).loopingValue())));
        operations.set("SetLooped", fn("SetLooped", args -> {
            requireSound(id, owner).setLoopingValue(args.checkboolean(2));
            return LuaValue.NONE;
        }));
        operations.set("GetVolume", fn("GetVolume", args -> LuaValue.valueOf(requireSound(id, owner).volumeValue())));
        operations.set("SetVolume", fn("SetVolume", args -> {
            requireSound(id, owner).setVolumeValue(MLuaLuaArguments.finiteNonNegativeFloat(
                args.arg(2), "SoundHandle:SetVolume", "volume"));
            return LuaValue.NONE;
        }));
        operations.set("GetPitch", fn("GetPitch", args -> LuaValue.valueOf(requireSound(id, owner).pitchValue())));
        operations.set("SetPitch", fn("SetPitch", args -> {
            requireSound(id, owner).setPitchValue(MLuaLuaArguments.finiteNonNegativeFloat(
                args.arg(2), "SoundHandle:SetPitch", "pitch"));
            return LuaValue.NONE;
        }));
        operations.set("GetPosition", fn("GetPosition", args -> {
            var sound = requireSound(id, owner);
            return vector3(sound.xPos(), sound.yPos(), sound.zPos());
        }));
        operations.set("SetPosition", fn("SetPosition", args -> {
            double[] position = MLuaLuaArguments.finiteVector3(args.arg(2), "SoundHandle:SetPosition");
            requireSound(id, owner).setPosition(position[0], position[1], position[2]);
            return LuaValue.NONE;
        }));
        operations.set("GetFollowEntity", fn("GetFollowEntity", args -> {
            MLuaEntityScope scope = requireSound(id, owner).followEntityScope();
            return scope != null ? LuaValue.valueOf(scope.entityId()) : LuaValue.NIL;
        }));
        operations.set("FollowEntity", fn("FollowEntity", args -> {
            requireSound(id, owner).followEntity(resolveEntityScope(args.arg(2)));
            return LuaValue.NONE;
        }));
        operations.set("AttachToEntity", operations.get("FollowEntity"));
        operations.set("SetFollowEntity", operations.get("FollowEntity"));
        operations.set("ClearFollowEntity", fn("ClearFollowEntity", args -> {
            requireSound(id, owner).followEntity(null);
            return LuaValue.NONE;
        }));
        operations.set("GetFollowOffset", fn("GetFollowOffset", args -> {
            var sound = requireSound(id, owner);
            return vector3(sound.followOffsetX(), sound.followOffsetY(), sound.followOffsetZ());
        }));
        operations.set("SetFollowOffset", fn("SetFollowOffset", args -> {
            double[] offset = vector3(args.arg(2), "FollowOffset");
            requireSound(id, owner).setFollowOffset(offset[0], offset[1], offset[2]);
            return LuaValue.NONE;
        }));
        LuaTable instance = new LuaTable();
        instance.rawset(HANDLE_ID, LuaValue.valueOf(id));
        instance.rawset(HANDLE_OPERATIONS, operations);
        instance.rawset("__mluaOwnerModId", LuaValue.valueOf(ownerModId));
        LuaTable attached = context.mlua().registration().attachClassInstance(instance, "SoundHandle", null);
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

    private MLuaClientSoundManager.ManagedSoundInstance requireSound(int id, MLuaSoundOwnershipRegistry.Owner owner) {
        var sound = MLuaClientSoundManager.get(id, owner);
        if (sound == null) {
            throw new LuaError("[MLua] Sound handle no longer exists");
        }
        return sound;
    }

    private MLuaEntityScope resolveEntityScope(LuaValue value) {
        return MLuaEntityScopeResolver.resolveClientTarget(value,
            context.mlua().registration(), "FollowEntity must reference an entity in the current client world");
    }

    private static LuaTable vector3(double x, double y, double z) {
        return MLuaVector.vector3(x, y, z);
    }


    private static VarArgFunction fn(String operation, Function<Varargs, LuaValue> body) {
        return MLuaLuaErrors.operation("SoundHandle:" + operation, args -> {
            if (!usesClientState(operation)) return body.apply(args);
            return MLuaGameThreadAccess.callUnchecked(context, () -> body.apply(args));
        }, failure -> {
            MLuaLogger.LOGGER.error("SoundHandle:{} failed", operation, failure);
            return new LuaError("[MLua] SoundHandle:" + operation + " failed: "
                + failure.getClass().getSimpleName() + ": "
                + (failure.getMessage() == null ? "no details" : failure.getMessage()));
        });
    }

    private static boolean usesClientState(String operation) {
        return switch (operation) {
            case "Stop", "Destroy", "GetIsPlaying", "GetIsDestroyed", "GetLooped", "SetLooped",
                "GetVolume", "SetVolume", "GetPitch", "SetPitch", "GetPosition", "SetPosition",
                "GetFollowEntity", "FollowEntity", "ClearFollowEntity", "GetFollowOffset", "SetFollowOffset" -> true;
            default -> false;
        };
    }

    static double[] vector3(LuaValue value, String name) {
        if (!MLuaVector.isVector3(value)) throw new LuaError("[MLua] Sound " + name + " requires a native Vector3 value.");
        return MLuaLuaArguments.finiteVector3(value, "Sound " + name);
    }

}
