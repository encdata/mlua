package dev.mlua.modules.sound.service;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.classes.MLuaClassMethods;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

/** Registers the Lua class facade for addon-owned live sound instances. */
final class MLuaSoundHandleBinding {
    static final String OPERATIONS = "__mlua_sound_handle_operations";
    static final String ID = "__mlua_sound_handle_id";

    private MLuaSoundHandleBinding() { }

    static void register(MLuaRegistration registration) {
        String description = "A live client sound instance returned by SoundService playback functions. It exposes playback state, position, looping, volume, pitch, and entity-follow settings.";
        registration.registerClass("SoundHandle", registration.requireInstanceBaseClassName(), description);
        registerProperty(registration, "Id", "number", "The sound's runtime identifier.", "GetId", null);
        registerProperty(registration, "IsPlaying", "boolean", "Whether this sound is currently playing.", "GetIsPlaying", null);
        registerProperty(registration, "IsDestroyed", "boolean", "Whether this sound has been removed.", "GetIsDestroyed", null);
        registerProperty(registration, "Looped", "boolean", "Whether playback repeats after reaching the end.", "GetLooped", "SetLooped");
        registerProperty(registration, "Volume", "number", "The sound's finite, non-negative playback volume.", "GetVolume", "SetVolume");
        registerProperty(registration, "Pitch", "number", "The sound's finite, non-negative playback pitch.", "GetPitch", "SetPitch");
        registerProperty(registration, "Position", "Vector3|CFrame", "The sound's current position with finite coordinates.", "GetPosition", "SetPosition");
        registerProperty(registration, "FollowEntity", "Entity|number|nil", "The entity followed by this sound. Assign an entity instance or id, or nil to clear it.", "GetFollowEntity", "SetFollowEntity");
        registerProperty(registration, "FollowOffset", "Vector3", "The finite offset from the followed entity.", "GetFollowOffset", "SetFollowOffset");
        registerMethod(registration, "Stop", "Stops playback of this sound.");
        registerMethod(registration, "Destroy", "Stops playback and removes this sound handle.");
        registerMethod(registration, "GetId", "Returns this sound's runtime identifier.");
        registerMethod(registration, "Equals", "Compares this handle with another handle for the same owned sound instance.");
        registerMethod(registration, "GetHashCode", "Returns a hash derived from this handle's owner and runtime identifier.");
        registerMethod(registration, "ToString", "Returns the runtime class, owner, and sound identifier.");
        registerMethod(registration, "GetIsPlaying", "Returns whether this sound is currently playing.");
        registerMethod(registration, "GetIsDestroyed", "Returns whether this sound has been removed.");
        registerMethod(registration, "GetLooped", "Returns whether playback repeats after reaching the end.");
        registerMethod(registration, "SetLooped", "Enables or disables repeating playback.");
        registerMethod(registration, "GetVolume", "Returns the sound's playback volume.");
        registerMethod(registration, "SetVolume", "Sets a finite, non-negative playback volume.");
        registerMethod(registration, "GetPitch", "Returns the sound's playback pitch.");
        registerMethod(registration, "SetPitch", "Sets a finite, non-negative playback pitch.");
        registerMethod(registration, "GetPosition", "Returns the sound's current position.");
        registerMethod(registration, "SetPosition", "Moves the sound to a Vector3 or CFrame with finite coordinates and clears any entity-follow target.");
        registerMethod(registration, "GetFollowEntity", "Returns the entity id followed by this sound, or nil.");
        registerMethod(registration, "SetFollowEntity", "Attaches this sound to an entity.");
        registerMethod(registration, "AttachToEntity", "Attaches this sound to an entity.");
        registerMethod(registration, "ClearFollowEntity", "Stops this sound from following an entity.");
        registerMethod(registration, "GetFollowOffset", "Returns the offset from the followed entity.");
        registerMethod(registration, "SetFollowOffset", "Sets a finite Vector3 offset from the followed entity.");
    }

    private static void registerProperty(MLuaRegistration registration, String name, String type,
                                         String description, String getter, String setter) {
        LuaValue get = new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                return MLuaSoundHandleBinding.invoke(self, getter, LuaValue.NONE).arg1();
            }
        };
        LuaValue set = setter == null ? LuaValue.NIL : new TwoArgFunction() {
            @Override public LuaValue call(LuaValue self, LuaValue value) {
                if (name.equals("FollowEntity") && value.isnil()) {
                    MLuaSoundHandleBinding.invoke(self, "ClearFollowEntity", LuaValue.NONE);
                } else {
                    MLuaSoundHandleBinding.invoke(self, setter, LuaValue.varargsOf(new LuaValue[]{value}));
                }
                return LuaValue.NONE;
            }
        };
        registration.registerClassProperty("SoundHandle", name, get, set, type, "client:Client", description);
    }

    private static void registerMethod(MLuaRegistration registration, String name, String description) {
        MLuaClassMethods.registerInstanceMethod(registration, "SoundHandle", name, description);
    }

    static Varargs invoke(LuaValue self, String name, Varargs values) {
        LuaValue operations = self.checktable().rawget(OPERATIONS);
        LuaValue operation = operations.istable() ? operations.checktable().rawget(name) : LuaValue.NIL;
        if (!operation.isfunction()) throw new LuaError("[MLua] SoundHandle." + name + " is unavailable on this instance.");
        LuaValue[] args = new LuaValue[values.narg() + 1];
        args[0] = self;
        for (int index = 1; index < args.length; index++) args[index] = values.arg(index);
        return operation.invoke(LuaValue.varargsOf(args));
    }
}
