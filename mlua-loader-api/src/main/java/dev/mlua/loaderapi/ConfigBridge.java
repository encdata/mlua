package dev.mlua.loaderapi;

public interface ConfigBridge {
    ConfigValue<?> define(String path, Object defaultValue, String comment);
    void build();
    void save();
}
