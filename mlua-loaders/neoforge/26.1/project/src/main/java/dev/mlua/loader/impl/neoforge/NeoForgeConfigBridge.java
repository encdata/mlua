package dev.mlua.loader.impl.neoforge;

import dev.mlua.loaderapi.ConfigBridge;
import dev.mlua.loaderapi.ConfigValue;
import dev.mlua.runtime.config.MLuaConfigRegistry;
import java.util.LinkedHashMap;
import java.util.Map;

public class NeoForgeConfigBridge implements ConfigBridge {
    private final MLuaConfigRegistry configRegistry = MLuaConfigRegistry.get();
    private final Map<String, ConfigEntry<?>> entries = new LinkedHashMap<>();

    private record ConfigEntry<T>(T defaultValue, ConfigValue<T> value) {
    }

    @Override
    public ConfigValue<?> define(String path, Object defaultValue, String comment) {
        ConfigValue<Object> value = () -> {
            ConfigEntry<?> entry = entries.get(path);
            return entry == null ? defaultValue : entry.value().get();
        };
        entries.put(path, new ConfigEntry<>(defaultValue, () -> defaultValue));
        return value;
    }

    @Override
    public void build() {
        configRegistry.install();
    }

    @Override
    public void save() {
        throw new UnsupportedOperationException("This Forge config bridge has no persistence backend. Use MLuaConfigService for persistent script config.");
    }

    public <T> void setValue(String path, T value) {
        entries.put(path, new ConfigEntry<>(value, () -> value));
    }

    @SuppressWarnings("unchecked")
    public <T> T getValue(String path) {
        ConfigEntry<?> entry = entries.get(path);
        return entry == null ? null : (T) entry.value().get();
    }
}
