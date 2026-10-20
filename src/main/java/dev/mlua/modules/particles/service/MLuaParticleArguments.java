package dev.mlua.modules.particles.service;

import dev.mlua.math.MLuaCFrame;
import dev.mlua.math.MLuaVector;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.runtime.client.MLuaClientParticleManager;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Validates Lua particle inputs and converts them into runtime particle values. */
final class MLuaParticleArguments {
    private MLuaParticleArguments() { }

    static LuaTable vector3(double x, double y, double z) {
        return MLuaVector.vector3(x, y, z);
    }

    static double[] position(LuaValue value) {
        double[] result = MLuaCFrame.vector(value);
        finite(result[0], "Particle", "position.x");
        finite(result[1], "Particle", "position.y");
        finite(result[2], "Particle", "position.z");
        return result;
    }

    static double[] vector3(LuaValue value, String property) {
        if (!MLuaVector.isVector3(value)) {
            throw new LuaError("[MLua] Particle " + property + " requires a native Vector3 value.");
        }
        double[] result = MLuaVector.components3(value);
        finite(result[0], "Particle", property + ".x");
        finite(result[1], "Particle", property + ".y");
        finite(result[2], "Particle", property + ".z");
        return result;
    }

    static int particleCount(Optional<Integer> value, int fallback, String method) {
        return boundedCount(value.orElse(fallback), method);
    }

    static int boundedCount(int count, String method) {
        if (count < 0 || count > 4096) {
            throw new LuaError("[MLua] ParticleService:" + method + " count must be from 0 to 4096.");
        }
        return count;
    }

    static double optionalNonNegative(LuaValue value, double fallback, String method, String parameter) {
        return value.isnil() ? nonNegative(fallback, method, parameter)
            : nonNegative(value.checkdouble(), method, parameter);
    }

    static double nonNegative(double value, String method, String parameter) {
        return bounded(value, method, parameter, 0.0, 4096.0);
    }

    static double finite(double value, String method, String parameter) {
        if (!Double.isFinite(value)) {
            throw new LuaError("[MLua] ParticleService:" + method + " " + parameter + " must be finite.");
        }
        return value;
    }

    static double bounded(double value, String method, String parameter, double minimum, double maximum) {
        finite(value, method, parameter);
        if (value < minimum || value > maximum) {
            throw new LuaError("[MLua] ParticleService:" + method + " " + parameter
                + " must be from " + minimum + " to " + maximum + ".");
        }
        return value;
    }

    static MLuaClientParticleManager.ParticleSpec specFromLua(LuaValue value) {
        if (value.isstring()) {
            return new MLuaClientParticleManager.ParticleSpec(particleId(value.tojstring()), "simple", null, null, null, 1.0F);
        }
        if (!value.istable()) {
            throw new LuaError("[MLua] Expected particle id string or particle spec table");
        }
        LuaTable table = value.checktable();
        String kind = firstNonNil(table.get("Kind"), table.get("kind"))
            .optjstring("simple").trim().toLowerCase(java.util.Locale.ROOT);
        if (!kind.equals("simple") && !kind.equals("dust") && !kind.equals("block") && !kind.equals("item")) {
            throw new LuaError("[MLua] Particle spec Kind must be 'simple', 'dust', 'block', or 'item'.");
        }
        String id = particleId(firstNonNil(table.get("Id"), table.get("id")).optjstring("minecraft:poof"));
        Float red = numberOrNull(firstNonNil(table.get("Red"), table.get("red")));
        Float green = numberOrNull(firstNonNil(table.get("Green"), table.get("green")));
        Float blue = numberOrNull(firstNonNil(table.get("Blue"), table.get("blue")));
        float size = (float) bounded(firstNonNil(table.get("Size"), table.get("size")).optdouble(1.0),
            "ParticleSpec", "size", 0.01, 16.0);
        return new MLuaClientParticleManager.ParticleSpec(id, kind, red, green, blue, size);
    }

    static Float numberOrNull(LuaValue value) {
        if (value.isnil()) return null;
        double component = finite(value.checkdouble(), "ParticleSpec", "color component");
        return (float) bounded(component, "ParticleSpec", "color component", 0.0, 1.0);
    }

    static String particleId(String id) {
        ResourceLocation location = MLuaResourceLocations.tryParseOrNull(id);
        if (location == null) throw new LuaError("[MLua] Invalid particle identifier: " + id);
        return location.toString();
    }

    static LuaValue firstNonNil(LuaValue a, LuaValue b) {
        return !a.isnil() ? a : b;
    }
}
