package dev.mlua.mc.impl.fabric;

import dev.mlua.loader.impl.fabric.FabricConfigBridge;
import java.nio.file.Path;

public final class FabricConfigWiring {
    private static final String CONFIG_FILE_NAME = "mlua.json";

    private FabricConfigWiring() {
    }

    public static void wire(FabricConfigBridge bridge, String modId) {
        Path configDir = MLuaFabricBridge.getConfigDir();
        Path configFile = configDir.resolve(modId).resolve(CONFIG_FILE_NAME);
        MLuaFabricConfigHandler handler = new MLuaFabricConfigHandler(configFile);

        for (var entry : bridge.getEntries().entrySet()) {
            handler.define(entry.getKey(), entry.getValue().defaultValue());
        }

        bridge.setLoadHandler(() -> {
            handler.load();
            for (var entry : bridge.getEntries().entrySet()) {
                Object loaded = handler.get(entry.getKey(), Object.class);
                if (loaded != null) {
                    bridge.setValue(entry.getKey(), loaded);
                }
            }
        });

        bridge.setSaveHandler(() -> {
            for (var entry : bridge.getEntries().entrySet()) {
                handler.set(entry.getKey(), entry.getValue().value().get());
            }
            handler.save();
        });
    }
}
