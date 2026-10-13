package dev.mlua.mc.impl.forge;

import java.util.List;
import java.util.function.Predicate;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

public final class MLuaForgeConfigAdapter {
    private MLuaForgeConfigAdapter() {
    }

    public static ForgeConfigSpec.Builder builder() {
        return new ForgeConfigSpec.Builder();
    }

    public static ForgeConfigSpec build(ForgeConfigSpec.Builder builder) {
        return builder.build();
    }

    public static ForgeConfigSpec.ConfigValue<Boolean> defineBoolean(ForgeConfigSpec.Builder builder, String path, boolean defaultValue) {
        return builder.define(path, defaultValue);
    }

    public static ForgeConfigSpec.ConfigValue<Integer> defineInteger(ForgeConfigSpec.Builder builder, String path, int defaultValue, int min, int max) {
        return builder.defineInRange(path, defaultValue, min, max);
    }

    public static ForgeConfigSpec.ConfigValue<Double> defineDouble(ForgeConfigSpec.Builder builder, String path, double defaultValue, double min, double max) {
        return builder.defineInRange(path, defaultValue, min, max);
    }

    public static ForgeConfigSpec.ConfigValue<String> defineString(ForgeConfigSpec.Builder builder, String path, String defaultValue) {
        return builder.define(path, defaultValue);
    }

    public static <T> ForgeConfigSpec.ConfigValue<T> define(ForgeConfigSpec.Builder builder, String path, T defaultValue) {
        return builder.define(path, defaultValue);
    }

    public static ForgeConfigSpec.ConfigValue<List<?>> defineList(ForgeConfigSpec.Builder builder, String path, List<?> defaultValue, Predicate<Object> elementValidator) {
        return builder.defineList(path, defaultValue, elementValidator);
    }

    public static void push(ForgeConfigSpec.Builder builder, String category) {
        builder.push(category);
    }

    public static void pop(ForgeConfigSpec.Builder builder) {
        builder.pop();
    }

    public static void comment(ForgeConfigSpec.Builder builder, String comment) {
        builder.comment(comment);
    }

    public static ModConfig.Type typeFromExtension(String extension) {
        for (ModConfig.Type type : ModConfig.Type.values()) {
            if (type.extension().equals(extension)) {
                return type;
            }
        }
        return ModConfig.Type.COMMON;
    }
}
