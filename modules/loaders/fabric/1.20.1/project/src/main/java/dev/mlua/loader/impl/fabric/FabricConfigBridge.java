package dev.mlua.loader.impl.fabric;

import dev.mlua.loaderapi.ConfigBridge;
import dev.mlua.loaderapi.ConfigValue;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FabricConfigBridge implements ConfigBridge {
    public final Map<String, ConfigEntry<?>> entries = new LinkedHashMap<>();
    public Runnable saveHandler;
    public Runnable loadHandler;

    public record ConfigEntry<T>(T defaultValue, ConfigValue<T> value) {
    }

    @Override
    public ConfigValue<?> define(String path, Object defaultValue, String comment) {
        ConfigValue<Object> value = () -> defaultValue;
        entries.put(path, new ConfigEntry<>(defaultValue, value));
        return value;
    }

    @Override
    public void build() {
        if (loadHandler != null) {
            loadHandler.run();
        }
    }

    @Override
    public void save() {
        if (saveHandler != null) {
            saveHandler.run();
        }
    }

    @SuppressWarnings("unchecked")
    public <T> void setValue(String path, T value) {
        ConfigEntry<?> entry = entries.get(path);
        if (entry != null) {
            ConfigValue<T> newValue = () -> value;
            entries.put(path, new ConfigEntry<>(value, newValue));
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getValue(String path) {
        ConfigEntry<?> entry = entries.get(path);
        if (entry != null) {
            return (T) entry.value().get();
        }
        return null;
    }

    public Map<String, ConfigEntry<?>> getEntries() {
        return entries;
    }

    public void setSaveHandler(Runnable handler) {
        this.saveHandler = handler;
    }

    public void setLoadHandler(Runnable handler) {
        this.loadHandler = handler;
    }

    public void loadDefaults() {
        for (Map.Entry<String, ConfigEntry<?>> entry : entries.entrySet()) {
            ConfigEntry<?> ce = entry.getValue();
            ConfigValue<Object> value = () -> ce.defaultValue();
            entries.put(entry.getKey(), new ConfigEntry<>(ce.defaultValue(), value));
        }
    }
}
