package dev.mlua.modules.registry.service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.loaderapi.GenerationDecorationStep;
import dev.mlua.modules.enums.MLuaEnumValue;

/** Converts Lua world generation ids and decoration steps into data pack values. */
final class MLuaRegistryWorldGenerationValues {
    private MLuaRegistryWorldGenerationValues() { }

    static String qualifiedId(String owner, String value, boolean tag) {
        String candidate = value.trim();
        if (!candidate.contains(":")) candidate = owner + ":" + candidate;
        var parsed = MLuaResourceLocations.tryParseOrNull(candidate);
        if (parsed == null) throw new LuaError("[MLua] Invalid world generation id: " + value);
        return tag ? "#" + parsed : parsed.toString();
    }

    static String decorationStep(LuaTable definition) {
        LuaValue value = definition.get("step");
        if (value.isnil()) value = definition.get("Step");
        if (value.type() == LuaValue.TSTRING) return value.checkjstring();
        if (value.isnil()) return null;
        GenerationDecorationStep step = MLuaEnumValue.toJava(value, GenerationDecorationStep.class);
        if (step == null) {
            throw new LuaError("[MLua] WorldGeneration.addFeature step expects a string or Enum.GenerationStep.Decoration value.");
        }
        return step.name();
    }
}
