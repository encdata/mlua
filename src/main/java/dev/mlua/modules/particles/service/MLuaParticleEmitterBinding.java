package dev.mlua.modules.particles.service;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.classes.MLuaClassMethods;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

/** Registers the Lua class surface shared by every ParticleEmitter instance. */
final class MLuaParticleEmitterBinding {
    static final String OPERATIONS = "__particle_emitter_operations";
    static final String ID = "__particle_emitter_id";

    private MLuaParticleEmitterBinding() { }

    static void register(MLuaRegistration registration) {
        registration.registerClass("ParticleEmitter", registration.requireInstanceBaseClassName(),
            "A live client-side particle emitter with mutable position, rate, spread, velocity, and particle settings.");
        property(registration, "Id", "number", "The emitter's runtime identifier.", "GetId", null);
        property(registration, "IsDestroyed", "boolean", "Whether the emitter has been removed.", "GetIsDestroyed", null);
        property(registration, "Position", "Vector3|CFrame", "The emitter's current position.", "GetPosition", "SetPosition");
        property(registration, "Rate", "number", "The emitter's particle rate.", "GetRate", "SetRate");
        property(registration, "Spread", "number", "The largest spread component.", "GetSpread", "SetSpread");
        property(registration, "SpreadVector", "Vector3", "The emitter's spread on each axis.", "GetSpreadVector", "SetSpreadVector");
        property(registration, "Speed", "number", "The emitter's particle speed.", "GetSpeed", "SetSpeed");
        property(registration, "Velocity", "Vector3", "The emitter's current velocity.", "GetVelocity", "SetVelocity");
        property(registration, "FollowEntity", "Entity|number|nil", "The entity followed by this emitter. Assign an entity instance or id, or nil to clear it.", "GetFollowEntity", "SetFollowEntity");
        property(registration, "Offset", "Vector3", "The offset from the followed entity.", "GetOffset", "SetOffset");
        property(registration, "Enabled", "boolean", "Whether this emitter is enabled.", "GetEnabled", "SetEnabled");
        method(registration, "GetId", "Returns this emitter's runtime identifier.");
        method(registration, "Equals", "Compares this handle with another handle for the same owned emitter.");
        method(registration, "GetHashCode", "Returns a hash derived from this handle's owner and runtime identifier.");
        method(registration, "ToString", "Returns the runtime class, owner, and emitter identifier.");
        method(registration, "GetIsDestroyed", "Returns whether this emitter has been removed.");
        method(registration, "Destroy", "Removes this emitter.");
        method(registration, "GetPosition", "Returns the emitter's current position.");
        method(registration, "SetPosition", "Moves the emitter to a Vector3 or CFrame position.");
        method(registration, "GetRate", "Returns the emitter's current particle rate.");
        method(registration, "SetRate", "Changes the emitter's particle rate.");
        method(registration, "GetSpread", "Returns the largest spread component.");
        method(registration, "SetSpread", "Sets the same spread on every axis.");
        method(registration, "GetSpreadVector", "Returns the spread on each axis as a Vector3.");
        method(registration, "SetSpreadVector", "Changes spread independently on each axis.");
        method(registration, "GetSpeed", "Returns the emitter's particle speed.");
        method(registration, "SetSpeed", "Changes the emitter's particle speed.");
        method(registration, "GetVelocity", "Returns the emitter's current velocity.");
        method(registration, "SetVelocity", "Changes the emitter's velocity.");
        method(registration, "GetFollowEntity", "Returns the entity id followed by this emitter, or nil.");
        method(registration, "SetFollowEntity", "Attaches the emitter to an entity or clears the attachment with nil.");
        method(registration, "ClearFollowEntity", "Stops this emitter from following an entity.");
        method(registration, "GetOffset", "Returns the offset from the followed entity.");
        method(registration, "SetOffset", "Changes the offset from the followed entity.");
        method(registration, "GetEnabled", "Returns whether the emitter is enabled.");
        method(registration, "SetEnabled", "Enables or disables the emitter.");
        method(registration, "SetParticle", "Changes the particle registry identifier.");
        method(registration, "SetSpec", "Changes the typed particle specification.");
    }

    private static void property(MLuaRegistration registration, String name, String type,
                                 String description, String getter, String setter) {
        LuaValue get = new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) {
                return MLuaParticleEmitterBinding.invoke(self, getter).arg1();
            }
        };
        LuaValue set = setter == null ? LuaValue.NIL : new TwoArgFunction() {
            @Override public LuaValue call(LuaValue self, LuaValue value) {
                MLuaParticleEmitterBinding.invoke(self, setter, value);
                return LuaValue.NONE;
            }
        };
        registration.registerClassProperty("ParticleEmitter", name, get, set, type, "client:Client", description);
    }

    private static void method(MLuaRegistration registration, String name, String description) {
        MLuaClassMethods.registerInstanceMethod(registration, "ParticleEmitter", name, description);
    }

    private static Varargs invoke(LuaValue self, String name, LuaValue... values) {
        return invoke(self, name, LuaValue.varargsOf(values));
    }

    private static Varargs invoke(LuaValue self, String name, Varargs values) {
        LuaValue operations = self.checktable().rawget(OPERATIONS);
        LuaValue operation = operations.istable() ? operations.checktable().rawget(name) : LuaValue.NIL;
        if (!operation.isfunction()) {
            throw new LuaError("[MLua] ParticleEmitter." + name + " is unavailable on this instance.");
        }
        return operation.invoke(LuaValue.varargsOf(self, values));
    }
}
