package dev.mlua.modules.effects;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;

/** Lua class definitions for effect metadata and active-effect snapshots. */
final class MLuaPotionEffectClassBinding {
    private MLuaPotionEffectClassBinding() { }

    static void register(MLuaRegistration registration) {
        registration.registerClass("PotionEffectType", registration.requireInstanceBaseClassName(),
            "A metadata snapshot for a registered Minecraft or addon potion effect.");
        property(registration, "Id", "string", "The namespaced effect identifier.");
        property(registration, "Name", "string", "The translated display name for the current language.");
        property(registration, "TranslationKey", "string", "The effect translation key.");
        property(registration, "DescriptionId", "string", "The effect description identifier.");
        property(registration, "Color", "number", "The effect's default particle color as an RGB integer.");
        property(registration, "IsBeneficial", "boolean", "Whether Minecraft classifies the effect as beneficial.");
        property(registration, "IsInstant", "boolean", "Whether the effect applies instantly rather than over time.");
        property(registration, "Icon", "string|nil", "The scripted effect icon path, when one is registered.");

        registration.registerClass("ActivePotionEffect", registration.requireInstanceBaseClassName(),
            "A point-in-time snapshot of one effect currently applied to an entity.");
        property(registration, "ActivePotionEffect", "Id", "string", "The namespaced effect identifier.");
        property(registration, "ActivePotionEffect", "Duration", "number", "Remaining duration in game ticks.");
        property(registration, "ActivePotionEffect", "Amplifier", "number", "The zero-based effect amplifier.");
        property(registration, "ActivePotionEffect", "Ambient", "boolean", "Whether the effect is ambient.");
        property(registration, "ActivePotionEffect", "ShowParticles", "boolean", "Whether effect particles are visible.");
    }

    private static void property(MLuaRegistration registration, String name, String type, String description) {
        property(registration, "PotionEffectType", name, type, description);
    }

    private static void property(MLuaRegistration registration, String className, String name,
                                String type, String description) {
        registration.registerClassFunction(className, "Get" + name, new OneArgFunction() {
            @Override public LuaValue call(LuaValue self) { return self.checktable().get(name); }
        }, description);
        MLuaClassMethods.registerInstanceProperty(registration, className, name, "Get" + name,
            null, type, "shared:Both", description);
    }
}
