package dev.mlua.minecraft.binding;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.classes.MLuaClassMethods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaFluidStateWrapper {
    private MLuaFluidStateWrapper() {
    }

    public static LuaTable wrap(FluidState state, Level level, BlockPos position, MLuaRegistration registration) {
        LuaTable fields = new LuaTable();
        fields.set("__fluid_state", LuaValue.userdataOf(state));
        fields.set("__level", LuaValue.userdataOf(level));
        fields.set("__block_pos", LuaValue.userdataOf(position.immutable()));
        return registration.createClassInstance("FluidState", fields);
    }

    public static LuaTable classTable(MLuaRegistration registration) {
        LuaTable type = registration.registerClass("FluidState", registration.requireInstanceBaseClassName(),
            "An immutable Minecraft fluid state at a world position.");
        if (type.rawget("__initialized").toboolean()) return type;
        type.rawset("__initialized", LuaValue.TRUE);
        registration.registerClassFunction("FluidState", "GetId", self((self, args) -> {
            var id = BuiltInRegistries.FLUID.getKey(state(self).getType());
            return id == null ? LuaValue.NIL : LuaValue.valueOf(id.toString());
        }), "Returns this fluid state's registered fluid identifier, or nil when unavailable.");
        registration.registerClassFunction("FluidState", "GetPosition", self((self, args) -> {
            BlockPos position = position(self);
            return dev.mlua.math.MLuaVector.vector3(position.getX(), position.getY(), position.getZ());
        }), "Returns this fluid state's world position as a Vector3.");
        registration.registerClassFunction("FluidState", "IsEmpty", self((self, args) ->
            LuaValue.valueOf(state(self).isEmpty())), "Returns whether this position contains no fluid.");
        registration.registerClassFunction("FluidState", "IsSource", self((self, args) ->
            LuaValue.valueOf(state(self).isSource())), "Returns whether this is a source fluid state.");
        registration.registerClassFunction("FluidState", "GetAmount", self((self, args) ->
            LuaValue.valueOf(state(self).getAmount())), "Returns this fluid state's level amount.");
        registration.registerClassFunction("FluidState", "GetHeight", self((self, args) ->
            LuaValue.valueOf(state(self).getHeight(level(self), position(self)))),
            "Returns this fluid state's height at its world position.");
        registration.registerClassFunction("FluidState", "GetBlockState", self((self, args) ->
            MLuaBlockStateWrapper.wrap(state(self).createLegacyBlock(), level(self), position(self), registration)),
            "Returns the legacy BlockState that represents this fluid at its world position.");
        String[][] properties = {
            {"Id", "GetId", "string|nil", "This fluid state's registered fluid identifier."},
            {"Position", "GetPosition", "Vector3", "This fluid state's world position."},
            {"Empty", "IsEmpty", "boolean", "Whether this position contains no fluid."},
            {"Source", "IsSource", "boolean", "Whether this is a source fluid state."},
            {"Amount", "GetAmount", "number", "This fluid state's level amount."},
            {"Height", "GetHeight", "number", "This fluid state's height at its world position."}
        };
        for (String[] property : properties) {
            MLuaClassMethods.registerInstanceProperty(registration, "FluidState", property[0], property[1], null,
                property[2], "shared:Shared", property[3]);
        }
        return type;
    }

    private static FluidState state(LuaValue self) {
        LuaValue value = self.checktable().rawget("__fluid_state");
        if (!value.isuserdata(FluidState.class)) throw new LuaError("Expected a native FluidState instance.");
        return (FluidState)value.touserdata(FluidState.class);
    }

    private static Level level(LuaValue self) {
        LuaValue value = self.checktable().rawget("__level");
        if (!value.isuserdata(Level.class)) throw new LuaError("Expected a live World instance.");
        return (Level)value.touserdata(Level.class);
    }

    private static BlockPos position(LuaValue self) {
        LuaValue value = self.checktable().rawget("__block_pos");
        if (!value.isuserdata(BlockPos.class)) throw new LuaError("Expected a FluidState position.");
        return (BlockPos)value.touserdata(BlockPos.class);
    }

    @FunctionalInterface
    private interface Body {
        LuaValue apply(LuaValue self, Varargs args);
    }

    private static VarArgFunction self(Body body) {
        return new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return body.apply(args.arg1(), args); }
        };
    }
}
