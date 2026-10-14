package dev.mlua.minecraft.binding;

import dev.mlua.module.MLuaRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaBlockStateWrapper {
    private MLuaBlockStateWrapper() {
    }

    public static LuaTable wrap(BlockState state, Level level, BlockPos position, MLuaRegistration registration) {
        LuaTable fields = new LuaTable();
        fields.set("__block_state", LuaValue.userdataOf(state));
        fields.set("__level", LuaValue.userdataOf(level));
        fields.set("__block_pos", LuaValue.userdataOf(position.immutable()));
        return registration.createClassInstance("BlockState", fields);
    }

    public static LuaTable classTable(MLuaRegistration registration) {
        LuaTable type = registration.registerClass("BlockState", registration.requireInstanceBaseClassName(),
            "An immutable Minecraft block state at a position in a world.");
        if (type.rawget("__initialized").toboolean()) return type;
        type.rawset("__initialized", LuaValue.TRUE);
        registration.registerClassFunction("BlockState", "GetId", self((self, args) -> {
            BlockState state = state(self);
            return LuaValue.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        }), "Returns the block identifier for this state.");
        registration.registerClassFunction("BlockState", "GetBlock", self((self, args) ->
            MLuaBlockWrapper.blockTable(level(self), position(self), registration)),
            "Returns the live Block instance at this state's position.");
        registration.registerClassFunction("BlockState", "GetPosition", self((self, args) -> {
            BlockPos position = position(self);
            return dev.mlua.math.MLuaVector.vector3(position.getX(), position.getY(), position.getZ());
        }), "Returns this state's world position as a Vector3.");
        registration.registerClassFunction("BlockState", "GetProperties", self((self, args) ->
            MLuaBlockStateProperties.read(state(self))), "Returns the current values of this state's properties.");
        registration.registerClassFunction("BlockState", "GetProperty", self((self, args) -> {
            LuaValue value = MLuaBlockStateProperties.read(state(self)).get(args.checkjstring(2));
            return value.isnil() ? LuaValue.NIL : value;
        }), "Returns one state property value, or nil when the state has no such property.");
        registration.registerClassFunction("BlockState", "HasProperty", self((self, args) ->
            LuaValue.valueOf(state(self).getBlock().getStateDefinition().getProperty(args.checkjstring(2)) != null)),
            "Returns whether this block type defines the named state property.");
        registration.registerClassFunction("BlockState", "IsAir", self((self, args) -> LuaValue.valueOf(state(self).isAir())),
            "Returns whether this state is air.");
        registration.registerClassFunction("BlockState", "IsSolid", self((self, args) -> LuaValue.valueOf(state(self).isSolid())),
            "Returns whether this state is solid.");
        registration.registerClassFunction("BlockState", "IsReplaceable", self((self, args) -> LuaValue.valueOf(state(self).canBeReplaced())),
            "Returns whether this state can be replaced by another block.");
        registration.registerClassFunction("BlockState", "GetDestroySpeed", self((self, args) -> {
            BlockState state = state(self);
            return LuaValue.valueOf(state.getDestroySpeed(level(self), position(self)));
        }), "Returns this state’s destroy speed in its world context.");
        String[][] properties = {
            {"Id", "GetId", "string", "The block identifier for this state."},
            {"Block", "GetBlock", "Block", "The live block at this state's position."},
            {"Position", "GetPosition", "Vector3", "This state's world position."},
            {"Properties", "GetProperties", "BlockStateProperties", "The current state property values."},
            {"Air", "IsAir", "boolean", "Whether this state is air."},
            {"Solid", "IsSolid", "boolean", "Whether this state is solid."},
            {"Replaceable", "IsReplaceable", "boolean", "Whether another block can replace this state."},
            {"DestroySpeed", "GetDestroySpeed", "number", "The state destroy speed in its world context."}
        };
        for (String[] property : properties) {
            dev.mlua.core.classes.MLuaClassMethods.registerInstanceProperty(registration, "BlockState", property[0],
                property[1], null, property[2], "shared:Shared", property[3]);
        }
        return type;
    }

    private static LuaValue stateValue(LuaValue self, String name) {
        LuaValue value = self.checktable().rawget(name);
        if (!value.isuserdata(BlockState.class)) throw new LuaError("Expected a live BlockState instance.");
        return value;
    }

    private static BlockState state(LuaValue self) {
        return (BlockState)stateValue(self, "__block_state").touserdata(BlockState.class);
    }

    private static Level level(LuaValue self) {
        LuaValue value = self.checktable().rawget("__level");
        if (!value.isuserdata(Level.class)) throw new LuaError("Expected a live World instance.");
        return (Level)value.touserdata(Level.class);
    }

    private static BlockPos position(LuaValue self) {
        LuaValue value = self.checktable().rawget("__block_pos");
        if (!value.isuserdata(BlockPos.class)) throw new LuaError("Expected a BlockState position.");
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
