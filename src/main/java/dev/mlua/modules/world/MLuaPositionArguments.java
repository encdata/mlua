package dev.mlua.modules.world;

import dev.mlua.math.MLuaCFrame;
import net.minecraft.core.BlockPos;
import dev.mlua.minecraft.binding.MLuaLuaArguments;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Shared parsing for world positions, coordinates, and optional Minecraft rotation arguments. */
public final class MLuaPositionArguments {
    private MLuaPositionArguments() { }

    public static double[] vector3(Varargs args, int index) {
        return vector3(args.arg(index), "World position");
    }

    public static double[] vector3(LuaValue value, String argumentName) {
        return MLuaLuaArguments.finiteVector3(value, argumentName);
    }

    public static int argumentWidth(Varargs args, int index) {
        return args.arg(index).istable() ? 1 : 3;
    }

    public static BlockPos blockPosition(Varargs args, int index) {
        double[] position = vector3(args, index);
        return BlockPos.containing(position[0], position[1], position[2]);
    }

    public static float yaw(Varargs args, int positionIndex, int yawIndex) {
        LuaValue yaw = args.arg(yawIndex);
        if (!yaw.isnil()) return finiteRotation(yaw, "Yaw");
        LuaValue position = args.arg(positionIndex);
        return MLuaCFrame.isCFrame(position) ? MLuaCFrame.fromLua(position).minecraftYaw() : 0.0f;
    }

    public static float pitch(Varargs args, int positionIndex, int pitchIndex) {
        LuaValue pitch = args.arg(pitchIndex);
        if (!pitch.isnil()) return finiteRotation(pitch, "Pitch");
        LuaValue position = args.arg(positionIndex);
        return MLuaCFrame.isCFrame(position) ? MLuaCFrame.fromLua(position).minecraftPitch() : 0.0f;
    }

    private static float finiteRotation(LuaValue value, String name) {
        return MLuaLuaArguments.finiteFloat(value, "World rotation", name.toLowerCase(java.util.Locale.ROOT));
    }
}
