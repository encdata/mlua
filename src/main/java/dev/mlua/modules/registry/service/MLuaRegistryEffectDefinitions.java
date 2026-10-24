package dev.mlua.modules.registry.service;

import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaLuaArguments;

/** Parses potion effects and scripted mob effect properties. */
final class MLuaRegistryEffectDefinitions {
    private MLuaRegistryEffectDefinitions() { }

    static MobEffectCategory category(LuaTable definition) {
        String value = MLuaRegistryPropertyReaders.stringProperty(definition, "category", "Category");
        if (value == null || value.isBlank()) return MobEffectCategory.NEUTRAL;
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "beneficial", "benefit", "good" -> MobEffectCategory.BENEFICIAL;
            case "harmful", "bad", "negative" -> MobEffectCategory.HARMFUL;
            case "neutral" -> MobEffectCategory.NEUTRAL;
            default -> throw new LuaError("[MLua] Unknown mob effect category: " + value);
        };
    }

    static String descriptionId(String owner, String name, String explicit) {
        return explicit != null && !explicit.isBlank() ? explicit : "effect." + owner + "." + name;
    }

    static List<MobEffectInstance> potionEffects(LuaTable properties) {
        LuaValue value = properties == null ? LuaValue.NIL : properties.get("effects");
        if (value.isnil()) return List.of();
        if (!value.istable()) throw new LuaError("[MLua] Potion effects must be a table");

        List<MobEffectInstance> effects = new java.util.ArrayList<>();
        LuaTable entries = value.checktable();
        for (int index = 1; ; index++) {
            LuaValue entry = entries.get(index);
            if (entry.isnil()) return effects;
            effects.add(potionEffect(entry.checktable()));
        }
    }

    private static MobEffectInstance potionEffect(LuaTable definition) {
        String effectId = definition.get("effect").checkjstring();
        //--version 1.21.1
        var effect = BuiltInRegistries.MOB_EFFECT.getHolder(MLuaResourceLocations.parse(effectId)).orElse(null);
        //--endversion
        //--version 26.1
        var effect = BuiltInRegistries.MOB_EFFECT.get(MLuaResourceLocations.parse(effectId)).orElse(null);
        //--endversion
        //--version 1.20.1
        MobEffect effect = (MobEffect) BuiltInRegistries.MOB_EFFECT.get(MLuaResourceLocations.parse(effectId));
        //--endversion
        if (effect == null) throw new LuaError("[MLua] Unknown potion effect: " + effectId);

        int duration = MLuaLuaArguments.optionalWholeInt(definition.get("duration"), 600,
                "Registry.Potions:Register", "duration");
        int amplifier = MLuaLuaArguments.optionalWholeInt(definition.get("amplifier"), 0,
                "Registry.Potions:Register", "amplifier");
        if (amplifier < 0) {
            throw new LuaError("[MLua] Registry.Potions:Register amplifier must be non-negative");
        }
        return new MobEffectInstance(effect, duration, amplifier,
                definition.get("ambient").optboolean(false), definition.get("visible").optboolean(true),
                definition.get("showIcon").optboolean(true));
    }
}
